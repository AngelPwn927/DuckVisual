package dev.duckvisual.module.impl.visual;

import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.setting.NumberSetting;
import dev.duckvisual.util.Animation;
import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/** Arrows around the crosshair pointing towards nearby players. */
public class Arrows extends Module {
    private final NumberSetting radius = add(new NumberSetting("Radius", 45, 20, 120, 1));
    private final NumberSetting size = add(new NumberSetting("Size", 1.0, 0.5, 2, 0.1));
    private final NumberSetting range = add(new NumberSetting("Range", 64, 8, 256, 1));
    private final BooleanSetting distance = add(new BooleanSetting("Distance", false));

    private float animRadius;

    public Arrows() {
        super("Arrows", "Arrows pointing to nearby players", Category.VISUALS);
    }

    @Override
    public void onRender2D(DrawContext ctx, float td) {
        float cx = ctx.getScaledWindowWidth() / 2f, cy = ctx.getScaledWindowHeight() / 2f;
        // expand while moving like the vanilla crosshair spread
        boolean moving = mc.player.input.getMovementInput().lengthSquared() > 0;
        animRadius = Animation.approach(animRadius, radius.getFloat() + (moving ? 10 : 0), 8);
        Vec3d me = mc.player.getLerpedPos(td);
        float yaw = mc.player.getYaw(td);
        int i = 0;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || p.isSpectator()) continue;
            Vec3d pos = p.getLerpedPos(td);
            double dist = pos.distanceTo(me);
            if (dist > range.get()) continue;
            double angle = Math.toDegrees(Math.atan2(pos.z - me.z, pos.x - me.x)) - 90 - yaw;
            float fade = (float) Math.min(1, 1.2 - dist / range.get());
            int color = ColorUtil.fade(ColorUtil.theme(i++ * 4), fade);

            ctx.getMatrices().push();
            ctx.getMatrices().translate(cx, cy, 0);
            ctx.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) angle));
            ctx.getMatrices().translate(0, -animRadius, 0);
            float s = 6 * size.getFloat();
            Render2D.glowDot(ctx, 0, 0, s * 2.2f, ColorUtil.fade(color, 0.35f));
            Render2D.triangle(ctx, 0, -s, -s * 0.8f, s * 0.7f, 0, s * 0.25f, color);
            Render2D.triangle(ctx, 0, -s, 0, s * 0.25f, s * 0.8f, s * 0.7f, ColorUtil.lerp(color, 0xFF000000, 0.2f));
            ctx.getMatrices().pop();

            if (distance.get()) {
                double rad = Math.toRadians(angle - 90);
                float tx = cx + (float) Math.cos(rad) * (animRadius + 14);
                float ty = cy + (float) Math.sin(rad) * (animRadius + 14);
                Render2D.centeredText(ctx, (int) dist + "m", tx, ty - 4, ColorUtil.fade(0xFFFFFFFF, fade));
            }
        }
    }
}
