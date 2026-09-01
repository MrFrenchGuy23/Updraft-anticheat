package com.updraft.anticheat.bukkit;

import com.updraft.anticheat.UpdraftAC;
import com.updraft.anticheat.checks.api.Check;
import com.updraft.anticheat.data.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerUnregisterChannelEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;

/**
 * Bukkit event listener that keeps {@link PlayerData} in sync with gameplay
 * state (teleports, damage, velocity, vehicle, flight, channels) and dispatches
 * events to checks via {@link Check#onBukkitEvent(Event, PlayerData)}.
 */
public final class BukkitListener implements Listener {

    private final UpdraftAC plugin;

    public BukkitListener(UpdraftAC plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData pd = plugin.playerData().register(player);
        if (pd == null) return;
        pd.lastJoinMs(System.currentTimeMillis());
        pd.allowFlight(player.getAllowFlight());
        pd.ping(plugin.lag().ping(player));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        PlayerData pd = plugin.playerData().get(player);
        if (pd == null) return;
        // persist final meta snapshot
        if (plugin.storage().isEnabled()) {
            String serverId = plugin.config().config().getString("general.server-id", "");
            plugin.storage().upsertMeta(new com.updraft.anticheat.data.storage.DatabaseManager.PlayerMetaRecord(
                    pd.uuid().toString(),
                    pd.name(),
                    pd.clientBrand(),
                    pd.clientVersion(),
                    pd.totalVl(),
                    System.currentTimeMillis()));
        }
        plugin.playerData().unregister(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        PlayerData pd = plugin.playerData().get(event.getPlayer());
        if (pd != null) pd.lastTeleportMs(System.currentTimeMillis());
        dispatch(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        PlayerData pd = plugin.playerData().get((Player) event.getEntity());
        if (pd != null) pd.lastDamageMs(System.currentTimeMillis());
        dispatch(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        PlayerData pd = plugin.playerData().get((Player) event.getEntity());
        if (pd != null) pd.lastDamageMs(System.currentTimeMillis());
        dispatch(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent event) {
        PlayerData pd = plugin.playerData().get(event.getPlayer());
        if (pd != null) pd.lastVelocityMs(System.currentTimeMillis());
        dispatch(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFlight(PlayerToggleFlightEvent event) {
        PlayerData pd = plugin.playerData().get(event.getPlayer());
        if (pd != null) {
            pd.lastFlightToggleMs(System.currentTimeMillis());
            pd.flying(event.isFlying());
        }
        dispatch(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        PlayerData pd = plugin.playerData().get((Player) event.getEntity());
        if (pd != null) pd.gliding(event.isGliding());
        dispatch(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (!(event.getEntered() instanceof Player)) return;
        PlayerData pd = plugin.playerData().get((Player) event.getEntered());
        if (pd != null) pd.inVehicle(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleExit(VehicleExitEvent event) {
        if (!(event.getExited() instanceof Player)) return;
        PlayerData pd = plugin.playerData().get((Player) event.getExited());
        if (pd != null) pd.inVehicle(false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent event) {
        PlayerData pd = plugin.playerData().get(event.getPlayer());
        if (pd != null) pd.allowFlight(event.getNewGameMode().ordinal() == 1); // 1 = CREATIVE
        dispatch(event);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        PlayerData pd = plugin.playerData().get(event.getPlayer());
        if (pd != null) pd.lastTeleportMs(System.currentTimeMillis());
    }

    @EventHandler
    public void onChangeWorld(PlayerChangedWorldEvent event) {
        PlayerData pd = plugin.playerData().get(event.getPlayer());
        if (pd != null) pd.lastTeleportMs(System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRegisterChannel(PlayerRegisterChannelEvent event) {
        PlayerData pd = plugin.playerData().get(event.getPlayer());
        if (pd != null) pd.registeredChannels().put(event.getChannel().toString(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUnregisterChannel(PlayerUnregisterChannelEvent event) {
        PlayerData pd = plugin.playerData().get(event.getPlayer());
        if (pd != null) pd.registeredChannels().remove(event.getChannel().toString());
    }

    /** Forward a Bukkit event to every check bound to that player. */
    private void dispatch(Event event) {
        Player player = playerOf(event);
        if (player == null) return;
        PlayerData pd = plugin.playerData().get(player);
        if (pd == null) return;
        for (Check c : plugin.checks().all()) {
            if (!c.enabled()) continue;
            try {
                c.onBukkitEvent(event, pd);
            } catch (Throwable ignored) {}
        }
    }

    /** Resolve the player a generic Bukkit event belongs to. */
    private static Player playerOf(Event event) {
        if (event instanceof org.bukkit.event.player.PlayerEvent) {
            return ((org.bukkit.event.player.PlayerEvent) event).getPlayer();
        }
        if (event instanceof org.bukkit.event.entity.EntityEvent
                && ((org.bukkit.event.entity.EntityEvent) event).getEntity() instanceof Player) {
            return (Player) ((org.bukkit.event.entity.EntityEvent) event).getEntity();
        }
        return null;
    }

    // ----- world / player checks need these events -----

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        dispatch(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        dispatch(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemConsume(PlayerItemConsumeEvent event) {
        dispatch(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        dispatch(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        dispatch(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        dispatch(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeldItem(PlayerItemHeldEvent event) {
        dispatch(event);
    }
}
