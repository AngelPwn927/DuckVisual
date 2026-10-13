package dev.duckvisual.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.duckvisual.DuckVisual;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file = FabricLoader.getInstance().getConfigDir().resolve("duckvisual.json");

    private static final java.util.concurrent.ExecutorService IO = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "DuckVisual-Config");
        t.setDaemon(true);
        return t;
    });

    /** Serializes on the caller thread (cheap) and writes the file in the background. */
    public void saveAsync() {
        String json = GSON.toJson(serialize());
        IO.execute(() -> write(json));
    }

    public void save() {
        write(GSON.toJson(serialize()));
    }

    private void write(String json) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, json);
        } catch (Exception e) {
            DuckVisual.LOGGER.error("Failed to save config", e);
        }
    }

    private JsonObject serialize() {
        JsonObject root = new JsonObject();
        for (Module m : DuckVisual.modules().getModules()) {
            JsonObject o = new JsonObject();
            o.addProperty("enabled", m.isEnabled());
            o.addProperty("key", m.getKey());
            JsonObject settings = new JsonObject();
            for (Setting<?> s : m.getSettings()) settings.add(s.getName(), s.save());
            o.add("settings", settings);
            root.add(m.getName(), o);
        }
        return root;
    }

    private Path profileDir() {
        return file.getParent().resolve("duckvisual");
    }

    private static boolean validName(String name) {
        return name.matches("[A-Za-z0-9_\\-]{1,32}");
    }

    /** Saves the current state as a named profile in config/duckvisual/&lt;name&gt;.json. */
    public boolean saveProfile(String name) {
        if (!validName(name)) return false;
        try {
            Files.createDirectories(profileDir());
            Files.writeString(profileDir().resolve(name + ".json"), GSON.toJson(serialize()));
            return true;
        } catch (Exception e) {
            DuckVisual.LOGGER.error("Failed to save profile", e);
            return false;
        }
    }

    public boolean loadProfile(String name) {
        if (!validName(name)) return false;
        Path p = profileDir().resolve(name + ".json");
        if (!Files.exists(p)) return false;
        boolean ok = load(p);
        if (ok) save();
        return ok;
    }

    public boolean deleteProfile(String name) {
        if (!validName(name)) return false;
        try {
            return Files.deleteIfExists(profileDir().resolve(name + ".json"));
        } catch (Exception e) {
            return false;
        }
    }

    public java.util.List<String> listProfiles() {
        try (var stream = Files.list(profileDir())) {
            return stream.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".json")).map(n -> n.substring(0, n.length() - 5)).sorted().toList();
        } catch (Exception e) {
            return java.util.List.of();
        }
    }

    public boolean load() {
        return load(file);
    }

    private boolean load(Path source) {
        if (!Files.exists(source)) return false;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(source)).getAsJsonObject();
            for (Module m : DuckVisual.modules().getModules()) {
                if (!root.has(m.getName())) continue;
                JsonObject o = root.getAsJsonObject(m.getName());
                if (o.has("key")) m.setKey(o.get("key").getAsInt());
                if (o.has("settings")) {
                    JsonObject settings = o.getAsJsonObject("settings");
                    for (Setting<?> s : m.getSettings()) {
                        JsonElement e = settings.get(s.getName());
                        if (e != null) {
                            try {
                                s.load(e);
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }
                if (o.has("enabled")) m.setEnabledSilent(o.get("enabled").getAsBoolean());
            }
            return true;
        } catch (Exception e) {
            DuckVisual.LOGGER.error("Failed to load config", e);
            return false;
        }
    }
}
