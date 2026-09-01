package com.updraft.anticheat.checks.combat;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.updraft.anticheat.UpdraftAC;
import com.updraft.anticheat.checks.api.Check;
import com.updraft.anticheat.checks.api.CheckType;
import com.updraft.anticheat.data.PlayerData;
import com.updraft.anticheat.util.MathUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;

/**
 * Reach: flags attacks where the distance between the attacker's eye position
 * and the target entity exceeds the configured {@code max-distance}.
 * <p>
 * A small buffer (0.5 blocks by default) is added to compensate for latency and
 * Bukkit entity position interpolation.
 * <p>
 * Bukkit world/entity state is only touched on the main thread — the lookup and
 * measurement are deferred to a scheduled task instead of running on the
 * PacketEvents netty thread.
 */
public final class Reach extends Check {

    public Reach(UpdraftAC plugin) { super(plugin, CheckType.REACH); }

    @Override
    public void onPacketReceive(PacketReceiveEvent event, PlayerData data) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;

        WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
        if (interact.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

        org.bukkit.entity.Player player = data.player();
        if (player == null) return;

        final int entityId = interact.getEntityId();
        final double ping = data.ping();
        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline() || player.isDead()) return;

                // target entity lookup by entity id (matches the client's attack packet)
                org.bukkit.entity.Entity target = null;
                for (org.bukkit.entity.Entity e : player.getWorld().getNearbyEntities(player.getLocation(), 6, 6, 6)) {
                    if (e.getEntityId() == entityId) { target = e; break; }
                }
                if (!(target instanceof LivingEntity)) return;

                // eye → target bounding box center distance
                org.bukkit.Location eye = player.getEyeLocation();
                org.bukkit.Location targetLoc = target.getLocation()
                        .add(0, ((LivingEntity) target).getEyeHeight() / 2, 0);

                double dx = eye.getX() - targetLoc.getX();
                double dy = eye.getY() - targetLoc.getY();
                double dz = eye.getZ() - targetLoc.getZ();
                double dist = MathUtil.distance3d(dx, dy, dz);

                double maxDist = plugin.config().checks().getDouble("combat.reach.max-distance", 3.5);
                // ping-based buffer: +0.03 per 50ms of ping
                double pingBuffer = Math.min(ping * 0.03 / 50.0, 0.5);
                if (dist > maxDist + pingBuffer) {
                    fail(data, String.format("dist=%.2f max=%.2f", dist, maxDist));
                }
            });
        } catch (IllegalStateException ignored) {
            // plugin is disabled/shutting down
        }
    }
}
