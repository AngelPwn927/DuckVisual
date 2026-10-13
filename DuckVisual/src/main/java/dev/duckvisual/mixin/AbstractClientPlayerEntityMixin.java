package dev.duckvisual.mixin;

import dev.duckvisual.cosmetics.CosmeticsManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {
    @Inject(method = "getSkinTextures", at = @At("RETURN"), cancellable = true)
    private void duck$cosmetics(CallbackInfoReturnable<SkinTextures> cir) {
        AbstractClientPlayerEntity self = (AbstractClientPlayerEntity) (Object) this;
        boolean own = self == MinecraftClient.getInstance().player;
        SkinTextures out = CosmeticsManager.apply(self.getGameProfile().getName(), own, cir.getReturnValue());
        if (out != cir.getReturnValue()) cir.setReturnValue(out);
    }
}
