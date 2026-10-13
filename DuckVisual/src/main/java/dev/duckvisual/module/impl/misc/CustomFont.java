package dev.duckvisual.module.impl.misc;

import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.util.Render2D;

/** Smooth Montserrat font for the HUD and menu (font: SIL OFL 1.1, see assets/duckvisual/font). */
public class CustomFont extends Module {
    private final BooleanSetting shadow = add(new BooleanSetting("Shadow", false));

    public CustomFont() {
        super("CustomFont", "Smooth Montserrat font for HUD and menu", Category.MISC);
    }

    @Override
    public boolean isHidden() {
        return true;
    }

    @Override
    public void onEnable() {
        Render2D.customFont = true;
    }

    @Override
    public void onDisable() {
        Render2D.customFont = false;
    }

    @Override
    public void onTick() {
        Render2D.customFont = true;
        Render2D.shadow = shadow.get();
    }
}
