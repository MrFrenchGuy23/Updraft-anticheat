package com.updraft.anticheat.api;

import com.updraft.anticheat.checks.api.CheckType;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired synchronously when a player fails a check, before punishments run.
 * <p>
 * Cancelling the event prevents VL addition, alerts, and punishment. Third
 * party plugins can use this to whitelist a player or suppress noise.
 */
public final class PlayerViolationEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final CheckType check;
    private final int vl;
    private final String detail;
    private final boolean bypassing;
    private boolean cancelled;

    public PlayerViolationEvent(Player player, CheckType check, int vl, String detail, boolean bypassing) {
        super(); // synchronous
        this.player = player;
        this.check = check;
        this.vl = vl;
        this.detail = detail;
        this.bypassing = bypassing;
    }

    public Player player() { return player; }
    public CheckType check() { return check; }
    public int vl() { return vl; }
    public String detail() { return detail; }
    public boolean isBypassing() { return bypassing; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
