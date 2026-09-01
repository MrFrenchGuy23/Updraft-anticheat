package com.updraft.anticheat.checks.manager;

import com.updraft.anticheat.UpdraftAC;
import com.updraft.anticheat.checks.api.Check;
import com.updraft.anticheat.checks.api.CheckContext;
import com.updraft.anticheat.data.PlayerData;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Owns the lifecycle of every registered {@link Check} and provides a single
 * entry point for dispatching packets/events/ticks to all of them.
 */
public final class CheckManager {

    private final UpdraftAC plugin;
    private final Map<String, Check> checks = new LinkedHashMap<>();
    private final ViolationManager violations;

    public CheckManager(UpdraftAC plugin) {
        this.plugin = plugin;
        this.violations = new ViolationManager(plugin);
        registerDefaults();
    }

    /** Instantiate and register every built-in check. */
    private void registerDefaults() {
        // combat
        register(new com.updraft.anticheat.checks.combat.KillAura(plugin));
        register(new com.updraft.anticheat.checks.combat.Reach(plugin));
        register(new com.updraft.anticheat.checks.combat.Criticals(plugin));
        register(new com.updraft.anticheat.checks.combat.AutoClicker(plugin));
        register(new com.updraft.anticheat.checks.combat.Velocity(plugin));
        // movement
        register(new com.updraft.anticheat.checks.movement.Fly(plugin));
        register(new com.updraft.anticheat.checks.movement.Speed(plugin));
        register(new com.updraft.anticheat.checks.movement.NoFall(plugin));
        register(new com.updraft.anticheat.checks.movement.Jesus(plugin));
        register(new com.updraft.anticheat.checks.movement.Step(plugin));
        register(new com.updraft.anticheat.checks.movement.Spider(plugin));
        register(new com.updraft.anticheat.checks.movement.Timer(plugin));
        register(new com.updraft.anticheat.checks.movement.Glide(plugin));
        // world
        register(new com.updraft.anticheat.checks.world.FastBreak(plugin));
        register(new com.updraft.anticheat.checks.world.FastPlace(plugin));
        register(new com.updraft.anticheat.checks.world.Nuker(plugin));
        register(new com.updraft.anticheat.checks.world.Scaffold(plugin));
        register(new com.updraft.anticheat.checks.world.Tower(plugin));
        register(new com.updraft.anticheat.checks.world.ReachBlock(plugin));
        // player
        register(new com.updraft.anticheat.checks.player.FastEat(plugin));
        register(new com.updraft.anticheat.checks.player.FastBow(plugin));
        register(new com.updraft.anticheat.checks.player.InventoryMove(plugin));
        register(new com.updraft.anticheat.checks.player.Blink(plugin));
        register(new com.updraft.anticheat.checks.player.Aim(plugin));
        // client
        register(new com.updraft.anticheat.checks.client.ClientBrandCheck(plugin));
        register(new com.updraft.anticheat.checks.client.ClientModCheck(plugin));
        register(new com.updraft.anticheat.checks.client.CommandCheck(plugin));
    }

    /** Register a check instance. Idempotent — later registrations override earlier ones. */
    public void register(Check check) {
        checks.put(check.shortId(), check);
        check.reload();
    }

    /** Reload config for every registered check. */
    public void reload() {
        for (Check c : checks.values()) c.reload();
    }

    public Check get(String shortId) { return checks.get(shortId); }

    public Collection<Check> all() { return Collections.unmodifiableCollection(checks.values()); }

    public ViolationManager violations() { return violations; }

    /** Per-second decay task entrypoint. */
    public void applyDecay() {
        for (PlayerData pd : plugin.playerData().all()) {
            violations.decay(pd);
        }
    }

    /**
     * Per-tick entrypoint, run synchronously on the main thread once per server
     * tick for every online player. Movement/physics checks must run here so
     * they can safely touch Bukkit world/player APIs. Players that are
     * contextually exempt are skipped unless the check opts out.
     */
    public void tick() {
        for (PlayerData pd : plugin.playerData().all()) {
            Player player = pd.player();
            if (player == null || !player.isOnline()) continue;
            CheckContext ctx = new CheckContext(plugin, pd);
            boolean globallyExempt = ctx.isExempt();
            for (Check c : checks.values()) {
                if (!c.enabled()) continue;
                if (globallyExempt && !c.ignoreContextExemption()) continue;
                try {
                    c.onTick(pd);
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.WARNING,
                            "Check " + c.id() + " threw onTick", t);
                }
            }
        }
    }
}
