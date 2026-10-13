package dev.duckvisual.module;

import dev.duckvisual.setting.NumberSetting;
import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Render2D;
import net.minecraft.client.gui.DrawContext;

/** A HUD element with a position that can be dragged while the ClickGUI is open. */
public abstract class HudModule extends Module {
    public final NumberSetting x;
    public final NumberSetting y;
    protected float width = 50;
    protected float height = 15;

    protected HudModule(String name, String description, float defX, float defY) {
        super(name, description, Category.HUD);
        x = add(new NumberSetting("X", defX, -10000, 10000, 0.5).visibleWhen(() -> false));
        y = add(new NumberSetting("Y", defY, -10000, 10000, 0.5).visibleWhen(() -> false));
    }

    public float getX() { return x.getFloat(); }
    public float getY() { return y.getFloat(); }
    public float getWidth() { return width; }
    public float getHeight() { return height; }

    public boolean isHovered(double mx, double my) {
        return mx >= getX() && mx <= getX() + width && my >= getY() && my <= getY() + height;
    }

    /** Keeps the element inside the screen after a drag or resolution change. */
    public void clamp(int sw, int sh) {
        x.set((double) Math.max(0, Math.min(sw - width, getX())));
        y.set((double) Math.max(0, Math.min(sh - height, getY())));
    }

    /** The standard Duck Visual panel: blurred-looking dark body, glow and accent stripe. */
    public static void panel(DrawContext ctx, float x, float y, float w, float h) {
        Render2D.shadow(ctx, x, y, w, h, 4, 5, ColorUtil.withAlpha(ColorUtil.theme(), 90));
        Render2D.roundedRect(ctx, x, y, w, h, 4, 0xE0111116);
    }

    public static void header(DrawContext ctx, float x, float y, float w) {
        Render2D.roundedGradient(ctx, x, y, w, 2, 1, ColorUtil.theme(0), ColorUtil.theme(10), ColorUtil.theme(10), ColorUtil.theme(0));
    }
}
