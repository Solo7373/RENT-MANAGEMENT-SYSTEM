package com.rentals.dao;

import com.rentals.payments.Payment;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for Payment operations.
 */
public class PaymentDAO {

    /** Records a new payment. */
    public boolean create(Payment payment) {
        String sql = "INSERT INTO payments (tenant_id, amount, payment_method, reference_number, status, notes, payment_date) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, payment.getTenantId());
            pstmt.setDouble(2, payment.getAmount());
            pstmt.setString(3, payment.getPaymentMethod());
            pstmt.setString(4, payment.getReferenceNumber());
            pstmt.setString(5, payment.getStatus());
            pstmt.setString(6, payment.getNotes());
            pstmt.setString(7, payment.getPaymentDate() != null ? payment.getPaymentDate()
                : java.time.LocalDate.now().toString());
            pstmt.executeUpdate();
            payment.setPaymentId(DatabaseConnection.lastInsertId(conn));
            return true;
        } catch (SQLException e) {
            System.err.println("Create payment error: " + e.getMessage());
            return false;
        }
    }

    /** Gets all payments with tenant names. */
    public List<Payment> getAll() {
        List<Payment> payments = new ArrayList<>();
        String sql = "SELECT p.*, t.full_name AS tenant_name FROM payments p " +
                     "LEFT JOIN tenants t ON p.tenant_id = t.tenant_id " +
                     "ORDER BY p.payment_date DESC, p.payment_id DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) payments.add(map(rs));
        } catch (SQLException e) {
            System.err.println("Get payments error: " + e.getMessage());
        }
        return payments;
    }

    /** Gets payments for one tenant. */
    public List<Payment> getByTenant(Long tenantId) {
        List<Payment> payments = new ArrayList<>();
        String sql = "SELECT p.*, t.full_name AS tenant_name FROM payments p " +
                     "LEFT JOIN tenants t ON p.tenant_id = t.tenant_id " +
                     "WHERE p.tenant_id = ? ORDER BY p.payment_date DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, tenantId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) payments.add(map(rs));
        } catch (SQLException e) {
            System.err.println("Get tenant payments error: " + e.getMessage());
        }
        return payments;
    }

    /** Updates the status of a payment (e.g. PENDING -> VERIFIED). */
    public boolean updateStatus(Long paymentId, String status) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement("UPDATE payments SET status = ? WHERE payment_id = ?")) {
            pstmt.setString(1, status);
            pstmt.setLong(2, paymentId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Update payment status error: " + e.getMessage());
            return false;
        }
    }

    /** Deletes a payment. */
    public boolean delete(Long paymentId) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement("DELETE FROM payments WHERE payment_id = ?")) {
            pstmt.setLong(1, paymentId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Delete payment error: " + e.getMessage());
            return false;
        }
    }

    /** Total money collected (all VERIFIED payments). */
    public double totalCollected() {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COALESCE(SUM(amount), 0) FROM payments WHERE status = 'VERIFIED'")) {
            if (rs.next()) return rs.getDouble(1);
        } catch (SQLException e) {
            System.err.println("Total collected error: " + e.getMessage());
        }
        return 0;
    }

    /** Total money collected in the current month. */
    public double totalThisMonth() {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COALESCE(SUM(amount), 0) FROM payments " +
                 "WHERE status = 'VERIFIED' AND strftime('%Y-%m', payment_date) = strftime('%Y-%m', 'now')")) {
            if (rs.next()) return rs.getDouble(1);
        } catch (SQLException e) {
            System.err.println("Monthly total error: " + e.getMessage());
        }
        return 0;
    }

    /** Converts a row to a Payment object. */
    private Payment map(ResultSet rs) throws SQLException {
        Payment p = new Payment();
        p.setPaymentId(rs.getLong("payment_id"));
        p.setTenantId(rs.getLong("tenant_id"));
        try { p.setTenantName(rs.getString("tenant_name")); } catch (SQLException e) {}
        p.setAmount(rs.getDouble("amount"));
        p.setPaymentMethod(rs.getString("payment_method"));
        p.setReferenceNumber(rs.getString("reference_number"));
        p.setStatus(rs.getString("status"));
        p.setNotes(rs.getString("notes"));
        p.setPaymentDate(rs.getString("payment_date"));
        p.setCreatedAt(rs.getString("created_at"));
        return p;
    }
}
