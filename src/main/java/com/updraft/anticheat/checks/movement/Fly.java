package com.updraft.anticheat.checks.movement;

import com.updraft.anticheat.UpdraftAC;
import com.updraft.anticheat.checks.api.Check;
import com.updraft.anticheat.checks.api.CheckType;
import com.updraft.anticheat.data.PlayerData;
import com.updraft.anticheat.util.BlockUtil;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/**
 * Fly: flags players who maintain or gain altitude without a legitimate
 * source of levitation (elytra, trident riptide, creative, flight, potion,
 * climbable blocks, water/lava, explosion knockback, etc.).
 * <p>
 * Checks that the player's vertical movement over recent ticks is explainable.
 */
public final class Fly extends Check {

    private static final double GRAVITY_ACCEL = 0.08; // vanilla gravity per tick
    private static final double JUMP_INITIAL = 0.42;

    /** Consecutive ticks the player has been airborne (not on ground). */
    private final java.util.Map<java.util.UUID, Integer> airTicks = new java.util.concurrent.ConcurrentHashMap<>();
    /** Consecutive ticks of near-zero vertical speed while airborne (hover). */
    private final java.util.Map<java.util.UUID, Integer> hoverTicks = new java.util.concurrent.ConcurrentHashMap<>();

    public Fly(UpdraftAC plugin) { super(plugin, CheckType.FLY); }

    @Override
    public void onTick(PlayerData data) {
        Player player = data.player();
        if (player == null) return;

        if (data.onGround()) {
            airTicks.remove(data.uuid());
            hoverTicks.remove(data.uuid());
        } else {
            airTicks.merge(data.uuid(), 1, Integer::sum);
        }

        double dy = data.dy();

        // Positive dy (going up) without any valid source
        if (dy > 0.0 && !canExplainUpward(data, player)) {
            double maxUnexplained = plugin.config().checks()
                    .getDouble("movement.fly.max-unexplained-vertical", 0.05);
            if (dy > maxUnexplained) {
                fail(data, String.format("dy=%.3f ground=%b", dy, data.onGround()));
            }
        }

        // Hovering: sustained near-zero vertical speed while airborne (a jump's
        // apex lasts at most one tick, so several consecutive ticks means the
        // player is holding altitude with no legitimate source).
        if (!data.onGround() && Math.abs(dy) < 0.03) {
            // Sinking/bobbing in liquid and standing still on a climbable both
            // produce a flat dy while airborne — exclude them.
            org.bukkit.Location loc = player.getLocation();
            boolean inLiquid = BlockUtil.isLiquid(loc.getBlock().getType())
                    || BlockUtil.isLiquid(BlockUtil.materialAt(loc.clone().add(0, 0.2, 0)));
            if (!inLiquid && !BlockUtil.isClimbable(loc.getBlock().getType())) {
                int n = hoverTicks.merge(data.uuid(), 1, Integer::sum);
                int minHover = plugin.config().checks()
                        .getInt("movement.fly.min-hover-ticks", 5);
                if (n >= minHover) {
                    fail(data, String.format("hover dy=%.4f ticks=%d", dy, n));
                    hoverTicks.remove(data.uuid());
                }
            }
        } else {
            hoverTicks.remove(data.uuid());
        }
    }

    private boolean canExplainUpward(PlayerData data, Player player) {
        // A jump arc: rising while airborne for a few ticks, decelerating from
        // JUMP_INITIAL at ~GRAVITY per tick. Anything faster/longer than a real
        // jump (in this tick of the arc) is unexplained.
        if (data.lastOnGround() || data.onGround()) {
            if (data.dy() <= JUMP_INITIAL + 0.05) return true;
        }
        int air = airTicks.getOrDefault(data.uuid(), 0);
        if (air > 0) {
            double expected = Math.max(0.0, JUMP_INITIAL - GRAVITY_ACCEL * (air - 1));
            if (data.dy() <= expected + 0.10) return true;
        }

        // In water/lava
        org.bukkit.Location loc = player.getLocation();
        if (BlockUtil.isLiquid(loc.getBlock().getType())) return true;
        if (BlockUtil.isLiquid(BlockUtil.materialAt(loc.clone().add(0, 0.2, 0)))) return true;

        // Slime block bounce
        if (BlockUtil.isBouncy(BlockUtil.materialAt(loc.clone().add(0, -0.5, 0)))) return true;

        // Climbable
        if (BlockUtil.isClimbable(loc.getBlock().getType())) return true;

        // Levitation potion
        if (player.hasPotionEffect(PotionEffectType.LEVITATION)) return true;
        // Slow falling (doesn't prevent upward but affects gravity)
        if (player.hasPotionEffect(PotionEffectType.SLOW_FALLING)) return true;

        // Jump boost modifies initial velocity
        if (hasJumpBoost(player)) return true;

        // Riptiding
        if (data.riptiding()) return true;

        // Elytra
        if (data.gliding()) return true;

        // Creative/spectator
        if (player.getAllowFlight() || data.allowFlight()) return true;

        return false;
    }

    /** Resolves the JUMP_BOOST potion type across Paper versions (was JUMP pre-1.20.5). */
    private static boolean hasJumpBoost(Player player) {
        try {
            return player.hasPotionEffect(PotionEffectType.JUMP_BOOST);
        } catch (Throwable t) {
            try {
                PotionEffectType jump = (PotionEffectType) PotionEffectType.class.getField("JUMP").get(null);
                return jump != null && player.hasPotionEffect(jump);
            } catch (Throwable ignored) {
                return false;
            }
        }
    }
}
