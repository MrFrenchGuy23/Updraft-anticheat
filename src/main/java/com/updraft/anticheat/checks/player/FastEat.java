package com.updraft.anticheat.checks.player;

import com.updraft.anticheat.UpdraftAC;
import com.updraft.anticheat.checks.api.Check;
import com.updraft.anticheat.checks.api.CheckType;
import com.updraft.anticheat.data.PlayerData;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FastEat: flags players who consume food faster than the minimum eat time
 * configured in {@code checks.yml}.
 * <p>
 * The clock starts when the player right-clicks with a food item
 * ({@link PlayerInteractEvent}) and stops when consumption finishes
 * ({@link PlayerItemConsumeEvent}).
 */
public final class FastEat extends Check {

    private final Map<UUID, Long> eatStartMs = new ConcurrentHashMap<>();

    public FastEat(UpdraftAC plugin) { super(plugin, CheckType.FASTEAT); }

    @Override
    public void onBukkitEvent(org.bukkit.event.Event event, PlayerData data) {
        if (event instanceof PlayerInteractEvent) {
            PlayerInteractEvent pie = (PlayerInteractEvent) event;
            if (pie.getAction() != Action.RIGHT_CLICK_AIR
                    && pie.getAction() != Action.RIGHT_CLICK_BLOCK) return;
            if (pie.getItem() == null || !pie.getItem().getType().isEdible()) return;
            // Don't reset an in-progress charge on repeat click packets.
            eatStartMs.putIfAbsent(data.uuid(), System.currentTimeMillis());
        } else if (event instanceof PlayerItemConsumeEvent) {
            Long start = eatStartMs.remove(data.uuid());
            if (start != null) {
                long elapsed = System.currentTimeMillis() - start;
                int minMs = plugin.config().checks().getInt("player.fasteat.min-eat-ms", 1500);
                if (elapsed < minMs) {
                    fail(data, String.format("eat-ms=%d min=%d", elapsed, minMs));
                }
            }
        } else if (event instanceof PlayerItemHeldEvent) {
            // Switching the held slot cancels an in-progress eat.
            eatStartMs.remove(data.uuid());
        }
    }
}
