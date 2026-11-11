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
        String sql = "INSERT INTO notification_logs (tenant_id, message, channel, status) VALUES (?, ?, ?, 'PENDING')";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, tenantId);
            pstmt.setString(2, message);
            pstmt.setString(3, channel);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Queue notification error: " + e.getMessage());
            return false;
        }
    }

    /** Marks a notification as sent. */
    public boolean markSent(Long notificationId) {
        return markSent(notificationId, null);
    }

    /** Marks a notification as sent and records the destination used. */
    public boolean markSent(Long notificationId, String recipient) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                 "UPDATE notification_logs SET status = 'SENT', sent_at = datetime('now'), " +
                 "recipient = COALESCE(?, recipient), error_message = NULL WHERE notification_id = ?")) {
            pstmt.setString(1, recipient);
            pstmt.setLong(2, notificationId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Mark notification sent error: " + e.getMessage());
            return false;
        }
    }

    /** Marks a notification as failed and stores the provider error. */
    public boolean markFailed(Long notificationId, String recipient, String error) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                 "UPDATE notification_logs SET status = 'FAILED', recipient = ?, " +
                 "error_message = ?, sent_at = datetime('now') WHERE notification_id = ?")) {
            pstmt.setString(1, recipient);
            pstmt.setString(2, error);
            pstmt.setLong(3, notificationId);
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
        n.setTenantId(rs.getLong("tenant_id"));
        try { n.setTenantName(rs.getString("tenant_name")); } catch (SQLException e) {}
        n.setMessage(rs.getString("message"));
        n.setChannel(rs.getString("channel"));
        n.setStatus(rs.getString("status"));
        try { n.setRecipient(rs.getString("recipient")); } catch (SQLException e) {}
        try { n.setErrorMessage(rs.getString("error_message")); } catch (SQLException e) {}
        n.setSentAt(rs.getString("sent_at"));
        n.setCreatedAt(rs.getString("created_at"));
        return n;
    }
}
