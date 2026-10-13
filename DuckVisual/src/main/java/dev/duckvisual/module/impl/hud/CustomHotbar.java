package dev.duckvisual.module.impl.hud;

import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.BindSetting;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.util.Animation;
import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Keys;
import dev.duckvisual.util.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

/**
 * Rounded hotbar with an animated selector, plus custom binds for each hotbar slot
 * (keyboard keys or mouse buttons). The vanilla hotbar is hidden in InGameHudMixin.
 */
public class CustomHotbar extends Module {
    public final BooleanSetting style = add(new BooleanSetting("Custom Style", true));
    private final BooleanSetting showBinds = add(new BooleanSetting("Show Binds", true));
    private final BindSetting[] slotBinds = new BindSetting[9];

    private float selector = -1;
    private final boolean[] held = new boolean[9];

    public CustomHotbar() {
        super("CustomHotbar", "Stylish hotbar with binds for every slot", Category.HUD);
        for (int i = 0; i < 9; i++) slotBinds[i] = add(new BindSetting("Slot " + (i + 1), -1));
    }

    @Override
    public boolean isHidden() {
        return true;
    }

    public boolean replacesVanilla() {
        return isEnabled() && style.get();
    }

    @Override
    public void onTick() {
        long w = mc.getWindow().getHandle();
        for (int i = 0; i < 9; i++) {
            int key = slotBinds[i].get();
            boolean down = mc.currentScreen == null && Keys.isDown(w, key);
            if (down && !held[i]) mc.player.getInventory().selectedSlot = i;
            held[i] = down;
        }
    }

    @Override
    public void onRender2D(DrawContext ctx, float td) {
        if (!style.get() || mc.player.isSpectator()) return;
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        float x = sw / 2f - 91, y = sh - 22;
        int sel = mc.player.getInventory().selectedSlot;
        selector = selector < 0 ? sel : Animation.approach(selector, sel, 18);

        Render2D.shadow(ctx, x, y, 182, 22, 5, 4, ColorUtil.withAlpha(ColorUtil.theme(), 70));
        Render2D.roundedRect(ctx, x, y, 182, 22, 5, 0xD8111116);
        float sx = x + 1 + selector * 20;
        Render2D.roundedGradient(ctx, sx, y + 1, 20, 20, 4,
                ColorUtil.withAlpha(ColorUtil.theme(0), 90), ColorUtil.withAlpha(ColorUtil.theme(8), 90),
                ColorUtil.withAlpha(ColorUtil.theme(8), 90), ColorUtil.withAlpha(ColorUtil.theme(0), 90));
        Render2D.roundedRect(ctx, sx + 4, y + 20, 12, 1.5f, 0.7f, ColorUtil.theme());

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            int ix = (int) (x + 3 + i * 20), iy = (int) (y + 3);
            if (!stack.isEmpty()) {
                ctx.drawItem(mc.player, stack, ix, iy, i + 1);
                ctx.drawStackOverlay(mc.textRenderer, stack, ix, iy);
            }
            int key = slotBinds[i].get();
            if (showBinds.get() && key > 0) {
                String k = key >= Keys.MOUSE ? "M" + (key - Keys.MOUSE + 1) : Keys.name(key);
                if (k.length() > 3) k = k.substring(0, 3);
                ctx.getMatrices().push();
                ctx.getMatrices().translate(x + 2 + i * 20, y + 1.5f, 300);
                ctx.getMatrices().scale(0.5f, 0.5f, 1);
                Render2D.text(ctx, k, 0, 0, ColorUtil.theme(i * 3));
                ctx.getMatrices().pop();
            }
        }

        ItemStack off = mc.player.getOffHandStack();
        if (!off.isEmpty()) {
            float ox = x - 29;
            Render2D.roundedRect(ctx, ox, y, 22, 22, 5, 0xD8111116);
            ctx.drawItem(mc.player, off, (int) ox + 3, (int) y + 3, 10);
            ctx.drawStackOverlay(mc.textRenderer, off, (int) ox + 3, (int) y + 3);
        }

        float cd = mc.player.getAttackCooldownProgress(td);
        if (cd < 1) {
            float bx = x + 182 + 6;
            Render2D.roundedRect(ctx, bx, y + 2, 4, 18, 2, 0xD8111116);
            Render2D.roundedRect(ctx, bx, y + 2 + 18 * (1 - cd), 4, 18 * cd, 2, ColorUtil.theme());
        }
    }
}
