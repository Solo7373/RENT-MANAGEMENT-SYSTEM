package com.rentals.dao;

import com.rentals.payments.PaymentMethod;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for payment methods (seeded in DatabaseInitializer).
 */
public class PaymentMethodDAO {

    public List<PaymentMethod> getAll() {
        List<PaymentMethod> methods = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM payment_methods ORDER BY method_id")) {
            while (rs.next()) {
                PaymentMethod m = new PaymentMethod();
                m.setMethodId(rs.getLong("method_id"));
                m.setName(rs.getString("name"));
                m.setType(rs.getString("type"));
                m.setAccountNumber(rs.getString("account_number"));
                m.setInstructions(rs.getString("instructions"));
                m.setEnabled(rs.getInt("enabled") == 1);
                methods.add(m);
            }
        } catch (SQLException e) {
            System.err.println("Get payment methods error: " + e.getMessage());
        }
        return methods;
    }

    /** Turns a payment method on or off. */
    public boolean setEnabled(Long methodId, boolean enabled) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                 "UPDATE payment_methods SET enabled = ? WHERE method_id = ?")) {
            pstmt.setInt(1, enabled ? 1 : 0);
            pstmt.setLong(2, methodId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Toggle payment method error: " + e.getMessage());
            return false;
        }
    }
}
