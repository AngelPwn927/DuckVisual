package dev.duckvisual.module.impl.visual;

import dev.duckvisual.event.WorldRender;
import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.setting.ModeSetting;
import dev.duckvisual.setting.NumberSetting;
import dev.duckvisual.util.Animation;
import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Render3D;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class JumpCircles extends Module {
    private final ModeSetting mode = add(new ModeSetting("Mode", "Glow", "Glow", "Ring", "Wave"));
    private final NumberSetting radius = add(new NumberSetting("Radius", 1.2, 0.5, 3, 0.1));
    private final NumberSetting duration = add(new NumberSetting("Duration", 1.4, 0.5, 4, 0.1));
    private final BooleanSetting additive = add(new BooleanSetting("Bright", true));

    private final List<Circle> circles = new ArrayList<>();
    private boolean wasOnGround = true;

    private record Circle(Vec3d pos, long start) {}

    public JumpCircles() {
        super("JumpCircles", "Spawns circles when you jump", Category.VISUALS);
    }

    @Override
    public void onTick() {
        boolean onGround = mc.player.isOnGround();
        if (wasOnGround && !onGround && mc.player.getVelocity().y > 0.2) {
            circles.add(new Circle(mc.player.getPos().add(0, 0.01, 0), System.currentTimeMillis()));
        }
        wasOnGround = onGround;
    }

    @Override
    public void onRender3D(WorldRender r) {
        long now = System.currentTimeMillis();
        long life = (long) (duration.get() * 1000);
        circles.removeIf(c -> now - c.start > life);
        if (circles.isEmpty()) return;
        Render3D.additive(additive.get());
        for (Circle c : circles) {
            float t = (now - c.start) / (float) life;
            float grow = Animation.easeOutCubic(t);
            float fade = 1 - Animation.easeInOutQuad(t);
            float rad = radius.getFloat() * grow;
            int color = ColorUtil.theme((int) (t * 20));
            Vec3d p = r.rel(c.pos);
            switch (mode.get()) {
                case "Ring" -> Render3D.ring(r, p, Math.max(0, rad - 0.08f), rad,
                        ColorUtil.withAlpha(color, (int) (255 * fade)), ColorUtil.withAlpha(color, (int) (255 * fade)));
                case "Wave" -> {
                    for (int i = 0; i < 3; i++) {
                        float rr = rad * (1 - i * 0.25f);
                        Render3D.ring(r, p, Math.max(0, rr - 0.05f), rr,
                                ColorUtil.withAlpha(color, (int) (200 * fade)), ColorUtil.withAlpha(color, (int) (60 * fade)));
                    }
                }
                default -> {
                    Render3D.ring(r, p, Math.max(0, rad - 0.35f), rad,
                            ColorUtil.withAlpha(color, 0), ColorUtil.withAlpha(color, (int) (220 * fade)));
                    Render3D.ring(r, p, rad, rad + 0.12f,
                            ColorUtil.withAlpha(color, (int) (220 * fade)), ColorUtil.withAlpha(color, 0));
                }
            }
        }
        Render3D.additive(false);
    }

    @Override
    public void onDisable() {
        circles.clear();
    }
}
