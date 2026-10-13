package dev.duckvisual.module.impl.hud;

import dev.duckvisual.module.HudModule;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.util.Animation;
import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Glass;
import dev.duckvisual.util.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;

import java.util.ArrayDeque;
import java.util.Deque;

/** WASD, mouse buttons with CPS, jump and sneak keys as glass keycaps that light up when pressed. */
public class Keystrokes extends HudModule {
    private final BooleanSetting mouse = add(new BooleanSetting("Mouse", true));
    private final BooleanSetting space = add(new BooleanSetting("Space", true));
    private final BooleanSetting sneak = add(new BooleanSetting("Sneak & Sprint", true));

    private static final float K = 17, G = 2;
    private final float[] glow = new float[9];
    private final Deque<Long> left = new ArrayDeque<>(), right = new ArrayDeque<>();
    private boolean wasLeft, wasRight;

    public Keystrokes() {
        super("Keystrokes", "WASD, mouse buttons and CPS", -1, -1);
    }

    @Override
    public boolean isHidden() {
        return true;
    }

    private static int cps(Deque<Long> clicks, boolean down, boolean was) {
        long now = System.currentTimeMillis();
        if (down && !was) clicks.add(now);
        while (!clicks.isEmpty() && now - clicks.peekFirst() > 1000) clicks.pollFirst();
        return clicks.size();
    }

    @Override
    public void onRender2D(DrawContext ctx, float td) {
        var o = mc.options;
        width = K * 3 + G * 2;
        float rows = K * 2 + G;
        if (mouse.get()) rows += G + K;
        if (space.get()) rows += G + 11;
        if (sneak.get()) rows += G + 11;
        height = rows;
        if (getX() < 0 || getY() < 0) {
            x.set((double) (ctx.getScaledWindowWidth() - width - 6));
            // above the Inventory panel in the bottom-right corner
            y.set((double) (ctx.getScaledWindowHeight() - 6 - InventoryHud.HEIGHT - 4 - height));
        }
        float x0 = getX(), y0 = getY();
        boolean l = o.attackKey.isPressed(), r = o.useKey.isPressed();
        int lc = cps(left, l, wasLeft), rc = cps(right, r, wasRight);
        wasLeft = l;
        wasRight = r;

        key(ctx, 0, o.forwardKey, x0 + K + G, y0, K, K, null);
        float yy = y0 + K + G;
        key(ctx, 1, o.leftKey, x0, yy, K, K, null);
        key(ctx, 2, o.backKey, x0 + K + G, yy, K, K, null);
        key(ctx, 3, o.rightKey, x0 + (K + G) * 2, yy, K, K, null);
        yy += K + G;
        float half = (width - G) / 2;
        if (mouse.get()) {
            key(ctx, 4, o.attackKey, x0, yy, half, K, "LMB|" + lc + " CPS");
            key(ctx, 5, o.useKey, x0 + half + G, yy, half, K, "RMB|" + rc + " CPS");
            yy += K + G;
        }
        if (space.get()) {
            key(ctx, 6, o.jumpKey, x0, yy, width, 11, "Space");
            yy += 11 + G;
        }
        if (sneak.get()) {
            key(ctx, 7, o.sneakKey, x0, yy, half, 11, null);
            key(ctx, 8, o.sprintKey, x0 + half + G, yy, half, 11, null);
        }
    }

    /** "Shift" instead of the long localized "Левый Shift" so labels fit the keycaps. */
    private static String shortName(KeyBinding kb) {
        String id = kb.getBoundKeyTranslationKey();
        if (id.contains("shift")) return "Shift";
        if (id.contains("control")) return "Ctrl";
        if (id.contains("alt")) return "Alt";
        if (id.contains("space")) return "Space";
        if (id.contains("mouse.left")) return "LMB";
        if (id.contains("mouse.right")) return "RMB";
        String s = kb.getBoundKeyLocalizedText().getString();
        return s.length() <= 3 ? s.toUpperCase() : s.substring(0, 3);
    }

    private void key(DrawContext ctx, int i, KeyBinding kb, float x, float y, float w, float h, String label) {
        glow[i] = Animation.approach(glow[i], kb.isPressed() ? 1 : 0, 20);
        Glass.panel(ctx, x, y, w, h, 5, 0xB0141418, 1);
        if (glow[i] > 0.01f) Render2D.roundedRect(ctx, x, y, w, h, 5, ColorUtil.withAlpha(ColorUtil.theme(), (int) (170 * glow[i])));
        Render2D.roundedOutline(ctx, x, y, w, h, 5, 1, 0x1EFFFFFF);
        int fg = ColorUtil.lerp(0xFFF2F2F7, 0xFF1A1104, glow[i]);
        if (label == null) label = shortName(kb);
        if (label.contains("|")) {
            String[] p = label.split("\\|");
            Render2D.centeredText(ctx, p[0], x + w / 2, y + 2.5f, fg);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(x + w / 2, y + h - 6, 0);
            ctx.getMatrices().scale(0.55f, 0.55f, 1);
            Render2D.centeredText(ctx, p[1], 0, 0, ColorUtil.lerp(0xFF98989F, 0xFF1A1104, glow[i]));
            ctx.getMatrices().pop();
        } else {
            float s = Render2D.width(label) > w - 4 ? 0.7f : h < 14 ? 0.75f : 1f;
            ctx.getMatrices().push();
            ctx.getMatrices().translate(x + w / 2, y + h / 2 - 4 * s, 0);
            ctx.getMatrices().scale(s, s, 1);
            Render2D.centeredText(ctx, label, 0, 0, fg);
            ctx.getMatrices().pop();
        }
    }
}
