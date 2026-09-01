package com.updraft.anticheat.net;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPluginMessage;
import com.updraft.anticheat.UpdraftAC;
import com.updraft.anticheat.checks.api.Check;
import com.updraft.anticheat.checks.api.CheckContext;
import com.updraft.anticheat.checks.api.CheckType;
import com.updraft.anticheat.data.PlayerData;
import com.updraft.anticheat.util.PermissionUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Central PacketEvents listener. Dispatches packets to all registered checks
 * and updates per-player {@link PlayerData} for movement, brand, and channel
 * registration.
 */
public final class PacketEventsListener extends PacketListenerAbstract {

    private final UpdraftAC plugin;

    public PacketEventsListener(UpdraftAC plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        Player player = resolvePlayer(event);
        if (player == null) return;
        PlayerData data = plugin.playerData().get(player);
        if (data == null) return;

        // Consume a pending CANCEL request: drop the next packet from a cheater.
        if (data.consumeCancel()) {
            event.setCancelled(true);
        }

        PacketTypeCommon pt = event.getPacketType();

        // 1. Update movement state from flying packets
        if (isFlyingType(pt)) {
            handleFlying(event, data);
        }

        // 2. Plugin messages (client brand + custom channels)
        if (pt == PacketType.Play.Client.PLUGIN_MESSAGE) {
            handleClientPluginMessage(event, data);
        }

        // 3. Dispatch to all checks (per-check exemption so velocity etc. can opt out)
        CheckContext ctx = new CheckContext(plugin, data);
        boolean globallyExempt = ctx.isExempt() || PermissionUtil.isExempt(player, "*");
        for (Check check : plugin.checks().all()) {
            if (!check.enabled()) continue;
            if (globallyExempt && !check.ignoreContextExemption()) continue;
            try {
                check.onPacketReceive(event, data);
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING,
                        "Check " + check.id() + " threw on receive", t);
            }
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        Player player = resolvePlayer(event);
        if (player == null) return;
        PlayerData data = plugin.playerData().get(player);
        if (data == null) return;

        PacketTypeCommon pt = event.getPacketType();

        // Update ping on keepalive send
        if (pt == PacketType.Play.Server.KEEP_ALIVE || pt == PacketType.Play.Server.PING) {
            data.lastKeepAliveMs(System.currentTimeMillis());
            data.ping(plugin.lag().ping(player));
        }

        // Server plugin message — capture register/unregister channel names
        if (pt == PacketType.Play.Server.PLUGIN_MESSAGE) {
            try {
                WrapperPlayServerPluginMessage msg = new WrapperPlayServerPluginMessage(event);
                String channel = msg.getChannelName();
                // Nothing actionable here — client->server register packets
                // are handled in onPacketReceive via WrapperPlayClientPluginMessage.
            } catch (Throwable ignored) { /* defensive */ }
        }

        CheckContext ctx = new CheckContext(plugin, data);
        boolean globallyExempt = ctx.isExempt() || PermissionUtil.isExempt(player, "*");
        for (Check check : plugin.checks().all()) {
            if (!check.enabled()) continue;
            if (globallyExempt && !check.ignoreContextExemption()) continue;
            try {
                check.onPacketSend(event, data);
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING,
                        "Check " + check.id() + " threw on send", t);
            }
        }
    }

    // ===================== helpers =====================

    private boolean isFlyingType(PacketTypeCommon pt) {
        return pt == PacketType.Play.Client.PLAYER_FLYING
                || pt == PacketType.Play.Client.PLAYER_POSITION
                || pt == PacketType.Play.Client.PLAYER_ROTATION
                || pt == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION;
    }

    private void handleFlying(PacketReceiveEvent event, PlayerData data) {
        try {
            WrapperPlayClientPlayerFlying flying = new WrapperPlayClientPlayerFlying(event);
            boolean onGround = flying.isOnGround();
            if (flying.hasPositionChanged()) {
                com.github.retrooper.packetevents.protocol.world.Location loc = flying.getLocation();
                data.updatePosition(loc.getX(), loc.getY(), loc.getZ(),
                        loc.getYaw(), loc.getPitch(), onGround);
            } else if (flying.hasRotationChanged()) {
                // Rotation-only packet — its embedded position is (0,0,0), so
                // only refresh the look direction and ground flag.
                com.github.retrooper.packetevents.protocol.world.Location loc = flying.getLocation();
                data.updateRotation(loc.getYaw(), loc.getPitch(), onGround);
            } else {
                // Pure flight-status packet (client standing still).
                data.updateGround(onGround);
            }
        } catch (Throwable t) {
            if (plugin.config().config().getBoolean("general.verbose", false)) {
                plugin.getLogger().log(Level.FINE, "flying parse error", t);
            }
        }
    }

    private void handleClientPluginMessage(PacketReceiveEvent event, PlayerData data) {
        try {
            WrapperPlayClientPluginMessage msg = new WrapperPlayClientPluginMessage(event);
            String channel = msg.getChannelName();
            byte[] payload = msg.getData();
            // Lossless 1:1 byte->char decode so binary payloads stay searchable.
            String payloadText = payload == null ? "" : new String(payload, StandardCharsets.ISO_8859_1);
            data.recordPluginMessage(channel, payloadText);

            if (isBrandChannel(channel)) {
                String brand = new String(payload, StandardCharsets.UTF_8)
                        .replace("\u0000", "").trim();
                data.clientBrand(brand);
                try {
                    com.github.retrooper.packetevents.protocol.player.ClientVersion cv =
                            event.getUser().getClientVersion();
                    if (cv != null) {
                        data.clientVersion(cv.getReleaseName() == null
                                ? cv.toString() : cv.getReleaseName());
                    }
                } catch (Throwable ignored) {}
                data.clientType(deriveClientType(data));
                trigger(data, "brand");
            } else if (isRegisterChannel(channel)) {
                // Register packet payload = null-separated channel names.
                String[] names = payloadText.split("\u0000");
                for (String n : names) {
                    if (!n.isEmpty()) {
                        data.registeredChannels().put(n, System.currentTimeMillis());
                        data.pluginChannels().add(n);
                    }
                }
                data.clientType(deriveClientType(data));
            }

            // Forge / NeoForge FML handshake — extract the actual installed mod list.
            if (isFmlChannel(channel)) {
                boolean changed = false;
                for (String modId : parseFmlModList(payload)) {
                    if (data.detectedModIds().add(modId)) changed = true;
                }
                if (changed) {
                    data.clientType(deriveClientType(data));
                    triggerMods(data);
                }
            }

            // Re-evaluate signatures whenever brand / channel state changed.
            if (isBrandChannel(channel) || isRegisterChannel(channel)) {
                triggerMods(data);
            }
        } catch (Throwable ignored) { /* defensive — wrapper parse can fail */ }
    }

    /** Run the check with the given short id against this player's data. */
    private void trigger(PlayerData data, String shortId) {
        for (Check c : plugin.checks().all()) {
            if (shortId.equals(c.shortId())) {
                try { c.onBukkitEvent(null, data); } catch (Throwable ignored) {}
                return;
            }
        }
    }

    private void triggerMods(PlayerData data) {
        trigger(data, "mods");
    }

    private static boolean isBrandChannel(String c) {
        return "minecraft:brand".equals(c) || "MC|Brand".equals(c);
    }

    private static boolean isRegisterChannel(String c) {
        return "minecraft:register".equals(c) || "MC|Register".equals(c);
    }

    private static boolean isFmlChannel(String c) {
        return c != null && (c.startsWith("FML|") || c.startsWith("fml:")
                || c.startsWith("forge:") || c.startsWith("neoforge:"));
    }

    // ===================== client type derivation =====================

    /** Derive the client type from the brand string and observed channels. */
    private String deriveClientType(PlayerData data) {
        String brand = data.clientBrand() == null ? "" : data.clientBrand().toLowerCase(Locale.ROOT);
        Set<String> channels = data.pluginChannels();

        if (brand.contains("neoforge") || hasAnyPrefix(channels, "neoforge:")) return "NEOFORGE";
        if (brand.contains("forge") || brand.contains("fml")
                || hasAnyPrefix(channels, "FML|", "fml:", "forge:")) return "FORGE";
        if (brand.contains("quilt") || hasAnyPrefix(channels, "quilt:")) return "QUILT";
        if (brand.contains("fabric") || hasAnyPrefix(channels, "fabric:")) return "FABRIC";
        if (brand.contains("liteloader") || hasAnyPrefix(channels, "LITE|")) return "LITELOADER";
        if ("vanilla".equals(brand) || brand.isEmpty() || "unknown".equals(brand)) return "VANILLA";
        return "UNKNOWN";
    }

    private static boolean hasAnyPrefix(Set<String> channels, String... prefixes) {
        for (String c : channels) {
            for (String p : prefixes) {
                if (c.startsWith(p)) return true;
            }
        }
        return false;
    }

    // ===================== Forge/NeoForge mod-list parsing =====================

    private static final Pattern MOD_ID_PATTERN = Pattern.compile("^[a-z0-9_-]{1,64}$");
    private static final int MAX_PARSED_MODS = 64;

    /**
     * Extract mod ids from an FML handshake payload. Tolerates both the legacy
     * (1.7-1.12, {@code FML|HS}, 4-byte count + short-length strings) and modern
     * (1.13+, {@code fml:handshake}, VarInt count + VarInt-length strings)
     * encodings, with or without the leading discriminator byte. Returns an
     * empty list when the payload is not a mod list.
     */
    private static List<String> parseFmlModList(byte[] payload) {
        if (payload == null || payload.length < 5) return Collections.emptyList();
        for (int start = 0; start <= 1; start++) {
            List<String> out = parseModernFml(payload, start);
            if (!out.isEmpty()) return out;
        }
        for (int start = 0; start <= 1; start++) {
            List<String> out = parseLegacyFml(payload, start);
            if (!out.isEmpty()) return out;
        }
        return Collections.emptyList();
    }

    private static List<String> parseModernFml(byte[] data, int start) {
        if (start >= data.length) return Collections.emptyList();
        int[] off = {start};
        int count = readVarInt(data, off);
        if (count < 1 || count > 1000) return Collections.emptyList();
        List<String> out = new ArrayList<>(Math.min(count, MAX_PARSED_MODS));
        for (int i = 0; i < count; i++) {
            String id = readVarIntString(data, off);
            String version = readVarIntString(data, off);
            if (id == null || version == null || !MOD_ID_PATTERN.matcher(id).matches()) {
                return Collections.emptyList();
            }
            out.add(id);
            if (out.size() >= MAX_PARSED_MODS) break;
        }
        return out;
    }

    private static List<String> parseLegacyFml(byte[] data, int start) {
        if (data.length - start < 6) return Collections.emptyList();
        int count = ((data[start] & 0xff) << 24) | ((data[start + 1] & 0xff) << 16)
                | ((data[start + 2] & 0xff) << 8) | (data[start + 3] & 0xff);
        if (count < 1 || count > 1000) return Collections.emptyList();
        int[] off = {start + 4};
        List<String> out = new ArrayList<>(Math.min(count, MAX_PARSED_MODS));
        for (int i = 0; i < count; i++) {
            if (off[0] + 2 > data.length) return Collections.emptyList();
            int idLen = ((data[off[0]] & 0xff) << 8) | (data[off[0] + 1] & 0xff);
            off[0] += 2;
            if (idLen < 1 || off[0] + idLen > data.length) return Collections.emptyList();
            String id = new String(data, off[0], idLen, StandardCharsets.UTF_8);
            off[0] += idLen;
            if (off[0] + 2 > data.length) return Collections.emptyList();
            int verLen = ((data[off[0]] & 0xff) << 8) | (data[off[0] + 1] & 0xff);
            off[0] += 2;
            if (off[0] + verLen > data.length) return Collections.emptyList();
            off[0] += verLen; // version is skipped, not validated
            if (!MOD_ID_PATTERN.matcher(id).matches()) return Collections.emptyList();
            out.add(id);
            if (out.size() >= MAX_PARSED_MODS) break;
        }
        return out;
    }

    private static int readVarInt(byte[] data, int[] off) {
        int result = 0;
        int shift = 0;
        while (off[0] < data.length) {
            byte b = data[off[0]++];
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return result;
            shift += 7;
            if (shift > 35) return -1;
        }
        return -1;
    }

    private static String readVarIntString(byte[] data, int[] off) {
        int len = readVarInt(data, off);
        if (len < 0 || len > 2048 || off[0] + len > data.length) return null;
        String s = new String(data, off[0], len, StandardCharsets.UTF_8);
        off[0] += len;
        return s;
    }

    /** Best-effort player resolution from the PacketEvents user object. */
    private Player resolvePlayer(PacketReceiveEvent event) {
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
