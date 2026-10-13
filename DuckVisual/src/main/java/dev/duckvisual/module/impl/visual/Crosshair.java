package dev.duckvisual.module.impl.visual;

import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.setting.ModeSetting;
import dev.duckvisual.setting.NumberSetting;
import dev.duckvisual.util.Animation;
import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Render2D;
import net.minecraft.client.gui.DrawContext;

public class Crosshair extends Module {
    private final ModeSetting mode = add(new ModeSetting("Mode", "Cross", "Cross", "Circle", "Dot"));
    private final NumberSetting gap = add(new NumberSetting("Gap", 2, 0, 10, 0.5));
    private final NumberSetting length = add(new NumberSetting("Length", 4, 1, 12, 0.5));
    private final NumberSetting thickness = add(new NumberSetting("Thickness", 1, 0.5, 4, 0.5));
    private final BooleanSetting dynamic = add(new BooleanSetting("Dynamic", true));
    private final BooleanSetting cooldown = add(new BooleanSetting("Cooldown", true));
    private final BooleanSetting dot = add(new BooleanSetting("Center Dot", false));
    private final BooleanSetting outline = add(new BooleanSetting("Outline", true));

    private float spread;

    public Crosshair() {
        super("Crosshair", "Customizable crosshair", Category.VISUALS);
    }

    @Override
    public void onRender2D(DrawContext ctx, float td) {
        if (!mc.options.getPerspective().isFirstPerson()) return;
        float cx = ctx.getScaledWindowWidth() / 2f, cy = ctx.getScaledWindowHeight() / 2f;
        boolean moving = mc.player.input.getMovementInput().lengthSquared() > 0 || !mc.player.isOnGround();
        spread = Animation.approach(spread, dynamic.get() && moving ? 3 : 0, 12);
        int color = ColorUtil.theme();
        float g = gap.getFloat() + spread, l = length.getFloat(), t = thickness.getFloat();
        float cd = mc.player.getAttackCooldownProgress(td);

        switch (mode.get()) {
            case "Circle" -> {
                float rad = g + 3;
                Render2D.arc(ctx, cx, cy, rad + 1, t + 1, 0, 360, 0x90000000);
                Render2D.arc(ctx, cx, cy, rad, t, -90, 360, ColorUtil.withAlpha(color, 90));
                if (cooldown.get()) Render2D.arc(ctx, cx, cy, rad, t, -90, 360 * cd, color);
                else Render2D.arc(ctx, cx, cy, rad, t, -90, 360, color);
            }
            case "Dot" -> {
                if (outline.get()) Render2D.circle(ctx, cx, cy, t + 1.5f, 0xA0000000);
                Render2D.circle(ctx, cx, cy, t + 0.5f, color);
            }
            default -> {
                bar(ctx, cx - t / 2, cy - g - l, t, l, color);
                bar(ctx, cx - t / 2, cy + g, t, l, color);
                bar(ctx, cx - g - l, cy - t / 2, l, t, color);
                bar(ctx, cx + g, cy - t / 2, l, t, color);
                if (cooldown.get() && cd < 1) {
                    float w = 16;
                    Render2D.rect(ctx, cx - w / 2, cy + g + l + 3, w, 1.5f, 0x80000000);
                    Render2D.rect(ctx, cx - w / 2, cy + g + l + 3, w * cd, 1.5f, color);
                }
            }
        }
        if (dot.get() && !mode.is("Dot")) Render2D.rect(ctx, cx - t / 2, cy - t / 2, t, t, color);
    }

    private void bar(DrawContext ctx, float x, float y, float w, float h, int color) {
        if (outline.get()) Render2D.rect(ctx, x - 0.5f, y - 0.5f, w + 1, h + 1, 0xA0000000);
        Render2D.rect(ctx, x, y, w, h, color);
    }
}
