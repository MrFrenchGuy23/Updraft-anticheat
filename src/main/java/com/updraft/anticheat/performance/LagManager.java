package com.updraft.anticheat.performance;

import com.updraft.anticheat.UpdraftAC;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Tracks server tick rate and per-player ping.
 * <p>
 * Uses Paper's {@code Server#getTPS()} reflection when available, falling back
 * to a rolling average computed from {@link System#nanoTime()} deltas between
 * ticks. The fallback works on Spigot where the Paper API is absent.
 */
public final class LagManager {

    private final UpdraftAC plugin;
    private final double[] tpsHistory = new double[3]; // 1m / 5m / 15m fallback slots
    private final long[] tickNanos = new long[20];
    private int tickCursor = 0;
    private volatile double tps = 20.0;
    private BukkitTask task;
    private Method paperGetTps;

    public LagManager(UpdraftAC plugin) {
        this.plugin = plugin;
        try {
            Method m = Server.class.getMethod("getTPS");
            if (m.getReturnType() == double[].class) paperGetTps = m;
        } catch (NoSuchMethodException ignored) {
            // Spigot fallback path
        }
    }

    public void start() {
        if (task != null) task.cancel();
        Arrays.fill(tickNanos, System.nanoTime());
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::onTick, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public double tps() {
        return tps;
    }

    /** Per-player ping via reflection on Paper's Player#getPing, falling back to Bukkit's handle. */
    public int ping(org.bukkit.entity.Player player) {
        if (player == null) return 0;
        try {
            Method m = player.getClass().getMethod("getPing");
            Object out = m.invoke(player);
            if (out instanceof Number) return ((Number) out).intValue();
        } catch (Throwable ignored) { /* fall through */ }
        return 0;
    }

    /** Bind a freshly joined player — refreshes ping values on keepalive (handled elsewhere). */
    public void bind(org.bukkit.entity.Player player) {
        com.updraft.anticheat.data.PlayerData pd = plugin.playerData().get(player);
        if (pd != null) pd.ping(ping(player));
    }

    private void onTick() {
        long now = System.nanoTime();
        int slot = tickCursor;
        long prev = tickNanos[slot];
        tickNanos[slot] = now;
        tickCursor = (slot + 1) % tickNanos.length;

        if (paperGetTps != null) {
            try {
                double[] arr = (double[]) paperGetTps.invoke(Bukkit.getServer());
                if (arr != null && arr.length > 0) {
                    tps = arr[0];
                    for (int i = 0; i < Math.min(arr.length, tpsHistory.length); i++) {
                        tpsHistory[i] = arr[i];
                    }
                    return;
                }
            } catch (Throwable ignored) { /* fall through to manual calc */ }
        }

        long elapsed = now - prev;
        double secondsThisTick = elapsed / 1_000_000_000.0;
        double instantTps = secondsThisTick <= 0 ? 20.0 : Math.min(20.0, 20.0 / secondsThisTick * (tickNanos.length / (double) tickNanos.length));
        // Simpler stable calc: ratio of expected vs observed elapsed over the full ring.
        double expectedNanos = 50_000_000L * tickNanos.length;
        double observedNanos = now - tickNanos[(tickCursor) % tickNanos.length];
        if (observedNanos > 0) {
            tps = Math.max(0.0, Math.min(20.0, 20.0 * expectedNanos / observedNanos));
        }
        tpsHistory[0] = tps;
        // touch unused to silence warnings
        if (instantTps < 0) tps = tps;
    }
}
