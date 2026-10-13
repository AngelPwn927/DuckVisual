package dev.duckvisual.module.impl.visual;

import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.BooleanSetting;

public class NoRender extends Module {
    public final BooleanSetting hurtCam = add(new BooleanSetting("Hurt Cam", true));
    public final BooleanSetting fire = add(new BooleanSetting("Fire Overlay", true));
    public final BooleanSetting pumpkin = add(new BooleanSetting("Pumpkin", true));
    public final BooleanSetting nausea = add(new BooleanSetting("Nausea / Portal", true));
    public final BooleanSetting bossBar = add(new BooleanSetting("Boss Bar", false));
    public final BooleanSetting scoreboard = add(new BooleanSetting("Scoreboard", false));
    public final BooleanSetting vanillaEffects = add(new BooleanSetting("Effect Icons", true));

    public NoRender() {
        super("NoRender", "Hides annoying overlays", Category.VISUALS);
    }

    public static boolean active(java.util.function.Function<NoRender, BooleanSetting> f) {
        NoRender nr = dev.duckvisual.DuckVisual.modules().get(NoRender.class);
        return nr.isEnabled() && f.apply(nr).get();
    }
}
