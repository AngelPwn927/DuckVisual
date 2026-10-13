package dev.duckvisual.module.impl.visual;

import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.NumberSetting;

/** Moves / scales held items. Applied in HeldItemRendererMixin. */
public class ViewModel extends Module {
    public final NumberSetting mainX = add(new NumberSetting("Main X", 0, -1, 1, 0.05));
    public final NumberSetting mainY = add(new NumberSetting("Main Y", 0, -1, 1, 0.05));
    public final NumberSetting mainZ = add(new NumberSetting("Main Z", 0, -1, 1, 0.05));
    public final NumberSetting offX = add(new NumberSetting("Off X", 0, -1, 1, 0.05));
    public final NumberSetting offY = add(new NumberSetting("Off Y", 0, -1, 1, 0.05));
    public final NumberSetting offZ = add(new NumberSetting("Off Z", 0, -1, 1, 0.05));
    public final NumberSetting scale = add(new NumberSetting("Scale", 1, 0.3, 1.5, 0.05));

    public ViewModel() {
        super("ViewModel", "Changes the position of held items", Category.VISUALS);
    }
}
