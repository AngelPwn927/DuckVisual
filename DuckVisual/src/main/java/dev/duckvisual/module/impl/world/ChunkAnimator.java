package dev.duckvisual.module.impl.world;

import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.ModeSetting;
import dev.duckvisual.setting.NumberSetting;
import dev.duckvisual.util.Animation;
import net.minecraft.client.render.chunk.ChunkBuilder;

import java.util.IdentityHashMap;
import java.util.Map;

/** Newly loaded chunk sections slide into place (WorldRendererMixin offsets the ChunkOffset uniform). */
public class ChunkAnimator extends Module {
    private final ModeSetting mode = add(new ModeSetting("Mode", "Rise", "Rise", "Drop"));
    private final NumberSetting duration = add(new NumberSetting("Duration", 0.8, 0.2, 3, 0.1));
    private final NumberSetting distance = add(new NumberSetting("Distance", 24, 4, 64, 1));

    /** Built chunk objects are pooled and re-used for other positions, so key on (chunk, origin). */
    private final Map<ChunkBuilder.BuiltChunk, long[]> seen = new IdentityHashMap<>();

    /** Sodium has its own chunk renderer, the vanilla hook we use is never called there. */
    public static final boolean SODIUM = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium");

    public ChunkAnimator() {
        super("ChunkAnimator", SODIUM ? "Not available with Sodium" : "Chunks slide in when they load", Category.WORLD);
    }

    @Override
    public String getSuffix() {
        return SODIUM ? "Sodium" : mode.get();
    }

    /** @return vertical offset in blocks for this section right now. */
    public float offset(ChunkBuilder.BuiltChunk chunk) {
        long origin = chunk.getOrigin().asLong();
        long now = System.currentTimeMillis();
        long[] entry = seen.get(chunk);
        if (entry == null || entry[0] != origin) {
            entry = new long[]{origin, now};
            seen.put(chunk, entry);
        }
        float t = (now - entry[1]) / (float) (duration.get() * 1000);
        if (t >= 1) return 0;
        float d = (1 - Animation.easeOutCubic(t)) * distance.getFloat();
        return mode.is("Drop") ? d : -d;
    }

    @Override
    public void onDisable() {
        seen.clear();
    }
}
