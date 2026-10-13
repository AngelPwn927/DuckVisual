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
        for (Module m : list) width = Math.max(width, Render2D.width(m.getName()) + Render2D.width(Keys.name(m.getKey())) + 30);
        height = 18 + list.size() * 11 + (list.isEmpty() ? 0 : 3);
        panel(ctx, x, y, width, height);
        header(ctx, x, y, width);
        Render2D.text(ctx, "Keybinds", x + 6, y + 6, 0xFFFFFFFF);
        Render2D.text(ctx, "⌨", x + width - 14, y + 6, ColorUtil.theme());
        float yy = y + 19;
        for (Module m : list) {
            int c = m.isEnabled() ? 0xFFFFFFFF : 0xFF8A8A95;
            Render2D.text(ctx, m.getName(), x + 6, yy, c);
            String k = "[" + Keys.name(m.getKey()) + "]";
            Render2D.text(ctx, k, x + width - 6 - Render2D.width(k), yy, m.isEnabled() ? ColorUtil.theme() : 0xFF8A8A95);
            yy += 11;
        }
    }
}
