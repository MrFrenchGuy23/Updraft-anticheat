package com.updraft.anticheat.punishment;

import com.updraft.anticheat.UpdraftAC;
import com.updraft.anticheat.checks.api.Check;
import com.updraft.anticheat.data.PlayerData;
import com.updraft.anticheat.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * Implements each {@link Action}. Kept separate from {@link PunishmentManager}
 * so individual actions are easy to unit-test and extend.
 */
final class ActionExecutor {

    private final UpdraftAC plugin;

    ActionExecutor(UpdraftAC plugin) { this.plugin = plugin; }

    void execute(Action action, Check check, PlayerData data, int vl, String detail) {
        Player player = data.player();
        if (player == null || !player.isOnline()) return;
        switch (action) {
            case CANCEL -> data.requestCancel();
            case WARN -> player.sendMessage(PunishmentManager.warnMessage(plugin, check, vl, data.ping()));
            case ALERT -> { /* handled by AlertManager; listed for completeness */ }
            case KICK -> player.kickPlayer(PunishmentManager.kickMessage(plugin, check, vl, data.ping()));
            case BAN_COMMAND -> runBanCommands(check, data, vl);
            case LP_DEMOTE -> plugin.luckPerms().demote(data, check);
        }
    }

    private void runBanCommands(Check check, PlayerData data, int vl) {
        List<String> cmds = plugin.config().punishments().getStringList("ban-commands");
        if (cmds.isEmpty()) return;
        for (String raw : cmds) {
            String formatted = ChatUtil.format(raw,
                    "player", data.name(),
                    "uuid", data.uuid().toString(),
                    "check", check.shortId(),
                    "check-display", check.type().display(),
                    "vl", Integer.toString(vl),
                    "ping", Integer.toString(data.ping()));
            // strip leading '/' if present — Bukkit dispatchCommand doesn't want it
            String trimmed = formatted.trim();
            if (trimmed.startsWith("/")) trimmed = trimmed.substring(1);
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), trimmed);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to run ban command '" + trimmed + "': " + e.getMessage());
            }
        }
    }
}
