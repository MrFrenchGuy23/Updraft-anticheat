package com.updraft.anticheat;

import com.github.retrooper.packetevents.PacketEvents;
import com.updraft.anticheat.alert.AlertManager;
import com.updraft.anticheat.bukkit.BukkitListener;
import com.updraft.anticheat.checks.manager.CheckManager;
import com.updraft.anticheat.command.CommandLogger;
import com.updraft.anticheat.command.UpdraftCommand;
import com.updraft.anticheat.config.ConfigManager;
import com.updraft.anticheat.config.DefaultSettings;
import com.updraft.anticheat.config.Messages;
import com.updraft.anticheat.data.PlayerData;
import com.updraft.anticheat.data.PlayerDataManager;
import com.updraft.anticheat.net.PacketEventsListener;
import com.updraft.anticheat.performance.LagManager;
import com.updraft.anticheat.punishment.PunishmentManager;
import com.updraft.anticheat.punishment.lp.LuckPermsManager;
import com.updraft.anticheat.data.storage.DatabaseManager;
import com.updraft.anticheat.webhook.DiscordWebhook;
import com.updraft.anticheat.xray.XrayManager;
import com.updraft.anticheat.xray.XrayPacketListener;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.logging.Level;

/**
 * Updraft Anti-Cheat plugin entrypoint.
 * <p>
 * Holds shared singletons (config, data, storage, alerts, punishments) and
 * wires them into Bukkit events and the PacketEvents listener.
 */
public final class UpdraftAC extends JavaPlugin {

    private static UpdraftAC instance;

    // shared singletons
    private ConfigManager configManager;
    private Messages messages;
    private DefaultSettings defaultSettings;
    private PlayerDataManager playerDataManager;
    private DatabaseManager databaseManager;
    private CheckManager checkManager;
    private PunishmentManager punishmentManager;
    private AlertManager alertManager;
    private LagManager lagManager;
    private DiscordWebhook discordWebhook;
    private LuckPermsManager luckPermsManager;

    // listeners / tasks
    private PacketEventsListener packetEventsListener;
    private XrayManager xrayManager;
    private XrayPacketListener xrayPacketListener;
    private BukkitTask flushTask;
    private BukkitTask decayTask;
    private BukkitTask tickTask;
    private BukkitTask purgeTask;

    private boolean packetEventsAvailable;

    @Override
    public void onEnable() {
        instance = this;

        // 1. configuration
        saveDefaultConfig();
        configManager = new ConfigManager(this);
        configManager.loadAll();
        messages = new Messages(this);
        defaultSettings = new DefaultSettings(this);

        // 2. core data stores
        playerDataManager = new PlayerDataManager();

        // 3. storage (may fail; degrade gracefully)
        databaseManager = new DatabaseManager(this);
        try {
            databaseManager.enable();
            getLogger().info(messages.system("storage", "backend", databaseManager.backendName()));
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, messages.system("storage-error", "error",
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), e);
        }

        // 4. luckperms (soft-depend)
        luckPermsManager = new LuckPermsManager(this);
        getLogger().info(messages.system("luckperms-detected", "state", String.valueOf(luckPermsManager.enabled())));

        // 5. subsystems that depend on the above
        lagManager = new LagManager(this);
        discordWebhook = new DiscordWebhook(this);
        alertManager = new AlertManager(this);
        punishmentManager = new PunishmentManager(this);
        checkManager = new CheckManager(this);
        xrayManager = new XrayManager(this);

        // 6. listeners
        Bukkit.getPluginManager().registerEvents(new BukkitListener(this), this);
        Bukkit.getPluginManager().registerEvents(new CommandLogger(this), this);
        UpdraftCommand command = new UpdraftCommand(this);
        Bukkit.getPluginCommand("updraft").setExecutor(command);
        Bukkit.getPluginCommand("updraft").setTabCompleter(command);

        // 7. packet events (soft-depend via 'depend' in plugin.yml — required)
        try {
            packetEventsAvailable = Bukkit.getPluginManager().getPlugin("PacketEvents") != null;
        } catch (Throwable t) {
            packetEventsAvailable = false;
        }
        if (packetEventsAvailable) {
            try {
                packetEventsListener = new PacketEventsListener(this);
                PacketEvents.getAPI().getEventManager().registerListener(packetEventsListener);
                PacketEvents.getAPI().init();
                // anti-xray resolves block states against the versioned PacketEvents
                // mappings, so it must load after init()
                xrayManager.reload();
                xrayPacketListener = new XrayPacketListener(this);
                PacketEvents.getAPI().getEventManager().registerListener(xrayPacketListener);
            } catch (Throwable t) {
                packetEventsAvailable = false;
                getLogger().log(Level.SEVERE, "Failed to initialize PacketEvents listener", t);
            }
        } else {
            getLogger().warning(messages.raw("system.packetevents-missing",
                    "version", getDescription().getVersion()));
        }

        // 8. async writers / periodic tasks
        int flushTicks = configManager.config().getInt("storage.flush-interval-ticks", 100);
        flushTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this,
                () -> databaseManager.flush(), flushTicks, flushTicks);
        decayTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this,
                () -> checkManager.applyDecay(), 20L, 20L);
        tickTask = Bukkit.getScheduler().runTaskTimer(this,
                () -> checkManager.tick(), 1L, 1L);

        // retention purge: daily, first run after an hour
        int retentionDays = configManager.config().getInt("storage.retention-days", 30);
        if (retentionDays > 0) {
            purgeTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this,
                    () -> databaseManager.purgeOld(retentionDays), 20L * 60L * 60L, 20L * 60L * 60L * 24L);
        }

        // 9. tag online players (reload scenario)
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData pd = playerDataManager.register(p);
            lagManager.bind(p);
        }

        // 10. lag ticker
        lagManager.start();

        getLogger().info(messages.system("enabled", "version", getDescription().getVersion()));
        getLogger().info(messages.system("webhook-enabled", "state", String.valueOf(discordWebhook.enabled())));
    }

    @Override
    public void onDisable() {
        if (flushTask != null) flushTask.cancel();
        if (decayTask != null) decayTask.cancel();
        if (tickTask != null) tickTask.cancel();
        if (purgeTask != null) purgeTask.cancel();
        if (lagManager != null) lagManager.stop();
        if (discordWebhook != null) discordWebhook.shutdown();
        if (alertManager != null) alertManager.bossBar().hideAll();
        if (databaseManager != null) {
            try {
                databaseManager.flush();
                databaseManager.disable();
            } catch (Exception ignored) { /* best effort */ }
        }
        if (packetEventsAvailable && packetEventsListener != null) {
            try {
                PacketEvents.getAPI().getEventManager().unregisterListener(packetEventsListener);
                if (xrayPacketListener != null) {
                    PacketEvents.getAPI().getEventManager().unregisterListener(xrayPacketListener);
                }
                PacketEvents.getAPI().terminate();
            } catch (Throwable ignored) { /* best effort */ }
        }
        if (messages != null) {
            getLogger().info(messages.system("disabled"));
        } else {
            getLogger().info("Updraft AC disabled.");
        }
        instance = null;
    }

    /** Reload config files and reset dependent caches. */
    public void reload() {
        configManager.reload();
        defaultSettings = new DefaultSettings(this);
        checkManager.reload();
        punishmentManager.reload();
        alertManager.reload();
        discordWebhook.reload();
        if (xrayManager != null) xrayManager.reload();
    }

    // ----- accessors -----
    public static UpdraftAC get() { return instance; }

    public ConfigManager config() { return configManager; }
    public Messages messages() { return messages; }
    public DefaultSettings defaultSettings() { return defaultSettings; }

    public PlayerDataManager playerData() { return playerDataManager; }
    public DatabaseManager storage() { return databaseManager; }
    public CheckManager checks() { return checkManager; }
    public PunishmentManager punishments() { return punishmentManager; }
    public AlertManager alerts() { return alertManager; }
    public LagManager lag() { return lagManager; }
    public DiscordWebhook webhook() { return discordWebhook; }
    public LuckPermsManager luckPerms() { return luckPermsManager; }

    public XrayManager xray() { return xrayManager; }

    public boolean packetEventsAvailable() { return packetEventsAvailable; }

    public boolean isAnticheatEnabled() {
        return configManager != null && configManager.config().getBoolean("general.enabled", true);
    }
}
