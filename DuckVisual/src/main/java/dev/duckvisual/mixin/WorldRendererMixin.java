package dev.duckvisual.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.duckvisual.DuckVisual;
import dev.duckvisual.module.impl.world.ChunkAnimator;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
    /** Shifts each chunk section's ChunkOffset uniform while it is animating in. */
    // Sodium replaces renderLayer entirely, so this is optional (require = 0) and simply does nothing there
    @WrapOperation(method = "renderLayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/GlUniform;set(FFF)V", ordinal = 0), require = 0)
    private void duck$chunkAnim(GlUniform uniform, float x, float y, float z, Operation<Void> original,
                                @Local ChunkBuilder.BuiltChunk chunk) {
        ChunkAnimator ca = DuckVisual.modules().get(ChunkAnimator.class);
        if (ca.isEnabled()) y += ca.offset(chunk);
        original.call(uniform, x, y, z);
    }
}
