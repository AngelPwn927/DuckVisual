package dev.duckvisual.module.impl.misc;

import dev.duckvisual.DuckVisual;
import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.module.impl.hud.Watermark;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Render2D;
import net.minecraft.client.gui.DrawContext;

import java.util.Random;

/** Animated Duck Visual title screen background and logo (applied in TitleScreenMixin). */
public class MainMenu extends Module {
    public final BooleanSetting particles = add(new BooleanSetting("Particles", true));
    public final BooleanSetting hideSplash = add(new BooleanSetting("Hide Splash", true));

    private final float[][] dots = new float[70][];
    private final long start = System.currentTimeMillis();

    public MainMenu() {
        super("MainMenu", "Animated Duck Visual title screen", Category.MISC);
        Random r = new Random(7);
        for (int i = 0; i < dots.length; i++) {
            // x, y (0..1), speed, size, phase
            dots[i] = new float[]{r.nextFloat(), r.nextFloat(), 0.01f + r.nextFloat() * 0.03f, 1 + r.nextFloat() * 2.5f, r.nextFloat() * 6.28f};
        }
    }

    @Override
    public boolean isHidden() {
        return true;
    }

    public void background(DrawContext ctx, int w, int h) {
        double t = (System.currentTimeMillis() - start) / 1000.0;
        Render2D.gradient(ctx, 0, 0, w, h, 0xFF0B0B12, 0xFF101018, 0xFF07070C, 0xFF0C0C14);
        // two slow drifting colored glows
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
        // vignette
        Render2D.gradient(ctx, 0, h * 0.75f, w, h * 0.25f, 0x00000000, 0x00000000, 0x90000000, 0x90000000);
    }

    public void logo(DrawContext ctx, int screenWidth, float alpha) {
        String title = "Duck Visual";
        float scale = 3.2f;
        float tw = Render2D.width(title) * scale;
        float duckSize = 34;
        float total = duckSize + 10 + tw;
        float x = screenWidth / 2f - total / 2, y = 30;
        double bob = Math.sin((System.currentTimeMillis() - start) / 600.0) * 2;
        Watermark.duck(ctx, x, (float) (y + bob), duckSize);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x + duckSize + 10, y + 5, 0);
        ctx.getMatrices().scale(scale, scale, 1);
        Render2D.gradientText(ctx, title, 0, 0, 0);
        ctx.getMatrices().pop();
        String sub = "v" + DuckVisual.VERSION + "  ·  Minecraft 1.21.4  ·  Right Shift - menu";
        Render2D.centeredText(ctx, sub, screenWidth / 2f, y + 42, ColorUtil.withAlpha(0xFFA0A0AA, (int) (255 * alpha)));
        ctx.getMatrices().push();
        ctx.getMatrices().translate(screenWidth / 2f, y + 55, 0);
        ctx.getMatrices().scale(0.75f, 0.75f, 1);
        Render2D.centeredText(ctx, "\u041D\u0435 \u044F\u0432\u043B\u044F\u0435\u0442\u0441\u044F \u043F\u0440\u043E\u0434\u0443\u043A\u0442\u043E\u043C Mojang AB  \u00B7  t.me/duckvisual",
                0, 0, ColorUtil.withAlpha(0xFF707080, (int) (255 * alpha)));
        ctx.getMatrices().pop();
    }
}
