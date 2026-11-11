import com.rentals.config.AppPaths;
import com.rentals.config.DatabaseInitializer;
import com.rentals.dao.DatabaseConnection;
import com.rentals.dao.ExpenseDAO;
import com.rentals.dao.PaymentDAO;
import com.rentals.dao.ReminderDAO;
import com.rentals.dao.RentChargeDAO;
import com.rentals.dao.SettingsDAO;
import com.rentals.dao.TenancyDAO;
import com.rentals.dao.TenantDAO;
import com.rentals.documents.TenantDocument;
import com.rentals.expenses.Expense;
import com.rentals.notifications.NotificationLog;
import com.rentals.payments.Payment;
import com.rentals.service.BackupService;
import com.rentals.service.CloudBackupService;
import com.rentals.service.DocumentService;
import com.rentals.service.MessagingService;
import com.rentals.service.NotificationService;
import com.rentals.service.ReceiptService;
import com.rentals.service.ReportExportService;
import com.rentals.settings.AppSettings;
import com.rentals.tenancies.Tenancy;
import com.rentals.tenants.Tenant;
import com.rentals.util.FormatUtils;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipFile;

/** End-to-end check of the "Now" phase features against an isolated data folder. */
public final class NowPhaseSmokeTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        Path dataDir = Files.createTempDirectory("rental-now-smoke");
        System.setProperty("rentals.dataDir", dataDir.toString());

        System.out.println("Data folder: " + dataDir);
        DatabaseInitializer.initDatabase();

        check("database created in isolated folder",
            Files.exists(dataDir.resolve("rentaldb.sqlite")));
        check("default due day seeded", AppSettings.dueDay() == 5);
        check("default currency seeded", AppSettings.currency().equals("UGX"));

        // ---------- Configurable billing / company ----------
        SettingsDAO settingsDAO = new SettingsDAO();
        settingsDAO.save("company_name", "Acme Rentals", "test");
        settingsDAO.save("company_address", "12 Test Road, Kampala", "test");
        settingsDAO.save("company_phone", "+256 700 111 222", "test");
        settingsDAO.save("company_email", "hello@acme.test", "test");
        settingsDAO.save("currency", "TST", "test");
        settingsDAO.save("due_day", "10", "test");
        settingsDAO.save("grace_period_days", "5", "test");
        settingsDAO.save("late_fee_percent", "5", "test");
        settingsDAO.save("receipt_prefix", "TST-RCPT", "test");
        settingsDAO.save("backup_retention", "2", "test");
        AppSettings.reload();

        check("company name reloaded", AppSettings.companyName().equals("Acme Rentals"));
        check("currency reloaded", FormatUtils.money(1250000).equals("TST 1,250,000"));
        check("due day reloaded", AppSettings.dueDay() == 10);
        check("late fee percentage reloaded", AppSettings.lateFeePercent() == 5.0);
        check("backup retention reloaded", AppSettings.backupRetention() == 2);

        // ---------- Seed a property / room / tenant / tenancy ----------
        long propertyId;
        long roomId;
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("INSERT INTO properties (name, address) VALUES ('Test Court', 'Kampala')");
            propertyId = DatabaseConnection.lastInsertId(conn);
            stmt.executeUpdate("INSERT INTO rooms (property_id, room_number, room_type, rent_amount, status) " +
                "VALUES (" + propertyId + ", 'A1', 'Single', 500000, 'VACANT')");
            roomId = DatabaseConnection.lastInsertId(conn);
        }

        Tenant tenant = new Tenant("Test Tenant", "0700000000");
        tenant.setIdNumber("ID-123");
        check("tenant created", new TenantDAO().create(tenant));
        long tenantId = tenant.getTenantId();

        Tenancy tenancy = new Tenancy(tenantId, roomId, java.time.LocalDate.now().toString(), 500000);
        check("tenancy created", new TenancyDAO().create(tenancy));

        // ---------- Rent charge generation uses the configured due day ----------
        RentChargeDAO rentChargeDAO = new RentChargeDAO();
        check("one rent charge generated", rentChargeDAO.generateForCurrentMonth() == 1);

        String expectedDue = YearMonth.now().atDay(10).toString();
        String actualDue = singleString("SELECT due_date FROM rent_charges");
        check("due date follows due_day setting", expectedDue.equals(actualDue));

        // ---------- Late fees ----------
        execute("UPDATE rent_charges SET due_date = date('now', '-10 days')");
        check("overdue charge detected", rentChargeDAO.countLateFeeCandidates() == 1);

        RentChargeDAO.LateFeeResult fees = rentChargeDAO.applyLateFees();
        check("late fee applied to one charge", fees.charges() == 1);
        check("late fee amount is 5%", Math.abs(fees.totalFees() - 25000) < 0.01);
        check("charge amount increased", Math.abs(doubleValue("SELECT amount FROM rent_charges") - 525000) < 0.01);
        check("late fee is not applied twice", rentChargeDAO.applyLateFees().charges() == 0);

        // ---------- Payment allocation and receipt ----------
        Payment payment = new Payment(tenantId, 525000.0, "Cash");
        payment.setReferenceNumber("CASH-001");
        payment.setNotes("Smoke test payment");
        payment.setStatus("PENDING");
        PaymentDAO paymentDAO = new PaymentDAO();
        check("payment recorded", paymentDAO.create(payment));
        check("payment verified", paymentDAO.updateStatus(payment.getPaymentId(), "VERIFIED"));
        double allocated = rentChargeDAO.allocatePayment(
            payment.getPaymentId(), tenantId, payment.getAmount());
        check("payment fully allocated", Math.abs(allocated - 525000) < 0.01);
        check("outstanding charge is now paid",
            singleString("SELECT status FROM rent_charges").equals("PAID"));
        List<Object[]> allocations = rentChargeDAO.getAllocationsForPayment(payment.getPaymentId());
        check("receipt allocation rows available", allocations.size() == 1);

        Path receipt = new ReceiptService().generate(payment);
        check("receipt PDF created", Files.exists(receipt) && Files.size(receipt) > 500);
        check("receipt starts with PDF header", startsWithPdf(receipt));
        check("receipt name uses configured prefix", receipt.getFileName().toString().startsWith("receipt-TST-RCPT-"));

        // ---------- Expenses ----------
        Expense expense = new Expense(propertyId, "Repair tap", 50000.0, "Maintenance");
        check("expense recorded", new ExpenseDAO().create(expense));

        // ---------- Report exports ----------
        ReportExportService exportService = new ReportExportService();
        Path summaryCsv = exportService.exportCsv(ReportExportService.ReportType.SUMMARY);
        String csv = Files.readString(summaryCsv, StandardCharsets.UTF_8);
        check("summary CSV created", Files.exists(summaryCsv) && Files.size(summaryCsv) > 50);
        check("summary CSV has headers", csv.contains("Metric") && csv.contains("Total Revenue"));
        check("summary CSV uses configured currency", csv.contains("TST 1,250,000") || csv.contains("TST 525,000"));

        Path outstandingCsv = exportService.exportCsv(ReportExportService.ReportType.OUTSTANDING);
        check("outstanding CSV created", Files.size(outstandingCsv) > 10);

        Path propertyCsv = exportService.exportCsv(ReportExportService.ReportType.PROPERTIES);
        check("property CSV includes data", Files.readString(propertyCsv, StandardCharsets.UTF_8).contains("Test Court"));

        Path revenuePdf = exportService.exportPdf(ReportExportService.ReportType.MONTHLY_REVENUE);
        check("monthly revenue PDF created", startsWithPdf(revenuePdf));

        Path expensesPdf = exportService.exportPdf(ReportExportService.ReportType.EXPENSES);
        check("expenses PDF created", startsWithPdf(expensesPdf));

        // ---------- Tenant documents ----------
        Path sourceDoc = Files.createTempFile("tenant-doc", ".txt");
        Files.writeString(sourceDoc, "This is a test identity document.");
        DocumentService documentService = new DocumentService();
        TenantDocument document = documentService.attach(tenantId, "National ID", sourceDoc);
        check("document record and file created",
            document.getDocumentId() != null && Files.exists(Path.of(document.getFilePath())));
        check("document listed for tenant", documentService.list(tenantId).size() == 1);
        check("document stored in tenant folder",
            Path.of(document.getFilePath()).startsWith(dataDir.resolve("documents").resolve("tenant-" + tenantId)));

        // ---------- Communication setup with a local mock gateway ----------
        // A mock HTTP server stands in for the SMS/WhatsApp providers so the test
        // runs without live credentials or an internet connection.
        AtomicReference<String> smsBody = new AtomicReference<>("");
        AtomicReference<String> whatsappBody = new AtomicReference<>("");

        HttpServer mock = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mock.createContext("/sms", exchange -> {
            smsBody.set(new String(exchange.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8));
            byte[] reply = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, reply.length);
            try (var out = exchange.getResponseBody()) { out.write(reply); }
        });
        mock.createContext("/test-phone-id/messages", exchange -> {
            whatsappBody.set(new String(exchange.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8));
            byte[] reply = "{\"messages\":[{\"id\":\"mock\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, reply.length);
            try (var out = exchange.getResponseBody()) { out.write(reply); }
        });
        mock.createContext("/fail", exchange -> {
            byte[] reply = "gateway down".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, reply.length);
            try (var out = exchange.getResponseBody()) { out.write(reply); }
        });
        mock.start();
        int gatewayPort = mock.getAddress().getPort();
        String gatewayUrl = "http://127.0.0.1:" + gatewayPort;

        Path cloudFolder = Files.createTempDirectory("rental-cloud");
        settingsDAO.save("admin_name", "Test Admin", "test");
        settingsDAO.save("admin_phone", "+256700123456", "test");
        settingsDAO.save("admin_email", "admin@acme.test", "test");
        settingsDAO.save("admin_whatsapp", "+256700123456", "test");
        settingsDAO.save("sms_enabled", "true", "test");
        settingsDAO.save("sms_provider", "GENERIC", "test");
        settingsDAO.save("sms_api_key", "test-token", "test");
        settingsDAO.save("sms_generic_url", gatewayUrl + "/sms", "test");
        settingsDAO.save("sms_country_code", "256", "test");
        settingsDAO.save("whatsapp_enabled", "true", "test");
        settingsDAO.save("whatsapp_api_url", gatewayUrl, "test");
        settingsDAO.save("whatsapp_phone_number_id", "test-phone-id", "test");
        settingsDAO.save("whatsapp_access_token", "test-token", "test");
        settingsDAO.save("cloud_backup_enabled", "true", "test");
        settingsDAO.save("cloud_provider", "OTHER", "test");
        settingsDAO.save("cloud_account", "admin@acme.test", "test");
        settingsDAO.save("cloud_folder", cloudFolder.toString(), "test");
        settingsDAO.save("cloud_auto_upload", "true", "test");
        settingsDAO.save("integration_setup_complete", "true", "test");
        AppSettings.reload();

        check("admin contact saved", "+256700123456".equals(AppSettings.adminPhone()));
        check("SMS setup reported as configured", AppSettings.smsConfigured());
        check("WhatsApp setup reported as configured", AppSettings.whatsappConfigured());
        check("cloud backup reported as configured", AppSettings.cloudBackupConfigured());
        check("integration setup flag saved", AppSettings.integrationSetupComplete());

        check("local number normalizes with country code",
            MessagingService.normalizePhone("0700123456", "256").equals("+256700123456"));
        check("formatted number normalizes",
            MessagingService.normalizePhone("+256 700 123 456", "256").equals("+256700123456"));
        check("international digits normalize",
            MessagingService.normalizePhone("256700123456", "256").equals("+256700123456"));

        MessagingService.Result smsResult = MessagingService.sendSms(
            MessagingService.SmsConfig.fromSettings(), "0700123456", "SMS gateway test");
        check("test SMS sent through configured gateway", smsResult.success());
        check("SMS used the normalized admin number",
            smsBody.get().contains("+256700123456"));
        check("SMS carried the reminder text",
            smsBody.get().contains("SMS gateway test"));

        MessagingService.Result whatsappResult = MessagingService.sendWhatsApp(
            MessagingService.WhatsAppConfig.fromSettings(), "+256700123456",
            "WhatsApp gateway test");
        check("test WhatsApp message sent", whatsappResult.success());
        check("WhatsApp used the Cloud API phone ID path",
            whatsappBody.get().contains("+256700123456")
                && whatsappBody.get().contains("WhatsApp gateway test"));

        MessagingService.Result failedSms = MessagingService.sendSms(
            new MessagingService.SmsConfig(true, MessagingService.SmsProvider.GENERIC,
                "", "", "", "", gatewayUrl + "/fail", "256"),
            "+256700123456", "failure test");
        check("gateway HTTP errors are reported",
            !failedSms.success() && failedSms.detail().contains("HTTP 500"));

        ReminderDAO reminderDAO = new ReminderDAO();
        check("SMS reminder queued",
            reminderDAO.queueNotification(tenantId, "Rent reminder via SMS", "SMS"));
        NotificationLog smsLog = reminderDAO.getAllLogs().get(0);
        NotificationService.SendResult sentSms = NotificationService.send(smsLog);
        NotificationLog storedSms = reminderDAO.getAllLogs().get(0);
        check("queued SMS reminder delivered", sentSms.success());
        check("SMS reminder marked SENT", "SENT".equals(storedSms.getStatus()));
        check("SMS recipient recorded", "0700000000".equals(storedSms.getRecipient()));
        check("tenant number normalized during delivery",
            smsBody.get().contains("+256700000000"));

        check("WhatsApp reminder queued",
            reminderDAO.queueNotification(tenantId, "Rent reminder via WhatsApp", "WHATSAPP"));
        NotificationLog waLog = reminderDAO.getAllLogs().get(0);
        NotificationService.SendResult sentWa = NotificationService.send(waLog);
        NotificationLog storedWa = reminderDAO.getAllLogs().get(0);
        check("queued WhatsApp reminder delivered", sentWa.success());
        check("WhatsApp reminder marked SENT", "SENT".equals(storedWa.getStatus()));
        check("WhatsApp fell back to the tenant phone",
            whatsappBody.get().contains("+256700000000"));

        check("email reminder queued",
            reminderDAO.queueNotification(tenantId, "Rent reminder via email", "EMAIL"));
        NotificationLog emailLog = reminderDAO.getAllLogs().get(0);
        NotificationService.SendResult sentEmail = NotificationService.send(emailLog);
        NotificationLog storedEmail = reminderDAO.getAllLogs().get(0);
        check("email is reported as not configured", !sentEmail.success());
        check("failed email reminder marked FAILED",
            "FAILED".equals(storedEmail.getStatus())
                && storedEmail.getErrorMessage() != null
                && storedEmail.getErrorMessage().contains("not configured"));

        settingsDAO.save("sms_generic_url", gatewayUrl + "/fail", "test");
        AppSettings.reload();
        check("failing SMS reminder queued",
            reminderDAO.queueNotification(tenantId, "This message will fail", "SMS"));
        NotificationLog failLog = reminderDAO.getAllLogs().get(0);
        NotificationService.SendResult failedReminder = NotificationService.send(failLog);
        NotificationLog storedFail = reminderDAO.getAllLogs().get(0);
        check("failed reminder marked FAILED",
            !failedReminder.success() && "FAILED".equals(storedFail.getStatus()));
        check("provider error stored on the reminder",
            storedFail.getErrorMessage() != null
                && storedFail.getErrorMessage().contains("HTTP 500"));
        check("failed reminder records the recipient",
            "0700000000".equals(storedFail.getRecipient()));

        settingsDAO.save("sms_generic_url", gatewayUrl + "/sms", "test");
        AppSettings.reload();

        check("cloud folder test succeeds",
            CloudBackupService.testFolder(cloudFolder).success());

        // ---------- Safer backups ----------
        Path backup = BackupService.createBackup();
        check("backup zip created", Files.exists(backup) && Files.size(backup) > 200);
        check("backup checksum verifies", BackupService.verify(backup));
        check("backup list contains backup", BackupService.listBackups().size() == 1);

        try (ZipFile zip = new ZipFile(backup.toFile())) {
            check("backup contains database snapshot", zip.getEntry("rentaldb.sqlite") != null);
            check("backup contains backup info", zip.getEntry("backup-info.txt") != null);
            check("backup contains tenant documents",
                zip.stream().anyMatch(e -> e.getName().startsWith("documents/tenant-" + tenantId + "/")));
        }

        BackupService.createBackup();
        BackupService.createBackup();
        check("backup retention keeps only configured number", BackupService.listBackups().size() == 2);

        // ---------- Cloud backup copies ----------
        Path latestLocal = BackupService.listBackups().get(0).path();
        CloudBackupService.Result upload = CloudBackupService.upload(latestLocal);
        check("backup uploaded to cloud folder",
            upload.success() && upload.uploaded() != null);
        check("cloud backup file exists",
            upload.uploaded() != null && Files.exists(upload.uploaded()));
        Path cloudChecksum = upload.uploaded() == null ? null
            : upload.uploaded().resolveSibling(upload.uploaded().getFileName() + ".sha256");
        check("cloud checksum file exists",
            cloudChecksum != null && Files.exists(cloudChecksum));
        check("cloud copy checksum verifies",
            upload.uploaded() != null && BackupService.verify(upload.uploaded()));

        BackupService.BackupOutcome outcome = BackupService.createBackupAndUpload();
        check("backup and automatic cloud upload succeed",
            outcome.cloudAttempted() && outcome.cloudOk()
                && outcome.cloudCopy() != null && Files.exists(outcome.cloudCopy()));

        BackupService.createBackupAndUpload();
        BackupService.createBackupAndUpload();
        check("cloud retention keeps only configured number",
            CloudBackupService.countCloudBackups(cloudFolder) == 2);
        long cloudChecksumCount;
        try (var stream = Files.list(cloudFolder)) {
            cloudChecksumCount = stream
                .filter(p -> p.getFileName().toString().endsWith(".sha256"))
                .count();
        }
        check("cloud checksum files follow retention", cloudChecksumCount == 2);

        mock.stop(0);

        // ---------- Clean-up path used when a tenant is deleted ----------
        documentService.deleteAllForTenant(tenantId);
        check("tenant documents removed", documentService.list(tenantId).isEmpty());
        check("tenant folder removed", !Files.exists(documentService.tenantFolder(tenantId)));

        System.out.println();
        System.out.println("Result: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  PASS  " + name);
        } else {
            failed++;
            System.out.println("  FAIL  " + name);
        }
    }

    private static void execute(String sql) throws Exception {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    private static String singleString(String sql) throws Exception {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private static double doubleValue(String sql) throws Exception {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getDouble(1) : Double.NaN;
        }
    }

    private static boolean startsWithPdf(Path file) throws Exception {
        try (var in = Files.newInputStream(file)) {
            byte[] header = in.readNBytes(5);
            return new String(header, StandardCharsets.US_ASCII).equals("%PDF-");
        }
    }
}
