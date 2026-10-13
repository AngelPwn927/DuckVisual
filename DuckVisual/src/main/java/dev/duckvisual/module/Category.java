package dev.duckvisual.module;

public enum Category {
    VISUALS("Visuals", "V"),
    HUD("Hud", "H"),
    WORLD("World", "W"),
    MISC("Misc", "M");

    public final String title;
    public final String icon;

    Category(String title, String icon) {
        this.title = title;
        this.icon = icon;
    }
}
