package dev.duckvisual.module.impl.visual;

import dev.duckvisual.event.WorldRender;
import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.setting.NumberSetting;
import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Render3D;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/** Lines from the crosshair to other players. */
public class Tracers extends Module {
    private final NumberSetting range = add(new NumberSetting("Range", 96, 8, 256, 1));
    private final BooleanSetting distanceColor = add(new BooleanSetting("Distance Color", true));
    private final NumberSetting alpha = add(new NumberSetting("Alpha", 200, 30, 255, 1));

    public Tracers() {
        super("Tracers", "Lines pointing at other players", Category.VISUALS);
    }

    @Override
    public void onRender3D(WorldRender r) {
        // start point a little in front of the camera, in the look direction
        float pitch = (float) Math.toRadians(r.camera().getPitch()), yaw = (float) Math.toRadians(r.camera().getYaw());
        Vec3d start = new Vec3d(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch)).multiply(0.5);
        Render3D.depth(false);
        int i = 0;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || p.isSpectator()) continue;
            double d = p.distanceTo(mc.player);
            if (d > range.get()) continue;
            Vec3d end = r.rel(p.getLerpedPos(r.tickDelta())).add(0, p.getHeight() * 0.6, 0);
            int c = distanceColor.get()
                    ? ColorUtil.lerp(0xFFFF4040, 0xFF40FF70, (float) Math.min(1, d / 40))
                    : ColorUtil.theme(i++ * 6);
            Render3D.line(r, start, end, ColorUtil.withAlpha(c, alpha.getInt() / 3), ColorUtil.withAlpha(c, alpha.getInt()));
        }
        Render3D.depth(true);
    }
}
