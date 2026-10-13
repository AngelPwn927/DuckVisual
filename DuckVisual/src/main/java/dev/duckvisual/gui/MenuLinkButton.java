package dev.duckvisual.gui;

import dev.duckvisual.util.Animation;
import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Glass;
import dev.duckvisual.util.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

/** Glass circle (icon only) or pill (icon + label) on the title screen that opens a link. */
public class MenuLinkButton extends PressableWidget {
    private final String icon, label, url;
    private final int iconColor;
    private float hover;

    public MenuLinkButton(int x, int y, int w, int h, String icon, String label, int iconColor, String url) {
        super(x, y, w, h, Text.literal(label == null ? url : label));
        this.icon = icon;
        this.label = label;
        this.url = url;
        this.iconColor = iconColor;
    }

    @Override
    public void onPress() {
        Util.getOperatingSystem().open(url);
    }

    @Override
    protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
        hover = Animation.approach(hover, isHovered() ? 1 : 0, 14);
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        float r = h / 2f;
        Render2D.shadow(ctx, x, y + 1, w, h, r, 5, ColorUtil.withAlpha(0xFF000000, (int) (70 * alpha)));
        Glass.panel(ctx, x, y, w, h, r, 0x9E141418, alpha);
        if (hover > 0.01f) Render2D.roundedRect(ctx, x, y, w, h, r, ColorUtil.withAlpha(0xFFFFFFFF, (int) (26 * hover * alpha)));
        Render2D.roundedOutline(ctx, x, y, w, h, r, 1, ColorUtil.withAlpha(0xFFFFFFFF, (int) ((34 + 60 * hover) * alpha)));
        float is = Math.min(10, h - 10);
        int ic = ColorUtil.withAlpha(iconColor == 0 ? ColorUtil.theme() : iconColor, (int) (255 * alpha));
        if (label == null) {
            Icons.draw(ctx, icon, x + w / 2f - is / 2, y + h / 2f - is / 2, is, ic);
        } else {
            Icons.draw(ctx, icon, x + 9, y + h / 2f - is / 2, is, ic);
            Render2D.text(ctx, label, x + 9 + is + 6, y + h / 2f - 3.5f, ColorUtil.withAlpha(0xFFE6E6EC, (int) (255 * alpha)));
        }
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
    }
}
