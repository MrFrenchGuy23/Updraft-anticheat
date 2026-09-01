package com.updraft.anticheat.checks.world;

import com.updraft.anticheat.UpdraftAC;
import com.updraft.anticheat.checks.api.Check;
import com.updraft.anticheat.checks.api.CheckType;
import com.updraft.anticheat.data.PlayerData;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FastBreak: flags players who break blocks faster than the configured
 * {@code min-break-ms} between consecutive breaks.
 */
public final class FastBreak extends Check {

    private final Map<UUID, Long> lastBreakMs = new ConcurrentHashMap<>();

    public FastBreak(UpdraftAC plugin) { super(plugin, CheckType.FASTBREAK); }

    @Override
    public void onBukkitEvent(org.bukkit.event.Event event, PlayerData data) {
        if (!(event instanceof BlockBreakEvent)) return;
        BlockBreakEvent bbe = (BlockBreakEvent) event;
        if (!bbe.getPlayer().getUniqueId().equals(data.uuid())) return;

        long now = System.currentTimeMillis();
        Long last = lastBreakMs.get(data.uuid());
        if (last != null) {
            long elapsed = now - last;
            int minMs = plugin.config().checks().getInt("world.fastbreak.min-break-ms", 50);
            if (elapsed < minMs) {
                fail(data, String.format("break-ms=%d min=%d", elapsed, minMs));
            }
        }
        lastBreakMs.put(data.uuid(), now);
    }
}
