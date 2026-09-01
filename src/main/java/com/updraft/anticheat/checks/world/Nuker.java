package com.updraft.anticheat.checks.world;

import com.updraft.anticheat.UpdraftAC;
import com.updraft.anticheat.checks.api.Check;
import com.updraft.anticheat.checks.api.CheckType;
import com.updraft.anticheat.data.PlayerData;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nuker: flags players who break multiple blocks in a single tick.
 */
public final class Nuker extends Check {

    private final Map<UUID, Integer> breaksThisTick = new ConcurrentHashMap<>();

    public Nuker(UpdraftAC plugin) { super(plugin, CheckType.NUKER); }

    @Override
    public void onBukkitEvent(org.bukkit.event.Event event, PlayerData data) {
        if (!(event instanceof BlockBreakEvent)) return;
        BlockBreakEvent bbe = (BlockBreakEvent) event;
        if (!bbe.getPlayer().getUniqueId().equals(data.uuid())) return;

        int count = breaksThisTick.merge(data.uuid(), 1, Integer::sum);
        int maxPerTick = plugin.config().checks().getInt("world.nuker.max-blocks-per-tick", 1);
        if (count > maxPerTick) {
            fail(data, "breaks=" + count);
        }
    }

    @Override
    public void onTick(PlayerData data) {
        breaksThisTick.remove(data.uuid());
    }
}
