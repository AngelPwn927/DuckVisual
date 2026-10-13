package dev.duckvisual.module.impl.hud;

import dev.duckvisual.module.HudModule;
import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Item cooldowns (ender pearls, golden apples, shields, wind charges...). */
public class Cooldowns extends HudModule {
    public Cooldowns() {
        super("Cooldowns", "Shows item cooldowns", 5, 200);
    }

    @Override
    public boolean isHidden() {
        return true;
    }

    @Override
    public void onRender2D(DrawContext ctx, float td) {
        List<ItemStack> items = new ArrayList<>();
        Set<Object> groups = new HashSet<>();
        var cd = mc.player.getItemCooldownManager();
        for (int i = 0; i < 9; i++) collect(mc.player.getInventory().getStack(i), items, groups, cd);
        collect(mc.player.getOffHandStack(), items, groups, cd);
        boolean editing = mc.currentScreen instanceof dev.duckvisual.gui.ClickGuiScreen;
        if (items.isEmpty() && !editing) {
            height = 0;
            return;
        }
        float x0 = getX(), y0 = getY();
        width = 110;
        height = 18 + items.size() * 14 + (items.isEmpty() ? 0 : 3);
        panel(ctx, x0, y0, width, height);
        header(ctx, x0, y0, width);
        Render2D.text(ctx, "Cooldowns", x0 + 6, y0 + 6, 0xFFFFFFFF);
        float yy = y0 + 18;
        int i = 0;
        for (ItemStack s : items) {
            float p = cd.getCooldownProgress(s, td);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(x0 + 5, yy, 0);
            ctx.getMatrices().scale(0.75f, 0.75f, 1);
            ctx.drawItem(s, 0, 0);
            ctx.getMatrices().pop();
            float bx = x0 + 22, bw = width - 28;
            Render2D.roundedRect(ctx, bx, yy + 4, bw, 4, 2, 0xFF2A2A33);
            Render2D.roundedRect(ctx, bx, yy + 4, Math.max(4, bw * p), 4, 2, ColorUtil.theme(i++ * 4));
            yy += 14;
        }
    }

    private static void collect(ItemStack s, List<ItemStack> items, Set<Object> groups,
                                net.minecraft.entity.player.ItemCooldownManager cd) {
        if (s.isEmpty() || !cd.isCoolingDown(s)) return;
        if (groups.add(cd.getGroup(s))) items.add(s);
    }
}
