package dev.duckvisual.mixin;

import dev.duckvisual.DuckVisual;
import dev.duckvisual.module.impl.world.WorldColor;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientWorld.class)
public class ClientWorldMixin {
    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void duck$sky(Vec3d cameraPos, float tickDelta, CallbackInfoReturnable<Integer> cir) {
        WorldColor wc = DuckVisual.modules().get(WorldColor.class);
        if (wc.isEnabled() && wc.sky.get()) cir.setReturnValue(wc.tint(cir.getReturnValue()));
    }

    @Inject(method = "getCloudsColor", at = @At("RETURN"), cancellable = true)
    private void duck$clouds(float tickDelta, CallbackInfoReturnable<Integer> cir) {
        WorldColor wc = DuckVisual.modules().get(WorldColor.class);
        if (wc.isEnabled() && wc.clouds.get()) cir.setReturnValue(wc.tint(cir.getReturnValue()));
    }
}
