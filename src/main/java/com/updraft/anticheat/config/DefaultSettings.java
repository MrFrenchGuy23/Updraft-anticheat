package com.updraft.anticheat.config;

import com.updraft.anticheat.UpdraftAC;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Reads the {@code defaults} block of {@code checks.yml} and produces a
 * {@link CheckSettings} template used as the fallback when a check omits
 * a field. Checks may still override any individual key.
 */
public final class DefaultSettings {

    private final CheckSettings defaults;

    public DefaultSettings(UpdraftAC plugin) {
        this(plugin.config().checks());
    }

    public DefaultSettings(FileConfiguration checks) {
        ConfigurationSection d = checks.getConfigurationSection("defaults");
        this.defaults = loadDefaults(d);
    }

    private static CheckSettings loadDefaults(ConfigurationSection d) {
        double decay = 1.0;
        int cancelVl = 5;
        TreeMap<Integer, List<String>> tiers = new TreeMap<>();
        if (d != null) {
            decay = d.getDouble("decay", 1.0);
            cancelVl = d.getInt("cancel-vl", 5);
            ConfigurationSection ts = d.getConfigurationSection("action-tiers");
            if (ts != null) {
                for (String key : ts.getKeys(false)) {
                    try {
                        int thr = Integer.parseInt(key);
                        List<String> acts = new ArrayList<>();
                        for (String a : ts.getStringList(key)) acts.add(a.toUpperCase());
                        tiers.put(thr, acts);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        if (tiers.isEmpty()) {
            tiers.put(5, list("ALERT"));
            tiers.put(10, list("ALERT", "CANCEL"));
            tiers.put(25, list("ALERT", "CANCEL", "KICK"));
        }
        return CheckSettings.of(true, 100, decay, cancelVl, tiers);
    }

    private static List<String> list(String... s) {
        List<String> l = new ArrayList<>();
        for (String x : s) l.add(x);
        return l;
    }

    public CheckSettings defaults() { return defaults; }

    /** Build settings for a check at {@code path.<checkId>}. */
    public CheckSettings forCheck(FileConfiguration checks, String category, String id) {
        ConfigurationSection section = checks.getConfigurationSection(category + "." + id);
        return CheckSettings.fromSection(section, defaults);
    }
}
