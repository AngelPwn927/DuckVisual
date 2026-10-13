package dev.duckvisual.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Skips mixins that would collide with other mods that replace the same vanilla code. */
public class DuckMixinPlugin implements IMixinConfigPlugin {
    private static final boolean SODIUM = FabricLoader.getInstance().isModLoaded("sodium");

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // Sodium @Overwrites WorldRenderer#renderLayer (its own chunk renderer), so ChunkAnimator's hook can't exist
        if (mixinClassName.endsWith(".WorldRendererMixin")) return !SODIUM;
        return true;
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
