package com.updraft.anticheat.checks.player;

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
 * Blink: flags players who hold back their movement packets for an abnormally
 * long time and then release them in a burst (the classic "blink" cheat).
 * <p>
 * Measures the gap between consecutive movement packets. A standing-still
 * player simply stops sending packets, so we only compare consecutive packet
 * arrival times — never "time since the last packet".
 */
public final class Blink extends Check {

    private final Map<UUID, Long> lastPacketMs = new ConcurrentHashMap<>();

    public Blink(UpdraftAC plugin) { super(plugin, CheckType.BLINK); }

    @Override
    public void onPacketReceive(PacketReceiveEvent event, PlayerData data) {
        if (!isMovement(event.getPacketType())) return;

        long now = System.currentTimeMillis();
        Long last = lastPacketMs.put(data.uuid(), now);
        if (last == null) return; // first packet — no baseline yet

        long gap = now - last;
        long minGap = plugin.config().checks().getLong("player.blink.min-gap-ms", 1000);
        if (gap > minGap) {
            fail(data, String.format("gap=%dms", gap));
        }
    }

    private static boolean isMovement(PacketTypeCommon pt) {
        return pt == PacketType.Play.Client.PLAYER_FLYING
                || pt == PacketType.Play.Client.PLAYER_POSITION
                || pt == PacketType.Play.Client.PLAYER_ROTATION
                || pt == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION;
    }
}
