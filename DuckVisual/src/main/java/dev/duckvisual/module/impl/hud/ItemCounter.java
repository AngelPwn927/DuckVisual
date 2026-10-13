package dev.duckvisual.module.impl.hud;

import dev.duckvisual.module.HudModule;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** How many totems, golden apples, pearls, XP bottles and so on you carry: one glass pill per item. */
public class ItemCounter extends HudModule {
    private final BooleanSetting hideEmpty = add(new BooleanSetting("Hide Empty", true));

    private static final Item[] ITEMS = {Items.TOTEM_OF_UNDYING, Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE,
            Items.ENDER_PEARL, Items.EXPERIENCE_BOTTLE, Items.SPLASH_POTION, Items.END_CRYSTAL, Items.OBSIDIAN};
    private final ItemStack[] icons = new ItemStack[ITEMS.length];

    public ItemCounter() {
        super("ItemCounter", "Totems, apples, pearls you carry", -1, -1);
        for (int i = 0; i < ITEMS.length; i++) icons[i] = new ItemStack(ITEMS[i]);
    }

    @Override
    public boolean isHidden() {
        return true;
    }

    @Override
    public void onRender2D(DrawContext ctx, float td) {
        int[] counts = new int[ITEMS.length];
        var inv = mc.player.getInventory();
        for (int s = 0; s < inv.size(); s++) {
            ItemStack st = inv.getStack(s);
            for (int i = 0; i < ITEMS.length; i++) if (st.isOf(ITEMS[i])) counts[i] += st.getCount();
        }
        float rowH = 15, w = 34;
        int shown = 0;
        for (int c : counts) if (c > 0 || !hideEmpty.get()) shown++;
        width = w;
        height = Math.max(rowH, shown * (rowH + 2) - 2);
        if (getX() < 0 || getY() < 0) {
            x.set((double) (ctx.getScaledWindowWidth() - width - 6));
            // stacked above Keystrokes and Inventory on the right
            y.set((double) Math.max(40, ctx.getScaledWindowHeight() - 6 - InventoryHud.HEIGHT - 4 - 81 - 6 - 8 * 17));
        }
        float x0 = getX(), yy = getY();
        for (int i = 0; i < ITEMS.length; i++) {
            if (counts[i] == 0 && hideEmpty.get()) continue;
            panel(ctx, x0, yy, w, rowH);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(x0 + 3, yy + 1.5f, 0);
            ctx.getMatrices().scale(0.75f, 0.75f, 1);
            ctx.drawItem(icons[i], 0, 0);
            ctx.getMatrices().pop();
            String n = String.valueOf(counts[i]);
            Render2D.text(ctx, n, x0 + w - 5 - Render2D.width(n), yy + 4, counts[i] > 0 ? 0xFFF2F2F7 : ColorUtil.withAlpha(0xFF98989F, 200));
            yy += rowH + 2;
        }
    }
}
