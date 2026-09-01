package com.updraft.anticheat.checks.movement;

import com.updraft.anticheat.UpdraftAC;
import com.updraft.anticheat.checks.api.Check;
import com.updraft.anticheat.checks.api.CheckType;
import com.updraft.anticheat.data.PlayerData;
import com.updraft.anticheat.util.BlockUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/**
 * Speed: flags players whose horizontal movement exceeds the vanilla speed budget.
 * <p>
 * Budget accounts for walking, sprinting, sneaking, flying, and status effects.
 * A configurable multiplier adds a tolerance buffer for latency.
 */
public final class Speed extends Check {

    /** Base speeds (blocks per tick). */
    private static final double WALK_SPEED = 0.1;
    private static final double SPRINT_SPEED = 0.287;
    private static final double SNEAK_SPEED = 0.03;
    private static final double FLY_SPEED = 0.5;

    public Speed(UpdraftAC plugin) { super(plugin, CheckType.SPEED); }

    @Override
    public void onTick(PlayerData data) {
        Player player = data.player();
        if (player == null) return;

        double hSpeed = data.horizontalSpeed();

        // Compute the player's speed budget based on their state
        double budget = getSpeedBudget(data, player);
        double multiplier = plugin.config().checks()
                .getDouble("movement.speed.speed-budget-multiplier", 1.05);
        double threshold = budget * multiplier;

        // Small grace: ignore near-zero movement (standing still)
        if (hSpeed < 0.01) return;

        // Ignore vertical component — that's handled by Fly
        // Ignore if player is in water (handled by Jesus)
        Material mat = player.getLocation().getBlock().getType();
        if (BlockUtil.isLiquid(mat)) return;

        if (hSpeed > threshold) {
            fail(data, String.format("speed=%.3f budget=%.3f", hSpeed, threshold));
        }
    }

    private double getSpeedBudget(PlayerData data, Player player) {
        double base;
        // Flight has the highest budget — it must be evaluated before sprint,
        // otherwise a flying player who is also sprinting gets the (lower)
        // sprint budget and is flagged for legitimate flight speed.
        if (data.flying() || player.getAllowFlight() || player.isFlying()) {
            base = FLY_SPEED;
        } else if (player.isSprinting()) {
            base = SPRINT_SPEED;
        } else if (player.isSneaking()) {
            base = SNEAK_SPEED;
        } else {
            base = WALK_SPEED;
        }

        // Speed potion effect amplifies base speed
        if (player.hasPotionEffect(PotionEffectType.SPEED)) {
            int amp = player.getPotionEffect(PotionEffectType.SPEED).getAmplifier();
            base *= 1.0 + 0.2 * (amp + 1);
        }

        // Slowness
        org.bukkit.potion.PotionEffectType slow = potionSlow();
        if (player.hasPotionEffect(slow)) {
            int amp = player.getPotionEffect(slow).getAmplifier();
            base *= Math.max(0, 1.0 - 0.15 * (amp + 1));
        }

        // Block modifiers
        Material below = BlockUtil.materialAt(player.getLocation().clone().add(0, -0.5, 0));
        if (below == Material.BLUE_ICE) base *= 1.6;
        else if (below == Material.PACKED_ICE) base *= 1.5;
        else if (BlockUtil.isIce(below)) base *= 1.4;
        if (below == Material.SOUL_SAND) base *= 0.4;

        return base;
    }

    /** Resolves the SLOWNESS potion type across Paper versions. */
    private static org.bukkit.potion.PotionEffectType potionSlow() {
        try {
            return org.bukkit.potion.PotionEffectType.SLOWNESS;
        } catch (Throwable t) {
            // Pre-1.20.5 fallback
            try {
                return (org.bukkit.potion.PotionEffectType)
                        org.bukkit.potion.PotionEffectType.class.getField("SLOW").get(null);
            } catch (Throwable ignored) {
                return null;
            }
        }
    }
}
