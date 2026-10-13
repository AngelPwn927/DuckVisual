package dev.duckvisual.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.duckvisual.DuckVisual;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Long Russian descriptions for every module, from assets/duckvisual/guide/descriptions_ru.json. */
public final class Guide {
    private Guide() {}

    private static Map<String, String> texts;

    public static String get(String module) {
        if (texts == null) load();
        return texts.getOrDefault(module, "");
    }

    private static void load() {
        texts = new HashMap<>();
        try (InputStream in = Guide.class.getResourceAsStream("/assets/duckvisual/guide/descriptions_ru.json")) {
            if (in == null) return;
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (String k : o.keySet()) texts.put(k, o.get(k).getAsString());
        } catch (Exception e) {
            DuckVisual.LOGGER.error("Failed to load module descriptions", e);
        }
    }
}
