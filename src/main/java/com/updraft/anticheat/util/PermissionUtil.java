package com.updraft.anticheat.util;

import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permissible;

/**
 * Permission helpers. A check id is lowercase (e.g. {@code killaura}), and maps to
 * the {@code updraft.exempt.<id>} / {@code updraft.bypass.<id>} nodes.
 */
public final class PermissionUtil {

    public static final String EXEMPT_PREFIX = "updraft.exempt.";
    public static final String BYPASS_PREFIX = "updraft.bypass.";
    public static final String EXEMPT_ALL = "updraft.exempt.*";
    public static final String BYPASS_ALL = "updraft.bypass.*";
    public static final String ADMIN = "updraft.*";

    private PermissionUtil() {}

    public static boolean has(Permissible p, String node) {
        return p != null && node != null && p.isPermissionSet(node) ? p.hasPermission(node)
                : p != null && p.hasPermission(node);
    }

    public static boolean isExempt(Permissible p, String checkId) {
        return has(p, EXEMPT_PREFIX + checkId) || has(p, EXEMPT_ALL) || has(p, ADMIN);
    }

    public static boolean isBypass(Permissible p, String checkId) {
        return has(p, BYPASS_PREFIX + checkId) || has(p, BYPASS_ALL) || has(p, ADMIN);
    }

    public static boolean canSeeAlerts(CommandSender sender) {
        return has(sender, "updraft.alerts") || has(sender, ADMIN);
    }
}
