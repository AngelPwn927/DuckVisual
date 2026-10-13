package dev.duckvisual.module.impl.misc;

import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.BooleanSetting;

/** Holds the GUI keybind (Right Shift by default) and GUI options. */
public class ClickGui extends Module {
    /** macOS frosted glass for the menu and the HUD panels */
    public final BooleanSetting glass = add(new BooleanSetting("Glass", true));
    public final BooleanSetting hudGlass = add(new BooleanSetting("Glass HUD", true));
    public final BooleanSetting blur = add(new BooleanSetting("Dim Background", true));
    public final BooleanSetting realBlur = add(new BooleanSetting("Blur Background", false));
    public final BooleanSetting descriptions = add(new BooleanSetting("Descriptions", true));

    public ClickGui() {
        super("ClickGui", "Opens the menu", Category.MISC);
    }

    @Override
    public boolean isHidden() {
        return true;
    }

    @Override
    public void setEnabled(boolean enabled) {
        // the bind opens the screen instead of toggling
    }
}
