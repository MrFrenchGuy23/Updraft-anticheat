package com.updraft.anticheat.checks.world;

import com.updraft.anticheat.UpdraftAC;
import com.updraft.anticheat.checks.api.Check;
import com.updraft.anticheat.checks.api.CheckType;
import com.updraft.anticheat.data.PlayerData;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Tower: flags players who place blocks rapidly while jumping (the classic
 * tower/scaffold tower pattern).
 * <p>
 * Detects a pattern of: block place → immediate upward movement → block place,
 * occurring faster than a human could reasonably do it.
 */
public final class Tower extends Check {

    private final java.util.Map<java.util.UUID, Long> lastPlaceMs = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, Boolean> wasJumping = new java.util.concurrent.ConcurrentHashMap<>();

    public Tower(UpdraftAC plugin) { super(plugin, CheckType.TOWER); }

    @Override
    public void onTick(PlayerData data) {
        // Track if player is jumping (positive dy while on ground last tick)
        wasJumping.put(data.uuid(), data.dy() > 0.3 && data.lastOnGround());
    }

    @Override
    public void onBukkitEvent(org.bukkit.event.Event event, PlayerData data) {
        if (!(event instanceof BlockPlaceEvent)) return;
        BlockPlaceEvent bpe = (BlockPlaceEvent) event;
        if (!bpe.getPlayer().getUniqueId().equals(data.uuid())) return;

        // Must be jumping upward while placing
        Boolean jumping = wasJumping.getOrDefault(data.uuid(), false);
        if (!jumping) return;

        // Check place-under-self: block was placed at player's feet level
        if (bpe.getBlockPlaced().getY() < data.y() && bpe.getBlockPlaced().getY() >= data.y() - 1.5) {
            long now = System.currentTimeMillis();
            Long last = lastPlaceMs.get(data.uuid());
            if (last != null && (now - last) < 150) {
                fail(data, "rapid-tower");
            }
            lastPlaceMs.put(data.uuid(), now);
        }
    }
}
