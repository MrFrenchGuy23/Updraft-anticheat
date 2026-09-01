package com.updraft.anticheat.punishment.lp;

import com.updraft.anticheat.UpdraftAC;
import com.updraft.anticheat.checks.api.Check;
import com.updraft.anticheat.data.PlayerData;
import com.updraft.anticheat.util.ChatUtil;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.track.Track;
import net.luckperms.api.track.DemotionResult;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Soft-dependency integration with LuckPerms 5.
 * <p>
 * On {@link #demote}, walks the configured LuckPerms {@code track} downward
 * (or runs the configured {@code demote-command} if the API is unavailable).
 * All work is dispatched onto LuckPerms' own async executor; no Bukkit thread
 * assumptions are made.
 */
public final class LuckPermsManager {

    private final UpdraftAC plugin;
    private final boolean enabled;
    private LuckPerms api;

    public LuckPermsManager(UpdraftAC plugin) {
        this.plugin = plugin;
        this.enabled = hook();
    }

    private boolean hook() {
        try {
            if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) return false;
            RegisteredServiceProvider<LuckPerms> rsp = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
            if (rsp != null) {
                api = rsp.getProvider();
                return api != null;
            }
            // fall back to static provider
            api = LuckPermsProvider.get();
            return api != null;
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "LuckPerms hook failed", t);
            return false;
        }
    }

    public boolean enabled() { return enabled; }
    public LuckPerms api() { return api; }

    /** Demote the player one step down the configured track (or run the configured console command). */
    public void demote(PlayerData data, Check check) {
        boolean cfgEnabled = plugin.config().punishments().getBoolean("luckperms.enabled", true);
        if (!enabled || !cfgEnabled) return;

        String commandTemplate = plugin.config().punishments().getString("luckperms.demote-command", "");
        if (!commandTemplate.isBlank()) {
            String formatted = ChatUtil.format(commandTemplate,
                    "player", data.name(),
                    "track", plugin.config().punishments().getString("luckperms.track", "anticheat"));
            String trimmed = formatted.trim();
            if (trimmed.startsWith("/")) trimmed = trimmed.substring(1);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), trimmed);
            return;
        }

        String trackName = plugin.config().punishments().getString("luckperms.track", "anticheat");
        UUID uuid = data.uuid();
        api.getTrackManager().loadTrack(trackName).thenComposeAsync(optTrack -> {
            if (optTrack.isEmpty()) return CompletableFuture.completedFuture(null);
            Track track = optTrack.get();
            return api.getUserManager().loadUser(uuid).thenAcceptAsync(user -> {
                if (user == null) return;
                try {
                    DemotionResult result = track.demote(user, api.getContextManager().getStaticContext());
                    // REMOVED_FROM_FIRST_GROUP = fell off the bottom of the track
                    if (result.getStatus() == DemotionResult.Status.REMOVED_FROM_FIRST_GROUP) {
                        String fallback = plugin.config().punishments().getString("luckperms.fallback-group", "default");
                        if (!fallback.isEmpty()) {
                            user.data().add(InheritanceNode.builder(fallback).build());
                            api.getUserManager().saveUser(user);
                        }
                    }
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.WARNING, "LuckPerms demote apply failed for " + data.name(), ex);
                }
            });
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "LuckPerms demote failed for " + data.name(), ex);
            return null;
        });
    }

    /** Convenience: return the player's primary group, or {@code default} on failure. */
    public String primaryGroup(Player player) {
        if (!enabled || player == null) return "default";
        try {
            User u = api.getUserManager().getUser(player.getUniqueId());
            return u == null ? "default" : u.getPrimaryGroup();
        } catch (Throwable ignored) {
            return "default";
        }
    }
}
