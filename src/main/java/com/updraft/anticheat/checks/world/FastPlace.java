package com.updraft.anticheat.checks.world;

import com.updraft.anticheat.UpdraftAC;
import com.updraft.anticheat.checks.api.Check;
import com.updraft.anticheat.checks.api.CheckType;
import com.updraft.anticheat.data.PlayerData;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FastPlace: flags players who place blocks faster than the configured
 * {@code min-place-ms} between consecutive placements.
 */
public final class FastPlace extends Check {

    private final Map<UUID, Long> lastPlaceMs = new ConcurrentHashMap<>();

    public FastPlace(UpdraftAC plugin) { super(plugin, CheckType.FASTPLACE); }

    @Override
    public void onBukkitEvent(org.bukkit.event.Event event, PlayerData data) {
        if (!(event instanceof BlockPlaceEvent)) return;
        BlockPlaceEvent bpe = (BlockPlaceEvent) event;
        if (!bpe.getPlayer().getUniqueId().equals(data.uuid())) return;

        long now = System.currentTimeMillis();
        Long last = lastPlaceMs.get(data.uuid());
        if (last != null) {
            long elapsed = now - last;
            int minMs = plugin.config().checks().getInt("world.fastplace.min-place-ms", 50);
            if (elapsed < minMs) {
                fail(data, String.format("place-ms=%d min=%d", elapsed, minMs));
            }
        }
        lastPlaceMs.put(data.uuid(), now);
    }
}
