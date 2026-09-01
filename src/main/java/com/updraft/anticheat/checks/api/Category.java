package com.updraft.anticheat.checks.api;

/**
 * High-level category for a check. Used for grouping in commands and stats.
 */
public enum Category {
    COMBAT("Combat"),
    MOVEMENT("Movement"),
    WORLD("World"),
    PLAYER("Player"),
    CLIENT("Client");

    private final String display;

    Category(String display) { this.display = display; }

    public String display() { return display; }
}
