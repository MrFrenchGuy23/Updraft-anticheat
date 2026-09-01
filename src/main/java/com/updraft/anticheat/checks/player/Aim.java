package com.updraft.anticheat.checks.player;

import com.updraft.anticheat.UpdraftAC;
import com.updraft.anticheat.checks.api.Check;
import com.updraft.anticheat.checks.api.CheckType;
import com.updraft.anticheat.data.PlayerData;
import com.updraft.anticheat.util.MathUtil;

import java.util.LinkedList;

/**
 * Aim: flags suspiciously mechanical aim patterns.
 * <p>
 * Detects:
 * <ul>
 *   <li>Constant yaw deltas (cheats often snap by fixed increments)</li>
 *   <li>Aimbot-style "perfect" aim with near-zero variance between hits</li>
 * </ul>
 */
public final class Aim extends Check {

    private final java.util.Map<java.util.UUID, LinkedList<Double>> yawDeltas = new java.util.concurrent.ConcurrentHashMap<>();

    public Aim(UpdraftAC plugin) { super(plugin, CheckType.AIM); }

    @Override
    public void onTick(PlayerData data) {
        double deltaYaw = MathUtil.angleDelta(data.yaw(), data.lastYaw());
        if (deltaYaw < 0.5) return; // ignore negligible movement

        LinkedList<Double> window = yawDeltas.computeIfAbsent(data.uuid(), k -> new LinkedList<>());
        window.addLast(deltaYaw);
        if (window.size() > 20) window.removeFirst();

        if (window.size() < 10) return;

        double[] arr = new double[window.size()];
        int i = 0;
        for (Double d : window) arr[i++] = d;
        double stddev = MathUtil.stddev(arr);

        // Aimbot-style cheats produce extremely low variance — the deltas are nearly identical.
        if (stddev < 0.5) {
            fail(data, String.format("robotic-aim stddev=%.3f", stddev));
        }
    }
}
