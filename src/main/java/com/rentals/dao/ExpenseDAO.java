package com.rentals.dao;

import com.rentals.expenses.Expense;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for expenses.
 */
public class ExpenseDAO {

    /** Records an expense. */
    public boolean create(Expense expense) {
        String sql = "INSERT INTO expenses (property_id, description, amount, expense_date, category) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (expense.getPropertyId() != null) pstmt.setLong(1, expense.getPropertyId());
            else pstmt.setNull(1, Types.INTEGER);
            pstmt.setString(2, expense.getDescription());
            pstmt.setDouble(3, expense.getAmount());
            pstmt.setString(4, expense.getExpenseDate() != null ? expense.getExpenseDate()
                : java.time.LocalDate.now().toString());
            pstmt.setString(5, expense.getCategory());
            pstmt.executeUpdate();
            expense.setExpenseId(DatabaseConnection.lastInsertId(conn));
            return true;
        } catch (SQLException e) {
            System.err.println("Create expense error: " + e.getMessage());
            return false;
        }
    }

    /** All expenses with property names. */
    public List<Expense> getAll() {
        List<Expense> expenses = new ArrayList<>();
        String sql = "SELECT e.*, p.name AS property_name FROM expenses e " +
                     "LEFT JOIN properties p ON e.property_id = p.property_id " +
                     "ORDER BY e.expense_date DESC, e.expense_id DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) expenses.add(map(rs));
        } catch (SQLException e) {
            System.err.println("Get expenses error: " + e.getMessage());
        }
        return expenses;
    }

    /** Deletes an expense. */
    public boolean delete(Long expenseId) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement("DELETE FROM expenses WHERE expense_id = ?")) {
            pstmt.setLong(1, expenseId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Delete expense error: " + e.getMessage());
            return false;
        }
    }

    /** Total money spent. */
    public double total() {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COALESCE(SUM(amount), 0) FROM expenses")) {
            if (rs.next()) return rs.getDouble(1);
        } catch (SQLException e) {
            System.err.println("Expense total error: " + e.getMessage());
        }
        return 0;
    }

    private Expense map(ResultSet rs) throws SQLException {
        Expense e = new Expense();
        e.setExpenseId(rs.getLong("expense_id"));
        e.setPropertyId(rs.getLong("property_id"));
        try { e.setPropertyName(rs.getString("property_name")); } catch (SQLException ex) {}
        e.setDescription(rs.getString("description"));
        e.setAmount(rs.getDouble("amount"));
        e.setExpenseDate(rs.getString("expense_date"));
        e.setCategory(rs.getString("category"));
        return e;
    }
}
