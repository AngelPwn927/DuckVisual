package dev.duckvisual.module.impl.visual;

import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.setting.ModeSetting;
import dev.duckvisual.setting.NumberSetting;
import dev.duckvisual.util.Animation;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

/** OptiFine-style zoom: hold the key, scroll to change the zoom level. */
public class Zoom extends Module {
    private final ModeSetting key = add(new ModeSetting("Key", "C", "C", "V", "Z", "X", "Left Alt", "Mouse 4"));
    private final NumberSetting factor = add(new NumberSetting("Factor", 4, 1.5, 15, 0.5));
    private final BooleanSetting scroll = add(new BooleanSetting("Scroll Adjust", true));
    private final BooleanSetting smoothCamera = add(new BooleanSetting("Cinematic", false));
    private final NumberSetting speed = add(new NumberSetting("Speed", 12, 3, 30, 1));

    private float current = 1;
    private float scrollFactor = -1;
    private boolean zooming;
    private boolean smoothBefore;

    public Zoom() {
        super("Zoom", "Hold a key to zoom in, scroll to adjust", Category.VISUALS);
    }

    @Override
    public String getSuffix() {
        return key.get();
    }

    public boolean isZooming() {
        return zooming;
    }

    private boolean keyDown() {
        long w = mc.getWindow().getHandle();
        return switch (key.get()) {
            case "V" -> InputUtil.isKeyPressed(w, GLFW.GLFW_KEY_V);
            case "Z" -> InputUtil.isKeyPressed(w, GLFW.GLFW_KEY_Z);
            case "X" -> InputUtil.isKeyPressed(w, GLFW.GLFW_KEY_X);
            case "Left Alt" -> InputUtil.isKeyPressed(w, GLFW.GLFW_KEY_LEFT_ALT);
            case "Mouse 4" -> GLFW.glfwGetMouseButton(w, GLFW.GLFW_MOUSE_BUTTON_4) == GLFW.GLFW_PRESS;
            default -> InputUtil.isKeyPressed(w, GLFW.GLFW_KEY_C);
        };
    }

    @Override
    public void onTick() {
        boolean down = mc.currentScreen == null && keyDown();
        if (down && !zooming) {
            scrollFactor = factor.getFloat();
            if (smoothCamera.get()) {
                smoothBefore = mc.options.smoothCameraEnabled;
                mc.options.smoothCameraEnabled = true;
            }
        } else if (!down && zooming && smoothCamera.get()) {
            mc.options.smoothCameraEnabled = smoothBefore;
        }
        zooming = down;
    }

    public void scroll(double amount) {
        if (!scroll.get()) return;
        scrollFactor = MathHelper.clamp(scrollFactor * (amount > 0 ? 1.2f : 1 / 1.2f), 1.2f, 50f);
    }

    /** Called from GameRenderer#getFov every frame. */
    public float apply(float fov) {
        float target = zooming ? (scrollFactor > 0 ? scrollFactor : factor.getFloat()) : 1;
        current = Animation.approach(current, target, speed.getFloat());
        return fov / current;
    }

    @Override
    public void onDisable() {
        if (zooming && smoothCamera.get()) mc.options.smoothCameraEnabled = smoothBefore;
        zooming = false;
        current = 1;
    }
}
