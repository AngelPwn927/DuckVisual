package dev.duckvisual.module.impl.hud;

import dev.duckvisual.DuckVisual;
import dev.duckvisual.module.HudModule;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Render2D;
import net.minecraft.client.gui.DrawContext;

import java.util.Comparator;
import java.util.List;

public class ArrayListHud extends HudModule {
    private final BooleanSetting background = add(new BooleanSetting("Background", true));
    private final BooleanSetting line = add(new BooleanSetting("Side Line", true));
    private final BooleanSetting suffix = add(new BooleanSetting("Suffix", true));

    public ArrayListHud() {
        super("ArrayList", "List of enabled modules", -1, 5);
    }

    @Override
    public boolean isHidden() {
        return true;
    }

    private String label(Module m) {
        String s = m.getSuffix();
        return suffix.get() && s != null ? m.getName() + " §7" + s : m.getName();
    }

    @Override
    public void onRender2D(DrawContext ctx, float td) {
        int sw = ctx.getScaledWindowWidth();
        if (getX() < 0) x.set((double) sw - width - 3);
        List<Module> list = DuckVisual.modules().getModules().stream()
                .filter(m -> !m.isHidden() && m.animation > 0.01f)
                .sorted(Comparator.comparingInt(m -> -Render2D.width(label(m))))
                .toList();

        // align to whichever screen half the element sits on
        boolean right = getX() + width / 2 > sw / 2f;
        float maxW = 0;
        for (Module m : list) maxW = Math.max(maxW, Render2D.width(label(m)) + 8);
        float oldW = width;
        width = Math.max(60, maxW);
        // right-anchored: grow towards the left so the list never leaves the screen
        if (right) x.set((double) Math.min(sw - width - 3, getX() + oldW - width));
        float yy = getY();
        int i = 0;
        for (Module m : list) {
            float a = m.animation;
            String text = label(m);
            float w = Render2D.width(text) + 8;
            float h = 11 * a;
            float xx = right ? getX() + width - w * a : getX() - w * (1 - a);
            int color = ColorUtil.theme(i * 3);
            if (background.get()) {
                Render2D.rect(ctx, xx, yy, w, h, ColorUtil.withAlpha(0xFF0F0F14, (int) (200 * a)));
            }
            if (line.get()) {
                Render2D.rect(ctx, right ? xx + w - 1.5f : xx, yy, 1.5f, h, ColorUtil.fade(color, a));
            }
            Render2D.text(ctx, text, xx + 4, yy + 2 * a, ColorUtil.fade(color, a));
            yy += h;
            i++;
        }
        height = Math.max(11, yy - getY());
    }
}
