package dev.duckvisual.module.impl.world;

import dev.duckvisual.event.WorldRender;
import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.ModeSetting;
import dev.duckvisual.setting.NumberSetting;
import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Render3D;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Ambient glowing particles floating around the player (fireflies / snow). */
public class WorldParticles extends Module {
    private final ModeSetting mode = add(new ModeSetting("Mode", "Crystals", "Crystals", "Fireflies", "Snow", "Rise", "Sakura"));
    private final NumberSetting amount = add(new NumberSetting("Amount", 60, 10, 300, 5));
    private final NumberSetting range = add(new NumberSetting("Range", 12, 4, 32, 1));
    private final NumberSetting size = add(new NumberSetting("Size", 1, 0.3, 3, 0.1));

    private final List<P> particles = new ArrayList<>();
    private final Random random = new Random();

    private static class P {
        Vec3d pos, prev, vel;
        int age;
        final int maxAge, color;

        P(Vec3d pos, Vec3d vel, int maxAge, int color) {
            this.pos = pos;
            this.prev = pos;
            this.vel = vel;
            this.maxAge = maxAge;
            this.color = color;
        }
    }

    public WorldParticles() {
        super("WorldParticles", "Ambient glowing particles", Category.WORLD);
    }

    @Override
    public String getSuffix() {
        return mode.get();
    }

    @Override
    public void onTick() {
        Vec3d me = mc.player.getPos();
        double r = range.get();
        while (particles.size() < amount.getInt()) {
            Vec3d pos = me.add((random.nextDouble() - 0.5) * 2 * r, random.nextDouble() * r * 0.6 - 1, (random.nextDouble() - 0.5) * 2 * r);
            Vec3d vel = switch (mode.get()) {
                case "Snow" -> new Vec3d((random.nextDouble() - 0.5) * 0.02, -0.03 - random.nextDouble() * 0.03, (random.nextDouble() - 0.5) * 0.02);
                case "Rise" -> new Vec3d(0, 0.02 + random.nextDouble() * 0.03, 0);
                case "Sakura" -> new Vec3d(0.01 + random.nextDouble() * 0.02, -0.02 - random.nextDouble() * 0.02, (random.nextDouble() - 0.5) * 0.02);
                case "Crystals" -> new Vec3d((random.nextDouble() - 0.5) * 0.012, 0.004 + random.nextDouble() * 0.01, (random.nextDouble() - 0.5) * 0.012);
                default -> new Vec3d((random.nextDouble() - 0.5) * 0.04, (random.nextDouble() - 0.5) * 0.02, (random.nextDouble() - 0.5) * 0.04);
            };
            if (mode.is("Crystals")) pos = pos.add(0, r * 0.3, 0); // they hang higher, in the air around you
            particles.add(new P(pos, vel, (mode.is("Crystals") ? 220 : 80) + random.nextInt(120), random.nextInt(40)));
        }
        for (P p : particles) {
            p.prev = p.pos;
            if (mode.is("Sakura")) {
                // petals sway side to side while falling
                p.vel = new Vec3d(p.vel.x, p.vel.y, Math.sin((p.age + p.color * 7) * 0.12) * 0.03);
            }
            if (mode.is("Fireflies")) {
                p.vel = p.vel.add((random.nextDouble() - 0.5) * 0.01, (random.nextDouble() - 0.5) * 0.006, (random.nextDouble() - 0.5) * 0.01).multiply(0.97);
            }
            p.pos = p.pos.add(p.vel);
            p.age++;
        }
        particles.removeIf(p -> p.age > p.maxAge || p.pos.squaredDistanceTo(me) > r * r * 2.5);
    }

    @Override
    public void onRender3D(WorldRender r) {
        if (mode.is("Crystals")) {
            crystals(r);
            return;
        }
        Render3D.additive(true);
        for (P p : particles) {
            float t = p.age / (float) p.maxAge;
            float fade = (float) Math.sin(Math.min(1, t) * Math.PI);
            Vec3d pos = r.rel(p.prev.lerp(p.pos, r.tickDelta()));
            // fade out particles right in front of the camera so they don't cover the screen
            fade *= (float) Math.min(1, Math.max(0, (pos.length() - 1.5) / 2.5));
            if (fade <= 0.01f) continue;
            int c = mode.is("Snow") ? 0xFFFFFFFF
                    : mode.is("Sakura") ? (p.color % 3 == 0 ? 0xFFFFB7D5 : p.color % 3 == 1 ? 0xFFFF8FB8 : 0xFFFFD6E8)
                    : ColorUtil.theme(p.color);
            Render3D.orb(r, pos, 0.12f * size.getFloat(), ColorUtil.fade(c, fade * 0.9f));
        }
        Render3D.additive(false);
    }

    /** Slowly spinning glowing octahedra: faint faces, bright edges and a soft halo, all in one draw each. */
    private void crystals(WorldRender r) {
        org.joml.Matrix4f m = r.matrices().peek().getPositionMatrix();
        float time = (System.currentTimeMillis() % 100000) / 1000f;
        net.minecraft.client.render.BufferBuilder faces = Render3D.buffer(net.minecraft.client.render.VertexFormat.DrawMode.TRIANGLES);
        java.util.List<float[]> edges = new ArrayList<>();
        java.util.List<Object[]> halos = new ArrayList<>(); // drawn after the faces: one Tessellator buffer at a time
        Render3D.additive(true);
        for (P p : particles) {
            float t = p.age / (float) p.maxAge;
            float fade = (float) Math.sin(Math.min(1, t) * Math.PI);
            Vec3d pos = r.rel(p.prev.lerp(p.pos, r.tickDelta()));
            fade *= (float) Math.min(1, Math.max(0, (pos.length() - 2) / 3));
            if (fade <= 0.01f) continue;
            float s = (0.16f + (p.color % 5) * 0.03f) * size.getFloat();
            float ang = time * (0.6f + (p.color % 7) * 0.12f) + p.color;
            float cos = (float) Math.cos(ang), sin = (float) Math.sin(ang);
            float tilt = 0.5f + (p.color % 3) * 0.2f;
            // octahedron: 4 points around (rotated about Y, tilted) + top and bottom
            float[][] v = new float[6][];
            for (int i = 0; i < 4; i++) {
                double a = Math.PI / 2 * i;
                float x = (float) Math.cos(a) * s, z = (float) Math.sin(a) * s;
                float rx = x * cos - z * sin, rz = x * sin + z * cos;
                v[i] = new float[]{(float) pos.x + rx, (float) pos.y + rz * tilt * 0.3f, (float) pos.z + rz};
            }
            v[4] = new float[]{(float) pos.x, (float) pos.y + s * 1.5f, (float) pos.z};
            v[5] = new float[]{(float) pos.x, (float) pos.y - s * 1.5f, (float) pos.z};
            int base = ColorUtil.lerp(0xFFBFD8FF, ColorUtil.theme(p.color), 0.25f);
            int face = ColorUtil.fade(base, fade * 0.16f);
            for (int i = 0; i < 4; i++) {
                float[] a = v[i], b = v[(i + 1) % 4];
                for (float[] tip : new float[][]{v[4], v[5]}) {
                    faces.vertex(m, a[0], a[1], a[2]).color(face);
                    faces.vertex(m, b[0], b[1], b[2]).color(face);
                    faces.vertex(m, tip[0], tip[1], tip[2]).color(ColorUtil.fade(base, fade * 0.3f));
                }
                edges.add(new float[]{a[0], a[1], a[2], b[0], b[1], b[2], fade});
                edges.add(new float[]{a[0], a[1], a[2], v[4][0], v[4][1], v[4][2], fade});
                edges.add(new float[]{a[0], a[1], a[2], v[5][0], v[5][1], v[5][2], fade});
            }
            halos.add(new Object[]{pos, s * 2.2f, ColorUtil.fade(base, fade * 0.35f)});
        }
        Render3D.draw(faces);
        net.minecraft.client.render.BufferBuilder lines = Render3D.buffer(net.minecraft.client.render.VertexFormat.DrawMode.DEBUG_LINES);
        for (float[] e : edges) {
            int c = ColorUtil.fade(0xFFE8F1FF, e[6] * 0.85f);
            lines.vertex(m, e[0], e[1], e[2]).color(c);
            lines.vertex(m, e[3], e[4], e[5]).color(c);
        }
        Render3D.draw(lines);
        for (Object[] h : halos) Render3D.orb(r, (Vec3d) h[0], (Float) h[1], (Integer) h[2]);
        Render3D.additive(false);
    }

    @Override
    public void onDisable() {
        particles.clear();
    }
}
