package dev.duckvisual.module.impl.visual;

import dev.duckvisual.event.WorldRender;
import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.setting.NumberSetting;
import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Render3D;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Floating damage / heal numbers above entities; critical hits by you are highlighted. */
public class DamageIndicators extends Module {
    private final BooleanSetting heals = add(new BooleanSetting("Heals", true));
    private final BooleanSetting onlyTarget = add(new BooleanSetting("Only Your Hits", false));
    private final BooleanSetting self = add(new BooleanSetting("Self", false));
    private final NumberSetting scale = add(new NumberSetting("Scale", 1.0, 0.5, 2.5, 0.1));
    private final NumberSetting duration = add(new NumberSetting("Duration", 1.2, 0.4, 3, 0.1));

    private final Map<Integer, Float> lastHealth = new HashMap<>();
    private final List<Popup> popups = new ArrayList<>();
    private final Random random = new Random();

    /** Last hit by the local player, set through {@link #markHit}. */
    private int pendingEntity = -1;
    private boolean pendingCrit;
    private long pendingTime;

    private record Popup(Vec3d pos, Vec3d drift, String text, int color, boolean crit, long born) {}

    public DamageIndicators() {
        super("DamageIndicators", "Floating damage numbers, crits highlighted", Category.VISUALS);
    }

    /** Called when the local player attacks: remembers whether that hit was a critical. */
    public void markHit(Entity target, boolean crit) {
        pendingEntity = target.getId();
        pendingCrit = crit;
        pendingTime = System.currentTimeMillis();
    }

    @Override
    public void onTick() {
        Map<Integer, Float> seen = new HashMap<>();
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity le)) continue;
            if (e == mc.player && !self.get()) continue;
            if (e.squaredDistanceTo(mc.player) > 48 * 48) continue;
            float hp = le.getHealth() + le.getAbsorptionAmount();
            Float prev = lastHealth.get(e.getId());
            seen.put(e.getId(), hp);
            if (prev == null) continue;
            float diff = hp - prev;
            if (Math.abs(diff) < 0.05f) continue;
            boolean mine = e.getId() == pendingEntity && System.currentTimeMillis() - pendingTime < 600;
            if (onlyTarget.get() && !mine) continue;
            if (diff > 0 && !heals.get()) continue;
            boolean crit = mine && pendingCrit && diff < 0;
            String text = (diff > 0 ? "+" : "") + format(Math.abs(diff)) + (crit ? "!" : "");
            int color = diff > 0 ? 0xFF55FF77 : crit ? 0xFFFFD43B : 0xFFFF5555;
            // spawn above the HealthTags bar so the two never overlap
            Vec3d pos = e.getPos().add((random.nextDouble() - 0.5) * 0.8, e.getHeight() + 0.95, (random.nextDouble() - 0.5) * 0.8);
            popups.add(new Popup(pos, new Vec3d((random.nextDouble() - 0.5) * 0.3, 0.6, (random.nextDouble() - 0.5) * 0.3),
                    text, color, crit, System.currentTimeMillis()));
            if (mine) pendingEntity = -1;
        }
        lastHealth.clear();
        lastHealth.putAll(seen);
        if (popups.size() > 60) popups.subList(0, popups.size() - 60).clear();
    }

    private static String format(float v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.format("%.1f", v);
    }

    @Override
    public void onRender3D(WorldRender r) {
        long now = System.currentTimeMillis();
        long life = (long) (duration.get() * 1000);
        popups.removeIf(p -> now - p.born > life);
        for (Popup p : popups) {
            float t = (now - p.born) / (float) life;
            // quick pop-in, float up, fade out
            float pop = t < 0.12f ? t / 0.12f : 1;
            float fade = t > 0.7f ? 1 - (t - 0.7f) / 0.3f : 1;
            Vec3d pos = r.rel(p.pos.add(p.drift.multiply(1 - Math.pow(1 - t, 2))));
            float s = scale.getFloat() * (p.crit ? 1.5f : 1f) * (0.6f + 0.4f * pop);
            Render3D.text(r, p.text, pos, s, ColorUtil.fade(p.color, fade), true);
        }
    }

    @Override
    public void onDisable() {
        popups.clear();
        lastHealth.clear();
    }
}
