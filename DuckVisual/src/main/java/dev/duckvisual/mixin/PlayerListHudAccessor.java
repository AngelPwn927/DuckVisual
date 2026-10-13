package dev.duckvisual.mixin;

import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(PlayerListHud.class)
public interface PlayerListHudAccessor {
    @Invoker("collectPlayerEntries")
    List<PlayerListEntry> duck$collect();

    @Accessor("header")
    Text duck$header();

    @Accessor("footer")
    Text duck$footer();
}
