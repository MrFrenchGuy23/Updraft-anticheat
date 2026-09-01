package com.updraft.anticheat.checks.movement;

import com.updraft.anticheat.UpdraftAC;
import com.updraft.anticheat.checks.api.Check;
import com.updraft.anticheat.checks.api.CheckType;
import com.updraft.anticheat.data.PlayerData;
import com.updraft.anticheat.util.BlockUtil;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/**
 * Glide: flags players whose downward vertical speed is reduced below what
 * gravity should produce, without a valid explanation (elytra, slow falling,
 * water, etc.).
 */
public final class Glide extends Check {

    private static final double VANILLA_GRAVITY = 0.08;
    private static final double TERMINAL_VELOCITY = 0.78;

    /** Consecutive ticks the player has been genuinely falling (airborne, downward motion). */
    private final java.util.Map<java.util.UUID, Integer> fallTicks = new java.util.concurrent.ConcurrentHashMap<>();

    public Glide(UpdraftAC plugin) { super(plugin, CheckType.GLIDE); }

    @Override
    public void onTick(PlayerData data) {
        Player player = data.player();
        if (player == null) return;

        // Legitimate slow-fall sources
        boolean legitSlow = player.hasPotionEffect(PotionEffectType.SLOW_FALLING)
                || data.gliding() // elytra
                || player.isInWater() || player.isInLava()
                || BlockUtil.isLiquid(player.getLocation().getBlock().getType())
                || BlockUtil.isCobweb(player.getLocation().getBlock().getType())
                || BlockUtil.isClimbable(player.getLocation().getBlock().getType())
                || player.isInsideVehicle();
        if (legitSlow) {
            fallTicks.remove(data.uuid());
            return;
        }

        // Only matters while actually falling (negative dy, not on ground).
        double dy = data.dy();
        if (data.onGround() || dy >= -0.01) {
            fallTicks.remove(data.uuid());
            return;
        }

        int n = fallTicks.merge(data.uuid(), 1, Integer::sum);
        int minTicks = plugin.config().checks()
                .getInt("movement.glide.min-fall-ticks", 6);
        if (n < minTicks) return;

        // Expected free-fall speed after n ticks, capped at terminal velocity.
        double expected = Math.min(VANILLA_GRAVITY * n, TERMINAL_VELOCITY);
        double ratio = Math.abs(dy) / expected;
        if (ratio < 0.6) {
            fail(data, String.format("glide dy=%.4f expected=%.3f ticks=%d",
                    dy, -expected, n));
        }
    }
}
