package com.updraft.anticheat.data.storage;

import com.updraft.anticheat.UpdraftAC;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * HikariCP-backed storage manager supporting SQLite (default, zero-config) and MySQL.
 * <p>
 * All writes go onto a {@link BlockingQueue} and are drained to the database
 * by a single dedicated writer thread, removing per-event JDBC cost.
 * <p>
 * If the database cannot be reached, the plugin continues operating with
 * in-memory violation tracking — the worst case is that logs aren't persisted.
 */
public final class DatabaseManager {

    private final UpdraftAC plugin;
    private final BlockingQueue<WriteOp> queue = new LinkedBlockingQueue<>(10_000);
    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private final AtomicInteger lastId = new AtomicInteger(0);

    private HikariDataSource dataSource;
    private String backend = "disabled";
    private Thread writerThread;

    public DatabaseManager(UpdraftAC plugin) {
        this.plugin = plugin;
    }

    public void enable() throws SQLException {
        String type = plugin.config().config().getString("storage.type", "sqlite").toLowerCase();
        try {
            if ("mysql".equals(type)) {
                dataSource = openMySql();
                backend = "MySQL";
            } else {
                dataSource = openSqlite();
                backend = "SQLite";
            }
            try (Connection c = dataSource.getConnection()) {
                createSchema(c);
            }
            enabled.set(true);
            startWriter();
        } catch (SQLException e) {
            closeQuietly();
            enabled.set(false);
            throw e;
        }
    }

    public void disable() {
        enabled.set(false);
        if (writerThread != null) {
            writerThread.interrupt();
            try { writerThread.join(1000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }
        closeQuietly();
    }

    /** Returns true if the DB is open and accepting writes. */
    public boolean isEnabled() { return enabled.get(); }

    public String backendName() { return backend; }

    /** Returns a live connection for synchronous reads. */
    public Connection connection() throws SQLException {
        if (!enabled.get() || dataSource == null) throw new SQLException("storage disabled");
        return dataSource.getConnection();
    }

    // ====================== writes ======================

    public void insertViolation(ViolationRecord r) {
        offer(new WriteOp(WriteOp.Kind.VIOLATION, r));
    }

    public void insertPunishment(PunishmentRecord r) {
        offer(new WriteOp(WriteOp.Kind.PUNISHMENT, r));
    }

    public void insertCommand(CommandRecord r) {
        offer(new WriteOp(WriteOp.Kind.COMMAND, r));
    }

    public void upsertMeta(PlayerMetaRecord r) {
        offer(new WriteOp(WriteOp.Kind.META, r));
    }

    private boolean offer(WriteOp op) {
        if (!enabled.get()) return false;
        return queue.offer(op);
    }

    /** Drains and writes the queue. Called by both the writer thread and the periodic flush task. */
    public void flush() {
        if (!enabled.get()) return;
        List<WriteOp> batch = new ArrayList<>(128);
        queue.drainTo(batch, 128);
        if (batch.isEmpty()) return;
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                for (WriteOp op : batch) writeOp(c, op);
                c.commit();
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "DB flush failed (" + e.getMessage() + ")", e);
        }
    }

    private void writeOp(Connection c, WriteOp op) throws SQLException {
        switch (op.kind) {
            case VIOLATION -> insertViolation(c, (ViolationRecord) op.record);
            case PUNISHMENT -> insertPunishment(c, (PunishmentRecord) op.record);
            case COMMAND -> insertCommand(c, (CommandRecord) op.record);
            case META -> upsertMeta(c, (PlayerMetaRecord) op.record);
        }
    }

    private void startWriter() {
        writerThread = new Thread(this::writerLoop, "updraft-db-writer");
        writerThread.setDaemon(true);
        writerThread.start();
    }

    private void writerLoop() {
        while (enabled.get()) {
            try {
                WriteOp head = queue.poll(1, TimeUnit.SECONDS);
                if (head == null) continue;
                List<WriteOp> batch = new ArrayList<>(64);
                batch.add(head);
                queue.drainTo(batch, 63);
                try (Connection c = dataSource.getConnection()) {
                    c.setAutoCommit(false);
                    try {
                        for (WriteOp op : batch) writeOp(c, op);
                        c.commit();
                    } catch (SQLException ex) {
                        c.rollback();
                        plugin.getLogger().log(Level.WARNING, "DB batch rolled back", ex);
                    } finally {
                        c.setAutoCommit(true);
                    }
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "DB writer error", t);
                sleepQuietly(1000);
            }
        }
    }

    private void sleepQuietly(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }

    private void closeQuietly() {
        if (dataSource != null) {
            try { dataSource.close(); } catch (Throwable ignored) {}
            dataSource = null;
        }
    }

    // ====================== connections ======================

    private HikariDataSource openSqlite() {
        java.io.File folder = plugin.getDataFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder.");
        }
        String dbFile = new java.io.File(folder, "updraft.db").getAbsolutePath();
        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("UpdraftAC-SQLite");
        cfg.setDriverClassName("org.sqlite.JDBC");
        cfg.setJdbcUrl("jdbc:sqlite:" + dbFile);
        cfg.setMaximumPoolSize(1); // SQLite serializes writes
        cfg.addDataSourceProperty("cachePrepStmts", "true");
        cfg.addDataSourceProperty("foreign_keys", "true");
        return new HikariDataSource(cfg);
    }

    private HikariDataSource openMySql() {
        var c = plugin.config().config().getConfigurationSection("storage.mysql");
        String host = c.getString("host", "localhost");
        int port = c.getInt("port", 3306);
        String db = c.getString("database", "minecraft");
        String user = c.getString("username", "root");
        String pass = c.getString("password", "");
        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("UpdraftAC-MySQL");
        cfg.setDriverClassName("com.mysql.cj.jdbc.Driver");
        cfg.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + db
                + "?useSSL=false&useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC");
        cfg.setUsername(user);
        cfg.setPassword(pass);
        int maxPool = c.getInt("properties.maximum-pool-size", 10);
        cfg.setMaximumPoolSize(maxPool);
        cfg.setMinimumIdle(c.getInt("properties.minimum-idle", 2));
        cfg.setConnectionTimeout(c.getLong("properties.connection-timeout-ms", 10000));
        cfg.setIdleTimeout(c.getLong("properties.idle-timeout-ms", 600000));
        cfg.setMaxLifetime(c.getLong("properties.max-lifetime-ms", 1800000));
        cfg.addDataSourceProperty("cachePrepStmts", "true");
        cfg.addDataSourceProperty("prepStmtCacheSize", "250");
        cfg.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        return new HikariDataSource(cfg);
    }

    private void createSchema(Connection c) throws SQLException {
        boolean sqlite = isSqlite(c);
        // SQLite and MySQL use different auto-increment syntax; a shared DDL
        // must be avoided or the default SQLite backend can never start.
        String idCol = sqlite
                ? "INTEGER PRIMARY KEY AUTOINCREMENT"
                : "BIGINT PRIMARY KEY AUTO_INCREMENT";
        try (Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS up_violations ("
                    + "  id " + idCol + ","
                    + "  uuid VARCHAR(36) NOT NULL,"
                    + "  player VARCHAR(32) NOT NULL,"
                    + "  check_id VARCHAR(48) NOT NULL,"
                    + "  category VARCHAR(16) NOT NULL,"
                    + "  vl REAL NOT NULL,"
                    + "  ping INTEGER NOT NULL DEFAULT 0,"
                    + "  server_id VARCHAR(64),"
                    + "  details VARCHAR(255),"
                    + "  time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                    + ")");
            st.execute("CREATE TABLE IF NOT EXISTS up_punishments ("
                    + "  id " + idCol + ","
                    + "  uuid VARCHAR(36) NOT NULL,"
                    + "  player VARCHAR(32) NOT NULL,"
                    + "  check_id VARCHAR(48) NOT NULL,"
                    + "  action VARCHAR(24) NOT NULL,"
                    + "  vl REAL NOT NULL,"
                    + "  server_id VARCHAR(64),"
                    + "  time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                    + ")");
            st.execute("CREATE TABLE IF NOT EXISTS up_player_meta ("
                    + "  uuid VARCHAR(36) PRIMARY KEY,"
                    + "  player VARCHAR(32) NOT NULL,"
                    + "  client_brand VARCHAR(64),"
                    + "  client_version VARCHAR(32),"
                    + "  total_vl REAL NOT NULL DEFAULT 0,"
                    + "  last_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                    + ")");
            st.execute("CREATE TABLE IF NOT EXISTS up_commands ("
                    + "  id " + idCol + ","
                    + "  uuid VARCHAR(36) NOT NULL,"
                    + "  player VARCHAR(32) NOT NULL,"
                    + "  command VARCHAR(255) NOT NULL,"
                    + "  world VARCHAR(64),"
                    + "  server_id VARCHAR(64),"
                    + "  time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                    + ")");
            // Indexes for the common lookup paths.
            st.execute("CREATE INDEX IF NOT EXISTS idx_violations_uuid ON up_violations(uuid)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_violations_time ON up_violations(time)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_punishments_uuid ON up_punishments(uuid)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_commands_uuid ON up_commands(uuid)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_commands_time ON up_commands(time)");
        }
    }

    private static boolean isSqlite(Connection c) {
        try {
            String name = c.getMetaData().getDatabaseProductName();
            return name != null && name.toLowerCase(java.util.Locale.ROOT).contains("sqlite");
        } catch (SQLException e) {
            return false;
        }
    }

    /** Delete log rows older than {@code retentionDays} days (0/negative disables). */
    public void purgeOld(int retentionDays) {
        if (retentionDays <= 0 || !enabled.get() || dataSource == null) return;
        long cutoff = System.currentTimeMillis() - retentionDays * 86_400_000L;
        Timestamp ts = new Timestamp(cutoff);
        String[] tables = { "up_violations", "up_punishments", "up_commands" };
        try (Connection c = dataSource.getConnection()) {
            for (String table : tables) {
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM " + table + " WHERE time < ?")) {
                    ps.setTimestamp(1, ts);
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Log retention purge failed", e);
        }
    }

    // ====================== prepared statements ======================

    private void insertViolation(Connection c, ViolationRecord r) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO up_violations(uuid, player, check_id, category, vl, ping, server_id, details, time) "
                        + "VALUES (?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, r.uuid);
            ps.setString(2, r.player);
            ps.setString(3, r.checkId);
            ps.setString(4, r.category);
            ps.setDouble(5, r.vl);
            ps.setInt(6, r.ping);
            ps.setString(7, r.serverId);
            ps.setString(8, r.details);
            ps.setTimestamp(9, new Timestamp(r.timeMs));
            ps.executeUpdate();
        }
    }

    private void insertPunishment(Connection c, PunishmentRecord r) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO up_punishments(uuid, player, check_id, action, vl, server_id, time) "
                        + "VALUES (?,?,?,?,?,?,?)")) {
            ps.setString(1, r.uuid);
            ps.setString(2, r.player);
            ps.setString(3, r.checkId);
            ps.setString(4, r.action);
            ps.setDouble(5, r.vl);
            ps.setString(6, r.serverId);
            ps.setTimestamp(7, new Timestamp(r.timeMs));
            ps.executeUpdate();
        }
    }

    private void insertCommand(Connection c, CommandRecord r) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO up_commands(uuid, player, command, world, server_id, time) "
                        + "VALUES (?,?,?,?,?,?)")) {
            ps.setString(1, r.uuid);
            ps.setString(2, r.player);
            ps.setString(3, r.command);
            ps.setString(4, r.world);
            ps.setString(5, r.serverId);
            ps.setTimestamp(6, new Timestamp(r.timeMs));
            ps.executeUpdate();
        }
    }

    private void upsertMeta(Connection c, PlayerMetaRecord r) throws SQLException {
        // REPLACE works on both SQLite and MySQL; avoid DB-specific MERGE syntax.
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT OR REPLACE INTO up_player_meta(uuid, player, client_brand, client_version, total_vl, last_seen) "
                        + "VALUES (?,?,?,?,?,?)")) {
            ps.setString(1, r.uuid);
            ps.setString(2, r.player);
            ps.setString(3, r.clientBrand);
            ps.setString(4, r.clientVersion);
            ps.setDouble(5, r.totalVl);
            ps.setTimestamp(6, new Timestamp(r.timeMs));
            ps.executeUpdate();
        }
    }

    // ====================== records / helpers ======================

    private static final class WriteOp {
        enum Kind { VIOLATION, PUNISHMENT, COMMAND, META }
        final Kind kind;
        final Object record;
        WriteOp(Kind kind, Object record) { this.kind = kind; this.record = record; }
    }

    public record ViolationRecord(String uuid, String player, String checkId, String category,
                                  double vl, int ping, String serverId, String details, long timeMs) {}

    public record PunishmentRecord(String uuid, String player, String checkId, String action,
                                   double vl, String serverId, long timeMs) {}

    public record CommandRecord(String uuid, String player, String command, String world,
                                String serverId, long timeMs) {}

    public record PlayerMetaRecord(String uuid, String player, String clientBrand,
                                   String clientVersion, double totalVl, long timeMs) {}
}
