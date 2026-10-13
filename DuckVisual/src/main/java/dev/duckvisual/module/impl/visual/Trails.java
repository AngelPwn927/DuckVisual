package dev.duckvisual.module.impl.visual;

import dev.duckvisual.event.WorldRender;
import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.setting.ModeSetting;
import dev.duckvisual.setting.NumberSetting;
import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Render3D;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.Deque;

public class Trails extends Module {
    private final ModeSetting mode = add(new ModeSetting("Mode", "Ribbon", "Ribbon", "Line", "Orbs"));
    private final NumberSetting length = add(new NumberSetting("Length", 1.0, 0.3, 3, 0.1));
    private final NumberSetting height = add(new NumberSetting("Height", 1.0, 0.2, 1.8, 0.1));
    private final BooleanSetting firstPerson = add(new BooleanSetting("First Person", false));

    private final Deque<Point> points = new ArrayDeque<>();

    private record Point(Vec3d pos, long time) {}

    public Trails() {
        super("Trails", "Draws a fading trail behind you", Category.VISUALS);
    }

    @Override
    public void onRender3D(WorldRender r) {
        long now = System.currentTimeMillis();
        long life = (long) (length.get() * 1000);
        Vec3d current = mc.player.getLerpedPos(r.tickDelta());
        Point last = points.peekLast();
        if (last == null || last.pos.squaredDistanceTo(current) > 0.0025) points.add(new Point(current, now));
        while (!points.isEmpty() && now - points.peekFirst().time > life) points.pollFirst();

        if (points.size() < 2) return;
        if (mc.options.getPerspective().isFirstPerson() && !firstPerson.get()) return;

        Matrix4f m = r.matrices().peek().getPositionMatrix();
        float h = height.getFloat();
        int i = 0;
        switch (mode.get()) {
            case "Line" -> {
                BufferBuilder b = Render3D.buffer(VertexFormat.DrawMode.DEBUG_LINE_STRIP);
                for (Point p : points) {
                    float fade = 1 - (now - p.time) / (float) life;
                    Vec3d v = r.rel(p.pos);
                    b.vertex(m, (float) v.x, (float) v.y + h / 2, (float) v.z)
                            .color(ColorUtil.withAlpha(ColorUtil.theme(i++), (int) (255 * fade)));
                }
                Render3D.draw(b);
            }
            case "Orbs" -> {
                Render3D.additive(true);
                for (Point p : points) {
                    if (i++ % 3 != 0) continue;
                    float fade = 1 - (now - p.time) / (float) life;
                    Render3D.glow(r, r.rel(p.pos).add(0, h / 2, 0), 0.15f * fade + 0.03f,
                            ColorUtil.withAlpha(ColorUtil.theme(i), (int) (200 * fade)));
                }
                Render3D.additive(false);
            }
            default -> {
                BufferBuilder b = Render3D.buffer(VertexFormat.DrawMode.TRIANGLE_STRIP);
                for (Point p : points) {
                    float fade = 1 - (now - p.time) / (float) life;
                    int c = ColorUtil.theme(i++);
                    Vec3d v = r.rel(p.pos);
                    b.vertex(m, (float) v.x, (float) v.y, (float) v.z).color(ColorUtil.withAlpha(c, (int) (40 * fade)));
                    b.vertex(m, (float) v.x, (float) v.y + h, (float) v.z).color(ColorUtil.withAlpha(c, (int) (170 * fade)));
                }
                Render3D.draw(b);
            }
        }
    }

    @Override
    public void onDisable() {
        points.clear();
    }
}
