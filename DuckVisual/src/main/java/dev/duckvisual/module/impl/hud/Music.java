package dev.duckvisual.module.impl.hud;

import dev.duckvisual.media.MediaInfo;
import dev.duckvisual.media.MediaService;
import dev.duckvisual.media.MediaSources;
import dev.duckvisual.module.HudModule;
import dev.duckvisual.setting.BindSetting;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.util.Animation;
import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Keys;
import dev.duckvisual.util.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.CompletableFuture;

/** Now playing: cover, title, artist, progress and an equalizer, from browsers / Spotify / etc. */
public class Music extends HudModule {
    private final BooleanSetting cover = add(new BooleanSetting("Cover", true));
    private final BooleanSetting progress = add(new BooleanSetting("Progress", true));
    private final BooleanSetting source = add(new BooleanSetting("Show Source", true));
    private final BooleanSetting hideIdle = add(new BooleanSetting("Hide When Idle", false));
    private final BindSetting playPause = add(new BindSetting("Play/Pause", -1));
    private final BindSetting next = add(new BindSetting("Next", -1));
    private final BindSetting previous = add(new BindSetting("Previous", -1));

    private static final Identifier COVER_ID = Identifier.of("duckvisual", "music_cover");
    private static final int COVER_SIZE = 64;

    private String loadedCover;
    private CompletableFuture<int[]> pendingCover;
    private String pendingPath;
    private boolean hasCover;
    private NativeImageBackedTexture coverTexture;

    private float appear;
    private float scroll;
    private final float[] bars = new float[5];
    private final boolean[] held = new boolean[3];

    public Music() {
        super("Music", "Now playing from browsers, Spotify and more", 5, 160);
    }

    @Override
    public boolean isHidden() {
        return true;
    }

    @Override
    public void onEnable() {
        MediaService.start();
    }

    @Override
    public void onDisable() {
        MediaService.stop();
    }

    @Override
    public void onTick() {
        long w = mc.getWindow().getHandle();
        BindSetting[] binds = {playPause, next, previous};
        String[] cmds = {"playpause", "next", "previous"};
        for (int i = 0; i < 3; i++) {
            boolean down = mc.currentScreen == null && Keys.isDown(w, binds[i].get());
            if (down && !held[i]) MediaService.command(cmds[i]);
            held[i] = down;
        }
    }

    @Override
    public void onRender2D(DrawContext ctx, float td) {
        MediaInfo m = MediaService.current();
        boolean editing = mc.currentScreen instanceof dev.duckvisual.gui.ClickGuiScreen;
        boolean show = m != null || !hideIdle.get() || editing;
        appear = Animation.approach(appear, show ? 1 : 0, 10);
        updateCover(m);
        width = 170;
        height = 46;
        if (appear < 0.02f) return;

        float x = getX(), y = getY(), a = appear;
        Render2D.shadow(ctx, x, y, width, height, 6, 5, ColorUtil.withAlpha(ColorUtil.theme(), (int) (80 * a)));
        Render2D.roundedRect(ctx, x, y, width, height, 6, ColorUtil.withAlpha(0xFF111116, (int) (230 * a)));

        float cx = x + 5, cy = y + 5, cs = 36;
        if (cover.get() && hasCover && m != null) {
            ctx.drawTexture(RenderLayer::getGuiTextured, COVER_ID, (int) cx, (int) cy, 0, 0, (int) cs, (int) cs,
                    (int) cs, (int) cs, (int) cs, (int) cs, ColorUtil.withAlpha(0xFFFFFFFF, (int) (255 * a)));
        } else {
            Render2D.roundedGradient(ctx, cx, cy, cs, cs, 5, ColorUtil.fade(ColorUtil.theme(0), a), ColorUtil.fade(ColorUtil.theme(8), a),
                    ColorUtil.fade(ColorUtil.theme(8), a), ColorUtil.fade(ColorUtil.theme(0), a));
            ctx.getMatrices().push();
            ctx.getMatrices().translate(cx + cs / 2 - 7, cy + cs / 2 - 9, 0);
            ctx.getMatrices().scale(2, 2, 1);
            Render2D.text(ctx, "♫", 0, 0, ColorUtil.fade(0xFF111111, a));
            ctx.getMatrices().pop();
        }

        float tx = cx + cs + 6, tw = x + width - 6 - tx;
        if (m == null) {
            Render2D.text(ctx, "Music", tx, y + 9, ColorUtil.fade(0xFFFFFFFF, a));
            String st = MediaService.status();
            ctx.getMatrices().push();
            ctx.getMatrices().translate(tx, y + 22, 0);
            ctx.getMatrices().scale(0.75f, 0.75f, 1);
            Render2D.text(ctx, st.isEmpty() ? "Nothing is playing" : st, 0, 0, ColorUtil.fade(0xFF9A9AA5, a));
            ctx.getMatrices().pop();
            return;
        }

        // equalizer bars next to the source badge
        for (int i = 0; i < bars.length; i++) {
            float target = m.playing() ? 0.25f + 0.75f * (float) Math.abs(Math.sin(System.currentTimeMillis() / (140.0 + i * 37) + i)) : 0.15f;
            bars[i] = Animation.approach(bars[i], target, 12);
        }

        // title (marquee when too long)
        int scx1 = (int) tx, scx2 = (int) (x + width - 22);
        String title = m.title();
        float titleW = Render2D.width(title);
        float avail = scx2 - scx1;
        float offset = 0;
        if (titleW > avail) {
            scroll += Animation.delta() * 22;
            float cycle = titleW + 30;
            offset = -(scroll % cycle);
        } else scroll = 0;
        ctx.enableScissor(scx1, (int) y + 4, scx2, (int) y + 18);
        Render2D.text(ctx, title, tx + offset, y + 6, ColorUtil.fade(0xFFFFFFFF, a));
        if (titleW > avail) Render2D.text(ctx, title, tx + offset + titleW + 30, y + 6, ColorUtil.fade(0xFFFFFFFF, a));
        ctx.disableScissor();
        for (int i = 0; i < bars.length; i++) {
            float bh = 9 * bars[i];
            Render2D.rect(ctx, x + width - 19 + i * 3, y + 15 - bh, 2, bh, ColorUtil.fade(ColorUtil.theme(i * 3), a));
        }

        // artist + source
        String artist = m.artist().isBlank() ? m.album() : m.artist();
        ctx.getMatrices().push();
        ctx.getMatrices().translate(tx, y + 18, 0);
        ctx.getMatrices().scale(0.8f, 0.8f, 1);
        String line = artist;
        float maxW = tw / 0.8f;
        if (source.get()) maxW -= Render2D.width(m.source()) + 8;
        while (Render2D.width(line) > maxW && line.length() > 3) line = line.substring(0, line.length() - 4) + "...";
        Render2D.text(ctx, line, 0, 0, ColorUtil.fade(0xFFB0B0BA, a));
        if (source.get()) {
            float sx = tw / 0.8f - Render2D.width(m.source());
            int sc = MediaSources.color(m.source());
            Render2D.text(ctx, m.source(), sx, 0, ColorUtil.fade(sc, a));
        }
        ctx.getMatrices().pop();

        // progress
        if (progress.get()) {
            float py = y + 33;
            if (m.duration() > 0) {
                double pos = m.currentPosition();
                float f = (float) Math.max(0, Math.min(1, pos / m.duration()));
                Render2D.roundedRect(ctx, tx, py, tw, 3, 1.5f, ColorUtil.withAlpha(0xFF2A2A33, (int) (255 * a)));
                Render2D.roundedGradient(ctx, tx, py, Math.max(3, tw * f), 3, 1.5f, ColorUtil.fade(ColorUtil.theme(0), a),
                        ColorUtil.fade(ColorUtil.theme(8), a), ColorUtil.fade(ColorUtil.theme(8), a), ColorUtil.fade(ColorUtil.theme(0), a));
                ctx.getMatrices().push();
                ctx.getMatrices().translate(tx, py + 4.5f, 0);
                ctx.getMatrices().scale(0.65f, 0.65f, 1);
                Render2D.text(ctx, time(pos), 0, 0, ColorUtil.fade(0xFF9A9AA5, a));
                String d = time(m.duration());
                Render2D.text(ctx, d, tw / 0.65f - Render2D.width(d), 0, ColorUtil.fade(0xFF9A9AA5, a));
                ctx.getMatrices().pop();
            } else {
                ctx.getMatrices().push();
                ctx.getMatrices().translate(tx, py, 0);
                ctx.getMatrices().scale(0.65f, 0.65f, 1);
                Render2D.text(ctx, m.playing() ? "▶ Playing" : "❚❚ Paused", 0, 0, ColorUtil.fade(ColorUtil.theme(), a));
                ctx.getMatrices().pop();
            }
        }
    }

    private static String time(double s) {
        int t = (int) Math.max(0, s);
        return t / 60 + ":" + String.format("%02d", t % 60);
    }

    /** Decodes the cover file off-thread (PNG/JPEG via ImageIO) and uploads it on the render thread. */
    private void updateCover(MediaInfo m) {
        String path = m == null ? null : m.cover();
        if (path == null) {
            hasCover = false;
            loadedCover = null;
            return;
        }
        if (path.equals(loadedCover) && pendingCover == null) return;
        if (pendingCover == null || !path.equals(pendingPath)) {
            pendingPath = path;
            pendingCover = CompletableFuture.supplyAsync(() -> decode(path));
        }
        if (!pendingCover.isDone()) return;
        int[] px = pendingCover.join();
        pendingCover = null;
        loadedCover = path;
        if (px == null) {
            hasCover = false;
            return;
        }
        NativeImage img = new NativeImage(COVER_SIZE, COVER_SIZE, false);
        for (int yy = 0; yy < COVER_SIZE; yy++)
            for (int xx = 0; xx < COVER_SIZE; xx++) img.setColorArgb(xx, yy, px[yy * COVER_SIZE + xx]);
        if (coverTexture == null) {
            coverTexture = new NativeImageBackedTexture(img);
            mc.getTextureManager().registerTexture(COVER_ID, coverTexture);
        } else {
            coverTexture.setImage(img);
            coverTexture.upload();
        }
        hasCover = true;
    }

    private static int[] decode(String path) {
        try {
            File f = new File(path);
            if (!f.isFile()) return null;
            BufferedImage src = ImageIO.read(f);
            if (src == null) return null;
            // center-crop to a square, then scale
            int side = Math.min(src.getWidth(), src.getHeight());
            BufferedImage sq = src.getSubimage((src.getWidth() - side) / 2, (src.getHeight() - side) / 2, side, side);
            BufferedImage out = new BufferedImage(COVER_SIZE, COVER_SIZE, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(sq, 0, 0, COVER_SIZE, COVER_SIZE, null);
            g.dispose();
            int[] px = new int[COVER_SIZE * COVER_SIZE];
            out.getRGB(0, 0, COVER_SIZE, COVER_SIZE, px, 0, COVER_SIZE);
            return px;
        } catch (Exception e) {
            return null;
        }
    }
}
