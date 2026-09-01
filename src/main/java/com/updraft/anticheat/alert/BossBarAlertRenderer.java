package com.updraft.anticheat.alert;

import com.updraft.anticheat.UpdraftAC;
import com.updraft.anticheat.util.PermissionUtil;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maintains a single shared bossbar shown to staff for {@code visible-ticks}
 * ticks, replacing the previous bar when a new alert arrives. This is much
 * cheaper than one bossbar per alert and keeps the UI uncluttered.
 */
public final class BossBarAlertRenderer {

    private final UpdraftAC plugin;
    private final ConcurrentHashMap<UUID, BossBar> bars = new ConcurrentHashMap<>();
    private boolean enabled;
    private BukkitTask clearTask;
    private long visibleTicks;

    public BossBarAlertRenderer(UpdraftAC plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        this.enabled = plugin.config().config().getBoolean("alerts.bossbar-enabled", true);
        this.visibleTicks = plugin.config().config().getLong("alerts.bossbar-visible-ticks", 80L);
        if (!enabled) hideAll();
    }

    public boolean enabled() { return enabled; }

    /** Show the message to every staff member, hiding after {@code visibleTicks}. */
    public void show(String message, String color) {
        if (!enabled) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            BarColor bc = parseColor(color);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!PermissionUtil.canSeeAlerts(p)) continue;
                if (!plugin.alerts().alertsEnabled(p.getUniqueId())) continue;
                BossBar bar = bars.get(p.getUniqueId());
                if (bar == null) {
                    bar = Bukkit.createBossBar(message, bc, BarStyle.SEGMENTED_10);
                    bar.addPlayer(p);
                    bar.setProgress(1.0);
                    bars.put(p.getUniqueId(), bar);
                } else {
                    bar.setTitle(message);
                    bar.setColor(bc);
                    bar.setVisible(true);
                }
            }
            scheduleClear();
        });
    }

    private void scheduleClear() {
        if (clearTask != null) clearTask.cancel();
        clearTask = Bukkit.getScheduler().runTaskLater(plugin, this::hideAll, visibleTicks);
    }

    public void hideAll() {
        for (Iterator<BossBar> it = bars.values().iterator(); it.hasNext(); ) {
            BossBar bar = it.next();
            bar.setVisible(false);
            bar.removeAll();
            it.remove();
        }
    }

    private BarColor parseColor(String color) {
        if (color == null || color.isEmpty()) return BarColor.YELLOW;
        // color is a ChatColor string like '§c'; map by first character
        char c = color.charAt(color.length() - 1);
        switch (c) {
            case 'c': case '4': return BarColor.RED;
            case 'e': case '6': return BarColor.YELLOW;
            case 'a': case '2': return BarColor.GREEN;
            case 'b': case '3': return BarColor.BLUE;
            case 'd': case '5': return BarColor.PURPLE;
            default: return BarColor.YELLOW;
        }
    }
}
