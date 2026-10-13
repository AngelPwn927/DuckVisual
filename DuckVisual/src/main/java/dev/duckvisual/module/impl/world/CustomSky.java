package dev.duckvisual.module.impl.world;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.duckvisual.event.WorldRender;
import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.setting.ModeSetting;
import dev.duckvisual.setting.NumberSetting;
import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Render3D;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Fog;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.joml.Matrix4f;

import java.util.Random;

/**
 * Starry night sky with animated aurora in theme colors. Drawn as a dome beyond the
 * render distance, so it only shows where the vanilla sky is visible.
 */
public class CustomSky extends Module {
    private final ModeSetting mode = add(new ModeSetting("Mode", "Night", "Night", "Aurora"));
    private final BooleanSetting nightOnly = add(new BooleanSetting("Night Only", true));
    private final BooleanSetting aurora = add(new BooleanSetting("Aurora", true));
    private final NumberSetting stars = add(new NumberSetting("Stars", 900, 0, 3000, 50));
    private final NumberSetting auroraStrength = add(new NumberSetting("Aurora Strength", 1, 0.2, 2, 0.1));

    private float[] starData;
    private int starCount = -1;

    public CustomSky() {
        super("CustomSky", "Starry sky with aurora", Category.WORLD);
    }

    @Override
    public String getSuffix() {
        return mode.get();
    }

    private void buildStars() {
        int n = stars.getInt();
        starCount = n;
        starData = new float[n * 5];
        Random r = new Random(1337);
        for (int i = 0; i < n; i++) {
            // uniform direction on the upper hemisphere (+ a bit below the horizon)
            double y = r.nextDouble() * 1.1 - 0.1;
            double a = r.nextDouble() * Math.PI * 2;
            double rad = Math.sqrt(1 - y * y);
            starData[i * 5] = (float) (Math.cos(a) * rad);
            starData[i * 5 + 1] = (float) y;
            starData[i * 5 + 2] = (float) (Math.sin(a) * rad);
            starData[i * 5 + 3] = 0.4f + r.nextFloat() * 1.2f; // size
            starData[i * 5 + 4] = r.nextFloat() * 6.28f;       // twinkle phase
        }
    }

    @Override
    public void onRender3D(WorldRender r) {
        if (mc.world.getRegistryKey() != World.OVERWORLD) return;
        float night = mc.world.getStarBrightness(r.tickDelta()) * 2; // 0 day .. ~1 night
        float vis = nightOnly.get() ? MathHelper.clamp(night, 0, 1) : 1;
        if (vis <= 0.01f) return;
        if (starCount != stars.getInt()) buildStars();

        float radius = mc.options.getClampedViewDistance() * 16 * 2.2f;
        Matrix4f m = r.matrices().peek().getPositionMatrix();
        double t = System.currentTimeMillis() / 1000.0;

        if (mode.is("Night")) {
            Fog fog = RenderSystem.getShaderFog();
            int horizon = ColorUtil.rgba((int) (fog.red() * 255), (int) (fog.green() * 255), (int) (fog.blue() * 255), (int) (255 * vis));
            int zenith = ColorUtil.withAlpha(ColorUtil.lerp(0xFF03030A, ColorUtil.theme(), 0.06f), (int) (255 * vis));
            int mid = ColorUtil.withAlpha(ColorUtil.lerp(0xFF0A0D24, ColorUtil.theme(8), 0.12f), (int) (255 * vis));
            dome(m, radius, horizon, mid, zenith);
        }

        // stars: tiny camera-independent quads on the dome, twinkling
        if (starCount > 0) {
            BufferBuilder b = Render3D.buffer(VertexFormat.DrawMode.QUADS);
            org.joml.Vector3f right = new org.joml.Vector3f(1, 0, 0).rotate(r.camera().getRotation());
            org.joml.Vector3f up = new org.joml.Vector3f(0, 1, 0).rotate(r.camera().getRotation());
            float rot = (float) ((t * 0.004) % (Math.PI * 2));
            float cos = MathHelper.cos(rot), sin = MathHelper.sin(rot);
            for (int i = 0; i < starCount; i++) {
                float x0 = starData[i * 5], y0 = starData[i * 5 + 1], z0 = starData[i * 5 + 2];
                float x = x0 * cos - z0 * sin, z = x0 * sin + z0 * cos;
                float tw = 0.55f + 0.45f * (float) Math.sin(t * 2.2 + starData[i * 5 + 4]);
                float s = starData[i * 5 + 3] * radius * 0.003f;
                int c = ColorUtil.withAlpha(i % 9 == 0 ? ColorUtil.theme(i) : 0xFFFFFFFF, (int) (255 * tw * vis * Math.min(1, Math.max(0, y0 + 0.05) * 5)));
                float px = x * radius, py = y0 * radius, pz = z * radius;
                // camera-facing quad (billboard), like vanilla particles
                float rx = right.x * s, ry = right.y * s, rz = right.z * s;
                float ux = up.x * s, uy = up.y * s, uz = up.z * s;
                b.vertex(m, px - rx - ux, py - ry - uy, pz - rz - uz).color(c);
                b.vertex(m, px + rx - ux, py + ry - uy, pz + rz - uz).color(c);
                b.vertex(m, px + rx + ux, py + ry + uy, pz + rz + uz).color(c);
                b.vertex(m, px - rx + ux, py - ry + uy, pz - rz + uz).color(c);
            }
            Render3D.additive(true);
            Render3D.draw(b);
            Render3D.additive(false);
        }

        if (aurora.get()) auroraBands(m, radius, t, vis);
    }

    /** Sphere with a vertical gradient: horizon → mid → zenith, and dark below the horizon. */
    private void dome(Matrix4f m, float radius, int horizon, int mid, int zenith) {
        int rings = 12, seg = 36;
        int below = ColorUtil.withAlpha(ColorUtil.lerp(horizon, 0xFF000000, 0.6f), ColorUtil.alpha(horizon));
        for (int ring = 0; ring < rings; ring++) {
            float e1 = -0.35f + 1.35f * ring / rings, e2 = -0.35f + 1.35f * (ring + 1) / rings;
            BufferBuilder b = Render3D.buffer(VertexFormat.DrawMode.TRIANGLE_STRIP);
            for (int i = 0; i <= seg; i++) {
                double a = Math.PI * 2 * i / seg;
                for (float y : new float[]{e1, Math.min(1, e2)}) {
                    float rad = (float) Math.sqrt(Math.max(0, 1 - y * y));
                    int c = y < 0 ? ColorUtil.lerp(horizon, below, -y / 0.35f)
                            : y < 0.35f ? ColorUtil.lerp(horizon, mid, y / 0.35f) : ColorUtil.lerp(mid, zenith, (y - 0.35f) / 0.65f);
                    b.vertex(m, (float) Math.cos(a) * rad * radius, y * radius, (float) Math.sin(a) * rad * radius).color(c);
                }
            }
            Render3D.draw(b);
        }
    }

    /** Three waving translucent curtains in theme colors. */
    private void auroraBands(Matrix4f m, float radius, double t, float vis) {
        Render3D.additive(true);
        float strength = auroraStrength.getFloat();
        for (int band = 0; band < 4; band++) {
            BufferBuilder b = Render3D.buffer(VertexFormat.DrawMode.TRIANGLE_STRIP);
            float baseAz = band * 1.6f + 0.4f;
            int seg = 60;
            for (int i = 0; i <= seg; i++) {
                float f = i / (float) seg;
                double az = baseAz + (f - 0.5) * 2.2 + Math.sin(t * 0.15 + band) * 0.2;
                double wave = Math.sin(f * 9 + t * 0.6 + band * 1.7) * 0.06 + Math.sin(f * 23 - t * 0.9) * 0.02;
                float elevLow = 0.12f + (float) wave + (band % 2) * 0.08f;
                float elevHigh = elevLow + 0.32f + (float) Math.sin(f * 5 + t * 0.4 + band) * 0.06f;
                float edge = MathHelper.sin(f * MathHelper.PI); // fade the ends
                float pulse = 0.7f + 0.3f * (float) Math.sin(t * 0.8 + f * 6 + band);
                int c = ColorUtil.theme(band * 7 + (int) (f * 20));
                int low = ColorUtil.withAlpha(c, (int) Math.min(255, 190 * edge * pulse * vis * strength));
                int high = ColorUtil.withAlpha(c, 0);
                float cx = (float) Math.cos(az), cz = (float) Math.sin(az);
                float rl = (float) Math.sqrt(1 - elevLow * elevLow), rh = (float) Math.sqrt(1 - elevHigh * elevHigh);
                float rr = radius * 0.97f;
                b.vertex(m, cx * rl * rr, elevLow * rr, cz * rl * rr).color(low);
                b.vertex(m, cx * rh * rr, elevHigh * rr, cz * rh * rr).color(high);
            }
            Render3D.draw(b);
        }
        Render3D.additive(false);
    }
}
