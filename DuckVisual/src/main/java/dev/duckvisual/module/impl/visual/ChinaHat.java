package dev.duckvisual.module.impl.visual;

import dev.duckvisual.event.WorldRender;
import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.setting.NumberSetting;
import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Render3D;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public class ChinaHat extends Module {
    private final NumberSetting radius = add(new NumberSetting("Radius", 0.7, 0.3, 1.2, 0.05));
    private final NumberSetting height = add(new NumberSetting("Height", 0.3, 0.1, 0.6, 0.05));
    private final NumberSetting alpha = add(new NumberSetting("Alpha", 150, 30, 255, 1));
    private final BooleanSetting outline = add(new BooleanSetting("Outline", true));
    private final BooleanSetting others = add(new BooleanSetting("Others", false));
    private final BooleanSetting firstPerson = add(new BooleanSetting("First Person", false));

    public ChinaHat() {
        super("ChinaHat", "Renders a cone hat above the head", Category.VISUALS);
    }

    @Override
    public void onRender3D(WorldRender r) {
        for (PlayerEntity p : mc.world.getPlayers()) {
            boolean self = p == mc.player;
            if (!self && !others.get()) continue;
            if (p.isInvisible()) continue;
            if (self && mc.options.getPerspective().isFirstPerson() && !firstPerson.get()) continue;
            draw(r, p);
        }
    }

    private void draw(WorldRender r, PlayerEntity p) {
        Vec3d pos = r.rel(p.getLerpedPos(r.tickDelta()));
        float headY = (float) (p.getStandingEyeHeight() + (p.isSneaking() ? 0.25 : 0.3));
        float rad = radius.getFloat(), h = height.getFloat();
        int a = alpha.getInt();
        float yaw = p.getYaw(r.tickDelta());

        MatrixStack ms = r.matrices();
        ms.push();
        ms.translate(pos.x, pos.y + headY, pos.z);
        Matrix4f m = ms.peek().getPositionMatrix();

        int seg = 64;
        BufferBuilder b = Render3D.buffer(VertexFormat.DrawMode.TRIANGLE_FAN);
        b.vertex(m, 0, h, 0).color(ColorUtil.withAlpha(ColorUtil.theme(0), a));
        for (int i = 0; i <= seg; i++) {
            double ang = Math.PI * 2 * i / seg + Math.toRadians(-yaw);
            int c = ColorUtil.withAlpha(ColorUtil.theme(Math.min(i, seg - i)), a);
            b.vertex(m, (float) Math.cos(ang) * rad, 0, (float) Math.sin(ang) * rad).color(c);
        }
        Render3D.draw(b);

        if (outline.get()) {
            BufferBuilder l = Render3D.buffer(VertexFormat.DrawMode.DEBUG_LINE_STRIP);
            for (int i = 0; i <= seg; i++) {
                double ang = Math.PI * 2 * i / seg;
                l.vertex(m, (float) Math.cos(ang) * rad, 0, (float) Math.sin(ang) * rad)
                        .color(ColorUtil.withAlpha(ColorUtil.theme(i), 255));
            }
            Render3D.draw(l);
        }
        ms.pop();
    }
}
