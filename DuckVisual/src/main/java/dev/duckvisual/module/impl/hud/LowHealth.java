package dev.duckvisual.module.impl.hud;

import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.setting.NumberSetting;
import dev.duckvisual.util.Animation;
import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;

/** Pulsing red vignette and heartbeat when your health is low. */
public class LowHealth extends Module {
    private final NumberSetting threshold = add(new NumberSetting("Threshold", 8, 2, 16, 1));
    private final BooleanSetting heartbeat = add(new BooleanSetting("Heartbeat", true));
    private final NumberSetting strength = add(new NumberSetting("Strength", 1, 0.2, 2, 0.1));

    private float anim;
    private long lastBeat;

    public LowHealth() {
        super("LowHealth", "Red vignette when health is low", Category.HUD);
    }

    @Override
    public boolean isHidden() {
        return true;
    }

    @Override
    public void onTick() {
        float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        if (heartbeat.get() && hp <= threshold.get() && mc.player.isAlive()) {
            long interval = (long) (400 + 900 * (hp / threshold.getFloat()));
            if (System.currentTimeMillis() - lastBeat > interval) {
                lastBeat = System.currentTimeMillis();
                mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_BASEDRUM.value(), 0.6f, 0.5f));
            }
        }
    }

    @Override
    public void onRender2D(DrawContext ctx, float td) {
        float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        float danger = mc.player.isAlive() && hp <= threshold.get() ? 1 - hp / threshold.getFloat() * 0.7f : 0;
        anim = Animation.approach(anim, danger, 4);
        if (anim < 0.01f) return;
        float pulse = 0.75f + 0.25f * (float) Math.sin(System.currentTimeMillis() / (120.0 + 200 * hp / threshold.get()));
        int a = (int) Math.min(220, 170 * anim * pulse * strength.getFloat());
        int edge = ColorUtil.withAlpha(0xFFB00010, a), clear = 0x00B00010;
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        float size = Math.min(w, h) * 0.28f;
        Render2D.gradient(ctx, 0, 0, w, size, edge, edge, clear, clear);
        Render2D.gradient(ctx, 0, h - size, w, size, clear, clear, edge, edge);
        Render2D.gradient(ctx, 0, 0, size, h, edge, clear, clear, edge);
        Render2D.gradient(ctx, w - size, 0, size, h, clear, edge, edge, clear);
    }
}
