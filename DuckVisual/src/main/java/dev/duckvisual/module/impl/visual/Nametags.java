package dev.duckvisual.module.impl.visual;

import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.setting.NumberSetting;
import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Projection;
import dev.duckvisual.util.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Replaces player name tags with a panel showing HP, ping, distance and equipment. */
public class Nametags extends Module {
    private final BooleanSetting health = add(new BooleanSetting("Health", true));
    private final BooleanSetting ping = add(new BooleanSetting("Ping", true));
    private final BooleanSetting distance = add(new BooleanSetting("Distance", false));
    private final BooleanSetting items = add(new BooleanSetting("Items", true));
    private final BooleanSetting durability = add(new BooleanSetting("Durability", true).visibleWhen(items::get));
    private final NumberSetting scale = add(new NumberSetting("Scale", 1.0, 0.5, 2, 0.05));
    private final NumberSetting range = add(new NumberSetting("Range", 128, 16, 512, 8));

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND};

    public Nametags() {
        super("Nametags", "Better player name tags with HP and armor", Category.VISUALS);
    }

    public boolean hidesVanilla() {
        return isEnabled();
    }

    @Override
    public void onRender2D(DrawContext ctx, float td) {
        List<PlayerEntity> players = new ArrayList<>(mc.world.getPlayers());
        Vec3d cam = Projection.camera();
        // draw far ones first so near tags are on top
        players.sort(Comparator.comparingDouble(p -> -p.squaredDistanceTo(cam)));
        for (PlayerEntity p : players) {
            if (p == mc.player && mc.options.getPerspective().isFirstPerson()) continue;
            if (p == mc.player || p.isInvisible() || p.squaredDistanceTo(cam) > range.get() * range.get()) continue;
            Vec3d top = p.getLerpedPos(td).add(0, p.getHeight() + (p.isSneaking() ? 0.35 : 0.5), 0);
            float[] sp = Projection.toScreen(top);
            if (sp == null) continue;
            draw(ctx, p, sp[0], sp[1], (float) p.getLerpedPos(td).distanceTo(cam));
        }
    }

    private void draw(DrawContext ctx, PlayerEntity p, float x, float y, float dist) {
        float s = scale.getFloat() * Math.max(0.55f, Math.min(1f, 1.4f - dist / 40f));
        StringBuilder right = new StringBuilder();
        float hp = p.getHealth() + p.getAbsorptionAmount();
        String name = p.getName().getString();
        if (health.get()) right.append(String.format(" %.1f", hp));
        String pingStr = "";
        if (ping.get()) {
            PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(p.getUuid());
            if (e != null) pingStr = " " + e.getLatency() + "ms";
        }
        String distStr = distance.get() ? " " + (int) dist + "m" : "";
        String hpStr = right.toString();
        float w = Render2D.width(name) + Render2D.width(hpStr) + Render2D.width(pingStr) + Render2D.width(distStr) + 10;
        float h = 13;

        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        ctx.getMatrices().scale(s, s, 1);
        float bx = -w / 2, by = -h;
        Render2D.shadow(ctx, bx, by, w, h, 3, 3, ColorUtil.withAlpha(ColorUtil.theme(), 60));
        Render2D.roundedRect(ctx, bx, by, w, h, 3, 0xC8101014);
        // health underline
        float f = Math.min(1, p.getHealth() / Math.max(1, p.getMaxHealth()));
        int hpColor = ColorUtil.lerp(0xFFFF4040, 0xFF40FF70, f);
        Render2D.rect(ctx, bx + 2, by + h - 1.5f, (w - 4) * f, 1.2f, hpColor);
        float tx = bx + 5;
        Render2D.text(ctx, name, tx, by + 3, p.isSneaking() ? 0xFFAAAAAA : 0xFFFFFFFF);
        tx += Render2D.width(name);
        Render2D.text(ctx, hpStr, tx, by + 3, hpColor);
        tx += Render2D.width(hpStr);
        Render2D.text(ctx, pingStr, tx, by + 3, 0xFF9A9AA5);
        tx += Render2D.width(pingStr);
        Render2D.text(ctx, distStr, tx, by + 3, ColorUtil.theme());

        if (items.get()) {
            List<ItemStack> stacks = new ArrayList<>();
            for (EquipmentSlot slot : SLOTS) {
                ItemStack st = p.getEquippedStack(slot);
                if (!st.isEmpty()) stacks.add(st);
            }
            float ix = -stacks.size() * 9f;
            float iy = by - 19;
            for (ItemStack st : stacks) {
                ctx.drawItem(st, (int) ix + 1, (int) iy);
                ctx.drawStackOverlay(mc.textRenderer, st, (int) ix + 1, (int) iy);
                if (durability.get() && st.isDamageable()) {
                    float d = 1 - st.getDamage() / (float) st.getMaxDamage();
                    String ds = Math.round(d * 100) + "";
                    ctx.getMatrices().push();
                    ctx.getMatrices().translate(ix + 9 - Render2D.width(ds) * 0.3f, iy - 5, 200);
                    ctx.getMatrices().scale(0.6f, 0.6f, 1);
                    Render2D.text(ctx, ds, 0, 0, ColorUtil.lerp(0xFFFF4040, 0xFF40FF70, d));
                    ctx.getMatrices().pop();
                }
                ix += 18;
            }
        }
        ctx.getMatrices().pop();
    }
}
