package com.updraft.anticheat.config;

import com.updraft.anticheat.UpdraftAC;
import com.updraft.anticheat.util.ChatUtil;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Strongly-typed accessor over {@code messages.yml}. Each method returns a fully
 * colored, formatted string ready to be sent to a receiver.
 */
public final class Messages {

    private final UpdraftAC plugin;

    public Messages(UpdraftAC plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration cfg() { return plugin.config().messages(); }

    public String prefix() { return ChatUtil.color(cfg().getString("prefix", "")); }

    public String withPrefix(String message) {
        return prefix() + ChatUtil.color(message);
    }

    // --- alerts ---
    public String alertHotbar(String player, String check, int vl, int maxVl, String color, int ping) {
        return ChatUtil.formatColor(cfg().getString("alert.hotbar", "%color%%player% failed %check%"),
                "player", player, "check", check, "vl", Integer.toString(vl),
                "maxvl", Integer.toString(maxVl), "color", color, "ping", Integer.toString(ping));
    }

    public String alertBossbar(String player, String check, int vl, String color) {
        return ChatUtil.formatColor(cfg().getString("alert.bossbar", "%color%%player% failed %check%"),
                "player", player, "check", check, "vl", Integer.toString(vl), "color", color);
    }

    public String alertConsole(String player, String check, int vl, int ping) {
        return ChatUtil.format(cfg().getString("alert.console", "[ALERT] %player% failed %check%"),
                "player", player, "check", check, "vl", Integer.toString(vl), "ping", Integer.toString(ping));
    }

    // --- commands ---
    public String cmd(String path, String... kv) {
        return withPrefix(cfg().getString("commands." + path, ""), kv);
    }

    public String raw(String path, String... kv) {
        return expandPrefix(ChatUtil.formatColor(cfg().getString(path, ""), kv));
    }

    public String system(String path, String... kv) {
        return withPrefix(cfg().getString("system." + path, ""), kv);
    }

    /** Expand {@code %prefix%} in a raw message and ensure a leading prefix is present exactly once. */
    private String withPrefix(String raw, String... kv) {
        String expanded = expandPrefix(ChatUtil.formatColor(raw, kv));
        return expanded.startsWith(prefix()) ? expanded : prefix() + expanded;
    }

    private String expandPrefix(String colored) {
        return colored.replace("%prefix%", prefix());
    }

    // frequently used constants
    public String noPermission() { return cmd("no-permission"); }
    public String playerOnly() { return cmd("player-only"); }
    public String playerNotFound(String target) { return cmd("player-not-found", "target", target); }
    public String reloadSuccess(long ms) { return cmd("reload-success", "time", Long.toString(ms)); }
}
