package com.updraft.anticheat.checks.combat;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.updraft.anticheat.UpdraftAC;
import com.updraft.anticheat.checks.api.Check;
import com.updraft.anticheat.checks.api.CheckType;
import com.updraft.anticheat.data.PlayerData;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Criticals: detects fake critical hits.
 * <p>
 * Crit damage is decided server-side (the attacker must actually be falling),
 * so a client can only fake a crit by spoofing {@code onGround=false} while
 * standing still. That signature is: a multi-tick "airborne" streak with no
 * real vertical motion — no jump (positive dy) and no fall (negative dy).
 * A genuine jump-arc apex attack never matches because the streak records the
 * jump's initial upward velocity.
 */
public final class Criticals extends Check {

    /** Minimum airborne ticks before a flat-dy attack is treated as a spoof. */
    private static final int MIN_AIR_TICKS = 3;
    /** Largest vertical motion allowed during a spoofed airborne streak. */
    private static final double MAX_SPOOFED_VERTICAL = 0.1;

    private final ConcurrentHashMap<UUID, Streak> streaks = new ConcurrentHashMap<>();

    public Criticals(UpdraftAC plugin) { super(plugin, CheckType.CRITICALS); }

    private static final class Streak {
        int airTicks;
        double maxVertical;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event, PlayerData data) {
        // Track the airborne streak from movement packets so it reflects real
        // elapsed time, not just the moments the player attacks.
        if (event.getPacketType() == PacketType.Play.Client.PLAYER_POSITION
                || event.getPacketType() == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION) {
            updateStreak(data);
            return;
        }

        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;

        WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
        if (interact.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

        org.bukkit.entity.Player player = data.player();
        if (player == null) return;

        // None of these states can produce a legit crit from flat ground.
        if (player.isGliding() || player.isSwimming()
                || player.isInWater() || player.isInLava()
                || player.getVehicle() != null) {
            streaks.remove(data.uuid());
            return;
        }

        if (data.onGround()) {
            // Honest ground hit — a normal attack, nothing to detect.
            streaks.remove(data.uuid());
            return;
        }

        Streak streak = streaks.get(data.uuid());
        if (streak == null) return; // no movement packets seen yet this streak

        // Airborne for several ticks without ever moving up or down means the
        // client is spoofing onGround=false while standing still.
        if (streak.airTicks >= MIN_AIR_TICKS && streak.maxVertical < MAX_SPOOFED_VERTICAL) {
            fail(data, "ground-spoof airTicks=" + streak.airTicks
                    + " maxDy=" + String.format("%.3f", streak.maxVertical));
        }
    }

    private void updateStreak(PlayerData data) {
        if (data.onGround()) {
            streaks.remove(data.uuid());
            return;
        }
        Streak streak = streaks.computeIfAbsent(data.uuid(), k -> new Streak());
        streak.airTicks++;
        double dy = Math.abs(data.dy());
        if (dy > streak.maxVertical) streak.maxVertical = dy;
    }
}
