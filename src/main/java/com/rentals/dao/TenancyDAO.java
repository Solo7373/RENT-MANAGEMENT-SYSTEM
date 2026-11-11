package com.rentals.dao;

import com.rentals.tenancies.Tenancy;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for Tenancy operations.
 */
public class TenancyDAO {

    /** Creates a new tenancy. */
    public boolean create(Tenancy tenancy) {
        String sql = "INSERT INTO tenancies (tenant_id, room_id, start_date, monthly_rent, deposit, status) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, tenancy.getTenantId());
            pstmt.setLong(2, tenancy.getRoomId());
            pstmt.setString(3, tenancy.getStartDate());
            pstmt.setDouble(4, tenancy.getMonthlyRent());
            pstmt.setDouble(5, tenancy.getDeposit());
            pstmt.setString(6, tenancy.getStatus());
            pstmt.executeUpdate();
            tenancy.setTenancyId(DatabaseConnection.lastInsertId(conn));

            // A room with a new tenancy becomes OCCUPIED
            try (PreparedStatement upd = conn.prepareStatement(
                    "UPDATE rooms SET status = 'OCCUPIED' WHERE room_id = ?")) {
                upd.setLong(1, tenancy.getRoomId());
                upd.executeUpdate();
            }
            return true;
        } catch (SQLException e) {
            System.err.println("Create tenancy error: " + e.getMessage());
            return false;
        }
    }

    /** Gets all tenancies with tenant and room details. */
    public List<Tenancy> getAll() {
        List<Tenancy> tenancies = new ArrayList<>();
        String sql = "SELECT t.*, tn.full_name as tenant_name, r.room_number " +
                     "FROM tenancies t " +
                     "JOIN tenants tn ON t.tenant_id = tn.tenant_id " +
                     "JOIN rooms r ON t.room_id = r.room_id " +
                     "ORDER BY t.start_date DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) tenancies.add(mapResultSetToTenancy(rs));
        } catch (SQLException e) {
            System.err.println("Get all tenancies error: " + e.getMessage());
        }
        return tenancies;
    }

    /** Finds a tenancy by ID. */
    public Tenancy findById(Long tenancyId) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                 "SELECT t.*, tn.full_name as tenant_name, r.room_number " +
                 "FROM tenancies t JOIN tenants tn ON t.tenant_id = tn.tenant_id " +
                 "JOIN rooms r ON t.room_id = r.room_id WHERE t.tenancy_id = ?")) {
            pstmt.setLong(1, tenancyId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return mapResultSetToTenancy(rs);
        } catch (SQLException e) {
            System.err.println("Find tenancy error: " + e.getMessage());
        }
        return null;
    }

    /** Updates a tenancy. */
    public boolean update(Tenancy tenancy) {        String sql = "UPDATE tenancies SET tenant_id = ?, room_id = ?, start_date = ?, end_date = ?, monthly_rent = ?, deposit = ?, status = ? WHERE tenancy_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, tenancy.getTenantId());
            pstmt.setLong(2, tenancy.getRoomId());
            pstmt.setString(3, tenancy.getStartDate());
            pstmt.setString(4, tenancy.getEndDate());
            pstmt.setDouble(5, tenancy.getMonthlyRent());
            pstmt.setDouble(6, tenancy.getDeposit());
            pstmt.setString(7, tenancy.getStatus());
            pstmt.setLong(8, tenancy.getTenancyId());
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Update tenancy error: " + e.getMessage());
            return false;
        }
    }

    /** Terminates a tenancy and frees the room. */
    public boolean terminate(Long tenancyId, Long roomId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "UPDATE tenancies SET status = 'ENDED', end_date = date('now') WHERE tenancy_id = ?")) {
                pstmt.setLong(1, tenancyId);
                pstmt.executeUpdate();
            }
            if (roomId != null) {
                try (PreparedStatement pstmt = conn.prepareStatement(
                        "UPDATE rooms SET status = 'VACANT' WHERE room_id = ?")) {
                    pstmt.setLong(1, roomId);
                    pstmt.executeUpdate();
                }
            }
            return true;
        } catch (SQLException e) {
            System.err.println("Terminate tenancy error: " + e.getMessage());
            return false;
        }
    }

    /** Converts a database row to a Tenancy object. */
    private Tenancy mapResultSetToTenancy(ResultSet rs) throws SQLException {
        Tenancy t = new Tenancy();
        t.setTenancyId(rs.getLong("tenancy_id"));
        t.setTenantId(rs.getLong("tenant_id"));
        t.setRoomId(rs.getLong("room_id"));
        t.setTenantName(rs.getString("tenant_name"));
        t.setRoomNumber(rs.getString("room_number"));
        t.setStartDate(rs.getString("start_date"));
        t.setEndDate(rs.getString("end_date"));
        t.setMonthlyRent(rs.getDouble("monthly_rent"));
        t.setDeposit(rs.getDouble("deposit"));
        t.setStatus(rs.getString("status"));
        return t;
    }
}
