package dev.duckvisual.mixin;

import dev.duckvisual.DuckVisual;
import dev.duckvisual.module.impl.visual.GlowESP;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    /** "Minecraft* 1.21.4 - Singleplayer" -> "Duck Launcher 1.21.4 - Singleplayer" */
    @Inject(method = "getWindowTitle", at = @At("RETURN"), cancellable = true)
    private void duck$title(CallbackInfoReturnable<String> cir) {
        String t = cir.getReturnValue();
        if (t != null && t.startsWith("Minecraft")) {
            int space = t.indexOf(' ');
            cir.setReturnValue("Duck Launcher" + (space > 0 ? t.substring(space) : " 1.21.4"));
        }
    }

    @Inject(method = "hasOutline", at = @At("HEAD"), cancellable = true)
    private void duck$glow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (DuckVisual.modules() != null && DuckVisual.modules().get(GlowESP.class).shouldGlow(entity)) cir.setReturnValue(true);
    }
}
