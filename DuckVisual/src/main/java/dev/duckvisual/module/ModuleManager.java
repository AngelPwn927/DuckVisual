package dev.duckvisual.module;

import dev.duckvisual.module.impl.hud.*;
import dev.duckvisual.module.impl.misc.*;
import dev.duckvisual.module.impl.visual.*;
import dev.duckvisual.module.impl.world.*;

import java.util.ArrayList;
import java.util.List;

public class ModuleManager {
    private final List<Module> modules = new ArrayList<>();

    public ModuleManager() {
        // Visuals
        add(new ChinaHat());
        add(new JumpCircles());
        add(new Trails());
        add(new TargetESP());
        add(new HitParticles());
        add(new HitColor());
        add(new Arrows());
        add(new Crosshair());
        add(new ViewModel());
        add(new SwingAnimation());
        add(new AspectRatio());
        add(new NoRender());
        add(new Zoom());
        add(new BlockOverlay());
        add(new BoxESP());
        add(new Tracers());
        add(new HitSounds());
        add(new KillEffects());
        add(new HitBoxes());
        add(new CustomModels());
        add(new Trajectories());
        add(new DamageIndicators());
        add(new CritIndicator());
        add(new HealthTags());
        add(new Freelook());
        add(new GlowESP());
        add(new Nametags());
        add(new TotemCounter());
        add(new ItemPhysics());
        add(new MotionBlur());
        add(new HandTint());
        add(new Hitmarker());
        add(new TargetCircle());
        // Hud
        add(new Watermark());
        add(new ArrayListHud());
        add(new Keybinds());
        add(new Potions());
        add(new TargetHud());
        add(new Information());
        add(new Notifications());
        add(new ArmorHud());
        add(new Cooldowns());
        add(new CustomHotbar());
        add(new LowHealth());
        add(new CombatInfo());
        add(new TabList());
        add(new ScoreboardHud());
        add(new Music());
        // World
        add(new Fullbright());
        add(new TimeChanger());
        add(new WorldParticles());
        add(new WorldColor());
        add(new CustomSky());
        add(new BreakEffects());
        add(new ChunkAnimator());
        // Misc
        add(new Theme());
        add(new ClickGui());
        add(new CustomFont());
        add(new MainMenu());
        add(new ChatTweaks());
        add(new MinecraftUI());
    }

    private void add(Module module) {
        modules.add(module);
    }

    public List<Module> getModules() {
        return modules;
    }

    public List<Module> getModules(Category category) {
        return modules.stream().filter(m -> m.getCategory() == category).toList();
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T get(Class<T> clazz) {
        for (Module m : modules) if (m.getClass() == clazz) return (T) m;
        throw new IllegalArgumentException(clazz.getName());
    }

    public Module get(String name) {
        for (Module m : modules) if (m.getName().equalsIgnoreCase(name)) return m;
        return null;
    }
}
