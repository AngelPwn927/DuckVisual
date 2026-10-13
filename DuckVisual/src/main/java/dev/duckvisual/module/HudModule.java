package dev.duckvisual.module;

import dev.duckvisual.setting.NumberSetting;
import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Glass;
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

    /** The standard Duck Visual panel: frosted glass (macOS style) with a soft shadow and a hairline border. */
    public static void panel(DrawContext ctx, float x, float y, float w, float h) {
        float r = Math.min(7, h / 2);
        Render2D.shadow(ctx, x, y + 1, w, h, r, 6, 0x50000000);
        Glass.panel(ctx, x, y, w, h, r, 0xB0141418, 1);
        Render2D.roundedOutline(ctx, x, y, w, h, r, 1, 0x1EFFFFFF);
    }

    /** Title row of a panel: the title, an icon on the right and a hairline under them. */
    public static void header(DrawContext ctx, float x, float y, float w) {
        Render2D.rect(ctx, x + 6, y + 17, w - 12, 1, 0x16FFFFFF);
    }

    public static void title(DrawContext ctx, String title, String icon, float x, float y, float w) {
        Render2D.text(ctx, title, x + 7, y + 6, 0xFFF2F2F7);
        if (icon != null) dev.duckvisual.gui.Icons.draw(ctx, icon, x + w - 15, y + 5, 8, ColorUtil.theme());
        header(ctx, x, y, w);
    }
}
