package dev.duckvisual.util;

import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.duckvisual.DuckVisual;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Frosted glass (macOS "vibrancy") for the menu, HUD and title screen.
 * <p>
 * {@link #capture()} blurs what is on screen once: the frame is shrunk to a quarter and blurred in
 * two separable passes, which costs about as much as drawing one textured rectangle.
 * {@link #panel} then draws any number of rounded panels that show that blurred frame behind them.
 * If the shaders do not compile (very old drivers), panels fall back to plain dark fills.
 */
public final class Glass {
    private static final ShaderProgramKey BLUR = new ShaderProgramKey(Identifier.of("duckvisual", "core/blur"), VertexFormats.POSITION_TEXTURE, Defines.EMPTY);
    private static final ShaderProgramKey GLASS = new ShaderProgramKey(Identifier.of("duckvisual", "core/glass"), VertexFormats.POSITION_TEXTURE_COLOR, Defines.EMPTY);
    private static final int DOWNSCALE = 4, SEGMENTS = 8;

    private static SimpleFramebuffer a, b;
    private static boolean failed;
    private static long frame, capturedFrame = -1;
    private static long capturedAt;
    private static int capturedW, capturedH;
    /** Minimum time between two blurs. Under a strong blur nobody sees a 30 Hz update, and it saves most of the cost. */
    private static long intervalNs = -1;

    private Glass() {}

    /** Called once per frame (GameRenderer.render): a capture from an earlier frame is never reused. */
    public static void newFrame() {
        frame++;
    }

    public static boolean ready() {
        return !failed && capturedFrame == frame;
    }

    /** Blurs the current frame. Call after the background is drawn and before the panels. */
    public static void capture(DrawContext ctx) {
        if (failed) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        Framebuffer main = mc.getFramebuffer();
        if (intervalNs < 0) intervalNs = softwareGl() ? 100_000_000L : 33_000_000L;
        long now = System.nanoTime();
        if (capturedFrame >= 0 && now - capturedAt < intervalNs && capturedW == main.textureWidth && capturedH == main.textureHeight) {
            capturedFrame = frame; // reuse the recent blur
            return;
        }
        ctx.draw();
        Render2D.flush(ctx);
        int w = Math.max(1, main.textureWidth / DOWNSCALE), h = Math.max(1, main.textureHeight / DOWNSCALE);
        if (a == null) {
            a = new SimpleFramebuffer(w, h, false);
            b = new SimpleFramebuffer(w, h, false);
            a.setTexFilter(GlConst.GL_LINEAR);
            b.setTexFilter(GlConst.GL_LINEAR);
        } else if (a.textureWidth != w || a.textureHeight != h) {
            a.resize(w, h);
            b.resize(w, h);
            a.setTexFilter(GlConst.GL_LINEAR);
            b.setTexFilter(GlConst.GL_LINEAR);
        }
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        if (scissor) GlStateManager._disableScissorTest();

        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.fbo);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, a.fbo);
        GlStateManager._glBlitFrameBuffer(0, 0, main.textureWidth, main.textureHeight, 0, 0, w, h, GL30.GL_COLOR_BUFFER_BIT, GL30.GL_LINEAR);

        RenderSystem.getModelViewStack().pushMatrix();
        RenderSystem.getModelViewStack().identity();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f(), ProjectionType.ORTHOGRAPHIC);
        RenderSystem.disableDepthTest();
        RenderSystem.disableBlend();
        boolean ok = pass(a, b, 1.6f / w, 0) && pass(b, a, 0, 1.6f / h) && pass(a, b, 3.2f / w, 0) && pass(b, a, 0, 3.2f / h);
        RenderSystem.enableDepthTest();
        RenderSystem.restoreProjectionMatrix();
        RenderSystem.getModelViewStack().popMatrix();

        main.beginWrite(true);
        if (scissor) GlStateManager._enableScissorTest();
        if (!ok) {
            failed = true;
            DuckVisual.LOGGER.warn("Glass blur shaders unavailable, using plain panels");
            return;
        }
        capturedFrame = frame;
        capturedAt = now;
        capturedW = main.textureWidth;
        capturedH = main.textureHeight;
    }

    /** Windows "basic display adapter" / Mesa llvmpipe: everything is drawn by the CPU, so blur rarely. */
    private static boolean softwareGl() {
        String r = String.valueOf(com.mojang.blaze3d.platform.GlDebugInfo.getRenderer()).toLowerCase();
        return r.contains("llvmpipe") || r.contains("softpipe") || r.contains("gdi generic") || r.contains("basic render");
    }

    private static boolean pass(SimpleFramebuffer from, SimpleFramebuffer to, float dx, float dy) {
        to.beginWrite(true);
        ShaderProgram p = RenderSystem.setShader(BLUR);
        if (p == null) return false;
        GlUniform u = p.getUniform("Direction");
        if (u != null) u.set(dx, dy);
        RenderSystem.setShaderTexture(0, from.getColorAttachment());
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        buf.vertex(-1, -1, 0).texture(0, 0);
        buf.vertex(1, -1, 0).texture(1, 0);
        buf.vertex(1, 1, 0).texture(1, 1);
        buf.vertex(-1, 1, 0).texture(0, 1);
        BufferRenderer.drawWithGlobalProgram(buf.end());
        return true;
    }

    /**
     * A rounded glass panel. {@code tint} is mixed over the blur by its alpha (0x99141418 = dark glass).
     * {@code opacity} fades the whole panel (open / close animations).
     */
    public static void panel(DrawContext ctx, float x, float y, float w, float h, float r, int tint, float opacity) {
        if (opacity <= 0.01f) return;
        if (!ready()) {
            // no blur this frame: a plain panel of about the same brightness
            int a = Math.min(255, (int) ((ColorUtil.alpha(tint) / 255f * 0.45f + 0.5f) * 255 * opacity));
            Render2D.roundedRect(ctx, x, y, w, h, r, ColorUtil.withAlpha(tint, a));
            return;
        }
        Render2D.flush(ctx);
        ShaderProgram p = RenderSystem.setShader(GLASS);
        if (p == null) {
            failed = true;
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        float sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShaderTexture(0, a.getColorAttachment());
        RenderSystem.setShaderColor(1, 1, 1, opacity);
        Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_TEXTURE_COLOR);
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2f));
        Vector3f v = new Vector3f();
        vertex(buf, m, v, x + w / 2f, y + h / 2f, sw, sh, tint);
        corner(buf, m, v, x + w - r, y + r, r, 270, sw, sh, tint);
        corner(buf, m, v, x + w - r, y + h - r, r, 0, sw, sh, tint);
        corner(buf, m, v, x + r, y + h - r, r, 90, sw, sh, tint);
        corner(buf, m, v, x + r, y + r, r, 180, sw, sh, tint);
        vertex(buf, m, v, x + w - r, y, sw, sh, tint);
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void corner(BufferBuilder buf, Matrix4f m, Vector3f v, float cx, float cy, float r, int start, float sw, float sh, int tint) {
        for (int i = 0; i <= SEGMENTS; i++) {
            double ang = Math.toRadians(start + 90.0 * i / SEGMENTS);
            vertex(buf, m, v, (float) (cx + Math.cos(ang) * r), (float) (cy + Math.sin(ang) * r), sw, sh, tint);
        }
    }

    /** The texture coordinate is where the vertex lands on screen, so the panel shows what is behind it. */
    private static void vertex(BufferBuilder buf, Matrix4f m, Vector3f v, float x, float y, float sw, float sh, int tint) {
        m.transformPosition(x, y, 0, v);
        buf.vertex(m, x, y, 0).texture(v.x / sw, 1 - v.y / sh).color(tint);
    }
}
