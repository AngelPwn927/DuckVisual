package dev.duckvisual.module;

public enum Category {
    VISUALS("Visuals", "V", "visuals", "Визуалы", "ESP, эффекты удара и анимации"),
    HUD("Hud", "H", "hud", "Интерфейс", "Панели на экране, их можно двигать"),
    WORLD("World", "W", "world", "Мир", "Небо, частицы и цвет мира"),
    COSMETICS("Cosmetics", "C", "cosmetics", "Косметика", "Плащи, маски и скины из гардероба"),
    MISC("Misc", "M", "misc", "Стиль", "Тема, шрифт и меню");

    public final String title;
    public final String icon;
    /** vector icon name for {@code dev.duckvisual.gui.Icons} */
    public final String iconName;
    public final String ru;
    public final String about;

    Category(String title, String icon, String iconName, String ru, String about) {
        this.title = title;
        this.icon = icon;
        this.iconName = iconName;
        this.ru = ru;
        this.about = about;
    }
}
