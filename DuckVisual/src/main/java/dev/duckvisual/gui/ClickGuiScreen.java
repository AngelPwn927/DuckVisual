package dev.duckvisual.gui;

import dev.duckvisual.DuckVisual;
import dev.duckvisual.module.Category;
import dev.duckvisual.module.HudModule;
import dev.duckvisual.module.Module;
import dev.duckvisual.module.impl.hud.Watermark;
import dev.duckvisual.module.impl.misc.ClickGui;
import dev.duckvisual.module.impl.misc.Theme;
import dev.duckvisual.setting.BindSetting;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.setting.ColorSetting;
import dev.duckvisual.setting.ModeSetting;
import dev.duckvisual.setting.NumberSetting;
import dev.duckvisual.setting.Setting;
import dev.duckvisual.util.Animation;
import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Glass;
import dev.duckvisual.util.Keys;
import dev.duckvisual.util.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Duck Visual menu in the macOS style: a frosted glass window with traffic-light buttons,
 * a sidebar (search, categories, palette, configs, guide, project) and a list of modules with
 * iOS switches. Right-click a row (or the gear) for settings, middle-click (or the key pill) to bind.
 * HUD elements can be dragged around the screen while the menu is open.
 * <p>
 * Shapes are drawn in one batch per frame (Render2D.beginBatch) and the glass blur is computed
 * once per frame, so the menu stays cheap even on weak PCs.
 */
public class ClickGuiScreen extends Screen {
    private static final int SIDEBAR = 132;
    private static final int TINT = 0xBE141418, SIDE_TINT = 0x26000000;
    private static final int TEXT = 0xFFF2F2F7, SUBTEXT = 0xFF98989F, DIM = 0xFF6C6C74;
    private static final int CARD = 0x0EFFFFFF, CARD_HOVER = 0x18FFFFFF, SEP = 0x14FFFFFF, FIELD = 0x14FFFFFF;
    private static final int RED = 0xFFFF5F57, YELLOW = 0xFFFEBC2E, GREEN = 0xFF28C840;

    /** The green traffic light makes the window bigger. */
    private static boolean large;
    private static int W = 500, H = 320;

    private static float windowX = -1, windowY = -1;
    private static Category current = Category.VISUALS;
    private static final Map<Category, Float> scroll = new HashMap<>();
    private static final Map<Module, Float> expand = new HashMap<>();
    private static final Map<Module, Boolean> expanded = new HashMap<>();
    private static final Map<ColorSetting, Boolean> pickerOpen = new HashMap<>();
    private static final Map<String, Float> tabAnim = new HashMap<>();
    private static final Map<Module, String> shortDesc = new HashMap<>();

    private float scrollAnim;
    private static final float OPEN_TIME = 0.2f, CLOSE_TIME = 0.12f;

    /** 0..1, driven by wall-clock time so open/close speed does not depend on FPS. */
    private float openAnim;
    private boolean closing;
    private long animStart = System.nanoTime();
    private float closeFrom = 1;
    private String search = "";
    private boolean searchFocused, clickedSearch, clickedConfigField;

    private Module binding;
    private BindSetting bindingSetting;
    private static boolean paletteTab, configsTab, guideTab, creatorTab;
    private static Module guideFocus;
    private static float guideScroll;
    private float guideScrollAnim;
    private String configName = "";
    private boolean configFocused;
    private String configStatus = "";
    private boolean draggingWindow;
    private float dragX, dragY;
    private HudModule draggingHud;
    private Slider activeSlider;
    private float contentHeight;

    /** Clickable regions recorded during render and consumed by mouse handlers. */
    private final List<Hit> hits = new ArrayList<>();

    private record Hit(float x, float y, float w, float h, Consumer<Integer> action) {
        boolean inside(double mx, double my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }

    /** A slider currently being dragged; {@code setter} receives 0..1. */
    private record Slider(float x, float w, Consumer<Float> setter) {}

    public ClickGuiScreen() {
        super(Text.literal(DuckVisual.NAME));
    }

    private static void applySize() {
        W = large ? 580 : 500;
        H = large ? 370 : 320;
    }

    @Override
    protected void init() {
        applySize();
        if (windowX < 0 || windowX > width - 40 || windowY > height - 40) centerWindow();
        scrollAnim = scroll.getOrDefault(current, 0f);
    }

    private void centerWindow() {
        windowX = (width - W) / 2f;
        windowY = (height - H) / 2f;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ClickGui gui = DuckVisual.modules().get(ClickGui.class);
        if (gui.realBlur.get() && openAnim > 0.3f && client != null && client.world != null) {
            applyBlur();
        }
        if (gui.blur.get()) {
            int a = (int) (90 * openAnim);
            ctx.fillGradient(0, 0, width, height, ColorUtil.withAlpha(0xFF050508, a), ColorUtil.withAlpha(0xFF0A0A10, a));
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        float elapsed = (System.nanoTime() - animStart) / 1_000_000_000f;
        if (closing) {
            float p = Math.min(1, elapsed / CLOSE_TIME);
            openAnim = closeFrom * (1 - p * p);
            if (p >= 1) {
                super.close();
                return;
            }
        } else {
            openAnim = Math.min(1, elapsed / OPEN_TIME);
        }
        renderBackground(ctx, mouseX, mouseY, delta);
        hits.clear();
        ClickGui gui = DuckVisual.modules().get(ClickGui.class);
        if (gui.glass.get()) Glass.capture(ctx);
        Render2D.beginBatch(ctx);

        // HUD drag outlines
        for (Module m : DuckVisual.modules().getModules()) {
            if (m instanceof HudModule hud && hud.isEnabled()) {
                boolean hov = hud == draggingHud || (hud.isHovered(mouseX, mouseY) && !insideWindow(mouseX, mouseY));
                if (hov) {
                    Render2D.roundedOutline(ctx, hud.getX() - 2, hud.getY() - 2, hud.getWidth() + 4, hud.getHeight() + 4, 5, 1,
                            ColorUtil.withAlpha(ColorUtil.theme(), 160));
                }
            }
        }
        if (draggingHud != null) {
            float nx = mouseX - dragX, ny = mouseY - dragY;
            float hw = draggingHud.getWidth(), hh = draggingHud.getHeight();
            // snap to screen center lines and edges (hold Shift to move freely)
            boolean free = hasShiftDown();
            boolean snapX = false, snapY = false;
            if (!free) {
                if (Math.abs(nx + hw / 2 - width / 2f) < 5) { nx = width / 2f - hw / 2; snapX = true; }
                if (Math.abs(ny + hh / 2 - height / 2f) < 5) { ny = height / 2f - hh / 2; snapY = true; }
                if (Math.abs(nx - 4) < 5) nx = 4;
                if (Math.abs(nx + hw - (width - 4)) < 5) nx = width - 4 - hw;
                if (Math.abs(ny - 4) < 5) ny = 4;
                if (Math.abs(ny + hh - (height - 4)) < 5) ny = height - 4 - hh;
            }
            draggingHud.x.set((double) nx);
            draggingHud.y.set((double) ny);
            draggingHud.clamp(width, height);
            if (snapX) Render2D.rect(ctx, width / 2f - 0.5f, 0, 1, height, ColorUtil.withAlpha(ColorUtil.theme(), 120));
            if (snapY) Render2D.rect(ctx, 0, height / 2f - 0.5f, width, 1, ColorUtil.withAlpha(ColorUtil.theme(), 120));
        }
        if (draggingWindow) {
            windowX = mouseX - dragX;
            windowY = mouseY - dragY;
        }
        float ease = Animation.easeOutCubic(openAnim);
        float x = windowX, y = windowY;
        ctx.getMatrices().push();
        float cx = x + W / 2f, cy = y + H / 2f;
        float scale = (0.94f + 0.06f * ease) * fitScale();
        ctx.getMatrices().translate(cx, cy, 0);
        ctx.getMatrices().scale(scale, scale, 1);
        ctx.getMatrices().translate(-cx, -cy, 0);
        // mouse in window space (undo the open-scale animation)
        float mx = cx + (mouseX - cx) / scale, my = cy + (mouseY - cy) / scale;

        // window: soft shadow, frosted glass, darker sidebar, hairline border
        Render2D.shadow(ctx, x, y + 4, W, H, 12, 22, ColorUtil.withAlpha(0xFF000000, (int) (120 * openAnim)));
        Glass.panel(ctx, x, y, W, H, 12, TINT, openAnim);
        Render2D.roundedRect(ctx, x, y, SIDEBAR, H, 12, ColorUtil.fade(SIDE_TINT, openAnim));
        Render2D.rect(ctx, x + SIDEBAR - 12, y, 12, H, ColorUtil.fade(SIDE_TINT, openAnim));
        Render2D.rect(ctx, x + SIDEBAR, y, 1, H, ColorUtil.fade(SEP, openAnim));
        Render2D.roundedOutline(ctx, x, y, W, H, 12, 1, ColorUtil.fade(0x26FFFFFF, openAnim));

        renderSidebar(ctx, x, y, mx, my);
        renderContent(ctx, x + SIDEBAR + 12, y + 12, W - SIDEBAR - 24, H - 22, mx, my);

        ctx.getMatrices().pop();

        if (binding != null || bindingSetting != null) {
            String s = "Нажми клавишу или кнопку мыши для «"
                    + (binding != null ? binding.getName() : bindingSetting.getName()) + "» (Esc — убрать)";
            float tw = Render2D.width(s) + 20;
            Glass.panel(ctx, width / 2f - tw / 2, 10, tw, 20, 10, TINT, 1);
            Render2D.roundedOutline(ctx, width / 2f - tw / 2, 10, tw, 20, 10, 1, 0x26FFFFFF);
            Render2D.centeredText(ctx, s, width / 2f, 16, TEXT);
        }
        Render2D.endBatch(ctx);
    }

    /** Shrinks the whole window on small screens / large GUI scales so it always fits. */
    private float fitScale() {
        return Math.min(1f, Math.min((width - 16f) / W, (height - 16f) / H));
    }

    private boolean insideWindow(double mx, double my) {
        double[] p = toWindow(mx, my);
        return p[0] >= windowX && p[0] <= windowX + W && p[1] >= windowY && p[1] <= windowY + H;
    }

    // ------------------------------------------------------------------ helpers

    private static void small(DrawContext ctx, String s, float x, float y, float scale, int color) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        ctx.getMatrices().scale(scale, scale, 1);
        Render2D.text(ctx, s, 0, 0, color);
        ctx.getMatrices().pop();
    }

    private static String ellipsize(String s, float maxW, float scale) {
        if (Render2D.width(s) * scale <= maxW) return s;
        while (s.length() > 2 && Render2D.width(s + "…") * scale > maxW) s = s.substring(0, s.length() - 1);
        return s.trim() + "…";
    }

    private static String plural(int n) {
        int a = n % 100, b = n % 10;
        if (a >= 11 && a <= 14) return "функций";
        return b == 1 ? "функция" : b >= 2 && b <= 4 ? "функции" : "функций";
    }

    private static boolean over(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    /** iOS-style switch. */
    private static void toggle(DrawContext ctx, float x, float y, float w, float h, float t) {
        int on = ColorUtil.theme();
        Render2D.roundedRect(ctx, x, y, w, h, h / 2, ColorUtil.lerp(0x33FFFFFF, on, t));
        float r = h / 2 - 1.2f, kx = x + h / 2 + (w - h) * t;
        Render2D.circle(ctx, kx, y + h / 2 + 0.4f, r + 0.5f, 0x40000000);
        Render2D.circle(ctx, kx, y + h / 2, r, 0xFFFFFFFF);
    }

    /** First sentence of the Russian guide text (falls back to the English description). */
    private static String desc(Module m) {
        return shortDesc.computeIfAbsent(m, k -> {
            String t = Guide.get(k.getName());
            if (t.isEmpty()) return k.getDescription();
            int cut = t.length();
            for (String stop : new String[]{". ", ": ", " — ", "; "}) {
                int i = t.indexOf(stop);
                if (i > 12 && i < cut) cut = i;
            }
            return t.substring(0, cut).replace("«", "").replace("»", "");
        });
    }

    // ------------------------------------------------------------------ sidebar

    private void renderSidebar(DrawContext ctx, float x, float y, float mx, float my) {
        // traffic lights: close, minimize (also closes), zoom (bigger window)
        float ly = y + 13;
        boolean lightsHover = over(mx, my, x + 8, ly - 7, 40, 14);
        int[] colors = {RED, YELLOW, GREEN};
        String[] glyphs = {"close", "minus", "plus"};
        for (int i = 0; i < 3; i++) {
            float lx = x + 14 + i * 12;
            Render2D.circle(ctx, lx, ly, 4.2f, ColorUtil.fade(colors[i], openAnim));
            if (lightsHover) Icons.draw(ctx, glyphs[i], lx - 2, ly - 2, 4, 0x99000000);
        }
        hits.add(new Hit(x, y, SIDEBAR, 26, b -> {
            draggingWindow = true;
            dragX = (float) lastMouseX - windowX;
            dragY = (float) lastMouseY - windowY;
        }));
        hits.add(new Hit(x + 9, ly - 5, 10, 10, b -> close()));
        hits.add(new Hit(x + 21, ly - 5, 10, 10, b -> close()));
        hits.add(new Hit(x + 33, ly - 5, 10, 10, b -> {
            large = !large;
            float ocx = windowX + W / 2f, ocy = windowY + H / 2f;
            applySize();
            windowX = ocx - W / 2f;
            windowY = ocy - H / 2f;
        }));

        // logo
        float hy = y + 28;
        Watermark.duck(ctx, x + 12, hy, 18);
        Render2D.text(ctx, "Duck Visual", x + 35, hy + 1, TEXT);
        small(ctx, "v" + DuckVisual.VERSION + " · 1.21.4", x + 35, hy + 11, 0.7f, SUBTEXT);

        // search
        float sy = y + 54, sw = SIDEBAR - 20;
        Render2D.roundedRect(ctx, x + 10, sy, sw, 17, 6, searchFocused ? 0x22FFFFFF : FIELD);
        if (searchFocused) Render2D.roundedOutline(ctx, x + 10, sy, sw, 17, 6, 1, ColorUtil.withAlpha(ColorUtil.theme(), 170));
        Icons.draw(ctx, "search", x + 16, sy + 5, 7, SUBTEXT);
        String shown = search.isEmpty() && !searchFocused ? "Поиск" : search
                + ((searchFocused && System.currentTimeMillis() / 500 % 2 == 0) ? "|" : "");
        Render2D.text(ctx, shown, x + 27, sy + 5, search.isEmpty() && !searchFocused ? DIM : TEXT);
        hits.add(new Hit(x + 10, sy, sw, 17, b -> {
            searchFocused = true;
            clickedSearch = true;
        }));

        float ty = y + 80;
        boolean noTab = search.isEmpty() && !paletteTab && !configsTab && !guideTab && !creatorTab;
        for (Category c : Category.values()) {
            int count = (int) DuckVisual.modules().getModules(c).stream().filter(Module::isEnabled).count();
            ty = sideItem(ctx, x, ty, mx, my, c.name(), c.iconName, c.ru, String.valueOf(count), c == current && noTab, () -> {
                scroll.put(current, scrollAnim);
                current = c;
                paletteTab = configsTab = guideTab = creatorTab = false;
                search = "";
                scrollAnim = scroll.getOrDefault(c, 0f);
            });
        }
        small(ctx, "НАСТРОЙКИ", x + 14, ty + 6, 0.62f, DIM);
        ty += 16;
        ty = sideItem(ctx, x, ty, mx, my, "palette", "palette", "Палитра", String.valueOf(Theme.PRESETS.length),
                paletteTab && !configsTab && !guideTab && !creatorTab && search.isEmpty(), () -> {
                    paletteTab = true;
                    configsTab = guideTab = creatorTab = false;
                    search = "";
                });
        ty = sideItem(ctx, x, ty, mx, my, "configs", "configs", "Конфиги", "",
                configsTab && !guideTab && !creatorTab && search.isEmpty(), () -> {
                    configsTab = true;
                    paletteTab = guideTab = creatorTab = false;
                    search = "";
                });
        ty = sideItem(ctx, x, ty, mx, my, "guide", "guide", "Описание", "", guideTab && !creatorTab, () -> openGuide(null));
        sideItem(ctx, x, ty, mx, my, "project", "project", "Проект", "", creatorTab, () -> {
            creatorTab = true;
            guideTab = paletteTab = configsTab = false;
            search = "";
        });
    }

    private float sideItem(DrawContext ctx, float x, float ty, float mx, float my, String key, String icon, String label,
                           String badge, boolean sel, Runnable action) {
        float ix = x + 8, iw = SIDEBAR - 16, ih = 19;
        boolean hov = over(mx, my, ix, ty, iw, ih);
        float a = Animation.approach(tabAnim.getOrDefault(key, 0f), sel ? 1 : 0, 14);
        float h = Animation.approach(tabAnim.getOrDefault(key + "#h", 0f), hov && !sel ? 1 : 0, 16);
        tabAnim.put(key, a);
        tabAnim.put(key + "#h", h);
        if (h > 0.01f) Render2D.roundedRect(ctx, ix, ty, iw, ih, 6, ColorUtil.withAlpha(0xFFFFFFFF, (int) (18 * h)));
        if (a > 0.01f) {
            Render2D.roundedGradient(ctx, ix, ty, iw, ih, 6,
                    ColorUtil.fade(ColorUtil.theme(0), a), ColorUtil.fade(ColorUtil.theme(8), a),
                    ColorUtil.fade(ColorUtil.theme(8), a), ColorUtil.fade(ColorUtil.theme(0), a));
        }
        int fg = ColorUtil.lerp(0xFFD8D8DE, 0xFF1A1104, a);
        Icons.draw(ctx, icon, ix + 7, ty + 5, 9, icon.equals("palette") ? 0 : ColorUtil.lerp(ColorUtil.theme(), 0xFF1A1104, a));
        Render2D.text(ctx, label, ix + 22, ty + 6, fg);
        if (!badge.isEmpty()) {
            float bw = Render2D.width(badge) * 0.75f;
            small(ctx, badge, ix + iw - 7 - bw, ty + 7, 0.75f, ColorUtil.lerp(DIM, 0xAA1A1104, a));
        }
        hits.add(new Hit(ix, ty, iw, ih, b -> action.run()));
        return ty + ih + 2;
    }

    // ------------------------------------------------------------------ content

    private void header(DrawContext ctx, float x, float y, float w, String title, String sub) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        ctx.getMatrices().scale(1.35f, 1.35f, 1);
        Render2D.text(ctx, title, 0, 0, TEXT);
        ctx.getMatrices().pop();
        small(ctx, sub, x, y + 15, 0.72f, SUBTEXT);
        // content header is a drag handle too
        hits.add(new Hit(x, y - 4, w, 26, b -> {
            draggingWindow = true;
            dragX = (float) lastMouseX - windowX;
            dragY = (float) lastMouseY - windowY;
        }));
    }

    private void renderContent(DrawContext ctx, float x, float y, float w, float h, float mx, float my) {
        if (creatorTab) {
            renderCreator(ctx, x, y, w, h, mx, my);
            return;
        }
        if (guideTab) {
            renderGuide(ctx, x, y, w, h, mx, my);
            return;
        }
        if (configsTab && search.isEmpty()) {
            renderConfigs(ctx, x, y, w, h, mx, my);
            return;
        }
        if (paletteTab && search.isEmpty()) {
            renderPalette(ctx, x, y, w, h, mx, my);
            return;
        }
        List<Module> modules = search.isEmpty()
                ? DuckVisual.modules().getModules(current)
                : DuckVisual.modules().getModules().stream()
                .filter(m -> m.getName().toLowerCase().contains(search.toLowerCase())
                        || desc(m).toLowerCase().contains(search.toLowerCase())).toList();

        if (search.isEmpty()) header(ctx, x, y, w, current.ru, current.about + " · " + modules.size() + " " + plural(modules.size()) + " · ПКМ — настройки, СКМ — бинд");
        else header(ctx, x, y, w, "Поиск", "«" + search + "» · найдено " + modules.size());

        float top = y + 30, viewH = h - 30;
        float target = MathHelper.clamp(scroll.getOrDefault(current, 0f), -Math.max(0, contentHeight - viewH), 0);
        scroll.put(current, target);
        scrollAnim = Animation.approach(scrollAnim, target, 16);

        Render2D.flush(ctx);
        ctx.enableScissor((int) x - 2, (int) top, (int) (x + w + 2), (int) (top + viewH));
        float yy = top + scrollAnim;
        boolean mouseInView = mx >= x && mx <= x + w && my >= top && my <= top + viewH;
        for (Module m : modules) {
            yy += renderRow(ctx, m, x, yy, w, mx, my, mouseInView, top, top + viewH) + 4;
        }
        Render2D.flush(ctx);
        ctx.disableScissor();
        contentHeight = yy - (top + scrollAnim);
        if (modules.isEmpty()) Render2D.centeredText(ctx, "Ничего не найдено", x + w / 2, top + 40, DIM);

        // scrollbar (macOS overlay style)
        if (contentHeight > viewH) {
            float barH = Math.max(16, viewH * viewH / contentHeight);
            float barY = top + (-scrollAnim / (contentHeight - viewH)) * (viewH - barH);
            Render2D.roundedRect(ctx, x + w + 4, barY, 3, barH, 1.5f, 0x40FFFFFF);
        }
    }

    private float renderRow(DrawContext ctx, Module m, float x, float y, float w, float mx, float my, boolean mouseInView,
                            float clipTop, float clipBottom) {
        boolean showDesc = DuckVisual.modules().get(ClickGui.class).descriptions.get();
        float headH = showDesc ? 30 : 21;
        float ex = Animation.approach(expand.getOrDefault(m, 0f), expanded.getOrDefault(m, false) ? 1 : 0, 14);
        expand.put(m, ex);

        float settingsH = 0;
        int visible = 0;
        for (Setting<?> s : m.getSettings()) {
            if (s.isVisible()) {
                settingsH += settingHeight(s);
                visible++;
            }
        }
        float total = headH + (settingsH + (settingsH > 0 ? 8 : 0)) * ex;
        if (y > clipBottom || y + total < clipTop) return total; // off screen: layout only

        boolean hover = mouseInView && over(mx, my, x, y, w, headH);
        Render2D.roundedRect(ctx, x, y, w, total, 8, hover ? CARD_HOVER : CARD);
        if (m.animation > 0.01f) Render2D.roundedRect(ctx, x, y, w, total, 8, ColorUtil.withAlpha(ColorUtil.theme(), (int) (16 * m.animation)));

        // controls from the right: switch, gear, key pill, "?"
        float cy = y + headH / 2;
        float swW = 22, swH = 12, swX = x + w - 10 - swW;
        boolean isGui = m instanceof ClickGui;
        if (!isGui) toggle(ctx, swX, cy - swH / 2, swW, swH, m.animation);
        float gx = isGui ? x + w - 20 : swX - 17;
        if (visible > 0) {
            boolean gh = mouseInView && over(mx, my, gx - 2, cy - 6, 13, 12);
            Icons.draw(ctx, "gear", gx, cy - 4.5f, 9, expanded.getOrDefault(m, false) ? ColorUtil.theme() : gh ? TEXT : DIM);
            if (mouseInView) hits.add(new Hit(gx - 2, cy - 6, 13, 12, b -> expanded.put(m, !expanded.getOrDefault(m, false))));
        }
        float px = gx - 6;
        if (!isGui) {
            String k = binding == m ? "..." : m.getKey() > 0 ? Keys.name(m.getKey()) : "—";
            float kw = Math.max(18, Render2D.width(k) * 0.7f + 8);
            px -= kw;
            boolean kh = mouseInView && over(mx, my, px, cy - 5.5f, kw, 11);
            Render2D.roundedRect(ctx, px, cy - 5.5f, kw, 11, 5.5f, binding == m ? ColorUtil.withAlpha(ColorUtil.theme(), 90) : kh ? 0x24FFFFFF : FIELD);
            small(ctx, k, px + kw / 2 - Render2D.width(k) * 0.35f, cy - 2.5f, 0.7f, m.getKey() > 0 ? TEXT : DIM);
            if (mouseInView) hits.add(new Hit(px, cy - 5.5f, kw, 11, b -> {
                if (b == 1) m.setKey(-1);
                else {
                    bindingSetting = null;
                    binding = m;
                }
            }));
        }
        float qx = px - 15;
        boolean qh = mouseInView && over(mx, my, qx, cy - 5, 10, 10);
        Render2D.circle(ctx, qx + 5, cy, 5, qh ? ColorUtil.withAlpha(ColorUtil.theme(), 200) : FIELD);
        small(ctx, "?", qx + 3.6f, cy - 2.6f, 0.7f, qh ? 0xFF111111 : DIM);

        // title + description
        Render2D.text(ctx, m.getName(), x + 10, y + (showDesc ? 7 : 7), TEXT);
        if (showDesc) small(ctx, ellipsize(desc(m), qx - x - 18, 0.7f), x + 10, y + 18, 0.7f, SUBTEXT);

        if (mouseInView) {
            // the whole row first, controls after it so they win the click
            hits.add(hits.size() - (isGui ? 0 : 1) - (visible > 0 ? 1 : 0), new Hit(x, y, w, headH, b -> {
                if (b == 0) m.toggle();
                else if (b == 1) expanded.put(m, !expanded.getOrDefault(m, false));
                else if (b == 2) {
                    bindingSetting = null;
                    binding = m;
                }
            }));
            hits.add(new Hit(qx, cy - 5, 10, 10, b -> openGuide(m)));
        }

        if (ex > 0.01f && settingsH > 0) {
            float sy = y + headH;
            Render2D.rect(ctx, x + 10, sy, w - 20, 1, SEP);
            sy += 4;
            float clip = y + total;
            for (Setting<?> s : m.getSettings()) {
                if (!s.isVisible()) continue;
                float sh = settingHeight(s);
                if (sy > clip) break;
                renderSetting(ctx, s, x + 10, sy, w - 20, mx, my, mouseInView && sy + sh <= clip + 1);
                sy += sh;
            }
        }
        return total;
    }

    // ------------------------------------------------------------------ settings

    private float settingHeight(Setting<?> s) {
        if (s instanceof NumberSetting) return 22;
        if (s instanceof ColorSetting c) return pickerOpen.getOrDefault(c, false) ? 16 + 4 * 10 + 2 : 16;
        return 16;
    }

    private void renderSetting(DrawContext ctx, Setting<?> s, float x, float y, float w, float mx, float my, boolean interact) {
        if (s instanceof BindSetting bs) {
            Render2D.text(ctx, s.getName(), x, y + 4, SUBTEXT);
            String v = bindingSetting == bs ? "..." : Keys.name(bs.get());
            float vw = Render2D.width(v) * 0.75f + 10;
            Render2D.roundedRect(ctx, x + w - vw, y + 2, vw, 12, 6, bindingSetting == bs ? ColorUtil.withAlpha(ColorUtil.theme(), 90) : FIELD);
            small(ctx, v, x + w - vw + 5, y + 5, 0.75f, bs.get() > 0 ? TEXT : DIM);
            if (interact) hits.add(new Hit(x, y, w, 15, btn -> {
                if (btn == 1) bs.set(-1);
                else {
                    binding = null;
                    bindingSetting = bs;
                }
            }));
        } else if (s instanceof BooleanSetting b) {
            Render2D.text(ctx, s.getName(), x, y + 4, b.get() ? TEXT : SUBTEXT);
            toggle(ctx, x + w - 17, y + 3, 17, 10, b.get() ? 1 : 0);
            if (interact) hits.add(new Hit(x, y, w, 15, btn -> b.toggle()));
        } else if (s instanceof NumberSetting n) {
            Render2D.text(ctx, s.getName(), x, y + 2, SUBTEXT);
            String v = n.display();
            Render2D.text(ctx, v, x + w - Render2D.width(v), y + 2, TEXT);
            float by = y + 15, bh = 3;
            float frac = (float) ((n.get() - n.getMin()) / (n.getMax() - n.getMin()));
            Render2D.roundedRect(ctx, x, by, w, bh, 1.5f, 0x24FFFFFF);
            Render2D.roundedGradient(ctx, x, by, Math.max(bh, w * frac), bh, 1.5f,
                    ColorUtil.theme(0), ColorUtil.theme(8), ColorUtil.theme(8), ColorUtil.theme(0));
            Render2D.circle(ctx, x + w * frac, by + 1.9f, 4.4f, 0x40000000);
            Render2D.circle(ctx, x + w * frac, by + 1.5f, 3.8f, 0xFFFFFFFF);
            if (interact) hits.add(new Hit(x, y, w, 21, btn -> activeSlider = new Slider(x, w,
                    f -> n.set(n.getMin() + (n.getMax() - n.getMin()) * f))));
        } else if (s instanceof ModeSetting mo) {
            Render2D.text(ctx, s.getName(), x, y + 4, SUBTEXT);
            String v = mo.get();
            float vw = Render2D.width(v) * 0.8f + 22;
            float vx = x + w - vw;
            Render2D.roundedRect(ctx, vx, y + 1.5f, vw, 13, 6.5f, FIELD);
            small(ctx, "‹", vx + 4, y + 4, 0.8f, DIM);
            small(ctx, v, vx + 11, y + 4.5f, 0.8f, ColorUtil.theme());
            small(ctx, "›", vx + vw - 8, y + 4, 0.8f, DIM);
            if (interact) hits.add(new Hit(x, y, w, 15, btn -> mo.cycle(btn == 0)));
        } else if (s instanceof ColorSetting c) {
            Render2D.text(ctx, s.getName(), x, y + 4, SUBTEXT);
            Render2D.circle(ctx, x + w - 6, y + 8, 5.5f, 0x40FFFFFF);
            Render2D.circle(ctx, x + w - 6, y + 8, 4.5f, c.get() | 0xFF000000);
            if (interact) hits.add(new Hit(x, y, w, 15, btn -> pickerOpen.put(c, !pickerOpen.getOrDefault(c, false))));
            if (pickerOpen.getOrDefault(c, false)) renderPicker(ctx, c, x, y + 17, w, interact);
        }
    }

    private void renderPicker(DrawContext ctx, ColorSetting c, float x, float y, float w, boolean interact) {
        float bh = 6;
        int steps = 12;
        for (int i = 0; i < steps; i++) {
            int c1 = 0xFF000000 | Color.HSBtoRGB(i / (float) steps, 1, 1);
            int c2 = 0xFF000000 | Color.HSBtoRGB((i + 1) / (float) steps, 1, 1);
            Render2D.horizontalGradient(ctx, x + w * i / steps, y, w / steps + 0.5f, bh, c1, c2);
        }
        marker(ctx, x + w * c.getHue(), y, bh);
        if (interact) hits.add(new Hit(x, y - 2, w, bh + 4, b -> activeSlider = new Slider(x, w, c::setHue)));
        y += 10;
        Render2D.horizontalGradient(ctx, x, y, w, bh, 0xFF000000 | Color.HSBtoRGB(c.getHue(), 0, c.getBrightness()),
                0xFF000000 | Color.HSBtoRGB(c.getHue(), 1, c.getBrightness()));
        marker(ctx, x + w * c.getSaturation(), y, bh);
        if (interact) hits.add(new Hit(x, y - 2, w, bh + 4, b -> activeSlider = new Slider(x, w, c::setSaturation)));
        y += 10;
        Render2D.horizontalGradient(ctx, x, y, w, bh, 0xFF000000,
                0xFF000000 | Color.HSBtoRGB(c.getHue(), c.getSaturation(), 1));
        marker(ctx, x + w * c.getBrightness(), y, bh);
        if (interact) hits.add(new Hit(x, y - 2, w, bh + 4, b -> activeSlider = new Slider(x, w, c::setBrightness)));
        y += 10;
        Render2D.horizontalGradient(ctx, x, y, w, bh, c.get() & 0xFFFFFF, c.get() | 0xFF000000);
        marker(ctx, x + w * c.getAlpha() / 255f, y, bh);
        if (interact) hits.add(new Hit(x, y - 2, w, bh + 4, b -> activeSlider = new Slider(x, w, f -> c.setAlpha((int) (f * 255)))));
    }

    private static void marker(DrawContext ctx, float x, float y, float h) {
        Render2D.roundedRect(ctx, x - 1.5f, y - 1, 3, h + 2, 1.5f, 0xFF000000);
        Render2D.rect(ctx, x - 0.5f, y - 1, 1, h + 2, 0xFFFFFFFF);
    }

    // ------------------------------------------------------------------ project page

    private static final String CHANNEL = "https://t.me/duckvisual";
    private static final String SUPPORT = "https://t.me/duckvisual/2";
    private static final String SITE = "https://duckvisual.ru";

    /** Project page: channel, site and support, plus the Mojang disclaimer. */
    private void renderCreator(DrawContext ctx, float x, float y, float w, float h, float mx, float my) {
        float cx = x + w / 2;
        float cy = y + 20;
        Render2D.glowDot(ctx, cx, cy + 26, 80, ColorUtil.withAlpha(ColorUtil.theme(), 60));
        Watermark.duck(ctx, cx - 28, cy, 56);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx, cy + 66, 0);
        ctx.getMatrices().scale(2, 2, 1);
        float tw = Render2D.width("Duck Visual");
        Render2D.gradientText(ctx, "Duck Visual", -tw / 2, 0, 0);
        ctx.getMatrices().pop();
        Render2D.centeredText(ctx, "Спасибо, что пользуешься Duck Visual!", cx, cy + 92, SUBTEXT);

        float bw = 180, bh = 24, bx = cx - bw / 2, by = cy + 112;
        button(ctx, bx, by, bw, bh, mx, my, "Канал  @DuckVisual", 0xFF2AABEE, 0xFFFFFFFF, CHANNEL);
        button(ctx, bx, by + bh + 6, bw, bh, mx, my, "Сайт и гардероб", 0x00000000, TEXT, SITE);
        button(ctx, bx, by + (bh + 6) * 2, bw, bh, mx, my, "★  Поддержать звёздами", ColorUtil.theme(), 0xFF1A1104, SUPPORT);

        small(ctx, "Duck Visual / Duck Launcher не является продуктом Mojang AB и не связан с Microsoft.",
                cx - Render2D.width("Duck Visual / Duck Launcher не является продуктом Mojang AB и не связан с Microsoft.") * 0.36f, y + h - 18, 0.72f, DIM);
        small(ctx, "Minecraft — товарный знак Mojang AB.", cx - Render2D.width("Minecraft — товарный знак Mojang AB.") * 0.36f, y + h - 9, 0.72f, DIM);
    }

    private void button(DrawContext ctx, float bx, float by, float bw, float bh, float mx, float my, String label, int fill, int fg, String url) {
        boolean hov = over(mx, my, bx, by, bw, bh);
        if (ColorUtil.alpha(fill) == 0) {
            Render2D.roundedRect(ctx, bx, by, bw, bh, 8, hov ? 0x24FFFFFF : FIELD);
            Render2D.roundedOutline(ctx, bx, by, bw, bh, 8, 1, 0x1EFFFFFF);
        } else {
            Render2D.shadow(ctx, bx, by, bw, bh, 8, hov ? 8 : 5, ColorUtil.withAlpha(fill, hov ? 140 : 80));
            Render2D.roundedRect(ctx, bx, by, bw, bh, 8, fill);
            if (hov) Render2D.roundedRect(ctx, bx, by, bw, bh, 8, 0x22FFFFFF);
        }
        Render2D.centeredText(ctx, label, bx + bw / 2, by + bh / 2 - 3.5f, fg);
        hits.add(new Hit(bx, by, bw, bh, b -> net.minecraft.util.Util.getOperatingSystem().open(url)));
    }

    // ------------------------------------------------------------------ guide

    private void openGuide(Module focus) {
        guideTab = true;
        creatorTab = paletteTab = configsTab = false;
        guideFocus = focus;
        guideScroll = 0;
        guideScrollAnim = 0;
        search = "";
    }

    /** Splits text into lines that fit {@code maxW} pixels at the given scale. */
    private static List<String> wrap(String text, float maxW, float scale) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String test = line.length() == 0 ? word : line + " " + word;
            if (Render2D.width(test) * scale > maxW && line.length() > 0) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }

    /** Scrollable list of every module with its long description (search filters it too). */
    private void renderGuide(DrawContext ctx, float x, float y, float w, float h, float mx, float my) {
        header(ctx, x, y, w, "Описание", "Что делает каждая функция и её настройки");
        float top = y + 30, viewH = h - 30;
        float descScale = 0.78f;
        float lineH = 10 * descScale + 1;

        List<Module> mods = DuckVisual.modules().getModules().stream()
                .filter(m -> search.isEmpty() || m.getName().toLowerCase().contains(search.toLowerCase())
                        || Guide.get(m.getName()).toLowerCase().contains(search.toLowerCase()))
                .toList();
        float focusY = -1, total = 0;
        List<List<String>> wrapped = new ArrayList<>();
        for (Module m : mods) {
            String text = Guide.get(m.getName());
            if (text.isEmpty()) text = m.getDescription();
            List<String> lines = wrap(text, w - 20, descScale);
            wrapped.add(lines);
            if (m == guideFocus) focusY = total;
            total += 22 + lines.size() * lineH + 6;
        }
        if (guideFocus != null && focusY >= 0) {
            guideScroll = -focusY;
            guideFocus = null;
        }
        guideScroll = MathHelper.clamp(guideScroll, -Math.max(0, total - viewH), 0);
        guideScrollAnim = Animation.approach(guideScrollAnim, guideScroll, 16);

        Render2D.flush(ctx);
        ctx.enableScissor((int) x - 2, (int) top, (int) (x + w + 2), (int) (top + viewH));
        float yy = top + guideScrollAnim;
        for (int i = 0; i < mods.size(); i++) {
            Module m = mods.get(i);
            List<String> lines = wrapped.get(i);
            float ch = 22 + lines.size() * lineH;
            if (yy + ch > top && yy < top + viewH) {
                Render2D.roundedRect(ctx, x, yy, w, ch, 8, CARD);
                Render2D.text(ctx, m.getName(), x + 10, yy + 7, m.isEnabled() ? ColorUtil.theme() : TEXT);
                small(ctx, m.getCategory().ru, x + 14 + Render2D.width(m.getName()), yy + 8, 0.7f, DIM);
                if (!(m instanceof ClickGui)) {
                    float swX = x + w - 29, swY = yy + 6;
                    toggle(ctx, swX, swY, 20, 11, m.animation);
                    if (yy + 6 > top && yy + 16 < top + viewH) hits.add(new Hit(swX - 2, swY - 2, 24, 15, b -> m.toggle()));
                }
                ctx.getMatrices().push();
                ctx.getMatrices().translate(x + 10, yy + 20, 0);
                ctx.getMatrices().scale(descScale, descScale, 1);
                float ly = 0;
                for (String l : lines) {
                    Render2D.text(ctx, l, 0, ly, 0xFFC8C8D0);
                    ly += lineH / descScale;
                }
                ctx.getMatrices().pop();
            }
            yy += ch + 6;
        }
        Render2D.flush(ctx);
        ctx.disableScissor();
        if (total > viewH) {
            float barH = Math.max(16, viewH * viewH / total);
            float barY = top + (-guideScrollAnim / (total - viewH)) * (viewH - barH);
            Render2D.roundedRect(ctx, x + w + 4, barY, 3, barH, 1.5f, 0x40FFFFFF);
        }
    }

    // ------------------------------------------------------------------ configs

    /** Save / load / delete named config profiles (config/duckvisual/<name>.json). */
    private void renderConfigs(DrawContext ctx, float x, float y, float w, float h, float mx, float my) {
        var cfg = DuckVisual.config();
        header(ctx, x, y, w, "Конфиги", "Сохрани настройки под именем и переключайся между ними");
        float fy = y + 32;
        float bw = 70, fw = w - bw - 6;
        Render2D.roundedRect(ctx, x, fy, fw, 20, 7, configFocused ? 0x22FFFFFF : FIELD);
        if (configFocused) Render2D.roundedOutline(ctx, x, fy, fw, 20, 7, 1, ColorUtil.withAlpha(ColorUtil.theme(), 170));
        String shown = configName.isEmpty() && !configFocused ? "Название конфига" : configName
                + ((configFocused && System.currentTimeMillis() / 500 % 2 == 0) ? "|" : "");
        Render2D.text(ctx, shown, x + 8, fy + 6, configName.isEmpty() && !configFocused ? DIM : TEXT);
        hits.add(new Hit(x, fy, fw, 20, b -> {
            configFocused = true;
            clickedConfigField = true;
        }));
        boolean hovSave = over(mx, my, x + fw + 6, fy, bw, 20);
        Render2D.roundedGradient(ctx, x + fw + 6, fy, bw, 20, 7, ColorUtil.theme(0), ColorUtil.theme(8), ColorUtil.theme(8), ColorUtil.theme(0));
        if (hovSave) Render2D.roundedRect(ctx, x + fw + 6, fy, bw, 20, 7, 0x30FFFFFF);
        Render2D.centeredText(ctx, "Сохранить", x + fw + 6 + bw / 2, fy + 6, 0xFF1A1104);
        hits.add(new Hit(x + fw + 6, fy, bw, 20, b -> {
            clickedConfigField = true;
            if (cfg.saveProfile(configName)) configStatus = "Сохранён «" + configName + "»";
            else configStatus = "Имя: латиница, цифры, _ и -";
        }));
        if (!configStatus.isEmpty()) small(ctx, configStatus, x + 2, fy + 24, 0.75f, ColorUtil.theme());
        float ly = fy + 36;
        List<String> list = cfg.listProfiles();
        if (list.isEmpty()) Render2D.text(ctx, "Сохранённых конфигов пока нет", x + 2, ly, DIM);
        for (String name : list) {
            boolean hov = over(mx, my, x, ly, w, 24);
            Render2D.roundedRect(ctx, x, ly, w, 24, 8, hov ? CARD_HOVER : CARD);
            Icons.draw(ctx, "configs", x + 9, ly + 7, 10, ColorUtil.theme());
            Render2D.text(ctx, name, x + 26, ly + 8, TEXT);
            float lx = x + w - 128;
            Render2D.roundedRect(ctx, lx, ly + 5, 58, 14, 7, ColorUtil.withAlpha(ColorUtil.theme(), 80));
            Render2D.centeredText(ctx, "Загрузить", lx + 29, ly + 8, TEXT);
            hits.add(new Hit(lx, ly + 5, 58, 14, b -> configStatus = cfg.loadProfile(name) ? "Загружен «" + name + "»" : "Не получилось загрузить"));
            float dx = x + w - 64;
            Render2D.roundedRect(ctx, dx, ly + 5, 56, 14, 7, 0x50FF453A);
            Render2D.centeredText(ctx, "Удалить", dx + 28, ly + 8, TEXT);
            hits.add(new Hit(dx, ly + 5, 56, 14, b -> {
                if (b == 1) configStatus = cfg.deleteProfile(name) ? "Удалён «" + name + "»" : "Не получилось";
                else configStatus = "Нажми «Удалить» правой кнопкой, чтобы подтвердить";
            }));
            ly += 28;
            if (ly > y + h - 24) break;
        }
    }

    // ------------------------------------------------------------------ palette

    /** Grid of theme presets plus the animation mode chips. */
    private void renderPalette(DrawContext ctx, float x, float y, float w, float h, float mx, float my) {
        Theme theme = DuckVisual.modules().get(Theme.class);
        header(ctx, x, y, w, "Палитра", "Цвета меню, HUD и эффектов · ПКМ по Custom — свои цвета");

        // mode segmented control
        String[] modes = {"Gradient", "Static", "Rainbow"};
        float segW = 0;
        for (String mode : modes) segW += Render2D.width(mode) * 0.8f + 14;
        float sx = x + w - segW - 2, sy = y + 2;
        Render2D.roundedRect(ctx, sx - 2, sy - 2, segW + 4, 16, 8, FIELD);
        for (String mode : modes) {
            float cw = Render2D.width(mode) * 0.8f + 14;
            boolean sel = theme.mode.is(mode);
            if (sel) Render2D.roundedRect(ctx, sx, sy, cw, 12, 6, 0x33FFFFFF);
            small(ctx, mode, sx + 7, sy + 3, 0.8f, sel ? TEXT : SUBTEXT);
            hits.add(new Hit(sx, sy, cw, 12, b -> theme.mode.set(mode)));
            sx += cw;
        }

        int cols = 5;
        float gap = 6, top = y + 32;
        float tw = (w - gap * (cols - 1)) / cols, th = 38;
        int n = Theme.PRESETS.length + 1;
        for (int i = 0; i < n; i++) {
            float tx = x + (i % cols) * (tw + gap), ty = top + (i / cols) * (th + gap);
            if (ty + th > y + h + 4) break;
            boolean custom = i == Theme.PRESETS.length;
            String name = custom ? "Custom" : Theme.PRESETS[i].name();
            int c1 = custom ? theme.first.get() | 0xFF000000 : Theme.PRESETS[i].first();
            int c2 = custom ? theme.second.get() | 0xFF000000 : Theme.PRESETS[i].second();
            boolean sel = theme.palette.is(name);
            boolean hov = over(mx, my, tx, ty, tw, th);
            Render2D.roundedRect(ctx, tx, ty, tw, th, 8, sel ? 0x22FFFFFF : hov ? CARD_HOVER : CARD);
            if (sel) Render2D.roundedOutline(ctx, tx, ty, tw, th, 8, 1, ColorUtil.withAlpha(c1, 200));
            Render2D.roundedGradient(ctx, tx + 4, ty + 4, tw - 8, 18, 6, c1, c2, c2, c1);
            if (sel) Render2D.centeredText(ctx, "✔", tx + tw / 2, ty + 9, 0xFF111111);
            small(ctx, name, tx + tw / 2 - Render2D.width(name) * 0.375f, ty + 26, 0.75f, sel ? TEXT : SUBTEXT);
            hits.add(new Hit(tx, ty, tw, th, b -> {
                theme.palette.set(name);
                if (custom && b == 1) {
                    // right click on Custom jumps to the Theme module to edit its colors
                    paletteTab = false;
                    current = theme.getCategory();
                    expanded.put(theme, true);
                }
            }));
        }
    }

    // ------------------------------------------------------------------ input

    private double lastMouseX, lastMouseY;

    /** Converts screen mouse coords into the scaled window space used while rendering. */
    private double[] toWindow(double mouseX, double mouseY) {
        float scale = (0.94f + 0.06f * Animation.easeOutCubic(openAnim)) * fitScale();
        float cx = windowX + W / 2f, cy = windowY + H / 2f;
        return new double[]{cx + (mouseX - cx) / scale, cy + (mouseY - cy) / scale};
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (closing) return true;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        if (binding != null || bindingSetting != null) {
            // middle / side mouse buttons can be bound; left and right click cancel
            if (button >= 2) applyBind(Keys.MOUSE + button);
            binding = null;
            bindingSetting = null;
            return true;
        }
        if (!insideWindow(mouseX, mouseY)) {
            searchFocused = false;
            configFocused = false;
            for (Module m : DuckVisual.modules().getModules()) {
                if (m instanceof HudModule hud && hud.isEnabled() && hud.isHovered(mouseX, mouseY) && button == 0) {
                    draggingHud = hud;
                    dragX = (float) (mouseX - hud.getX());
                    dragY = (float) (mouseY - hud.getY());
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        double[] p = toWindow(mouseX, mouseY);
        clickedSearch = false;
        clickedConfigField = false;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.inside(p[0], p[1])) {
                h.action.accept(button);
                if (activeSlider != null) mouseDragged(mouseX, mouseY, button, 0, 0);
                break;
            }
        }
        if (!clickedSearch) searchFocused = false;
        if (!clickedConfigField) configFocused = false;
        return true;
    }

    private void applyBind(int key) {
        if (bindingSetting != null) {
            bindingSetting.set(key);
        } else if (binding != null) {
            binding.setKey(key);
            if (binding instanceof ClickGui && (key == -1 || key >= Keys.MOUSE)) binding.setKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
        }
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingWindow = false;
        draggingHud = null;
        activeSlider = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        if (activeSlider != null) {
            double[] p = toWindow(mouseX, mouseY);
            activeSlider.setter.accept(MathHelper.clamp((float) ((p[0] - activeSlider.x) / activeSlider.w), 0f, 1f));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (insideWindow(mouseX, mouseY) && guideTab) {
            guideScroll += (float) vertical * 30;
            return true;
        }
        if (insideWindow(mouseX, mouseY)) {
            scroll.put(current, scroll.getOrDefault(current, 0f) + (float) vertical * 26);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (closing) return true;
        if (binding != null || bindingSetting != null) {
            applyBind(keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE
                    || keyCode == GLFW.GLFW_KEY_BACKSPACE ? -1 : keyCode);
            binding = null;
            bindingSetting = null;
            return true;
        }
        if (configFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !configName.isEmpty()) {
                configName = configName.substring(0, configName.length() - 1);
            } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                configFocused = false;
            }
            return true;
        }
        if (searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
                search = search.substring(0, search.length() - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) {
                searchFocused = false;
                return true;
            }
            return true;
        }
        // Ctrl+F focuses the search like in macOS apps
        if (keyCode == GLFW.GLFW_KEY_F && hasControlDown()) {
            searchFocused = true;
            return true;
        }
        int guiKey = DuckVisual.modules().get(ClickGui.class).getKey();
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == guiKey) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (configFocused) {
            if (configName.length() < 24 && (Character.isLetterOrDigit(chr) || chr == '_' || chr == '-') && chr < 128) configName += chr;
            return true;
        }
        if (searchFocused && search.length() < 16 && chr >= ' ') {
            search += chr;
            scroll.put(current, 0f);
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public void close() {
        if (closing) return;
        closing = true;
        closeFrom = openAnim;
        animStart = System.nanoTime();
        draggingWindow = false;
        draggingHud = null;
        activeSlider = null;
        searchFocused = false;
        binding = null;
        bindingSetting = null;
    }

    @Override
    public void removed() {
        Render2D.endBatch(null);
        DuckVisual.config().saveAsync();
    }
}
