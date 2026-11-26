package com.rentals.ui.settings;

import com.rentals.audit.AuditLog;
import com.rentals.config.AppPaths;
import com.rentals.dao.AuditDAO;
import com.rentals.dao.PaymentMethodDAO;
import com.rentals.dao.SettingsDAO;
import com.rentals.payments.PaymentMethod;
import com.rentals.security.SecretStore;
import com.rentals.service.AuditService;
import com.rentals.service.AuthenticationService;
import com.rentals.service.BackupService;
import com.rentals.service.CloudBackupService;
import com.rentals.service.SessionService;
import com.rentals.settings.AppSettings;
import com.rentals.settings.SystemSetting;
import com.rentals.util.FormatUtils;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.List;

/**
 * Settings view - account, system settings, payment methods,
 * activity log and database backup, organised in tabs.
 */
public class SettingsView {

    private final SettingsDAO settingsDAO = new SettingsDAO();
    private final AuthenticationService authService = new AuthenticationService();
    private final PaymentMethodDAO paymentMethodDAO = new PaymentMethodDAO();
    private final AuditDAO auditDAO = new AuditDAO();

    private final ObservableList<SystemSetting> settings = FXCollections.observableArrayList();
    private final ObservableList<PaymentMethod> methods = FXCollections.observableArrayList();
    private final ObservableList<AuditLog> auditLogs = FXCollections.observableArrayList();
    private final ObservableList<BackupService.BackupInfo> backups = FXCollections.observableArrayList();
    private Stage stage;
    private Runnable onOpenReminders;

    public static Pane create(Stage stage, Runnable onBack) {
        return create(stage, onBack, null);
    }

    public static Pane create(Stage stage, Runnable onBack, Runnable onOpenReminders) {
        return new SettingsView().build(stage, onBack, onOpenReminders);
    }

    private Pane build(Stage stage, Runnable onBack, Runnable onOpenReminders) {
        this.stage = stage;
        this.onOpenReminders = onOpenReminders;
        VBox root = new VBox(16);
        root.getStyleClass().add("card");
        root.setPadding(new Insets(25));
        root.setAlignment(Pos.TOP_LEFT);

        // ---------- Header ----------
        HBox header = new HBox(15);
        header.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label("⚙️ Settings");
        titleLabel.getStyleClass().add("label-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button backBtn = new Button("Back to Dashboard");
        backBtn.getStyleClass().add("btn-back");
        backBtn.setOnAction(e -> onBack.run());

        header.getChildren().addAll(titleLabel, spacer, backBtn);
        root.getChildren().add(header);

        // ---------- Tabs ----------
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tabs, Priority.ALWAYS);

        tabs.getTabs().addAll(
            new Tab("🔑 Account", buildAccountTab()),
            new Tab("🏢 Billing & Company", buildBillingTab()),
            new Tab("📡 Communication", buildCommunicationTab()),
            new Tab("🧾 System Settings", buildSettingsTab()),
            new Tab("💳 Payment Methods", buildPaymentMethodsTab()),
            new Tab("📜 Activity Log", buildActivityTab()),
            new Tab("💾 Backup", buildBackupTab())
        );

        root.getChildren().add(tabs);

        refreshSettings();
        refreshMethods();
        refreshAudit();
        refreshBackups();

        return root;
    }

    /* ==================== ACCOUNT TAB ==================== */

    private VBox buildAccountTab() {
        VBox box = new VBox(15);
        box.setPadding(new Insets(20));
        box.setAlignment(Pos.TOP_LEFT);

        Label subtitle = new Label("Change the password used to log in to the application.");
        subtitle.getStyleClass().add("label-subtitle");

        HBox form = new HBox(12);
        form.setAlignment(Pos.CENTER_LEFT);

        PasswordField oldPasswordField = new PasswordField();
        oldPasswordField.setPromptText("Current password");
        oldPasswordField.setPrefWidth(200);

        PasswordField newPasswordField = new PasswordField();
        newPasswordField.setPromptText("New password");
        newPasswordField.setPrefWidth(200);

        PasswordField confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Confirm new password");
        confirmPasswordField.setPrefWidth(200);

        Button changePasswordBtn = new Button("Change Password");
        changePasswordBtn.getStyleClass().add("btn-primary");
        changePasswordBtn.setOnAction(e -> {
            if (SessionService.getCurrentUser() == null) {
                showAlert("No active session - please log in again");
                return;
            }
            String oldPw = oldPasswordField.getText();
            String newPw = newPasswordField.getText();
            String confirmPw = confirmPasswordField.getText();

            if (oldPw.isEmpty() || newPw.isEmpty() || confirmPw.isEmpty()) {
                showAlert("Please fill all password fields");
                return;
            }
            if (newPw.length() < 6) {
                showAlert("New password must be at least 6 characters");
                return;
            }
            if (!newPw.equals(confirmPw)) {
                showAlert("New passwords do not match");
                return;
            }

            boolean changed = authService.changePassword(
                SessionService.getCurrentUser().getUserId(), oldPw, newPw);

            if (changed) {
                AuditService.log("PASSWORD_CHANGE", "Admin changed their password");
                showInfo("Password changed successfully");
                oldPasswordField.clear();
                newPasswordField.clear();
                confirmPasswordField.clear();
                refreshAudit();
            } else {
                showAlert("Current password is incorrect");
            }
        });

        form.getChildren().addAll(oldPasswordField, newPasswordField, confirmPasswordField, changePasswordBtn);

        box.getChildren().addAll(subtitle, form);
        return box;
    }

    /* ==================== BILLING & COMPANY TAB ==================== */

    private VBox buildBillingTab() {
        VBox box = new VBox(15);
        box.setPadding(new Insets(20));
        box.setAlignment(Pos.TOP_LEFT);

        Label subtitle = new Label(
            "These values control rent due dates, late fees, and what is printed on receipts and reports.");
        subtitle.getStyleClass().add("label-subtitle");
        subtitle.setWrapText(true);

        GridPane grid = new GridPane();
        grid.setHgap(18);
        grid.setVgap(12);
        grid.setPadding(new Insets(6, 0, 6, 0));

        TextField companyName = new TextField(AppSettings.companyName());
        companyName.setPrefWidth(300);
        TextField companyAddress = new TextField(AppSettings.companyAddress());
        companyAddress.setPrefWidth(300);
        TextField companyPhone = new TextField(AppSettings.companyPhone());
        companyPhone.setPrefWidth(300);
        TextField companyEmail = new TextField(AppSettings.companyEmail());
        companyEmail.setPrefWidth(300);
        TextField currency = new TextField(AppSettings.currency());
        currency.setPrefWidth(120);
        TextField receiptPrefix = new TextField(AppSettings.receiptPrefix());
        receiptPrefix.setPrefWidth(120);

        Spinner<Integer> dueDay = new Spinner<>(1, 28, AppSettings.dueDay());
        dueDay.setEditable(true);
        dueDay.setPrefWidth(100);

        Spinner<Integer> graceDays = new Spinner<>(0, 365, AppSettings.graceDays());
        graceDays.setEditable(true);
        graceDays.setPrefWidth(100);

        TextField lateFee = new TextField(String.valueOf(AppSettings.lateFeePercent()));
        lateFee.setPrefWidth(100);

        Spinner<Integer> retention = new Spinner<>(1, 365, AppSettings.backupRetention());
        retention.setEditable(true);
        retention.setPrefWidth(100);

        int row = 0;
        addFormRow(grid, row++, "Company name", companyName,
            "Printed at the top of receipts and reports");
        addFormRow(grid, row++, "Address", companyAddress,
            "Printed under the company name");
        addFormRow(grid, row++, "Phone", companyPhone,
            "Contact phone shown on receipts");
        addFormRow(grid, row++, "Email", companyEmail,
            "Contact email shown on receipts");
        addFormRow(grid, row++, "Currency", currency,
            "Prefix used for all money values (e.g. UGX, USD, KES)");
        addFormRow(grid, row++, "Rent due day", dueDay,
            "Day of the month rent is due (1-28)");
        addFormRow(grid, row++, "Grace period (days)", graceDays,
            "Days after the due date before a charge becomes overdue");
        addFormRow(grid, row++, "Late fee (%)", lateFee,
            "Applied once to each overdue charge when you run Apply Late Fees");
        addFormRow(grid, row++, "Receipt prefix", receiptPrefix,
            "Used in receipt numbers, e.g. RCPT-20261110-00001");
        addFormRow(grid, row++, "Backups to keep", retention,
            "Older backups are deleted automatically after a new backup");

        Label preview = new Label("Formatting preview:  " + FormatUtils.money(1250000));
        preview.getStyleClass().add("label-subtitle");

        Button saveBtn = new Button("💾 Save Billing Settings");
        saveBtn.getStyleClass().add("btn-success");
        saveBtn.setOnAction(e -> {
            double fee;
            try {
                fee = Double.parseDouble(lateFee.getText().trim());
                if (fee < 0 || fee > 100) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                showAlert("Late fee must be a number between 0 and 100.");
                return;
            }
            if (currency.getText().isBlank()) {
                showAlert("Currency is required.");
                return;
            }

            saveSetting("company_name", companyName.getText(), "Business name shown in the app and on receipts");
            saveSetting("company_address", companyAddress.getText(), "Business address printed on receipts");
            saveSetting("company_phone", companyPhone.getText(), "Business phone printed on receipts");
            saveSetting("company_email", companyEmail.getText(), "Business email printed on receipts");
            saveSetting("currency", currency.getText().trim().toUpperCase(), "Currency used for all amounts");
            saveSetting("due_day", String.valueOf(dueDay.getValue()), "Day of the month rent is due (1-28)");
            saveSetting("grace_period_days", String.valueOf(graceDays.getValue()),
                "Days after due date before a charge is overdue");
            saveSetting("late_fee_percent", String.valueOf(fee),
                "Late fee percentage applied once to overdue charges");
            saveSetting("receipt_prefix", receiptPrefix.getText().trim().toUpperCase(),
                "Prefix used for receipt numbers");
            saveSetting("backup_retention", String.valueOf(retention.getValue()),
                "Number of automatic/manual backups to keep");

            AppSettings.reload();
            AuditService.log("BILLING_SETTINGS_SAVE", "Updated billing and company settings");
            refreshSettings();
            refreshAudit();
            preview.setText("Formatting preview:  " + FormatUtils.money(1250000));
            showInfo("Billing and company settings saved.");
        });

        box.getChildren().addAll(subtitle, grid, preview, saveBtn);
        return box;
    }

    private void addFormRow(GridPane grid, int row, String labelText,
                            Control field, String hint) {
        Label label = new Label(labelText);
        label.setMinWidth(150);
        grid.add(label, 0, row);
        grid.add(field, 1, row);

        Label hintLabel = new Label(hint);
        hintLabel.getStyleClass().add("label-subtitle");
        grid.add(hintLabel, 2, row);
    }

    private void saveSetting(String key, String value, String description) {
        settingsDAO.save(key, value, description);
    }

    /* ==================== COMMUNICATION TAB ==================== */

    private VBox buildCommunicationTab() {
        VBox box = new VBox(15);
        box.setPadding(new Insets(20));
        box.setAlignment(Pos.TOP_LEFT);

        Label subtitle = new Label(
            "Connect the administrator's contact details, SMS gateway, WhatsApp Cloud API " +
            "account and cloud backup folder. The setup wizard asks for all of them.");
        subtitle.getStyleClass().add("label-subtitle");
        subtitle.setWrapText(true);

        Label adminValue = new Label();
        Label adminDetail = new Label();
        Label smsValue = new Label();
        Label smsDetail = new Label();
        Label whatsappValue = new Label();
        Label whatsappDetail = new Label();
        Label cloudValue = new Label();
        Label cloudDetail = new Label();
        Label automationValue = new Label();
        Label automationDetail = new Label();

        GridPane status = new GridPane();
        status.setHgap(18);
        status.setVgap(12);
        addStatusRow(status, 0, "Admin contact", adminValue, adminDetail);
        addStatusRow(status, 1, "SMS", smsValue, smsDetail);
        addStatusRow(status, 2, "WhatsApp", whatsappValue, whatsappDetail);
        addStatusRow(status, 3, "Cloud backup", cloudValue, cloudDetail);
        addStatusRow(status, 4, "Automation", automationValue, automationDetail);

        Runnable refresh = () -> {
            String name = AppSettings.adminName();
            String phone = AppSettings.adminPhone();
            String email = AppSettings.adminEmail();
            String whatsapp = AppSettings.adminWhatsapp();
            adminValue.setText(phone.isBlank() ? "Not set" : name + " • " + phone);
            setStatusColor(adminValue, phone.isBlank() ? "#C62828" : "#2E7D32");
            adminDetail.setText(joinDetails(
                email.isBlank() ? null : "Email: " + email,
                whatsapp.isBlank() ? null : "WhatsApp: " + whatsapp));

            if (AppSettings.smsConfigured()) {
                smsValue.setText("Configured");
                setStatusColor(smsValue, "#2E7D32");
            } else if (AppSettings.smsEnabled()) {
                smsValue.setText("Incomplete");
                setStatusColor(smsValue, "#EF6C00");
            } else {
                smsValue.setText("Disabled");
                setStatusColor(smsValue, "#607D8B");
            }
            String sender = AppSettings.smsSenderId().isBlank()
                ? AppSettings.smsFromNumber() : AppSettings.smsSenderId();
            String environment = "AFRICAS_TALKING".equalsIgnoreCase(AppSettings.smsProvider())
                ? " • " + AppSettings.smsEnvironment() : "";
            String keySummary = AppSettings.smsApiKeyRaw().isBlank()
                ? " • no key saved"
                : " • " + AppSettings.smsApiKeySummary();
            smsDetail.setText(providerLabel(AppSettings.smsProvider()) + environment
                + (sender.isBlank() ? "" : " • sender " + sender) + keySummary);

            if (AppSettings.whatsappConfigured()) {
                whatsappValue.setText("Configured");
                setStatusColor(whatsappValue, "#2E7D32");
            } else if (AppSettings.whatsappEnabled()) {
                whatsappValue.setText("Incomplete");
                setStatusColor(whatsappValue, "#EF6C00");
            } else {
                whatsappValue.setText("Disabled");
                setStatusColor(whatsappValue, "#607D8B");
            }
            whatsappDetail.setText(AppSettings.whatsappBusinessNumber().isBlank()
                ? "WhatsApp Cloud API"
                : AppSettings.whatsappBusinessNumber());

            if (AppSettings.cloudBackupConfigured()) {
                cloudValue.setText("Configured");
                setStatusColor(cloudValue, "#2E7D32");
            } else if (AppSettings.cloudBackupEnabled()) {
                cloudValue.setText("Incomplete");
                setStatusColor(cloudValue, "#EF6C00");
            } else {
                cloudValue.setText("Disabled");
                setStatusColor(cloudValue, "#607D8B");
            }
            cloudDetail.setText(AppSettings.cloudFolder().isBlank()
                ? "No cloud sync folder selected"
                : cloudProviderLabel(AppSettings.cloudProvider())
                    + (AppSettings.cloudAccount().isBlank()
                        ? "" : " • " + AppSettings.cloudAccount())
                    + " • " + AppSettings.cloudFolder());

            boolean autoSend = AppSettings.reminderAutoSendEnabled();
            automationValue.setText(autoSend ? "Reminders on" : "Reminders off");
            setStatusColor(automationValue, autoSend ? "#2E7D32" : "#607D8B");
            automationDetail.setText("Tenant timeline: "
                + (AppSettings.autoTrackEnabled() ? "on" : "off")
                + " • scans every " + AppSettings.reminderSchedulerMinutes()
                + " min while the app is open"
                + " • voice/voicemail needs a hosted relay");
        };
        refresh.run();

        Button setupBtn = new Button("📡 Open Setup Wizard");
        setupBtn.getStyleClass().add("btn-primary");
        setupBtn.setOnAction(e -> {
            IntegrationsSetupDialog.show(stage, this::refreshSettings);
            refresh.run();
        });

        Button remindersBtn = new Button("🔔 Open Reminders");
        remindersBtn.getStyleClass().add("btn-secondary");
        remindersBtn.setOnAction(e -> {
            if (onOpenReminders != null) {
                onOpenReminders.run();
            } else if (stage != null) {
                Alert info = new Alert(Alert.AlertType.INFORMATION,
                    "Open Reminders from the sidebar to queue messages and press Send Now.",
                    ButtonType.OK);
                info.setHeaderText(null);
                info.initOwner(stage);
                info.showAndWait();
            }
        });

        HBox actions = new HBox(12, setupBtn, remindersBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        Label note = new Label(
            "Credentials are stored locally in the app database, encrypted with Windows DPAPI " +
            "and shown only as a masked suffix. They are tied to this Windows user and PC, so a " +
            "backup restored elsewhere needs the keys again. SMS and WhatsApp both require an " +
            "internet connection. Delivery reports, replies and voice callbacks need a hosted " +
            "relay, which is not part of this desktop build.");
        note.getStyleClass().add("label-subtitle");
        note.setWrapText(true);

        box.getChildren().addAll(subtitle, status, actions, note);
        return box;
    }

    private void addStatusRow(GridPane grid, int row, String labelText,
                              Label value, Label detail) {
        Label label = new Label(labelText);
        label.setMinWidth(130);
        label.setStyle("-fx-font-weight: bold;");
        value.setStyle("-fx-font-weight: bold;");
        detail.getStyleClass().add("label-subtitle");
        detail.setWrapText(true);
        detail.setMaxWidth(520);
        grid.add(label, 0, row);
        grid.add(value, 1, row);
        grid.add(detail, 2, row);
    }

    private void setStatusColor(Label label, String color) {
        label.setStyle("-fx-font-weight: bold; -fx-text-fill: " + color + ";");
    }

    private String joinDetails(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                if (!sb.isEmpty()) sb.append(" • ");
                sb.append(part);
            }
        }
        return sb.toString();
    }

    private String providerLabel(String code) {
        return switch (code == null ? "" : code.toUpperCase(java.util.Locale.ROOT)) {
            case "TWILIO" -> "Twilio";
            case "GENERIC" -> "Generic HTTP gateway";
            default -> "Africa's Talking";
        };
    }

    private String cloudProviderLabel(String code) {
        return switch (code == null ? "" : code.toUpperCase(java.util.Locale.ROOT)) {
            case "GOOGLE_DRIVE" -> "Google Drive";
            case "DROPBOX" -> "Dropbox";
            case "OTHER" -> "Other cloud folder";
            default -> "OneDrive";
        };
    }

    /* ==================== SYSTEM SETTINGS TAB ==================== */

    private VBox buildSettingsTab() {
        VBox box = new VBox(15);
        box.setPadding(new Insets(20));
        box.setAlignment(Pos.TOP_LEFT);

        TableView<SystemSetting> table = new TableView<>();
        table.setItems(settings);
        table.setPrefHeight(300);

        TableColumn<SystemSetting, Long> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("settingId"));
        idCol.setPrefWidth(50);

        TableColumn<SystemSetting, String> keyCol = new TableColumn<>("Key");
        keyCol.setCellValueFactory(new PropertyValueFactory<>("settingKey"));
        keyCol.setPrefWidth(200);

        TableColumn<SystemSetting, String> valueCol = new TableColumn<>("Value");
        valueCol.setCellValueFactory(data -> {
            SystemSetting setting = data.getValue();
            if (SecretStore.isSecretKey(setting.getSettingKey())) {
                String raw = setting.getSettingValue();
                String display = raw == null || raw.isBlank()
                    ? "" : SecretStore.savedSummary(raw);
                return new SimpleStringProperty(display);
            }
            return new SimpleStringProperty(setting.getSettingValue());
        });
        valueCol.setPrefWidth(250);

        TableColumn<SystemSetting, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(new PropertyValueFactory<>("description"));
        descCol.setPrefWidth(350);

        table.getColumns().addAll(idCol, keyCol, valueCol, descCol);

        HBox actions = new HBox(12);
        actions.setAlignment(Pos.CENTER_LEFT);

        Button deleteBtn = new Button("🗑 Delete Setting");
        deleteBtn.getStyleClass().add("btn-danger");
        deleteBtn.setOnAction(e -> {
            SystemSetting selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { showAlert("Select a setting first"); return; }
            if (settingsDAO.delete(selected.getSettingKey())) {
                AuditService.log("SETTING_DELETE", "Deleted setting " + selected.getSettingKey());
                AppSettings.reload();
                refreshSettings();
                refreshAudit();
            }
        });

        actions.getChildren().add(deleteBtn);

        HBox form = new HBox(12);
        form.setAlignment(Pos.CENTER_LEFT);

        TextField keyField = new TextField();
        keyField.setPromptText("Setting key");
        keyField.setPrefWidth(180);

        TextField valueField = new TextField();
        valueField.setPromptText("Setting value");
        valueField.setPrefWidth(200);

        TextField descField = new TextField();
        descField.setPromptText("Description");
        descField.setPrefWidth(300);

        Button saveBtn = new Button("Save Setting");
        saveBtn.getStyleClass().add("btn-success");
        saveBtn.setOnAction(e -> {
            String key = keyField.getText().trim();
            if (key.isEmpty()) {
                showAlert("Setting key is required");
                return;
            }
            boolean saved = SecretStore.isSecretKey(key)
                ? settingsDAO.saveSecret(key, valueField.getText(), descField.getText())
                : settingsDAO.save(key, valueField.getText(), descField.getText());
            if (saved) {
                AuditService.log("SETTING_SAVE", "Saved setting " + key);
                AppSettings.reload();
                refreshSettings();
                refreshAudit();
                keyField.clear();
                valueField.clear();
                descField.clear();
            } else if (SecretStore.isSecretKey(key)) {
                showAlert("The credential could not be encrypted with Windows DPAPI. "
                    + "Check the console for details.");
            }
        });

        form.getChildren().addAll(keyField, valueField, descField, saveBtn);

        box.getChildren().addAll(table, actions, form);
        return box;
    }

    /* ==================== PAYMENT METHODS TAB ==================== */

    private VBox buildPaymentMethodsTab() {
        VBox box = new VBox(15);
        box.setPadding(new Insets(20));
        box.setAlignment(Pos.TOP_LEFT);

        Label subtitle = new Label("Payment methods offered to tenants. Enable or disable them here.");
        subtitle.getStyleClass().add("label-subtitle");

        TableView<PaymentMethod> table = new TableView<>();
        table.setItems(methods);
        table.setPrefHeight(300);

        TableColumn<PaymentMethod, Long> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("methodId"));
        idCol.setPrefWidth(50);

        TableColumn<PaymentMethod, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(180);

        TableColumn<PaymentMethod, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(new PropertyValueFactory<>("type"));
        typeCol.setPrefWidth(140);

        TableColumn<PaymentMethod, String> accountCol = new TableColumn<>("Account Number");
        accountCol.setCellValueFactory(new PropertyValueFactory<>("accountNumber"));
        accountCol.setPrefWidth(160);

        TableColumn<PaymentMethod, String> instructionsCol = new TableColumn<>("Instructions");
        instructionsCol.setCellValueFactory(new PropertyValueFactory<>("instructions"));
        instructionsCol.setPrefWidth(300);

        TableColumn<PaymentMethod, Boolean> enabledCol = new TableColumn<>("Enabled");
        enabledCol.setCellValueFactory(new PropertyValueFactory<>("enabled"));
        enabledCol.setPrefWidth(90);

        table.getColumns().addAll(idCol, nameCol, typeCol, accountCol, instructionsCol, enabledCol);

        HBox actions = new HBox(12);
        actions.setAlignment(Pos.CENTER_LEFT);

        Button toggleBtn = new Button("Enable / Disable");
        toggleBtn.getStyleClass().add("btn-secondary");
        toggleBtn.setOnAction(e -> {
            PaymentMethod selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { showAlert("Select a payment method first"); return; }
            if (paymentMethodDAO.setEnabled(selected.getMethodId(), !selected.isEnabled())) {
                AuditService.log("PAYMENT_METHOD_TOGGLE",
                    (selected.isEnabled() ? "Disabled " : "Enabled ") + selected.getName());
                refreshMethods();
                refreshAudit();
            }
        });

        actions.getChildren().add(toggleBtn);

        box.getChildren().addAll(subtitle, table, actions);
        return box;
    }

    /* ==================== ACTIVITY LOG TAB ==================== */

    private VBox buildActivityTab() {
        VBox box = new VBox(15);
        box.setPadding(new Insets(20));
        box.setAlignment(Pos.TOP_LEFT);

        Label subtitle = new Label("The last 100 actions performed in the system.");
        subtitle.getStyleClass().add("label-subtitle");

        TableView<AuditLog> table = new TableView<>();
        table.setItems(auditLogs);
        table.setPrefHeight(380);

        TableColumn<AuditLog, Long> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("logId"));
        idCol.setPrefWidth(50);

        TableColumn<AuditLog, String> userCol = new TableColumn<>("User");
        userCol.setCellValueFactory(new PropertyValueFactory<>("username"));
        userCol.setPrefWidth(130);

        TableColumn<AuditLog, String> actionCol = new TableColumn<>("Action");
        actionCol.setCellValueFactory(new PropertyValueFactory<>("action"));
        actionCol.setPrefWidth(170);

        TableColumn<AuditLog, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(new PropertyValueFactory<>("description"));
        descCol.setPrefWidth(400);

        TableColumn<AuditLog, String> dateCol = new TableColumn<>("When");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
        dateCol.setPrefWidth(160);

        table.getColumns().addAll(idCol, userCol, actionCol, descCol, dateCol);

        Button refreshBtn = new Button("🔄 Refresh");
        refreshBtn.getStyleClass().add("btn-secondary");
        refreshBtn.setOnAction(e -> refreshAudit());

        box.getChildren().addAll(subtitle, table, refreshBtn);
        return box;
    }

    /* ==================== BACKUP TAB ==================== */

    private VBox buildBackupTab() {
        VBox box = new VBox(15);
        box.setPadding(new Insets(20));
        box.setAlignment(Pos.TOP_LEFT);

        Label subtitle = new Label(
            "Backups are consistent snapshots taken while the app is running.\n" +
            "Each .zip contains the database, all tenant documents, a readable info file and a SHA-256 checksum.\n" +
            "They are stored in: " + AppPaths.backupsDir());
        subtitle.getStyleClass().add("label-subtitle");
        subtitle.setWrapText(true);

        Label cloudInfo = new Label(cloudSummary());
        cloudInfo.getStyleClass().add("label-subtitle");
        cloudInfo.setWrapText(true);

        TableView<BackupService.BackupInfo> table = new TableView<>();
        table.setItems(backups);
        table.setPrefHeight(260);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<BackupService.BackupInfo, String> nameCol = new TableColumn<>("Backup file");
        nameCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().name()));

        TableColumn<BackupService.BackupInfo, String> sizeCol = new TableColumn<>("Size");
        sizeCol.setCellValueFactory(d -> new SimpleStringProperty(
            String.format("%.2f MB", d.getValue().sizeMb())));

        TableColumn<BackupService.BackupInfo, String> modifiedCol = new TableColumn<>("Created");
        modifiedCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().modified()));

        table.getColumns().addAll(nameCol, sizeCol, modifiedCol);

        Label statusLabel = new Label();
        statusLabel.getStyleClass().add("label-subtitle");

        Button backupBtn = new Button("💾 Backup Now");
        backupBtn.getStyleClass().add("btn-primary");

        Button restoreInfoBtn = new Button("📂 Open Backups Folder");
        restoreInfoBtn.getStyleClass().add("btn-secondary");
        restoreInfoBtn.setOnAction(e -> FormatUtils.open(AppPaths.backupsDir()));

        Button verifyBtn = new Button("✔ Verify Checksum");
        verifyBtn.getStyleClass().add("btn-secondary");
        verifyBtn.setOnAction(e -> {
            BackupService.BackupInfo selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                showAlert("Select a backup to verify.");
                return;
            }
            try {
                boolean ok = BackupService.verify(selected.path());
                if (ok) {
                    AuditService.log("BACKUP_VERIFY", "Verified backup " + selected.name());
                    refreshAudit();
                    showInfo("Backup is intact; the checksum matches.");
                } else {
                    showAlert("Checksum missing or does not match. This backup may be corrupted.");
                }
            } catch (Exception ex) {
                showAlert("Could not verify backup: " + ex.getMessage());
            }
        });

        Button refreshBtn = new Button("🔄 Refresh");
        refreshBtn.getStyleClass().add("btn-secondary");
        refreshBtn.setOnAction(e -> {
            refreshBackups();
            cloudInfo.setText(cloudSummary());
        });

        Button uploadCloudBtn = new Button("☁ Upload Latest");
        uploadCloudBtn.getStyleClass().add("btn-secondary");
        uploadCloudBtn.setOnAction(e -> {
            BackupService.BackupInfo selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                showAlert("Select a backup to upload.");
                return;
            }
            if (AppSettings.cloudFolder().isBlank()) {
                showAlert("No cloud folder is configured. Open the Communication tab and run Setup.");
                return;
            }
            uploadCloudBtn.setDisable(true);
            statusLabel.setText("Uploading " + selected.name() + " to the cloud folder...");
            Task<CloudBackupService.Result> task = new Task<>() {
                @Override
                protected CloudBackupService.Result call() {
                    return CloudBackupService.upload(selected.path());
                }
            };
            task.setOnSucceeded(event -> {
                uploadCloudBtn.setDisable(false);
                CloudBackupService.Result result = task.getValue();
                if (result.success()) {
                    AuditService.log("CLOUD_BACKUP",
                        "Uploaded " + selected.name() + " to the cloud folder");
                    refreshAudit();
                    statusLabel.setText("Uploaded to cloud: " + selected.name());
                    cloudInfo.setText(cloudSummary());
                    showInfo(result.message());
                } else {
                    AuditService.log("CLOUD_BACKUP_FAIL",
                        "Cloud upload failed for " + selected.name() + ": " + result.message());
                    refreshAudit();
                    statusLabel.setText("Cloud upload failed.");
                    showAlert(result.message());
                }
            });
            task.setOnFailed(event -> {
                uploadCloudBtn.setDisable(false);
                Throwable error = task.getException();
                statusLabel.setText("Cloud upload failed.");
                showAlert("Cloud upload failed: "
                    + (error == null ? "unknown error" : error.getMessage()));
            });
            new Thread(task, "cloud-upload").start();
        });

        Button openCloudBtn = new Button("☁ Open Cloud Folder");
        openCloudBtn.getStyleClass().add("btn-secondary");
        openCloudBtn.setOnAction(e -> {
            String folder = AppSettings.cloudFolder();
            if (folder.isBlank()) {
                showAlert("No cloud folder is configured. Open the Communication tab and run Setup.");
                return;
            }
            FormatUtils.open(Path.of(folder));
        });

        Button testCloudBtn = new Button("✔ Test Cloud Folder");
        testCloudBtn.getStyleClass().add("btn-secondary");
        testCloudBtn.setOnAction(e -> {
            String folder = AppSettings.cloudFolder();
            if (folder.isBlank()) {
                showAlert("No cloud folder is configured. Open the Communication tab and run Setup.");
                return;
            }
            CloudBackupService.Result result = CloudBackupService.testFolder(Path.of(folder));
            if (result.success()) {
                AuditService.log("CLOUD_TEST", "Cloud backup folder is writable: " + folder);
                refreshAudit();
                showInfo(result.message());
            } else {
                showAlert(result.message());
            }
        });

        backupBtn.setOnAction(e -> {
            backupBtn.setDisable(true);
            statusLabel.setText("Creating backup...");

            Task<BackupService.BackupOutcome> task = new Task<>() {
                @Override
                protected BackupService.BackupOutcome call() throws Exception {
                    return BackupService.createBackupAndUpload();
                }
            };
            task.setOnSucceeded(event -> {
                backupBtn.setDisable(false);
                refreshBackups();
                BackupService.BackupOutcome outcome = task.getValue();
                AuditService.log("BACKUP", "Created backup " + outcome.local().getFileName());
                refreshAudit();
                statusLabel.setText("Last backup: " + outcome.local().getFileName());

                if (outcome.cloudAttempted()) {
                    if (outcome.cloudOk()) {
                        AuditService.log("CLOUD_BACKUP",
                            "Uploaded " + outcome.local().getFileName() + " to the cloud folder");
                        refreshAudit();
                        statusLabel.setText("Last backup: " + outcome.local().getFileName()
                            + " (cloud synced)");
                        showInfo("Backup created locally and copied to the cloud folder.\n\n"
                            + outcome.local().toAbsolutePath() + "\n\n" + outcome.cloudMessage());
                    } else {
                        AuditService.log("CLOUD_BACKUP_FAIL",
                            "Cloud upload failed: " + outcome.cloudMessage());
                        refreshAudit();
                        statusLabel.setText("Local backup created; cloud upload failed.");
                        showAlert("The local backup was created, but the cloud copy failed:\n\n"
                            + outcome.cloudMessage() + "\n\nLocal file:\n"
                            + outcome.local().toAbsolutePath());
                    }
                } else {
                    showInfo("Backup created:\n" + outcome.local().toAbsolutePath()
                        + "\n\nIt includes the database, tenant documents and a checksum."
                        + (outcome.cloudMessage().isBlank()
                            ? "" : "\n\n" + outcome.cloudMessage()));
                }
                cloudInfo.setText(cloudSummary());
            });
            task.setOnFailed(event -> {
                backupBtn.setDisable(false);
                Throwable error = task.getException();
                statusLabel.setText("Backup failed.");
                showAlert("Backup failed: " + (error == null ? "unknown error" : error.getMessage()));
            });
            new Thread(task, "backup-task").start();
        });

        HBox actions = new HBox(12, backupBtn, verifyBtn, restoreInfoBtn, refreshBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        HBox cloudActions = new HBox(12, uploadCloudBtn, openCloudBtn, testCloudBtn);
        cloudActions.setAlignment(Pos.CENTER_LEFT);

        Label retention = new Label(
            "Keeping the newest " + AppSettings.backupRetention() +
            " backups locally and in the cloud folder. Change this on the Billing & Company tab.");
        retention.getStyleClass().add("label-subtitle");

        box.getChildren().addAll(subtitle, cloudInfo, table, actions, cloudActions,
            statusLabel, retention);
        return box;
    }

    /* ==================== REFRESH HELPERS ==================== */

    private void refreshSettings() {
        settings.setAll(settingsDAO.getAll());
    }

    private void refreshMethods() {
        methods.setAll(paymentMethodDAO.getAll());
    }

    private void refreshAudit() {
        auditLogs.setAll(auditDAO.getRecent(100));
    }

    private void refreshBackups() {
        backups.setAll(BackupService.listBackups());
    }

    private String cloudSummary() {
        if (!AppSettings.cloudBackupEnabled()) {
            return "Cloud backup: disabled. Open the Communication tab to connect OneDrive, " +
                "Google Drive, Dropbox or another synced folder.";
        }
        if (AppSettings.cloudFolder().isBlank()) {
            return "Cloud backup: enabled but no folder is selected. Open the Communication tab.";
        }
        return "Cloud backup: " + cloudProviderLabel(AppSettings.cloudProvider())
            + (AppSettings.cloudAccount().isBlank() ? "" : " • " + AppSettings.cloudAccount())
            + " • " + AppSettings.cloudFolder()
            + (AppSettings.cloudAutoUpload() ? " • auto-upload on" : " • manual upload");
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
