package dev.duckvisual.mixin;

import dev.duckvisual.DuckVisual;
import dev.duckvisual.module.impl.visual.SwingAnimation;
import dev.duckvisual.module.impl.visual.ViewModel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MaceItem;
import net.minecraft.item.TridentItem;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {
    @Inject(method = "applyEquipOffset", at = @At("TAIL"))
    private void duck$viewModel(MatrixStack matrices, Arm arm, float equipProgress, CallbackInfo ci) {
        ViewModel vm = DuckVisual.modules().get(ViewModel.class);
        if (!vm.isEnabled()) return;
        boolean main = arm == MinecraftClient.getInstance().options.getMainArm().getValue();
        int side = arm == Arm.RIGHT ? 1 : -1;
        if (main) matrices.translate(vm.mainX.getFloat() * side, vm.mainY.getFloat(), vm.mainZ.getFloat());
        else matrices.translate(vm.offX.getFloat() * side, vm.offY.getFloat(), vm.offZ.getFloat());
        float s = vm.scale.getFloat();
        matrices.scale(s, s, s);
    }

    @Inject(method = "applySwingOffset", at = @At("HEAD"), cancellable = true)
    private void duck$swing(MatrixStack matrices, Arm arm, float swingProgress, CallbackInfo ci) {
        SwingAnimation sa = DuckVisual.modules().get(SwingAnimation.class);
        if (!sa.isEnabled()) return;
        var player = MinecraftClient.getInstance().player;
        if (player == null) return;
        if (sa.onlySword.get()) {
            ItemStack stack = player.getMainHandStack();
            boolean weapon = stack.isIn(ItemTags.SWORDS) || stack.getItem() instanceof AxeItem
                    || stack.getItem() instanceof MaceItem || stack.getItem() instanceof TridentItem;
            if (!weapon) return;
        }
        int side = arm == Arm.RIGHT ? 1 : -1;
        float sin = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
        float sqrt = MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
        switch (sa.mode.get()) {
            case "Block" -> {
                matrices.translate(side * -0.1f, 0.1f, 0);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 45));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * -20 - sqrt * side * 20));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-sqrt * 60 - 20));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * -45));
            }
            case "Slide" -> {
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * (45 + sin * -10)));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sqrt * -50));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * -45));
            }
            case "Spin" -> {
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 45));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(swingProgress * -360));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * -45));
            }
            case "Punch" -> {
                matrices.translate(0, 0, -sqrt * 0.3f);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 45));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sqrt * -15));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * -45));
            }
            case "Down" -> {
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 45));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sqrt * -90));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * -45));
            }
            default -> { // Smooth
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * (45 + sin * -15)));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * sqrt * -15));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sqrt * -60));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * -45));
            }
        }
        ci.cancel();
    }
}
