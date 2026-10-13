package dev.duckvisual.util;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.duckvisual.event.WorldRender;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public final class Render3D {
    private Render3D() {}

    /** Prepares GL state for our world-space drawing. Must be paired with {@link #end()}. */
    public static void begin(Matrix4f projection) {
        RenderSystem.getModelViewStack().pushMatrix();
        RenderSystem.getModelViewStack().identity();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(projection, ProjectionType.PERSPECTIVE);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
    }

    public static void end() {
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.restoreProjectionMatrix();
        RenderSystem.getModelViewStack().popMatrix();
    }

    public static void additive(boolean on) {
        if (on) RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        else RenderSystem.defaultBlendFunc();
    }

    public static void depth(boolean on) {
        if (on) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
    }

    public static BufferBuilder buffer(VertexFormat.DrawMode mode) {
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        return Tessellator.getInstance().begin(mode, VertexFormats.POSITION_COLOR);
    }

    public static void draw(BufferBuilder b) {
        var built = b.endNullable();
        if (built != null) BufferRenderer.drawWithGlobalProgram(built);
    }

    /** Horizontal ring (flat on the XZ plane) centered at a camera-relative position. */
    public static void ring(WorldRender r, Vec3d c, float inner, float outer, int innerColor, int outerColor) {
        Matrix4f m = r.matrices().peek().getPositionMatrix();
        BufferBuilder b = buffer(VertexFormat.DrawMode.TRIANGLE_STRIP);
        int seg = 72;
        for (int i = 0; i <= seg; i++) {
            double a = Math.PI * 2 * i / seg;
            float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
            b.vertex(m, (float) c.x + cos * outer, (float) c.y, (float) c.z + sin * outer).color(outerColor);
            b.vertex(m, (float) c.x + cos * inner, (float) c.y, (float) c.z + sin * inner).color(innerColor);
        }
        draw(b);
    }

    /** Camera-facing soft glowing disc. */
    public static void glow(WorldRender r, Vec3d c, float size, int color) {
        MatrixStack ms = r.matrices();
        ms.push();
        ms.translate(c.x, c.y, c.z);
        ms.multiply(r.camera().getRotation());
        Matrix4f m = ms.peek().getPositionMatrix();
        BufferBuilder b = buffer(VertexFormat.DrawMode.TRIANGLE_FAN);
        b.vertex(m, 0, 0, 0).color(color);
        int edge = ColorUtil.withAlpha(color, 0);
        for (int i = 0; i <= 20; i++) {
            double a = Math.PI * 2 * i / 20;
            b.vertex(m, (float) Math.cos(a) * size, (float) Math.sin(a) * size, 0).color(edge);
        }
        draw(b);
        ms.pop();
    }

    /** Camera-facing glow with a solid core: two layered discs. */
    public static void orb(WorldRender r, Vec3d c, float size, int color) {
        glow(r, c, size, ColorUtil.fade(color, 0.55f));
        glow(r, c, size * 0.35f, color);
    }

    /** Filled box; {@code b} must be camera-relative. Top/bottom get their own colors for a gradient. */
    public static void filledBox(WorldRender r, net.minecraft.util.math.Box b, int bottom, int top) {
        Matrix4f m = r.matrices().peek().getPositionMatrix();
        float x1 = (float) b.minX, y1 = (float) b.minY, z1 = (float) b.minZ;
        float x2 = (float) b.maxX, y2 = (float) b.maxY, z2 = (float) b.maxZ;
        BufferBuilder q = buffer(VertexFormat.DrawMode.QUADS);
        // bottom / top
        q.vertex(m, x1, y1, z1).color(bottom); q.vertex(m, x2, y1, z1).color(bottom);
        q.vertex(m, x2, y1, z2).color(bottom); q.vertex(m, x1, y1, z2).color(bottom);
        q.vertex(m, x1, y2, z1).color(top); q.vertex(m, x1, y2, z2).color(top);
        q.vertex(m, x2, y2, z2).color(top); q.vertex(m, x2, y2, z1).color(top);
        // sides
        q.vertex(m, x1, y1, z1).color(bottom); q.vertex(m, x1, y2, z1).color(top);
        q.vertex(m, x2, y2, z1).color(top); q.vertex(m, x2, y1, z1).color(bottom);
        q.vertex(m, x1, y1, z2).color(bottom); q.vertex(m, x2, y1, z2).color(bottom);
        q.vertex(m, x2, y2, z2).color(top); q.vertex(m, x1, y2, z2).color(top);
        q.vertex(m, x1, y1, z1).color(bottom); q.vertex(m, x1, y1, z2).color(bottom);
        q.vertex(m, x1, y2, z2).color(top); q.vertex(m, x1, y2, z1).color(top);
        q.vertex(m, x2, y1, z1).color(bottom); q.vertex(m, x2, y2, z1).color(top);
        q.vertex(m, x2, y2, z2).color(top); q.vertex(m, x2, y1, z2).color(bottom);
        draw(q);
    }

    /** Box edges; {@code b} must be camera-relative. */
    public static void outlineBox(WorldRender r, net.minecraft.util.math.Box b, int bottom, int top) {
        Matrix4f m = r.matrices().peek().getPositionMatrix();
        float x1 = (float) b.minX, y1 = (float) b.minY, z1 = (float) b.minZ;
        float x2 = (float) b.maxX, y2 = (float) b.maxY, z2 = (float) b.maxZ;
        BufferBuilder l = buffer(VertexFormat.DrawMode.DEBUG_LINES);
        float[][] c = {{x1, z1}, {x2, z1}, {x2, z2}, {x1, z2}};
        for (int i = 0; i < 4; i++) {
            float[] a = c[i], n = c[(i + 1) % 4];
            l.vertex(m, a[0], y1, a[1]).color(bottom); l.vertex(m, n[0], y1, n[1]).color(bottom);
            l.vertex(m, a[0], y2, a[1]).color(top); l.vertex(m, n[0], y2, n[1]).color(top);
            l.vertex(m, a[0], y1, a[1]).color(bottom); l.vertex(m, a[0], y2, a[1]).color(top);
        }
        draw(l);
    }

    public static void line(WorldRender r, Vec3d a, Vec3d b, int colorA, int colorB) {
        Matrix4f m = r.matrices().peek().getPositionMatrix();
        BufferBuilder l = buffer(VertexFormat.DrawMode.DEBUG_LINES);
        l.vertex(m, (float) a.x, (float) a.y, (float) a.z).color(colorA);
        l.vertex(m, (float) b.x, (float) b.y, (float) b.z).color(colorB);
        draw(l);
    }

    /**
     * Camera-facing text at a camera-relative position, like a name tag.
     * {@code scale} 1 = vanilla name tag size.
     */
    public static void text(WorldRender r, String text, Vec3d c, float scale, int color, boolean throughWalls) {
        if (ColorUtil.alpha(color) < 8) return;
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        MatrixStack ms = r.matrices();
        ms.push();
        ms.translate(c.x, c.y, c.z);
        ms.multiply(r.camera().getRotation());
        float s = 0.025f * scale;
        ms.scale(s, -s, s);
        var consumers = mc.getBufferBuilders().getEntityVertexConsumers();
        float x = -mc.textRenderer.getWidth(text) / 2f;
        mc.textRenderer.draw(text, x, 0, color, true, ms.peek().getPositionMatrix(), consumers,
                throughWalls ? net.minecraft.client.font.TextRenderer.TextLayerType.SEE_THROUGH
                        : net.minecraft.client.font.TextRenderer.TextLayerType.NORMAL,
                0, net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE);
        consumers.draw();
        ms.pop();
        // the text layer changes GL state; restore ours
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
    }

    /** Camera-facing flat bar (e.g. health) centered at {@code c}, size in blocks. */
    public static void bar(WorldRender r, Vec3d c, float w, float h, float fill, int bg, int fg) {
        MatrixStack ms = r.matrices();
        ms.push();
        ms.translate(c.x, c.y, c.z);
        ms.multiply(r.camera().getRotation());
        Matrix4f m = ms.peek().getPositionMatrix();
        BufferBuilder b = buffer(VertexFormat.DrawMode.QUADS);
        float x1 = -w / 2, x2 = w / 2, y1 = -h / 2, y2 = h / 2, fx = x1 + w * Math.max(0, Math.min(1, fill));
        b.vertex(m, x1, y1, 0).color(bg); b.vertex(m, x1, y2, 0).color(bg); b.vertex(m, x2, y2, 0).color(bg); b.vertex(m, x2, y1, 0).color(bg);
        b.vertex(m, x1, y1, -0.001f).color(fg); b.vertex(m, x1, y2, -0.001f).color(fg); b.vertex(m, fx, y2, -0.001f).color(fg); b.vertex(m, fx, y1, -0.001f).color(fg);
        draw(b);
        ms.pop();
    }

    public static Vec3d interpolate(net.minecraft.entity.Entity e, float tickDelta) {
        return e.getLerpedPos(tickDelta);
    }
}
