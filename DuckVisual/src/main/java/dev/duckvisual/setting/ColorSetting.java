package dev.duckvisual.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.awt.Color;

/** Color stored as HSB + alpha so the GUI picker can edit each channel independently. */
public class ColorSetting extends Setting<Integer> {
    private float hue;
    private float saturation;
    private float brightness;
    private int alpha;

    public ColorSetting(String name, int argb) {
        super(name, argb);
        setArgb(argb);
    }

    public void setArgb(int argb) {
        float[] hsb = Color.RGBtoHSB((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, null);
        hue = hsb[0];
        saturation = hsb[1];
        brightness = hsb[2];
        alpha = (argb >>> 24) & 255;
        update();
    }

    private void update() {
        value = (alpha << 24) | (Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF);
    }

    public float getHue() { return hue; }
    public float getSaturation() { return saturation; }
    public float getBrightness() { return brightness; }
    public int getAlpha() { return alpha; }

    public void setHue(float h) { hue = h; update(); }
    public void setSaturation(float s) { saturation = s; update(); }
    public void setBrightness(float b) { brightness = b; update(); }
    public void setAlpha(int a) { alpha = a; update(); }

    @Override
    public JsonElement save() {
        JsonObject o = new JsonObject();
        o.addProperty("h", hue);
        o.addProperty("s", saturation);
        o.addProperty("b", brightness);
        o.addProperty("a", alpha);
        return o;
    }

    @Override
    public void load(JsonElement element) {
        if (!element.isJsonObject()) return;
        JsonObject o = element.getAsJsonObject();
        hue = o.get("h").getAsFloat();
        saturation = o.get("s").getAsFloat();
        brightness = o.get("b").getAsFloat();
        alpha = o.get("a").getAsInt();
        update();
    }
}
