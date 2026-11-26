package com.rentals.ui.settings;

import com.rentals.dao.SettingsDAO;
import com.rentals.service.AuditService;
import com.rentals.service.CloudBackupService;
import com.rentals.service.MessagingService;
import com.rentals.service.ThemeManager;
import com.rentals.settings.AppSettings;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * Guided setup for the admin contact, SMS gateway, WhatsApp Cloud API and
 * cloud backup folder.
 *
 * <p>The wizard is shown automatically after login until the admin completes
 * it, and can be reopened from Settings > Communication at any time.</p>
 */
public final class IntegrationsSetupDialog {

    private static final List<String> CLOUD_LABELS = List.of(
        "OneDrive", "Google Drive", "Dropbox", "Other");

    private final Window owner;
    private final Runnable onSaved;
    private final SettingsDAO settingsDAO = new SettingsDAO();

    private final TextField adminNameField = new TextField(AppSettings.adminName());
    private final TextField adminPhoneField = new TextField(AppSettings.adminPhone());
    private final TextField adminEmailField = new TextField(AppSettings.adminEmail());
    private final TextField adminWhatsappField = new TextField(AppSettings.adminWhatsapp());

    private final CheckBox smsEnabledBox = new CheckBox("Enable SMS delivery");
    private final ComboBox<MessagingService.SmsProvider> smsProviderBox = new ComboBox<>();
    private final ComboBox<MessagingService.Environment> smsEnvironmentBox = new ComboBox<>();
    private final TextField smsUsernameField = new TextField(AppSettings.smsUsername());
    private final PasswordField smsApiKeyField = new PasswordField();
    private final TextField smsSenderField = new TextField(AppSettings.smsSenderId());
    private final TextField smsFromField = new TextField(AppSettings.smsFromNumber());
    private final TextField smsUrlField = new TextField(AppSettings.smsGenericUrl());
    private final TextField smsCountryCodeField = new TextField(AppSettings.smsCountryCode());
    private final Label smsTestLabel = new Label();
    private final Label smsConnectionLabel = new Label();

    private final CheckBox whatsappEnabledBox = new CheckBox("Enable WhatsApp delivery");
    private final TextField whatsappNumberField = new TextField(AppSettings.whatsappBusinessNumber());
    private final TextField whatsappPhoneIdField = new TextField(AppSettings.whatsappPhoneNumberId());
    private final PasswordField whatsappTokenField = new PasswordField();
    private final TextField whatsappTemplateField = new TextField(AppSettings.whatsappTemplateName());
    private final TextField whatsappLanguageField = new TextField(AppSettings.whatsappTemplateLanguage());
    private final TextField whatsappUrlField = new TextField(AppSettings.whatsappApiUrl());
    private final Label whatsappTestLabel = new Label();

    private final CheckBox autoTrackBox =
        new CheckBox("Track every outbound message in the tenant communication history");
    private final CheckBox autoSendBox =
        new CheckBox("Send due reminders automatically while this app is open");

    private final CheckBox cloudEnabledBox = new CheckBox("Copy backups to my cloud folder");
    private final ComboBox<String> cloudProviderBox = new ComboBox<>();
    private final TextField cloudAccountField = new TextField(AppSettings.cloudAccount());
    private final TextField cloudFolderField = new TextField(AppSettings.cloudFolder());
    private final CheckBox cloudAutoUploadBox = new CheckBox("Upload each new backup automatically");
    private final Label cloudHintLabel = new Label();
    private final Label cloudTestLabel = new Label();

    private IntegrationsSetupDialog(Window owner, Runnable onSaved) {
        this.owner = owner;
        this.onSaved = onSaved;
    }

    /** Opens the wizard when the admin has not completed communication setup. */
    public static void showIfNeeded(Window owner) {
        if (!AppSettings.integrationSetupComplete()) show(owner, null);
    }

    public static void show(Window owner) {
        show(owner, null);
    }

    public static void show(Window owner, Runnable onSaved) {
        new IntegrationsSetupDialog(owner, onSaved).open();
    }

    private void open() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Communication & Cloud Backup Setup");
        dialog.setHeaderText("Connect SMS, WhatsApp and cloud backups for this system.");
        dialog.initOwner(owner);
        dialog.setResizable(true);

        ButtonType saveType = new ButtonType("Save & Finish", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, cancelType);

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
            new Tab("Admin Contact", scroll(buildAdminTab())),
            new Tab("SMS", scroll(buildSmsTab())),
            new Tab("WhatsApp", scroll(buildWhatsAppTab())),
            new Tab("Cloud Backup", scroll(buildCloudTab()))
        );
        tabs.setPrefHeight(400);

        Label footnote = new Label(
            "SMS and WhatsApp need an internet connection. API keys/tokens are encrypted with " +
            "Windows DPAPI, which ties them to this Windows user on this PC. A database restored " +
            "on another PC will ask for the keys again. Use app-specific tokens and protect " +
            "this PC and its backups.");
        footnote.getStyleClass().add("label-subtitle");
        footnote.setWrapText(true);

        VBox content = new VBox(12, tabs, footnote);
        content.setPadding(new Insets(4, 4, 0, 4));
        content.setPrefWidth(760);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(800);
        dialog.setOnShown(e -> ThemeManager.applyTo(dialog.getDialogPane().getScene()));

        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveType);
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            String error = validateInputs();
            if (error != null) {
                alert(Alert.AlertType.WARNING, error);
                event.consume();
                return;
            }
            try {
                saveSettings();
                AppSettings.reload();
                AuditService.log("INTEGRATIONS_SAVE",
                    "Updated admin contact, SMS, WhatsApp and cloud backup settings");
                if (onSaved != null) onSaved.run();
            } catch (Exception ex) {
                alert(Alert.AlertType.ERROR, "Could not save settings: " + ex.getMessage());
                event.consume();
            }
        });

        dialog.showAndWait();
    }

    /* ==================== TABS ==================== */

    private VBox buildAdminTab() {
        VBox box = tabBox();

        Label intro = subtitle(
            "This is the administrator's own contact. It is used for setup tests and system " +
            "alerts. Tenant phone numbers are used when sending rent reminders.");
        box.getChildren().add(intro);

        GridPane grid = grid();
        int row = 0;
        addRow(grid, row++, "Admin name", adminNameField,
            "The person responsible for this system");
        addRow(grid, row++, "Admin phone", adminPhoneField,
            "Required. Used for SMS tests and urgent system alerts (e.g. 0700123456)");
        addRow(grid, row++, "Admin email", adminEmailField,
            "Optional contact email for your records");
        addRow(grid, row++, "Admin WhatsApp", adminWhatsappField,
            "Optional. Used for WhatsApp tests if it differs from the phone");
        addRow(grid, row++, "Default country code", smsCountryCodeField,
            "Used to turn local numbers like 0700... into +256700...");
        box.getChildren().add(grid);

        Label note = subtitle(
            "Enter the number in international format where possible. The country code is " +
            "used when a number starts with 0.");
        box.getChildren().add(note);
        return box;
    }

    private VBox buildSmsTab() {
        VBox box = tabBox();
        box.getChildren().add(subtitle(
            "Choose the SMS gateway you have an account with. The sender ID or From number is " +
            "what tenants see; the admin phone above is the test recipient."));

        smsEnabledBox.setSelected(AppSettings.smsEnabled());
        box.getChildren().add(smsEnabledBox);

        smsProviderBox.getItems().setAll(MessagingService.SmsProvider.values());
        smsProviderBox.setValue(MessagingService.SmsProvider.from(AppSettings.smsProvider()));
        smsProviderBox.setPrefWidth(240);
        smsProviderBox.valueProperty().addListener((obs, old, value) -> updateSmsFields());
        box.getChildren().add(providerRow("SMS provider", smsProviderBox));

        smsEnvironmentBox.getItems().setAll(MessagingService.Environment.values());
        smsEnvironmentBox.setValue(
            MessagingService.Environment.from(AppSettings.smsEnvironment()));
        smsEnvironmentBox.setPrefWidth(180);
        box.getChildren().add(providerRow("Environment", smsEnvironmentBox));

        smsApiKeyField.setPromptText("Leave blank to keep the saved key");

        GridPane grid = grid();
        int row = 0;
        addRow(grid, row++, "Username / Account SID", smsUsernameField,
            "Africa's Talking username, or Twilio Account SID");
        addRow(grid, row++, "API key / Auth token", smsApiKeyField,
            savedKeyHint(AppSettings.smsApiKeyRaw()));
        addRow(grid, row++, "Sender ID", smsSenderField,
            "Africa's Talking sender ID (optional but recommended)");
        addRow(grid, row++, "From number", smsFromField,
            "Twilio sender number in +countrycode format");
        addRow(grid, row++, "Generic gateway URL", smsUrlField,
            "Receives JSON {to,message,sender}, or a URL containing {to} and {message}");
        box.getChildren().add(grid);

        Label hint = subtitle(
            "Africa's Talking: username + API key (+ sender ID). The sandbox only delivers to " +
            "numbers added to your sandbox app; production needs an approved sender ID. Twilio: " +
            "Account SID + Auth token + From number. Generic: a URL; the API key is sent as a " +
            "Bearer token. Saved keys are encrypted with Windows DPAPI and never shown again.");
        hint.setWrapText(true);
        box.getChildren().add(hint);

        Button testBtn = new Button("📨 Send Test SMS");
        testBtn.getStyleClass().add("btn-secondary");
        testBtn.setOnAction(e -> testSms(testBtn));

        Button connectionBtn = new Button("🔌 Test Connection");
        connectionBtn.getStyleClass().add("btn-secondary");
        connectionBtn.setOnAction(e -> testSmsConnection(connectionBtn));

        smsTestLabel.getStyleClass().add("label-subtitle");
        smsTestLabel.setWrapText(true);
        smsConnectionLabel.getStyleClass().add("label-subtitle");
        smsConnectionLabel.setWrapText(true);
        box.getChildren().add(new VBox(8,
            new HBox(8, testBtn, connectionBtn), smsTestLabel, smsConnectionLabel));

        Label automationTitle = new Label("Automation");
        automationTitle.getStyleClass().add("label-section");
        autoTrackBox.setSelected(AppSettings.autoTrackEnabled());
        autoSendBox.setSelected(AppSettings.reminderAutoSendEnabled());
        Label automationNote = subtitle(
            "Automatic reminders only run while this desktop app is open (checked every "
            + AppSettings.reminderSchedulerMinutes() + " minutes). Delivery reports, replies and "
            + "voice callbacks need a hosted relay and are not active yet.");
        automationNote.setWrapText(true);
        box.getChildren().addAll(automationTitle, autoTrackBox, autoSendBox, automationNote);

        updateSmsFields();
        return box;
    }

    private VBox buildWhatsAppTab() {
        VBox box = tabBox();
        box.getChildren().add(subtitle(
            "WhatsApp uses Meta's WhatsApp Cloud API. You need a Meta business account, a " +
            "phone number ID and an access token. The PC must be online to send."));

        whatsappEnabledBox.setSelected(AppSettings.whatsappEnabled());
        box.getChildren().add(whatsappEnabledBox);

        whatsappTokenField.setPromptText("Leave blank to keep the saved token");

        GridPane grid = grid();
        int row = 0;
        addRow(grid, row++, "Business display number", whatsappNumberField,
            "The WhatsApp number tenants will recognise (for your reference)");
        addRow(grid, row++, "Phone number ID", whatsappPhoneIdField,
            "Meta WhatsApp Cloud API phone number ID");
        addRow(grid, row++, "Access token", whatsappTokenField,
            savedKeyHint(AppSettings.whatsappAccessTokenRaw()));
        addRow(grid, row++, "Template name", whatsappTemplateField,
            "Optional. Needed outside Meta's 24-hour customer service window");
        addRow(grid, row++, "Template language", whatsappLanguageField,
            "Example: en_US");
        addRow(grid, row++, "API base URL", whatsappUrlField,
            "Advanced. Default: https://graph.facebook.com/v20.0");
        box.getChildren().add(grid);

        Label hint = subtitle(
            "If a template is set, it must be approved by Meta and contain one body variable " +
            "({{1}}); RentalSys fills it with the reminder text. Without a template, plain " +
            "text only works within 24 hours of the tenant's last message.");
        hint.setWrapText(true);
        box.getChildren().add(hint);

        Button testBtn = new Button("🟢 Send Test WhatsApp");
        testBtn.getStyleClass().add("btn-secondary");
        testBtn.setOnAction(e -> testWhatsApp(testBtn));
        whatsappTestLabel.getStyleClass().add("label-subtitle");
        whatsappTestLabel.setWrapText(true);
        box.getChildren().add(new VBox(8, testBtn, whatsappTestLabel));
        return box;
    }

    private VBox buildCloudTab() {
        VBox box = tabBox();
        box.getChildren().add(subtitle(
            "RentalSys does not ask for your cloud password. Sign in once to OneDrive, " +
            "Google Drive or Dropbox on this PC, then choose the folder it syncs. Every backup " +
            "zip and checksum is copied there automatically."));

        cloudEnabledBox.setSelected(AppSettings.cloudBackupEnabled());
        box.getChildren().add(cloudEnabledBox);

        cloudProviderBox.getItems().setAll(CLOUD_LABELS);
        cloudProviderBox.setValue(cloudLabel(AppSettings.cloudProvider()));
        cloudProviderBox.setPrefWidth(200);
        cloudProviderBox.valueProperty().addListener((obs, old, value) -> updateCloudHint());
        box.getChildren().add(providerRow("Cloud provider", cloudProviderBox));

        GridPane grid = grid();
        int row = 0;
        addRow(grid, row++, "Cloud account", cloudAccountField,
            "Your cloud email or a label, e.g. admin@example.com (for your reference only)");

        HBox folderBox = new HBox(8, cloudFolderField);
        HBox.setHgrow(cloudFolderField, Priority.ALWAYS);
        Button browseBtn = new Button("Browse…");
        browseBtn.getStyleClass().add("btn-secondary");
        browseBtn.setMinWidth(Region.USE_PREF_SIZE);
        browseBtn.setOnAction(e -> browseCloudFolder());
        Button detectBtn = new Button("Detect");
        detectBtn.getStyleClass().add("btn-secondary");
        detectBtn.setMinWidth(Region.USE_PREF_SIZE);
        detectBtn.setOnAction(e -> detectCloudFolder());
        folderBox.getChildren().addAll(browseBtn, detectBtn);
        addFolderRow(grid, row++, "Cloud sync folder", folderBox,
            "The local folder your cloud client syncs to the cloud");
        box.getChildren().add(grid);

        cloudAutoUploadBox.setSelected(AppSettings.cloudAutoUpload());
        box.getChildren().add(cloudAutoUploadBox);

        cloudHintLabel.getStyleClass().add("label-subtitle");
        cloudHintLabel.setWrapText(true);
        box.getChildren().add(cloudHintLabel);

        Button testBtn = new Button("✔ Test Folder");
        testBtn.getStyleClass().add("btn-secondary");
        testBtn.setOnAction(e -> testCloudFolder());
        cloudTestLabel.getStyleClass().add("label-subtitle");
        cloudTestLabel.setWrapText(true);
        box.getChildren().add(new VBox(8, testBtn, cloudTestLabel));

        Label retention = subtitle(
            "Cloud copies use the same retention setting as local backups (currently " +
            AppSettings.backupRetention() + "). Change it under Billing & Company.");
        box.getChildren().add(retention);

        updateCloudHint();
        return box;
    }

    /* ==================== ACTIONS ==================== */

    private void testSms(Button button) {
        if (adminPhoneField.getText().isBlank()) {
            alert(Alert.AlertType.WARNING,
                "Enter the admin phone number on the Admin Contact tab first.");
            return;
        }
        MessagingService.SmsConfig config = buildSmsConfig(true);
        if (!config.isConfigured()) {
            alert(Alert.AlertType.WARNING,
                "Fill in the SMS provider details before testing.");
            return;
        }

        String recipient = adminPhoneField.getText().trim();
        String message = "Rental Management System: your SMS setup works.";

        button.setDisable(true);
        smsTestLabel.setText("Sending test SMS…");
        Task<MessagingService.Result> task = new Task<>() {
            @Override
            protected MessagingService.Result call() {
                return MessagingService.sendSms(config, recipient, message);
            }
        };
        task.setOnSucceeded(e -> {
            button.setDisable(false);
            MessagingService.Result result = task.getValue();
            com.rentals.service.NotificationService.recordTestMessage(
                "SMS", recipient, message, result);
            smsTestLabel.setText((result.success() ? "✔ " : "✖ ") + result.detail());
            if (!result.success()) {
                alert(Alert.AlertType.ERROR, "Test SMS failed:\n" + result.detail());
            }
        });
        task.setOnFailed(e -> {
            button.setDisable(false);
            Throwable error = task.getException();
            smsTestLabel.setText("✖ " + (error == null ? "unknown error" : error.getMessage()));
            alert(Alert.AlertType.ERROR, "Test SMS failed: "
                + (error == null ? "unknown error" : error.getMessage()));
        });
        new Thread(task, "sms-test").start();
    }

    private void testSmsConnection(Button button) {
        MessagingService.SmsConfig config = buildSmsConfig(true);
        if (!config.isConfigured()) {
            alert(Alert.AlertType.WARNING,
                "Fill in the SMS provider details before testing the connection.");
            return;
        }

        button.setDisable(true);
        smsConnectionLabel.setText("Checking provider credentials…");
        Task<MessagingService.TestResult> task = new Task<>() {
            @Override
            protected MessagingService.TestResult call() {
                return MessagingService.testConnection(config);
            }
        };
        task.setOnSucceeded(e -> {
            button.setDisable(false);
            MessagingService.TestResult result = task.getValue();
            smsConnectionLabel.setText((result.success() ? "✔ " : "✖ ") + result.detail());
            if (!result.success()) {
                alert(Alert.AlertType.ERROR, "Connection test failed:\n" + result.detail());
            }
        });
        task.setOnFailed(e -> {
            button.setDisable(false);
            Throwable error = task.getException();
            smsConnectionLabel.setText("✖ "
                + (error == null ? "unknown error" : error.getMessage()));
            alert(Alert.AlertType.ERROR, "Connection test failed: "
                + (error == null ? "unknown error" : error.getMessage()));
        });
        new Thread(task, "sms-connection-test").start();
    }

    private void testWhatsApp(Button button) {
        String recipient = adminWhatsappField.getText().isBlank()
            ? adminPhoneField.getText() : adminWhatsappField.getText();
        if (recipient.isBlank()) {
            alert(Alert.AlertType.WARNING,
                "Enter the admin WhatsApp or phone number on the Admin Contact tab first.");
            return;
        }
        MessagingService.WhatsAppConfig config = buildWhatsAppConfig(true);
        if (!config.isConfigured()) {
            alert(Alert.AlertType.WARNING,
                "Fill in the WhatsApp phone number ID and access token before testing.");
            return;
        }

        String message = "Rental Management System: your WhatsApp setup works.";
        button.setDisable(true);
        whatsappTestLabel.setText("Sending test WhatsApp message…");
        Task<MessagingService.Result> task = new Task<>() {
            @Override
            protected MessagingService.Result call() {
                return MessagingService.sendWhatsApp(config, recipient, message);
            }
        };
        task.setOnSucceeded(e -> {
            button.setDisable(false);
            MessagingService.Result result = task.getValue();
            com.rentals.service.NotificationService.recordTestMessage(
                "WHATSAPP", recipient, message, result);
            whatsappTestLabel.setText((result.success() ? "✔ " : "✖ ") + result.detail());
            if (!result.success()) {
                alert(Alert.AlertType.ERROR, "Test WhatsApp failed:\n" + result.detail());
            }
        });
        task.setOnFailed(e -> {
            button.setDisable(false);
            Throwable error = task.getException();
            whatsappTestLabel.setText("✖ " + (error == null ? "unknown error" : error.getMessage()));
            alert(Alert.AlertType.ERROR, "Test WhatsApp failed: "
                + (error == null ? "unknown error" : error.getMessage()));
        });
        new Thread(task, "whatsapp-test").start();
    }

    private void testCloudFolder() {
        String text = cloudFolderField.getText().trim();
        if (text.isBlank()) {
            alert(Alert.AlertType.WARNING, "Choose the cloud sync folder first.");
            return;
        }
        CloudBackupService.Result result = CloudBackupService.testFolder(Path.of(text));
        cloudTestLabel.setText((result.success() ? "✔ " : "✖ ") + result.message());
        if (!result.success()) {
            alert(Alert.AlertType.ERROR, result.message());
        }
    }

    private void browseCloudFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose your cloud sync folder");

        String current = cloudFolderField.getText().trim();
        if (!current.isBlank()) {
            File folder = new File(current);
            if (folder.isDirectory()) chooser.setInitialDirectory(folder);
        }

        File chosen = chooser.showDialog(owner);
        if (chosen != null) {
            cloudFolderField.setText(chosen.getAbsolutePath());
            cloudTestLabel.setText("");
        }
    }

    private void detectCloudFolder() {
        String code = cloudCode(cloudProviderBox.getValue());
        String suggestion = CloudBackupService.suggestFolder(code);
        if (suggestion.isBlank()) {
            alert(Alert.AlertType.INFORMATION,
                "No signed-in cloud folder was found automatically.\n\n" +
                "Open the cloud provider's desktop app and sign in, or use Browse to choose " +
                "the folder manually.");
            return;
        }
        cloudFolderField.setText(suggestion);
        cloudTestLabel.setText("Detected: " + suggestion);
    }

    /* ==================== SAVE ==================== */

    private String validateInputs() {
        String adminPhone = adminPhoneField.getText().replaceAll("[^0-9]", "");
        if (adminPhone.length() < 7) {
            return "Enter a valid admin phone number on the Admin Contact tab.";
        }

        if (smsEnabledBox.isSelected()) {
            MessagingService.SmsConfig config = buildSmsConfig(true);
            if (!config.isConfigured()) {
                return "SMS is enabled but its credentials are incomplete.\n" +
                    "Fill in the provider details on the SMS tab, or untick Enable SMS delivery.";
            }
            String url = smsUrlField.getText().trim();
            if (config.provider() == MessagingService.SmsProvider.GENERIC
                && !url.startsWith("http://") && !url.startsWith("https://")) {
                return "The generic SMS gateway URL must start with http:// or https://.";
            }
        }

        if (whatsappEnabledBox.isSelected()) {
            if (whatsappPhoneIdField.getText().isBlank()
                || effectiveWhatsappToken().isBlank()) {
                return "WhatsApp is enabled but the phone number ID or access token is missing.\n" +
                    "Fill in the WhatsApp tab, or untick Enable WhatsApp delivery.";
            }
            String url = whatsappUrlField.getText().trim();
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                return "The WhatsApp API base URL must start with http:// or https://.";
            }
        }

        boolean smsReady = smsEnabledBox.isSelected() && buildSmsConfig(true).isConfigured();
        boolean whatsappReady = whatsappEnabledBox.isSelected()
            && buildWhatsAppConfig(true).isConfigured();
        if (autoSendBox.isSelected() && !smsReady && !whatsappReady) {
            return "Automatic reminders are enabled, but neither SMS nor WhatsApp is fully " +
                "configured. Complete one channel or untick automatic sending.";
        }

        if (cloudEnabledBox.isSelected()) {
            String folder = cloudFolderField.getText().trim();
            if (folder.isBlank()) {
                return "Cloud backup is enabled but no sync folder is selected.";
            }
            CloudBackupService.Result result = CloudBackupService.testFolder(Path.of(folder));
            if (!result.success()) {
                return "Cloud folder check failed:\n" + result.message();
            }
        }

        return null;
    }

    private void saveSettings() {
        save("admin_name", adminNameField.getText(),
            "Administrator name used for alerts and setup");
        save("admin_phone", adminPhoneField.getText(),
            "Administrator phone used for SMS tests and system alerts");
        save("admin_email", adminEmailField.getText(), "Administrator email");
        save("admin_whatsapp", adminWhatsappField.getText(), "Administrator WhatsApp number");

        save("sms_enabled", String.valueOf(smsEnabledBox.isSelected()),
            "Enable SMS delivery through the configured gateway");
        save("sms_provider", smsProviderBox.getValue() == null
                ? "AFRICAS_TALKING" : smsProviderBox.getValue().name(),
            "SMS gateway: AFRICAS_TALKING, TWILIO or GENERIC");
        save("sms_environment", smsEnvironmentBox.getValue() == null
                ? "PRODUCTION" : smsEnvironmentBox.getValue().name(),
            "Africa's Talking environment: SANDBOX or PRODUCTION");
        save("sms_username", smsUsernameField.getText(),
            "Africa's Talking username or Twilio Account SID");
        saveSecretIfProvided("sms_api_key", smsApiKeyField.getText(),
            "SMS gateway API key or Twilio auth token (DPAPI-encrypted)");
        save("sms_sender_id", smsSenderField.getText(),
            "Sender ID shown on SMS (Africa's Talking)");
        save("sms_from_number", smsFromField.getText(), "Twilio sender phone number");
        save("sms_generic_url", smsUrlField.getText(), "Generic SMS gateway URL");
        save("sms_country_code", smsCountryCodeField.getText(),
            "Country code used for local phone numbers");

        save("whatsapp_enabled", String.valueOf(whatsappEnabledBox.isSelected()),
            "Enable WhatsApp Cloud API delivery");
        save("whatsapp_api_url", whatsappUrlField.getText(),
            "WhatsApp Cloud API base URL");
        save("whatsapp_phone_number_id", whatsappPhoneIdField.getText(),
            "Meta WhatsApp phone number ID");
        saveSecretIfProvided("whatsapp_access_token", whatsappTokenField.getText(),
            "Meta WhatsApp Cloud API access token (DPAPI-encrypted)");
        save("whatsapp_business_number", whatsappNumberField.getText(),
            "WhatsApp business display number");
        save("whatsapp_template_name", whatsappTemplateField.getText(),
            "Approved WhatsApp template for first-contact messages");
        save("whatsapp_template_language", whatsappLanguageField.getText(),
            "WhatsApp template language code");

        save("cloud_backup_enabled", String.valueOf(cloudEnabledBox.isSelected()),
            "Copy backups to a cloud-synced folder");
        save("cloud_provider", cloudCode(cloudProviderBox.getValue()),
            "Cloud provider: ONEDRIVE, GOOGLE_DRIVE, DROPBOX or OTHER");
        save("cloud_account", cloudAccountField.getText(),
            "Cloud account email or label shown to the admin");
        save("cloud_folder", cloudFolderField.getText(),
            "Local folder synced by the cloud provider");
        save("cloud_auto_upload", String.valueOf(cloudAutoUploadBox.isSelected()),
            "Upload each new backup to the cloud folder automatically");
        save("auto_track_enabled", String.valueOf(autoTrackBox.isSelected()),
            "Record outbound messages in the tenant communication history");
        save("reminder_auto_send_enabled", String.valueOf(autoSendBox.isSelected()),
            "Send due reminders automatically while the app is open");
        save("integration_setup_complete", "true",
            "Whether the admin completed communication setup");
    }

    private void save(String key, String value, String description) {
        settingsDAO.save(key, value == null ? "" : value.trim(), description);
    }

    private void saveSecretIfProvided(String key, String value, String description) {
        if (value == null || value.isBlank()) return;   // keep the existing key
        if (!settingsDAO.saveSecret(key, value.trim(), description)) {
            throw new IllegalStateException(
                "Could not encrypt " + key + " with Windows DPAPI on this PC.");
        }
    }

    /* ==================== BUILDERS ==================== */

    private MessagingService.SmsConfig buildSmsConfig(boolean forceEnabled) {
        MessagingService.Environment environment = smsEnvironmentBox.getValue() == null
            ? MessagingService.Environment.from(AppSettings.smsEnvironment())
            : smsEnvironmentBox.getValue();
        return new MessagingService.SmsConfig(
            forceEnabled || smsEnabledBox.isSelected(),
            smsProviderBox.getValue() == null
                ? MessagingService.SmsProvider.AFRICAS_TALKING : smsProviderBox.getValue(),
            environment,
            smsUsernameField.getText(),
            effectiveSmsApiKey(),
            smsSenderField.getText(),
            smsFromField.getText(),
            smsUrlField.getText(),
            smsCountryCodeField.getText());
    }

    private MessagingService.WhatsAppConfig buildWhatsAppConfig(boolean forceEnabled) {
        return new MessagingService.WhatsAppConfig(
            forceEnabled || whatsappEnabledBox.isSelected(),
            whatsappUrlField.getText(),
            whatsappPhoneIdField.getText(),
            effectiveWhatsappToken(),
            whatsappNumberField.getText(),
            whatsappTemplateField.getText(),
            whatsappLanguageField.getText());
    }

    /** A blank field means "keep the saved key", not "erase it". */
    private String effectiveSmsApiKey() {
        String typed = smsApiKeyField.getText();
        return typed == null || typed.isBlank() ? AppSettings.smsApiKey() : typed.trim();
    }

    private String effectiveWhatsappToken() {
        String typed = whatsappTokenField.getText();
        return typed == null || typed.isBlank()
            ? AppSettings.whatsappAccessToken() : typed.trim();
    }

    private String savedKeyHint(String stored) {
        if (stored == null || stored.isBlank()) {
            return "No key saved yet. Use an app-specific key, not your login password.";
        }
        if (!com.rentals.security.SecretStore.canDecrypt(stored)) {
            return "A saved key exists but cannot be read on this PC. Enter it again.";
        }
        String plain = com.rentals.security.SecretStore.decrypt(stored);
        return "Saved: " + com.rentals.security.SecretStore.mask(plain)
            + ". Leave this field blank to keep it.";
    }

    private void updateSmsFields() {
        MessagingService.SmsProvider provider = smsProviderBox.getValue();
        if (provider == null) provider = MessagingService.SmsProvider.AFRICAS_TALKING;

        boolean at = provider == MessagingService.SmsProvider.AFRICAS_TALKING;
        boolean twilio = provider == MessagingService.SmsProvider.TWILIO;
        boolean generic = provider == MessagingService.SmsProvider.GENERIC;

        smsUsernameField.setDisable(!(at || twilio));
        smsSenderField.setDisable(!at);
        smsFromField.setDisable(!twilio);
        smsUrlField.setDisable(!generic);
        smsEnvironmentBox.setDisable(!at);
    }

    private void updateCloudHint() {
        String label = cloudProviderBox.getValue();
        String hint = switch (label == null ? "" : label) {
            case "OneDrive" ->
                "Open the OneDrive app, sign in, then choose a folder inside your OneDrive " +
                "directory. RentalSys suggests a RentalBackups subfolder.";
            case "Google Drive" ->
                "Open Google Drive for desktop, sign in, then choose a folder inside your " +
                "Google Drive (for example G:\\My Drive\\RentalBackups).";
            case "Dropbox" ->
                "Open the Dropbox app, sign in, then choose a folder inside your Dropbox " +
                "directory.";
            default ->
                "Choose any folder that is synchronised to your cloud account by another app.";
        };
        cloudHintLabel.setText(hint);
    }

    private VBox tabBox() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(18));
        box.setAlignment(Pos.TOP_LEFT);
        return box;
    }

    private ScrollPane scroll(VBox content) {
        ScrollPane pane = new ScrollPane(content);
        pane.setFitToWidth(true);
        pane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        pane.setStyle("-fx-background-color: transparent;");
        return pane;
    }

    private GridPane grid() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        return grid;
    }

    private HBox providerRow(String text, ComboBox<?> combo) {
        Label label = new Label(text);
        label.setMinWidth(150);
        HBox box = new HBox(12, label, combo);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private void addRow(GridPane grid, int row, String labelText,
                        javafx.scene.Node field, String hint) {
        Label label = new Label(labelText);
        label.setMinWidth(150);
        label.setAlignment(Pos.CENTER_LEFT);
        grid.add(label, 0, row);

        if (field instanceof Region region) {
            region.setMaxWidth(field instanceof HBox ? 360 : 280);
            if (field instanceof TextInputControl input) {
                input.setPrefWidth(250);
            }
        }
        grid.add(field, 1, row);

        Label hintLabel = new Label(hint);
        hintLabel.getStyleClass().add("label-subtitle");
        hintLabel.setWrapText(true);
        hintLabel.setMaxWidth(200);
        grid.add(hintLabel, 2, row);
    }

    /** Folder rows need a text field plus browse/detect buttons in one cell. */
    private void addFolderRow(GridPane grid, int row, String labelText,
                              HBox field, String hint) {
        Label label = new Label(labelText);
        label.setMinWidth(150);
        label.setAlignment(Pos.CENTER_LEFT);
        grid.add(label, 0, row);

        field.setMaxWidth(430);
        grid.add(field, 1, row);

        Label hintLabel = new Label(hint);
        hintLabel.getStyleClass().add("label-subtitle");
        hintLabel.setWrapText(true);
        hintLabel.setMaxWidth(160);
        grid.add(hintLabel, 2, row);
    }

    private Label subtitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("label-subtitle");
        label.setWrapText(true);
        return label;
    }

    private static String cloudCode(String label) {
        if (label == null) return "OTHER";
        return switch (label) {
            case "OneDrive" -> "ONEDRIVE";
            case "Google Drive" -> "GOOGLE_DRIVE";
            case "Dropbox" -> "DROPBOX";
            default -> "OTHER";
        };
    }

    private static String cloudLabel(String code) {
        if (code == null) return "OneDrive";
        return switch (code.toUpperCase(java.util.Locale.ROOT)) {
            case "GOOGLE_DRIVE" -> "Google Drive";
            case "DROPBOX" -> "Dropbox";
            case "OTHER" -> "Other";
            default -> "OneDrive";
        };
    }

    private void alert(Alert.AlertType type, String message) {
        Alert alert = new Alert(type, message, ButtonType.OK);
        alert.setHeaderText(null);
        if (owner != null) alert.initOwner(owner);
        alert.showAndWait();
    }
}
