package dev.duckvisual.mixin;

import dev.duckvisual.DuckVisual;
import dev.duckvisual.module.impl.misc.ChatTweaks;
import dev.duckvisual.util.ColorUtil;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
    @Shadow @Final private List<ChatHudLine> messages;

    @Shadow protected abstract void refresh();

    @Unique private String duck$lastRaw;
    @Unique private int duck$count = 1;

    @Unique private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    @ModifyVariable(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"), argsOnly = true)
    private Text duck$decorate(Text message, Text same, MessageSignatureData signature, MessageIndicator indicator) {
        if (DuckVisual.modules() == null) return message;
        ChatTweaks ct = DuckVisual.modules().get(ChatTweaks.class);
        if (!ct.isEnabled()) return message;
        Text result = message;
        String raw = message.getString();
        if (ct.stack.get() && raw.equals(duck$lastRaw) && !messages.isEmpty()) {
            duck$count++;
            messages.remove(0);
            refresh();
            result = message.copy().append(Text.literal(" (x" + duck$count + ")")
                    .styled(s -> s.withColor(TextColor.fromRgb(ColorUtil.theme() & 0xFFFFFF))));
        } else {
            duck$count = 1;
        }
        duck$lastRaw = raw;
        if (ct.timestamps.get()) {
            MutableText ts = Text.literal("[" + LocalTime.now().format(TIME) + "] ").styled(s -> s.withColor(0x8A8A95));
            result = ts.append(result);
        }
        return result;
    }

    @ModifyConstant(method = {"addMessage(Lnet/minecraft/client/gui/hud/ChatHudLine;)V", "addVisibleMessage"},
            constant = @Constant(intValue = 100), require = 0)
    private int duck$history(int original) {
        if (DuckVisual.modules() == null) return original;
        ChatTweaks ct = DuckVisual.modules().get(ChatTweaks.class);
        return ct.isEnabled() && ct.longHistory.get() ? 1000 : original;
    }
}
