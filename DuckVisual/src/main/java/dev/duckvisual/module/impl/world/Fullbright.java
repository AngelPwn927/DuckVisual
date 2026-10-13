package dev.duckvisual.module.impl.world;

import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.NumberSetting;

/** Raises the lightmap gamma. Applied in LightmapTextureManagerMixin. */
public class Fullbright extends Module {
    public final NumberSetting gamma = add(new NumberSetting("Gamma", 15, 1, 20, 0.5));

    public Fullbright() {
        super("Fullbright", "See in the dark", Category.WORLD);
    }
}
