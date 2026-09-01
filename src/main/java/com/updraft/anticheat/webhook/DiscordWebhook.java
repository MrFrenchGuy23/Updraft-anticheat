package com.updraft.anticheat.webhook;

import com.updraft.anticheat.UpdraftAC;
import com.updraft.anticheat.checks.api.Check;
import com.updraft.anticheat.data.PlayerData;
import com.updraft.anticheat.punishment.Action;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Posts violation alerts to a Discord webhook.
 * <p>
 * Uses a single-thread scheduler for backpressure: each (player, check) pair
 * has its own cooldown configured in {@code config.yml discord.cooldown-ms}.
 * The webhook JSON is hand-rolled to avoid pulling in a JSON dependency beyond
 * what the server already ships (Gson is provided by Paper).
 */
public final class DiscordWebhook {

    private final UpdraftAC plugin;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "updraft-webhook");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, Long> lastPostMs = new ConcurrentHashMap<>();

    private volatile boolean enabled;
    private volatile String url;
    private volatile String username;
    private volatile String avatar;
    private volatile long cooldownMs;
    private volatile int minVl;
    private volatile List<String> triggers;

    public DiscordWebhook(UpdraftAC plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        this.enabled = plugin.config().config().getBoolean("discord.enabled", false);
        this.url = plugin.config().config().getString("discord.url", "");
        this.username = plugin.config().config().getString("discord.username", "Updraft AC");
        this.avatar = plugin.config().config().getString("discord.avatar-url", "");
        this.cooldownMs = plugin.config().config().getLong("discord.cooldown-ms", 5000);
        this.minVl = plugin.config().config().getInt("discord.min-vl", 5);
        this.triggers = plugin.config().config().getStringList("discord.trigger-on")
                .stream().map(String::toUpperCase).toList();
    }

    public boolean enabled() {
        return enabled && url != null && !url.isBlank();
    }

    public void onViolation(Check check, PlayerData data, int vl, List<String> actions) {
        if (!enabled()) return;
        if (vl < minVl) return;
        boolean triggered = false;
        for (String a : actions) {
            if (triggers.contains(a.toUpperCase())) { triggered = true; break; }
        }
        if (!triggered) return;

        String key = data.uuid() + ":" + check.shortId();
        long now = System.currentTimeMillis();
        Long last = lastPostMs.get(key);
        if (last != null && now - last < cooldownMs) return;
        lastPostMs.put(key, now);

        String json = buildJson(check, data, vl, actions);
        executor.submit(() -> post(json));
    }

    private String buildJson(Check check, PlayerData data, int vl, List<String> actions) {
        String serverId = plugin.config().config().getString("general.server-id", "");
        String title = "Flag: " + check.type().display() + " (" + vl + " VL)";
        String desc = "**Player:** " + escape(data.name()) + "\n"
                + "**Check:** " + check.shortId() + " *" + check.category().display() + "*\n"
                + "**VL:** " + vl + " / " + check.settings().maxVl() + "\n"
                + "**Ping:** " + data.ping() + "ms\n"
                + "**Brand:** " + escape(data.clientBrand()) + "\n"
                + "**Actions:** " + String.join(", ", actions)
                + (serverId.isEmpty() ? "" : "\n**Server:** " + escape(serverId));
        String colorHex = vl >= 25 ? "15548997" : vl >= 10 ? "16098851" : "16776960"; // red/orange/yellow
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"username\":\"").append(escape(username)).append("\",");
        if (avatar != null && !avatar.isBlank())
            sb.append("\"avatar_url\":\"").append(escape(avatar)).append("\",");
        sb.append("\"embeds\":[{");
        sb.append("\"title\":\"").append(escape(title)).append("\",");
        sb.append("\"description\":\"").append(desc).append("\",");
        sb.append("\"color\":").append(colorHex).append(',');
        sb.append("\"timestamp\":\"").append(java.time.Instant.now().toString()).append('"');
        sb.append("}]}");
        return sb.toString();
    }

    private void post(String json) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("User-Agent", "UpdraftAC");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            if (code >= 400) {
                plugin.getLogger().warning("Discord webhook returned " + code);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Discord webhook post failed: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** Minimal JSON string escaper. */
    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    public void shutdown() {
        executor.shutdownNow();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
