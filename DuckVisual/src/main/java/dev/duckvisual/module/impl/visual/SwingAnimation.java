package dev.duckvisual.module.impl.visual;

import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.setting.ModeSetting;
import dev.duckvisual.setting.NumberSetting;

/** Custom first-person swing animations. Applied in HeldItemRendererMixin / LivingEntityMixin. */
public class SwingAnimation extends Module {
    public final ModeSetting mode = add(new ModeSetting("Mode", "Smooth", "Smooth", "Block", "Slide", "Spin", "Punch", "Down"));
    public final NumberSetting speed = add(new NumberSetting("Swing Time", 8, 2, 20, 1));
    public final BooleanSetting onlySword = add(new BooleanSetting("Only Weapons", false));

    public SwingAnimation() {
        super("SwingAnimation", "Custom hand swing animations", Category.VISUALS);
    }

    @Override
    public String getSuffix() {
        return mode.get();
    }
}
