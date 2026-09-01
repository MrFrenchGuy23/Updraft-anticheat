package com.updraft.anticheat.checks.combat;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.updraft.anticheat.UpdraftAC;
import com.updraft.anticheat.checks.api.Check;
import com.updraft.anticheat.checks.api.CheckType;
import com.updraft.anticheat.data.PlayerData;

/**
 * AutoClicker: detects abnormally high and consistent clicks-per-second.
 * <p>
 * Tracks click timestamps in a 1-second sliding window. If the CPS exceeds
 * the configured {@code max-cps}, the check fails.
 */
public final class AutoClicker extends Check {

    public AutoClicker(UpdraftAC plugin) { super(plugin, CheckType.AUTOCLICKER); }

    @Override
    public void onPacketReceive(PacketReceiveEvent event, PlayerData data) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;

        WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
        if (interact.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

        long now = System.currentTimeMillis();
        long windowStart = data.cpsWindowStart();
        int clicks = data.cpsClicks();

        // slide the window: if more than 1 second has passed, reset
        if (now - windowStart > 1000L) {
            clicks = 1;
            windowStart = now;
        } else {
            clicks++;
        }
        data.cpsClicks(clicks);
        data.cpsWindowStart(windowStart);

        int maxCps = plugin.config().checks().getInt("combat.autoclicker.max-cps", 16);
        if (clicks > maxCps) {
            fail(data, "cps=" + clicks);
        }
    }
}
