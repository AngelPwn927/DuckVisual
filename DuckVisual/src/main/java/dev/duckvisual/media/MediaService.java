package dev.duckvisual.media;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.duckvisual.DuckVisual;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Background "now playing" reader. One daemon thread per OS:
 * <ul>
 *     <li>Windows: a hidden PowerShell process reading the System Media Transport Controls
 *     (covers browsers, Spotify, Yandex Music, AIMP, foobar2000, Apple Music, ...)</li>
 *     <li>Linux: {@code playerctl} (any MPRIS player, including browsers)</li>
 *     <li>macOS: {@code osascript} for Spotify and Apple Music</li>
 * </ul>
 */
public final class MediaService {
    private static volatile MediaInfo current;
    private static volatile String status = "Starting...";
    private static volatile boolean running;
    private static Thread thread;
    private static Process process;
    private static Path workDir;

    private MediaService() {}

    public static MediaInfo current() {
        return current;
    }

    /** Human readable state for the HUD when nothing is playing (e.g. "playerctl not installed"). */
    public static String status() {
        return status;
    }

    public static synchronized void start() {
        if (running) return;
        running = true;
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Runnable loop = os.contains("win") ? MediaService::windowsLoop
                : os.contains("mac") ? MediaService::macLoop
                : MediaService::linuxLoop;
        thread = new Thread(loop, "DuckVisual-Media");
        thread.setDaemon(true);
        thread.start();
    }

    public static synchronized void stop() {
        running = false;
        if (process != null) process.destroyForcibly();
        process = null;
        if (thread != null) thread.interrupt();
        thread = null;
        current = null;
    }

    /** playpause / next / previous */
    public static void command(String cmd) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("win")) {
                if (workDir != null) Files.writeString(workDir.resolve("command.txt"), cmd);
            } else if (os.contains("mac")) {
                MediaInfo m = current;
                String app = m != null && m.source().equals("Spotify") ? "Spotify" : "Music";
                String verb = switch (cmd) {
                    case "next" -> "next track";
                    case "previous" -> "previous track";
                    default -> "playpause";
                };
                new ProcessBuilder("osascript", "-e", "tell application \"" + app + "\" to " + verb).start();
            } else {
                String verb = switch (cmd) {
                    case "next" -> "next";
                    case "previous" -> "previous";
                    default -> "play-pause";
                };
                new ProcessBuilder("playerctl", verb).start();
            }
        } catch (Exception e) {
            DuckVisual.LOGGER.debug("media command failed", e);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------------ Windows

    private static void windowsLoop() {
        while (running) {
            try {
                workDir = Files.createTempDirectory("duckvisual-media");
                Path script = workDir.resolve("media.ps1");
                try (InputStream in = MediaService.class.getResourceAsStream("/duckvisual/media_windows.ps1")) {
                    if (in == null) throw new IllegalStateException("bridge script missing");
                    Files.copy(in, script, StandardCopyOption.REPLACE_EXISTING);
                }
                String sysRoot = System.getenv().getOrDefault("SystemRoot", "C:\\Windows");
                Path ps = Path.of(sysRoot, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
                String exe = Files.exists(ps) ? ps.toString() : "powershell.exe";
                ProcessBuilder pb = new ProcessBuilder(exe, "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                        "-WindowStyle", "Hidden", "-File", script.toString(),
                        "-CommandFile", workDir.resolve("command.txt").toString(), "-ThumbDir", workDir.toString());
                pb.redirectErrorStream(true);
                process = pb.start();
                status = "Waiting for a player...";
                try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while (running && (line = r.readLine()) != null) parseWindows(line);
                }
                if (running) status = "Media bridge stopped, restarting...";
            } catch (Exception e) {
                status = "Media not available: " + e.getMessage();
                DuckVisual.LOGGER.warn("Music HUD (Windows) failed", e);
            }
            if (process != null) process.destroyForcibly();
            current = null;
            sleep(5000);
        }
    }

    private static void parseWindows(String line) {
        line = line.replace("\uFEFF", "").trim();
        if (!line.startsWith("{")) {
            if (!line.isEmpty()) DuckVisual.LOGGER.debug("media bridge: {}", line);
            return;
        }
        try {
            JsonObject o = JsonParser.parseString(line).getAsJsonObject();
            if (o.has("error")) {
                status = "Media error: " + o.get("error").getAsString();
                return;
            }
            if (o.has("none")) {
                current = null;
                status = "Nothing is playing";
                return;
            }
            String title = str(o, "title");
            if (title.isBlank()) {
                current = null;
                status = "Nothing is playing";
                return;
            }
            boolean playing = "Playing".equalsIgnoreCase(str(o, "status"));
            double pos = num(o, "position"), dur = num(o, "duration");
            // timeline position is valid at "updated"; convert to local time
            long updated = o.has("updated") ? o.get("updated").getAsLong() : System.currentTimeMillis();
            long psNow = o.has("now") ? o.get("now").getAsLong() : System.currentTimeMillis();
            long sampledAt = System.currentTimeMillis() - Math.max(0, psNow - updated);
            if (updated <= 0 || psNow - updated > 24L * 3600 * 1000) sampledAt = System.currentTimeMillis();
            String thumb = str(o, "thumb");
            current = new MediaInfo(title, str(o, "artist"), str(o, "album"), MediaSources.pretty(str(o, "app")),
                    playing, pos, dur, sampledAt, thumb.isBlank() ? null : thumb);
            status = "";
        } catch (Exception e) {
            DuckVisual.LOGGER.debug("bad media line {}", line, e);
        }
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    private static double num(JsonObject o, String k) {
        try {
            return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsDouble() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------ Linux (MPRIS)

    private static final String SEP = "\u001F";

    private static void linuxLoop() {
        while (running) {
            try {
                Process p = new ProcessBuilder("playerctl", "metadata", "--format",
                        String.join(SEP, "{{status}}", "{{title}}", "{{artist}}", "{{album}}", "{{playerName}}",
                                "{{position}}", "{{mpris:length}}", "{{mpris:artUrl}}"))
                        .redirectErrorStream(true).start();
                String out;
                try (InputStream in = p.getInputStream()) {
                    out = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
                }
                p.waitFor(2, TimeUnit.SECONDS);
                String[] f = out.split(SEP, -1);
                if (p.exitValue() != 0 || f.length < 8 || f[1].isBlank()) {
                    current = null;
                    status = "Nothing is playing";
                } else {
                    String art = f[7].startsWith("file://") ? java.net.URLDecoder.decode(f[7].substring(7), StandardCharsets.UTF_8) : null;
                    current = new MediaInfo(f[1], f[2], f[3], MediaSources.pretty(f[4]), "Playing".equals(f[0]),
                            parse(f[5]) / 1_000_000.0, parse(f[6]) / 1_000_000.0, System.currentTimeMillis(), art);
                    status = "";
                }
            } catch (java.io.IOException e) {
                current = null;
                status = "Install playerctl to show music";
                sleep(10000);
            } catch (Exception e) {
                current = null;
                status = "Media not available";
            }
            sleep(1000);
        }
    }

    private static double parse(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------ macOS

    private static final String MAC_SCRIPT = """
            on readApp(appName)
              if application appName is running then
                tell application appName
                  if player state is playing or player state is paused then
                    set t to name of current track
                    set a to artist of current track
                    set al to album of current track
                    set d to duration of current track
                    set p to player position
                    set s to (player state as string)
                    return appName & "\u001F" & s & "\u001F" & t & "\u001F" & a & "\u001F" & al & "\u001F" & p & "\u001F" & d
                  end if
                end tell
              end if
              return ""
            end readApp
            set r to readApp("Spotify")
            if r is "" then set r to readApp("Music")
            return r
            """;

    private static void macLoop() {
        while (running) {
            try {
                Process p = new ProcessBuilder("osascript", "-e", MAC_SCRIPT).redirectErrorStream(true).start();
                String out;
                try (InputStream in = p.getInputStream()) {
                    out = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
                }
                String[] f = out.split(SEP, -1);
                if (f.length < 7 || f[2].isBlank()) {
                    current = null;
                    status = "Nothing is playing";
                } else {
                    double dur = parse(f[6].replace(',', '.'));
                    // Spotify reports milliseconds, Music seconds
                    if (f[0].equals("Spotify")) dur /= 1000.0;
                    current = new MediaInfo(f[2], f[3], f[4], f[0].equals("Music") ? "Apple Music" : f[0],
                            f[1].equals("playing"), parse(f[5].replace(',', '.')), dur, System.currentTimeMillis(), null);
                    status = "";
                }
            } catch (Exception e) {
                current = null;
                status = "Media not available";
            }
            sleep(1000);
        }
    }
}
