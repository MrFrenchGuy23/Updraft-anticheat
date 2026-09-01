package com.updraft.anticheat.checks.world;

import com.updraft.anticheat.UpdraftAC;
import com.updraft.anticheat.checks.api.Check;
import com.updraft.anticheat.checks.api.CheckType;
import com.updraft.anticheat.data.PlayerData;
import com.updraft.anticheat.util.MathUtil;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.util.Vector;

/**
 * Scaffold: flags players who place blocks under themselves while moving,
 * characterized by suspicious pitch snapping (looking straight down) between
 * movement and placement packets.
 */
public final class Scaffold extends Check {

    private final java.util.Map<java.util.UUID, Float> lastPitch = new java.util.concurrent.ConcurrentHashMap<>();

    public Scaffold(UpdraftAC plugin) { super(plugin, CheckType.SCAFFOLD); }

    @Override
    public void onTick(PlayerData data) {
        lastPitch.put(data.uuid(), data.pitch());
    }

    @Override
    public void onBukkitEvent(org.bukkit.event.Event event, PlayerData data) {
        if (!(event instanceof BlockPlaceEvent)) return;
        BlockPlaceEvent bpe = (BlockPlaceEvent) event;
        if (!bpe.getPlayer().getUniqueId().equals(data.uuid())) return;

        Float prev = lastPitch.get(data.uuid());
        if (prev == null) return;

        double pitchChange = Math.abs(data.pitch() - prev);
        double suspiciousChange = plugin.config().checks()
                .getDouble("world.scaffold.suspicious-pitch-change", 60.0);

        // Scaffold snaps pitch to ~90 (looking straight down)
        boolean snappingDown = data.pitch() > 70.0 && prev < 40.0;
        if (snappingDown && pitchChange > suspiciousChange) {
            fail(data, String.format("pitch-change=%.1f snap-down", pitchChange));
        }
    }
}
