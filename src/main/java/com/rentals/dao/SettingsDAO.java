package com.rentals.dao;

import com.rentals.security.SecretStore;
import com.rentals.settings.SystemSetting;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for system settings (key/value configuration).
 */
public class SettingsDAO {

    /** Gets all settings. */
    public List<SystemSetting> getAll() {
        List<SystemSetting> settings = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM system_settings ORDER BY setting_key")) {
            while (rs.next()) {
                SystemSetting s = new SystemSetting();
                s.setSettingId(rs.getLong("setting_id"));
                s.setSettingKey(rs.getString("setting_key"));
                s.setSettingValue(rs.getString("setting_value"));
                s.setDescription(rs.getString("description"));
                settings.add(s);
            }
        } catch (SQLException e) {
            System.err.println("Get settings error: " + e.getMessage());
        }
        return settings;
    }

    /** Gets one setting value by key, or null if it doesn't exist. */
    public String get(String key) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement("SELECT setting_value FROM system_settings WHERE setting_key = ?")) {
            pstmt.setString(1, key);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getString(1);
        } catch (SQLException e) {
            System.err.println("Get setting error: " + e.getMessage());
        }
        return null;
    }

    /** Inserts or updates a setting. */
    public boolean save(String key, String value, String description) {
        String sql = "INSERT INTO system_settings (setting_key, setting_value, description) VALUES (?, ?, ?) " +
                     "ON CONFLICT(setting_key) DO UPDATE SET setting_value = excluded.setting_value, description = excluded.description";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, key);
            pstmt.setString(2, value);
            pstmt.setString(3, description);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Save setting error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Encrypts a credential with Windows DPAPI and stores the ciphertext.
     * A blank value clears the saved credential.
     */
    public boolean saveSecret(String key, String plaintext, String description) {
        try {
            return save(key, SecretStore.encrypt(plaintext == null ? "" : plaintext), description);
        } catch (RuntimeException e) {
            System.err.println("Save encrypted setting error: " + e.getMessage());
            return false;
        }
    }

    /** Deletes a setting. */
    public boolean delete(String key) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement("DELETE FROM system_settings WHERE setting_key = ?")) {
            pstmt.setString(1, key);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Delete setting error: " + e.getMessage());
            return false;
        }
    }
}
