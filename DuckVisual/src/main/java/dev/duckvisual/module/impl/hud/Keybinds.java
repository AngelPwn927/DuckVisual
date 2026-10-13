package dev.duckvisual.module.impl.hud;

import dev.duckvisual.DuckVisual;
import dev.duckvisual.module.HudModule;
import dev.duckvisual.module.Module;
import dev.duckvisual.module.impl.misc.ClickGui;
import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Keys;
import dev.duckvisual.util.Render2D;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

public class Keybinds extends HudModule {
    public Keybinds() {
        super("Keybinds", "Shows bound modules", 5, 60);
    }

    @Override
    public boolean isHidden() {
        return true;
    }

    @Override
    public void onRender2D(DrawContext ctx, float td) {
        List<Module> list = DuckVisual.modules().getModules().stream()
                .filter(m -> m.getKey() > 0 && !(m instanceof ClickGui)).toList();
        float x = getX(), y = getY();
        width = 100;
        for (Module m : list) width = Math.max(width, Render2D.width(m.getName()) + Render2D.width(Keys.name(m.getKey())) + 40);
        height = 20 + list.size() * 13 + (list.isEmpty() ? 0 : 2);
        panel(ctx, x, y, width, height);
        title(ctx, "Keybinds", "hud", x, y, width);
        float yy = y + 21;
        for (Module m : list) {
            int c = m.isEnabled() ? 0xFFF2F2F7 : 0xFF8A8A95;
            if (m.animation > 0.01f) Render2D.circle(ctx, x + 9, yy + 4, 2, ColorUtil.withAlpha(ColorUtil.theme(), (int) (255 * m.animation)));
            Render2D.text(ctx, m.getName(), x + 15, yy, c);
            String k = Keys.name(m.getKey());
            float kw = Render2D.width(k) + 8;
            Render2D.roundedRect(ctx, x + width - 6 - kw, yy - 2, kw, 11, 5.5f, m.isEnabled() ? ColorUtil.withAlpha(ColorUtil.theme(), 70) : 0x16FFFFFF);
            Render2D.text(ctx, k, x + width - 2 - kw, yy, c);
            yy += 13;
        }
    }
}
