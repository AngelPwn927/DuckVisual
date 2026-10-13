package dev.duckvisual.module.impl.world;

import dev.duckvisual.event.WorldRender;
import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.setting.NumberSetting;
import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Render3D;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Theme-colored glowing shards and a fading box when you break a block. */
public class BreakEffects extends Module {
    private final NumberSetting count = add(new NumberSetting("Particles", 14, 0, 40, 1));
    private final BooleanSetting box = add(new BooleanSetting("Fade Box", true));

    private final List<float[]> parts = new ArrayList<>(); // x y z px py pz vx vy vz born color
    private final List<Object[]> boxes = new ArrayList<>();
    private final Random random = new Random();

    public BreakEffects() {
        super("BreakEffects", "Glowing effect when breaking blocks", Category.WORLD);
    }

    public void onBreak(BlockPos pos) {
        long now = System.currentTimeMillis();
        for (int i = 0; i < count.getInt(); i++) {
            float x = pos.getX() + 0.5f, y = pos.getY() + 0.5f, z = pos.getZ() + 0.5f;
            parts.add(new float[]{x, y, z, x, y, z, (random.nextFloat() - 0.5f) * 0.25f, random.nextFloat() * 0.25f,
                    (random.nextFloat() - 0.5f) * 0.25f, now % 100000, random.nextInt(30)});
        }
        if (box.get()) boxes.add(new Object[]{new Box(pos), now});
    }

    @Override
    public void onTick() {
        for (float[] p : parts) {
            p[3] = p[0]; p[4] = p[1]; p[5] = p[2];
            p[7] -= 0.03f;
            p[6] *= 0.94f; p[7] *= 0.98f; p[8] *= 0.94f;
            p[0] += p[6]; p[1] += p[7]; p[2] += p[8];
        }
    }

    @Override
    public void onRender3D(WorldRender r) {
        long now = System.currentTimeMillis() % 100000;
        parts.removeIf(p -> (now - p[9] + 100000) % 100000 > 1200);
        Render3D.additive(true);
        for (float[] p : parts) {
            float age = ((now - p[9] + 100000) % 100000) / 1200f;
            Vec3d prev = new Vec3d(p[3], p[4], p[5]), cur = new Vec3d(p[0], p[1], p[2]);
            Render3D.orb(r, r.rel(prev.lerp(cur, r.tickDelta())), 0.12f * (1 - age) + 0.02f, ColorUtil.fade(ColorUtil.theme((int) p[10]), 1 - age));
        }
        Render3D.additive(false);
        long abs = System.currentTimeMillis();
        boxes.removeIf(b -> abs - (long) b[1] > 500);
        for (Object[] b : boxes) {
            float t = (abs - (long) b[1]) / 500f;
            Box bx = ((Box) b[0]).expand(t * 0.15).offset(r.camera().getPos().negate());
            int c = ColorUtil.theme();
            Render3D.filledBox(r, bx, ColorUtil.withAlpha(c, (int) (70 * (1 - t))), ColorUtil.withAlpha(c, (int) (70 * (1 - t))));
            Render3D.outlineBox(r, bx, ColorUtil.withAlpha(c, (int) (255 * (1 - t))), ColorUtil.withAlpha(c, (int) (255 * (1 - t))));
        }
    }

    @Override
    public void onDisable() {
        parts.clear();
        boxes.clear();
    }
}
