package com.updraft.anticheat.checks.movement;

import com.updraft.anticheat.UpdraftAC;
import com.updraft.anticheat.checks.api.Check;
import com.updraft.anticheat.checks.api.CheckType;
import com.updraft.anticheat.data.PlayerData;
import com.updraft.anticheat.util.BlockUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Spider: flags players who ascend vertically while pressed against a wall
 * without a climbable block (ladder, vine, etc.).
 */
public final class Spider extends Check {

    /** Consecutive ticks of sustained upward motion while pressed against a wall. */
    private final java.util.Map<java.util.UUID, Integer> climbTicks = new java.util.concurrent.ConcurrentHashMap<>();

    public Spider(UpdraftAC plugin) { super(plugin, CheckType.SPIDER); }

    @Override
    public void onTick(PlayerData data) {
        Player player = data.player();
        if (player == null) return;

        // Climbing a ladder/vine/scaffold is legitimate.
        if (BlockUtil.isClimbable(player.getLocation().getBlock().getType())) {
            climbTicks.remove(data.uuid());
            return;
        }
        // Swimming upward is legitimate.
        if (player.isInWater() || player.isInLava()) {
            climbTicks.remove(data.uuid());
            return;
        }
        // On the ground or not moving up → not climbing.
        double dy = data.dy();
        if (data.onGround() || dy <= 0.06) {
            climbTicks.remove(data.uuid());
            return;
        }

        Location loc = player.getLocation();
        // Check if there's a solid wall beside the player (they'd be climbing it)
        boolean nearWall = false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                Material m = BlockUtil.materialAt(loc.clone().add(dx, 0, dz));
                if (BlockUtil.isSolid(m)) { nearWall = true; break; }
            }
            if (nearWall) break;
        }
        if (!nearWall) {
            climbTicks.remove(data.uuid());
            return;
        }

        // A plain jump against a wall only rises for ~6 ticks; anything longer
        // at a steady clip means the player is climbing the wall.
        int n = climbTicks.merge(data.uuid(), 1, Integer::sum);
        int minTicks = plugin.config().checks()
                .getInt("movement.spider.min-climb-ticks", 8);
        if (n >= minTicks) {
            fail(data, String.format("wall-climb dy=%.3f ticks=%d", dy, n));
        }
    }
}
