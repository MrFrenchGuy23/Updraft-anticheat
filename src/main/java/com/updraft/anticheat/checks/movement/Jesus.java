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
 * Jesus: flags players who walk on the surface of deep water without a valid
 * source (boat, lily pad, frost walker, solid floor beneath).
 * <p>
 * The tell: the client reports {@code onGround=true} while the block directly
 * under the feet is liquid — a genuine floor-supported player always has a
 * solid block right below their feet.
 */
public final class Jesus extends Check {

    public Jesus(UpdraftAC plugin) { super(plugin, CheckType.JESUS); }

    @Override
    public void onTick(PlayerData data) {
        Player player = data.player();
        if (player == null) return;
        if (!data.onGround()) return;

        Location loc = player.getLocation();
        Material atFeet = BlockUtil.materialAt(loc.clone().add(0, -0.1, 0));
        Material belowFeet = BlockUtil.materialAt(loc.clone().add(0, -1.0, 0));

        // Must be standing on or near water
        if (!BlockUtil.isLiquid(atFeet) && !BlockUtil.isLiquid(BlockUtil.materialAt(loc.clone().add(0, -0.5, 0)))) return;

        // Frost walker enchantment creates ice — legitimate
        if (player.getInventory().getBoots() != null
                && player.getInventory().getBoots().containsEnchantment(
                org.bukkit.enchantments.Enchantment.FROST_WALKER)) return;

        // Standing on a solid floor beneath the water (pool floor, riverbed) — legit.
        if (BlockUtil.isSolid(belowFeet)) return;

        // Lily pads support players.
        if (belowFeet == Material.LILY_PAD) return;

        // In a boat?
        if (player.isInsideVehicle()) return;

        fail(data, "water-walk");
    }
}
