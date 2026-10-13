package dev.duckvisual.module.impl.hud;

import dev.duckvisual.module.HudModule;
import dev.duckvisual.util.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

/** The main inventory (3×9) on screen, so you see your loot without opening it. */
public class InventoryHud extends HudModule {
    private static final float SLOT = 15;
    static final float HEIGHT = 20 + SLOT * 3 + 4;

    public InventoryHud() {
        super("Inventory", "Shows your inventory on screen", -1, -1);
    }

    @Override
    public boolean isHidden() {
        return true;
    }

    @Override
    public void onRender2D(DrawContext ctx, float td) {
        width = SLOT * 9 + 8;
        height = HEIGHT;
        if (getX() < 0 || getY() < 0) {
            x.set((double) (ctx.getScaledWindowWidth() - width - 6));
            y.set((double) (ctx.getScaledWindowHeight() - height - 6));
        }
        float x0 = getX(), y0 = getY();
        panel(ctx, x0, y0, width, height);
        title(ctx, "Inventory", "project", x0, y0, width);
        for (int i = 0; i < 27; i++) {
            float sx = x0 + 4 + (i % 9) * SLOT, sy = y0 + 20 + (i / 9) * SLOT;
            Render2D.roundedRect(ctx, sx + 0.5f, sy + 0.5f, SLOT - 1, SLOT - 1, 3, 0x12FFFFFF);
        }
        for (int i = 0; i < 27; i++) {
            ItemStack s = mc.player.getInventory().getStack(9 + i);
            if (s.isEmpty()) continue;
            float sx = x0 + 4 + (i % 9) * SLOT, sy = y0 + 20 + (i / 9) * SLOT;
            ctx.getMatrices().push();
            ctx.getMatrices().translate(sx + 1.5f, sy + 1.5f, 0);
            ctx.getMatrices().scale(0.75f, 0.75f, 1);
            ctx.drawItem(s, 0, 0);
            ctx.drawStackOverlay(mc.textRenderer, s, 0, 0);
            ctx.getMatrices().pop();
        }
    }
}
