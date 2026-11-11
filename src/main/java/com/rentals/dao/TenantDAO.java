package com.rentals.dao;

import com.rentals.tenants.Tenant;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class TenantDAO {

    /** Creates a new tenant. */
    public boolean create(Tenant tenant) {
        String sql = "INSERT INTO tenants (full_name, phone, whatsapp_number, email, id_number, school_id, refugee_id, emergency_contact, emergency_phone, room_number, rent_amount, notes, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, tenant.getFullName());
            pstmt.setString(2, tenant.getPhone());
            pstmt.setString(3, tenant.getWhatsappNumber());
            pstmt.setString(4, tenant.getEmail());
            pstmt.setString(5, tenant.getIdNumber());
            pstmt.setString(6, tenant.getSchoolId());
            pstmt.setString(7, tenant.getRefugeeId());
            pstmt.setString(8, tenant.getEmergencyContact());
            pstmt.setString(9, tenant.getEmergencyPhone());
            pstmt.setString(10, tenant.getRoomNumber());
            pstmt.setDouble(11, tenant.getRentAmount() != null ? tenant.getRentAmount() : 0.0);
            pstmt.setString(12, tenant.getNotes());
            pstmt.setString(13, tenant.getStatus());
            pstmt.executeUpdate();
            tenant.setTenantId(DatabaseConnection.lastInsertId(conn));
            return true;
        } catch (SQLException e) {
            System.err.println("Create tenant error: " + e.getMessage());
            return false;
        }
    }

    /** Gets all tenants. */
    public List<Tenant> getAll() {
        List<Tenant> tenants = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM tenants ORDER BY full_name")) {
            while (rs.next()) tenants.add(mapResultSetToTenant(rs));
        } catch (SQLException e) {
            System.err.println("Get all tenants error: " + e.getMessage());
        }
        return tenants;
    }

    /** Finds a tenant by ID. */
    public Tenant findById(Long tenantId) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM tenants WHERE tenant_id = ?")) {
            pstmt.setLong(1, tenantId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return mapResultSetToTenant(rs);
        } catch (SQLException e) {
            System.err.println("Find tenant error: " + e.getMessage());
        }
        return null;
    }

    /** Updates a tenant. */
    public boolean update(Tenant tenant) {
        String sql = "UPDATE tenants SET full_name = ?, phone = ?, whatsapp_number = ?, email = ?, id_number = ?, school_id = ?, refugee_id = ?, emergency_contact = ?, emergency_phone = ?, room_number = ?, rent_amount = ?, notes = ?, status = ? WHERE tenant_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, tenant.getFullName());
            pstmt.setString(2, tenant.getPhone());
            pstmt.setString(3, tenant.getWhatsappNumber());
            pstmt.setString(4, tenant.getEmail());
            pstmt.setString(5, tenant.getIdNumber());
            pstmt.setString(6, tenant.getSchoolId());
            pstmt.setString(7, tenant.getRefugeeId());
            pstmt.setString(8, tenant.getEmergencyContact());
            pstmt.setString(9, tenant.getEmergencyPhone());
            pstmt.setString(10, tenant.getRoomNumber());
            pstmt.setDouble(11, tenant.getRentAmount() != null ? tenant.getRentAmount() : 0.0);
            pstmt.setString(12, tenant.getNotes());
            pstmt.setString(13, tenant.getStatus());
            pstmt.setLong(14, tenant.getTenantId());
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Update tenant error: " + e.getMessage());
            return false;
        }
    }

    /** Deletes a tenant. */
    public boolean delete(Long tenantId) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement("DELETE FROM tenants WHERE tenant_id = ?")) {
            pstmt.setLong(1, tenantId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Delete tenant error: " + e.getMessage());
            return false;
        }
    }

    /** Searches tenants by name, phone, or ID number. */
    public List<Tenant> search(String keyword) {
        List<Tenant> tenants = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM tenants WHERE full_name LIKE ? OR phone LIKE ? OR id_number LIKE ? OR school_id LIKE ? OR refugee_id LIKE ?")) {
            String pattern = "%" + keyword + "%";
            pstmt.setString(1, pattern);
            pstmt.setString(2, pattern);
            pstmt.setString(3, pattern);
            pstmt.setString(4, pattern);
            pstmt.setString(5, pattern);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) tenants.add(mapResultSetToTenant(rs));
        } catch (SQLException e) {
            System.err.println("Search tenants error: " + e.getMessage());
        }
        return tenants;
    }

    /** Converts a database row to a Tenant object. */
    private Tenant mapResultSetToTenant(ResultSet rs) throws SQLException {
        Tenant t = new Tenant();
        t.setTenantId(rs.getLong("tenant_id"));
        t.setFullName(rs.getString("full_name"));
        t.setPhone(rs.getString("phone"));
        try { t.setWhatsappNumber(rs.getString("whatsapp_number")); } catch (SQLException e) {}
        t.setEmail(rs.getString("email"));
        try { t.setIdNumber(rs.getString("id_number")); } catch (SQLException e) {}
        try { t.setSchoolId(rs.getString("school_id")); } catch (SQLException e) {}
        try { t.setRefugeeId(rs.getString("refugee_id")); } catch (SQLException e) {}
        t.setEmergencyContact(rs.getString("emergency_contact"));
        t.setEmergencyPhone(rs.getString("emergency_phone"));
        try { t.setRoomNumber(rs.getString("room_number")); } catch (SQLException e) {}
        try { t.setRentAmount(rs.getDouble("rent_amount")); } catch (SQLException e) {}
        t.setNotes(rs.getString("notes"));
        t.setStatus(rs.getString("status"));
        return t;
    }
}
