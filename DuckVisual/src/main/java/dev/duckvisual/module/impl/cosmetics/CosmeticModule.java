package dev.duckvisual.module.impl.cosmetics;

import dev.duckvisual.cosmetics.CosmeticsManager;
import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.setting.ModeSetting;

import java.util.ArrayList;
import java.util.List;

/** Shared part of Capes, Masks and Skins: what I wear and whether to show other players' picks. */
public abstract class CosmeticModule extends Module {
    public static final String SITE = "Wardrobe", NONE = "None";

    private final CosmeticsManager.Kind kind;
    protected final ModeSetting mine;
    private final BooleanSetting others;

    protected CosmeticModule(String name, String description, CosmeticsManager.Kind kind) {
        super(name, description, Category.COSMETICS);
        this.kind = kind;
        List<String> modes = new ArrayList<>(List.of(SITE, NONE));
        modes.addAll(CosmeticsManager.names(kind));
        mine = add(new ModeSetting("Mine", SITE, modes.toArray(String[]::new)));
        others = add(new BooleanSetting("Others", true));
        setEnabledSilent(true); // on by default, also for configs saved before cosmetics existed
    }

    /** My item: the wardrobe pick from the site, or the one chosen right here in the GUI. */
    public String mine(String fromSite) {
        if (mine.is(SITE)) return fromSite;
        if (mine.is(NONE)) return null;
        return CosmeticsManager.idByName(kind, mine.get());
    }

    public boolean usesSite() { return mine.is(SITE); }

    public boolean others() { return others.get(); }

    @Override
    public void onTick() {
        if (mc.player != null) CosmeticsManager.poll(mc);
    }

    @Override
    public String getSuffix() { return mine.get(); }
}
