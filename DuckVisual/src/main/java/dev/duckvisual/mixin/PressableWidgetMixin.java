package dev.duckvisual.mixin;

import dev.duckvisual.gui.Icons;
import dev.duckvisual.module.impl.misc.MinecraftUI;
import dev.duckvisual.util.Animation;
import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Glass;
import dev.duckvisual.util.Render2D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** macOS-style buttons for every Minecraft screen: frosted glass, hairline border, soft hover, icons on menu buttons. */
@Mixin(PressableWidget.class)
public abstract class PressableWidgetMixin extends ClickableWidget {
    @Unique private float duck$hover;

    public PressableWidgetMixin(int x, int y, int width, int height, Text message) {
        super(x, y, width, height, message);
    }

    @Unique
    private String duck$icon() {
        if (!(getMessage().getContent() instanceof TranslatableTextContent t)) return null;
        return switch (t.getKey()) {
            case "menu.singleplayer" -> "user";
            case "menu.multiplayer" -> "users";
            case "menu.options", "menu.options.title" -> "gear";
            case "menu.quit" -> "exit";
            case "menu.online" -> "world";
            case "menu.returnToMenu", "menu.disconnect" -> "exit";
            case "menu.returnToGame" -> "project";
            default -> null;
        };
    }

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void duck$button(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!MinecraftUI.buttonsActive()) return;
        ci.cancel();
        boolean hov = active && (isHovered() || isFocused());
        duck$hover = Animation.approach(duck$hover, hov ? 1 : 0, 14);
        float h = duck$hover;
        int x = getX(), y = getY(), w = getWidth(), ht = getHeight();
        int a = (int) (255 * alpha);
        if (a < 8) return;
        float r = Math.min(7, ht / 2.8f);
        Render2D.shadow(ctx, x, y + 1, w, ht, r, 5, ColorUtil.withAlpha(0xFF000000, (int) (70 * alpha)));
        Glass.panel(ctx, x, y, w, ht, r, active ? 0x9E141418 : 0xC80C0C10, alpha);
        if (h > 0.01f) Render2D.roundedRect(ctx, x, y, w, ht, r, ColorUtil.withAlpha(0xFFFFFFFF, (int) (24 * h * alpha)));
        Render2D.roundedOutline(ctx, x, y, w, ht, r, 1, ColorUtil.withAlpha(ColorUtil.lerp(0xFFFFFFFF, ColorUtil.theme(), h), (int) ((34 + 120 * h) * alpha)));
        int textColor = active ? ColorUtil.lerp(0xFFE6E6EC, 0xFFFFFFFF, h) : 0xFF6A6A75;
        // icon only when it fits left of the centered label
        String icon = (w - MinecraftClient.getInstance().textRenderer.getWidth(getMessage())) / 2 >= 24 ? duck$icon() : null;
        if (icon != null) Icons.draw(ctx, icon, x + 9, y + ht / 2f - 4.5f, 9, ColorUtil.withAlpha(ColorUtil.lerp(0xFFB0B0BA, ColorUtil.theme(), h), a));
        ((PressableWidget) (Object) this).drawMessage(ctx, MinecraftClient.getInstance().textRenderer, textColor & 0xFFFFFF | (a << 24));
    }
}
