package com.updraft.anticheat.checks.manager;

import com.updraft.anticheat.UpdraftAC;
import com.updraft.anticheat.api.PlayerViolationEvent;
import com.updraft.anticheat.checks.api.Check;
import com.updraft.anticheat.config.CheckSettings;
import com.updraft.anticheat.data.PlayerData;
import com.updraft.anticheat.util.PermissionUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Coordinates the "check failed" pipeline:
 * <ol>
 *   <li>Add VL (respecting max + decay rules)</li>
 *   <li>Resolve the active action tier</li>
 *   <li>Fire {@link PlayerViolationEvent} (cancellable)</li>
 *   <li>Run punishments via {@link com.updraft.anticheat.punishment.PunishmentManager}</li>
 *   <li>Send alerts + Discord webhook</li>
 *   <li>Persist a {@code ViolationRecord} to storage</li>
 * </ol>
 *
 * Thread-safety: invoked on the packet thread by default; heavy actions
 * (kick, ban command, LP demote) are scheduled on the main thread.
 */
public final class ViolationManager {

    private final UpdraftAC plugin;

    public ViolationManager(UpdraftAC plugin) { this.plugin = plugin; }

    /**
     * Report a violation. Returns false if suppressed (cooldown, bypass, or cancelled event).
     * <p>
     * VL bookkeeping happens on the calling thread (cheap + thread-safe); the
     * cancellable API event and every downstream action (punishments, alerts,
     * webhook, logging) are executed synchronously on the main thread so Bukkit
     * events are always fired on the main thread.
     */
    public boolean report(Check check, PlayerData data, String detail) {
        if (!check.enabled()) return false;

        // permission bypass: count but don't punish
        boolean bypass = PermissionUtil.isBypass(data.player(), check.shortId());
        if (bypass && !plugin.config().config().getBoolean("general.count-bypass-violations", true)) {
            return false;
        }

        long now = System.currentTimeMillis();
        long cooldown = plugin.config().config().getLong("alerts.cooldown-ms", 600);
        if (now - data.lastFlagMs(check.shortId()) < cooldown) {
            return false;
        }
        data.lastFlagMs(check.shortId(), now);

        // ---- VL bookkeeping ----
        CheckSettings settings = check.settings();
        double previous = data.vl(check.shortId());
        double current = Math.min(settings.maxVl(), previous + 1.0);
        data.vl(check.shortId(), current);
        int vlInt = (int) Math.round(current);

        boolean bypassFinal = bypass;
        double previousFinal = previous;
        Runnable pipeline = () -> {
            // ---- fire the cancellable API event ----
            PlayerViolationEvent event = new PlayerViolationEvent(
                    data.player(), check.type(), vlInt, detail, bypassFinal);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                data.vl(check.shortId(), Math.max(0.0, previousFinal));
                return;
            }

            // ---- actions (if not bypassing) ----
            List<String> actions = settings.actionsFor(vlInt);
            if (!actions.isEmpty() && !bypassFinal) {
                plugin.punishments().execute(check, data, actions, vlInt, detail);
            }

            // ---- alerts + webhook + log (always, unless bypassing) ----
            if (!bypassFinal) {
                plugin.alerts().flag(check, data, vlInt, detail);
                plugin.webhook().onViolation(check, data, vlInt, actions);
            }

            // ---- persist ----
            if (plugin.storage().isEnabled()) {
                String serverId = plugin.config().config().getString("general.server-id", "");
                plugin.storage().insertViolation(new com.updraft.anticheat.data.storage.DatabaseManager.ViolationRecord(
                        data.uuid().toString(),
                        data.name(),
                        check.shortId(),
                        check.category().name(),
                        current,
                        data.ping(),
                        serverId,
                        detail,
                        now));
            }
        };

        try {
            if (Bukkit.isPrimaryThread()) pipeline.run();
            else Bukkit.getScheduler().runTask(plugin, pipeline);
        } catch (IllegalStateException e) {
            // plugin is shutting down — nothing left to do
        }
        return true;
    }

    /** Per-second decay applied by the periodic task. */
    public void decay(PlayerData data) {
        for (Check check : plugin.checks().all()) {
            if (check.settings() == null) continue;
            double decay = check.settings().decay();
            if (decay <= 0) continue;
            double vl = data.vl(check.shortId());
            if (vl <= 0) continue;
            data.vl(check.shortId(), Math.max(0, vl - decay));
        }
    }
}
