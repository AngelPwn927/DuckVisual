package dev.duckvisual.mixin;

import dev.duckvisual.module.impl.misc.MinecraftUI;
import dev.duckvisual.util.Animation;
import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Render2D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PressableWidget.class)
public abstract class PressableWidgetMixin extends ClickableWidget {
    @Unique private float duck$hover;

    public PressableWidgetMixin(int x, int y, int width, int height, Text message) {
        super(x, y, width, height, message);
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
        float r = Math.min(5, ht / 3f);
        if (h > 0.01f) Render2D.shadow(ctx, x, y, w, ht, r, 4, ColorUtil.withAlpha(ColorUtil.theme(), (int) (90 * h * alpha)));
        int base = active ? 0xFF15151B : 0xFF0E0E12;
        Render2D.roundedRect(ctx, x, y, w, ht, r, ColorUtil.withAlpha(base, (int) (225 * alpha)));
        if (h > 0.01f) {
            Render2D.roundedGradient(ctx, x, y, w, ht, r,
                    ColorUtil.withAlpha(ColorUtil.theme(0), (int) (55 * h * alpha)), ColorUtil.withAlpha(ColorUtil.theme(8), (int) (55 * h * alpha)),
                    ColorUtil.withAlpha(ColorUtil.theme(8), (int) (55 * h * alpha)), ColorUtil.withAlpha(ColorUtil.theme(0), (int) (55 * h * alpha)));
        }
        // theme accent line at the bottom, grows on hover
        float lw = (w - 2 * r) * (0.25f + 0.75f * h);
        if (active) Render2D.roundedRect(ctx, x + w / 2f - lw / 2, y + ht - 1.5f, lw, 1.5f, 0.75f,
                ColorUtil.withAlpha(ColorUtil.theme(), (int) ((120 + 135 * h) * alpha)));
        int textColor = active ? ColorUtil.lerp(0xFFE6E6EC, 0xFFFFFFFF, h) : 0xFF6A6A75;
        ((PressableWidget) (Object) this).drawMessage(ctx, MinecraftClient.getInstance().textRenderer, textColor & 0xFFFFFF | (a << 24));
    }
}
