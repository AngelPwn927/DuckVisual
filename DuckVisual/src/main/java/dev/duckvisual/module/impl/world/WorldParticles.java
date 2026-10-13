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
    private final ModeSetting mode = add(new ModeSetting("Mode", "Fireflies", "Fireflies", "Snow", "Rise", "Sakura"));
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
                default -> new Vec3d((random.nextDouble() - 0.5) * 0.04, (random.nextDouble() - 0.5) * 0.02, (random.nextDouble() - 0.5) * 0.04);
            };
            particles.add(new P(pos, vel, 80 + random.nextInt(120), random.nextInt(40)));
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

    @Override
    public void onDisable() {
        particles.clear();
    }
}
