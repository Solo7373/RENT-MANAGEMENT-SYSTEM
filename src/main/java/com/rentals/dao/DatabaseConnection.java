package com.rentals.dao;

import com.rentals.config.AppPaths;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * ============================================================
 * DATABASE CONNECTION (SQLite for Desktop)
 * ============================================================
 *
 * SQLite is a file-based database - no server needed!
 * The database is stored in the user's data folder:
 *   %APPDATA%\RentalManagementSystem\rentaldb.sqlite
 *
 * Schema creation lives in com.rentals.config.DatabaseInitializer
 * so there is a single source of truth.
 */
public class DatabaseConnection {

    // Absolute path in the user data folder (works from a packaged installer)
    private static final String DB_URL = "jdbc:sqlite:" + AppPaths.databaseFile();

    // Private constructor - utility class only
    private DatabaseConnection() {}

    /**
     * Creates and returns a NEW database connection.
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    /**
     * SQLite does not implement JDBC's RETURN_GENERATED_KEYS overload, so
     * every DAO reads the last inserted row id from the same connection instead.
     */
    public static long lastInsertId(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT last_insert_rowid()")) {
            return rs.next() ? rs.getLong(1) : -1;
        }
    }
}
