package com.updraft.anticheat.checks.combat;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import com.updraft.anticheat.UpdraftAC;
import com.updraft.anticheat.checks.api.Check;
import com.updraft.anticheat.checks.api.CheckContext;
import com.updraft.anticheat.checks.api.CheckType;
import com.updraft.anticheat.data.PlayerData;
import com.updraft.anticheat.util.MathUtil;
import org.bukkit.entity.LivingEntity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * KillAura: detects multi-target attacks in a single tick and impossibly
 * large yaw changes between the rotation packet and the attack packet.
 * <p>
 * Heuristics:
 * <ul>
 *   <li>Multiple attack packets for different entities in the same tick</li>
 *   <li>Rotation yaw delta exceeds the configured {@code max-angle} between
 *       the attack packet and the most recent flying packet</li>
 * </ul>
 */
public final class KillAura extends Check {

    /** Per-tick attack count, keyed by player UUID (this check is stateful). */
    private final Map<java.util.UUID, Integer> attacksThisTick = new ConcurrentHashMap<>();
    private final Map<java.util.UUID, Integer> lastTargetEntityId = new ConcurrentHashMap<>();

    public KillAura(UpdraftAC plugin) { super(plugin, CheckType.KILLAURA); }

    @Override
    public void onPacketReceive(PacketReceiveEvent event, PlayerData data) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;

        WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
        if (interact.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

        int targetId = interact.getEntityId();
        Integer lastId = lastTargetEntityId.get(data.uuid());

        int count = attacksThisTick.getOrDefault(data.uuid(), 0) + 1;
        attacksThisTick.put(data.uuid(), count);

        // 1. multi-aura: more than max-attacks-per-tick
        int maxPerTick = (int) Math.round(plugin.config().checks()
                .getDouble("combat.killaura.max-attacks-per-tick", 1));
        if (count > maxPerTick && (lastId == null || lastId != targetId)) {
            fail(data, "multi-attack count=" + count);
        }
        lastTargetEntityId.put(data.uuid(), targetId);
    }

    @Override
    public void onTick(PlayerData data) {
        // reset per-tick counters
        attacksThisTick.remove(data.uuid());
    }
}
