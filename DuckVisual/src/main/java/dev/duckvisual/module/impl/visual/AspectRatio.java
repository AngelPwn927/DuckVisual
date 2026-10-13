package dev.duckvisual.module.impl.visual;

import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.NumberSetting;

/** Stretched resolution. Applied in GameRendererMixin. */
public class AspectRatio extends Module {
    public final NumberSetting ratio = add(new NumberSetting("Ratio", 1.33, 0.5, 2.5, 0.01));

    public AspectRatio() {
        super("AspectRatio", "Stretches the world view", Category.VISUALS);
    }

    @Override
    public String getSuffix() {
        return ratio.display();
    }
}
