package com.updraft.anticheat.checks.api;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.updraft.anticheat.UpdraftAC;
import com.updraft.anticheat.config.CheckSettings;
import com.updraft.anticheat.data.PlayerData;
import org.bukkit.event.Event;

/**
 * Base class for every Updraft check.
 * <p>
 * A check declares its {@link CheckType} and implements the {@code handle*}
 * hooks it cares about. When a check detects something, it calls
 * {@link #fail(PlayerData, String)} with a short detail string; the
 * {@link com.updraft.anticheat.checks.manager.ViolationManager ViolationManager}
 * then takes care of VL bookkeeping, punishment, alerts, and logging.
 */
public abstract class Check {

    protected final UpdraftAC plugin;
    protected final CheckType type;
    protected CheckSettings settings;

    protected Check(UpdraftAC plugin, CheckType type) {
        this.plugin = plugin;
        this.type = type;
    }

    /** Stable identifier (e.g. {@code combat.killaura}). */
    public final String id() { return type.fullId(); }

    /** Short identifier (e.g. {@code killaura}). */
    public final String shortId() { return type.id(); }

    public final CheckType type() { return type; }

    public final Category category() { return type.category(); }

    /** Reload from config. Called on plugin enable and {@code /updraft reload}. */
    public final void reload() {
        settings = plugin.defaultSettings().forCheck(plugin.config().checks(),
                category().name().toLowerCase(), shortId());
        dataAppliedToAll();
    }

    /** Push the resolved settings onto a player's data cache (for fast lookup). */
    private void dataAppliedToAll() {
        for (PlayerData pd : plugin.playerData().all()) {
            pd.settings(shortId(), settings);
        }
    }

    public final CheckSettings settings() { return settings; }

    public final boolean enabled() { return settings != null && settings.isEnabled() && plugin.isAnticheatEnabled(); }

    // ===================== Hooks =====================

    /** Called for every inbound packet. Override when you need raw packet data. */
    public void onPacketReceive(PacketReceiveEvent event, PlayerData data) {}

    /** Called for every outbound packet. Override for packet-send checks (rare). */
    public void onPacketSend(PacketSendEvent event, PlayerData data) {}

    /** Called once per server tick (synchronously) for each online player. */
    public void onTick(PlayerData data) {}

    /**
     * Return true for checks that must run even when the player is contextually
     * exempt (e.g. {@code velocity} observes knockback the exemption is designed
     * to suppress). Default false — exempt players skip all check hooks.
     */
    public boolean ignoreContextExemption() { return false; }

    /** Generic Bukkit event hook. The {@link com.updraft.anticheat.bukkit.BukkitListener}
     *  dispatches events of interest here. */
    public void onBukkitEvent(Event event, PlayerData data) {}

    // ===================== Failures =====================

    /**
     * Report a violation. Returns true if the violation was accepted (not on cooldown).
     */
    public final boolean fail(PlayerData data, String detail) {
        return plugin.checks().violations().report(this, data, detail);
    }

    /**
     * Convenience: fail with no detail message.
     */
    public final boolean fail(PlayerData data) {
        return fail(data, "");
    }
}
