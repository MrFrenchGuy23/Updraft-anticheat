package com.updraft.anticheat.punishment;

/**
 * An action that can be taken when a player's violation crosses a tier.
 * Order matters: actions are executed left-to-right as configured.
 */
public enum Action {
    /** Cancel the offending packet / action. No side effects. */
    CANCEL,
    /** Send a warning message to the player only. */
    WARN,
    /** Broadcast an alert to staff. (Handled by AlertManager; listed here for completeness.) */
    ALERT,
    /** Disconnect the player. */
    KICK,
    /** Run a configurable console command (e.g. {@code /ban}). */
    BAN_COMMAND,
    /** Demote the player down a LuckPerms track. */
    LP_DEMOTE;

    public static Action parse(String s) {
        if (s == null) return null;
        try { return Action.valueOf(s.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }
}
