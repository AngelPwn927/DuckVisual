package dev.duckvisual.util;

import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** Key codes: GLFW keyboard codes, or {@link #MOUSE} + button index for mouse buttons. -1 = none. */
public final class Keys {
    private Keys() {}

    public static final int MOUSE = 1000;

    public static String name(int key) {
        if (key <= 0) return "None";
        if (key >= MOUSE) {
            int b = key - MOUSE;
            return b == 2 ? "MMB" : "Mouse " + (b + 1);
        }
        String s = InputUtil.Type.KEYSYM.createFromCode(key).getLocalizedText().getString();
        return s.length() > 10 ? s.substring(0, 10) : s;
    }

    public static boolean isDown(long window, int key) {
        if (key <= 0) return false;
        if (key >= MOUSE) return GLFW.glfwGetMouseButton(window, key - MOUSE) == GLFW.GLFW_PRESS;
        return InputUtil.isKeyPressed(window, key);
    }
}
