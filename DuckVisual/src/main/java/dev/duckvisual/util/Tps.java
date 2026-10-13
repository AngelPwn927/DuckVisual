package dev.duckvisual.util;

/**
 * Server TPS measured on the client: the server sends the world time every 20 of its ticks,
 * so 20 ticks divided by the real time between two packets is its tick rate.
 */
public final class Tps {
    private static final float[] samples = new float[5];
    private static int count, index;
    private static long last;

    private Tps() {}

    /** Called on the main thread for every world time packet. */
    public static void onTimeUpdate() {
        long now = System.nanoTime();
        if (last != 0) {
            float seconds = (now - last) / 1e9f;
            if (seconds > 0.05f) {
                samples[index] = Math.min(20f, 20f / seconds);
                index = (index + 1) % samples.length;
                count = Math.min(count + 1, samples.length);
            }
        }
        last = now;
    }

    public static void reset() {
        count = index = 0;
        last = 0;
    }

    public static float get() {
        if (count == 0) return 20f;
        float s = 0;
        for (int i = 0; i < count; i++) s += samples[i];
        return s / count;
    }
}
