package dev.duckvisual.util;

/** Frame-rate independent exponential smoothing. */
public final class Animation {
    private Animation() {}

    private static long last = System.nanoTime();
    private static float frameDelta = 0.016f;

    public static void updateFrame() {
        long now = System.nanoTime();
        frameDelta = Math.min(0.1f, (now - last) / 1_000_000_000f);
        last = now;
    }

    public static float delta() {
        return frameDelta;
    }

    public static float approach(float current, float target, float speed) {
        float f = 1f - (float) Math.exp(-speed * frameDelta);
        float v = current + (target - current) * f;
        return Math.abs(target - v) < 0.001f ? target : v;
    }

    public static float easeOutCubic(float t) {
        t = Math.max(0, Math.min(1, t));
        return 1 - (float) Math.pow(1 - t, 3);
    }

    public static float easeInOutQuad(float t) {
        t = Math.max(0, Math.min(1, t));
        return t < 0.5f ? 2 * t * t : 1 - (float) Math.pow(-2 * t + 2, 2) / 2;
    }
}
