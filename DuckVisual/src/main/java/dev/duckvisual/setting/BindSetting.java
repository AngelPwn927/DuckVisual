package dev.duckvisual.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A key (or mouse button, see {@link dev.duckvisual.util.Keys}) chosen in the GUI. -1 = none. */
public class BindSetting extends Setting<Integer> {
    public BindSetting(String name, int key) {
        super(name, key);
    }

    @Override
    public JsonElement save() {
        return new JsonPrimitive(value);
    }

    @Override
    public void load(JsonElement element) {
        if (element.isJsonPrimitive()) value = element.getAsInt();
    }
}
