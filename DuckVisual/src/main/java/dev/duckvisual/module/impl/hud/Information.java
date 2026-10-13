package dev.duckvisual.module.impl.hud;

import dev.duckvisual.module.HudModule;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.util.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/** Coordinates, speed and server in a small glass pill (under the watermark by default). */
public class Information extends HudModule {
    private final BooleanSetting coords = add(new BooleanSetting("Coords", true));
    private final BooleanSetting nether = add(new BooleanSetting("Nether Coords", true));
    private final BooleanSetting speed = add(new BooleanSetting("Speed", true));
    private final BooleanSetting server = add(new BooleanSetting("Server", false));

    private static final int LABEL = 0xFF98989F, VALUE = 0xFFF2F2F7;

    public Information() {
        super("Information", "Coordinates, speed and server", 5, 27);
    }

    @Override
    public boolean isHidden() {
        return true;
    }

    @Override
    public void onRender2D(DrawContext ctx, float td) {
        List<String[]> seg = new ArrayList<>();
        if (coords.get()) {
            double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
            seg.add(new String[]{"", String.format("%.0f, %.0f, %.0f", px, py, pz)});
            boolean inNether = mc.world.getRegistryKey() == World.NETHER;
            if (nether.get() && (inNether || mc.world.getRegistryKey() == World.OVERWORLD)) {
                double f = inNether ? 8 : 0.125;
                seg.add(new String[]{inNether ? "OW " : "Nether ", String.format("%.0f, %.0f", px * f, pz * f)});
            }
        }
        if (speed.get()) {
            double dx = mc.player.getX() - mc.player.prevX, dz = mc.player.getZ() - mc.player.prevZ;
            double bps = Math.sqrt(dx * dx + dz * dz) * mc.world.getTickManager().getTickRate();
            seg.add(new String[]{"BPS ", String.format("%.1f", bps)});
        }
        if (server.get()) {
            seg.add(new String[]{"", mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address : "Singleplayer"});
        }
        if (seg.isEmpty()) return;
        float w = 4;
        for (String[] s : seg) w += 9 + Render2D.width(s[0] + s[1]);
        width = w;
        height = 17;
        float x = getX(), y = getY();
        if (y < 0) {
            this.y.set(27.0);
            y = 27;
        }
        panel(ctx, x, y, width, height);
        float cx = x + 2;
        boolean first = true;
        for (String[] s : seg) {
            if (!first) Render2D.rect(ctx, cx, y + 4, 1, height - 8, 0x26FFFFFF);
            first = false;
            cx += 5;
            if (!s[0].isEmpty()) {
                Render2D.text(ctx, s[0], cx, y + 5, LABEL);
                cx += Render2D.width(s[0]);
            }
            Render2D.text(ctx, s[1], cx, y + 5, VALUE);
            cx += Render2D.width(s[1]) + 4;
        }
    }
}
