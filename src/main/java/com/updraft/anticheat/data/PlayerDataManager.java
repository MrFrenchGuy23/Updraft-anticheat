package com.updraft.anticheat.data;

import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks a {@link PlayerData} for every online player. Lookups happen on the
 * packet thread, so the backing map is concurrent and removal is idempotent.
 */
public final class PlayerDataManager {

    private final Map<UUID, PlayerData> data = new ConcurrentHashMap<>();

    public PlayerData register(Player player) {
        PlayerData pd = new PlayerData(player);
        data.put(player.getUniqueId(), pd);
        return pd;
    }

    public PlayerData unregister(UUID uuid) {
        return data.remove(uuid);
    }

    public PlayerData get(UUID uuid) { return data.get(uuid); }

    public PlayerData get(Player player) {
        return player == null ? null : data.get(player.getUniqueId());
    }

    public Collection<PlayerData> all() { return data.values(); }

    public boolean has(UUID uuid) { return data.containsKey(uuid); }

    public void clear() { data.clear(); }
}
