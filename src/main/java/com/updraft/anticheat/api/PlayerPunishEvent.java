package com.updraft.anticheat.api;

import com.updraft.anticheat.checks.api.CheckType;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.List;

/**
 * Fired immediately before punishments (kick, ban command, LP demote) are applied
 * for a violation. Cancelling prevents all punishment actions for this event.
 */
public final class PlayerPunishEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final CheckType check;
    private final int vl;
    private final List<String> actions;
    private boolean cancelled;

    public PlayerPunishEvent(Player player, CheckType check, int vl, List<String> actions) {
        super();
        this.player = player;
        this.check = check;
        this.vl = vl;
        this.actions = actions;
    }

    public Player player() { return player; }
    public CheckType check() { return check; }
    public int vl() { return vl; }
    public List<String> actions() { return actions; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
