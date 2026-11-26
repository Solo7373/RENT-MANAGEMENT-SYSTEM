package com.rentals.security;

import com.rentals.dao.SettingsDAO;
import com.rentals.settings.SystemSetting;
import com.sun.jna.platform.win32.Crypt32Util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

/**
 * Encrypts provider credentials before they are written to the local database.
 *
 * <p>Windows DPAPI is used so the ciphertext is bound to the current Windows
 * user. This means a database copied to another user or PC cannot reveal the
 * original key, but it also means a restored backup on another PC will need the
 * key to be entered again.</p>
 *
 * <p>Values saved by older versions are plain text. {@link #migrateStoredSecrets()}
 * upgrades them in place, and {@link #decrypt(String)} still accepts plain text
 * during the transition.</p>
 */
public final class SecretStore {

    private static final String DPAPI_PREFIX = "dpapi:";
    private static final Set<String> SECRET_KEYS = Set.of(
        "sms_api_key",
        "whatsapp_access_token");

    private SecretStore() {}

    public static boolean isSecretKey(String key) {
        return key != null && SECRET_KEYS.contains(key);
    }

    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(DPAPI_PREFIX);
    }

    /** Encrypts a plain-text secret, returning a storable prefixed value. */
    public static String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) return "";
        if (isEncrypted(plaintext)) return plaintext;
        try {
            byte[] protectedBytes = Crypt32Util.cryptProtectData(
                plaintext.getBytes(StandardCharsets.UTF_8));
            return DPAPI_PREFIX + Base64.getEncoder().encodeToString(protectedBytes);
        } catch (Throwable e) {
            throw new IllegalStateException(
                "Could not encrypt the credential with Windows DPAPI: " + e.getMessage(), e);
        }
    }

    /**
     * Decrypts a stored secret. Plain-text legacy values are returned as-is.
     * Returns an empty string when DPAPI cannot decrypt the value on this PC.
     */
    public static String decrypt(String stored) {
        if (stored == null || stored.isBlank()) return "";
        if (!isEncrypted(stored)) return stored;
        try {
            byte[] protectedBytes = Base64.getDecoder()
                .decode(stored.substring(DPAPI_PREFIX.length()));
            byte[] plainBytes = Crypt32Util.cryptUnprotectData(protectedBytes);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Throwable e) {
            System.err.println("Stored credential cannot be decrypted on this PC: "
                + e.getMessage());
            return "";
        }
    }

    /** True when a stored value exists and can be read on this PC. */
    public static boolean canDecrypt(String stored) {
        return stored != null && !stored.isBlank() && !decrypt(stored).isBlank();
    }

    /** Human-readable summary for a saved secret; never returns the full key. */
    public static String savedSummary(String stored) {
        if (stored == null || stored.isBlank()) return "";
        String plain = decrypt(stored);
        if (plain.isBlank()) return "saved credential cannot be read on this PC";
        return "saved credential " + mask(plain);
    }

    /** Masks a secret, keeping only the final four characters. */
    public static String mask(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) return "";
        String trimmed = plaintext.trim();
        if (trimmed.length() <= 4) return "••••";
        return "••••••••" + trimmed.substring(trimmed.length() - 4);
    }

    /**
     * Encrypts any legacy plain-text secrets in the settings table.
     * Failures are reported but do not stop application startup.
     */
    public static void migrateStoredSecrets() {
        try {
            SettingsDAO dao = new SettingsDAO();
            for (SystemSetting setting : dao.getAll()) {
                if (!isSecretKey(setting.getSettingKey())) continue;
                String value = setting.getSettingValue();
                if (value == null || value.isBlank() || isEncrypted(value)) continue;
                dao.save(setting.getSettingKey(), encrypt(value), setting.getDescription());
                System.out.println("Encrypted stored credential: " + setting.getSettingKey());
            }
        } catch (Exception e) {
            System.err.println("Credential encryption migration skipped: " + e.getMessage());
        }
    }
}
