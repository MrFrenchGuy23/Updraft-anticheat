package com.updraft.anticheat.checks.client;

import com.updraft.anticheat.UpdraftAC;
import com.updraft.anticheat.checks.api.Check;
import com.updraft.anticheat.checks.api.CheckType;
import com.updraft.anticheat.data.PlayerData;
import com.updraft.anticheat.util.ChatUtil;

/**
 * ClientBrandCheck: flags players whose client brand string matches a known
 * blacklisted cheat client (e.g. "wurst", "impact", "meteor").
 * <p>
 * Invoked by {@link com.updraft.anticheat.net.PacketEventsListener} when the
 * client brand plugin message arrives.
 */
public final class ClientBrandCheck extends Check {

    public ClientBrandCheck(UpdraftAC plugin) { super(plugin, CheckType.BRAND); }

    /**
     * Triggered directly by the packet listener. The generic {@code onBukkitEvent}
     * hook is a no-op; we use this method because brand data arrives via packet.
     */
    public void check(PlayerData data) {
        String brand = data.clientBrand();
        if (brand == null || brand.isEmpty() || "unknown".equalsIgnoreCase(brand)) return;

        java.util.List<String> blacklist = plugin.config().checks()
                .getStringList("client.brand.blacklist");
        String lower = brand.toLowerCase(java.util.Locale.ROOT);
        for (String bad : blacklist) {
            if (bad == null || bad.isBlank()) continue;
            if (lower.contains(bad.toLowerCase(java.util.Locale.ROOT))) {
                fail(data, "brand=" + brand);
                return;
            }
        }
    }

    @Override
    public void onBukkitEvent(org.bukkit.event.Event event, PlayerData data) {
        check(data);
    }
}
