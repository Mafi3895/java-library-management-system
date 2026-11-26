package com.library.database;

import com.library.exception.DatabaseException;
import org.sqlite.SQLiteConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Manages SQLite JDBC connections and database schema initialization.
 * Ensures foreign key constraints are enforced on every connection.
 */
public class DatabaseManager {

    public static final String DEFAULT_DATABASE_URL = "jdbc:sqlite:library.db";
    private static final String SCHEMA_RESOURCE_PATH = "/database/schema.sql";
    private static final int BUSY_TIMEOUT_MS = 5000;

    private final String jdbcUrl;
    private final Properties connectionProperties;

    /**
     * Creates a DatabaseManager using the default persistent SQLite file.
     */
    public DatabaseManager() {
        this(DEFAULT_DATABASE_URL);
    }

    /**
     * Creates a DatabaseManager for a specific SQLite JDBC URL (e.g., a temporary test file).
     */
    public DatabaseManager(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("JDBC URL must not be null or empty.");
        }
        this.jdbcUrl = jdbcUrl.trim();
        this.connectionProperties = buildSqliteProperties();
    }

    private Properties buildSqliteProperties() {
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setBusyTimeout(BUSY_TIMEOUT_MS);
        return config.toProperties();
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    /**
     * Opens and returns a new SQLite JDBC connection with foreign keys enabled.
     */
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, connectionProperties);
    }

    /**
     * Initializes the database schema from {@code /database/schema.sql} if tables do not yet exist.
     */
    public void initializeDatabase() {
        List<String> statements = loadSchemaStatements();

        try (Connection connection = getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                for (String sql : statements) {
                    statement.execute(sql);
                }
                connection.commit();
            } catch (SQLException ex) {
                rollbackQuietly(connection, ex);
                throw new DatabaseException("Failed to initialize database schema.", ex);
            } finally {
                restoreAutoCommit(connection, originalAutoCommit);
            }
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to connect to database for schema initialization.", ex);
        }
    }

    private List<String> loadSchemaStatements() {
        String rawSql = readSchemaResource();
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String line : rawSql.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }
            current.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                String sql = current.toString().trim();
                // Strip trailing semicolon before executing via JDBC Statement
                sql = sql.substring(0, sql.length() - 1).trim();
                if (!sql.isEmpty()) {
                    statements.add(sql);
                }
                current.setLength(0);
            }
        }

        String remaining = current.toString().trim();
        if (!remaining.isEmpty()) {
            statements.add(remaining);
        }
        return statements;
    }

    private String readSchemaResource() {
        InputStream stream = DatabaseManager.class.getResourceAsStream(SCHEMA_RESOURCE_PATH);
        if (stream == null) {
            throw new DatabaseException("Schema resource not found at " + SCHEMA_RESOURCE_PATH);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            return builder.toString();
        } catch (IOException ex) {
            throw new DatabaseException("Failed to read schema resource at " + SCHEMA_RESOURCE_PATH, ex);
        }
    }

    private void rollbackQuietly(Connection connection, SQLException primaryException) {
        try {
            connection.rollback();
        } catch (SQLException rollbackEx) {
            primaryException.addSuppressed(rollbackEx);
        }
    }

    private void restoreAutoCommit(Connection connection, boolean originalAutoCommit) {
        try {
            connection.setAutoCommit(originalAutoCommit);
        } catch (SQLException ex) {
            throw new DatabaseException("Failed to restore auto-commit mode.", ex);
        }
    }
}
