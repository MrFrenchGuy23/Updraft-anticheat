package com.updraft.anticheat.checks.movement;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.updraft.anticheat.UpdraftAC;
import com.updraft.anticheat.checks.api.Check;
import com.updraft.anticheat.checks.api.CheckType;
import com.updraft.anticheat.data.PlayerData;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Timer: flags players who send movement packets at a rate higher than the
 * server expects (20 packets/second). Cheats speed up the game tick by
 * sending packets faster.
 * <p>
 * Uses a rolling window of packet timestamps to compute the actual rate.
 * Runs on the packet thread, because it must observe every packet — the
 * per-tick (main thread) hook would only see one sample per server tick.
 */
public final class Timer extends Check {

    private static final int WINDOW_SIZE = 80; // 4 seconds at 20/s — plenty of history

    private final Map<UUID, long[]> packetTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> packetIndex = new ConcurrentHashMap<>();

    public Timer(UpdraftAC plugin) { super(plugin, CheckType.TIMER); }

    @Override
    public void onPacketReceive(PacketReceiveEvent event, PlayerData data) {
        if (!isMovement(event.getPacketType())) return;

        UUID id = data.uuid();
        long[] times = packetTimes.computeIfAbsent(id, k -> new long[WINDOW_SIZE]);
        int idx = packetIndex.merge(id, 1, Integer::sum);
        long now = System.currentTimeMillis();
        times[(idx - 1) % WINDOW_SIZE] = now;

        // Need at least a full second of data
        if (idx < 20) return;

        // How many packets arrived within the last 1000ms?
        int count = 0;
        long cutoff = now - 1000;
        for (long t : times) {
            if (t >= cutoff) count++;
        }

        double deviation = plugin.config().checks()
                .getDouble("movement.timer.balance-deviation-percent", 5.0) / 100.0;
        double maxPackets = 20.0 * (1.0 + deviation);

        if (count > maxPackets) {
            fail(data, String.format("rate=%.1f/s max=%.1f/s", (double) count, maxPackets));
        }
    }

    private static boolean isMovement(PacketTypeCommon pt) {
        return pt == PacketType.Play.Client.PLAYER_FLYING
                || pt == PacketType.Play.Client.PLAYER_POSITION
                || pt == PacketType.Play.Client.PLAYER_ROTATION
                || pt == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION;
    }
}
