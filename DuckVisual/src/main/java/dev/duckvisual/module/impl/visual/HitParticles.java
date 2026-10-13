package dev.duckvisual.module.impl.visual;

import dev.duckvisual.event.WorldRender;
import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.setting.ModeSetting;
import dev.duckvisual.setting.NumberSetting;
import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Render3D;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class HitParticles extends Module {
    private final ModeSetting shape = add(new ModeSetting("Shape", "Orbs", "Orbs", "Stars", "Hearts", "Cubes"));
    private final NumberSetting count = add(new NumberSetting("Count", 12, 2, 40, 1));
    private final NumberSetting life = add(new NumberSetting("Life", 2.0, 0.5, 5, 0.1));
    private final NumberSetting size = add(new NumberSetting("Size", 1.0, 0.3, 3, 0.1));
    private final BooleanSetting physics = add(new BooleanSetting("Physics", true));

    private final List<Particle> particles = new ArrayList<>();
    private final Random random = new Random();

    public HitParticles() {
        super("HitParticles", "Spawns glowing particles when you hit something", Category.VISUALS);
    }

    @Override
    public String getSuffix() {
        return shape.get();
    }

    public void spawn(Entity target) {
        Vec3d center = target.getPos().add(0, target.getHeight() / 2, 0);
        for (int i = 0; i < count.getInt(); i++) {
            Vec3d vel = new Vec3d(random.nextGaussian() * 0.12, random.nextDouble() * 0.25 + 0.05, random.nextGaussian() * 0.12);
            particles.add(new Particle(center, vel, System.currentTimeMillis(), random.nextInt(40), random.nextFloat() * 360));
        }
    }

    @Override
    public void onTick() {
        for (Particle p : particles) {
            p.prev = p.pos;
            if (physics.get()) {
                p.vel = p.vel.add(0, -0.025, 0).multiply(0.96);
                Vec3d next = p.pos.add(p.vel);
                // bounce on solid blocks
                if (!mc.world.getBlockState(BlockPos.ofFloored(next)).getCollisionShape(mc.world, BlockPos.ofFloored(next)).isEmpty()) {
                    p.vel = new Vec3d(p.vel.x * 0.6, -p.vel.y * 0.45, p.vel.z * 0.6);
                    next = p.pos.add(p.vel);
                }
                p.pos = next;
            } else {
                p.vel = p.vel.multiply(0.9);
                p.pos = p.pos.add(p.vel);
            }
            p.spin += 12;
        }
    }

    @Override
    public void onRender3D(WorldRender r) {
        long now = System.currentTimeMillis();
        long lifeMs = (long) (life.get() * 1000);
        particles.removeIf(p -> now - p.born > lifeMs);
        if (particles.isEmpty()) return;
        Render3D.additive(true);
        for (Particle p : particles) {
            float age = (now - p.born) / (float) lifeMs;
            float fade = age < 0.8f ? 1 : 1 - (age - 0.8f) / 0.2f;
            Vec3d pos = r.rel(p.prev.lerp(p.pos, r.tickDelta()));
            int color = ColorUtil.fade(ColorUtil.theme(p.colorIndex), fade);
            float s = 0.12f * size.getFloat();
            switch (shape.get()) {
                case "Stars" -> star(r, pos, s * 1.3f, p.spin, color);
                case "Hearts" -> heart(r, pos, s * 1.3f, color);
                case "Cubes" -> cube(r, pos, s * 0.6f, p.spin, color);
                default -> Render3D.orb(r, pos, s * 1.6f, color);
            }
        }
        Render3D.additive(false);
    }

    private void star(WorldRender r, Vec3d pos, float s, float spin, int color) {
        MatrixStack ms = r.matrices();
        ms.push();
        ms.translate(pos.x, pos.y, pos.z);
        ms.multiply(r.camera().getRotation());
        ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(spin));
        Matrix4f m = ms.peek().getPositionMatrix();
        BufferBuilder b = Render3D.buffer(VertexFormat.DrawMode.TRIANGLE_FAN);
        b.vertex(m, 0, 0, 0).color(color);
        for (int i = 0; i <= 10; i++) {
            double a = Math.PI * 2 * i / 10 - Math.PI / 2;
            float rad = i % 2 == 0 ? s : s * 0.42f;
            b.vertex(m, (float) Math.cos(a) * rad, (float) Math.sin(a) * rad, 0).color(ColorUtil.fade(color, 0.7f));
        }
        Render3D.draw(b);
        ms.pop();
        Render3D.glow(r, pos, s * 1.6f, ColorUtil.fade(color, 0.35f));
    }

    private void heart(WorldRender r, Vec3d pos, float s, int color) {
        MatrixStack ms = r.matrices();
        ms.push();
        ms.translate(pos.x, pos.y, pos.z);
        ms.multiply(r.camera().getRotation());
        Matrix4f m = ms.peek().getPositionMatrix();
        BufferBuilder b = Render3D.buffer(VertexFormat.DrawMode.TRIANGLE_FAN);
        b.vertex(m, 0, 0, 0).color(color);
        for (int i = 0; i <= 40; i++) {
            double t = Math.PI * 2 * i / 40;
            double x = 16 * Math.pow(Math.sin(t), 3);
            double y = 13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t);
            // camera-space Y points down after the billboard rotation, so negate y
            b.vertex(m, (float) (x / 16 * s), (float) (-y / 16 * s), 0).color(ColorUtil.fade(color, 0.8f));
        }
        Render3D.draw(b);
        ms.pop();
    }

    private void cube(WorldRender r, Vec3d pos, float s, float spin, int color) {
        MatrixStack ms = r.matrices();
        ms.push();
        ms.translate(pos.x, pos.y, pos.z);
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(spin));
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(spin * 0.7f));
        Matrix4f m = ms.peek().getPositionMatrix();
        BufferBuilder b = Render3D.buffer(VertexFormat.DrawMode.DEBUG_LINES);
        float[][] v = {{-s, -s, -s}, {s, -s, -s}, {s, s, -s}, {-s, s, -s}, {-s, -s, s}, {s, -s, s}, {s, s, s}, {-s, s, s}};
        int[][] e = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] edge : e) {
            b.vertex(m, v[edge[0]][0], v[edge[0]][1], v[edge[0]][2]).color(color);
            b.vertex(m, v[edge[1]][0], v[edge[1]][1], v[edge[1]][2]).color(color);
        }
        Render3D.draw(b);
        ms.pop();
        Render3D.glow(r, pos, s * 3, ColorUtil.fade(color, 0.3f));
    }

    @Override
    public void onDisable() {
        particles.clear();
    }

    private static class Particle {
        Vec3d pos, prev, vel;
        final long born;
        final int colorIndex;
        float spin;

        Particle(Vec3d pos, Vec3d vel, long born, int colorIndex, float spin) {
            this.pos = pos;
            this.prev = pos;
            this.vel = vel;
            this.born = born;
            this.colorIndex = colorIndex;
            this.spin = spin;
        }
    }
}
