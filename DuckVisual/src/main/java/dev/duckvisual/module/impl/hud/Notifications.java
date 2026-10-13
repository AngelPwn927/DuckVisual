package dev.duckvisual.module.impl.hud;

import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.util.Animation;
import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Render2D;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

public class Notifications extends Module {
    private final List<Toast> toasts = new ArrayList<>();

    private static class Toast {
        final String title, text;
        final boolean positive;
        final long born = System.currentTimeMillis();
        float anim, y = -1;

        Toast(String title, String text, boolean positive) {
            this.title = title;
            this.text = text;
            this.positive = positive;
        }
    }

    public Notifications() {
        super("Notifications", "Pop-ups when modules are toggled", Category.HUD);
    }

    @Override
    public boolean isHidden() {
        return true;
    }

    public void push(String title, String text, boolean positive) {
        toasts.add(new Toast(title, text, positive));
        if (toasts.size() > 6) toasts.remove(0);
    }

    @Override
    public void onRender2D(DrawContext ctx, float td) {
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        long now = System.currentTimeMillis();
        long life = 2000;
        float baseY = sh - 30;
        float h = 24;
        for (int i = toasts.size() - 1; i >= 0; i--) {
            Toast t = toasts.get(i);
            boolean alive = now - t.born < life;
            t.anim = Animation.approach(t.anim, alive ? 1 : 0, 10);
            float targetY = baseY - (toasts.size() - 1 - i) * (h + 4);
            t.y = t.y < 0 ? targetY : Animation.approach(t.y, targetY, 12);

            String line = t.title + " was " + t.text;
            float w = Render2D.width(line) + 34;
            float x = sw - (w + 6) * Animation.easeOutCubic(t.anim);
            float a = t.anim;
            Render2D.shadow(ctx, x, t.y, w, h, 4, 4, ColorUtil.withAlpha(ColorUtil.theme(), (int) (70 * a)));
            Render2D.roundedRect(ctx, x, t.y, w, h, 4, ColorUtil.withAlpha(0xFF111116, (int) (225 * a)));
            int accent = t.positive ? 0xFF55FF88 : 0xFFFF5566;
            Render2D.circle(ctx, x + 12, t.y + h / 2, 5, ColorUtil.fade(accent, a * 0.25f));
            Render2D.circle(ctx, x + 12, t.y + h / 2, 2.5f, ColorUtil.fade(accent, a));
            Render2D.text(ctx, t.title, x + 22, t.y + 8, ColorUtil.fade(0xFFFFFFFF, a));
            Render2D.text(ctx, " was " + t.text, x + 22 + Render2D.width(t.title), t.y + 8, ColorUtil.fade(0xFFA0A0AA, a));
            // progress line
            float p = Math.min(1, (now - t.born) / (float) life);
            Render2D.rect(ctx, x + 2, t.y + h - 1.5f, (w - 4) * (1 - p), 1.5f, ColorUtil.fade(ColorUtil.theme(i * 4), a));
        }
        toasts.removeIf(t -> now - t.born > life && t.anim < 0.02f);
    }
}
