package com.updraft.anticheat.alert;

import com.updraft.anticheat.UpdraftAC;
import com.updraft.anticheat.checks.api.Check;
import com.updraft.anticheat.data.PlayerData;
import com.updraft.anticheat.util.ChatUtil;
import com.updraft.anticheat.util.PermissionUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Routes alerts to staff (hotbar + bossbar + console) and applies per-player
 * alert toggles. Color tiers are derived from the player's VL bucket.
 */
public final class AlertManager {

    private final UpdraftAC plugin;
    private final BossBarAlertRenderer bossBar;
    private final HotbarAlertRenderer hotbar;
    private final ConcurrentHashMap<UUID, Boolean> alertToggles = new ConcurrentHashMap<>();

    public AlertManager(UpdraftAC plugin) {
        this.plugin = plugin;
        this.bossBar = new BossBarAlertRenderer(plugin);
        this.hotbar = new HotbarAlertRenderer(plugin);
    }

    public void reload() {
        bossBar.reload();
        hotbar.reload();
    }

    /** Called by the violation pipeline when a check fails. */
    public void flag(Check check, PlayerData data, int vl, String detail) {
        String color = colorFor(vl);
        try {
            // 1. console
            plugin.getLogger().info(plugin.messages().alertConsole(
                    data.name(), check.shortId(), vl, data.ping()));
        } catch (Throwable ignored) {}

        // 2. hotbar + bossbar to all alert recipients
        String hotbarMsg = plugin.messages().alertHotbar(
                data.name(), check.shortId(), vl, check.settings().maxVl(), color, data.ping());
        String bossbarMsg = plugin.messages().alertBossbar(
                data.name(), check.shortId(), vl, color);

        Runnable broadcast = () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!PermissionUtil.canSeeAlerts(p)) continue;
                if (!alertsEnabled(p.getUniqueId())) continue;
                if (hotbar.enabled()) hotbar.send(p, hotbarMsg);
            }
            if (bossBar.enabled()) bossBar.show(bossbarMsg, color);
        };
        if (Bukkit.isPrimaryThread()) broadcast.run();
        else Bukkit.getScheduler().runTask(plugin, broadcast);
    }

    /** Color name per VL bucket from config (e.g. {@code YELLOW} / {@code GOLD} / {@code RED}). */
    private String colorFor(int vl) {
        String tier = vl >= 25 ? "high" : vl >= 10 ? "medium" : "low";
        String name = plugin.config().config().getString("alerts.colors." + tier, "YELLOW");
        try {
            return "" + ChatColor.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return "" + ChatColor.YELLOW;
        }
    }

    // ----- per-player toggles -----
    public boolean alertsEnabled(UUID uuid) {
        return alertToggles.getOrDefault(uuid, true);
    }

    public boolean toggle(UUID uuid) {
        boolean next = !alertsEnabled(uuid);
        alertToggles.put(uuid, next);
        return next;
    }

    public void setEnabled(UUID uuid, boolean enabled) {
        alertToggles.put(uuid, enabled);
    }

    public BossBarAlertRenderer bossBar() { return bossBar; }
    public HotbarAlertRenderer hotbar() { return hotbar; }
}
