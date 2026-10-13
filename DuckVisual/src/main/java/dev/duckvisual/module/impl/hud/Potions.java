package dev.duckvisual.module.impl.hud;

import dev.duckvisual.module.HudModule;
import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.util.StringHelper;

import java.util.ArrayList;
import java.util.List;

public class Potions extends HudModule {
    public Potions() {
        super("Potions", "Shows active potion effects", 5, 130);
    }

    @Override
    public boolean isHidden() {
        return true;
    }

    @Override
    public void onRender2D(DrawContext ctx, float td) {
        List<StatusEffectInstance> effects = new ArrayList<>(mc.player.getStatusEffects());
        float x = getX(), y = getY();
        width = 110;
        List<String[]> rows = new ArrayList<>();
        for (StatusEffectInstance e : effects) {
            String name = e.getEffectType().value().getName().getString();
            if (e.getAmplifier() > 0) name += " " + (e.getAmplifier() + 1);
            String dur = e.isInfinite() ? "∞" : StringHelper.formatTicks(e.getDuration(), mc.world.getTickManager().getTickRate());
            rows.add(new String[]{name, dur});
            width = Math.max(width, Render2D.width(name) + Render2D.width(dur) + 22);
        }
        height = 18 + rows.size() * 11 + (rows.isEmpty() ? 0 : 3);
        panel(ctx, x, y, width, height);
        header(ctx, x, y, width);
        Render2D.text(ctx, "Potions", x + 6, y + 6, 0xFFFFFFFF);
        float yy = y + 19;
        for (int i = 0; i < rows.size(); i++) {
            StatusEffectInstance e = effects.get(i);
            int color = 0xFF000000 | e.getEffectType().value().getColor();
            boolean ending = !e.isInfinite() && e.getDuration() < 200 && (System.currentTimeMillis() / 300) % 2 == 0;
            Render2D.circle(ctx, x + 8, yy + 3.5f, 2.5f, color);
            Render2D.text(ctx, rows.get(i)[0], x + 14, yy, 0xFFFFFFFF);
            Render2D.text(ctx, rows.get(i)[1], x + width - 6 - Render2D.width(rows.get(i)[1]), yy,
                    ending ? 0xFFFF5555 : ColorUtil.theme(i * 3));
            yy += 11;
        }
    }
}
