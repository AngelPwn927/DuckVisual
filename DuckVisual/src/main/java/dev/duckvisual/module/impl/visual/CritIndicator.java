package dev.duckvisual.module.impl.visual;

import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.util.Animation;
import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvents;

/** Shows when your next hit will be a critical, and flashes when you land one. */
public class CritIndicator extends Module {
    private final BooleanSetting ready = add(new BooleanSetting("Ready Indicator", true));
    private final BooleanSetting flash = add(new BooleanSetting("Crit Flash", true));
    private final BooleanSetting sound = add(new BooleanSetting("Crit Sound", false));

    private float readyAnim;
    private float flashAnim;
    private int crits;

    public CritIndicator() {
        super("CritIndicator", "Shows when a hit will be critical", Category.VISUALS);
    }

    @Override
    public String getSuffix() {
        return crits > 0 ? String.valueOf(crits) : null;
    }

    /** Same conditions vanilla uses for a critical hit. */
    public static boolean canCrit(PlayerEntity p) {
        return p.getAttackCooldownProgress(0.5f) > 0.9f && p.fallDistance > 0 && !p.isOnGround() && !p.isClimbing()
                && !p.isTouchingWater() && !p.hasStatusEffect(StatusEffects.BLINDNESS) && !p.hasVehicle() && !p.isSprinting();
    }

    public void onHit(boolean crit) {
        if (!crit) return;
        crits++;
        flashAnim = 1;
        if (sound.get()) mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, 1.3f, 0.8f));
    }

    @Override
    public void onRender2D(DrawContext ctx, float td) {
        float cx = ctx.getScaledWindowWidth() / 2f, cy = ctx.getScaledWindowHeight() / 2f;
        readyAnim = Animation.approach(readyAnim, ready.get() && canCrit(mc.player) ? 1 : 0, 16);
        flashAnim = Animation.approach(flashAnim, 0, 5);
        if (readyAnim > 0.02f) {
            float r = 9 + 3 * (1 - readyAnim);
            for (int i = 0; i < 4; i++) {
                Render2D.arc(ctx, cx, cy, r, 1.5f, i * 90 + 20, 50, ColorUtil.fade(ColorUtil.theme(i * 4), readyAnim));
            }
        }
        if (flash.get() && flashAnim > 0.02f) {
            Render2D.glowDot(ctx, cx, cy, 14 + 10 * (1 - flashAnim), ColorUtil.fade(0xFFFFD43B, flashAnim * 0.6f));
            String s = "CRIT";
            ctx.getMatrices().push();
            ctx.getMatrices().translate(cx, cy - 22 - 6 * (1 - flashAnim), 0);
            float sc = 0.8f + 0.4f * flashAnim;
            ctx.getMatrices().scale(sc, sc, 1);
            Render2D.centeredText(ctx, s, 0, 0, ColorUtil.fade(0xFFFFD43B, flashAnim));
            ctx.getMatrices().pop();
        }
    }
}
