package dev.duckvisual.module.impl.hud;

import dev.duckvisual.DuckVisual;
import dev.duckvisual.module.HudModule;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class Watermark extends HudModule {
    private final BooleanSetting showFps = add(new BooleanSetting("FPS", true));
    private final BooleanSetting showPing = add(new BooleanSetting("Ping", true));
    private final BooleanSetting showTime = add(new BooleanSetting("Time", true));
    private final BooleanSetting showName = add(new BooleanSetting("Nickname", true));

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    public Watermark() {
        super("Watermark", "Client logo with info", 5, 5);
    }

    @Override
    public boolean isHidden() {
        return true;
    }

    @Override
    public void onRender2D(DrawContext ctx, float td) {
        StringBuilder info = new StringBuilder();
        if (showName.get()) info.append(mc.getSession().getUsername());
        if (showFps.get()) sep(info).append(mc.getCurrentFps()).append(" fps");
        if (showPing.get()) {
            PlayerListEntry e = mc.getNetworkHandler() == null ? null : mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            sep(info).append(e == null ? 0 : e.getLatency()).append(" ms");
        }
        if (showTime.get()) sep(info).append(LocalTime.now().format(TIME));

        String logo = DuckVisual.NAME;
        float x = getX(), y = getY();
        float iconSize = 13;
        float logoW = Render2D.width(logo);
        float infoW = info.length() > 0 ? Render2D.width(info.toString()) + 12 : 0;
        width = 6 + iconSize + 4 + logoW + infoW + 6;
        height = 19;

        panel(ctx, x, y, width, height);
        duck(ctx, x + 5, y + 3, iconSize);
        Render2D.gradientText(ctx, logo, x + 6 + iconSize + 4, y + 6, 0);
        if (infoW > 0) {
            float sx = x + 6 + iconSize + 4 + logoW + 5;
            Render2D.rect(ctx, sx, y + 5, 1, height - 10, 0x40FFFFFF);
            Render2D.text(ctx, info.toString(), sx + 6, y + 6, 0xFFD0D0D8);
        }
    }

    private static StringBuilder sep(StringBuilder sb) {
        if (sb.length() > 0) sb.append(" · ");
        return sb;
    }

    /** Tiny vector duck used as the logo. */
    public static void duck(DrawContext ctx, float x, float y, float s) {
        int body = ColorUtil.theme(0);
        int head = ColorUtil.theme(6);
        Render2D.glowDot(ctx, x + s * 0.5f, y + s * 0.55f, s * 0.9f, ColorUtil.withAlpha(body, 60));
        Render2D.roundedRect(ctx, x, y + s * 0.45f, s * 0.85f, s * 0.5f, s * 0.24f, body);
        Render2D.circle(ctx, x + s * 0.62f, y + s * 0.32f, s * 0.24f, head);
        Render2D.triangle(ctx, x + s * 0.8f, y + s * 0.26f, x + s * 0.8f, y + s * 0.42f, x + s, y + s * 0.36f, 0xFFFFA030);
        Render2D.circle(ctx, x + s * 0.66f, y + s * 0.28f, s * 0.05f, 0xFF101010);
    }
}
