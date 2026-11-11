package com.rentals.dao;

import com.rentals.audit.AuditLog;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for the audit trail.
 */
public class AuditDAO {

    /** Records an action performed by a user. */
    public void log(Long userId, String action, String description) {
        String sql = "INSERT INTO audit_logs (user_id, action, description) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (userId != null) pstmt.setLong(1, userId);
            else pstmt.setNull(1, Types.INTEGER);
            pstmt.setString(2, action);
            pstmt.setString(3, description);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Audit log error: " + e.getMessage());
        }
    }

    /** Latest audit entries (most recent first). */
    public List<AuditLog> getRecent(int limit) {
        List<AuditLog> logs = new ArrayList<>();
        String sql = "SELECT a.*, u.username FROM audit_logs a " +
                     "LEFT JOIN users u ON a.user_id = u.user_id " +
                     "ORDER BY a.log_id DESC LIMIT ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, limit);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                AuditLog log = new AuditLog();
                log.setLogId(rs.getLong("log_id"));
                log.setUserId(rs.getLong("user_id"));
                try { log.setUsername(rs.getString("username")); } catch (SQLException e) {}
                log.setAction(rs.getString("action"));
                log.setDescription(rs.getString("description"));
                log.setCreatedAt(rs.getString("created_at"));
                logs.add(log);
            }
        } catch (SQLException e) {
            System.err.println("Get audit logs error: " + e.getMessage());
        }
        return logs;
    }
}
