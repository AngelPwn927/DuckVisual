package dev.duckvisual.mixin;

import dev.duckvisual.DuckVisual;
import dev.duckvisual.module.impl.visual.ItemPhysics;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public class ItemEntityRendererMixin {
    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"), cancellable = true)
    private void duck$physics(ItemEntityRenderState state, MatrixStack matrices, VertexConsumerProvider consumers, int light, CallbackInfo ci) {
        if (!DuckVisual.modules().get(ItemPhysics.class).isEnabled() || state.itemRenderState.isEmpty()) return;
        matrices.push();
        boolean block = state.itemRenderState.hasDepth();
        float scaleY = state.itemRenderState.getTransformation().scale.y();
        // a fixed per-item yaw instead of spinning
        matrices.multiply(RotationAxis.POSITIVE_Y.rotation(state.uniqueOffset));
        if (block) {
            matrices.translate(0, 0.25f * scaleY, 0);
        } else {
            matrices.translate(0, 0.02f, 0);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
        }
        ItemEntityRenderer.renderStack(matrices, consumers, light, state, Random.create(state.seed));
        matrices.pop();
        ci.cancel();
    }
}
