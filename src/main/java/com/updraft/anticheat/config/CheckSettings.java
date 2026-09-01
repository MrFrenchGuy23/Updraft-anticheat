package com.updraft.anticheat.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Typed snapshot of a single check's configuration block under {@code checks.yml}.
 * <p>
 * Resolves {@code action-tiers} into a sorted-by-threshold map so the
 * {@code ViolationManager} can look up the active tier via binary search.
 */
public final class CheckSettings {

    private final boolean enabled;
    private final int maxVl;
    private final double decay;
    private final int cancelVl;
    /** Sorted ascending by threshold. */
    private final TreeMap<Integer, List<String>> actionTiers;

    private CheckSettings(boolean enabled, int maxVl, double decay, int cancelVl,
                          TreeMap<Integer, List<String>> actionTiers) {
        this.enabled = enabled;
        this.maxVl = maxVl;
        this.decay = decay;
        this.cancelVl = cancelVl;
        this.actionTiers = actionTiers;
    }

    /** Construct directly from raw values (used by {@code DefaultSettings}). */
    public static CheckSettings of(boolean enabled, int maxVl, double decay, int cancelVl,
                                   TreeMap<Integer, List<String>> actionTiers) {
        return new CheckSettings(enabled, maxVl, decay, cancelVl, actionTiers);
    }

    public static CheckSettings fromSection(ConfigurationSection section, CheckSettings defaults) {
        if (section == null) return defaults;
        boolean enabled = section.getBoolean("enabled", defaults.enabled);
        int maxVl = section.getInt("max-vl", defaults.maxVl);
        double decay = section.getDouble("decay", defaults.decay);
        int cancelVl = section.getInt("cancel-vl", defaults.cancelVl);

        TreeMap<Integer, List<String>> tiers;
        ConfigurationSection tierSection = section.getConfigurationSection("action-tiers");
        if (tierSection != null) {
            tiers = new TreeMap<>();
            for (String key : tierSection.getKeys(false)) {
                try {
                    int threshold = Integer.parseInt(key);
                    List<String> actions = new ArrayList<>();
                    for (String a : tierSection.getStringList(key)) {
                        actions.add(a.toUpperCase());
                    }
                    tiers.put(threshold, actions);
                } catch (NumberFormatException ignored) { /* skip bad key */ }
            }
        } else {
            tiers = new TreeMap<>(defaults.actionTiers);
        }
        return new CheckSettings(enabled, maxVl, decay, cancelVl, tiers);
    }

    /** Returns the actions for the highest threshold that {@code vl} satisfies (or empty list). */
    public List<String> actionsFor(int vl) {
        Map.Entry<Integer, List<String>> e = actionTiers.floorEntry(vl);
        return e == null ? new ArrayList<>() : e.getValue();
    }

    public boolean isEnabled() { return enabled; }
    public int maxVl() { return maxVl; }
    public double decay() { return decay; }
    public int cancelVl() { return cancelVl; }
    public TreeMap<Integer, List<String>> actionTiers() { return actionTiers; }
}
