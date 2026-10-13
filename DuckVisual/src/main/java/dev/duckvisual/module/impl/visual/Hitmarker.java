package dev.duckvisual.module.impl.visual;

import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.RotationAxis;

/** Shooter-style X hitmarker at the crosshair; red on critical hits. */
public class Hitmarker extends Module {
    private final BooleanSetting critColor = add(new BooleanSetting("Crit Color", true));

    private long hitTime;
    private boolean crit;

    public Hitmarker() {
        super("Hitmarker", "X marker at the crosshair when you hit", Category.VISUALS);
    }

    public void onHit(boolean crit) {
        hitTime = System.currentTimeMillis();
        this.crit = crit;
    }

    @Override
    public void onRender2D(DrawContext ctx, float td) {
        float t = (System.currentTimeMillis() - hitTime) / 300f;
        if (t >= 1) return;
        float cx = ctx.getScaledWindowWidth() / 2f, cy = ctx.getScaledWindowHeight() / 2f;
        int c = ColorUtil.fade(crit && critColor.get() ? 0xFFFF3B3B : 0xFFFFFFFF, 1 - t * t);
        float gap = 3 + t * 3, len = 4;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx, cy, 0);
        ctx.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(45));
        for (int i = 0; i < 4; i++) {
            ctx.getMatrices().push();
            ctx.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(90 * i));
            Render2D.rect(ctx, -0.75f, gap, 1.5f, len, c);
            ctx.getMatrices().pop();
        }
        ctx.getMatrices().pop();
    }
}
