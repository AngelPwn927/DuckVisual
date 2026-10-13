package dev.duckvisual;

import dev.duckvisual.config.ConfigManager;
import dev.duckvisual.event.WorldRender;
import dev.duckvisual.gui.ClickGuiScreen;
import dev.duckvisual.module.Module;
import dev.duckvisual.module.ModuleManager;
import dev.duckvisual.module.impl.hud.Notifications;
import dev.duckvisual.module.impl.misc.ClickGui;
import dev.duckvisual.util.Animation;
import dev.duckvisual.util.Render3D;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.ActionResult;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

public class DuckVisual implements ClientModInitializer {
    public static final String NAME = "Duck Visual";
    public static final String VERSION = "1.0";
    public static final Logger LOGGER = LoggerFactory.getLogger("DuckVisual");

    private static ModuleManager moduleManager;
    private static ConfigManager configManager;
    private static boolean loaded;
    private final Set<Integer> heldKeys = new HashSet<>();

    public static ModuleManager modules() {
        return moduleManager;
    }

    public static ConfigManager config() {
        return configManager;
    }

    @Override
    public void onInitializeClient() {
        moduleManager = new ModuleManager();
        configManager = new ConfigManager();
        if (!configManager.load()) {
            // sensible first-launch defaults
            for (String name : new String[]{"Watermark", "ArrayList", "TargetHud", "Notifications", "Theme",
                    "JumpCircles", "TargetESP", "HitParticles", "Keybinds", "Potions",
                    "CustomFont", "MainMenu", "MinecraftUI", "TabList", "ChatTweaks", "Nametags", "CritIndicator"}) {
                Module m = moduleManager.get(name);
                if (m != null) m.setEnabledSilent(true);
            }
        }
        ClickGui gui = moduleManager.get(ClickGui.class);
        if (gui.getKey() == -1) gui.setKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
        loaded = true;

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);

        HudRenderCallback.EVENT.register((ctx, counter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.options.hudHidden || mc.player == null) return;
            float td = counter.getTickDelta(true);
            for (Module m : moduleManager.getModules()) {
                m.animation = Animation.approach(m.animation, m.isEnabled() ? 1 : 0, 10);
                if (m.isEnabled()) m.onRender2D(ctx, td);
            }
        });

        WorldRenderEvents.LAST.register(context -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null) return;
            MatrixStack ms = new MatrixStack();
            ms.multiplyPositionMatrix(context.positionMatrix());
            WorldRender render = new WorldRender(ms, context.camera(), context.tickCounter().getTickDelta(true));
            dev.duckvisual.util.Projection.capture(context.positionMatrix(), context.projectionMatrix(), context.camera().getPos());
            Render3D.begin(context.projectionMatrix());
            try {
                for (Module m : moduleManager.getModules()) {
                    if (m.isEnabled()) m.onRender3D(render);
                }
            } finally {
                Render3D.end();
            }
        });

        // BlockOverlay draws its own outline in the LAST pass, so suppress the vanilla one
        WorldRenderEvents.BLOCK_OUTLINE.register((ctx, outline) ->
                !moduleManager.get(dev.duckvisual.module.impl.visual.BlockOverlay.class).isEnabled());

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient) EventHooks.onAttack(entity);
            return ActionResult.PASS;
        });

        net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents.AFTER.register((world, player, pos, state) -> {
            var be = moduleManager.get(dev.duckvisual.module.impl.world.BreakEffects.class);
            if (be.isEnabled()) be.onBreak(pos);
        });

        // CustomFont state must be right before the first frame (title screen) — onTick only runs in a world
        dev.duckvisual.util.Render2D.customFont = moduleManager.get(dev.duckvisual.module.impl.misc.CustomFont.class).isEnabled();

        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> configManager.save());
        LOGGER.info("{} {} loaded with {} modules", NAME, VERSION, moduleManager.getModules().size());
    }

    private void onTick(MinecraftClient mc) {
        handleKeybinds(mc);
        if (mc.player == null || mc.world == null) return;
        for (Module m : moduleManager.getModules()) {
            if (m.isEnabled()) m.onTick();
        }
    }

    private void handleKeybinds(MinecraftClient mc) {
        long window = mc.getWindow().getHandle();
        boolean inGame = mc.currentScreen == null;
        for (Module m : moduleManager.getModules()) {
            int key = m.getKey();
            if (key <= 0) continue;
            boolean down = dev.duckvisual.util.Keys.isDown(window, key);
            if (down && !heldKeys.contains(key)) {
                heldKeys.add(key);
                if (inGame) {
                    if (m instanceof ClickGui) mc.setScreen(new ClickGuiScreen());
                    else m.toggle();
                }
            } else if (!down) {
                heldKeys.remove(key);
            }
        }
    }

    public static void onModuleToggled(Module module) {
        if (!loaded) return;
        Notifications n = moduleManager.get(Notifications.class);
        if (n.isEnabled() && !(module instanceof ClickGui)) {
            n.push(module.getName(), module.isEnabled() ? "enabled" : "disabled", module.isEnabled());
        }
    }
}
