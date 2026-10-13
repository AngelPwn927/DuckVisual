package dev.duckvisual.command;

import dev.duckvisual.DuckVisual;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.setting.ModeSetting;
import dev.duckvisual.setting.NumberSetting;
import dev.duckvisual.setting.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;

import java.util.Locale;

/** Client-side chat commands: messages starting with {@link #PREFIX} are handled here. */
public final class Commands {
    private Commands() {}

    public static final String PREFIX = ".";

    public static void execute(String line) {
        String[] a = line.trim().split("\\s+");
        if (a.length == 0 || a[0].isEmpty()) {
            help();
            return;
        }
        try {
            switch (a[0].toLowerCase(Locale.ROOT)) {
                case "help", "h" -> help();
                case "toggle", "t" -> toggle(a);
                case "bind", "b" -> bind(a);
                case "unbind" -> unbind(a);
                case "set", "s" -> set(a);
                case "config", "cfg", "c" -> config(a);
                case "modules", "list" -> list();
                default -> error("Unknown command. Type " + PREFIX + "help");
            }
        } catch (Exception e) {
            error("Error: " + e.getMessage());
        }
    }

    private static void help() {
        info("§6Duck Visual commands:");
        info(PREFIX + "toggle <module>");
        info(PREFIX + "bind <module> <key>   §7(e.g. " + PREFIX + "bind Zoom c)");
        info(PREFIX + "unbind <module>");
        info(PREFIX + "set <module> <setting> <value>");
        info(PREFIX + "config save|load|delete <name>, " + PREFIX + "config list");
        info(PREFIX + "modules");
    }

    private static Module module(String name) {
        Module m = DuckVisual.modules().get(name);
        if (m == null) throw new IllegalArgumentException("no module '" + name + "'");
        return m;
    }

    private static void toggle(String[] a) {
        Module m = module(a[1]);
        m.toggle();
        info(m.getName() + (m.isEnabled() ? " §aenabled" : " §cdisabled"));
    }

    private static void bind(String[] a) {
        Module m = module(a[1]);
        String key = a[2].toLowerCase(Locale.ROOT);
        InputUtil.Key k = InputUtil.fromTranslationKey("key.keyboard." + key);
        if (k == InputUtil.UNKNOWN_KEY) throw new IllegalArgumentException("unknown key '" + a[2] + "'");
        m.setKey(k.getCode());
        DuckVisual.config().saveAsync();
        info(m.getName() + " bound to §e" + a[2].toUpperCase(Locale.ROOT));
    }

    private static void unbind(String[] a) {
        Module m = module(a[1]);
        m.setKey(-1);
        DuckVisual.config().saveAsync();
        info(m.getName() + " unbound");
    }

    private static void set(String[] a) {
        Module m = module(a[1]);
        // setting names may contain spaces: everything between module and value
        StringBuilder sn = new StringBuilder();
        for (int i = 2; i < a.length - 1; i++) sn.append(i > 2 ? " " : "").append(a[i]);
        String value = a[a.length - 1];
        for (Setting<?> s : m.getSettings()) {
            if (!s.getName().equalsIgnoreCase(sn.toString())) continue;
            if (s instanceof BooleanSetting b) b.set(Boolean.parseBoolean(value) || value.equals("on") || value.equals("1"));
            else if (s instanceof NumberSetting n) n.set(Double.parseDouble(value));
            else if (s instanceof ModeSetting mo) {
                String found = null;
                for (String mode : mo.getModes()) if (mode.equalsIgnoreCase(value)) found = mode;
                if (found == null) throw new IllegalArgumentException("modes: " + String.join(", ", mo.getModes()));
                mo.set(found);
            } else throw new IllegalArgumentException("this setting can only be changed in the GUI");
            DuckVisual.config().saveAsync();
            info(m.getName() + " §7" + s.getName() + " §f= §e" + s.get());
            return;
        }
        throw new IllegalArgumentException("no setting '" + sn + "' in " + m.getName());
    }

    private static void config(String[] a) {
        var cfg = DuckVisual.config();
        switch (a[1].toLowerCase(Locale.ROOT)) {
            case "save" -> info(cfg.saveProfile(a[2]) ? "Saved config §e" + a[2] : "§cCould not save (name: letters, digits, _ -)");
            case "load" -> info(cfg.loadProfile(a[2]) ? "Loaded config §e" + a[2] : "§cNo config '" + a[2] + "'");
            case "delete", "remove" -> info(cfg.deleteProfile(a[2]) ? "Deleted §e" + a[2] : "§cNo config '" + a[2] + "'");
            case "list" -> {
                var list = cfg.listProfiles();
                info(list.isEmpty() ? "No saved configs" : "Configs: §e" + String.join("§7, §e", list));
            }
            default -> error("Usage: " + PREFIX + "config save|load|delete <name> / list");
        }
    }

    private static void list() {
        StringBuilder sb = new StringBuilder();
        for (Module m : DuckVisual.modules().getModules()) {
            if (sb.length() > 0) sb.append("§7, ");
            sb.append(m.isEnabled() ? "§a" : "§f").append(m.getName());
        }
        info(sb.toString());
    }

    public static void info(String msg) {
        MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(Text.literal("§6[Duck] §r" + msg));
    }

    private static void error(String msg) {
        info("§c" + msg);
    }
}
