package dev.duckvisual.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Arrays;
import java.util.List;

public class ModeSetting extends Setting<String> {
    private final List<String> modes;

    public ModeSetting(String name, String value, String... modes) {
        super(name, value);
        this.modes = Arrays.asList(modes);
    }

    public List<String> getModes() {
        return modes;
    }

    public boolean is(String mode) {
        return value.equalsIgnoreCase(mode);
    }

    public void cycle(boolean forward) {
        int i = modes.indexOf(value);
        i = (i + (forward ? 1 : -1) + modes.size()) % modes.size();
        value = modes.get(i);
    }

    @Override
    public JsonElement save() {
        return new JsonPrimitive(value);
    }

    @Override
    public void load(JsonElement element) {
        if (!element.isJsonPrimitive()) return;
        String s = element.getAsString();
        for (String m : modes) if (m.equalsIgnoreCase(s)) value = m;
    }
}
