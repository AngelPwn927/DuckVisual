package dev.duckvisual.module.impl.hud;

import dev.duckvisual.module.HudModule;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

/** Armor pieces with durability above the hotbar. */
public class ArmorHud extends HudModule {
    private final BooleanSetting percent = add(new BooleanSetting("Percent", true));
    private final BooleanSetting background = add(new BooleanSetting("Background", true));

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    public ArmorHud() {
        super("ArmorHud", "Armor durability", -1, -1);
    }

    @Override
    public boolean isHidden() {
        return true;
    }

    @Override
    public void onRender2D(DrawContext ctx, float td) {
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        width = 4 * 22 + 6;
        height = percent.get() ? 30 : 22;
        if (getX() < 0) {
            x.set(sw / 2.0 + 95);
            y.set(sh - height - 3.0);
        }
        float x0 = getX(), y0 = getY();
        if (background.get()) HudModule.panel(ctx, x0, y0, width, height);
        for (int i = 0; i < 4; i++) {
            ItemStack stack = mc.player.getEquippedStack(SLOTS[i]);
            float ix = x0 + 4 + i * 22, iy = y0 + 3;
            if (stack.isEmpty()) {
                Render2D.roundedRect(ctx, ix + 1, iy + 1, 16, 16, 3, 0x30FFFFFF);
                continue;
            }
            ctx.drawItem(stack, (int) ix + 1, (int) iy + 1);
            if (stack.isDamageable()) {
                float f = 1 - stack.getDamage() / (float) stack.getMaxDamage();
                int c = ColorUtil.lerp(0xFFFF4040, ColorUtil.theme(i * 4), f);
                Render2D.rect(ctx, ix + 2, iy + 18, 14, 1.5f, 0x80000000);
                Render2D.rect(ctx, ix + 2, iy + 18, 14 * f, 1.5f, c);
                if (percent.get()) {
                    String p = Math.round(f * 100) + "";
                    ctx.getMatrices().push();
                    ctx.getMatrices().translate(ix + 9 - Render2D.width(p) * 0.35f, iy + 21, 200);
                    ctx.getMatrices().scale(0.7f, 0.7f, 1);
                    Render2D.text(ctx, p, 0, 0, c);
                    ctx.getMatrices().pop();
                }
            }
        }
    }
}
