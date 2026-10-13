package dev.duckvisual.module.impl.hud;

import dev.duckvisual.mixin.PlayerListHudAccessor;
import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.util.Animation;
import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.world.GameMode;

import java.util.List;

/** Redesigned player list (Tab): rounded panel, heads, colored ping. */
public class TabList extends Module {
    private final BooleanSetting header = add(new BooleanSetting("Header/Footer", true));
    private final BooleanSetting pingNumber = add(new BooleanSetting("Ping Number", true));

    private float anim;
    private long lastRender;

    public TabList() {
        super("TabList", "Redesigned player list (Tab)", Category.HUD);
    }

    @Override
    public boolean isHidden() {
        return true;
    }

    public void render(DrawContext ctx, PlayerListHudAccessor hud) {
        long now = System.currentTimeMillis();
        if (now - lastRender > 200) anim = 0; // re-opened
        lastRender = now;
        anim = Animation.approach(anim, 1, 14);

        List<PlayerListEntry> entries = hud.duck$collect();
        int sw = ctx.getScaledWindowWidth();
        int rows = Math.min(20, Math.max(1, entries.size()));
        int cols = (entries.size() + rows - 1) / rows;
        if (cols == 0) cols = 1;
        int colW = 0;
        for (PlayerListEntry e : entries) colW = Math.max(colW, Render2D.width(name(e)) + (pingNumber.get() ? 34 : 18) + 14);
        colW = Math.max(colW, 110);
        float w = cols * colW + (cols - 1) * 4 + 12;

        String head = header.get() && hud.duck$header() != null ? hud.duck$header().getString() : null;
        String foot = header.get() && hud.duck$footer() != null ? hud.duck$footer().getString() : null;
        String[] headLines = head == null || head.isBlank() ? new String[0] : head.split("\n");
        String[] footLines = foot == null || foot.isBlank() ? new String[0] : foot.split("\n");
        for (String l : headLines) w = Math.max(w, Render2D.width(l) + 16);
        for (String l : footLines) w = Math.max(w, Render2D.width(l) + 16);

        float h = 22 + rows * 13 + headLines.length * 10 + footLines.length * 10 + (footLines.length > 0 ? 4 : 0);
        float x = sw / 2f - w / 2, y = 10 - 8 * (1 - Animation.easeOutCubic(anim));
        float a = anim;

        Render2D.shadow(ctx, x, y, w, h, 6, 6, ColorUtil.withAlpha(ColorUtil.theme(), (int) (90 * a)));
        Render2D.roundedRect(ctx, x, y, w, h, 6, ColorUtil.withAlpha(0xFF101015, (int) (230 * a)));
        Render2D.roundedGradient(ctx, x, y, w, 2, 1, ColorUtil.theme(0), ColorUtil.theme(10), ColorUtil.theme(10), ColorUtil.theme(0));

        float yy = y + 6;
        for (String l : headLines) {
            Render2D.centeredText(ctx, l, sw / 2f, yy, ColorUtil.fade(0xFFFFFFFF, a));
            yy += 10;
        }
        String title = "Players: " + entries.size();
        Render2D.centeredText(ctx, title, sw / 2f, yy, ColorUtil.fade(ColorUtil.theme(), a));
        yy += 12;

        for (int i = 0; i < entries.size() && i < rows * cols; i++) {
            PlayerListEntry e = entries.get(i);
            int col = i / rows, row = i % rows;
            float ex = x + 6 + col * (colW + 4), ey = yy + row * 13;
            boolean self = mc.player != null && e.getProfile().getId().equals(mc.player.getUuid());
            Render2D.roundedRect(ctx, ex, ey, colW, 12, 3, ColorUtil.withAlpha(self ? ColorUtil.theme() : 0xFF1C1C24, (int) ((self ? 60 : 200) * a)));
            PlayerSkinDrawer.draw(ctx, e.getSkinTextures(), (int) ex + 2, (int) ey + 2, 8, ColorUtil.withAlpha(0xFFFFFFFF, (int) (255 * a)));
            int nameColor = e.getGameMode() == GameMode.SPECTATOR ? 0xFF8A8A95 : 0xFFFFFFFF;
            Render2D.text(ctx, name(e), ex + 13, ey + 2, ColorUtil.fade(nameColor, a));
            int ping = e.getLatency();
            int pc = ping < 80 ? 0xFF55FF77 : ping < 160 ? 0xFFFFD43B : ping < 300 ? 0xFFFF9A3B : 0xFFFF4545;
            // signal bars
            float bx = ex + colW - 14;
            int bars = ping < 0 ? 0 : ping < 80 ? 4 : ping < 160 ? 3 : ping < 300 ? 2 : 1;
            for (int b = 0; b < 4; b++) {
                float bh = 2 + b * 2;
                Render2D.rect(ctx, bx + b * 3, ey + 10 - bh, 2, bh, ColorUtil.fade(b < bars ? pc : 0xFF3A3A44, a));
            }
            if (pingNumber.get()) {
                String ps = ping + "";
                ctx.getMatrices().push();
                ctx.getMatrices().translate(bx - 3 - Render2D.width(ps) * 0.7f, ey + 3, 0);
                ctx.getMatrices().scale(0.7f, 0.7f, 1);
                Render2D.text(ctx, ps, 0, 0, ColorUtil.fade(pc, a));
                ctx.getMatrices().pop();
            }
        }
        yy += rows * 13 + 4;
        for (String l : footLines) {
            Render2D.centeredText(ctx, l, sw / 2f, yy, ColorUtil.fade(0xFFB0B0BA, a));
            yy += 10;
        }
    }

    private static String name(PlayerListEntry e) {
        return e.getDisplayName() != null ? e.getDisplayName().getString() : e.getProfile().getName();
    }
}
