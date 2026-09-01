package com.updraft.anticheat.checks.player;

import com.updraft.anticheat.UpdraftAC;
import com.updraft.anticheat.checks.api.Check;
import com.updraft.anticheat.checks.api.CheckType;
import com.updraft.anticheat.data.PlayerData;

/**
 * InventoryMove: flags players who move while their inventory is open.
 * <p>
 * Legitimate movement with an open inventory is not possible in vanilla. If a
 * player sends movement packets while a container/inventory screen is open,
 * they are using an inventory-move cheat.
 */
public final class InventoryMove extends Check {

    public InventoryMove(UpdraftAC plugin) { super(plugin, CheckType.INVENTORYMOVE); }

    @Override
    public void onTick(PlayerData data) {
        org.bukkit.entity.Player player = data.player();
        if (player == null) return;

        // When no screen is open, the "top" inventory IS the player's own
        // inventory — that is not a screen, so moving around must be allowed.
        if (player.getOpenInventory().getTopInventory() == player.getInventory()) return;
        if (player.isSneaking() && player.getOpenInventory().getType() == org.bukkit.event.inventory.InventoryType.CRAFTING) return;

        // Player is in a real inventory screen. Check if they're moving.
        double hSpeed = data.horizontalSpeed();
        double vSpeed = Math.abs(data.dy());

        // Small threshold to ignore micro-movement from lag
        if (hSpeed > 0.05 || vSpeed > 0.05) {
            fail(data, String.format("move-while-inv h=%.3f v=%.3f", hSpeed, vSpeed));
        }
    }
}
