package dev.duckvisual.mixin;

import dev.duckvisual.DuckVisual;
import dev.duckvisual.module.impl.world.TimeChanger;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientWorld.Properties.class)
public class WorldPropertiesMixin {
    @Inject(method = "getTimeOfDay", at = @At("HEAD"), cancellable = true)
    private void duck$time(CallbackInfoReturnable<Long> cir) {
        if (DuckVisual.modules() == null) return;
        TimeChanger tc = DuckVisual.modules().get(TimeChanger.class);
        if (tc.isEnabled()) cir.setReturnValue(tc.getTime());
    }
}
