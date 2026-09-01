package com.updraft.anticheat.config;

import com.updraft.anticheat.UpdraftAC;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Loads and caches every YAML resource used by the plugin.
 * <p>
 * On first load we copy the shipped resource into the plugin data folder if it
 * does not already exist, so server admins can edit the file freely. Subsequent
 * {@link #reload()} calls re-read from disk.
 */
public final class ConfigManager {

    private final UpdraftAC plugin;
    private final Map<String, FileConfiguration> cache = new HashMap<>();

    public ConfigManager(UpdraftAC plugin) {
        this.plugin = plugin;
    }

    /** Load all known configs. Should be called on enable and on {@code /updraft reload}. */
    public void loadAll() {
        cache.clear();
        load("config.yml");
        load("checks.yml");
        load("punishments.yml");
        load("messages.yml");
    }

    /** Reload from disk (alias kept for clarity). */
    public void reload() {
        loadAll();
    }

    /** Get a loaded config, throwing if missing. */
    public FileConfiguration get(String name) {
        FileConfiguration cfg = cache.get(name);
        if (cfg == null) {
            load(name);
            cfg = cache.get(name);
        }
        return cfg;
    }

    public FileConfiguration config() { return get("config.yml"); }
    public FileConfiguration checks() { return get("checks.yml"); }
    public FileConfiguration punishments() { return get("punishments.yml"); }
    public FileConfiguration messages() { return get("messages.yml"); }

    private void load(String name) {
        File file = file(name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
        YamlConfiguration cfg = new YamlConfiguration();
        // Load the user's file from disk so admin edits take effect.
        try (InputStreamReader reader = new InputStreamReader(
                new java.io.FileInputStream(file), StandardCharsets.UTF_8)) {
            cfg.load(reader);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load " + name, e);
        }
        // Merge defaults from the shipped resource so new keys appear without wiping user edits.
        try (InputStream def = plugin.getResource(name)) {
            if (def != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(def, StandardCharsets.UTF_8));
                cfg.setDefaults(defaults);
            }
        } catch (IOException ignored) { /* non-fatal */ }
        cache.put(name, cfg);
    }

    private File file(String name) {
        return new File(plugin.getDataFolder(), name);
    }
}
