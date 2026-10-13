package dev.duckvisual;

import dev.duckvisual.module.impl.hud.TargetHud;
import dev.duckvisual.module.impl.visual.HitParticles;
import dev.duckvisual.module.impl.visual.TargetESP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

public final class EventHooks {
    private EventHooks() {}

    /** Last entity the local player attacked and when, shared by TargetESP / TargetHud. */
    public static LivingEntity target;
    public static long targetTime;

    public static void onAttack(Entity entity) {
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        boolean crit = mc.player != null && dev.duckvisual.module.impl.visual.CritIndicator.canCrit(mc.player)
                && entity instanceof LivingEntity;
        var ci = DuckVisual.modules().get(dev.duckvisual.module.impl.visual.CritIndicator.class);
        if (ci.isEnabled()) ci.onHit(crit);
        var di = DuckVisual.modules().get(dev.duckvisual.module.impl.visual.DamageIndicators.class);
        if (di.isEnabled()) di.markHit(entity, crit);
        var hm = DuckVisual.modules().get(dev.duckvisual.module.impl.visual.Hitmarker.class);
        if (hm.isEnabled()) hm.onHit(crit);
        var combat = DuckVisual.modules().get(dev.duckvisual.module.impl.hud.CombatInfo.class);
        if (combat.isEnabled()) combat.onAttack(entity);
        if (entity instanceof LivingEntity living) {
            target = living;
            targetTime = System.currentTimeMillis();
        }
        HitParticles hp = DuckVisual.modules().get(HitParticles.class);
        if (hp.isEnabled()) hp.spawn(entity);
        TargetESP esp = DuckVisual.modules().get(TargetESP.class);
        if (esp.isEnabled()) esp.onHit();
        var sounds = DuckVisual.modules().get(dev.duckvisual.module.impl.visual.HitSounds.class);
        if (sounds.isEnabled()) sounds.play();
        TargetHud hud = DuckVisual.modules().get(TargetHud.class);
        if (hud.isEnabled()) hud.onHit();
    }

    /** Returns the current combat target if it is still valid (alive, nearby, recently hit). */
    public static LivingEntity currentTarget(long timeoutMs) {
        if (target == null) return null;
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc.player == null || target.isRemoved() || !target.isAlive()
                || target.squaredDistanceTo(mc.player) > 12 * 12
                || System.currentTimeMillis() - targetTime > timeoutMs) {
            return null;
        }
        return target;
    }
}
