package dev.duckvisual.media;

/**
 * Snapshot of what is currently playing.
 *
 * @param position  seconds at {@code sampledAt}
 * @param duration  seconds, 0 when the player does not report a timeline (many browser sites)
 * @param sampledAt local {@link System#currentTimeMillis()} when {@code position} was valid
 * @param cover     path of a cover image file, or null
 */
public record MediaInfo(String title, String artist, String album, String source, boolean playing,
                        double position, double duration, long sampledAt, String cover) {

    public double currentPosition() {
        if (!playing) return position;
        double p = position + (System.currentTimeMillis() - sampledAt) / 1000.0;
        return duration > 0 ? Math.min(p, duration) : p;
    }
}
