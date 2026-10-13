package dev.duckvisual.module.impl.visual;

import dev.duckvisual.mixin.OverlayTextureAccessor;
import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.setting.ColorSetting;
import dev.duckvisual.util.ColorUtil;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;

/** Recolors the red "hurt" overlay that entities flash when damaged. */
public class HitColor extends Module {
    private static final int VANILLA = 0xB2FF0000;

    private final ColorSetting color = add(new ColorSetting("Color", 0xB2B47CFF));
    private final BooleanSetting useTheme = add(new BooleanSetting("Theme Color", true));

    private int applied = VANILLA;

    public HitColor() {
        super("HitColor", "Changes the color entities flash when hurt", Category.VISUALS);
    }

    @Override
    public void onTick() {
        int want = useTheme.get() ? ColorUtil.withAlpha(ColorUtil.theme(), ColorUtil.alpha(color.get())) : color.get();
        if (want != applied) apply(want);
    }

    @Override
    public void onDisable() {
        apply(VANILLA);
    }

    private void apply(int argb) {
        applied = argb;
        NativeImageBackedTexture texture = ((OverlayTextureAccessor) mc.gameRenderer.getOverlayTexture()).duck$getTexture();
        NativeImage image = texture.getImage();
        if (image == null) return;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 16; x++) image.setColorArgb(x, y, argb);
        }
        texture.upload();
    }
}
