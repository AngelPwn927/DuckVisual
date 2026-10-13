package dev.duckvisual.module.impl.visual;

import dev.duckvisual.event.WorldRender;
import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.setting.NumberSetting;
import dev.duckvisual.util.Animation;
import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Render3D;
import net.minecraft.block.BlockState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;

/** Replaces the vanilla black block outline with a smooth, theme-colored box. */
public class BlockOverlay extends Module {
    private final BooleanSetting fill = add(new BooleanSetting("Fill", true));
    private final NumberSetting fillAlpha = add(new NumberSetting("Fill Alpha", 50, 5, 200, 1).visibleWhen(fill::get));
    private final BooleanSetting outline = add(new BooleanSetting("Outline", true));
    private final BooleanSetting smooth = add(new BooleanSetting("Smooth Move", true));
    private final BooleanSetting throughWalls = add(new BooleanSetting("Through Walls", false));

    private Box shown;

    public BlockOverlay() {
        super("BlockOverlay", "Custom outline for the block you look at", Category.VISUALS);
    }

    @Override
    public void onRender3D(WorldRender r) {
        HitResult hit = mc.crosshairTarget;
        Box target = null;
        if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = bhr.getBlockPos();
            BlockState state = mc.world.getBlockState(pos);
            VoxelShape shape = state.getOutlineShape(mc.world, pos);
            if (!shape.isEmpty()) target = shape.getBoundingBox().offset(pos).expand(0.002);
        }
        if (target == null) {
            shown = null;
            return;
        }
        if (shown == null || !smooth.get()) shown = target;
        else {
            float s = 22;
            shown = new Box(
                    Animation.approach((float) shown.minX, (float) target.minX, s), Animation.approach((float) shown.minY, (float) target.minY, s),
                    Animation.approach((float) shown.minZ, (float) target.minZ, s), Animation.approach((float) shown.maxX, (float) target.maxX, s),
                    Animation.approach((float) shown.maxY, (float) target.maxY, s), Animation.approach((float) shown.maxZ, (float) target.maxZ, s));
            // precision loss of float lerp at large coordinates: snap when close
            if (Math.abs(shown.minX - target.minX) < 0.01 && Math.abs(shown.minY - target.minY) < 0.01 && Math.abs(shown.minZ - target.minZ) < 0.01)
                shown = target;
        }
        Box rel = shown.offset(r.camera().getPos().negate());
        int c1 = ColorUtil.theme(0), c2 = ColorUtil.theme(10);
        if (throughWalls.get()) Render3D.depth(false);
        if (fill.get()) Render3D.filledBox(r, rel, ColorUtil.withAlpha(c1, fillAlpha.getInt()), ColorUtil.withAlpha(c2, fillAlpha.getInt()));
        if (outline.get()) Render3D.outlineBox(r, rel, c1, c2);
        Render3D.depth(true);
    }
}
