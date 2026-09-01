package com.updraft.anticheat.checks.combat;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import com.updraft.anticheat.UpdraftAC;
import com.updraft.anticheat.checks.api.Check;
import com.updraft.anticheat.checks.api.CheckType;
import com.updraft.anticheat.data.PlayerData;
import com.updraft.anticheat.util.MathUtil;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerVelocityEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Velocity: flags players who don't take the expected knockback from a hit.
 * <p>
 * Tracks the last velocity applied to a player (from {@code PlayerVelocityEvent})
 * and compares it against the observed movement over the next few ticks. If the
 * ratio of observed vs. expected velocity falls below the configured
 * {@code min-accepted-percent}, the player is flagged.
 */
public final class Velocity extends Check {

    /** Last expected velocity (vertical, blocks/tick), set on the main thread from the Bukkit event. */
    private final Map<UUID, Double> expectedVy = new ConcurrentHashMap<>();
    /** Ticks remaining to observe before clearing. */
    private final Map<UUID, Integer> observeTicks = new ConcurrentHashMap<>();

    public Velocity(UpdraftAC plugin) { super(plugin, CheckType.VELOCITY); }

    /**
     * Knockback is only applied to players who can be velocity-affected, so the
     * generic context exemption (which covers velocity/takeoff) must not apply
     * here — otherwise the check can never run.
     */
    @Override
    public boolean ignoreContextExemption() { return true; }

    @Override
    public void onBukkitEvent(org.bukkit.event.Event event, PlayerData data) {
        if (!(event instanceof PlayerVelocityEvent)) return;
        PlayerVelocityEvent ve = (PlayerVelocityEvent) event;
        if (ve.getPlayer().getUniqueId().equals(data.uuid())) {
            org.bukkit.util.Vector vel = ve.getVelocity();
            expectedVy.put(data.uuid(), vel.getY());
            observeTicks.put(data.uuid(), 5); // observe for 5 ticks
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event, PlayerData data) {
        Integer ticks = observeTicks.get(data.uuid());
        if (ticks == null || ticks <= 0) return;

        Double expected = expectedVy.get(data.uuid());
        if (expected == null || expected == 0.0) {
            observeTicks.remove(data.uuid());
            expectedVy.remove(data.uuid());
            return;
        }

        // Compare actual vertical movement this tick vs expected
        double actualDy = data.dy();
        // The player is being observed right after velocity was applied
        double ratio = actualDy / Math.abs(expected);

        int remaining = ticks - 1;
        observeTicks.put(data.uuid(), remaining);

        double minPercent = plugin.config().checks()
                .getDouble("combat.velocity.min-accepted-percent", 70.0) / 100.0;
        if (ratio < minPercent && Math.abs(expected) > 0.1) {
            fail(data, String.format("ratio=%.2f expected=%.3f actual=%.3f", ratio, expected, actualDy));
            // clear after flagging to avoid double-flag
            observeTicks.remove(data.uuid());
            expectedVy.remove(data.uuid());
        }
    }
}
