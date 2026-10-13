package dev.duckvisual.mixin;

import dev.duckvisual.DuckVisual;
import dev.duckvisual.module.impl.world.WorldColor;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BackgroundRenderer.class)
public class WorldColorMixin {
    @Inject(method = "getFogColor", at = @At("RETURN"))
    private static void duck$fogColor(Camera camera, float tickDelta, ClientWorld world, int viewDistance, float skyDarkness,
                                      CallbackInfoReturnable<Vector4f> cir) {
        WorldColor wc = DuckVisual.modules().get(WorldColor.class);
        if (wc.isEnabled() && wc.fog.get()) wc.tint(cir.getReturnValue());
    }
}
