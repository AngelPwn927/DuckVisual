package dev.duckvisual.module.impl.hud;

import dev.duckvisual.EventHooks;
import dev.duckvisual.module.HudModule;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.util.Animation;
import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TargetHud extends HudModule {
    private final BooleanSetting armor = add(new BooleanSetting("Armor", true));
    private final BooleanSetting particles = add(new BooleanSetting("Particles", true));

    private LivingEntity shown;
    private float appear;
    private float healthAnim;
    private float headHurt;
    private final List<float[]> parts = new ArrayList<>();
    private final Random random = new Random();

    public TargetHud() {
        super("TargetHud", "Information about your target", -1, -1);
    }

    @Override
    public boolean isHidden() {
        return true;
    }

    public void onHit() {
        if (!particles.get()) return;
        for (int i = 0; i < 8; i++) {
            // x, y, vx, vy, life, colorIndex
            parts.add(new float[]{0, 0, (random.nextFloat() - 0.5f) * 3, (random.nextFloat() - 0.5f) * 3, 1, random.nextInt(30)});
        }
    }

    @Override
    public void onRender2D(DrawContext ctx, float td) {
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        if (getX() < 0) {
            x.set(sw / 2.0 + 20);
            y.set(sh / 2.0 + 20);
        }
        LivingEntity target = EventHooks.currentTarget(4000);
        // preview on self while editing the HUD
        if (target == null && (mc.currentScreen instanceof ChatScreen || mc.currentScreen instanceof dev.duckvisual.gui.ClickGuiScreen)) {
            target = mc.player;
        }
        if (target != null) shown = target;
        appear = Animation.approach(appear, target != null ? 1 : 0, 10);
        if (shown == null || appear < 0.02f) {
            healthAnim = 0;
            return;
        }

        float x = getX(), y = getY();
        width = 130;
        height = armor.get() ? 50 : 40;
        float hp = Math.max(0, shown.getHealth());
        float max = Math.max(1, shown.getMaxHealth());
        float abs = shown.getAbsorptionAmount();
        float frac = Math.min(1, hp / max);
        healthAnim = Animation.approach(healthAnim, frac, 6);
        headHurt = Animation.approach(headHurt, shown.hurtTime > 0 ? 1 : 0, 14);

        ctx.getMatrices().push();
        float cx = x + width / 2, cy = y + height / 2;
        float scale = 0.85f + 0.15f * Animation.easeOutCubic(appear);
        ctx.getMatrices().translate(cx, cy, 0);
        ctx.getMatrices().scale(scale, scale, 1);
        ctx.getMatrices().translate(-cx, -cy, 0);

        Render2D.shadow(ctx, x, y, width, height, 5, 6, ColorUtil.withAlpha(ColorUtil.theme(), (int) (90 * appear)));
        Render2D.roundedRect(ctx, x, y, width, height, 5, ColorUtil.withAlpha(0xFF111116, (int) (225 * appear)));

        // head
        float headSize = 30;
        float hx = x + 5, hy = y + 5;
        if (shown instanceof AbstractClientPlayerEntity player) {
            float s = 1 - headHurt * 0.1f;
            ctx.getMatrices().push();
            ctx.getMatrices().translate(hx + headSize / 2, hy + headSize / 2, 0);
            ctx.getMatrices().scale(s, s, 1);
            ctx.getMatrices().translate(-headSize / 2, -headSize / 2, 0);
            int tint = ColorUtil.rgba(255, (int) (255 - 120 * headHurt), (int) (255 - 120 * headHurt), (int) (255 * appear));
            PlayerSkinDrawer.draw(ctx, player.getSkinTextures(), 0, 0, (int) headSize, tint);
            ctx.getMatrices().pop();
        } else {
            Render2D.roundedRect(ctx, hx, hy, headSize, headSize, 4, ColorUtil.withAlpha(0xFF22222A, (int) (255 * appear)));
            Render2D.centeredText(ctx, "?", hx + headSize / 2, hy + headSize / 2 - 4, ColorUtil.fade(ColorUtil.theme(), appear));
        }

        // particles burst from the head on hit
        for (float[] p : parts) {
            p[0] += p[2] * Animation.delta() * 30;
            p[1] += p[3] * Animation.delta() * 30;
            p[4] -= Animation.delta() * 1.2f;
            Render2D.glowDot(ctx, hx + headSize / 2 + p[0], hy + headSize / 2 + p[1], 3,
                    ColorUtil.fade(ColorUtil.theme((int) p[5]), Math.max(0, p[4]) * appear));
        }
        parts.removeIf(p -> p[4] <= 0);

        float tx = hx + headSize + 6;
        String name = shown.getName().getString();
        Render2D.text(ctx, name, tx, y + 7, ColorUtil.fade(0xFFFFFFFF, appear));
        String hpText = String.format("%.1f", hp + abs) + " HP";
        Render2D.text(ctx, hpText, tx, y + 18, ColorUtil.fade(0xFFB0B0BA, appear));
        if (shown instanceof PlayerEntity && mc.player != null) {
            String rel = hp + abs >= mc.player.getHealth() + mc.player.getAbsorptionAmount() ? "▲" : "▼";
            int relColor = rel.equals("▲") ? 0xFFFF5555 : 0xFF55FF77;
            Render2D.text(ctx, rel, x + width - 12, y + 7, ColorUtil.fade(relColor, appear));
        }

        // health bar
        float bx = tx, by = y + 30, bw = x + width - 6 - tx, bh = 4;
        Render2D.roundedRect(ctx, bx, by, bw, bh, 2, ColorUtil.withAlpha(0xFF2A2A33, (int) (255 * appear)));
        if (healthAnim > 0.01f) {
            Render2D.shadow(ctx, bx, by, bw * healthAnim, bh, 2, 2, ColorUtil.withAlpha(ColorUtil.theme(), (int) (120 * appear)));
            Render2D.roundedGradient(ctx, bx, by, bw * healthAnim, bh, 2,
                    ColorUtil.fade(ColorUtil.theme(0), appear), ColorUtil.fade(ColorUtil.theme(10), appear),
                    ColorUtil.fade(ColorUtil.theme(10), appear), ColorUtil.fade(ColorUtil.theme(0), appear));
        }
        if (abs > 0) {
            float af = Math.min(1, abs / max);
            Render2D.roundedRect(ctx, bx, by, bw * af, bh, 2, ColorUtil.withAlpha(0xFFFFCC33, (int) (200 * appear)));
        }

        // armor + held items
        if (armor.get() && appear > 0.5f) {
            float ix = tx - 2;
            float iy = y + 36;
            ctx.getMatrices().push();
            ctx.getMatrices().translate(ix, iy, 0);
            ctx.getMatrices().scale(0.75f, 0.75f, 1);
            int off = 0;
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
                    EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack stack = shown.getEquippedStack(slot);
                if (!stack.isEmpty()) {
                    ctx.drawItem(stack, off, 0);
                    ctx.drawStackOverlay(mc.textRenderer, stack, off, 0);
                }
                off += 17;
            }
            ctx.getMatrices().pop();
        }
        ctx.getMatrices().pop();
    }
}
