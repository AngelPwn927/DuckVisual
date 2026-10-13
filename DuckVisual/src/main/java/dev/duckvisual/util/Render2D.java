package dev.duckvisual.util;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;

public final class Render2D {
    private Render2D() {}

    private static final int CORNER_SEGMENTS = 8;

    // ------------------------------------------------------------------ batching
    // Every shape is turned into triangles. Outside a batch each call is drawn at once (as before);
    // inside beginBatch()/endBatch() all shapes go into one buffer and are drawn with a single call,
    // which keeps big screens (the menu: hundreds of rounded rects) cheap on weak GPUs.
    // Text flushes the batch first, so the drawing order stays exactly the same.

    private static final BufferAllocator BATCH_MEMORY = new BufferAllocator(1 << 18);
    private static BufferBuilder batch;
    private static boolean batching;

    public static void beginBatch(DrawContext ctx) {
        flush(ctx);
        batching = true;
    }

    public static void endBatch(DrawContext ctx) {
        flush(ctx);
        batching = false;
    }

    /** Draws the shapes collected so far (call before scissor changes or foreign draw calls). */
    public static void flush(DrawContext ctx) {
        if (batch == null) return;
        BufferBuilder b = batch;
        batch = null;
        drawBuffer(b);
    }

    private static BufferBuilder open(DrawContext ctx) {
        if (batching) {
            if (batch == null) {
                ctx.draw(); // text queued before the batch must stay underneath it
                batch = new BufferBuilder(BATCH_MEMORY, VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
            }
            return batch;
        }
        ctx.draw();
        return Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
    }

    private static void close(BufferBuilder b) {
        if (!batching) drawBuffer(b);
    }

    private static void drawBuffer(BufferBuilder b) {
        var built = b.endNullable();
        if (built == null) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        BufferRenderer.drawWithGlobalProgram(built);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void tri(BufferBuilder b, Matrix4f m, float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3) {
        b.vertex(m, x1, y1, 0).color(c1);
        b.vertex(m, x2, y2, 0).color(c2);
        b.vertex(m, x3, y3, 0).color(c3);
    }

    // ------------------------------------------------------------------ shapes

    public static void rect(DrawContext ctx, float x, float y, float w, float h, int color) {
        gradient(ctx, x, y, w, h, color, color, color, color);
    }

    /** Four-corner gradient: top-left, top-right, bottom-right, bottom-left. */
    public static void gradient(DrawContext ctx, float x, float y, float w, float h, int tl, int tr, int br, int bl) {
        Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
        BufferBuilder b = open(ctx);
        tri(b, m, x, y, tl, x, y + h, bl, x + w, y + h, br);
        tri(b, m, x, y, tl, x + w, y + h, br, x + w, y, tr);
        close(b);
    }

    public static void horizontalGradient(DrawContext ctx, float x, float y, float w, float h, int left, int right) {
        gradient(ctx, x, y, w, h, left, right, right, left);
    }

    public static void roundedRect(DrawContext ctx, float x, float y, float w, float h, float r, int color) {
        roundedGradient(ctx, x, y, w, h, r, color, color, color, color);
    }

    /** Rounded rectangle with a color per corner (tl, tr, br, bl) blended across the shape. */
    public static void roundedGradient(DrawContext ctx, float x, float y, float w, float h, float r,
                                       int tl, int tr, int br, int bl) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2f));
        Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
        BufferBuilder b = open(ctx);
        float cx = x + w / 2f, cy = y + h / 2f;
        int cc = ColorUtil.lerp(ColorUtil.lerp(tl, tr, 0.5f), ColorUtil.lerp(bl, br, 0.5f), 0.5f);
        float[] px = new float[4 * (CORNER_SEGMENTS + 1) + 1], py = new float[px.length];
        int[] pc = new int[px.length];
        int n = 0;
        n = cornerPoints(px, py, pc, n, x + w - r, y + r, r, 270, tr);
        n = cornerPoints(px, py, pc, n, x + w - r, y + h - r, r, 0, br);
        n = cornerPoints(px, py, pc, n, x + r, y + h - r, r, 90, bl);
        n = cornerPoints(px, py, pc, n, x + r, y + r, r, 180, tl);
        px[n] = px[0]; py[n] = py[0]; pc[n] = pc[0]; n++;
        for (int i = 0; i + 1 < n; i++) tri(b, m, cx, cy, cc, px[i], py[i], pc[i], px[i + 1], py[i + 1], pc[i + 1]);
        close(b);
    }

    private static int cornerPoints(float[] px, float[] py, int[] pc, int n, float cx, float cy, float r, int startDeg, int color) {
        for (int i = 0; i <= CORNER_SEGMENTS; i++) {
            double a = Math.toRadians(startDeg + 90.0 * i / CORNER_SEGMENTS);
            px[n] = (float) (cx + Math.cos(a) * r);
            py[n] = (float) (cy + Math.sin(a) * r);
            pc[n] = color;
            n++;
        }
        return n;
    }

    /** Outline (border) of a rounded rectangle, {@code t} pixels thick, drawn inside the bounds. */
    public static void roundedOutline(DrawContext ctx, float x, float y, float w, float h, float r, float t, int color) {
        ring(ctx, x, y, w, h, r, t, color, color);
    }

    /**
     * Soft drop shadow (macOS style): a ring around the rectangle fading from {@code color} at the
     * edge to transparent {@code size} pixels out. One mesh instead of stacked rectangles.
     */
    public static void shadow(DrawContext ctx, float x, float y, float w, float h, float r, float size, int color) {
        ring(ctx, x - size, y - size, w + size * 2, h + size * 2, r + size, size, ColorUtil.withAlpha(color, 0), color);
    }

    /** Ring between the outer rounded rect and the same shape {@code t} pixels inside it. */
    private static void ring(DrawContext ctx, float x, float y, float w, float h, float r, float t, int outer, int inner) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2f));
        float ri = r - t;
        Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
        BufferBuilder b = open(ctx);
        int seg = CORNER_SEGMENTS;
        float[][] centers = {{x + w - r, y + r}, {x + w - r, y + h - r}, {x + r, y + h - r}, {x + r, y + r}};
        int[] starts = {270, 0, 90, 180};
        float ox0 = 0, oy0 = 0, ix0 = 0, iy0 = 0;
        boolean first = true;
        for (int c = 0; c <= 4; c++) {
            int ci = c % 4;
            int steps = c == 4 ? 0 : seg;
            for (int i = 0; i <= steps; i++) {
                double a = Math.toRadians(starts[ci] + 90.0 * i / seg);
                float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
                float ox = centers[ci][0] + cos * r, oy = centers[ci][1] + sin * r;
                float ix, iy;
                if (r > t) {
                    ix = centers[ci][0] + cos * ri;
                    iy = centers[ci][1] + sin * ri;
                } else {
                    // the inner shape is a plain rectangle inset by t
                    ix = centers[ci][0] + sgn(cos) * (r - t);
                    iy = centers[ci][1] + sgn(sin) * (r - t);
                }
                if (!first) {
                    tri(b, m, ox0, oy0, outer, ox, oy, outer, ix, iy, inner);
                    tri(b, m, ox0, oy0, outer, ix, iy, inner, ix0, iy0, inner);
                }
                ox0 = ox; oy0 = oy; ix0 = ix; iy0 = iy;
                first = false;
            }
        }
        close(b);
    }

    private static float sgn(float v) {
        return v > 1e-4f ? 1 : v < -1e-4f ? -1 : 0;
    }

    public static void circle(DrawContext ctx, float cx, float cy, float radius, int color) {
        Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
        BufferBuilder b = open(ctx);
        int seg = radius < 6 ? 16 : 32;
        float px = cx + radius, py = cy;
        for (int i = 1; i <= seg; i++) {
            double a = Math.PI * 2 * i / seg;
            float nx = (float) (cx + Math.cos(a) * radius), ny = (float) (cy + Math.sin(a) * radius);
            tri(b, m, cx, cy, color, px, py, color, nx, ny, color);
            px = nx; py = ny;
        }
        close(b);
    }

    /** Glowing dot: solid center fading to transparent edges. */
    public static void glowDot(DrawContext ctx, float cx, float cy, float radius, int color) {
        Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
        BufferBuilder b = open(ctx);
        int edge = ColorUtil.withAlpha(color, 0);
        float px = cx + radius, py = cy;
        for (int i = 1; i <= 24; i++) {
            double a = Math.PI * 2 * i / 24;
            float nx = (float) (cx + Math.cos(a) * radius), ny = (float) (cy + Math.sin(a) * radius);
            tri(b, m, cx, cy, color, px, py, edge, nx, ny, edge);
            px = nx; py = ny;
        }
        close(b);
    }

    /** Partial ring (arc) between {@code start} and {@code start + sweep} degrees. */
    public static void arc(DrawContext ctx, float cx, float cy, float radius, float thickness, float start, float sweep, int color) {
        Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
        BufferBuilder b = open(ctx);
        int seg = Math.max(4, (int) (Math.abs(sweep) / 6));
        float pox = 0, poy = 0, pix = 0, piy = 0;
        for (int i = 0; i <= seg; i++) {
            double a = Math.toRadians(start + sweep * i / seg);
            float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
            float ox = cx + cos * radius, oy = cy + sin * radius;
            float ix = cx + cos * (radius - thickness), iy = cy + sin * (radius - thickness);
            if (i > 0) {
                tri(b, m, pox, poy, color, ox, oy, color, ix, iy, color);
                tri(b, m, pox, poy, color, ix, iy, color, pix, piy, color);
            }
            pox = ox; poy = oy; pix = ix; piy = iy;
        }
        close(b);
    }

    /** Straight line of the given width (for icons). */
    public static void line(DrawContext ctx, float x1, float y1, float x2, float y2, float width, int color) {
        float dx = x2 - x1, dy = y2 - y1, len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-4f) return;
        float nx = -dy / len * width / 2, ny = dx / len * width / 2;
        Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
        BufferBuilder b = open(ctx);
        tri(b, m, x1 + nx, y1 + ny, color, x1 - nx, y1 - ny, color, x2 - nx, y2 - ny, color);
        tri(b, m, x1 + nx, y1 + ny, color, x2 - nx, y2 - ny, color, x2 + nx, y2 + ny, color);
        close(b);
    }

    public static void triangle(DrawContext ctx, float x1, float y1, float x2, float y2, float x3, float y3, int color) {
        Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
        BufferBuilder b = open(ctx);
        tri(b, m, x1, y1, color, x2, y2, color, x3, y3, color);
        close(b);
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
        flush(ctx);
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
