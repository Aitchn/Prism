package com.aitchn.prism.core.data;

import com.aitchn.prism.api.data.DataService;
import com.aitchn.prism.api.data.DataStore;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;

/**
 * SQLite backed {@link DataService}. One connection guarded by a lock: simple and safe for the small, mostly
 * key-addressed data mods keep. The SQLite JDBC driver ships inside Paper, so nothing is bundled here.
 */
public final class SqliteDataService implements DataService, AutoCloseable {

    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");

    private final Connection connection;
    private final Object lock = new Object();

    /** @param file the database file, created if missing; {@code null} opens an in-memory database */
    public SqliteDataService(Path file) throws SQLException {
        String url = file == null ? "jdbc:sqlite::memory:" : "jdbc:sqlite:" + file.toAbsolutePath();
        connection = DriverManager.getConnection(url);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("CREATE TABLE IF NOT EXISTS kv (ns TEXT NOT NULL, k TEXT NOT NULL, v TEXT NOT NULL,"
                    + " PRIMARY KEY (ns, k)) WITHOUT ROWID");
        }
    }

    @Override
    public @NotNull DataStore store(@NotNull String namespace) {
        if (!NAMESPACE.matcher(namespace).matches()) {
            throw new IllegalArgumentException("invalid namespace '" + namespace + "', expected [a-z0-9_.-]+");
        }
        return new Store(namespace);
    }

    @Override
    public void close() {
        synchronized (lock) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // closing on shutdown; nothing useful to do
            }
        }
    }

    private final class Store implements DataStore {
        private final String namespace;

        Store(String namespace) {
            this.namespace = namespace;
        }

        @Override
        public @NotNull Optional<String> get(@NotNull String key) {
            synchronized (lock) {
                try (PreparedStatement s = connection.prepareStatement("SELECT v FROM kv WHERE ns=? AND k=?")) {
                    s.setString(1, namespace);
                    s.setString(2, key);
                    try (ResultSet rs = s.executeQuery()) {
                        return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
                    }
                } catch (SQLException e) {
                    throw new IllegalStateException("data store read failed", e);
                }
            }
        }

        @Override
        public void put(@NotNull String key, @NotNull String value) {
            synchronized (lock) {
                try (PreparedStatement s = connection.prepareStatement(
                        "INSERT INTO kv (ns, k, v) VALUES (?,?,?) ON CONFLICT(ns, k) DO UPDATE SET v=excluded.v")) {
                    s.setString(1, namespace);
                    s.setString(2, key);
                    s.setString(3, value);
                    s.executeUpdate();
                } catch (SQLException e) {
                    throw new IllegalStateException("data store write failed", e);
                }
            }
        }

        @Override
        public boolean remove(@NotNull String key) {
            synchronized (lock) {
                try (PreparedStatement s = connection.prepareStatement("DELETE FROM kv WHERE ns=? AND k=?")) {
                    s.setString(1, namespace);
                    s.setString(2, key);
                    return s.executeUpdate() > 0;
                } catch (SQLException e) {
                    throw new IllegalStateException("data store write failed", e);
                }
            }
        }

        @Override
        public @NotNull List<String> keys(@NotNull String prefix) {
            synchronized (lock) {
                // substr comparison instead of LIKE, so '%' and '_' in a prefix need no escaping
                try (PreparedStatement s = connection.prepareStatement(
                        "SELECT k FROM kv WHERE ns=? AND substr(k, 1, ?)=? ORDER BY k")) {
                    s.setString(1, namespace);
                    s.setInt(2, prefix.length());
                    s.setString(3, prefix);
                    List<String> keys = new ArrayList<>();
                    try (ResultSet rs = s.executeQuery()) {
                        while (rs.next()) {
                            keys.add(rs.getString(1));
                        }
                    }
                    return keys;
                } catch (SQLException e) {
                    throw new IllegalStateException("data store read failed", e);
                }
            }
        }
    }
}
