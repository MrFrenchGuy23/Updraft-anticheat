package com.updraft.anticheat.util;

import org.bukkit.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Color/format helpers that work on both Paper (Adventure) and Spigot (legacy).
 * <p>
 * All internal plugin text is stored in legacy '&amp;' format and converted
 * to a plain colored {@link String} via {@link #color(String)} before being
 * sent to receivers. Adventure components are only used where the API exists.
 */
public final class ChatUtil {

    /** RGB hex pattern, e.g. {@code &#ff0000}. */
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private ChatUtil() {}

    /** Translate '&amp;' color codes (and optional {@code &#RRGGBB}) into a legacy-colored string. */
    public static String color(String input) {
        if (input == null || input.isEmpty()) return "";
        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            try {
                int rgb = Integer.parseInt(hex, 16);
                matcher.appendReplacement(sb,
                        ChatColor.COLOR_CHAR + "x"
                                + ChatColor.COLOR_CHAR + ((rgb >> 20) & 0xF)
                                + ChatColor.COLOR_CHAR + ((rgb >> 16) & 0xF)
                                + ChatColor.COLOR_CHAR + ((rgb >> 12) & 0xF)
                                + ChatColor.COLOR_CHAR + ((rgb >> 8) & 0xF)
                                + ChatColor.COLOR_CHAR + ((rgb >> 4) & 0xF)
                                + ChatColor.COLOR_CHAR + (rgb & 0xF));
            } catch (NumberFormatException ignored) {
                matcher.appendReplacement(sb, matcher.group());
            }
        }
        matcher.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    /** Strip color codes from a string. */
    public static String strip(String input) {
        return input == null ? "" : ChatColor.stripColor(color(input));
    }

    /** Apply placeholders of the form {@code %key%}. */
    public static String format(String input, String... keyValues) {
        if (input == null) return "";
        String out = input;
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            out = out.replace("%" + keyValues[i] + "%", keyValues[i + 1] == null ? "" : keyValues[i + 1]);
        }
        return out;
    }

    /** Convenience: format then color in one step. */
    public static String formatColor(String input, String... keyValues) {
        return color(format(input, keyValues));
    }
}
