package com.updraft.anticheat.data;

import com.updraft.anticheat.checks.api.Category;
import com.updraft.anticheat.config.CheckSettings;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player state shared across checks.
 * <p>
 * A single instance lives for the duration of a player's session and is read
 * on both the Netty (packet) thread and the main thread. Fields that are written
 * on the packet thread are either {@code volatile} or guarded by being part of
 * a {@link ConcurrentHashMap}.
 */
public final class PlayerData {

    private final UUID uuid;
    private final String name;
    private final Player player;

    // ---- Movement / position tracking ----
    private volatile double x, y, z;
    private volatile double lastX, lastY, lastZ;
    private volatile float yaw, pitch;
    private volatile float lastYaw, lastPitch;
    private volatile boolean onGround;
    private volatile boolean lastOnGround;
    /** Server-side timestamp (ms) of the last accepted movement packet. */
    private volatile long lastMoveMs;
    private volatile long lastTickMs;
    private volatile int tickCount;

    // ---- Network ----
    private volatile int ping;
    private volatile long lastKeepAliveMs;
    private volatile long lastJoinMs;

    // ---- Client brand / mods ----
    private volatile String clientBrand = "unknown";
    private volatile String clientVersion = "unknown";
    /** Registered plugin message channels from this client. */
    private final Map<String, Long> registeredChannels = new ConcurrentHashMap<>();
    /** Derived client type: VANILLA, FORGE, NEOFORGE, FABRIC, QUILT, LITELOADER or UNKNOWN. */
    private volatile String clientType = "VANILLA";
    /** Mod ids extracted from Forge/NeoForge FML handshake payloads. */
    private final Set<String> detectedModIds = ConcurrentHashMap.newKeySet();
    /** Every plugin-message channel this client used or registered (register names included). */
    private final Set<String> pluginChannels = ConcurrentHashMap.newKeySet();
    /** Decoded (ISO-8859-1) payload of the last plugin message per channel, for signature matching. */
    private final Map<String, String> pluginPayloads = new ConcurrentHashMap<>();
    /** Upper bound on retained payload strings to keep per-player memory bounded. */
    private static final int MAX_PLUGIN_PAYLOADS = 32;

    // ---- Exemptions (contextual) ----
    private volatile long lastTeleportMs;
    private volatile long lastDamageMs;
    private volatile long lastVelocityMs;
    private volatile long lastFlightToggleMs;
    private volatile boolean allowFlight;
    private volatile boolean flying;
    private volatile boolean gliding;
    private volatile boolean riptiding;
    private volatile boolean inVehicle;

    // ---- Action timestamps (per-check cooldowns) ----
    private final Map<String, Long> lastFlagMs = new ConcurrentHashMap<>();
    private final Map<String, Long> lastPunishMs = new ConcurrentHashMap<>();

    // ---- Per-check VL ----
    private final Map<String, Double> violations = new ConcurrentHashMap<>();
    private final Map<String, CheckSettings> settings = new ConcurrentHashMap<>();

    // ---- CANCEL action support ----
    /** Set by the CANCEL action; the next inbound packet from this player is dropped. */
    private volatile boolean cancelNextPacket;

    // ---- Player-specific misc ----
    private volatile boolean alertsEnabled = true;
    private volatile int cpsClicks;
    private volatile long cpsWindowStart;

    /** Recent position history for movement predictions (ring buffer). */
    private final double[][] positionHistory = new double[20][3];
    private int positionIndex = 0;

    public PlayerData(Player player) {
        this.player = player;
        this.uuid = player.getUniqueId();
        this.name = player.getName();
        this.lastJoinMs = System.currentTimeMillis();
        Location l = player.getLocation();
        this.x = l.getX(); this.y = l.getY(); this.z = l.getZ();
    }

    public UUID uuid() { return uuid; }
    public String name() { return name; }
    public Player player() { return player; }

    // ===== Position/movement =====
    public void updatePosition(double x, double y, double z, float yaw, float pitch, boolean onGround) {
        this.lastX = this.x; this.lastY = this.y; this.lastZ = this.z;
        this.lastYaw = this.yaw; this.lastPitch = this.pitch;
        this.lastOnGround = this.onGround;
        this.x = x; this.y = y; this.z = z;
        this.yaw = yaw; this.pitch = pitch;
        this.onGround = onGround;
        long now = System.currentTimeMillis();
        this.lastMoveMs = now;
        this.lastTickMs = now;
        this.tickCount++;
        synchronized (positionHistory) {
            double[] slot = positionHistory[positionIndex];
            slot[0] = x; slot[1] = y; slot[2] = z;
            positionIndex = (positionIndex + 1) % positionHistory.length;
        }
    }

    /** Rotation-only packet (no position change): keep x/y/z, refresh look + ground flag. */
    public void updateRotation(float yaw, float pitch, boolean onGround) {
        this.lastYaw = this.yaw;
        this.lastPitch = this.pitch;
        this.lastOnGround = this.onGround;
        this.yaw = yaw;
        this.pitch = pitch;
        this.onGround = onGround;
    }

    /** Pure flight-status packet (no position, no rotation): refresh the ground flag. */
    public void updateGround(boolean onGround) {
        this.lastOnGround = this.onGround;
        this.onGround = onGround;
    }

    public double dx() { return x - lastX; }
    public double dy() { return y - lastY; }
    public double dz() { return z - lastZ; }
    public double horizontalSpeed() {
        double dx = dx(), dz = dz();
        return Math.sqrt(dx * dx + dz * dz);
    }
    public double verticalSpeed() { return Math.abs(dy()); }

    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public double lastY() { return lastY; }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
    public float lastYaw() { return lastYaw; }
    public float lastPitch() { return lastPitch; }
    public boolean onGround() { return onGround; }
    public boolean lastOnGround() { return lastOnGround; }
    public long lastMoveMs() { return lastMoveMs; }
    public long lastTickMs() { return lastTickMs; }
    public int tickCount() { return tickCount; }

    // ===== Network =====
    public int ping() { return ping; }
    public void ping(int ping) { this.ping = ping; }
    public long lastKeepAliveMs() { return lastKeepAliveMs; }
    public void lastKeepAliveMs(long ms) { this.lastKeepAliveMs = ms; }
    public long lastJoinMs() { return lastJoinMs; }
    public void lastJoinMs(long ms) { this.lastJoinMs = ms; }

    // ===== Brand / channels / mod detection =====
    public String clientBrand() { return clientBrand; }
    public void clientBrand(String b) { this.clientBrand = b; }
    public String clientVersion() { return clientVersion; }
    public void clientVersion(String v) { this.clientVersion = v; }
    public Map<String, Long> registeredChannels() { return registeredChannels; }

    public String clientType() { return clientType; }
    public void clientType(String type) {
        this.clientType = type == null || type.isEmpty() ? "VANILLA" : type.toUpperCase(Locale.ROOT);
    }
    public Set<String> detectedModIds() { return detectedModIds; }
    public Set<String> pluginChannels() { return pluginChannels; }
    public Map<String, String> pluginPayloads() { return pluginPayloads; }

    /** Record a plugin message: remember the channel and a decoded payload for matching. */
    public void recordPluginMessage(String channel, String payload) {
        if (channel == null || channel.isEmpty()) return;
        pluginChannels.add(channel);
        if (payload != null && !payload.isEmpty()) {
            if (pluginPayloads.size() >= MAX_PLUGIN_PAYLOADS) {
                var it = pluginPayloads.entrySet().iterator();
                if (it.hasNext()) it.remove();
            }
            pluginPayloads.put(channel, payload);
        }
    }

    // ===== Contextual exemptions =====
    public long lastTeleportMs() { return lastTeleportMs; }
    public void lastTeleportMs(long ms) { this.lastTeleportMs = ms; }
    public long lastDamageMs() { return lastDamageMs; }
    public void lastDamageMs(long ms) { this.lastDamageMs = ms; }
    public long lastVelocityMs() { return lastVelocityMs; }
    public void lastVelocityMs(long ms) { this.lastVelocityMs = ms; }
    public long lastFlightToggleMs() { return lastFlightToggleMs; }
    public void lastFlightToggleMs(long ms) { this.lastFlightToggleMs = ms; }
    public boolean allowFlight() { return allowFlight; }
    public void allowFlight(boolean b) { this.allowFlight = b; }
    public boolean flying() { return flying; }
    public void flying(boolean b) { this.flying = b; }
    public boolean gliding() { return gliding; }
    public void gliding(boolean b) { this.gliding = b; }
    public boolean riptiding() { return riptiding; }
    public void riptiding(boolean b) { this.riptiding = b; }
    public boolean inVehicle() { return inVehicle; }
    public void inVehicle(boolean b) { this.inVehicle = b; }

    // ===== Cooldowns =====
    public long lastFlagMs(String checkId) { return lastFlagMs.getOrDefault(checkId, 0L); }
    public void lastFlagMs(String checkId, long ms) { lastFlagMs.put(checkId, ms); }
    public long lastPunishMs(String checkId) { return lastPunishMs.getOrDefault(checkId, 0L); }
    public void lastPunishMs(String checkId, long ms) { lastPunishMs.put(checkId, ms); }

    // ===== Violations =====
    public double vl(String checkId) { return violations.getOrDefault(checkId, 0.0); }
    public void vl(String checkId, double vl) { violations.put(checkId, vl); }
    public void addVl(String checkId, double add) {
        violations.merge(checkId, add, Double::sum);
    }
    public Map<String, Double> violations() { return violations; }

    public CheckSettings settings(String checkId) {
        return settings.get(checkId);
    }
    public void settings(String checkId, CheckSettings s) { settings.put(checkId, s); }

    // ===== CANCEL action =====
    /** Ask the packet listener to drop the next inbound packet. */
    public void requestCancel() { cancelNextPacket = true; }

    /** Atomically consume the pending cancel request (true = drop the current packet). */
    public boolean consumeCancel() {
        boolean v = cancelNextPacket;
        cancelNextPacket = false;
        return v;
    }

    public boolean alertsEnabled() { return alertsEnabled; }
    public void alertsEnabled(boolean b) { this.alertsEnabled = b; }

    // ===== CPS =====
    public int cpsClicks() { return cpsClicks; }
    public void cpsClicks(int cps) { this.cpsClicks = cps; }
    public long cpsWindowStart() { return cpsWindowStart; }
    public void cpsWindowStart(long ms) { this.cpsWindowStart = ms; }

    /** Total VL across all checks (informational). */
    public double totalVl() {
        double sum = 0;
        for (double v : violations.values()) sum += v;
        return sum;
    }

    /** Category-bucketed totals, used by {@code /updraft info}. */
    public Map<Category, Double> vlByCategory(java.util.function.Function<String, Category> classifier) {
        Map<Category, Double> out = new EnumMap<>(Category.class);
        for (Map.Entry<String, Double> e : violations.entrySet()) {
            Category c = classifier.apply(e.getKey());
            if (c == null) continue;
            out.merge(c, e.getValue(), Double::sum);
        }
        return out;
    }
}
