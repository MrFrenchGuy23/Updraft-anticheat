package com.updraft.anticheat.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/**
 * Block/world helpers used by movement checks (Fly, Speed, Jesus, etc.).
 */
public final class BlockUtil {

    public static final BlockFace[] FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST,
            BlockFace.NORTH_EAST, BlockFace.NORTH_WEST, BlockFace.SOUTH_EAST, BlockFace.SOUTH_WEST
    };

    private BlockUtil() {}

    /** True if the given material is a type of air. */
    public static boolean isAir(Material material) {
        return material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR;
    }

    /** True if the given material is a liquid (water/lava incl. flowing variants). */
    public static boolean isLiquid(Material material) {
        return material == Material.WATER || material == Material.LAVA
                || material.name().endsWith("WATER") || material.name().endsWith("LAVA");
    }

    /** True if a player can stand on the material (a normal solid block, not air/liquid/fence/etc). */
    public static boolean isSolid(Material material) {
        if (isAir(material) || isLiquid(material)) return false;
        return material.isSolid();
    }

    /** True if the player can climb the material (ladders, vines, scaffolding). */
    public static boolean isClimbable(Material material) {
        return material == Material.LADDER
                || material == Material.VINE
                || material == Material.SCAFFOLDING
                || material == Material.WEEPING_VINES
                || material == Material.WEEPING_VINES_PLANT
                || material == Material.TWISTING_VINES
                || material == Material.TWISTING_VINES_PLANT
                || material == Material.CAVE_VINES
                || material == Material.CAVE_VINES_PLANT;
    }

    /** True if the material is ice-like (affects friction/speed). */
    public static boolean isIce(Material material) {
        return material == Material.ICE
                || material == Material.PACKED_ICE
                || material == Material.BLUE_ICE
                || material == Material.FROSTED_ICE;
    }

    /** True if the material is slime (bouncy). */
    public static boolean isBouncy(Material material) {
        return material == Material.SLIME_BLOCK;
    }

    /** True if the material is a cobweb (drastically reduces fall speed). */
    public static boolean isCobweb(Material material) {
        return material == Material.COBWEB;
    }

    /** Snapshot {@link Material} at a world-relative location, never throwing. */
    public static Material materialAt(Location location) {
        if (location == null || location.getWorld() == null) return Material.AIR;
        try {
            return location.getBlock().getType();
        } catch (Throwable ignored) {
            return Material.AIR;
        }
    }

    /** Convenience: material at offset from a base location. */
    public static Material materialAt(Location base, double dx, double dy, double dz) {
        return materialAt(base.clone().add(dx, dy, dz));
    }

    /** True if there is any solid block within {@code radius} (inclusive) of the location horizontally. */
    public static boolean isNearSolid(Location loc, double radius) {
        if (loc == null || loc.getWorld() == null) return false;
        int r = (int) Math.ceil(radius);
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    if (x * x + y * y + z * z > radius * radius + 1) continue;
                    Block b = loc.clone().add(x, y, z).getBlock();
                    if (isSolid(b.getType())) return true;
                }
            }
        }
        return false;
    }
}
