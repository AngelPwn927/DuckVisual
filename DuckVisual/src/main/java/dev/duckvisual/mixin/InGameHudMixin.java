package dev.duckvisual.mixin;

import dev.duckvisual.DuckVisual;
import dev.duckvisual.module.impl.hud.Potions;
import dev.duckvisual.module.impl.visual.Crosshair;
import dev.duckvisual.module.impl.visual.NoRender;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void duck$crosshair(DrawContext ctx, RenderTickCounter counter, CallbackInfo ci) {
        if (DuckVisual.modules().get(Crosshair.class).isEnabled()) ci.cancel();
    }

    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
    private void duck$hotbar(DrawContext ctx, RenderTickCounter counter, CallbackInfo ci) {
        if (DuckVisual.modules().get(dev.duckvisual.module.impl.hud.CustomHotbar.class).replacesVanilla()) ci.cancel();
    }

    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
    private void duck$effects(DrawContext ctx, RenderTickCounter counter, CallbackInfo ci) {
        if (NoRender.active(nr -> nr.vanillaEffects) || DuckVisual.modules().get(Potions.class).isEnabled()) ci.cancel();
    }

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At("HEAD"), cancellable = true)
    private void duck$scoreboard(DrawContext ctx, ScoreboardObjective objective, CallbackInfo ci) {
        if (NoRender.active(nr -> nr.scoreboard)) {
            ci.cancel();
            return;
        }
        var hud = DuckVisual.modules().get(dev.duckvisual.module.impl.hud.ScoreboardHud.class);
        if (hud.isEnabled()) {
            hud.render(ctx, objective);
            ci.cancel();
        }
    }

    @Inject(method = "renderOverlay", at = @At("HEAD"), cancellable = true)
    private void duck$pumpkin(DrawContext ctx, Identifier texture, float opacity, CallbackInfo ci) {
        if (NoRender.active(nr -> nr.pumpkin) && texture.getPath().contains("pumpkin")) ci.cancel();
    }

    @Inject(method = "renderNauseaOverlay", at = @At("HEAD"), cancellable = true)
    private void duck$nausea(DrawContext ctx, float strength, CallbackInfo ci) {
        if (NoRender.active(nr -> nr.nausea)) ci.cancel();
    }

    @Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void duck$portal(DrawContext ctx, float strength, CallbackInfo ci) {
        if (NoRender.active(nr -> nr.nausea)) ci.cancel();
    }
}
