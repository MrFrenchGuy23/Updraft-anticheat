package com.updraft.anticheat.checks.api;

import com.updraft.anticheat.UpdraftAC;
import com.updraft.anticheat.data.PlayerData;
import com.updraft.anticheat.util.BlockUtil;
import com.updraft.anticheat.util.PermissionUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Snapshot of the context a check runs in for a single event/tick.
 * <p>
 * Centralizes exemption logic so individual checks don't have to repeat the
 * "is the player just-teleported/flying/lagging?" boilerplate.
 */
public final class CheckContext {

    private final UpdraftAC plugin;
    private final PlayerData data;
    private final Player player;

    public CheckContext(UpdraftAC plugin, PlayerData data) {
        this.plugin = plugin;
        this.data = data;
        this.player = data.player();
    }

    public UpdraftAC plugin() { return plugin; }
    public PlayerData data() { return data; }
    public Player player() { return player; }
    public Location location() { return player == null ? null : player.getLocation(); }

    /** True if any contextual reason makes this player exempt from checks right now. */
    public boolean isExempt() {
        if (player == null) return true;
        if (!plugin.isAnticheatEnabled()) return true;

        long now = System.currentTimeMillis();
        long teleport = plugin.config().config().getLong("performance.teleport-exemption-ms", 2000);
        long damage = plugin.config().config().getLong("performance.damage-exemption-ms", 800);
        int maxPing = plugin.config().config().getInt("performance.max-ping-ms", 350);
        double minTps = plugin.config().config().getDouble("performance.min-tps", 19.0);

        if (now - data.lastTeleportMs() < teleport) return true;
        if (now - data.lastDamageMs() < damage) return true;
        if (now - data.lastVelocityMs() < damage) return true;
        if (now - data.lastFlightToggleMs() < teleport) return true;
        if (data.allowFlight() || data.flying()) return true;
        if (data.gliding()) return true;
        if (data.riptiding()) return true;
        if (data.inVehicle()) return true;
        if (data.ping() > maxPing) return true;
        if (plugin.lag() != null && plugin.lag().tps() < minTps) return true;
        // Standing in climbable invalidates vertical checks elsewhere; treat as exempt here.
        if (player.isDead() || player.getHealth() <= 0.0) return true;
        return false;
    }

    /** Permission-based exemption for a specific check id. */
    public boolean isExemptByPermission(String checkId) {
        return PermissionUtil.isExempt(player, checkId);
    }

    /** Convenience: true if the player is currently standing on/near a climbable block. */
    public boolean nearClimbable() {
        Location l = location();
        return l != null && BlockUtil.isClimbable(l.getBlock().getType());
    }
}
