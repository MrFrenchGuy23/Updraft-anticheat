package com.updraft.anticheat.checks.client;

import com.updraft.anticheat.UpdraftAC;
import com.updraft.anticheat.checks.api.Check;
import com.updraft.anticheat.checks.api.CheckType;
import com.updraft.anticheat.data.PlayerData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ClientModCheck: flags players who have registered plugin message channels,
 * a client brand matching a known cheat-mod signature, a parsed Forge/NeoForge
 * handshake mod id, a recognizable plugin-message payload, or a derived client
 * type matching a configured signature. All signatures live in {@code checks.yml}.
 * <p>
 * Each entry under {@code mods} can match ANY of these signals:
 * <pre>
 *   - name: "MyCheat"
 *     channels:        [ "MYCHANNEL" ]     # plugin-message channels seen/registered
 *     brand-contains:  [ "mycheat" ]       # substrings inside the client brand
 *     mod-ids:         [ "mycheat" ]       # ids parsed from the FML handshake
 *     payload-contains: [ "mycheat" ]      # substrings inside any plugin payload
 *     client-types:    [ FORGE ]           # VANILLA/FORGE/NEOFORGE/FABRIC/QUILT/LITELOADER
 * </pre>
 */
public final class ClientModCheck extends Check {

    @SuppressWarnings("unchecked")
    public ClientModCheck(UpdraftAC plugin) { super(plugin, CheckType.MODS); }

    @SuppressWarnings("unchecked")
    public void check(PlayerData data) {
        List<?> mods = plugin.config().checks().getList("mods");
        if (mods.isEmpty()) return;

        String brand = data.clientBrand() == null ? "" : data.clientBrand().toLowerCase(Locale.ROOT);
        String clientType = data.clientType() == null ? "" : data.clientType().toUpperCase(Locale.ROOT);
        Collection<String> channels = data.pluginChannels();
        Collection<String> modIds = data.detectedModIds();
        Collection<String> payloads = data.pluginPayloads().values();

        for (Object rawMod : mods) {
            if (!(rawMod instanceof Map)) continue;
            Map<String, Object> mod = (Map<String, Object>) rawMod;

            String name = String.valueOf(mod.getOrDefault("name", "?"));
            List<String> sigChannels = toList(mod.get("channels"));
            List<String> sigBrand = toList(mod.get("brand-contains"));
            List<String> sigModIds = toList(mod.get("mod-ids"));
            List<String> sigPayload = toList(mod.get("payload-contains"));
            List<String> sigTypes = toList(mod.get("client-types"));

            String signal = null;
            if (matchesAny(channels, sigChannels)) {
                signal = "channel";
            } else if (matchesContains(brand, sigBrand)) {
                signal = "brand";
            } else if (matchesAny(modIds, sigModIds)) {
                signal = "mod-id";
            } else if (matchesPayload(payloads, sigPayload)) {
                signal = "payload";
            } else if (matchesAny(Collections.singleton(clientType), sigTypes)) {
                signal = "client-type";
            }

            if (signal != null) {
                fail(data, name + " (" + signal + ")");
                return; // one flag per scan is enough
            }
        }
    }

    @Override
    public void onBukkitEvent(org.bukkit.event.Event event, PlayerData data) {
        check(data);
    }

    /** Case-insensitive equality of any signature against any collected value. */
    private static boolean matchesAny(Collection<String> values, List<String> signatures) {
        if (signatures.isEmpty() || values.isEmpty()) return false;
        for (String sig : signatures) {
            if (sig == null) continue;
            for (String v : values) {
                if (v != null && v.equalsIgnoreCase(sig)) return true;
            }
        }
        return false;
    }

    /** Case-insensitive substring match against a single haystack (brand / client type). */
    private static boolean matchesContains(String haystack, List<String> needles) {
        if (needles.isEmpty() || haystack.isEmpty()) return false;
        for (String n : needles) {
            if (n != null && haystack.contains(n.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    /** Case-insensitive substring match against any retained plugin-message payload. */
    private static boolean matchesPayload(Collection<String> payloads, List<String> needles) {
        if (needles.isEmpty() || payloads.isEmpty()) return false;
        for (String n : needles) {
            if (n == null || n.isEmpty()) continue;
            String needle = n.toLowerCase(Locale.ROOT);
            for (String p : payloads) {
                if (p != null && p.toLowerCase(Locale.ROOT).contains(needle)) return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static List<String> toList(Object o) {
        if (o instanceof List<?>) {
            List<String> out = new ArrayList<>();
            for (Object item : (List<?>) o) {
                out.add(item == null ? null : item.toString());
            }
            return out;
        }
        return Collections.emptyList();
    }
}
