package dev.duckvisual.module.impl.visual;

import dev.duckvisual.DuckVisual;
import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.module.impl.hud.Notifications;
import dev.duckvisual.setting.BooleanSetting;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvents;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Counts totems of undying popped by players (entity status 35). */
public class TotemCounter extends Module {
    private final BooleanSetting notify = add(new BooleanSetting("Notification", true));
    private final BooleanSetting chat = add(new BooleanSetting("Chat Message", false));
    private final BooleanSetting sound = add(new BooleanSetting("Sound", true));
    private final BooleanSetting self = add(new BooleanSetting("Self", true));

    private final Map<UUID, Integer> pops = new HashMap<>();

    public TotemCounter() {
        super("TotemCounter", "Counts totems popped by players", Category.VISUALS);
    }

    public int getPops(PlayerEntity p) {
        return pops.getOrDefault(p.getUuid(), 0);
    }

    /** Called from ClientPlayNetworkHandlerMixin on the main thread. */
    public void onStatus(Entity e, byte status) {
        if (!(e instanceof PlayerEntity p)) return;
        if (status == 3) { // death resets the counter
            pops.remove(p.getUuid());
            return;
        }
        if (status != 35) return;
        if (p == mc.player && !self.get()) return;
        int n = pops.merge(p.getUuid(), 1, Integer::sum);
        String name = p.getName().getString();
        if (notify.get()) {
            Notifications nt = DuckVisual.modules().get(Notifications.class);
            if (nt.isEnabled()) nt.push(name, "popped a totem (" + n + ")", false);
        }
        if (chat.get()) {
            mc.inGameHud.getChatHud().addMessage(net.minecraft.text.Text.literal("§6[Duck] §f" + name
                    + " §7popped §e" + n + "§7 totem" + (n == 1 ? "" : "s")));
        }
        if (sound.get() && p != mc.player) {
            mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 0.8f));
        }
    }

    @Override
    public void onDisable() {
        pops.clear();
    }
}
