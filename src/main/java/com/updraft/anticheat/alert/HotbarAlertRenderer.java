package com.updraft.anticheat.alert;

import com.updraft.anticheat.UpdraftAC;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/**
 * Sends alert text on the action bar (hotbar). Uses Paper's
 * {@code Player#sendActionBar} via reflection when available, falling back to
 * Spigot's {@code Player.Spigot#sendMessage} bar variant.
 */
public final class HotbarAlertRenderer {

    private final UpdraftAC plugin;
    private boolean enabled;
    private Method sendActionBarPaper;

    public HotbarAlertRenderer(UpdraftAC plugin) {
        this.plugin = plugin;
        reload();
        try {
            sendActionBarPaper = Player.class.getMethod("sendActionBar", String.class);
        } catch (NoSuchMethodException ignored) {
            try {
                sendActionBarPaper = Player.class.getMethod("sendActionBar",
                        Class.forName("net.kyori.adventure.text.Component"));
            } catch (Throwable ignored2) { /* pure spigot path below */ }
        }
    }

    public void reload() {
        this.enabled = plugin.config().config().getBoolean("alerts.hotbar-enabled", true);
    }

    public boolean enabled() { return enabled; }

    public void send(Player player, String message) {
        if (player == null || message == null) return;
        try {
            if (sendActionBarPaper != null
                    && sendActionBarPaper.getParameterTypes()[0] == String.class) {
                sendActionBarPaper.invoke(player, message);
                return;
            }
        } catch (Throwable ignored) { /* fall through */ }
        try {
            // Spigot fallback: player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent)
            Object spigot = Player.class.getMethod("spigot").invoke(player);
            Class<?> bcClass = Class.forName("org.bukkit.command.CommandSender$Spigot");
            Class<?> tcClass = Class.forName("net.md_5.bungee.api.chat.BaseComponent");
            Class<?> ctcClass = Class.forName("net.md_5.bungee.api.chat.TextComponent");
            Class<?> chatMsgClass = Class.forName("net.md_5.bungee.api.ChatMessageType");
            Object actionBar = Enum.valueOf((Class<? extends Enum>) chatMsgClass, "ACTION_BAR");
            Object component = tcClass.getConstructor(String.class).newInstance(message);
            bcClass.getMethod("sendMessage", chatMsgClass, tcClass).invoke(spigot, actionBar, component);
        } catch (Throwable ignored) {
            // last resort: plain chat
            player.sendMessage(message);
        }
    }
}
