package dev.duckvisual.module.impl.visual;

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
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import org.joml.Matrix4f;

/**
 * Replaces player models with simple animated box models. The vanilla model is
 * skipped in EntityRenderDispatcherMixin and the replacement is drawn in the world pass.
 */
public class CustomModels extends Module {
    private final ModeSetting model = add(new ModeSetting("Model", "Duck", "Duck", "Amogus", "Rabbit", "Penguin"));
    private final BooleanSetting others = add(new BooleanSetting("Others", false));
    private final BooleanSetting themeColor = add(new BooleanSetting("Theme Color", false));
    private final NumberSetting scale = add(new NumberSetting("Scale", 1.0, 0.5, 1.5, 0.05));

    /** Faces of the box currently being built. */
    private BufferBuilder buf;
    private Matrix4f mat;
    private float light = 1;

    public CustomModels() {
        super("CustomModels", "Replaces player models (Duck, Amogus...)", Category.VISUALS);
    }

    @Override
    public String getSuffix() {
        return model.get();
    }

    public boolean replaces(Entity e) {
        if (!isEnabled() || !(e instanceof PlayerEntity) || e.isInvisible()) return false;
        return e == mc.player || others.get();
    }

    @Override
    public void onRender3D(WorldRender r) {
        RenderSystem.depthMask(true);
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (!replaces(p)) continue;
            if (p == mc.player && mc.options.getPerspective().isFirstPerson() && mc.getCameraEntity() == mc.player) continue;
            draw(r, p);
        }
        RenderSystem.depthMask(false);
    }

    private void draw(WorldRender r, PlayerEntity p) {
        float td = r.tickDelta();
        Vec3d pos = r.rel(p.getLerpedPos(td));
        float bodyYaw = MathHelper.lerpAngleDegrees(td, p.prevBodyYaw, p.bodyYaw);
        float headYaw = MathHelper.lerpAngleDegrees(td, p.prevHeadYaw, p.headYaw);
        float pitch = p.getPitch(td);
        float limbPos = p.limbAnimator.getPos(td);
        float limbSpeed = Math.min(1, p.limbAnimator.getSpeed(td));
        float swing = (float) Math.cos(limbPos * 0.6662f) * 1.2f * limbSpeed;
        float hurt = p.hurtTime > 0 ? p.hurtTime / 10f : 0;

        BlockPos bp = BlockPos.ofFloored(p.getEyePos());
        int block = mc.world.getLightLevel(LightType.BLOCK, bp);
        int sky = mc.world.getLightLevel(LightType.SKY, bp) - mc.world.getAmbientDarkness();
        light = 0.25f + 0.75f * Math.max(block, Math.max(0, sky)) / 15f;

        MatrixStack ms = r.matrices();
        ms.push();
        ms.translate(pos.x, pos.y, pos.z);
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-bodyYaw));
        float s = scale.getFloat();
        ms.scale(s, s, s);
        if (p.isInSneakingPose()) ms.translate(0, -0.15, 0);

        buf = Render3D.buffer(VertexFormat.DrawMode.QUADS);
        switch (model.get()) {
            case "Amogus" -> amogus(ms, swing, headYaw - bodyYaw, hurt);
            case "Rabbit" -> rabbit(ms, swing, headYaw - bodyYaw, pitch, hurt);
            case "Penguin" -> penguin(ms, swing, headYaw - bodyYaw, pitch, hurt);
            default -> duck(ms, swing, headYaw - bodyYaw, pitch, hurt);
        }
        Render3D.draw(buf);
        ms.pop();
    }

    private int main(int def, float hurt) {
        int c = themeColor.get() ? ColorUtil.theme() : def;
        return ColorUtil.lerp(c, 0xFFFF3030, hurt * 0.6f);
    }

    // ------------------------------------------------------------------ models (front = +Z, units = blocks)

    private void duck(MatrixStack ms, float swing, float headYaw, float pitch, float hurt) {
        int yellow = main(0xFFFFD43B, hurt), orange = 0xFFFF8A1F, dark = ColorUtil.lerp(yellow, 0xFF000000, 0.15f);
        leg(ms, -0.13f, swing, orange, 0.07f, 0.32f, true);
        leg(ms, 0.13f, -swing, orange, 0.07f, 0.32f, true);
        box(ms, -0.32f, 0.32f, -0.42f, 0.32f, 0.82f, 0.38f, yellow);          // body
        box(ms, -0.18f, 0.62f, -0.55f, 0.18f, 0.86f, -0.40f, yellow);         // tail
        box(ms, -0.36f, 0.45f, -0.30f, -0.32f, 0.74f, 0.22f, dark);           // wings
        box(ms, 0.32f, 0.45f, -0.30f, 0.36f, 0.74f, 0.22f, dark);
        head(ms, 0, 0.82f, 0.20f, headYaw, pitch, () -> {
            box(ms, -0.21f, 0, -0.18f, 0.21f, 0.42f, 0.22f, yellow);
            box(ms, -0.13f, 0.08f, 0.22f, 0.13f, 0.18f, 0.42f, orange);      // beak
            box(ms, -0.17f, 0.24f, 0.21f, -0.09f, 0.32f, 0.23f, 0xFF111111); // eyes
            box(ms, 0.09f, 0.24f, 0.21f, 0.17f, 0.32f, 0.23f, 0xFF111111);
        });
    }

    private void amogus(MatrixStack ms, float swing, float headYaw, float hurt) {
        int red = main(0xFFC51111, hurt), dark = ColorUtil.lerp(red, 0xFF000000, 0.3f);
        leg(ms, -0.17f, swing, red, 0.13f, 0.38f, false);
        leg(ms, 0.17f, -swing, red, 0.13f, 0.38f, false);
        ms.push();
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-headYaw * 0.3f));
        box(ms, -0.32f, 0.38f, -0.26f, 0.32f, 1.30f, 0.26f, red);            // body
        box(ms, -0.26f, 1.30f, -0.22f, 0.26f, 1.40f, 0.22f, red);            // rounded top
        box(ms, -0.24f, 0.55f, -0.46f, 0.24f, 1.05f, -0.26f, dark);          // backpack
        box(ms, -0.24f, 0.92f, 0.26f, 0.24f, 1.18f, 0.33f, 0xFF9AD8F0);      // visor
        box(ms, -0.14f, 1.10f, 0.33f, 0.04f, 1.15f, 0.34f, 0xFFFFFFFF);      // visor shine
        ms.pop();
    }

    private void rabbit(MatrixStack ms, float swing, float headYaw, float pitch, float hurt) {
        int fur = main(0xFFF2F2F2, hurt), pink = 0xFFFFA0B4, tail = 0xFFFFFFFF;
        leg(ms, -0.15f, swing, fur, 0.09f, 0.35f, true);
        leg(ms, 0.15f, -swing, fur, 0.09f, 0.35f, true);
        box(ms, -0.28f, 0.35f, -0.30f, 0.28f, 1.05f, 0.26f, fur);            // body
        box(ms, -0.10f, 0.45f, -0.42f, 0.10f, 0.65f, -0.30f, tail);          // tail
        box(ms, -0.36f, 0.72f, -0.05f, -0.28f, 0.95f, 0.10f, fur);           // arms
        box(ms, 0.28f, 0.72f, -0.05f, 0.36f, 0.95f, 0.10f, fur);
        head(ms, 0, 1.05f, 0, headYaw, pitch, () -> {
            box(ms, -0.22f, 0, -0.20f, 0.22f, 0.40f, 0.22f, fur);
            box(ms, -0.18f, 0.40f, -0.04f, -0.08f, 0.85f, 0.04f, fur);       // ears
            box(ms, 0.08f, 0.40f, -0.04f, 0.18f, 0.85f, 0.04f, fur);
            box(ms, -0.16f, 0.45f, 0.04f, -0.10f, 0.80f, 0.05f, pink);
            box(ms, 0.10f, 0.45f, 0.04f, 0.16f, 0.80f, 0.05f, pink);
            box(ms, -0.04f, 0.10f, 0.22f, 0.04f, 0.16f, 0.24f, pink);        // nose
            box(ms, -0.16f, 0.20f, 0.21f, -0.08f, 0.28f, 0.23f, 0xFF111111);
            box(ms, 0.08f, 0.20f, 0.21f, 0.16f, 0.28f, 0.23f, 0xFF111111);
        });
    }

    private void penguin(MatrixStack ms, float swing, float headYaw, float pitch, float hurt) {
        int black = main(0xFF1E1E26, hurt), white = 0xFFF4F4F4, orange = 0xFFFF9A1F;
        leg(ms, -0.13f, swing, orange, 0.07f, 0.12f, true);
        leg(ms, 0.13f, -swing, orange, 0.07f, 0.12f, true);
        box(ms, -0.32f, 0.12f, -0.26f, 0.32f, 1.15f, 0.24f, black);          // body
        box(ms, -0.24f, 0.18f, 0.24f, 0.24f, 1.00f, 0.29f, white);           // belly
        float flap = swing * 15;
        ms.push();
        ms.translate(-0.32f, 1.0f, 0);
        ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-10 - Math.abs(flap)));
        box(ms, -0.06f, -0.55f, -0.15f, 0, 0, 0.15f, black);                 // flippers
        ms.pop();
        ms.push();
        ms.translate(0.32f, 1.0f, 0);
        ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(10 + Math.abs(flap)));
        box(ms, 0, -0.55f, -0.15f, 0.06f, 0, 0.15f, black);
        ms.pop();
        head(ms, 0, 1.15f, 0, headYaw, pitch, () -> {
            box(ms, -0.24f, 0, -0.22f, 0.24f, 0.40f, 0.22f, black);
            box(ms, -0.17f, 0.06f, 0.22f, 0.17f, 0.32f, 0.24f, white);       // face
            box(ms, -0.06f, 0.10f, 0.24f, 0.06f, 0.17f, 0.40f, orange);      // beak
            box(ms, -0.13f, 0.20f, 0.24f, -0.06f, 0.27f, 0.25f, 0xFF111111);
            box(ms, 0.06f, 0.20f, 0.24f, 0.13f, 0.27f, 0.25f, 0xFF111111);
        });
    }

    // ------------------------------------------------------------------ helpers

    /** Leg pivoting at the hip; {@code feet} adds a flat forward foot. */
    private void leg(MatrixStack ms, float x, float swing, int color, float half, float height, boolean feet) {
        ms.push();
        ms.translate(x, height, 0);
        ms.multiply(RotationAxis.POSITIVE_X.rotation(swing));
        box(ms, -half, -height, -half, half, 0, half, color);
        if (feet) box(ms, -half * 1.6f, -height, -half, half * 1.6f, -height + 0.04f, half + 0.14f, color);
        ms.pop();
    }

    private void head(MatrixStack ms, float x, float y, float z, float yaw, float pitch, Runnable parts) {
        ms.push();
        ms.translate(x, y, z);
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-MathHelper.clamp(yaw, -75, 75)));
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch * 0.6f));
        parts.run();
        ms.pop();
    }

    /** Solid box with simple directional shading. */
    private void box(MatrixStack ms, float x1, float y1, float z1, float x2, float y2, float z2, int color) {
        Matrix4f m = ms.peek().getPositionMatrix();
        int top = shade(color, 1.0f), side1 = shade(color, 0.8f), side2 = shade(color, 0.62f), bottom = shade(color, 0.5f);
        quad(m, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, top);
        quad(m, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, bottom);
        quad(m, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, side1); // front
        quad(m, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, side1); // back
        quad(m, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, side2);
        quad(m, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, side2);
    }

    private void quad(Matrix4f m, float ax, float ay, float az, float bx, float by, float bz,
                      float cx, float cy, float cz, float dx, float dy, float dz, int c) {
        buf.vertex(m, ax, ay, az).color(c);
        buf.vertex(m, bx, by, bz).color(c);
        buf.vertex(m, cx, cy, cz).color(c);
        buf.vertex(m, dx, dy, dz).color(c);
    }

    private int shade(int c, float f) {
        f *= light;
        return ColorUtil.rgba((int) (ColorUtil.red(c) * f), (int) (ColorUtil.green(c) * f), (int) (ColorUtil.blue(c) * f), 255);
    }
}
