package dev.duckvisual.mixin;

import dev.duckvisual.DuckVisual;
import dev.duckvisual.module.impl.visual.Freelook;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Camera.class)
public class CameraMixin {
    @Redirect(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getYaw(F)F"))
    private float duck$yaw(Entity entity, float tickDelta) {
        Freelook fl = DuckVisual.modules().get(Freelook.class);
        return fl.isActive() && entity == MinecraftClient.getInstance().player ? fl.getYaw() : entity.getYaw(tickDelta);
    }

    @Redirect(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getPitch(F)F"))
    private float duck$pitch(Entity entity, float tickDelta) {
        Freelook fl = DuckVisual.modules().get(Freelook.class);
        return fl.isActive() && entity == MinecraftClient.getInstance().player ? fl.getPitch() : entity.getPitch(tickDelta);
    }
}
