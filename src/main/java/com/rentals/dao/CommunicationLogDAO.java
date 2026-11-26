package com.rentals.dao;

import com.rentals.communications.CommunicationLog;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for the per-tenant communication timeline.
 */
public class CommunicationLogDAO {

    /** Inserts a new communication entry and returns its ID (or null on failure). */
    public Long create(CommunicationLog log) {
        String sql = "INSERT INTO communication_logs (tenant_id, tenancy_id, charge_id, payment_id, " +
                     "notification_id, event_type, channel, direction, recipient, message, status, " +
                     "provider, provider_message_id, provider_status, error_message) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            setNullableLong(pstmt, 1, log.getTenantId());
            setNullableLong(pstmt, 2, log.getTenancyId());
            setNullableLong(pstmt, 3, log.getChargeId());
            setNullableLong(pstmt, 4, log.getPaymentId());
            setNullableLong(pstmt, 5, log.getNotificationId());
            pstmt.setString(6, log.getEventType());
            pstmt.setString(7, log.getChannel());
            pstmt.setString(8, log.getDirection());
            pstmt.setString(9, log.getRecipient());
            pstmt.setString(10, log.getMessage());
            pstmt.setString(11, log.getStatus());
            pstmt.setString(12, log.getProvider());
            pstmt.setString(13, log.getProviderMessageId());
            pstmt.setString(14, log.getProviderStatus());
            pstmt.setString(15, log.getErrorMessage());
            pstmt.executeUpdate();
            Long id = DatabaseConnection.lastInsertId(conn);
            log.setCommunicationId(id);
            return id;
        } catch (SQLException e) {
            System.err.println("Create communication log error: " + e.getMessage());
            return null;
        }
    }

    /** Updates delivery details after a provider response or callback. */
    public boolean updateStatus(Long communicationId, String status, String providerMessageId,
                                String providerStatus, String errorMessage) {
        String sql = "UPDATE communication_logs SET status = ?, " +
                     "provider_message_id = COALESCE(?, provider_message_id), " +
                     "provider_status = COALESCE(?, provider_status), " +
                     "error_message = ?, updated_at = datetime('now') WHERE communication_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status);
            pstmt.setString(2, emptyToNull(providerMessageId));
            pstmt.setString(3, emptyToNull(providerStatus));
            pstmt.setString(4, emptyToNull(errorMessage));
            pstmt.setLong(5, communicationId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Update communication log error: " + e.getMessage());
            return false;
        }
    }

    /** Most recent communications for one tenant (newest first). */
    public List<CommunicationLog> getByTenant(Long tenantId, int limit) {
        List<CommunicationLog> logs = new ArrayList<>();
        String sql = "SELECT c.*, t.full_name AS tenant_name FROM communication_logs c " +
                     "LEFT JOIN tenants t ON c.tenant_id = t.tenant_id " +
                     "WHERE c.tenant_id = ? ORDER BY c.communication_id DESC LIMIT ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, tenantId);
            pstmt.setInt(2, Math.max(1, limit));
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) logs.add(map(rs));
        } catch (SQLException e) {
            System.err.println("Get tenant communications error: " + e.getMessage());
        }
        return logs;
    }

    /** Every recent communication, used by tests and future admin views. */
    public List<CommunicationLog> getRecent(int limit) {
        List<CommunicationLog> logs = new ArrayList<>();
        String sql = "SELECT c.*, t.full_name AS tenant_name FROM communication_logs c " +
                     "LEFT JOIN tenants t ON c.tenant_id = t.tenant_id " +
                     "ORDER BY c.communication_id DESC LIMIT ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Math.max(1, limit));
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) logs.add(map(rs));
        } catch (SQLException e) {
            System.err.println("Get recent communications error: " + e.getMessage());
        }
        return logs;
    }

    /* ---------- helpers ---------- */

    private CommunicationLog map(ResultSet rs) throws SQLException {
        CommunicationLog log = new CommunicationLog();
        log.setCommunicationId(rs.getLong("communication_id"));
        log.setTenantId(nullableLong(rs, "tenant_id"));
        log.setTenancyId(nullableLong(rs, "tenancy_id"));
        log.setChargeId(nullableLong(rs, "charge_id"));
        log.setPaymentId(nullableLong(rs, "payment_id"));
        log.setNotificationId(nullableLong(rs, "notification_id"));
        try { log.setTenantName(rs.getString("tenant_name")); } catch (SQLException ignored) {}
        log.setEventType(rs.getString("event_type"));
        log.setChannel(rs.getString("channel"));
        log.setDirection(rs.getString("direction"));
        log.setRecipient(rs.getString("recipient"));
        log.setMessage(rs.getString("message"));
        log.setStatus(rs.getString("status"));
        log.setProvider(rs.getString("provider"));
        log.setProviderMessageId(rs.getString("provider_message_id"));
        log.setProviderStatus(rs.getString("provider_status"));
        log.setErrorMessage(rs.getString("error_message"));
        log.setCreatedAt(rs.getString("created_at"));
        log.setUpdatedAt(rs.getString("updated_at"));
        return log;
    }

    private void setNullableLong(PreparedStatement pstmt, int index, Long value)
            throws SQLException {
        if (value == null) pstmt.setNull(index, Types.INTEGER);
        else pstmt.setLong(index, value);
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
