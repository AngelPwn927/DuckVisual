package dev.duckvisual.mixin;

import dev.duckvisual.module.impl.misc.MinecraftUI;
import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Render2D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SliderWidget.class)
public abstract class SliderWidgetMixin extends ClickableWidget {
    @Shadow protected double value;

    public SliderWidgetMixin(int x, int y, int width, int height, Text message) {
        super(x, y, width, height, message);
    }

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void duck$slider(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!MinecraftUI.slidersActive()) return;
        ci.cancel();
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        int a = (int) (255 * alpha);
        if (a < 8) return;
        boolean hov = isHovered() || isFocused();
        float r = Math.min(5, h / 3f);
        Render2D.roundedRect(ctx, x, y, w, h, r, ColorUtil.withAlpha(0xFF15151B, (int) (225 * alpha)));
        float fill = (float) (w * value);
        if (fill > 2) {
            Render2D.roundedGradient(ctx, x, y, Math.max(2 * r, fill), h, r,
                    ColorUtil.withAlpha(ColorUtil.theme(0), (int) ((hov ? 90 : 60) * alpha)), ColorUtil.withAlpha(ColorUtil.theme(8), (int) ((hov ? 90 : 60) * alpha)),
                    ColorUtil.withAlpha(ColorUtil.theme(8), (int) ((hov ? 90 : 60) * alpha)), ColorUtil.withAlpha(ColorUtil.theme(0), (int) ((hov ? 90 : 60) * alpha)));
        }
        // knob
        float kx = Math.max(x + 2, Math.min(x + w - 6, x + fill - 2));
        Render2D.roundedRect(ctx, kx, y + 2, 4, h - 4, 2, ColorUtil.withAlpha(hov ? 0xFFFFFFFF : ColorUtil.theme(), a));
        int color = (active ? 0xFFFFFF : 0x6A6A75) | (a << 24);
        drawScrollableText(ctx, MinecraftClient.getInstance().textRenderer, 2, color);
    }
}
