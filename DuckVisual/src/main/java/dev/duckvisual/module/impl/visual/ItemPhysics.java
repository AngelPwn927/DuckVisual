package dev.duckvisual.module.impl.visual;

import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;

/** Dropped items lie flat on the ground instead of floating and spinning (ItemEntityRendererMixin). */
public class ItemPhysics extends Module {
    public ItemPhysics() {
        super("ItemPhysics", "Dropped items lie flat on the ground", Category.VISUALS);
    }
}
