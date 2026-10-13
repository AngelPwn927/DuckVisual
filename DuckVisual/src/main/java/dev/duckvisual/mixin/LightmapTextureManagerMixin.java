package dev.duckvisual.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.duckvisual.DuckVisual;
import dev.duckvisual.module.impl.world.Fullbright;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LightmapTextureManager.class)
public class LightmapTextureManagerMixin {
    // ordinal 0 is the darkness-effect scale, ordinal 1 is the gamma option
    @ModifyExpressionValue(method = "update", at = @At(value = "INVOKE", target = "Ljava/lang/Double;floatValue()F", ordinal = 1))
    private float duck$fullbright(float gamma) {
        Fullbright fb = DuckVisual.modules().get(Fullbright.class);
        return fb.isEnabled() ? fb.gamma.getFloat() : gamma;
    }
}
