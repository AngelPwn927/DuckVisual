package dev.duckvisual.mixin;

import dev.duckvisual.module.impl.misc.MinecraftUI;
import dev.duckvisual.util.Render2D;
import net.minecraft.client.font.FontStorage;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Style;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

@Mixin(TextRenderer.class)
public class TextRendererMixin {
    @Shadow @Final private Function<Identifier, FontStorage> fontStorageAccessor;

    /** Text in the default font is drawn with Montserrat while MinecraftUI is on. */
    @Inject(method = "getFontStorage", at = @At("HEAD"), cancellable = true)
    private void duck$uiFont(Identifier id, CallbackInfoReturnable<FontStorage> cir) {
        if (Style.DEFAULT_FONT_ID.equals(id) && MinecraftUI.fontActive()) {
            cir.setReturnValue(fontStorageAccessor.apply(Render2D.FONT));
        }
    }
}
