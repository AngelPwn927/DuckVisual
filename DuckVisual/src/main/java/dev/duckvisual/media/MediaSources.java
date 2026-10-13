package dev.duckvisual.media;

import java.util.Locale;

/** Turns player/app identifiers (Windows AUMIDs, MPRIS names) into readable names. */
public final class MediaSources {
    private MediaSources() {}

    public static String pretty(String id) {
        if (id == null || id.isBlank()) return "Media";
        String s = id.toLowerCase(Locale.ROOT);
        if (s.contains("spotify")) return "Spotify";
        if (s.contains("yandex.music") || s.contains("yandexmusic") || s.contains("yandex music")) return "Яндекс Музыка";
        if (s.contains("yandex") || s.contains("browser.exe")) return "Яндекс Браузер";
        if (s.contains("soundcloud")) return "SoundCloud";
        if (s.contains("chrome")) return "Chrome";
        if (s.contains("msedge") || s.contains("edge")) return "Edge";
        // Firefox registers itself with this AUMID hash on Windows
        if (s.contains("firefox") || s.contains("308046b0af4a39cb")) return "Firefox";
        if (s.contains("opera")) return "Opera";
        if (s.contains("brave")) return "Brave";
        if (s.contains("vivaldi")) return "Vivaldi";
        if (s.contains("applemusic") || s.contains("apple music") || s.contains("itunes") || s.equals("music")) return "Apple Music";
        if (s.contains("aimp")) return "AIMP";
        if (s.contains("foobar")) return "foobar2000";
        if (s.contains("musicbee")) return "MusicBee";
        if (s.contains("vlc")) return "VLC";
        if (s.contains("deezer")) return "Deezer";
        if (s.contains("tidal")) return "TIDAL";
        if (s.contains("vk")) return "VK Музыка";
        if (s.contains("zunemusic") || s.contains("mediaplayer")) return "Media Player";
        if (s.contains("discord")) return "Discord";
        if (s.contains("telegram")) return "Telegram";
        if (s.contains("youtube")) return "YouTube";
        if (s.contains("rhythmbox")) return "Rhythmbox";
        if (s.contains("mpv")) return "mpv";
        // strip common decorations: "Foo.exe", "Company.App_hash!App"
        String name = id;
        int bang = name.indexOf('!');
        if (bang > 0) name = name.substring(0, bang);
        int us = name.indexOf('_');
        if (us > 0) name = name.substring(0, us);
        if (name.toLowerCase(Locale.ROOT).endsWith(".exe")) name = name.substring(0, name.length() - 4);
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1) name = name.substring(dot + 1);
        return name.isEmpty() ? "Media" : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /** Accent color for a source (used for the small badge in the HUD). */
    public static int color(String pretty) {
        return switch (pretty) {
            case "Spotify" -> 0xFF1DB954;
            case "Яндекс Музыка", "Яндекс Браузер" -> 0xFFFFCC00;
            case "SoundCloud" -> 0xFFFF5500;
            case "YouTube", "Chrome" -> 0xFFFF3B3B;
            case "Firefox" -> 0xFFFF7139;
            case "Edge" -> 0xFF2FA3EE;
            case "Opera" -> 0xFFFF1B2D;
            case "Apple Music" -> 0xFFFA2D48;
            case "VK Музыка" -> 0xFF2787F5;
            case "Deezer" -> 0xFFA238FF;
            case "AIMP" -> 0xFF3AA0FF;
            default -> 0xFFB0B0BA;
        };
    }
}
