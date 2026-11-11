package com.rentals.dao;

import com.rentals.properties.Property;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for Property operations.
 * Uses JDBC with SQLite (file-based database).
 */
public class PropertyDAO {

    /** Creates a new property. */
    public boolean create(Property property) {
        String sql = "INSERT INTO properties (name, address, description, status) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, property.getName());
            pstmt.setString(2, property.getAddress());
            pstmt.setString(3, property.getDescription());
            pstmt.setString(4, property.getStatus());
            pstmt.executeUpdate();
            property.setPropertyId(DatabaseConnection.lastInsertId(conn));
            return true;
        } catch (SQLException e) {
            System.err.println("Create property error: " + e.getMessage());
            return false;
        }
    }

    /** Gets all properties. */
    public List<Property> getAll() {
        List<Property> properties = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM properties ORDER BY name")) {
            while (rs.next()) properties.add(mapResultSetToProperty(rs));
        } catch (SQLException e) {
            System.err.println("Get all properties error: " + e.getMessage());
        }
        return properties;
    }

    /** Finds a property by ID. */
    public Property findById(Long propertyId) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM properties WHERE property_id = ?")) {
            pstmt.setLong(1, propertyId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return mapResultSetToProperty(rs);
        } catch (SQLException e) {
            System.err.println("Find property error: " + e.getMessage());
        }
        return null;
    }

    /** Updates a property. */
    public boolean update(Property property) {
        String sql = "UPDATE properties SET name = ?, address = ?, description = ?, status = ? WHERE property_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, property.getName());
            pstmt.setString(2, property.getAddress());
            pstmt.setString(3, property.getDescription());
            pstmt.setString(4, property.getStatus());
            pstmt.setLong(5, property.getPropertyId());
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Update property error: " + e.getMessage());
            return false;
        }
    }

    /** Deletes a property. */
    public boolean delete(Long propertyId) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement("DELETE FROM properties WHERE property_id = ?")) {
            pstmt.setLong(1, propertyId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Delete property error: " + e.getMessage());
            return false;
        }
    }

    /** Converts a database row to a Property object. */
    private Property mapResultSetToProperty(ResultSet rs) throws SQLException {
        Property p = new Property();
        p.setPropertyId(rs.getLong("property_id"));
        p.setName(rs.getString("name"));
        p.setAddress(rs.getString("address"));
        p.setDescription(rs.getString("description"));
        p.setStatus(rs.getString("status"));
        return p;
    }
}
