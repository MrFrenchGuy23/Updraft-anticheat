package com.updraft.anticheat.checks.player;

import com.updraft.anticheat.UpdraftAC;
import com.updraft.anticheat.checks.api.Check;
import com.updraft.anticheat.checks.api.CheckType;
import com.updraft.anticheat.data.PlayerData;
import org.bukkit.Material;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FastBow: flags players who fire a bow faster than the minimum charge time.
 * <p>
 * Tracks when the player starts using a bow (right-click) and compares against
 * when the arrow is actually released.
 */
public final class FastBow extends Check {

    private final Map<UUID, Long> bowChargeStart = new ConcurrentHashMap<>();

    public FastBow(UpdraftAC plugin) { super(plugin, CheckType.FASTBOW); }

    @Override
    public void onBukkitEvent(org.bukkit.event.Event event, PlayerData data) {
        if (event instanceof EntityShootBowEvent) {
            EntityShootBowEvent ese = (EntityShootBowEvent) event;
            if (!(ese.getEntity() instanceof org.bukkit.entity.Player)) return;
            if (!ese.getEntity().getUniqueId().equals(data.uuid())) return;

            // Weak/tap shots are legitimate (barely-drawn arrows). Only a
            // near-full-power shot needs the full draw time.
            if (ese.getForce() < 0.95f) return;

            long now = System.currentTimeMillis();
            Long start = bowChargeStart.remove(data.uuid());
            if (start == null) return; // couldn't observe the charge start

            long elapsed = now - start;
            int minMs = plugin.config().checks().getInt("player.fastbow.min-charge-ms", 1100);

            // Quick Charge shortens crossbow loading by 0.25s per level.
            ItemStack bow = ese.getBow();
            if (bow != null && bow.getType() == Material.CROSSBOW) {
                int qc = bow.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.QUICK_CHARGE);
                minMs -= 250 * qc;
            }

            if (elapsed < minMs) {
                fail(data, String.format("charge-ms=%d min=%d", elapsed, minMs));
            }
        } else if (event instanceof PlayerInteractEvent) {
            // Right-clicking with a bow starts the charge.
            PlayerInteractEvent pie = (PlayerInteractEvent) event;
            org.bukkit.event.block.Action action = pie.getAction();
            if (action != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                    && action != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
            if (!isBow(pie.getItem())) return;
            bowChargeStart.put(data.uuid(), System.currentTimeMillis());
        } else if (event instanceof PlayerItemHeldEvent) {
            // Switching the held slot cancels the charge.
            bowChargeStart.remove(data.uuid());
        }
    }

    private static boolean isBow(ItemStack item) {
        if (item == null) return false;
        Material type = item.getType();
        return type == Material.BOW || type == Material.CROSSBOW;
    }
}
