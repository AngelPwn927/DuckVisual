package dev.duckvisual.cosmetics;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.duckvisual.DuckVisual;
import dev.duckvisual.module.impl.cosmetics.Capes;
import dev.duckvisual.module.impl.cosmetics.Masks;
import dev.duckvisual.module.impl.cosmetics.Skins;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Capes, masks and skins people pick in the wardrobe on duckvisual.ru (or in the launcher).
 * Who wears what is fetched from the site by Minecraft nickname; the textures of the catalog are
 * bundled in the mod and anything newer (or a custom skin) is downloaded once and cached on disk.
 */
public final class CosmeticsManager {
    public static final String SITE = System.getProperty("duckvisual.site", "https://duckvisual.ru");
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,32}");
    private static final Pattern UPLOAD = Pattern.compile("u:[a-f0-9]{16,40}");
    private static final long TTL_MS = 90_000, OWN_TTL_MS = 30_000;

    /** What one player wears, as the site reports it. */
    public record Look(String skin, boolean slim, String cape, String mask) {
        static final Look NONE = new Look(null, false, null, null);
    }

    public enum Kind {
        CAPES("capes"), MASKS("masks"), SKINS("skins");
        public final String dir;
        Kind(String dir) { this.dir = dir; }
    }

    private static final Map<String, Look> looks = new ConcurrentHashMap<>();
    private static final Map<String, Long> fetchedAt = new ConcurrentHashMap<>();
    private static final Map<String, Identifier> textures = new ConcurrentHashMap<>();
    private static final Set<String> loading = ConcurrentHashMap.newKeySet();
    private static final Set<String> failed = ConcurrentHashMap.newKeySet();
    private static final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "DuckVisual-Cosmetics");
        t.setDaemon(true);
        return t;
    });
    private static HttpClient http;
    private static volatile boolean requesting;
    private static long lastPoll;

    /** Catalog bundled with the mod: kind -> (id -> display name), in catalog order. */
    private static final Map<Kind, LinkedHashMap<String, String>> catalog = loadCatalog();

    private CosmeticsManager() {}

    private static Map<Kind, LinkedHashMap<String, String>> loadCatalog() {
        Map<Kind, LinkedHashMap<String, String>> out = new EnumMap<>(Kind.class);
        for (Kind k : Kind.values()) out.put(k, new LinkedHashMap<>());
        try (InputStream in = CosmeticsManager.class.getResourceAsStream("/assets/duckvisual/textures/cosmetics/catalog.json")) {
            if (in == null) return out;
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Kind k : Kind.values()) {
                if (!o.has(k.dir)) continue;
                for (JsonElement e : o.getAsJsonArray(k.dir)) {
                    JsonObject it = e.getAsJsonObject();
                    out.get(k).put(it.get("id").getAsString(), it.get("name").getAsString());
                }
            }
        } catch (Exception e) {
            DuckVisual.LOGGER.warn("Cosmetics catalog: {}", e.toString());
        }
        return out;
    }

    /** Display names of the bundled catalog (used by the "Mine" selectors in the GUI). */
    public static List<String> names(Kind k) {
        return new ArrayList<>(catalog.get(k).values());
    }

    /** Maps a display name from the GUI back to its id. */
    public static String idByName(Kind k, String name) {
        for (Map.Entry<String, String> e : catalog.get(k).entrySet()) {
            if (e.getValue().equalsIgnoreCase(name)) return e.getKey();
        }
        return null;
    }

    public static Look look(String name) {
        if (name == null) return Look.NONE;
        return looks.getOrDefault(name.toLowerCase(Locale.ROOT), Look.NONE);
    }

    // ------------------------------------------------------------------ polling

    /** Called every client tick while a cosmetics module is on: asks the site about new players. */
    public static void poll(MinecraftClient mc) {
        long now = System.currentTimeMillis();
        if (requesting || now - lastPoll < 2000) return;
        lastPoll = now;
        String own = mc.getSession().getUsername();
        List<String> want = new ArrayList<>();
        if (stale(own, now, OWN_TTL_MS)) want.add(own);
        if (mc.getNetworkHandler() != null) {
            for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) {
                String n = e.getProfile().getName();
                if (n != null && !n.equalsIgnoreCase(own) && n.length() <= 16 && stale(n, now, TTL_MS)) want.add(n);
                if (want.size() >= 60) break;
            }
        }
        if (want.isEmpty()) return;
        for (String n : want) fetchedAt.put(n.toLowerCase(Locale.ROOT), now);
        requesting = true;
        io.execute(() -> {
            try {
                request(want);
            } finally {
                requesting = false;
            }
        });
    }

    private static boolean stale(String name, long now, long ttl) {
        Long t = fetchedAt.get(name.toLowerCase(Locale.ROOT));
        return t == null || now - t > ttl;
    }

    private static HttpClient client() {
        if (http == null) {
            http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).followRedirects(HttpClient.Redirect.NORMAL).build();
        }
        return http;
    }

    private static void request(List<String> names) {
        try {
            String q = URLEncoder.encode(String.join(",", names), StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder(URI.create(SITE + "/api/cosmetics.php?names=" + q))
                    .timeout(Duration.ofSeconds(8)).header("User-Agent", "DuckVisual/" + DuckVisual.VERSION).GET().build();
            HttpResponse<String> res = client().send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) return;
            JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
            for (String n : names) {
                String key = n.toLowerCase(Locale.ROOT);
                JsonElement e = o.get(key);
                if (e == null || !e.isJsonObject()) {
                    looks.remove(key);
                    continue;
                }
                JsonObject l = e.getAsJsonObject();
                looks.put(key, new Look(str(l, "skin"), "slim".equals(str(l, "model")), str(l, "cape"), str(l, "mask")));
            }
        } catch (Exception e) {
            // offline or the site is down: keep what we had, try again after the TTL
        }
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    // ------------------------------------------------------------------ textures

    /** Texture of a catalog item or an uploaded skin, or null while it is still downloading. */
    public static Identifier texture(Kind kind, String id) {
        if (id == null) return null;
        boolean upload = kind == Kind.SKINS && UPLOAD.matcher(id).matches();
        if (!upload && !ID.matcher(id).matches()) return null;
        String key = kind.dir + "/" + id;
        Identifier have = textures.get(key);
        if (have != null) return have;
        if (!upload && catalog.get(kind).containsKey(id)) {
            Identifier bundled = Identifier.of("duckvisual", "textures/cosmetics/" + kind.dir + "/" + id + ".png");
            textures.put(key, bundled);
            return bundled;
        }
        if (failed.contains(key) || !loading.add(key)) return null;
        String file = upload ? "u/" + id.substring(2) : kind.dir + "/" + id;
        io.execute(() -> download(kind, key, file));
        return null;
    }

    private static void download(Kind kind, String key, String file) {
        try {
            Path cache = MinecraftClient.getInstance().runDirectory.toPath().resolve("config/duckvisual/cosmetics/" + file + ".png");
            byte[] png;
            if (Files.isRegularFile(cache)) {
                png = Files.readAllBytes(cache);
            } else {
                HttpRequest req = HttpRequest.newBuilder(URI.create(SITE + "/cosmetics/" + file + ".png"))
                        .timeout(Duration.ofSeconds(10)).header("User-Agent", "DuckVisual/" + DuckVisual.VERSION).GET().build();
                HttpResponse<byte[]> res = client().send(req, HttpResponse.BodyHandlers.ofByteArray());
                if (res.statusCode() != 200 || res.body().length > 64 * 1024) {
                    failed.add(key);
                    return;
                }
                png = res.body();
                Files.createDirectories(cache.getParent());
                Files.write(cache, png);
            }
            byte[] data = png;
            MinecraftClient.getInstance().execute(() -> {
                try {
                    NativeImage img = NativeImage.read(data);
                    int w = img.getWidth(), h = img.getHeight();
                    boolean ok = switch (kind) {
                        case SKINS -> w == 64 && (h == 64 || h == 32);
                        case CAPES -> w == 64 && h == 32;
                        case MASKS -> w == 32 && h == 16;
                    };
                    if (!ok) {
                        img.close();
                        failed.add(key);
                        return;
                    }
                    Identifier id = Identifier.of("duckvisual", "dyn/" + key.replace(':', '_'));
                    MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(img));
                    textures.put(key, id);
                } catch (Exception e) {
                    failed.add(key);
                }
            });
        } catch (Exception e) {
            failed.add(key);
        } finally {
            loading.remove(key);
        }
    }

    // ------------------------------------------------------------------ applying

    private static Capes capes;
    private static Masks masks;
    private static Skins skins;

    /** The three modules, looked up once (render code calls in here every frame). */
    private static boolean modules() {
        if (skins != null) return true;
        var mods = DuckVisual.modules();
        if (mods == null) return false;
        capes = mods.get(Capes.class);
        masks = mods.get(Masks.class);
        skins = mods.get(Skins.class);
        return true;
    }

    /** Replaces the skin and cape of a player for rendering. Called a lot: only map lookups here. */
    public static SkinTextures apply(String name, boolean self, SkinTextures orig) {
        if (orig == null || !modules()) return orig;
        Look l = look(name);

        Identifier texture = orig.texture();
        String url = orig.textureUrl();
        SkinTextures.Model model = orig.model();
        boolean changed = false;
        if (skins.isEnabled() && (self || skins.others())) {
            String skin = self ? skins.mine(l.skin()) : l.skin();
            Identifier t = texture(Kind.SKINS, skin);
            if (t != null) {
                texture = t;
                url = null;
                model = self && !skins.usesSite() ? SkinTextures.Model.WIDE : l.slim() ? SkinTextures.Model.SLIM : SkinTextures.Model.WIDE;
                changed = true;
            }
        }
        Identifier cape = orig.capeTexture(), elytra = orig.elytraTexture();
        if (capes.isEnabled() && (self || capes.others())) {
            String id = self ? capes.mine(l.cape()) : l.cape();
            Identifier t = texture(Kind.CAPES, id);
            if (t != null) {
                cape = t;
                if (capes.elytra()) elytra = t;
                changed = true;
            }
        }
        return changed ? new SkinTextures(texture, url, cape, elytra, model, orig.secure()) : orig;
    }

    /** Mask texture for a player being rendered, or null. */
    public static Identifier mask(String name, boolean self) {
        if (name == null || !modules()) return null;
        if (!masks.isEnabled() || (!self && !masks.others())) return null;
        String id = look(name).mask();
        if (self) id = masks.mine(id);
        return texture(Kind.MASKS, id);
    }
}
