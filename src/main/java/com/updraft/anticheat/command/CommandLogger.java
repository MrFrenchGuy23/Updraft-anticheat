package com.updraft.anticheat.command;

import com.updraft.anticheat.UpdraftAC;
import com.updraft.anticheat.checks.api.Check;
import com.updraft.anticheat.checks.client.CommandCheck;
import com.updraft.anticheat.data.PlayerData;
import com.updraft.anticheat.util.PermissionUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.List;
import java.util.Locale;

/**
 * Command logger + flagger.
 * <p>
 * Hooks {@link PlayerCommandPreprocessEvent} to:
 * <ol>
 *   <li>Persist executed commands to the {@code up_commands} table (unless
 *       ignored or logging is disabled).</li>
 *   <li>Flag commands matching {@code client.command.flagged-commands} via
 *       {@link CommandCheck} so they flow through the normal VL/alerts/
 *       punishment pipeline.</li>
 * </ol>
 * Bukkit events are synchronous and run on the main thread.
 */
public final class CommandLogger implements Listener {

    private final UpdraftAC plugin;

    public CommandLogger(UpdraftAC plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.config().config().getBoolean("command-logger.enabled", true)) return;

        Player player = event.getPlayer();
        PlayerData data = plugin.playerData().get(player);
        if (data == null) return;

        String raw = event.getMessage();
        if (raw == null || raw.isEmpty()) return;

        String command = raw.startsWith("/") ? raw.substring(1) : raw;
        boolean ignored = isIgnored(command);
        boolean flagged = !ignored && isFlagged(command);

        // 1. persist (unless ignored or the server only logs flagged commands)
        if (!ignored && (flagged || plugin.config().config()
                .getBoolean("command-logger.log-all-commands", true))) {
            logCommand(data, raw, player);
        }

        // 2. flag suspicious commands through the normal violation pipeline
        if (flagged && !PermissionUtil.isExempt(player, "command")) {
            Check check = plugin.checks().get("command");
            if (check instanceof CommandCheck && check.enabled()) {
                ((CommandCheck) check).flag(data, command);
            }
        }
    }

    private void logCommand(PlayerData data, String raw, Player player) {
        if (!plugin.storage().isEnabled()) return;
        String stored = raw;
        int maxLen = plugin.config().config().getInt("command-logger.max-command-length", 200);
        if (stored.length() > maxLen) stored = stored.substring(0, maxLen);
        String serverId = plugin.config().config().getString("general.server-id", "");
        plugin.storage().insertCommand(new com.updraft.anticheat.data.storage.DatabaseManager.CommandRecord(
                data.uuid().toString(),
                data.name(),
                stored,
                player.getWorld().getName(),
                serverId,
                System.currentTimeMillis()));
    }

    /** Prefix match against {@code command-logger.ignored}, case-insensitive. */
    private boolean isIgnored(String command) {
        List<String> ignored = plugin.config().config().getStringList("command-logger.ignored");
        String lower = command.toLowerCase(Locale.ROOT);
        for (String s : ignored) {
            if (matches(lower, s)) return true;
        }
        return false;
    }

    /** Prefix match against {@code client.command.flagged-commands}, case-insensitive. */
    private boolean isFlagged(String command) {
        List<String> flagged = plugin.config().checks().getStringList("client.command.flagged-commands");
        String lower = command.toLowerCase(Locale.ROOT);
        for (String s : flagged) {
            if (matches(lower, s)) return true;
        }
        return false;
    }

    /** Match a command (no leading '/') against a configured entry, e.g. {@code /op}. */
    private static boolean matches(String lowerCommand, String rawEntry) {
        if (rawEntry == null) return false;
        String norm = rawEntry.trim().toLowerCase(Locale.ROOT);
        if (norm.startsWith("/")) norm = norm.substring(1);
        if (norm.isEmpty()) return false;
        return lowerCommand.equals(norm) || lowerCommand.startsWith(norm + " ");
    }
}
