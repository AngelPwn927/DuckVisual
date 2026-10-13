package dev.duckvisual.mixin;

import dev.duckvisual.DuckVisual;
import dev.duckvisual.gui.MenuLinkButton;
import dev.duckvisual.module.impl.misc.MainMenu;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.LogoDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.SplashTextRenderer;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.PressableTextWidget;
import net.minecraft.client.gui.widget.TextIconButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(Text title) {
        super(title);
    }

    private static MainMenu duck$menu() {
        MainMenu m = DuckVisual.modules() == null ? null : DuckVisual.modules().get(MainMenu.class);
        return m != null && m.isEnabled() ? m : null;
    }

    private static String duck$key(ButtonWidget b) {
        return b.getMessage().getContent() instanceof TranslatableTextContent t ? t.getKey() : "";
    }

    /** Centered column of glass buttons (like macOS / Plintus), small icons in the corners, link buttons. */
    @Inject(method = "init", at = @At("TAIL"))
    private void duck$layout(CallbackInfo ci) {
        MainMenu m = duck$menu();
        if (m == null) return;
        List<ButtonWidget> main = new ArrayList<>();
        ButtonWidget quit = null;
        int icons = 0;
        for (Element e : children()) {
            if (e instanceof TextIconButtonWidget icon) {
                icon.setPosition(6 + icons++ * 24, 6);
            } else if (e instanceof ButtonWidget b && !(e instanceof PressableTextWidget)) { // the copyright line stays put
                String key = duck$key(b);
                if (key.equals("menu.online")) b.visible = false; // Realms
                else if (key.equals("menu.quit")) quit = b;
                else main.add(b);
            }
        }
        main.sort(Comparator.comparingInt(ButtonWidget::getY).thenComparingInt(ButtonWidget::getX));
        int bw = 170, bh = 22, gap = 6;
        int total = main.size() * (bh + gap) + (quit != null ? bh + 4 : 0);
        int y = Math.max(70, height / 2 - total / 2 + 18);
        MainMenu.menuTop = y;
        for (ButtonWidget b : main) {
            b.setDimensionsAndPosition(bw, bh, width / 2 - bw / 2, y);
            y += bh + gap;
        }
        if (quit != null) quit.setDimensionsAndPosition(130, bh - 2, width / 2 - 65, y + 4);

        addDrawableChild(new MenuLinkButton(width - 30, 6, 24, 24, "project", null, 0xFF2AABEE, "https://t.me/duckvisual"));
        addDrawableChild(new MenuLinkButton(width - 58, 6, 24, 24, "world", null, 0, "https://duckvisual.ru"));
        addDrawableChild(new MenuLinkButton(width - 108, height - 38, 102, 20, "heart", "Поддержать", 0xFFFF5F7A, "https://t.me/duckvisual/2"));
    }

    @Inject(method = "renderPanoramaBackground", at = @At("HEAD"), cancellable = true)
    private void duck$background(DrawContext ctx, float delta, CallbackInfo ci) {
        MainMenu m = duck$menu();
        if (m != null && !m.panorama()) {
            m.background(ctx, ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight());
            ci.cancel();
        }
    }

    @Inject(method = "renderPanoramaBackground", at = @At("TAIL"))
    private void duck$glass(DrawContext ctx, float delta, CallbackInfo ci) {
        MainMenu m = duck$menu();
        if (m != null && m.panorama()) m.overlay(ctx, ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight());
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
        MainMenu m = duck$menu();
        return m != null ? m.version() : "Duck Launcher 1.21.4";
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/SplashTextRenderer;render(Lnet/minecraft/client/gui/DrawContext;ILnet/minecraft/client/font/TextRenderer;I)V"))
    private void duck$splash(SplashTextRenderer splash, DrawContext ctx, int screenWidth, TextRenderer tr, int alpha) {
        MainMenu m = duck$menu();
        if (m == null || !m.hideSplash.get()) splash.render(ctx, screenWidth, tr, alpha);
    }
}
