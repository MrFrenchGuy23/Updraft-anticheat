package com.updraft.anticheat.punishment;

import com.updraft.anticheat.UpdraftAC;
import com.updraft.anticheat.api.PlayerPunishEvent;
import com.updraft.anticheat.checks.api.Check;
import com.updraft.anticheat.data.PlayerData;
import com.updraft.anticheat.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.logging.Level;

/**
 * Executes the action list resolved from a check's {@code action-tiers}.
 * <p>
 * Heavy actions (kick, ban command, LP demote) are scheduled on the main thread
 * because they touch Bukkit APIs that are not thread-safe. The {@code CANCEL}
 * and {@code WARN} actions are applied inline if the caller is already on the
 * main thread, otherwise deferred.
 */
public final class PunishmentManager {

    private final UpdraftAC plugin;
    private ActionExecutor executor;

    public PunishmentManager(UpdraftAC plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        this.executor = new ActionExecutor(plugin);
    }

    public ActionExecutor executor() { return executor; }

    /**
     * Run the given action list for a check/player combination. Cooldowns and
     * cancellable events are respected.
     */
    public void execute(Check check, PlayerData data, List<String> actions, int vl, String detail) {
        if (actions == null || actions.isEmpty()) return;

        // Everything (cooldown, cancellable event, actions) runs on the main
        // thread so Bukkit events are always fired on the main thread.
        Runnable task = () -> {
            // cooldown check
            long cooldown = plugin.config().punishments().getLong("cooldown-ms", 3000);
            long now = System.currentTimeMillis();
            if (now - data.lastPunishMs(check.shortId()) < cooldown) return;
            data.lastPunishMs(check.shortId(), now);

            // fire cancellable API event
            PlayerPunishEvent event = new PlayerPunishEvent(data.player(), check.type(), vl, actions);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) return;

            for (String raw : actions) {
                Action a = Action.parse(raw);
                if (a == null) continue;
                try {
                    executor.execute(a, check, data, vl, detail);
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.WARNING,
                            "Action " + a + " failed for " + data.name(), t);
                }
            }
            // log to DB
            if (plugin.storage().isEnabled()) {
                String serverId = plugin.config().config().getString("general.server-id", "");
                for (String raw : actions) {
                    plugin.storage().insertPunishment(
                            new com.updraft.anticheat.data.storage.DatabaseManager.PunishmentRecord(
                                    data.uuid().toString(),
                                    data.name(),
                                    check.shortId(),
                                    raw,
                                    vl,
                                    serverId,
                                    now));
                }
            }
        };

        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /** Apply a kick message template with placeholders. */
    public static String kickMessage(UpdraftAC plugin, Check check, int vl, int ping) {
        String template = plugin.config().punishments().getString("kick.message",
                "&cDisconnected.\n&7Reason: Unfair advantage (%check%)");
        return ChatUtil.formatColor(template,
                "player", check.type().display(),
                "check", check.shortId(),
                "vl", Integer.toString(vl),
                "ping", Integer.toString(ping));
    }

    /** Apply a warn message template with placeholders. */
    public static String warnMessage(UpdraftAC plugin, Check check, int vl, int ping) {
        String template = plugin.config().punishments().getString("warn.message",
                "&c[AC] You were flagged for %check% (VL %vl%)");
        return ChatUtil.formatColor(template,
                "player", "",
                "check", check.shortId(),
                "vl", Integer.toString(vl),
                "ping", Integer.toString(ping));
    }
}
