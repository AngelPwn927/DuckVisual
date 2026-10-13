package dev.duckvisual.module.impl.visual;

import dev.duckvisual.DuckVisual;
import dev.duckvisual.EventHooks;
import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.module.impl.hud.Notifications;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.setting.ModeSetting;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.sound.SoundEvents;

/** Client-side effect when the entity you were fighting dies. */
public class KillEffects extends Module {
    private final ModeSetting effect = add(new ModeSetting("Effect", "Lightning", "Lightning", "Particles", "Both"));
    private final BooleanSetting sound = add(new BooleanSetting("Sound", true));
    private final BooleanSetting notify = add(new BooleanSetting("Notification", true));

    private LivingEntity tracked;
    private int kills;

    public KillEffects() {
        super("KillEffects", "Lightning and particles when your target dies", Category.VISUALS);
    }

    @Override
    public String getSuffix() {
        return kills > 0 ? String.valueOf(kills) : null;
    }

    public int getKills() {
        return kills;
    }

    @Override
    public void onTick() {
        LivingEntity t = EventHooks.target;
        if (t != null && System.currentTimeMillis() - EventHooks.targetTime < 3000) tracked = t;
        if (tracked == null) return;
        if (tracked.isDead() || tracked.getHealth() <= 0) {
            trigger(tracked);
            tracked = null;
            EventHooks.target = null;
        } else if (tracked.isRemoved() || tracked.squaredDistanceTo(mc.player) > 64 * 64) {
            tracked = null;
        }
    }

    private void trigger(LivingEntity dead) {
        kills++;
        if (!effect.is("Particles")) {
            LightningEntity bolt = new LightningEntity(EntityType.LIGHTNING_BOLT, mc.world);
            bolt.setCosmetic(true);
            bolt.refreshPositionAfterTeleport(dead.getPos());
            mc.world.addEntity(bolt);
        }
        if (!effect.is("Lightning")) {
            HitParticles hp = DuckVisual.modules().get(HitParticles.class);
            for (int i = 0; i < 3; i++) hp.spawn(dead);
        }
        if (sound.get()) {
            mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.4f, 0.7f));
        }
        if (notify.get()) {
            Notifications n = DuckVisual.modules().get(Notifications.class);
            if (n.isEnabled()) n.push(dead.getName().getString(), "killed", true);
        }
    }
}
