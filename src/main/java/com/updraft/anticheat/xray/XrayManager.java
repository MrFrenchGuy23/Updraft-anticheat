package com.updraft.anticheat.xray;

import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.updraft.anticheat.UpdraftAC;
import org.bukkit.World;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Anti-xray configuration and block-state resolution.
 * <p>
 * Converts the {@code anti-xray} section of {@code config.yml} into global block
 * state IDs (PacketEvents space). Ores are hidden from affected players on the
 * packet level; the fake block sent in their place is chosen from
 * {@code disguise-blocks} based on the block's Y coordinate and world type.
 */
public final class XrayManager {

    private final UpdraftAC plugin;

    private boolean enabled;
    /** OBFUSCATE also rewrites block-change packets; SIMPLE only hides chunk data. */
    private boolean obfuscate;
    /** Empty list = apply to every world. */
    private List<String> worldWhitelist = Collections.emptyList();
    private String bypassPermission;

    private Set<Integer> hiddenIds = Collections.emptySet();
    private WrappedBlockState disguiseStone;
    private WrappedBlockState disguiseDeepslate;
    private WrappedBlockState disguiseNetherrack;
    private boolean valid;

    public XrayManager(UpdraftAC plugin) {
        this.plugin = plugin;
    }

    /** (Re)read configuration and resolve block states. Runs on the main thread. */
    public void reload() {
        var cfg = plugin.config().config();
        enabled = cfg.getBoolean("anti-xray.enabled", false);
        obfuscate = "OBFUSCATE".equalsIgnoreCase(cfg.getString("anti-xray.engine", "OBFUSCATE"));
        worldWhitelist = cfg.getStringList("anti-xray.world-whitelist");
        bypassPermission = cfg.getString("anti-xray.bypass-permission", "updraft.bypass.antixray");

        hiddenIds = Collections.emptySet();
        valid = false;

        if (!enabled) return;
        if (!plugin.packetEventsAvailable()) {
            enabled = false;
            plugin.getLogger().warning("Anti-xray disabled: PacketEvents is unavailable.");
            return;
        }

        Set<Integer> ids = new HashSet<>();
        for (String raw : cfg.getStringList("anti-xray.hidden-blocks")) {
            int id = resolve(raw);
            if (id >= 0) ids.add(id);
        }
        hiddenIds = ids;

        disguiseStone = resolveState(cfg.getString("anti-xray.disguise-blocks.default", "stone"));
        disguiseDeepslate = resolveState(cfg.getString("anti-xray.disguise-blocks.deepslate", "deepslate"));
        disguiseNetherrack = resolveState(cfg.getString("anti-xray.disguise-blocks.netherrack", "netherrack"));

        valid = !hiddenIds.isEmpty() && disguiseStone != null && disguiseDeepslate != null && disguiseNetherrack != null;
        if (!valid) {
            enabled = false;
            plugin.getLogger().warning("Anti-xray disabled: no hidden blocks resolved. Check 'anti-xray' in config.yml.");
            return;
        }

        plugin.getLogger().info("Anti-xray loaded: " + hiddenIds.size() + " hidden block states, engine="
                + (obfuscate ? "OBFUSCATE" : "SIMPLE")
                + (worldWhitelist.isEmpty() ? " (all worlds)" : ", worlds=" + worldWhitelist));
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean valid() {
        return valid;
    }

    /** OBFUSCATE engine also rewrites block-change packets. */
    public boolean obfuscate() {
        return obfuscate;
    }

    public String bypassPermission() {
        return bypassPermission;
    }

    /** Whether anti-xray applies to the given world (whitelist empty = all worlds). */
    public boolean isEnabled(World world) {
        if (!valid || world == null) return false;
        if (worldWhitelist.isEmpty()) return true;
        String name = world.getName();
        for (String w : worldWhitelist) {
            if (w.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    public boolean isHidden(int globalId) {
        return hiddenIds.contains(globalId);
    }

    public Set<Integer> hiddenIds() {
        return hiddenIds;
    }

    /** Fake block to show for a hidden ore at absolute Y in the given environment. */
    public int disguiseId(int y, World.Environment environment) {
        return disguiseState(y, environment).getGlobalId();
    }

    /** Fake block state for a hidden ore at absolute Y in the given environment. */
    public WrappedBlockState disguiseState(int y, World.Environment environment) {
        if (environment == World.Environment.NETHER) return disguiseNetherrack;
        if (environment == World.Environment.THE_END) return disguiseStone;
        return y < 0 ? disguiseDeepslate : disguiseStone;
    }

    /** Resolve a block-state name to a global ID in the server's mapping space. */
    private int resolve(String name) {
        WrappedBlockState state = resolveState(name);
        return state == null ? -1 : state.getGlobalId();
    }

    private WrappedBlockState resolveState(String name) {
        if (name == null) return null;
        String norm = name.trim().toLowerCase(Locale.ROOT);
        if (norm.startsWith("minecraft:")) norm = norm.substring("minecraft:".length());
        if (norm.isEmpty()) return null;
        if (StateTypes.getByName(norm) == null) {
            plugin.getLogger().warning("Anti-xray: unknown block '" + name + "' in config.yml");
            return null;
        }
        WrappedBlockState state = WrappedBlockState.getByString(norm);
        if (state.getType() == StateTypes.AIR) {
            plugin.getLogger().warning("Anti-xray: no block state data for '" + name + "' on this server version");
            return null;
        }
        return state;
    }
}
