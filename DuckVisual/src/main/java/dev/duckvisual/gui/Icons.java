package dev.duckvisual.gui;

import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Render2D;
import net.minecraft.client.gui.DrawContext;

/** Small vector icons (SF Symbols style) drawn from shapes, so they stay sharp at any GUI scale. */
public final class Icons {
    private Icons() {}

    /** Draws icon {@code name} in a {@code s}×{@code s} box at (x, y). */
    public static void draw(DrawContext ctx, String name, float x, float y, float s, int c) {
        float cx = x + s / 2, cy = y + s / 2, t = Math.max(1f, s / 9f);
        switch (name) {
            case "visuals" -> { // eye
                ctx.getMatrices().push();
                ctx.getMatrices().translate(cx, cy, 0);
                ctx.getMatrices().scale(1, 0.62f, 1);
                Render2D.arc(ctx, 0, 0, s / 2, t / 0.62f * 0.8f, 0, 360, c);
                ctx.getMatrices().pop();
                Render2D.circle(ctx, cx, cy, s * 0.17f, c);
            }
            case "hud" -> { // window with a bar
                Render2D.roundedOutline(ctx, x, y + s * 0.1f, s, s * 0.8f, s * 0.15f, t, c);
                Render2D.rect(ctx, x, y + s * 0.32f, s, t, c);
                Render2D.rect(ctx, x + s * 0.32f, y + s * 0.32f, t, s * 0.55f, c);
            }
            case "world" -> { // globe
                Render2D.arc(ctx, cx, cy, s / 2, t, 0, 360, c);
                ctx.getMatrices().push();
                ctx.getMatrices().translate(cx, cy, 0);
                ctx.getMatrices().scale(0.45f, 1, 1);
                Render2D.arc(ctx, 0, 0, s / 2, t / 0.45f, 0, 360, c);
                ctx.getMatrices().pop();
                Render2D.rect(ctx, x, cy - t / 2, s, t, c);
            }
            case "cosmetics" -> { // t-shirt
                float w = s, h = s * 0.86f, top = y + (s - h) / 2;
                Render2D.rect(ctx, x + w * 0.25f, top + h * 0.12f, w * 0.5f, h * 0.88f, c);
                Render2D.triangle(ctx, x + w * 0.25f, top + h * 0.12f, x, top + h * 0.38f, x + w * 0.25f, top + h * 0.5f, c);
                Render2D.triangle(ctx, x + w * 0.75f, top + h * 0.12f, x + w * 0.75f, top + h * 0.5f, x + w, top + h * 0.38f, c);
                Render2D.rect(ctx, x + w * 0.25f, top, w * 0.15f, h * 0.15f, c);
                Render2D.rect(ctx, x + w * 0.6f, top, w * 0.15f, h * 0.15f, c);
            }
            case "misc" -> { // sliders
                Render2D.rect(ctx, x, y + s * 0.28f - t / 2, s, t, c);
                Render2D.rect(ctx, x, y + s * 0.72f - t / 2, s, t, c);
                Render2D.circle(ctx, x + s * 0.3f, y + s * 0.28f, s * 0.16f, c);
                Render2D.circle(ctx, x + s * 0.7f, y + s * 0.72f, s * 0.16f, c);
            }
            case "palette" -> {
                Render2D.circle(ctx, cx, y + s * 0.27f, s * 0.22f, ColorUtil.theme(0));
                Render2D.circle(ctx, x + s * 0.27f, y + s * 0.7f, s * 0.22f, ColorUtil.theme(6));
                Render2D.circle(ctx, x + s * 0.73f, y + s * 0.7f, s * 0.22f, ColorUtil.theme(12));
            }
            case "configs" -> { // folder
                Render2D.roundedRect(ctx, x, y + s * 0.12f, s * 0.45f, s * 0.25f, s * 0.08f, c);
                Render2D.roundedRect(ctx, x, y + s * 0.25f, s, s * 0.65f, s * 0.12f, c);
            }
            case "guide" -> { // book
                Render2D.roundedOutline(ctx, x + s * 0.08f, y, s * 0.84f, s, s * 0.12f, t, c);
                Render2D.rect(ctx, x + s * 0.3f, y + s * 0.28f, s * 0.42f, t, c);
                Render2D.rect(ctx, x + s * 0.3f, y + s * 0.48f, s * 0.42f, t, c);
            }
            case "project" -> { // paper plane
                Render2D.triangle(ctx, x, y + s * 0.45f, x + s, y + s * 0.05f, x + s * 0.45f, y + s * 0.62f, c);
                Render2D.triangle(ctx, x + s * 0.45f, y + s * 0.62f, x + s, y + s * 0.05f, x + s * 0.7f, y + s * 0.95f, c);
            }
            case "search" -> {
                Render2D.arc(ctx, x + s * 0.42f, y + s * 0.42f, s * 0.36f, t, 0, 360, c);
                Render2D.line(ctx, x + s * 0.68f, y + s * 0.68f, x + s, y + s, t * 1.2f, c);
            }
            case "gear" -> {
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * 2 * i / 8;
                    float dx = (float) Math.cos(a), dy = (float) Math.sin(a);
                    Render2D.line(ctx, cx + dx * s * 0.3f, cy + dy * s * 0.3f, cx + dx * s * 0.5f, cy + dy * s * 0.5f, s * 0.2f, c);
                }
                Render2D.arc(ctx, cx, cy, s * 0.36f, s * 0.16f, 0, 360, c);
            }
            case "chevron" -> {
                Render2D.line(ctx, x + s * 0.3f, y + s * 0.15f, x + s * 0.7f, y + s * 0.5f, t, c);
                Render2D.line(ctx, x + s * 0.7f, y + s * 0.5f, x + s * 0.3f, y + s * 0.85f, t, c);
            }
            case "close" -> {
                Render2D.line(ctx, x, y, x + s, y + s, t, c);
                Render2D.line(ctx, x + s, y, x, y + s, t, c);
            }
            case "minus" -> Render2D.rect(ctx, x, cy - t / 2, s, t, c);
            case "plus" -> {
                Render2D.rect(ctx, x, cy - t / 2, s, t, c);
                Render2D.rect(ctx, cx - t / 2, y, t, s, c);
            }
            case "user" -> {
                Render2D.circle(ctx, cx, y + s * 0.3f, s * 0.24f, c);
                Render2D.roundedRect(ctx, x + s * 0.12f, y + s * 0.6f, s * 0.76f, s * 0.4f, s * 0.2f, c);
            }
            case "users" -> {
                Render2D.circle(ctx, x + s * 0.36f, y + s * 0.3f, s * 0.2f, c);
                Render2D.roundedRect(ctx, x, y + s * 0.58f, s * 0.7f, s * 0.38f, s * 0.18f, c);
                Render2D.circle(ctx, x + s * 0.74f, y + s * 0.34f, s * 0.16f, ColorUtil.withAlpha(c, 170));
                Render2D.roundedRect(ctx, x + s * 0.58f, y + s * 0.6f, s * 0.42f, s * 0.32f, s * 0.15f, ColorUtil.withAlpha(c, 170));
            }
            case "exit" -> {
                Render2D.roundedOutline(ctx, x, y, s * 0.6f, s, s * 0.12f, t, c);
                Render2D.rect(ctx, x + s * 0.35f, cy - t / 2, s * 0.6f, t, c);
                Render2D.line(ctx, x + s * 0.75f, y + s * 0.3f, x + s, cy, t, c);
                Render2D.line(ctx, x + s * 0.75f, y + s * 0.7f, x + s, cy, t, c);
            }
            case "heart" -> {
                Render2D.circle(ctx, x + s * 0.3f, y + s * 0.35f, s * 0.24f, c);
                Render2D.circle(ctx, x + s * 0.7f, y + s * 0.35f, s * 0.24f, c);
                Render2D.triangle(ctx, x + s * 0.07f, y + s * 0.45f, x + s * 0.93f, y + s * 0.45f, cx, y + s * 0.92f, c);
            }
            default -> Render2D.circle(ctx, cx, cy, s / 3, c);
        }
    }
}
