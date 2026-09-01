package com.updraft.anticheat.checks.api;

/**
 * Checks an anticheat performs. The {@link #id} matches the key used in
 * {@code checks.yml} (e.g. {@code combat.killaura}) and is used to look up
 * per-check {@link com.updraft.anticheat.config.CheckSettings settings}.
 *
 * <p>Adding a new check is a one-liner here plus a {@code Check} subclass.</p>
 */
public enum CheckType {

    // Combat
    KILLAURA(Category.COMBAT, "killaura", "KillAura"),
    REACH(Category.COMBAT, "reach", "Reach"),
    CRITICALS(Category.COMBAT, "criticals", "Criticals"),
    AUTOCLICKER(Category.COMBAT, "autoclicker", "AutoClicker"),
    VELOCITY(Category.COMBAT, "velocity", "Velocity"),

    // Movement
    FLY(Category.MOVEMENT, "fly", "Fly"),
    SPEED(Category.MOVEMENT, "speed", "Speed"),
    NOFALL(Category.MOVEMENT, "nofall", "NoFall"),
    JESUS(Category.MOVEMENT, "jesus", "Jesus"),
    STEP(Category.MOVEMENT, "step", "Step"),
    SPIDER(Category.MOVEMENT, "spider", "Spider"),
    TIMER(Category.MOVEMENT, "timer", "Timer"),
    GLIDE(Category.MOVEMENT, "glide", "Glide"),

    // World
    FASTBREAK(Category.WORLD, "fastbreak", "FastBreak"),
    FASTPLACE(Category.WORLD, "fastplace", "FastPlace"),
    NUKER(Category.WORLD, "nuker", "Nuker"),
    SCAFFOLD(Category.WORLD, "scaffold", "Scaffold"),
    TOWER(Category.WORLD, "tower", "Tower"),
    REACHBLOCK(Category.WORLD, "reachblock", "ReachBlock"),

    // Player
    FASTEAT(Category.PLAYER, "fasteat", "FastEat"),
    FASTBOW(Category.PLAYER, "fastbow", "FastBow"),
    INVENTORYMOVE(Category.PLAYER, "inventorymove", "InventoryMove"),
    BLINK(Category.PLAYER, "blink", "Blink"),
    AIM(Category.PLAYER, "aim", "Aim"),

    // Client
    BRAND(Category.CLIENT, "brand", "ClientBrand"),
    MODS(Category.CLIENT, "mods", "ClientMods"),
    COMMAND(Category.CLIENT, "command", "Command");

    private final Category category;
    private final String id;
    private final String display;

    CheckType(Category category, String id, String display) {
        this.category = category;
        this.id = id;
        this.display = display;
    }

    public Category category() { return category; }
    /** Lower-case id (without category prefix), matches {@code checks.yml}. */
    public String id() { return id; }
    /** Full id, e.g. {@code combat.killaura}. */
    public String fullId() { return category.name().toLowerCase() + "." + id; }
    public String display() { return display; }

    /** Resolve by id, e.g. {@code killaura} or {@code combat.killaura}. */
    public static CheckType byId(String id) {
        if (id == null) return null;
        String norm = id.toLowerCase();
        for (CheckType t : values()) {
            if (t.id.equals(norm) || t.fullId().equals(norm)) return t;
        }
        return null;
    }
}
