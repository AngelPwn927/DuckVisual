package dev.duckvisual.mixin;

import dev.duckvisual.DuckVisual;
import dev.duckvisual.module.impl.misc.MainMenu;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.LogoDrawer;
import net.minecraft.client.gui.screen.SplashTextRenderer;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {
    private static MainMenu duck$menu() {
        MainMenu m = DuckVisual.modules() == null ? null : DuckVisual.modules().get(MainMenu.class);
        return m != null && m.isEnabled() ? m : null;
    }

    @Inject(method = "renderPanoramaBackground", at = @At("HEAD"), cancellable = true)
    private void duck$background(DrawContext ctx, float delta, CallbackInfo ci) {
        MainMenu m = duck$menu();
        if (m != null) {
            m.background(ctx, ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight());
            ci.cancel();
        }
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/LogoDrawer;draw(Lnet/minecraft/client/gui/DrawContext;IF)V"))
    private void duck$logo(LogoDrawer drawer, DrawContext ctx, int screenWidth, float alpha) {
        MainMenu m = duck$menu();
        if (m != null) m.logo(ctx, screenWidth, alpha);
        else drawer.draw(ctx, screenWidth, alpha);
    }

    /** Short version line (the long vanilla one overlapped the copyright text with the UI font). */
    @org.spongepowered.asm.mixin.injection.ModifyArg(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;III)I"), index = 1)
    private String duck$versionText(String original) {
        return "Duck Launcher 1.21.4";
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/SplashTextRenderer;render(Lnet/minecraft/client/gui/DrawContext;ILnet/minecraft/client/font/TextRenderer;I)V"))
    private void duck$splash(SplashTextRenderer splash, DrawContext ctx, int screenWidth, TextRenderer tr, int alpha) {
        MainMenu m = duck$menu();
        if (m == null || !m.hideSplash.get()) splash.render(ctx, screenWidth, tr, alpha);
    }
}
