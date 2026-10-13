package dev.duckvisual.util;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;

public final class Render2D {
    private Render2D() {}

    private static final int CORNER_SEGMENTS = 8;

    private static BufferBuilder begin(DrawContext ctx, VertexFormat.DrawMode mode) {
        ctx.draw();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        return Tessellator.getInstance().begin(mode, VertexFormats.POSITION_COLOR);
    }

    private static void end(BufferBuilder buf) {
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    public static void rect(DrawContext ctx, float x, float y, float w, float h, int color) {
        gradient(ctx, x, y, w, h, color, color, color, color);
    }

    /** Four-corner gradient: top-left, top-right, bottom-right, bottom-left. */
    public static void gradient(DrawContext ctx, float x, float y, float w, float h, int tl, int tr, int br, int bl) {
        Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
        BufferBuilder b = begin(ctx, VertexFormat.DrawMode.QUADS);
        b.vertex(m, x, y, 0).color(tl);
        b.vertex(m, x, y + h, 0).color(bl);
        b.vertex(m, x + w, y + h, 0).color(br);
        b.vertex(m, x + w, y, 0).color(tr);
        end(b);
    }

    public static void horizontalGradient(DrawContext ctx, float x, float y, float w, float h, int left, int right) {
        gradient(ctx, x, y, w, h, left, right, right, left);
    }

    public static void roundedRect(DrawContext ctx, float x, float y, float w, float h, float r, int color) {
        roundedGradient(ctx, x, y, w, h, r, color, color, color, color);
    }

    /** Rounded rectangle with a color per corner (tl, tr, br, bl) blended across the fan. */
    public static void roundedGradient(DrawContext ctx, float x, float y, float w, float h, float r,
                                       int tl, int tr, int br, int bl) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2f));
        Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
        BufferBuilder b = begin(ctx, VertexFormat.DrawMode.TRIANGLE_FAN);
        float cx = x + w / 2f, cy = y + h / 2f;
        b.vertex(m, cx, cy, 0).color(ColorUtil.lerp(ColorUtil.lerp(tl, tr, 0.5f), ColorUtil.lerp(bl, br, 0.5f), 0.5f));
        corner(b, m, x + w - r, y + r, r, 270, tr);
        corner(b, m, x + w - r, y + h - r, r, 0, br);
        corner(b, m, x + r, y + h - r, r, 90, bl);
        corner(b, m, x + r, y + r, r, 180, tl);
        b.vertex(m, x + w - r, y, 0).color(tr);
        end(b);
    }

    private static void corner(BufferBuilder b, Matrix4f m, float cx, float cy, float r, int startDeg, int color) {
        for (int i = 0; i <= CORNER_SEGMENTS; i++) {
            double a = Math.toRadians(startDeg + 90.0 * i / CORNER_SEGMENTS);
            b.vertex(m, (float) (cx + Math.cos(a) * r), (float) (cy + Math.sin(a) * r), 0).color(color);
        }
    }

    /** Soft glow / drop shadow made from stacked translucent rounded rects. */
    public static void shadow(DrawContext ctx, float x, float y, float w, float h, float r, float size, int color) {
        int layers = 6;
        int baseAlpha = ColorUtil.alpha(color);
        for (int i = layers; i >= 1; i--) {
            float grow = size * i / layers;
            int a = (int) (baseAlpha * (1f - (float) i / (layers + 1)) / layers * 1.6f);
            roundedRect(ctx, x - grow, y - grow, w + grow * 2, h + grow * 2, r + grow, ColorUtil.withAlpha(color, a));
        }
    }

    public static void circle(DrawContext ctx, float cx, float cy, float radius, int color) {
        Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
        BufferBuilder b = begin(ctx, VertexFormat.DrawMode.TRIANGLE_FAN);
        b.vertex(m, cx, cy, 0).color(color);
        for (int i = 0; i <= 32; i++) {
            double a = Math.PI * 2 * i / 32;
            b.vertex(m, (float) (cx + Math.cos(a) * radius), (float) (cy + Math.sin(a) * radius), 0).color(color);
        }
        end(b);
    }

    /** Glowing dot: solid center fading to transparent edges. */
    public static void glowDot(DrawContext ctx, float cx, float cy, float radius, int color) {
        Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
        BufferBuilder b = begin(ctx, VertexFormat.DrawMode.TRIANGLE_FAN);
        b.vertex(m, cx, cy, 0).color(color);
        int edge = ColorUtil.withAlpha(color, 0);
        for (int i = 0; i <= 24; i++) {
            double a = Math.PI * 2 * i / 24;
            b.vertex(m, (float) (cx + Math.cos(a) * radius), (float) (cy + Math.sin(a) * radius), 0).color(edge);
        }
        end(b);
    }

    /** Partial ring (arc) between {@code start} and {@code start + sweep} degrees. */
    public static void arc(DrawContext ctx, float cx, float cy, float radius, float thickness, float start, float sweep, int color) {
        Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
        BufferBuilder b = begin(ctx, VertexFormat.DrawMode.TRIANGLE_STRIP);
        int seg = Math.max(4, (int) (Math.abs(sweep) / 6));
        for (int i = 0; i <= seg; i++) {
            double a = Math.toRadians(start + sweep * i / seg);
            float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
            b.vertex(m, cx + cos * radius, cy + sin * radius, 0).color(color);
            b.vertex(m, cx + cos * (radius - thickness), cy + sin * (radius - thickness), 0).color(color);
        }
        end(b);
    }

    public static void triangle(DrawContext ctx, float x1, float y1, float x2, float y2, float x3, float y3, int color) {
        Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
        BufferBuilder b = begin(ctx, VertexFormat.DrawMode.TRIANGLES);
        b.vertex(m, x1, y1, 0).color(color);
        b.vertex(m, x2, y2, 0).color(color);
        b.vertex(m, x3, y3, 0).color(color);
        end(b);
    }

    // ---- text helpers ----

    public static TextRenderer font() {
        return MinecraftClient.getInstance().textRenderer;
    }

    /** Montserrat TTF font shipped in assets/duckvisual/font (toggled by the CustomFont module). */
    public static final net.minecraft.util.Identifier FONT = net.minecraft.util.Identifier.of("duckvisual", "main");
    public static boolean customFont;
    public static boolean shadow = true;

    private static final net.minecraft.text.Style STYLE = net.minecraft.text.Style.EMPTY.withFont(FONT);

    private static net.minecraft.text.Text styled(String s) {
        return net.minecraft.text.Text.literal(s).setStyle(STYLE);
    }

    public static int width(String s) {
        return customFont ? font().getWidth(styled(s)) : font().getWidth(s);
    }

    public static void text(DrawContext ctx, String s, float x, float y, int color) {
        // vanilla treats near-zero alpha as fully opaque, so skip invisible text instead
        if (ColorUtil.alpha(color) < 8) return;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        if (customFont) ctx.drawText(font(), styled(s), 0, 0, color, shadow);
        else ctx.drawText(font(), s, 0, 0, color, true);
        ctx.getMatrices().pop();
    }

    public static void centeredText(DrawContext ctx, String s, float cx, float y, int color) {
        text(ctx, s, cx - width(s) / 2f, y, color);
    }

    /** Text where every character gets its own theme color, producing a running gradient. */
    public static void gradientText(DrawContext ctx, String s, float x, float y, int offset) {
        float cx = x;
        for (int i = 0; i < s.length(); i++) {
            String ch = String.valueOf(s.charAt(i));
            text(ctx, ch, cx, y, ColorUtil.theme(offset + i));
            cx += width(ch);
        }
    }
}
