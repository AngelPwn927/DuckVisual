package dev.duckvisual.module.impl.misc;

import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.BooleanSetting;

/**
 * Restyles vanilla Minecraft screens in the Duck Visual look: Montserrat font for all UI text,
 * dark rounded buttons and sliders with theme accents (TextRendererMixin, PressableWidgetMixin, SliderWidgetMixin).
 */
public class MinecraftUI extends Module {
    public static MinecraftUI INSTANCE;

    public final BooleanSetting font = add(new BooleanSetting("UI Font", true));
    public final BooleanSetting worldText = add(new BooleanSetting("World Text Font", false));
    public final BooleanSetting buttons = add(new BooleanSetting("Buttons", true));
    public final BooleanSetting sliders = add(new BooleanSetting("Sliders", true));

    /** True while the 3D world is being rendered (signs, name tags...), set by GameRendererMixin. */
    public static boolean inWorld;

    public MinecraftUI() {
        super("MinecraftUI", "Duck Visual style for Minecraft menus", Category.MISC);
        INSTANCE = this;
        // on by default, also for existing configs that do not know this module yet
        setEnabledSilent(true);
    }

    @Override
    public boolean isHidden() {
        return true;
    }

    public static boolean fontActive() {
        MinecraftUI m = INSTANCE;
        return m != null && m.isEnabled() && m.font.get() && (!inWorld || m.worldText.get());
    }

    public static boolean buttonsActive() {
        MinecraftUI m = INSTANCE;
        return m != null && m.isEnabled() && m.buttons.get();
    }

    public static boolean slidersActive() {
        MinecraftUI m = INSTANCE;
        return m != null && m.isEnabled() && m.sliders.get();
    }
}
