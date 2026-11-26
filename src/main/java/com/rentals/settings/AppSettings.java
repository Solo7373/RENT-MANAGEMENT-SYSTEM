package com.rentals.settings;

import com.rentals.dao.SettingsDAO;
import com.rentals.security.SecretStore;

import java.util.HashMap;
import java.util.Map;

/**
 * Typed, cached access to the system settings used by the business logic.
 * Call reload() after the settings screen saves a change.
 */
public final class AppSettings {

    private static final SettingsDAO dao = new SettingsDAO();
    private static Map<String, String> values = new HashMap<>();
    private static boolean loaded = false;

    private AppSettings() {}

    public static synchronized String get(String key, String fallback) {
        ensureLoaded();
        String value = values.get(key);
        return (value == null || value.isBlank()) ? fallback : value;
    }

    public static synchronized int getInt(String key, int fallback) {
        try {
            return Integer.parseInt(get(key, String.valueOf(fallback)).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static synchronized double getDouble(String key, double fallback) {
        try {
            return Double.parseDouble(get(key, String.valueOf(fallback)).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Re-reads every setting from the database. */
    public static synchronized void reload() {
        values = new HashMap<>();
        for (SystemSetting setting : dao.getAll()) {
            values.put(setting.getSettingKey(), setting.getSettingValue());
        }
        loaded = true;
    }

    private static void ensureLoaded() {
        if (!loaded) reload();
    }

    /* ---------- Billing / company ---------- */

    public static String companyName() {
        return get("company_name", "RentalSys");
    }

    public static String companyAddress() {
        return get("company_address", "");
    }

    public static String companyPhone() {
        return get("company_phone", "");
    }

    public static String companyEmail() {
        return get("company_email", "");
    }

    public static String currency() {
        return get("currency", "UGX");
    }

    /** Day of the month rent is due (1-28). */
    public static int dueDay() {
        return Math.max(1, Math.min(28, getInt("due_day", 5)));
    }

    /** Days after the due date before a charge becomes overdue. */
    public static int graceDays() {
        return Math.max(0, Math.min(365, getInt("grace_period_days", 5)));
    }

    /** Late fee percentage applied once to overdue charges. */
    public static double lateFeePercent() {
        return Math.max(0, Math.min(100, getDouble("late_fee_percent", 5)));
    }

    /** Number of backups to keep before the oldest are pruned. */
    public static int backupRetention() {
        return Math.max(1, Math.min(365, getInt("backup_retention", 20)));
    }

    public static String receiptPrefix() {
        return get("receipt_prefix", "RCPT");
    }

    /* ---------- Admin contact ---------- */

    public static String adminName() {
        return get("admin_name", "System Administrator");
    }

    public static String adminPhone() {
        return get("admin_phone", "");
    }

    public static String adminEmail() {
        return get("admin_email", "");
    }

    public static String adminWhatsapp() {
        return get("admin_whatsapp", "");
    }

    /* ---------- SMS gateway ---------- */

    public static boolean smsEnabled() {
        return "true".equalsIgnoreCase(get("sms_enabled", "false"));
    }

    public static String smsProvider() {
        return get("sms_provider", "AFRICAS_TALKING");
    }

    /** Africa's Talking environment: SANDBOX or PRODUCTION. */
    public static String smsEnvironment() {
        return get("sms_environment", "PRODUCTION");
    }

    public static String smsUsername() {
        return get("sms_username", "");
    }

    /** Decrypted API key/token. Returns blank if it cannot be read on this PC. */
    public static String smsApiKey() {
        return SecretStore.decrypt(get("sms_api_key", ""));
    }

    /** Raw value from the database; used only to show a masked summary. */
    public static String smsApiKeyRaw() {
        return get("sms_api_key", "");
    }

    public static String smsApiKeySummary() {
        return SecretStore.savedSummary(smsApiKeyRaw());
    }

    public static boolean smsApiKeyReadable() {
        return SecretStore.canDecrypt(smsApiKeyRaw());
    }

    public static String smsSenderId() {
        return get("sms_sender_id", "");
    }

    public static String smsFromNumber() {
        return get("sms_from_number", "");
    }

    public static String smsGenericUrl() {
        return get("sms_generic_url", "");
    }

    public static String smsCountryCode() {
        return get("sms_country_code", "256");
    }

    /** True when SMS is enabled and the selected provider has the needed fields. */
    public static boolean smsConfigured() {
        if (!smsEnabled()) return false;
        return switch (smsProvider().toUpperCase(java.util.Locale.ROOT)) {
            case "AFRICAS_TALKING" -> !smsUsername().isBlank() && !smsApiKey().isBlank();
            case "TWILIO" -> !smsUsername().isBlank() && !smsApiKey().isBlank()
                && !smsFromNumber().isBlank();
            case "GENERIC" -> !smsGenericUrl().isBlank();
            default -> false;
        };
    }

    /* ---------- Communication automation ---------- */

    /** When true, outbound SMS/WhatsApp attempts are recorded in tenant history. */
    public static boolean autoTrackEnabled() {
        return "true".equalsIgnoreCase(get("auto_track_enabled", "true"));
    }

    /** When true, due reminders are sent automatically while the app is open. */
    public static boolean reminderAutoSendEnabled() {
        return "true".equalsIgnoreCase(get("reminder_auto_send_enabled", "false"));
    }

    /** How often the in-app scheduler scans for due reminders (minutes). */
    public static int reminderSchedulerMinutes() {
        return Math.max(1, Math.min(1440, getInt("reminder_scheduler_minutes", 15)));
    }

    public static boolean voiceEnabled() {
        return "true".equalsIgnoreCase(get("voice_enabled", "false"));
    }

    public static String smsVoiceNumber() {
        return get("sms_voice_number", "");
    }

    /* ---------- WhatsApp Cloud API ---------- */

    public static boolean whatsappEnabled() {
        return "true".equalsIgnoreCase(get("whatsapp_enabled", "false"));
    }

    public static String whatsappApiUrl() {
        return get("whatsapp_api_url", "https://graph.facebook.com/v20.0");
    }

    public static String whatsappPhoneNumberId() {
        return get("whatsapp_phone_number_id", "");
    }

    public static String whatsappAccessToken() {
        return SecretStore.decrypt(get("whatsapp_access_token", ""));
    }

    public static String whatsappAccessTokenRaw() {
        return get("whatsapp_access_token", "");
    }

    public static String whatsappAccessTokenSummary() {
        return SecretStore.savedSummary(whatsappAccessTokenRaw());
    }

    public static boolean whatsappAccessTokenReadable() {
        return SecretStore.canDecrypt(whatsappAccessTokenRaw());
    }

    public static String whatsappBusinessNumber() {
        return get("whatsapp_business_number", "");
    }

    public static String whatsappTemplateName() {
        return get("whatsapp_template_name", "");
    }

    public static String whatsappTemplateLanguage() {
        return get("whatsapp_template_language", "en_US");
    }

    public static boolean whatsappConfigured() {
        return whatsappEnabled()
            && !whatsappPhoneNumberId().isBlank()
            && !whatsappAccessToken().isBlank();
    }

    /* ---------- Cloud backup ---------- */

    public static boolean cloudBackupEnabled() {
        return "true".equalsIgnoreCase(get("cloud_backup_enabled", "false"));
    }

    public static boolean cloudAutoUpload() {
        return "true".equalsIgnoreCase(get("cloud_auto_upload", "true"));
    }

    public static String cloudProvider() {
        return get("cloud_provider", "ONEDRIVE");
    }

    public static String cloudAccount() {
        return get("cloud_account", "");
    }

    public static String cloudFolder() {
        return get("cloud_folder", "");
    }

    public static boolean cloudBackupConfigured() {
        return cloudBackupEnabled() && !cloudFolder().isBlank();
    }

    /* ---------- Setup ---------- */

    public static boolean integrationSetupComplete() {
        return "true".equalsIgnoreCase(get("integration_setup_complete", "false"));
    }
}
