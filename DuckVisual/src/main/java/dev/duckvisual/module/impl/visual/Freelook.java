package dev.duckvisual.module.impl.visual;

import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.BindSetting;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.util.Keys;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

/** Hold a key to orbit the camera around yourself while you keep moving forward. */
public class Freelook extends Module {
    private final BindSetting key = add(new BindSetting("Key", GLFW.GLFW_KEY_LEFT_ALT));
    private final BooleanSetting thirdPerson = add(new BooleanSetting("Third Person", true));
    private final BooleanSetting invertPitch = add(new BooleanSetting("Invert Y", false));

    private boolean active;
    private float yaw, pitch;
    private Perspective before;

    public Freelook() {
        super("Freelook", "Hold a key to look around without turning", Category.VISUALS);
    }

    public boolean isActive() {
        return isEnabled() && active;
    }

    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }

    @Override
    public void onTick() {
        boolean down = mc.currentScreen == null && Keys.isDown(mc.getWindow().getHandle(), key.get());
        if (down && !active) {
            active = true;
            yaw = mc.player.getYaw();
            pitch = mc.player.getPitch();
            before = mc.options.getPerspective();
            if (thirdPerson.get() && before.isFirstPerson()) mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        } else if (!down && active) {
            stop();
        }
    }

    private void stop() {
        active = false;
        if (before != null) mc.options.setPerspective(before);
        before = null;
    }

    /** Mouse movement while active turns the camera instead of the player (EntityMixin). */
    public void look(double dx, double dy) {
        yaw += (float) dx * 0.15f;
        pitch = MathHelper.clamp(pitch + (float) (invertPitch.get() ? -dy : dy) * 0.15f, -90, 90);
    }

    @Override
    public void onDisable() {
        if (active) stop();
    }
}
