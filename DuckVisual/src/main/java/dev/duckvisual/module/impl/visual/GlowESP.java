package dev.duckvisual.module.impl.visual;

import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.setting.ModeSetting;
import dev.duckvisual.setting.NumberSetting;
import dev.duckvisual.util.ColorUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;

/** Vanilla-style glowing outline (seen through walls) in theme colors. */
public class GlowESP extends Module {
    private final ModeSetting color = add(new ModeSetting("Color", "Theme", "Theme", "Health", "Team"));
    private final BooleanSetting players = add(new BooleanSetting("Players", true));
    private final BooleanSetting monsters = add(new BooleanSetting("Monsters", false));
    private final BooleanSetting items = add(new BooleanSetting("Items", false));
    private final NumberSetting range = add(new NumberSetting("Range", 96, 8, 256, 1));

    public GlowESP() {
        super("GlowESP", "Glowing outline around entities", Category.VISUALS);
    }

    public boolean shouldGlow(Entity e) {
        if (!isEnabled() || mc.player == null || e == mc.player) return false;
        if (e.squaredDistanceTo(mc.player) > range.get() * range.get()) return false;
        if (e instanceof PlayerEntity) return players.get() && !e.isSpectator();
        if (e instanceof Monster) return monsters.get();
        if (e instanceof ItemEntity) return items.get();
        return false;
    }

    public int colorFor(Entity e) {
        if (color.is("Health") && e instanceof LivingEntity le) {
            return ColorUtil.lerp(0xFFFF4040, 0xFF40FF70, Math.min(1, le.getHealth() / le.getMaxHealth())) & 0xFFFFFF;
        }
        if (color.is("Team") && e.getScoreboardTeam() != null && e.getScoreboardTeam().getColor().getColorValue() != null) {
            return e.getScoreboardTeam().getColor().getColorValue();
        }
        return ColorUtil.theme(e.getId() % 20) & 0xFFFFFF;
    }
}
