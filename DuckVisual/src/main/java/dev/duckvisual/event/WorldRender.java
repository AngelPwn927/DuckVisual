package dev.duckvisual.event;

import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

/**
 * 3D render pass data. The matrix stack only contains the camera rotation, so all
 * positions must be passed camera-relative (see {@link #rel(Vec3d)}) to keep float precision.
 */
public record WorldRender(MatrixStack matrices, Camera camera, float tickDelta) {
    public Vec3d rel(Vec3d world) {
        return world.subtract(camera.getPos());
    }

    public Vec3d rel(double x, double y, double z) {
        Vec3d c = camera.getPos();
        return new Vec3d(x - c.x, y - c.y, z - c.z);
    }
}
