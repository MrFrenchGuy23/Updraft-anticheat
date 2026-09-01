package com.updraft.anticheat.xray;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.chunk.impl.v_1_18.Chunk_v1_18;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.DataPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.GlobalPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.Palette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.PaletteType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import com.updraft.anticheat.UpdraftAC;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.logging.Level;

/**
 * Packet-level anti-xray. Hides configured ores from players who lack the bypass
 * permission by rewriting the chunk data, single-block-change and multi-block-change
 * packets they receive. The hidden ore is replaced with a disguise block (stone /
 * deepslate / netherrack depending on Y and world), so the client's world view
 * stays internally consistent.
 * <p>
 * Runs on the netty send thread. Every handler is defensive: if a packet cannot
 * be decoded the original bytes are forwarded untouched (PacketEvents only
 * re-encodes a packet when a wrapper fully finishes reading).
 */
public final class XrayPacketListener extends PacketListenerAbstract {

    private final UpdraftAC plugin;

    public XrayPacketListener(UpdraftAC plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        XrayManager xray = plugin.xray();
        if (xray == null || !xray.enabled()) return;

        Player player = resolvePlayer(event);
        if (player == null || !xray.isEnabled(player.getWorld())) return;
        if (player.hasPermission(xray.bypassPermission())) return;
        if (com.updraft.anticheat.util.PermissionUtil.isBypass(player, "antixray")) return;

        PacketTypeCommon pt = event.getPacketType();
        try {
            if (pt == PacketType.Play.Server.CHUNK_DATA) {
                handleChunkData(event, xray, player.getWorld());
            } else if (xray.obfuscate() && pt == PacketType.Play.Server.BLOCK_CHANGE) {
                handleBlockChange(event, xray, player.getWorld());
            } else if (xray.obfuscate() && pt == PacketType.Play.Server.MULTI_BLOCK_CHANGE) {
                handleMultiBlockChange(event, xray, player.getWorld());
            }
        } catch (Throwable t) {
            if (plugin.config().config().getBoolean("general.verbose", false)) {
                plugin.getLogger().log(Level.FINE, "Anti-xray failed on " + pt, t);
            }
        }
    }

    // ===================== chunk data =====================

    private void handleChunkData(PacketSendEvent event, XrayManager xray, World world) {
        WrapperPlayServerChunkData wrapper = new WrapperPlayServerChunkData(event);
        Column column = wrapper.getColumn();
        if (column == null) return;

        int minY = 0;
        try {
            minY = event.getUser().getDimensionType().getMinY();
        } catch (Throwable ignored) { /* fall back to 0 */ }

        BaseChunk[] chunks = column.getChunks();
        if (chunks == null) return;
        for (int index = 0; index < chunks.length; index++) {
            BaseChunk base = chunks[index];
            if (!(base instanceof Chunk_v1_18 section)) continue;
            if (section.isEmpty()) continue;
            DataPalette data = section.getChunkData();
            if (!paletteContainsHidden(xray, data)) continue;
            hideSection(section, data, xray, world, minY + (index << 4));
        }
    }

    private void hideSection(Chunk_v1_18 section, DataPalette data, XrayManager xray,
                             World world, int sectionBaseY) {
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int id = data.get(x, y, z);
                    if (xray.isHidden(id)) {
                        section.set(x, y, z, xray.disguiseState(sectionBaseY + y, world.getEnvironment()));
                    }
                }
            }
        }
    }

    /** Cheap pre-check: does this section's palette hold any hidden block state? */
    private boolean paletteContainsHidden(XrayManager xray, DataPalette data) {
        Palette palette = data.palette;
        if (palette == GlobalPalette.INSTANCE) {
            if (data.storage == null) return false;
            int n = PaletteType.CHUNK.getStorageSize();
            for (int i = 0; i < n; i++) {
                if (xray.isHidden(data.storage.get(i))) return true;
            }
            return false;
        }
        int size = palette.size();
        for (int i = 0; i < size; i++) {
            if (xray.isHidden(palette.idToState(i))) return true;
        }
        return false;
    }

    // ===================== block changes =====================

    private void handleBlockChange(PacketSendEvent event, XrayManager xray, World world) {
        WrapperPlayServerBlockChange wrapper = new WrapperPlayServerBlockChange(event);
        if (xray.isHidden(wrapper.getBlockId())) {
            wrapper.setBlockID(xray.disguiseId(wrapper.getBlockPosition().getY(), world.getEnvironment()));
        }
    }

    private void handleMultiBlockChange(PacketSendEvent event, XrayManager xray, World world) {
        WrapperPlayServerMultiBlockChange wrapper = new WrapperPlayServerMultiBlockChange(event);
        WrapperPlayServerMultiBlockChange.EncodedBlock[] blocks = wrapper.getBlocks();
        if (blocks == null) return;
        int disguise = -1;
        for (WrapperPlayServerMultiBlockChange.EncodedBlock block : blocks) {
            if (xray.isHidden(block.getBlockId())) {
                if (disguise == -1) {
                    disguise = xray.disguiseId(block.getY(), world.getEnvironment());
                }
                block.setBlockId(disguise);
            }
        }
    }

    // ===================== helpers =====================

    private Player resolvePlayer(PacketSendEvent event) {
        try {
            com.github.retrooper.packetevents.protocol.player.User user = event.getUser();
            if (user == null) return null;
            UUID uuid = user.getUUID();
            if (uuid != null) return Bukkit.getPlayer(uuid);
            String name = user.getName();
            if (name != null) return Bukkit.getPlayerExact(name);
        } catch (Throwable ignored) {}
        return null;
    }
}
