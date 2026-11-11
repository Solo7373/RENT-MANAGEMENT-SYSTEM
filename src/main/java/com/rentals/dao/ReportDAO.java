package com.rentals.dao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for reports - aggregate queries across the whole system.
 * All figures are calculated live from the database.
 */
public class ReportDAO {

    /** Total number of properties. */
    public int totalProperties() {
        return countQuery("SELECT COUNT(*) FROM properties");
    }

    /** Total number of rooms. */
    public int totalRooms() {
        return countQuery("SELECT COUNT(*) FROM rooms");
    }

    /** Number of occupied rooms. */
    public int occupiedRooms() {
        return countQuery("SELECT COUNT(*) FROM rooms WHERE status = 'OCCUPIED'");
    }

    /** Number of vacant rooms. */
    public int vacantRooms() {
        return countQuery("SELECT COUNT(*) FROM rooms WHERE status = 'VACANT'");
    }

    /** Total number of tenants. */
    public int totalTenants() {
        return countQuery("SELECT COUNT(*) FROM tenants");
    }

    /** Number of active tenancies. */
    public int activeTenancies() {
        return countQuery("SELECT COUNT(*) FROM tenancies WHERE status = 'ACTIVE'");
    }

    /** Total rent collected (verified payments). */
    public double totalRevenue() {
        return doubleQuery("SELECT COALESCE(SUM(amount), 0) FROM payments WHERE status = 'VERIFIED'");
    }

    /** Rent collected this calendar month. */
    public double revenueThisMonth() {
        return doubleQuery("SELECT COALESCE(SUM(amount), 0) FROM payments " +
            "WHERE status = 'VERIFIED' AND strftime('%Y-%m', payment_date) = strftime('%Y-%m', 'now')");
    }

    /** Total expected monthly rent from active tenancies. */
    public double expectedMonthlyRent() {
        return doubleQuery("SELECT COALESCE(SUM(monthly_rent), 0) FROM tenancies WHERE status = 'ACTIVE'");
    }

    /** Total outstanding rent charges (amount minus anything already allocated). */
    public double arrears() {
        return doubleQuery("SELECT COALESCE(SUM(amount - COALESCE(paid_amount, 0)), 0) " +
            "FROM rent_charges WHERE status IN ('PENDING','PARTIAL')");
    }

    /** Total expenses recorded. */
    public double totalExpenses() {
        return doubleQuery("SELECT COALESCE(SUM(amount), 0) FROM expenses");
    }

    /** Occupancy percentage (0-100). */
    public double occupancyRate() {
        int total = totalRooms();
        if (total == 0) return 0;
        return (occupiedRooms() * 100.0) / total;
    }

    /**
     * Revenue grouped by month for the last 6 months.
     * Returns rows of [monthLabel, amount].
     */
    public List<Object[]> monthlyRevenue() {
        List<Object[]> rows = new ArrayList<>();
        String sql = "SELECT strftime('%Y-%m', payment_date) AS month, COALESCE(SUM(amount), 0) AS total " +
                     "FROM payments WHERE status = 'VERIFIED' AND payment_date >= date('now', '-6 months') " +
                     "GROUP BY month ORDER BY month DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) rows.add(new Object[]{ rs.getString("month"), rs.getDouble("total") });
        } catch (SQLException e) {
            System.err.println("Monthly revenue error: " + e.getMessage());
        }
        return rows;
    }

    /**
     * Property summary: [propertyName, totalRooms, occupiedRooms, vacantRooms].
     */
    public List<Object[]> propertySummary() {
        List<Object[]> rows = new ArrayList<>();
        String sql = "SELECT p.name, " +
                     "COUNT(r.room_id) AS total, " +
                     "SUM(CASE WHEN r.status = 'OCCUPIED' THEN 1 ELSE 0 END) AS occupied, " +
                     "SUM(CASE WHEN r.status = 'VACANT' THEN 1 ELSE 0 END) AS vacant " +
                     "FROM properties p LEFT JOIN rooms r ON p.property_id = r.property_id " +
                     "GROUP BY p.property_id ORDER BY p.name";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                rows.add(new Object[]{
                    rs.getString("name"),
                    rs.getInt("total"),
                    rs.getInt("occupied"),
                    rs.getInt("vacant")
                });
            }
        } catch (SQLException e) {
            System.err.println("Property summary error: " + e.getMessage());
        }
        return rows;
    }

    /* ---------- helpers ---------- */

    private int countQuery(String sql) {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            System.err.println("Count query error: " + e.getMessage());
        }
        return 0;
    }

    private double doubleQuery(String sql) {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) return rs.getDouble(1);
        } catch (SQLException e) {
            System.err.println("Double query error: " + e.getMessage());
        }
        return 0;
    }
}
