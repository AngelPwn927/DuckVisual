package dev.duckvisual.module.impl.misc;

import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.ColorSetting;
import dev.duckvisual.setting.ModeSetting;
import dev.duckvisual.setting.NumberSetting;

/** Global client colors used by every visual and the GUI. Always on. */
public class Theme extends Module {
    /** name, first color, second color */
    public record Preset(String name, int first, int second) {}

    public static final Preset[] PRESETS = {
            new Preset("Duck", 0xFFFFD43B, 0xFFFF8A1F),
            new Preset("Sunset", 0xFFFF5F6D, 0xFFFFC371),
            new Preset("Ocean", 0xFF2193B0, 0xFF6DD5ED),
            new Preset("Purple", 0xFF8E2DE2, 0xFFC084FC),
            new Preset("Mint", 0xFF00F5A0, 0xFF00D9F5),
            new Preset("Cherry", 0xFFEB3349, 0xFFF45C43),
            new Preset("Sky", 0xFF56CCF2, 0xFF2F80ED),
            new Preset("Lime", 0xFFA8E063, 0xFF56AB2F),
            new Preset("Pink", 0xFFFF6FD8, 0xFFFFA8D9),
            new Preset("Aqua", 0xFF13F1FC, 0xFF0470DC),
            new Preset("Fire", 0xFFF12711, 0xFFF5AF19),
            new Preset("Lavender", 0xFFB993D6, 0xFF8CA6DB),
            new Preset("Blood", 0xFFB31217, 0xFFE52D27),
            new Preset("Gold", 0xFFF7971E, 0xFFFFD200),
            new Preset("Ice", 0xFFE0EAFC, 0xFF9CC9FF),
            new Preset("Neon", 0xFFFC00FF, 0xFF00DBDE),
            new Preset("Forest", 0xFF11998E, 0xFF38EF7D),
            new Preset("Royal", 0xFF4776E6, 0xFF8E54E9),
            new Preset("Peach", 0xFFFFB88C, 0xFFDE6262),
            new Preset("Candy", 0xFFD53369, 0xFFDAAE51),
            new Preset("Toxic", 0xFFB6FF00, 0xFF3CFF8F),
            new Preset("Love", 0xFF6A82FB, 0xFFFC5C7D),
            new Preset("Mono", 0xFFFFFFFF, 0xFF8A8A8A),
            new Preset("Rose", 0xFFF4C4F3, 0xFFFC67FA),
            new Preset("Coral", 0xFFFF7E5F, 0xFFFEB47B),
            new Preset("Emerald", 0xFF43E97B, 0xFF38F9D7),
    };

    public final ModeSetting mode = add(new ModeSetting("Mode", "Gradient", "Gradient", "Static", "Rainbow"));
    public final ModeSetting palette = add(new ModeSetting("Palette", "Duck", paletteNames()));
    public final ColorSetting first = add(new ColorSetting("First Color", 0xFFFFD43B).visibleWhen(this::isCustom));
    public final ColorSetting second = add(new ColorSetting("Second Color", 0xFFFF8A1F).visibleWhen(this::isCustom));
    public final NumberSetting speed = add(new NumberSetting("Speed", 1, 0.1, 5, 0.1));

    public Theme() {
        super("Theme", "Client color scheme", Category.MISC);
    }

    private static String[] paletteNames() {
        String[] names = new String[PRESETS.length + 1];
        for (int i = 0; i < PRESETS.length; i++) names[i] = PRESETS[i].name();
        names[PRESETS.length] = "Custom";
        return names;
    }

    public boolean isCustom() {
        return palette.is("Custom");
    }

    private Preset preset() {
        for (Preset p : PRESETS) if (palette.is(p.name())) return p;
        return PRESETS[0];
    }

    public int firstColor() {
        return isCustom() ? first.get() : preset().first();
    }

    public int secondColor() {
        return isCustom() ? second.get() : preset().second();
    }

    @Override
    public String getSuffix() {
        return palette.get();
    }

    @Override
    public boolean isHidden() {
        return true;
    }

    @Override
    public void setEnabled(boolean enabled) {
        // the theme can't be turned off
        super.setEnabled(true);
    }
}
