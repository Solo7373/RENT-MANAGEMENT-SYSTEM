package com.rentals.config;

import com.rentals.dao.DatabaseConnection;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.sql.*;

public class DatabaseInitializer {

    private static final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public static void initDatabase() {
        System.out.println("Initializing database...");
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("CREATE TABLE IF NOT EXISTS users (user_id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT NOT NULL UNIQUE, password_hash TEXT NOT NULL, full_name TEXT NOT NULL, email TEXT, phone TEXT, role TEXT DEFAULT 'STAFF', status TEXT DEFAULT 'ACTIVE', created_at TEXT DEFAULT (datetime('now')))");

            stmt.execute("CREATE TABLE IF NOT EXISTS properties (property_id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, address TEXT, description TEXT, status TEXT DEFAULT 'ACTIVE', created_at TEXT DEFAULT (datetime('now')))");

            stmt.execute("CREATE TABLE IF NOT EXISTS rooms (room_id INTEGER PRIMARY KEY AUTOINCREMENT, property_id INTEGER REFERENCES properties(property_id), room_number TEXT NOT NULL, room_type TEXT, rent_amount REAL NOT NULL, status TEXT DEFAULT 'VACANT', condition_notes TEXT, created_at TEXT DEFAULT (datetime('now')))");

            stmt.execute("CREATE TABLE IF NOT EXISTS tenants (tenant_id INTEGER PRIMARY KEY AUTOINCREMENT, full_name TEXT NOT NULL, phone TEXT NOT NULL, whatsapp_number TEXT, email TEXT, id_number TEXT, school_id TEXT, refugee_id TEXT, emergency_contact TEXT, emergency_phone TEXT, room_number TEXT, rent_amount REAL DEFAULT 0, notes TEXT, status TEXT DEFAULT 'ACTIVE', created_at TEXT DEFAULT (datetime('now')))");

            // Add new columns to existing tenants table (for upgrades)
            try { stmt.execute("ALTER TABLE tenants ADD COLUMN whatsapp_number TEXT"); } catch (SQLException e) {}
            try { stmt.execute("ALTER TABLE tenants ADD COLUMN school_id TEXT"); } catch (SQLException e) {}
            try { stmt.execute("ALTER TABLE tenants ADD COLUMN refugee_id TEXT"); } catch (SQLException e) {}
            try { stmt.execute("ALTER TABLE tenants ADD COLUMN room_number TEXT"); } catch (SQLException e) {}
            try { stmt.execute("ALTER TABLE tenants ADD COLUMN rent_amount REAL DEFAULT 0"); } catch (SQLException e) {}

            stmt.execute("CREATE TABLE IF NOT EXISTS tenancies (tenancy_id INTEGER PRIMARY KEY AUTOINCREMENT, tenant_id INTEGER REFERENCES tenants(tenant_id), room_id INTEGER REFERENCES rooms(room_id), start_date TEXT NOT NULL, end_date TEXT, monthly_rent REAL NOT NULL, deposit REAL DEFAULT 0, status TEXT DEFAULT 'ACTIVE', created_at TEXT DEFAULT (datetime('now')))");

            // Upgrade existing tenancies tables
            try { stmt.execute("ALTER TABLE tenancies ADD COLUMN deposit REAL DEFAULT 0"); } catch (SQLException e) {}

            stmt.execute("CREATE TABLE IF NOT EXISTS rent_charges (charge_id INTEGER PRIMARY KEY AUTOINCREMENT, tenancy_id INTEGER REFERENCES tenancies(tenancy_id), amount REAL NOT NULL, paid_amount REAL DEFAULT 0, late_fee_applied REAL DEFAULT 0, due_date TEXT NOT NULL, status TEXT DEFAULT 'PENDING', period_month TEXT, created_at TEXT DEFAULT (datetime('now')))");

            // Upgrade existing rent_charges tables
            try { stmt.execute("ALTER TABLE rent_charges ADD COLUMN paid_amount REAL DEFAULT 0"); } catch (SQLException e) {}
            try { stmt.execute("ALTER TABLE rent_charges ADD COLUMN late_fee_applied REAL DEFAULT 0"); } catch (SQLException e) {}

            stmt.execute("CREATE TABLE IF NOT EXISTS payments (payment_id INTEGER PRIMARY KEY AUTOINCREMENT, tenant_id INTEGER REFERENCES tenants(tenant_id), amount REAL NOT NULL, payment_method TEXT, reference_number TEXT, status TEXT DEFAULT 'PENDING', verified_by INTEGER REFERENCES users(user_id), notes TEXT, payment_date TEXT DEFAULT (date('now')), created_at TEXT DEFAULT (datetime('now')))");

            stmt.execute("CREATE TABLE IF NOT EXISTS payment_allocations (allocation_id INTEGER PRIMARY KEY AUTOINCREMENT, payment_id INTEGER REFERENCES payments(payment_id), charge_id INTEGER REFERENCES rent_charges(charge_id), amount REAL NOT NULL, created_at TEXT DEFAULT (datetime('now')))");

            stmt.execute("CREATE TABLE IF NOT EXISTS payment_methods (method_id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, type TEXT, account_number TEXT, instructions TEXT, enabled INTEGER DEFAULT 1, created_at TEXT DEFAULT (datetime('now')))");

            stmt.execute("CREATE TABLE IF NOT EXISTS notification_logs (notification_id INTEGER PRIMARY KEY AUTOINCREMENT, tenant_id INTEGER REFERENCES tenants(tenant_id), message TEXT NOT NULL, channel TEXT, status TEXT DEFAULT 'PENDING', sent_at TEXT, created_at TEXT DEFAULT (datetime('now')))");
            try { stmt.execute("ALTER TABLE notification_logs ADD COLUMN recipient TEXT"); } catch (SQLException e) {}
            try { stmt.execute("ALTER TABLE notification_logs ADD COLUMN error_message TEXT"); } catch (SQLException e) {}
            try { stmt.execute("ALTER TABLE notification_logs ADD COLUMN tenancy_id INTEGER REFERENCES tenancies(tenancy_id)"); } catch (SQLException e) {}
            try { stmt.execute("ALTER TABLE notification_logs ADD COLUMN charge_id INTEGER REFERENCES rent_charges(charge_id)"); } catch (SQLException e) {}
            try { stmt.execute("ALTER TABLE notification_logs ADD COLUMN payment_id INTEGER REFERENCES payments(payment_id)"); } catch (SQLException e) {}
            try { stmt.execute("ALTER TABLE notification_logs ADD COLUMN rule_id INTEGER REFERENCES reminder_rules(rule_id)"); } catch (SQLException e) {}
            try { stmt.execute("ALTER TABLE notification_logs ADD COLUMN event_type TEXT DEFAULT 'REMINDER'"); } catch (SQLException e) {}
            try { stmt.execute("ALTER TABLE notification_logs ADD COLUMN provider TEXT"); } catch (SQLException e) {}
            try { stmt.execute("ALTER TABLE notification_logs ADD COLUMN provider_message_id TEXT"); } catch (SQLException e) {}
            try { stmt.execute("ALTER TABLE notification_logs ADD COLUMN provider_status TEXT"); } catch (SQLException e) {}
            try { stmt.execute("ALTER TABLE notification_logs ADD COLUMN dedupe_key TEXT"); } catch (SQLException e) {}
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_notification_dedupe ON notification_logs(dedupe_key)");

            // Per-tenant audit trail for every outbound SMS/WhatsApp attempt.
            stmt.execute("CREATE TABLE IF NOT EXISTS communication_logs (" +
                "communication_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "tenant_id INTEGER REFERENCES tenants(tenant_id), " +
                "tenancy_id INTEGER, charge_id INTEGER, payment_id INTEGER, notification_id INTEGER, " +
                "event_type TEXT DEFAULT 'REMINDER', channel TEXT, direction TEXT DEFAULT 'OUTBOUND', " +
                "recipient TEXT, message TEXT, status TEXT, provider TEXT, " +
                "provider_message_id TEXT, provider_status TEXT, error_message TEXT, " +
                "created_at TEXT DEFAULT (datetime('now')), updated_at TEXT DEFAULT (datetime('now')))");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_comm_tenant ON communication_logs(tenant_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_comm_message ON communication_logs(provider_message_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_comm_created ON communication_logs(created_at)");

            stmt.execute("CREATE TABLE IF NOT EXISTS reminder_rules (rule_id INTEGER PRIMARY KEY AUTOINCREMENT, stage INTEGER NOT NULL, days_after_due INTEGER NOT NULL, channel TEXT DEFAULT 'EMAIL', message_template TEXT, enabled INTEGER DEFAULT 1, created_at TEXT DEFAULT (datetime('now')))");

            stmt.execute("CREATE TABLE IF NOT EXISTS expenses (expense_id INTEGER PRIMARY KEY AUTOINCREMENT, property_id INTEGER REFERENCES properties(property_id), description TEXT NOT NULL, amount REAL NOT NULL, expense_date TEXT DEFAULT (date('now')), category TEXT, created_at TEXT DEFAULT (datetime('now')))");

            stmt.execute("CREATE TABLE IF NOT EXISTS tenant_documents (document_id INTEGER PRIMARY KEY AUTOINCREMENT, tenant_id INTEGER REFERENCES tenants(tenant_id), document_type TEXT, file_name TEXT NOT NULL, file_path TEXT NOT NULL, file_size INTEGER DEFAULT 0, uploaded_at TEXT DEFAULT (datetime('now')))");
            try { stmt.execute("ALTER TABLE tenant_documents ADD COLUMN file_size INTEGER DEFAULT 0"); } catch (SQLException e) {}

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_documents_tenant ON tenant_documents(tenant_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_charges_tenancy ON rent_charges(tenancy_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_payments_tenant ON payments(tenant_id)");

            stmt.execute("CREATE TABLE IF NOT EXISTS audit_logs (log_id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER REFERENCES users(user_id), action TEXT NOT NULL, description TEXT, created_at TEXT DEFAULT (datetime('now')))");

            stmt.execute("CREATE TABLE IF NOT EXISTS system_settings (setting_id INTEGER PRIMARY KEY AUTOINCREMENT, setting_key TEXT NOT NULL UNIQUE, setting_value TEXT, description TEXT)");

            // Insert the default admin only on first run. Using INSERT OR IGNORE
            // (instead of REPLACE) means a password changed in Settings survives restarts.
            String hashedPassword = passwordEncoder.encode("admin123");
            stmt.execute("INSERT OR IGNORE INTO users (user_id, username, password_hash, full_name, email, role) " +
                "VALUES (1, 'admin', '" + hashedPassword + "', 'System Administrator', 'admin@rentals.com', 'ADMIN')");

            // Insert default payment methods
            stmt.execute("INSERT OR IGNORE INTO payment_methods (method_id, name, type, instructions) VALUES " +
                "(1, 'MTN Mobile Money', 'MOBILE_MONEY', 'Dial *182*5*1# and follow prompts')," +
                "(2, 'Airtel Money', 'MOBILE_MONEY', 'Dial *185*5*1# and follow prompts')," +
                "(3, 'Cash', 'CASH', 'Pay at the office during working hours')," +
                "(4, 'Bank Transfer', 'BANK', 'Contact admin for bank details')");

            // Seed default system settings (only if missing)
            stmt.execute("INSERT OR IGNORE INTO system_settings (setting_key, setting_value, description) VALUES " +
                "('company_name', 'RentalSys', 'Business name shown in the app and on receipts')," +
                "('company_address', 'Kampala, Uganda', 'Business address printed on receipts')," +
                "('company_phone', '+256 700 000 000', 'Business phone printed on receipts')," +
                "('company_email', 'admin@rentals.com', 'Business email printed on receipts')," +
                "('currency', 'UGX', 'Currency used for all amounts')," +
                "('due_day', '5', 'Day of the month rent is due (1-28)')," +
                "('grace_period_days', '5', 'Days after due date before a charge is overdue')," +
                "('late_fee_percent', '5', 'Late fee percentage applied once to overdue charges')," +
                "('backup_retention', '20', 'Number of automatic/manual backups to keep')," +
                "('receipt_prefix', 'RCPT', 'Prefix used for receipt numbers')," +
                "('admin_name', 'System Administrator', 'Administrator name used for alerts and setup')," +
                "('admin_phone', '', 'Administrator phone used for SMS tests and system alerts')," +
                "('admin_email', 'admin@rentals.com', 'Administrator email')," +
                "('admin_whatsapp', '', 'Administrator WhatsApp number')," +
                "('sms_enabled', 'false', 'Enable SMS delivery through the configured gateway')," +
                "('sms_provider', 'AFRICAS_TALKING', 'SMS gateway: AFRICAS_TALKING, TWILIO or GENERIC')," +
                "('sms_environment', 'PRODUCTION', 'Africa''s Talking environment: SANDBOX or PRODUCTION')," +
                "('sms_username', '', 'Africa''s Talking username or Twilio Account SID')," +
                "('sms_api_key', '', 'SMS gateway API key or Twilio auth token (DPAPI-encrypted)')," +
                "('sms_sender_id', '', 'Sender ID shown on SMS (Africa''s Talking)')," +
                "('sms_from_number', '', 'Twilio sender phone number')," +
                "('sms_generic_url', '', 'Generic SMS gateway URL')," +
                "('sms_country_code', '256', 'Country code used for local phone numbers')," +
                "('sms_voice_number', '', 'Africa''s Talking voice number (reserved for hosted voice)')," +
                "('voice_enabled', 'false', 'Enable automated voice/voicemail (requires a hosted relay)')," +
                "('auto_track_enabled', 'true', 'Record outbound messages in the tenant communication history')," +
                "('reminder_auto_send_enabled', 'false', 'Send due reminders automatically while the app is open')," +
                "('reminder_scheduler_minutes', '15', 'How often the in-app reminder scheduler scans (minutes)')," +
                "('whatsapp_enabled', 'false', 'Enable WhatsApp Cloud API delivery')," +
                "('whatsapp_api_url', 'https://graph.facebook.com/v20.0', 'WhatsApp Cloud API base URL')," +
                "('whatsapp_phone_number_id', '', 'Meta WhatsApp phone number ID')," +
                "('whatsapp_access_token', '', 'Meta WhatsApp Cloud API access token (DPAPI-encrypted)')," +
                "('whatsapp_business_number', '', 'WhatsApp business display number')," +
                "('whatsapp_template_name', '', 'Approved WhatsApp template for first-contact messages')," +
                "('whatsapp_template_language', 'en_US', 'WhatsApp template language code')," +
                "('cloud_backup_enabled', 'false', 'Copy backups to a cloud-synced folder')," +
                "('cloud_provider', 'ONEDRIVE', 'Cloud provider: ONEDRIVE, GOOGLE_DRIVE, DROPBOX or OTHER')," +
                "('cloud_account', '', 'Cloud account email or label shown to the admin')," +
                "('cloud_folder', '', 'Local folder synced by the cloud provider')," +
                "('cloud_auto_upload', 'true', 'Upload each new backup to the cloud folder automatically')," +
                "('integration_setup_complete', 'false', 'Whether the admin completed communication setup')," +
                "('theme', 'light', 'UI theme (light or dark)')");

            // Seed default reminder rules (only if none exist)
            ResultSet ruleCount = stmt.executeQuery("SELECT COUNT(*) FROM reminder_rules");
            if (ruleCount.next() && ruleCount.getInt(1) == 0) {
                stmt.execute("INSERT INTO reminder_rules (stage, days_after_due, channel, message_template, enabled) VALUES " +
                    "(1, 3, 'SMS', 'Hello {name}, your rent of {amount} is overdue. Please pay soon.', 1)," +
                    "(2, 7, 'SMS', 'Hello {name}, your rent of {amount} is now 7 days overdue.', 1)," +
                    "(3, 14, 'EMAIL', 'Dear {name}, your rent of {amount} is 14 days overdue. Please contact the office.', 1)");
            }

            System.out.println("Database ready!");

        } catch (Exception e) {
            System.err.println("Database initialization failed: " + e.getMessage());
            e.printStackTrace();
        }

        // Upgrade any credentials saved by older versions as plain text.
        // Failures are reported but must not stop the application from starting.
        com.rentals.security.SecretStore.migrateStoredSecrets();
    }
}
