package dev.duckvisual.mixin;

import dev.duckvisual.DuckVisual;
import dev.duckvisual.command.Commands;
import dev.duckvisual.module.impl.visual.TotemCounter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {
    /** Messages starting with the prefix are Duck Visual commands and never reach the server. */
    @Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true)
    private void duck$commands(String message, CallbackInfo ci) {
        if (message.startsWith(Commands.PREFIX)) {
            Commands.execute(message.substring(Commands.PREFIX.length()));
            ci.cancel();
        }
    }

    @Inject(method = "onEntityStatus", at = @At("HEAD"))
    private void duck$status(EntityStatusS2CPacket packet, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        // the handler first runs on the network thread and re-schedules itself; only act on the main thread
        if (!mc.isOnThread() || mc.world == null) return;
        TotemCounter tc = DuckVisual.modules().get(TotemCounter.class);
        if (!tc.isEnabled()) return;
        Entity e = packet.getEntity(mc.world);
        if (e != null) tc.onStatus(e, packet.getStatus());
    }
}
