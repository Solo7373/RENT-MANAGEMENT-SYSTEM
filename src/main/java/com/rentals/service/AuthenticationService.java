package com.rentals.service;

import com.rentals.dao.DatabaseConnection;
import com.rentals.users.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.sql.*;

/**
 * ============================================================
 * AUTHENTICATION SERVICE
 * ============================================================
 * 
 * Handles admin login and password security.
 * Passwords are hashed using BCrypt - never stored as plain text.
 */
public class AuthenticationService {

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * Authenticates an admin user.
     * Returns the User object if login succeeds, null otherwise.
     */
    public User authenticateAdmin(String username, String password) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                 "SELECT * FROM users WHERE username = ? AND role = 'ADMIN'")) {

            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String storedHash = rs.getString("password_hash");
                if (passwordEncoder.matches(password, storedHash)) {
                    User user = new User();
                    user.setUserId(rs.getLong("user_id"));
                    user.setUsername(rs.getString("username"));
                    user.setFullName(rs.getString("full_name"));
                    user.setEmail(rs.getString("email"));
                    user.setPhone(rs.getString("phone"));
                    user.setRole(rs.getString("role"));
                    user.setStatus(rs.getString("status"));
                    return user;
                }
            }
        } catch (SQLException e) {
            System.err.println("Login error: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Hashes a plain text password using BCrypt.
     */
    public String hashPassword(String plainPassword) {
        return passwordEncoder.encode(plainPassword);
    }

    /**
     * Changes the password of a user after verifying the current one.
     * Returns true if the old password matched and the update succeeded.
     */
    public boolean changePassword(Long userId, String oldPassword, String newPassword) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            // 1. Fetch the stored hash
            String storedHash;
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT password_hash FROM users WHERE user_id = ?")) {
                pstmt.setLong(1, userId);
                ResultSet rs = pstmt.executeQuery();
                if (!rs.next()) return false;
                storedHash = rs.getString(1);
            }

            // 2. Verify the old password
            if (!passwordEncoder.matches(oldPassword, storedHash)) {
                return false;
            }

            // 3. Save the new hash
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "UPDATE users SET password_hash = ? WHERE user_id = ?")) {
                pstmt.setString(1, passwordEncoder.encode(newPassword));
                pstmt.setLong(2, userId);
                return pstmt.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            System.err.println("Change password error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Verifies a plain text password against a stored hash.
     */
    public boolean verifyPassword(String plainPassword, String storedHash) {
        return passwordEncoder.matches(plainPassword, storedHash);
    }
}
