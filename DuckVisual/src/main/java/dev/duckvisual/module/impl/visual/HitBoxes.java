package dev.duckvisual.module.impl.visual;

import dev.duckvisual.event.WorldRender;
import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.setting.ModeSetting;
import dev.duckvisual.setting.NumberSetting;
import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Render3D;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Styled version of the F3+B hitboxes (visual only, does not change hitbox sizes). */
public class HitBoxes extends Module {
    private final ModeSetting color = add(new ModeSetting("Color", "Theme", "Theme", "White", "Health"));
    private final BooleanSetting players = add(new BooleanSetting("Players", true));
    private final BooleanSetting mobs = add(new BooleanSetting("Mobs", true));
    private final BooleanSetting items = add(new BooleanSetting("Items", false));
    private final BooleanSetting self = add(new BooleanSetting("Self", false));
    private final BooleanSetting eyeLine = add(new BooleanSetting("Eye Height", true));
    private final BooleanSetting lookLine = add(new BooleanSetting("Look Direction", true));
    private final BooleanSetting fill = add(new BooleanSetting("Fill", false));
    private final BooleanSetting throughWalls = add(new BooleanSetting("Through Walls", false));
    private final NumberSetting range = add(new NumberSetting("Range", 48, 8, 128, 1));

    public HitBoxes() {
        super("HitBoxes", "Shows entity hitboxes (like F3+B)", Category.VISUALS);
    }

    private boolean valid(Entity e) {
        if (e.squaredDistanceTo(mc.player) > range.get() * range.get()) return false;
        if (e == mc.player) return self.get() && !mc.options.getPerspective().isFirstPerson();
        if (e instanceof PlayerEntity) return players.get() && !e.isSpectator();
        if (e instanceof LivingEntity) return mobs.get();
        if (e instanceof ItemEntity) return items.get();
        return false;
    }

    private int colorFor(Entity e, int index) {
        return switch (color.get()) {
            case "White" -> 0xFFFFFFFF;
            case "Health" -> e instanceof LivingEntity le
                    ? ColorUtil.lerp(0xFFFF4040, 0xFF40FF70, Math.min(1, le.getHealth() / le.getMaxHealth()))
                    : 0xFFFFFFFF;
            default -> ColorUtil.theme(index * 5);
        };
    }

    @Override
    public void onRender3D(WorldRender r) {
        if (throughWalls.get()) Render3D.depth(false);
        int i = 0;
        for (Entity e : mc.world.getEntities()) {
            if (!valid(e)) continue;
            Vec3d pos = r.rel(e.getLerpedPos(r.tickDelta()));
            Box box = e.getBoundingBox().offset(e.getPos().negate()).offset(pos);
            int c = colorFor(e, i++);
            if (e instanceof LivingEntity le && le.hurtTime > 0) c = ColorUtil.lerp(c, 0xFFFF3030, le.hurtTime / 10f);
            if (fill.get()) Render3D.filledBox(r, box, ColorUtil.withAlpha(c, 35), ColorUtil.withAlpha(c, 35));
            Render3D.outlineBox(r, box, c, c);
            if (e instanceof LivingEntity) {
                if (eyeLine.get()) {
                    double ey = pos.y + e.getStandingEyeHeight();
                    Box eye = new Box(box.minX, ey - 0.01, box.minZ, box.maxX, ey + 0.01, box.maxZ);
                    Render3D.outlineBox(r, eye, 0xFFFF3B3B, 0xFFFF3B3B);
                }
                if (lookLine.get()) {
                    Vec3d eyePos = pos.add(0, e.getStandingEyeHeight(), 0);
                    Vec3d look = e.getRotationVec(r.tickDelta()).multiply(2);
                    Render3D.line(r, eyePos, eyePos.add(look), 0xFF3B8BFF, 0x203B8BFF);
                }
            }
        }
        Render3D.depth(true);
    }
}
