package dev.duckvisual.module.impl.visual;

import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.setting.NumberSetting;
import dev.duckvisual.util.ColorUtil;

/** Tints the first-person hand and held item with the theme color (GameRendererMixin). */
public class HandTint extends Module {
    private final NumberSetting strength = add(new NumberSetting("Strength", 0.5, 0.1, 1, 0.05));
    private final BooleanSetting pulse = add(new BooleanSetting("Pulse", true));
    private final NumberSetting alpha = add(new NumberSetting("Hand Alpha", 1, 0.2, 1, 0.05));

    public HandTint() {
        super("HandTint", "Colors your hand and held item", Category.VISUALS);
    }

    /** {r, g, b, a} multiplier for RenderSystem.setShaderColor. */
    public float[] color() {
        float s = strength.getFloat();
        if (pulse.get()) s *= 0.75f + 0.25f * (float) Math.sin(System.currentTimeMillis() / 400.0);
        int c = ColorUtil.theme();
        // brighten the theme color so the multiply does not darken the item too much
        float r = ColorUtil.red(c) / 255f, g = ColorUtil.green(c) / 255f, b = ColorUtil.blue(c) / 255f;
        float max = Math.max(r, Math.max(g, b));
        if (max > 0) { r /= max; g /= max; b /= max; }
        return new float[]{1 + (r - 1) * s, 1 + (g - 1) * s, 1 + (b - 1) * s, alpha.getFloat()};
    }
}
