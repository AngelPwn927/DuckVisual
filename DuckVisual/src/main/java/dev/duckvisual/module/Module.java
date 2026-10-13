package dev.duckvisual.module;

import dev.duckvisual.DuckVisual;
import dev.duckvisual.event.WorldRender;
import dev.duckvisual.setting.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    protected static final MinecraftClient mc = MinecraftClient.getInstance();

    private final String name;
    private final String description;
    private final Category category;
    private final List<Setting<?>> settings = new ArrayList<>();
    private boolean enabled;
    private int key = -1;

    /** 0..1 animation used by the array list and the GUI toggles. */
    public float animation;

    protected Module(String name, String description, Category category) {
        this.name = name;
        this.description = description;
        this.category = category;
    }

    protected <T extends Setting<?>> T add(T setting) {
        settings.add(setting);
        return setting;
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        if (enabled) onEnable(); else onDisable();
        DuckVisual.onModuleToggled(this);
    }

    /** Sets the state without firing notifications (used by the config loader). */
    public void setEnabledSilent(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        if (enabled) onEnable(); else onDisable();
    }

    public boolean isEnabled() { return enabled; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Category getCategory() { return category; }
    public List<Setting<?>> getSettings() { return settings; }
    public int getKey() { return key; }
    public void setKey(int key) { this.key = key; }

    /** Extra text shown next to the module name in the array list. */
    public String getSuffix() { return null; }

    /** Modules that should not be listed in the array list. */
    public boolean isHidden() { return false; }

    public void onEnable() {}
    public void onDisable() {}
    public void onTick() {}
    public void onRender2D(DrawContext ctx, float tickDelta) {}
    public void onRender3D(WorldRender render) {}
}
