package com.rentals.dao;

import com.rentals.notifications.NotificationLog;
import com.rentals.reminders.ReminderRule;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for reminder rules and notification logs.
 */
public class ReminderDAO {

    /* ==================== REMINDER RULES ==================== */

    public List<ReminderRule> getAllRules() {
        List<ReminderRule> rules = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM reminder_rules ORDER BY stage")) {
            while (rs.next()) rules.add(mapRule(rs));
        } catch (SQLException e) {
            System.err.println("Get reminder rules error: " + e.getMessage());
        }
        return rules;
    }

    public boolean createRule(ReminderRule rule) {
        String sql = "INSERT INTO reminder_rules (stage, days_after_due, channel, message_template, enabled) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, rule.getStage());
            pstmt.setInt(2, rule.getDaysAfterDue());
            pstmt.setString(3, rule.getChannel());
            pstmt.setString(4, rule.getMessageTemplate());
            pstmt.setInt(5, rule.isEnabled() ? 1 : 0);
            pstmt.executeUpdate();
            rule.setRuleId(DatabaseConnection.lastInsertId(conn));
            return true;
        } catch (SQLException e) {
            System.err.println("Create reminder rule error: " + e.getMessage());
            return false;
        }
    }

    public boolean updateRule(ReminderRule rule) {
        String sql = "UPDATE reminder_rules SET stage = ?, days_after_due = ?, channel = ?, message_template = ?, enabled = ? WHERE rule_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, rule.getStage());
            pstmt.setInt(2, rule.getDaysAfterDue());
            pstmt.setString(3, rule.getChannel());
            pstmt.setString(4, rule.getMessageTemplate());
            pstmt.setInt(5, rule.isEnabled() ? 1 : 0);
            pstmt.setLong(6, rule.getRuleId());
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Update reminder rule error: " + e.getMessage());
            return false;
        }
    }

    public boolean deleteRule(Long ruleId) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement("DELETE FROM reminder_rules WHERE rule_id = ?")) {
            pstmt.setLong(1, ruleId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Delete reminder rule error: " + e.getMessage());
            return false;
        }
    }

    /* ==================== NOTIFICATION LOGS ==================== */

    public List<NotificationLog> getAllLogs() {
        List<NotificationLog> logs = new ArrayList<>();
        String sql = "SELECT n.*, t.full_name AS tenant_name FROM notification_logs n " +
                     "LEFT JOIN tenants t ON n.tenant_id = t.tenant_id ORDER BY n.notification_id DESC LIMIT 200";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) logs.add(mapLog(rs));
        } catch (SQLException e) {
            System.err.println("Get notification logs error: " + e.getMessage());
        }
        return logs;
    }

    /** Queues a reminder message for a tenant (status PENDING). */
    public boolean queueNotification(Long tenantId, String message, String channel) {
        return queueTrackedNotification(tenantId, null, null, null,
            "REMINDER", channel, message, null) != null;
    }

    /**
     * Queues a reminder with its tenant/lease/charge context and an optional
     * dedupe key. Returns the notification ID, or null when the dedupe key
     * already exists (another message for the same rule and charge was sent).
     */
    public Long queueTrackedNotification(Long tenantId, Long tenancyId, Long chargeId,
                                         Long ruleId, String eventType, String channel,
                                         String message, String dedupeKey) {
        String sql = "INSERT INTO notification_logs (tenant_id, tenancy_id, charge_id, rule_id, " +
                     "event_type, message, channel, status, dedupe_key) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING', ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            setNullableLong(pstmt, 1, tenantId);
            setNullableLong(pstmt, 2, tenancyId);
            setNullableLong(pstmt, 3, chargeId);
            setNullableLong(pstmt, 4, ruleId);
            pstmt.setString(5, eventType == null || eventType.isBlank() ? "REMINDER" : eventType);
            pstmt.setString(6, message);
            pstmt.setString(7, channel);
            pstmt.setString(8, emptyToNull(dedupeKey));
            pstmt.executeUpdate();
            return DatabaseConnection.lastInsertId(conn);
        } catch (SQLException e) {
            if (dedupeKey != null && e.getMessage() != null
                && e.getMessage().toUpperCase().contains("UNIQUE")) {
                return null;   // already queued or sent for this rule + charge
            }
            System.err.println("Queue notification error: " + e.getMessage());
            return null;
        }
    }

    /** True when this rule + charge combination already has a queued/sent message. */
    public boolean dedupeExists(String dedupeKey) {
        if (dedupeKey == null || dedupeKey.isBlank()) return false;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                 "SELECT 1 FROM notification_logs WHERE dedupe_key = ? LIMIT 1")) {
            pstmt.setString(1, dedupeKey);
            return pstmt.executeQuery().next();
        } catch (SQLException e) {
            System.err.println("Check notification dedupe error: " + e.getMessage());
            return false;
        }
    }

    /** Marks a notification as sent. */
    public boolean markSent(Long notificationId) {
        return markSent(notificationId, null);
    }

    /** Marks a notification as sent and records the destination used. */
    public boolean markSent(Long notificationId, String recipient) {
        return markSent(notificationId, recipient, null, null, null);
    }

    /** Marks a notification as sent and stores provider delivery metadata. */
    public boolean markSent(Long notificationId, String recipient, String provider,
                            String providerMessageId, String providerStatus) {
        String sql = "UPDATE notification_logs SET status = 'SENT', sent_at = datetime('now'), " +
                     "recipient = COALESCE(?, recipient), error_message = NULL, " +
                     "provider = COALESCE(?, provider), " +
                     "provider_message_id = COALESCE(?, provider_message_id), " +
                     "provider_status = COALESCE(?, provider_status) WHERE notification_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, recipient);
            pstmt.setString(2, emptyToNull(provider));
            pstmt.setString(3, emptyToNull(providerMessageId));
            pstmt.setString(4, emptyToNull(providerStatus));
            pstmt.setLong(5, notificationId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Mark notification sent error: " + e.getMessage());
            return false;
        }
    }

    /** Marks a notification as failed and stores the provider error. */
    public boolean markFailed(Long notificationId, String recipient, String error) {
        return markFailed(notificationId, recipient, error, null, null, null);
    }

    /**
     * Marks a notification as failed and stores provider details. The dedupe
     * key is cleared so the scheduler can try the same rule + charge again on
     * a later run instead of treating the failed attempt as delivered.
     */
    public boolean markFailed(Long notificationId, String recipient, String error,
                              String provider, String providerMessageId,
                              String providerStatus) {
        String sql = "UPDATE notification_logs SET status = 'FAILED', recipient = ?, " +
                     "error_message = ?, sent_at = datetime('now'), dedupe_key = NULL, " +
                     "provider = COALESCE(?, provider), " +
                     "provider_message_id = COALESCE(?, provider_message_id), " +
                     "provider_status = COALESCE(?, provider_status) WHERE notification_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, recipient);
            pstmt.setString(2, error);
            pstmt.setString(3, emptyToNull(provider));
            pstmt.setString(4, emptyToNull(providerMessageId));
            pstmt.setString(5, emptyToNull(providerStatus));
            pstmt.setLong(6, notificationId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Mark notification failed error: " + e.getMessage());
            return false;
        }
    }

    /* ==================== MAPPERS ==================== */

    private ReminderRule mapRule(ResultSet rs) throws SQLException {
        ReminderRule r = new ReminderRule();
        r.setRuleId(rs.getLong("rule_id"));
        r.setStage(rs.getInt("stage"));
        r.setDaysAfterDue(rs.getInt("days_after_due"));
        r.setChannel(rs.getString("channel"));
        r.setMessageTemplate(rs.getString("message_template"));
        r.setEnabled(rs.getInt("enabled") == 1);
        return r;
    }

    private NotificationLog mapLog(ResultSet rs) throws SQLException {
        NotificationLog n = new NotificationLog();
        n.setNotificationId(rs.getLong("notification_id"));
        n.setTenantId(nullableLong(rs, "tenant_id"));
        n.setTenancyId(nullableLong(rs, "tenancy_id"));
        n.setChargeId(nullableLong(rs, "charge_id"));
        n.setPaymentId(nullableLong(rs, "payment_id"));
        n.setRuleId(nullableLong(rs, "rule_id"));
        try { n.setTenantName(rs.getString("tenant_name")); } catch (SQLException e) {}
        n.setMessage(rs.getString("message"));
        n.setChannel(rs.getString("channel"));
        n.setStatus(rs.getString("status"));
        try { n.setRecipient(rs.getString("recipient")); } catch (SQLException e) {}
        try { n.setErrorMessage(rs.getString("error_message")); } catch (SQLException e) {}
        try { n.setEventType(rs.getString("event_type")); } catch (SQLException e) {}
        try { n.setProvider(rs.getString("provider")); } catch (SQLException e) {}
        try { n.setProviderMessageId(rs.getString("provider_message_id")); } catch (SQLException e) {}
        try { n.setProviderStatus(rs.getString("provider_status")); } catch (SQLException e) {}
        try { n.setDedupeKey(rs.getString("dedupe_key")); } catch (SQLException e) {}
        n.setSentAt(rs.getString("sent_at"));
        n.setCreatedAt(rs.getString("created_at"));
        return n;
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
