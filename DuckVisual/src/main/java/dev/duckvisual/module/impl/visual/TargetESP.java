package dev.duckvisual.module.impl.visual;

import dev.duckvisual.EventHooks;
import dev.duckvisual.event.WorldRender;
import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.ModeSetting;
import dev.duckvisual.setting.NumberSetting;
import dev.duckvisual.util.Animation;
import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Render3D;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public class TargetESP extends Module {
    private final ModeSetting mode = add(new ModeSetting("Mode", "Ghosts", "Ghosts", "Circle", "Marker", "Souls"));
    private final NumberSetting speed = add(new NumberSetting("Speed", 1.0, 0.2, 3, 0.1));
    private final NumberSetting size = add(new NumberSetting("Size", 1.0, 0.5, 2, 0.1));

    private LivingEntity last;
    private float appear;
    private float hitPulse;

    public TargetESP() {
        super("TargetESP", "Highlights the entity you are fighting", Category.VISUALS);
    }

    public void onHit() {
        hitPulse = 1;
    }

    @Override
    public String getSuffix() {
        return mode.get();
    }

    @Override
    public void onRender3D(WorldRender r) {
        LivingEntity target = EventHooks.currentTarget(4000);
        if (target != null) last = target;
        appear = Animation.approach(appear, target != null ? 1 : 0, 8);
        hitPulse = Animation.approach(hitPulse, 0, 5);
        if (last == null || appear <= 0.01f) return;

        Vec3d pos = r.rel(last.getLerpedPos(r.tickDelta()));
        float h = last.getHeight();
        float w = last.getWidth() * 0.9f * size.getFloat();
        double time = System.currentTimeMillis() / 1000.0 * speed.get();
        // flash red-ish while the target is hurt
        float hurt = last.hurtTime > 0 ? last.hurtTime / 10f : 0;

        Render3D.depth(false);
        switch (mode.get()) {
            case "Circle" -> circle(r, pos, w, h, time, hurt);
            case "Marker" -> marker(r, pos, h, time, hurt);
            case "Souls" -> souls(r, pos, w, h, time, hurt);
            default -> ghosts(r, pos, w, h, time, hurt);
        }
        Render3D.depth(true);
    }

    private int color(int index, float hurt) {
        int c = ColorUtil.lerp(ColorUtil.theme(index), 0xFFFF3030, hurt * 0.6f);
        return ColorUtil.fade(c, appear);
    }

    /** Three spiralling chains of glowing orbs around the target. */
    private void ghosts(WorldRender r, Vec3d pos, float w, float h, double time, float hurt) {
        Render3D.additive(true);
        int chains = 3, length = 14;
        float radius = w * 0.75f + hitPulse * 0.15f;
        for (int c = 0; c < chains; c++) {
            for (int i = 0; i < length; i++) {
                double t = time * 2.2 - i * 0.07 + c * (Math.PI * 2 / chains);
                double y = h / 2 + Math.sin(time * 1.4 + c * 2.1 - i * 0.05) * h * 0.4;
                double x = Math.cos(t) * radius;
                double z = Math.sin(t) * radius;
                float f = 1 - i / (float) length;
                Render3D.glow(r, pos.add(x, y, z), (0.14f * f + 0.03f) * size.getFloat(), ColorUtil.fade(color(c * 6 + i, hurt), f * 0.6f));
            }
        }
        Render3D.additive(false);
    }

    /** A gradient ring moving up and down the hitbox with a trailing skirt. */
    private void circle(WorldRender r, Vec3d pos, float w, float h, double time, float hurt) {
        double phase = (Math.sin(time * 2) + 1) / 2;
        double prev = (Math.sin(time * 2 - 0.35) + 1) / 2;
        float y1 = (float) (phase * h), y2 = (float) (prev * h);
        Matrix4f m = r.matrices().peek().getPositionMatrix();
        int seg = 72;
        float rad = w * 0.8f;
        BufferBuilder b = Render3D.buffer(VertexFormat.DrawMode.TRIANGLE_STRIP);
        for (int i = 0; i <= seg; i++) {
            double a = Math.PI * 2 * i / seg;
            float x = (float) (pos.x + Math.cos(a) * rad), z = (float) (pos.z + Math.sin(a) * rad);
            int c = color(Math.min(i, seg - i), hurt);
            b.vertex(m, x, (float) pos.y + y1, z).color(ColorUtil.fade(c, 0.85f));
            b.vertex(m, x, (float) pos.y + y2, z).color(ColorUtil.withAlpha(c, 0));
        }
        Render3D.draw(b);
        BufferBuilder l = Render3D.buffer(VertexFormat.DrawMode.DEBUG_LINE_STRIP);
        for (int i = 0; i <= seg; i++) {
            double a = Math.PI * 2 * i / seg;
            l.vertex(m, (float) (pos.x + Math.cos(a) * rad), (float) pos.y + y1, (float) (pos.z + Math.sin(a) * rad))
                    .color(color(Math.min(i, seg - i), hurt));
        }
        Render3D.draw(l);
    }

    /** Rotating camera-facing square marker (the classic "target" texture look). */
    private void marker(WorldRender r, Vec3d pos, float h, double time, float hurt) {
        MatrixStack ms = r.matrices();
        ms.push();
        ms.translate(pos.x, pos.y + h / 2, pos.z);
        ms.multiply(r.camera().getRotation());
        ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) (time * 90)));
        float s = (0.55f + hitPulse * 0.2f) * size.getFloat();
        float t = 0.09f;
        for (int k = 0; k < 4; k++) {
            ms.push();
            ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(90 * k));
            Matrix4f m = ms.peek().getPositionMatrix();
            int c = color(k * 5, hurt);
            // L-shaped corner bracket
            BufferBuilder b = Render3D.buffer(VertexFormat.DrawMode.QUADS);
            quad(b, m, s - 0.3f, s - t, s, s, c);
            quad(b, m, s - t, s - 0.3f, s, s - t, c);
            Render3D.draw(b);
            ms.pop();
        }
        ms.pop();
        Render3D.additive(true);
        Render3D.glow(r, pos.add(0, h / 2, 0), 0.5f * size.getFloat(), ColorUtil.fade(color(0, hurt), 0.35f));
        Render3D.additive(false);
    }

    private static void quad(BufferBuilder b, Matrix4f m, float x1, float y1, float x2, float y2, int c) {
        b.vertex(m, x1, y1, 0).color(c);
        b.vertex(m, x1, y2, 0).color(c);
        b.vertex(m, x2, y2, 0).color(c);
        b.vertex(m, x2, y1, 0).color(c);
    }

    /** Orbs rising up around the target like escaping souls. */
    private void souls(WorldRender r, Vec3d pos, float w, float h, double time, float hurt) {
        Render3D.additive(true);
        int count = 10;
        for (int i = 0; i < count; i++) {
            double p = (time * 0.6 + i / (double) count) % 1.0;
            double a = i * 2.399 + time * 0.8;
            float rad = w * (0.6f + 0.2f * (float) Math.sin(i));
            float fade = (float) Math.sin(p * Math.PI);
            Render3D.glow(r, pos.add(Math.cos(a) * rad, p * (h + 0.4), Math.sin(a) * rad),
                    0.18f, ColorUtil.fade(color(i * 3, hurt), fade));
        }
        Render3D.additive(false);
    }
}
