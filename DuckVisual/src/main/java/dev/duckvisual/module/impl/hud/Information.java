package dev.duckvisual.module.impl.hud;

import dev.duckvisual.module.HudModule;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.world.World;

/** Coordinates, speed and server info in the bottom-left corner. */
public class Information extends HudModule {
    private final BooleanSetting coords = add(new BooleanSetting("Coords", true));
    private final BooleanSetting nether = add(new BooleanSetting("Nether Coords", true));
    private final BooleanSetting speed = add(new BooleanSetting("Speed", true));
    private final BooleanSetting server = add(new BooleanSetting("Server", true));

    public Information() {
        super("Information", "Coordinates, speed and server", 5, -1);
    }

    @Override
    public boolean isHidden() {
        return true;
    }

    @Override
    public void onRender2D(DrawContext ctx, float td) {
        int sh = ctx.getScaledWindowHeight();
        if (getY() < 0) y.set(sh - 40.0);
        float yy = getY();
        float maxW = 0;
        int i = 0;
        if (coords.get()) {
            double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
            String s = String.format("XYZ: %.0f, %.0f, %.0f", px, py, pz);
            boolean inNether = mc.world.getRegistryKey() == World.NETHER;
            if (nether.get() && (inNether || mc.world.getRegistryKey() == World.OVERWORLD)) {
                double f = inNether ? 8 : 0.125;
                s += String.format(" §7[%.0f, %.0f]", px * f, pz * f);
            }
            maxW = Math.max(maxW, line(ctx, s, yy, i++));
            yy += 10;
        }
        if (speed.get()) {
            double dx = mc.player.getX() - mc.player.prevX, dz = mc.player.getZ() - mc.player.prevZ;
            double bps = Math.sqrt(dx * dx + dz * dz) * 20 * mc.world.getTickManager().getTickRate() / 20;
            maxW = Math.max(maxW, line(ctx, String.format("BPS: %.2f", bps), yy, i++));
            yy += 10;
        }
        if (server.get()) {
            String ip = mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address : "Singleplayer";
            maxW = Math.max(maxW, line(ctx, "Server: " + ip, yy, i++));
            yy += 10;
        }
        width = Math.max(40, maxW);
        height = Math.max(10, yy - getY());
    }

    private float line(DrawContext ctx, String s, float yy, int i) {
        int colon = s.indexOf(':');
        String key = s.substring(0, colon + 1);
        Render2D.text(ctx, key, getX(), yy, ColorUtil.theme(i * 4));
        Render2D.text(ctx, s.substring(colon + 1), getX() + Render2D.width(key), yy, 0xFFFFFFFF);
        return Render2D.width(s);
    }
}
