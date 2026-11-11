package com.rentals.dao;

import com.rentals.rooms.Room;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for Room operations.
 */
public class RoomDAO {

    /** Creates a new room. */
    public boolean create(Room room) {
        String sql = "INSERT INTO rooms (property_id, room_number, room_type, rent_amount, status, condition_notes) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, room.getPropertyId());
            pstmt.setString(2, room.getRoomNumber());
            pstmt.setString(3, room.getRoomType());
            pstmt.setDouble(4, room.getRentAmount());
            pstmt.setString(5, room.getStatus());
            pstmt.setString(6, room.getConditionNotes());
            pstmt.executeUpdate();
            room.setRoomId(DatabaseConnection.lastInsertId(conn));
            return true;
        } catch (SQLException e) {
            System.err.println("Create room error: " + e.getMessage());
            return false;
        }
    }

    /** Gets all rooms. */
    public List<Room> getAll() {
        List<Room> rooms = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT r.*, p.name as property_name FROM rooms r LEFT JOIN properties p ON r.property_id = p.property_id ORDER BY r.room_number")) {
            while (rs.next()) rooms.add(mapResultSetToRoom(rs));
        } catch (SQLException e) {
            System.err.println("Get all rooms error: " + e.getMessage());
        }
        return rooms;
    }

    /** Gets rooms by property ID. */
    public List<Room> getByProperty(Long propertyId) {
        List<Room> rooms = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement("SELECT r.*, p.name as property_name FROM rooms r LEFT JOIN properties p ON r.property_id = p.property_id WHERE r.property_id = ?")) {
            pstmt.setLong(1, propertyId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) rooms.add(mapResultSetToRoom(rs));
        } catch (SQLException e) {
            System.err.println("Get rooms by property error: " + e.getMessage());
        }
        return rooms;
    }

    /** Gets vacant rooms. */
    public List<Room> getVacant() {
        List<Room> rooms = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT r.*, p.name as property_name FROM rooms r LEFT JOIN properties p ON r.property_id = p.property_id WHERE r.status = 'VACANT'")) {
            while (rs.next()) rooms.add(mapResultSetToRoom(rs));
        } catch (SQLException e) {
            System.err.println("Get vacant rooms error: " + e.getMessage());
        }
        return rooms;
    }

    /** Finds a room by ID. */
    public Room findById(Long roomId) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement("SELECT r.*, p.name as property_name FROM rooms r LEFT JOIN properties p ON r.property_id = p.property_id WHERE r.room_id = ?")) {
            pstmt.setLong(1, roomId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return mapResultSetToRoom(rs);
        } catch (SQLException e) {
            System.err.println("Find room error: " + e.getMessage());
        }
        return null;
    }

    /** Updates a room. */
    public boolean update(Room room) {
        String sql = "UPDATE rooms SET property_id = ?, room_number = ?, room_type = ?, rent_amount = ?, status = ?, condition_notes = ? WHERE room_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, room.getPropertyId());
            pstmt.setString(2, room.getRoomNumber());
            pstmt.setString(3, room.getRoomType());
            pstmt.setDouble(4, room.getRentAmount());
            pstmt.setString(5, room.getStatus());
            pstmt.setString(6, room.getConditionNotes());
            pstmt.setLong(7, room.getRoomId());
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Update room error: " + e.getMessage());
            return false;
        }
    }

    /** Deletes a room. */
    public boolean delete(Long roomId) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement("DELETE FROM rooms WHERE room_id = ?")) {
            pstmt.setLong(1, roomId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Delete room error: " + e.getMessage());
            return false;
        }
    }

    /** Converts a database row to a Room object. */
    private Room mapResultSetToRoom(ResultSet rs) throws SQLException {
        Room r = new Room();
        r.setRoomId(rs.getLong("room_id"));
        r.setPropertyId(rs.getLong("property_id"));
        r.setRoomNumber(rs.getString("room_number"));
        r.setRoomType(rs.getString("room_type"));
        r.setRentAmount(rs.getDouble("rent_amount"));
        r.setStatus(rs.getString("status"));
        r.setConditionNotes(rs.getString("condition_notes"));
        return r;
    }
}
