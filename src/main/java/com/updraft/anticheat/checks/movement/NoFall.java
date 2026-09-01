package com.updraft.anticheat.checks.movement;

import com.updraft.anticheat.UpdraftAC;
import com.updraft.anticheat.checks.api.Check;
import com.updraft.anticheat.checks.api.CheckType;
import com.updraft.anticheat.data.PlayerData;
import com.updraft.anticheat.util.BlockUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NoFall: detects players who spoof their {@code onGround} flag to avoid fall
 * damage.
 * <p>
 * A player claiming to be on the ground while there is still air below their
 * feet — after a fall long enough to deal damage — is spoofing. Genuine
 * landings always have a solid (non-air) block below.
 */
public final class NoFall extends Check {

    /** Accumulated fall distance (blocks) while airborne. */
    private final Map<UUID, Double> fallDistance = new ConcurrentHashMap<>();

    public NoFall(UpdraftAC plugin) { super(plugin, CheckType.NOFALL); }

    @Override
    public void onTick(PlayerData data) {
        Player player = data.player();
        if (player == null) return;
        if (player.isInWater() || player.isInLava()) return;
        if (player.isInsideVehicle()) return;

        Material below = BlockUtil.materialAt(player.getLocation().clone().add(0, -0.5, 0));
        boolean actualGround = !BlockUtil.isAir(below) && !BlockUtil.isLiquid(below);

        if (actualGround) {
            // Genuinely stood on something — a real landing.
            fallDistance.remove(data.uuid());
            return;
        }

        double dy = data.dy();
        if (dy < 0) {
            fallDistance.merge(data.uuid(), -dy, Double::sum);
        }

        if (data.onGround()) {
            double dist = fallDistance.getOrDefault(data.uuid(), 0.0);
            double minFall = plugin.config().checks()
                    .getDouble("movement.nofall.min-fall-distance", 3.0);
            if (dist >= minFall) {
                fail(data, String.format("spoofed-ground dist=%.1f", dist));
                fallDistance.remove(data.uuid());
            }
        }
    }
}
