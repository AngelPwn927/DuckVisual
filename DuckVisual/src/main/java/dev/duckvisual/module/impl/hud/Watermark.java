package dev.duckvisual.module.impl.hud;

import dev.duckvisual.DuckVisual;
import dev.duckvisual.module.HudModule;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Render2D;
import dev.duckvisual.util.Tps;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** macOS-style menu bar pill: logo, then FPS, ping, TPS, time and players as label/value segments. */
public class Watermark extends HudModule {
    private final BooleanSetting showFps = add(new BooleanSetting("FPS", true));
    private final BooleanSetting showPing = add(new BooleanSetting("Ping", true));
    private final BooleanSetting showTps = add(new BooleanSetting("TPS", true));
    private final BooleanSetting showTime = add(new BooleanSetting("Time", true));
    private final BooleanSetting showPlayers = add(new BooleanSetting("Players", false));
    private final BooleanSetting showName = add(new BooleanSetting("Nickname", false));

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int LABEL = 0xFF98989F, VALUE = 0xFFF2F2F7;

    public Watermark() {
        super("Watermark", "Client logo with info", 5, 5);
    }

    @Override
    public boolean isHidden() {
        return true;
    }

    @Override
    public void onRender2D(DrawContext ctx, float td) {
        List<String[]> seg = new ArrayList<>();
        if (showName.get()) seg.add(new String[]{"", mc.getSession().getUsername()});
        if (showFps.get()) seg.add(new String[]{"FPS ", String.valueOf(mc.getCurrentFps())});
        if (showPing.get()) {
            PlayerListEntry e = mc.getNetworkHandler() == null ? null : mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            seg.add(new String[]{"Ping ", (e == null ? 0 : e.getLatency()) + "ms"});
        }
        if (showTps.get()) seg.add(new String[]{"TPS ", String.format("%.1f", Tps.get())});
        if (showTime.get()) seg.add(new String[]{"", LocalTime.now().format(TIME)});
        if (showPlayers.get() && mc.getNetworkHandler() != null) seg.add(new String[]{"Online ", String.valueOf(mc.getNetworkHandler().getPlayerList().size())});

        String logo = DuckVisual.NAME;
        float x = getX(), y = getY();
        float icon = 12;
        float w = 7 + icon + 4 + Render2D.width(logo) + 7;
        for (String[] s : seg) w += 9 + Render2D.width(s[0] + s[1]);
        width = w;
        height = 19;

        panel(ctx, x, y, width, height);
        duck(ctx, x + 7, y + 3.5f, icon);
        float cx = x + 7 + icon + 4;
        Render2D.gradientText(ctx, logo, cx, y + 6, 0);
        cx += Render2D.width(logo) + 4;
        for (String[] s : seg) {
            Render2D.rect(ctx, cx, y + 5, 1, height - 10, 0x26FFFFFF);
            cx += 5;
            if (!s[0].isEmpty()) {
                Render2D.text(ctx, s[0], cx, y + 6, LABEL);
                cx += Render2D.width(s[0]);
            }
            Render2D.text(ctx, s[1], cx, y + 6, VALUE);
            cx += Render2D.width(s[1]) + 4;
        }
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
