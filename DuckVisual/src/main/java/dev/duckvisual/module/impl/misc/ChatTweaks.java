package dev.duckvisual.module.impl.misc;

import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.BooleanSetting;

/** Chat timestamps, longer history and stacking of repeated messages (ChatHudMixin). */
public class ChatTweaks extends Module {
    public final BooleanSetting timestamps = add(new BooleanSetting("Timestamps", true));
    public final BooleanSetting stack = add(new BooleanSetting("Stack Duplicates", true));
    public final BooleanSetting longHistory = add(new BooleanSetting("Long History", true));

    public ChatTweaks() {
        super("ChatTweaks", "Timestamps, long history, stacked duplicates", Category.MISC);
    }

    @Override
    public boolean isHidden() {
        return true;
    }
}
