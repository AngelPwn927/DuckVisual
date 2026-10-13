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
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/** Health bar and HP number above entities' heads. */
public class HealthTags extends Module {
    private final BooleanSetting players = add(new BooleanSetting("Players", true));
    private final BooleanSetting mobs = add(new BooleanSetting("Mobs", false));
    private final BooleanSetting number = add(new BooleanSetting("Number", true));
    private final NumberSetting range = add(new NumberSetting("Range", 32, 8, 96, 1));

    public HealthTags() {
        super("HealthTags", "Health bars above heads", Category.VISUALS);
    }

    @Override
    public void onRender3D(WorldRender r) {
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity le) || e == mc.player || !e.isAlive() || e instanceof ArmorStandEntity) continue;
            if (e.isInvisible() || e.squaredDistanceTo(mc.player) > range.get() * range.get()) continue;
            if (e instanceof PlayerEntity ? !players.get() : !mobs.get()) continue;
            float hp = le.getHealth(), max = Math.max(1, le.getMaxHealth()), abs = le.getAbsorptionAmount();
            float f = Math.min(1, hp / max);
            // sit above the vanilla name tag
            Vec3d pos = r.rel(e.getLerpedPos(r.tickDelta())).add(0, e.getHeight() + (e instanceof PlayerEntity ? 0.75 : 0.45), 0);
            int c = ColorUtil.lerp(0xFFFF4040, 0xFF40FF70, f);
            Render3D.bar(r, pos, 0.8f, 0.07f, f, 0xA0101014, c);
            if (abs > 0) Render3D.bar(r, pos.add(0, -0.075, 0), 0.8f, 0.03f, Math.min(1, abs / max), 0x00000000, 0xFFFFCC33);
            if (number.get()) {
                String s = String.format("%.1f", hp + abs);
                Render3D.text(r, s, pos.add(0, 0.06, 0), 0.8f, c, false);
            }
        }
    }
}
