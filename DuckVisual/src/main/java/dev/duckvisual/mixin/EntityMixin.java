package dev.duckvisual.mixin;

import dev.duckvisual.DuckVisual;
import dev.duckvisual.module.impl.visual.Freelook;
import dev.duckvisual.module.impl.visual.GlowESP;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityMixin {
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void duck$freelook(double dx, double dy, CallbackInfo ci) {
        if ((Object) this != MinecraftClient.getInstance().player) return;
        Freelook fl = DuckVisual.modules().get(Freelook.class);
        if (fl.isActive()) {
            fl.look(dx, dy);
            ci.cancel();
        }
    }

    @Inject(method = "getTeamColorValue", at = @At("HEAD"), cancellable = true)
    private void duck$glowColor(CallbackInfoReturnable<Integer> cir) {
        GlowESP glow = DuckVisual.modules().get(GlowESP.class);
        if (glow.isEnabled() && glow.shouldGlow((Entity) (Object) this)) cir.setReturnValue(glow.colorFor((Entity) (Object) this));
    }
}
