package dev.duckvisual.module.impl.hud;

import dev.duckvisual.module.HudModule;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.util.Animation;
import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Combo counter and reach of your last hit. */
public class CombatInfo extends HudModule {
    private final BooleanSetting combo = add(new BooleanSetting("Combo", true));
    private final BooleanSetting reach = add(new BooleanSetting("Reach", true));

    private int comboCount;
    private long lastHit;
    private double lastReach;
    private float pop;
    private int lastHurt;

    public CombatInfo() {
        super("CombatInfo", "Combo counter and hit reach", -1, -1);
    }

    @Override
    public boolean isHidden() {
        return true;
    }

    /** Called from EventHooks when you attack. */
    public void onAttack(Entity target) {
        if (System.currentTimeMillis() - lastHit > 2500) comboCount = 0;
        comboCount++;
        lastHit = System.currentTimeMillis();
        pop = 1;
        Vec3d eye = mc.player.getEyePos();
        Box box = target.getBoundingBox();
        double cx = Math.max(box.minX, Math.min(eye.x, box.maxX));
        double cy = Math.max(box.minY, Math.min(eye.y, box.maxY));
        double cz = Math.max(box.minZ, Math.min(eye.z, box.maxZ));
        lastReach = eye.distanceTo(new Vec3d(cx, cy, cz));
    }

    @Override
    public void onTick() {
        // getting hit breaks the combo
        if (mc.player.hurtTime > 0 && lastHurt == 0) comboCount = 0;
        lastHurt = mc.player.hurtTime;
        if (System.currentTimeMillis() - lastHit > 2500) comboCount = 0;
    }

    @Override
    public void onRender2D(DrawContext ctx, float td) {
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        width = 64;
        height = 30;
        if (getX() < 0) {
            x.set(sw / 2.0 - 140);
            y.set(sh / 2.0 + 20);
        }
        boolean editing = mc.currentScreen instanceof dev.duckvisual.gui.ClickGuiScreen;
        boolean recent = System.currentTimeMillis() - lastHit < 3000;
        if (!recent && !editing) return;
        pop = Animation.approach(pop, 0, 8);
        float x0 = getX(), y0 = getY();
        panel(ctx, x0, y0, width, height);
        float yy = y0 + 5;
        if (combo.get()) {
            String c = comboCount + "x";
            ctx.getMatrices().push();
            ctx.getMatrices().translate(x0 + 6, yy, 0);
            float s = 1 + pop * 0.35f;
            ctx.getMatrices().scale(s, s, 1);
            Render2D.text(ctx, c, 0, 0, ColorUtil.theme());
            ctx.getMatrices().pop();
            Render2D.text(ctx, "combo", x0 + 10 + Render2D.width(c) * (1 + pop * 0.35f), yy, 0xFF9A9AA5);
            yy += 11;
        }
        if (reach.get()) {
            Render2D.text(ctx, String.format("%.2f", lastReach), x0 + 6, yy, 0xFFFFFFFF);
            Render2D.text(ctx, "reach", x0 + 10 + Render2D.width(String.format("%.2f", lastReach)), yy, 0xFF9A9AA5);
            yy += 11;
        }
        height = yy - y0 + 3;
    }
}
