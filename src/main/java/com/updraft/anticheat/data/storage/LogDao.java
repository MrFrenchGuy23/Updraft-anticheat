package com.updraft.anticheat.data.storage;

import com.updraft.anticheat.UpdraftAC;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Synchronous read queries for the commands subsystem.
 * <p>
 * Each call opens a short-lived connection from the pool. Callers that don't
 * want to block the main thread should use the {@code Async} variants.
 */
public final class LogDao {

    private final UpdraftAC plugin;

    public LogDao(UpdraftAC plugin) { this.plugin = plugin; }

    public record ViolationRow(String checkId, String category, double vl, int ping, long timeMs) {}
    public record PunishmentRow(String checkId, String action, double vl, long timeMs) {}
    public record CommandRow(String command, String world, long timeMs) {}
    public record MetaRow(String clientBrand, String clientVersion, double totalVl, long lastSeenMs) {}

    /** Latest N violation rows for a player, newest first. */
    public List<ViolationRow> recentViolations(UUID uuid, int limit) {
        List<ViolationRow> out = new ArrayList<>();
        if (!plugin.storage().isEnabled()) return out;
        String sql = "SELECT check_id, category, vl, ping, time FROM up_violations "
                + "WHERE uuid=? ORDER BY time DESC FETCH FIRST ? ROWS ONLY";
        try (Connection c = plugin.storage().connection();
             PreparedStatement ps = c.prepareStatement(sqlFallBack(c, sql))) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new ViolationRow(
                            rs.getString("check_id"),
                            rs.getString("category"),
                            rs.getDouble("vl"),
                            rs.getInt("ping"),
                            rs.getTimestamp("time").getTime()));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "recentViolations failed", e);
        }
        return out;
    }

    public List<PunishmentRow> recentPunishments(UUID uuid, int limit) {
        List<PunishmentRow> out = new ArrayList<>();
        if (!plugin.storage().isEnabled()) return out;
        String sql = "SELECT check_id, action, vl, time FROM up_punishments "
                + "WHERE uuid=? ORDER BY time DESC FETCH FIRST ? ROWS ONLY";
        try (Connection c = plugin.storage().connection();
             PreparedStatement ps = c.prepareStatement(sqlFallBack(c, sql))) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new PunishmentRow(
                            rs.getString("check_id"),
                            rs.getString("action"),
                            rs.getDouble("vl"),
                            rs.getTimestamp("time").getTime()));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "recentPunishments failed", e);
        }
        return out;
    }

    /** Latest N executed commands for a player, newest first. */
    public List<CommandRow> recentCommands(UUID uuid, int limit) {
        List<CommandRow> out = new ArrayList<>();
        if (!plugin.storage().isEnabled()) return out;
        String sql = "SELECT command, world, time FROM up_commands "
                + "WHERE uuid=? ORDER BY time DESC FETCH FIRST ? ROWS ONLY";
        try (Connection c = plugin.storage().connection();
             PreparedStatement ps = c.prepareStatement(sqlFallBack(c, sql))) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new CommandRow(
                            rs.getString("command"),
                            rs.getString("world"),
                            rs.getTimestamp("time").getTime()));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "recentCommands failed", e);
        }
        return out;
    }

    public MetaRow meta(UUID uuid) {
        if (!plugin.storage().isEnabled()) return null;
        try (Connection c = plugin.storage().connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT client_brand, client_version, total_vl, last_seen FROM up_player_meta WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new MetaRow(
                            rs.getString("client_brand"),
                            rs.getString("client_version"),
                            rs.getDouble("total_vl"),
                            rs.getTimestamp("last_seen").getTime());
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "meta failed", e);
        }
        return null;
    }

    public CompletableFuture<List<ViolationRow>> recentViolationsAsync(UUID uuid, int limit) {
        return CompletableFuture.supplyAsync(() -> recentViolations(uuid, limit));
    }

    public CompletableFuture<List<PunishmentRow>> recentPunishmentsAsync(UUID uuid, int limit) {
        return CompletableFuture.supplyAsync(() -> recentPunishments(uuid, limit));
    }

    public CompletableFuture<List<CommandRow>> recentCommandsAsync(UUID uuid, int limit) {
        return CompletableFuture.supplyAsync(() -> recentCommands(uuid, limit));
    }

    public CompletableFuture<MetaRow> metaAsync(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> meta(uuid));
    }

    /**
     * SQLite doesn't understand {@code FETCH FIRST n ROWS ONLY}; fall back to {@code LIMIT}.
     */
    private String sqlFallBack(Connection c, String sql) {
        try {
            String name = c.getMetaData().getDatabaseProductName();
            if (name != null && name.toLowerCase().contains("sqlite")) {
                return sql.replace("FETCH FIRST ? ROWS ONLY", "LIMIT ?");
            }
        } catch (SQLException ignored) {}
        return sql;
    }
}
