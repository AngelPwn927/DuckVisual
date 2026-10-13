package dev.duckvisual.module.impl.visual;

import dev.duckvisual.event.WorldRender;
import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.setting.ModeSetting;
import dev.duckvisual.setting.NumberSetting;
import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Render3D;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class BoxESP extends Module {
    private final ModeSetting mode = add(new ModeSetting("Mode", "Box", "Box", "Filled", "Corners"));
    private final BooleanSetting players = add(new BooleanSetting("Players", true));
    private final BooleanSetting monsters = add(new BooleanSetting("Monsters", false));
    private final BooleanSetting animals = add(new BooleanSetting("Animals", false));
    private final BooleanSetting health = add(new BooleanSetting("Health Bar", true));
    private final NumberSetting range = add(new NumberSetting("Range", 64, 8, 256, 1));

    public BoxESP() {
        super("BoxESP", "Draws boxes around entities", Category.VISUALS);
    }

    @Override
    public String getSuffix() {
        return mode.get();
    }

    private boolean valid(Entity e) {
        if (e == mc.player || !(e instanceof LivingEntity) || !e.isAlive()) return false;
        if (e.squaredDistanceTo(mc.player) > range.get() * range.get()) return false;
        if (e instanceof PlayerEntity) return players.get();
        if (e instanceof Monster) return monsters.get();
        if (e instanceof AnimalEntity) return animals.get();
        return false;
    }

    @Override
    public void onRender3D(WorldRender r) {
        Render3D.depth(false);
        int i = 0;
        for (Entity e : mc.world.getEntities()) {
            if (!valid(e)) continue;
            Vec3d pos = r.rel(e.getLerpedPos(r.tickDelta()));
            Box box = e.getBoundingBox().offset(e.getPos().negate()).offset(pos).expand(0.05);
            int c1 = ColorUtil.theme(i * 5), c2 = ColorUtil.theme(i * 5 + 10);
            if (e instanceof LivingEntity le && le.hurtTime > 0) {
                c1 = ColorUtil.lerp(c1, 0xFFFF3030, le.hurtTime / 10f);
                c2 = ColorUtil.lerp(c2, 0xFFFF3030, le.hurtTime / 10f);
            }
            switch (mode.get()) {
                case "Filled" -> {
                    Render3D.filledBox(r, box, ColorUtil.withAlpha(c1, 40), ColorUtil.withAlpha(c2, 70));
                    Render3D.outlineBox(r, box, c1, c2);
                }
                case "Corners" -> corners(r, box, c1, c2);
                default -> Render3D.outlineBox(r, box, c1, c2);
            }
            if (health.get() && e instanceof LivingEntity le) healthBar(r, le, box);
            i++;
        }
        Render3D.depth(true);
    }

    private void corners(WorldRender r, Box b, int bottom, int top) {
        double lx = (b.maxX - b.minX) * 0.25, ly = (b.maxY - b.minY) * 0.2, lz = (b.maxZ - b.minZ) * 0.25;
        double[] xs = {b.minX, b.maxX}, zs = {b.minZ, b.maxZ};
        for (double x : xs) for (double z : zs) {
            double dx = x == b.minX ? lx : -lx, dz = z == b.minZ ? lz : -lz;
            for (int k = 0; k < 2; k++) {
                double y = k == 0 ? b.minY : b.maxY, dy = k == 0 ? ly : -ly;
                int c = k == 0 ? bottom : top;
                Vec3d p = new Vec3d(x, y, z);
                Render3D.line(r, p, p.add(dx, 0, 0), c, c);
                Render3D.line(r, p, p.add(0, dy, 0), c, c);
                Render3D.line(r, p, p.add(0, 0, dz), c, c);
            }
        }
    }

    /** Vertical health bar on the box edge that faces the camera's right side. */
    private void healthBar(WorldRender r, LivingEntity e, Box b) {
        float f = Math.max(0, Math.min(1, e.getHealth() / e.getMaxHealth()));
        double yaw = Math.toRadians(r.camera().getYaw());
        // camera right vector on the XZ plane
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        double half = (b.maxX - b.minX) / 2 + 0.12;
        Vec3d base = new Vec3d((b.minX + b.maxX) / 2 + rx * half, b.minY, (b.minZ + b.maxZ) / 2 + rz * half);
        double h = b.maxY - b.minY;
        Render3D.line(r, base, base.add(0, h, 0), 0xA0000000, 0xA0000000);
        int c = ColorUtil.lerp(0xFFFF4040, 0xFF40FF70, f);
        Render3D.line(r, base, base.add(0, h * f, 0), c, c);
    }
}
