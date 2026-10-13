package dev.duckvisual.module.impl.visual;

import dev.duckvisual.EventHooks;
import dev.duckvisual.event.WorldRender;
import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.setting.NumberSetting;
import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Render3D;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

/** Visual-only circle around your target showing attack range and distance. */
public class TargetCircle extends Module {
    private final NumberSetting radius = add(new NumberSetting("Radius", 3, 1, 6, 0.1));
    private final BooleanSetting distance = add(new BooleanSetting("Distance", true));

    public TargetCircle() {
        super("TargetCircle", "Range circle and distance around your target", Category.VISUALS);
    }

    @Override
    public void onRender3D(WorldRender r) {
        LivingEntity t = EventHooks.currentTarget(5000);
        if (t == null) return;
        Vec3d feet = r.rel(t.getLerpedPos(r.tickDelta())).add(0, 0.02, 0);
        double d = mc.player.distanceTo(t);
        boolean inRange = d <= radius.get();
        int c = inRange ? ColorUtil.theme() : 0xFFFF4545;
        float rr = radius.getFloat();
        Render3D.ring(r, feet, rr - 0.06f, rr, ColorUtil.withAlpha(c, 230), ColorUtil.withAlpha(c, 230));
        Render3D.ring(r, feet, rr - 0.5f, rr - 0.06f, ColorUtil.withAlpha(c, 0), ColorUtil.withAlpha(c, 60));
        if (distance.get()) Render3D.text(r, String.format("%.1fm", d), feet.add(0, 0.25, 0), 1.0f, c, false);
    }
}
