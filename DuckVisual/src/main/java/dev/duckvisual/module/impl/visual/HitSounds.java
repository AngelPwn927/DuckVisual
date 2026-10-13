package dev.duckvisual.module.impl.visual;

import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.ModeSetting;
import dev.duckvisual.setting.NumberSetting;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;

public class HitSounds extends Module {
    private final ModeSetting sound = add(new ModeSetting("Sound", "Bell", "Bell", "Pling", "Orb", "Chime", "Arrow"));
    private final NumberSetting volume = add(new NumberSetting("Volume", 0.8, 0.1, 2, 0.1));
    private final NumberSetting pitch = add(new NumberSetting("Pitch", 1.2, 0.5, 2, 0.05));

    public HitSounds() {
        super("HitSounds", "Plays a sound when you hit something", Category.VISUALS);
    }

    @Override
    public String getSuffix() {
        return sound.get();
    }

    public void play() {
        float p = pitch.getFloat(), v = volume.getFloat();
        PositionedSoundInstance s = switch (sound.get()) {
            case "Pling" -> PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), p, v);
            case "Orb" -> PositionedSoundInstance.master(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, p, v);
            case "Chime" -> PositionedSoundInstance.master(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, p, v);
            case "Arrow" -> PositionedSoundInstance.master(SoundEvents.ENTITY_ARROW_HIT_PLAYER, p, v);
            default -> PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), p, v);
        };
        mc.getSoundManager().play(s);
    }
}
