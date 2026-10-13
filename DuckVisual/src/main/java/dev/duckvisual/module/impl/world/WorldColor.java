package dev.duckvisual.module.impl.world;

import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.setting.ColorSetting;
import dev.duckvisual.setting.NumberSetting;
import dev.duckvisual.util.ColorUtil;
import org.joml.Vector4f;

/** Tints the sky, fog and clouds (applied in WorldColorMixin / ClientWorldMixin). */
public class WorldColor extends Module {
    public final BooleanSetting sky = add(new BooleanSetting("Sky", true));
    public final BooleanSetting fog = add(new BooleanSetting("Fog", true));
    public final BooleanSetting clouds = add(new BooleanSetting("Clouds", true));
    private final BooleanSetting useTheme = add(new BooleanSetting("Theme Color", true));
    private final ColorSetting color = add(new ColorSetting("Color", 0xFFB36BFF).visibleWhen(() -> !useTheme.get()));
    private final NumberSetting strength = add(new NumberSetting("Strength", 0.6, 0.05, 1, 0.05));

    public WorldColor() {
        super("WorldColor", "Colors the sky, fog and clouds", Category.WORLD);
    }

    private int target() {
        return useTheme.get() ? ColorUtil.theme() : color.get();
    }

    public int tint(int rgb) {
        int mixed = ColorUtil.lerp(0xFF000000 | rgb, 0xFF000000 | target(), strength.getFloat());
        return (rgb & 0xFF000000) | (mixed & 0xFFFFFF);
    }

    public void tint(Vector4f fog) {
        int t = target();
        float s = strength.getFloat();
        // keep the original brightness so nights stay dark
        float lum = Math.max(fog.x, Math.max(fog.y, fog.z));
        fog.x += (ColorUtil.red(t) / 255f * lum - fog.x) * s;
        fog.y += (ColorUtil.green(t) / 255f * lum - fog.y) * s;
        fog.z += (ColorUtil.blue(t) / 255f * lum - fog.z) * s;
    }
}
