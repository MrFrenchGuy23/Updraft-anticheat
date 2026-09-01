package com.updraft.anticheat.checks.world;

import com.updraft.anticheat.UpdraftAC;
import com.updraft.anticheat.checks.api.Check;
import com.updraft.anticheat.checks.api.CheckType;
import com.updraft.anticheat.data.PlayerData;
import com.updraft.anticheat.util.MathUtil;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.Location;

/**
 * ReachBlock: flags players who interact with (break/place) blocks beyond the
 * configured {@code max-distance}.
 */
public final class ReachBlock extends Check {

    public ReachBlock(UpdraftAC plugin) { super(plugin, CheckType.REACHBLOCK); }

    @Override
    public void onBukkitEvent(org.bukkit.event.Event event, PlayerData data) {
        Location blockLoc = null;
        if (event instanceof BlockBreakEvent) {
            blockLoc = ((BlockBreakEvent) event).getBlock().getLocation();
        } else if (event instanceof BlockPlaceEvent) {
            blockLoc = ((BlockPlaceEvent) event).getBlock().getLocation();
        }
        if (blockLoc == null) return;

        org.bukkit.entity.Player player = data.player();
        if (player == null) return;

        Location eye = player.getEyeLocation();
        double dx = eye.getX() - (blockLoc.getX() + 0.5);
        double dy = eye.getY() - (blockLoc.getY() + 0.5);
        double dz = eye.getZ() - (blockLoc.getZ() + 0.5);
        double dist = MathUtil.distance3d(dx, dy, dz);

        double maxDist = plugin.config().checks().getDouble("world.reachblock.max-distance", 5.5);
        // small ping buffer
        double pingBuffer = Math.min(data.ping() * 0.02 / 50.0, 0.3);
        if (dist > maxDist + pingBuffer) {
            fail(data, String.format("dist=%.2f max=%.2f", dist, maxDist));
        }
    }
}
