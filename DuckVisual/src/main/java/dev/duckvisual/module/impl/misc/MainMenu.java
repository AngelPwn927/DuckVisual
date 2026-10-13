package dev.duckvisual.module.impl.misc;

import dev.duckvisual.DuckVisual;
import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.module.impl.hud.Watermark;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.setting.ModeSetting;
import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Glass;
import dev.duckvisual.util.Render2D;
import net.minecraft.client.gui.DrawContext;

import java.util.Random;

/**
 * Duck Visual title screen (applied in TitleScreenMixin): the Minecraft panorama behind frosted
 * glass with rain or snow, a centered column of glass buttons with icons and link buttons, in the
 * macOS style. "Animated" keeps the older gradient background with glowing dots.
 */
public class MainMenu extends Module {
    public final ModeSetting background = add(new ModeSetting("Background", "Panorama", "Panorama", "Animated"));
    public final ModeSetting weather = add(new ModeSetting("Weather", "Rain", "Rain", "Snow", "None"));
    public final BooleanSetting particles = add(new BooleanSetting("Particles", true).visibleWhen(() -> background.is("Animated")));
    public final BooleanSetting hideSplash = add(new BooleanSetting("Hide Splash", true));

    /** Top of the button column, set by TitleScreenMixin when it lays the buttons out. */
    public static int menuTop = 120;

    private final float[][] dots = new float[70][];
    private final float[][] drops = new float[140][];
    private final long start = System.currentTimeMillis();

    public MainMenu() {
        super("MainMenu", "Duck Visual title screen", Category.MISC);
        Random r = new Random(7);
        for (int i = 0; i < dots.length; i++) {
            // x, y (0..1), speed, size, phase
            dots[i] = new float[]{r.nextFloat(), r.nextFloat(), 0.01f + r.nextFloat() * 0.03f, 1 + r.nextFloat() * 2.5f, r.nextFloat() * 6.28f};
        }
        for (int i = 0; i < drops.length; i++) {
            // x, y (0..1), speed (screens per second), length / size, alpha
            drops[i] = new float[]{r.nextFloat() * 1.3f, r.nextFloat(), 0.55f + r.nextFloat() * 0.5f, 8 + r.nextFloat() * 16, 40 + r.nextFloat() * 90};
        }
    }

    @Override
    public boolean isHidden() {
        return true;
    }

    public boolean panorama() {
        return background.is("Panorama");
    }

    /** Old animated background (drawn instead of the panorama). */
    public void background(DrawContext ctx, int w, int h) {
        double t = (System.currentTimeMillis() - start) / 1000.0;
        Render2D.beginBatch(ctx);
        Render2D.gradient(ctx, 0, 0, w, h, 0xFF0B0B12, 0xFF101018, 0xFF07070C, 0xFF0C0C14);
        float gx = (float) (w * (0.3 + 0.15 * Math.sin(t * 0.3))), gy = (float) (h * (0.35 + 0.1 * Math.cos(t * 0.25)));
        Render2D.glowDot(ctx, gx, gy, Math.max(w, h) * 0.55f, ColorUtil.withAlpha(ColorUtil.theme(0), 70));
        float hx = (float) (w * (0.72 + 0.12 * Math.cos(t * 0.27))), hy = (float) (h * (0.7 + 0.1 * Math.sin(t * 0.33)));
        Render2D.glowDot(ctx, hx, hy, Math.max(w, h) * 0.45f, ColorUtil.withAlpha(ColorUtil.theme(10), 55));
        if (particles.get()) {
            for (int i = 0; i < dots.length; i++) {
                float[] d = dots[i];
                float y = (float) ((d[1] - t * d[2]) % 1.0);
                if (y < 0) y += 1;
                float x = d[0] + (float) Math.sin(t * 0.5 + d[4]) * 0.01f;
                float tw = 0.5f + 0.5f * (float) Math.sin(t * 2 + d[4]);
                Render2D.glowDot(ctx, x * w, y * h, d[3] * 2.2f, ColorUtil.withAlpha(ColorUtil.theme(i), (int) (60 + 140 * tw)));
            }
        }
        weather(ctx, w, h, t);
        Render2D.gradient(ctx, 0, h * 0.75f, w, h * 0.25f, 0x00000000, 0x00000000, 0x90000000, 0x90000000);
        Render2D.endBatch(ctx);
    }

    /** Drawn over the vanilla panorama: frosted glass, weather and a soft vignette. */
    public void overlay(DrawContext ctx, int w, int h) {
        double t = (System.currentTimeMillis() - start) / 1000.0;
        Glass.capture(ctx);
        Render2D.beginBatch(ctx);
        Glass.panel(ctx, -2, -2, w + 4, h + 4, 0, 0x70101624, 1);
        weather(ctx, w, h, t);
        Render2D.gradient(ctx, 0, 0, w, h * 0.3f, 0x70000000, 0x70000000, 0x00000000, 0x00000000);
        Render2D.gradient(ctx, 0, h * 0.7f, w, h * 0.3f, 0x00000000, 0x00000000, 0x80000000, 0x80000000);
        Render2D.endBatch(ctx);
    }

    private void weather(DrawContext ctx, int w, int h, double t) {
        if (weather.is("Rain")) {
            for (float[] d : drops) {
                float y = (float) ((d[1] + t * d[2]) % 1.0) * (h + 60) - 30;
                float x = d[0] * w - y * 0.28f;
                float len = d[3];
                Render2D.line(ctx, x, y, x - len * 0.28f, y - len, 0.8f, ColorUtil.withAlpha(0xFFD8E4FF, (int) d[4]));
            }
        } else if (weather.is("Snow")) {
            for (int i = 0; i < drops.length; i++) {
                float[] d = drops[i];
                float y = (float) ((d[1] + t * d[2] * 0.12) % 1.0) * (h + 20) - 10;
                float x = (float) (d[0] / 1.3f * w + Math.sin(t * 0.8 + i) * 8);
                Render2D.circle(ctx, x, y, 0.6f + d[3] / 24f * 1.2f, ColorUtil.withAlpha(0xFFFFFFFF, (int) (d[4] * 1.4f)));
            }
        }
    }

    public void logo(DrawContext ctx, int screenWidth, float alpha) {
        String title = "Duck Visual";
        float scale = 2.6f;
        float tw = Render2D.width(title) * scale;
        float duckSize = 26;
        float total = duckSize + 8 + tw;
        float x = screenWidth / 2f - total / 2, y = Math.max(8, menuTop - 52);
        double bob = Math.sin((System.currentTimeMillis() - start) / 600.0) * 1.5;
        Watermark.duck(ctx, x, (float) (y + bob), duckSize);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x + duckSize + 8, y + 3, 0);
        ctx.getMatrices().scale(scale, scale, 1);
        Render2D.gradientText(ctx, title, 0, 0, 0);
        ctx.getMatrices().pop();
        String sub = "Спасибо за использование Duck Visual";
        Render2D.centeredText(ctx, sub, screenWidth / 2f, y + 32, ColorUtil.withAlpha(0xFFC4C6D0, (int) (255 * alpha)));
    }

    public String version() {
        return "Duck Visual " + DuckVisual.VERSION + " · Minecraft 1.21.4";
    }
}
