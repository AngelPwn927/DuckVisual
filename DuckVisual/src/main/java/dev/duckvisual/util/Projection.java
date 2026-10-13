package dev.duckvisual.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** World → GUI-space projection using the matrices captured in the last world render pass. */
public final class Projection {
    private Projection() {}

    private static final Matrix4f viewProj = new Matrix4f();
    private static Vec3d camera = Vec3d.ZERO;

    public static void capture(Matrix4f view, Matrix4f projection, Vec3d cameraPos) {
        viewProj.set(projection).mul(view);
        camera = cameraPos;
    }

    /** @return {x, y} in scaled GUI pixels, or null when the point is behind the camera. */
    public static float[] toScreen(Vec3d world) {
        Vector4f v = new Vector4f((float) (world.x - camera.x), (float) (world.y - camera.y), (float) (world.z - camera.z), 1);
        viewProj.transform(v);
        if (v.w <= 0.05f) return null;
        float nx = v.x / v.w, ny = v.y / v.w;
        var w = MinecraftClient.getInstance().getWindow();
        return new float[]{(nx + 1) / 2f * w.getScaledWidth(), (1 - ny) / 2f * w.getScaledHeight()};
    }

    public static Vec3d camera() {
        return camera;
    }
}
