package dev.duckvisual.mixin;

import dev.duckvisual.DuckVisual;
import dev.duckvisual.module.impl.visual.Zoom;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseMixin {
    /** While zooming the scroll wheel changes the zoom level instead of the hotbar slot. */
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void duck$zoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        Zoom zoom = DuckVisual.modules().get(Zoom.class);
        if (zoom.isEnabled() && zoom.isZooming() && net.minecraft.client.MinecraftClient.getInstance().currentScreen == null) {
            zoom.scroll(vertical);
            ci.cancel();
        }
    }
}
