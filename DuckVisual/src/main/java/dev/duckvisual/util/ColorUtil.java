package dev.duckvisual.util;

import dev.duckvisual.DuckVisual;
import dev.duckvisual.module.impl.misc.Theme;

public final class ColorUtil {
    private ColorUtil() {}

    public static int alpha(int c) { return (c >>> 24) & 255; }
    public static int red(int c) { return (c >> 16) & 255; }
    public static int green(int c) { return (c >> 8) & 255; }
    public static int blue(int c) { return c & 255; }

    public static int rgba(int r, int g, int b, int a) {
        return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    public static int withAlpha(int c, int a) {
        return (clamp(a) << 24) | (c & 0xFFFFFF);
    }

    /** Multiplies the existing alpha by {@code f}. */
    public static int fade(int c, float f) {
        return withAlpha(c, (int) (alpha(c) * Math.max(0, Math.min(1, f))));
    }

    public static int lerp(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        return rgba(
                (int) (red(a) + (red(b) - red(a)) * t),
                (int) (green(a) + (green(b) - green(a)) * t),
                (int) (blue(a) + (blue(b) - blue(a)) * t),
                (int) (alpha(a) + (alpha(b) - alpha(a)) * t));
    }

    /** Two-color theme wave. {@code index} shifts the phase so lists get a running gradient. */
    public static int theme(int index) {
        Theme theme = DuckVisual.modules().get(Theme.class);
        double speed = theme.speed.get();
        double t = (System.currentTimeMillis() / (1000.0 / speed) + index * 0.08) % 2.0;
        float f = (float) (t > 1 ? 2 - t : t);
        if (theme.mode.is("Rainbow")) {
            float hue = (float) ((System.currentTimeMillis() * speed / 4000.0 + index * 0.03) % 1.0);
            return 0xFF000000 | java.awt.Color.HSBtoRGB(hue, 0.6f, 1f);
        }
        if (theme.mode.is("Static")) return 0xFF000000 | (theme.firstColor() & 0xFFFFFF);
        return lerp(0xFF000000 | theme.firstColor(), 0xFF000000 | theme.secondColor(), f);
    }

    public static int theme() {
        return theme(0);
    }

    public static float[] toFloats(int c) {
        return new float[]{red(c) / 255f, green(c) / 255f, blue(c) / 255f, alpha(c) / 255f};
    }
}
