package dev.duckvisual.module.impl.cosmetics;

import dev.duckvisual.cosmetics.CosmeticsManager;
import dev.duckvisual.setting.BooleanSetting;

public class Capes extends CosmeticModule {
    private final BooleanSetting elytra = add(new BooleanSetting("Elytra", true));

    public Capes() {
        super("Capes", "Capes from the duckvisual.ru wardrobe", CosmeticsManager.Kind.CAPES);
    }

    /** Paint the elytra with the cape too. */
    public boolean elytra() { return elytra.get(); }
}
