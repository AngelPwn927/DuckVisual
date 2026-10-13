package dev.duckvisual.module.impl.world;

import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.setting.NumberSetting;

/** Client-side time of day. Applied in WorldPropertiesMixin. */
public class TimeChanger extends Module {
    public final NumberSetting time = add(new NumberSetting("Time", 18000, 0, 24000, 100));
    public final BooleanSetting cycle = add(new BooleanSetting("Cycle", false));
    public final NumberSetting cycleSpeed = add(new NumberSetting("Cycle Speed", 50, 1, 500, 1).visibleWhen(cycle::get));

    private long cycled;

    public TimeChanger() {
        super("TimeChanger", "Changes the time of day client-side", Category.WORLD);
    }

    @Override
    public void onTick() {
        cycled = (cycled + cycleSpeed.getInt()) % 24000;
    }

    public long getTime() {
        return cycle.get() ? cycled : time.get().longValue();
    }

    @Override
    public String getSuffix() {
        return cycle.get() ? "Cycle" : String.valueOf(time.getInt());
    }
}
