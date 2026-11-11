package com.rentals.ui.reminders;

import com.rentals.dao.ReminderDAO;
import com.rentals.dao.TenantDAO;
import com.rentals.notifications.NotificationLog;
import com.rentals.reminders.ReminderRule;
import com.rentals.service.AuditService;
import com.rentals.service.NotificationService;
import com.rentals.settings.AppSettings;
import com.rentals.tenants.Tenant;
import com.rentals.ui.settings.IntegrationsSetupDialog;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Stage;

/**
 * Reminders view - manage reminder rules and notification history.
 * Uses ReminderDAO (live database data).
 */
public class RemindersView {

    private final ReminderDAO reminderDAO = new ReminderDAO();
    private final TenantDAO tenantDAO = new TenantDAO();

    private final ObservableList<ReminderRule> rules = FXCollections.observableArrayList();
    private final ObservableList<NotificationLog> logs = FXCollections.observableArrayList();
    private TableView<ReminderRule> rulesTable;
    private TableView<NotificationLog> logsTable;
    private Stage stage;
    private final Label configLabel = new Label();

    public static Pane create(Stage stage, Runnable onBack) {
        return new RemindersView().build(stage, onBack);
    }

    private Pane build(Stage stage, Runnable onBack) {
        this.stage = stage;
        VBox root = new VBox(18);
        root.getStyleClass().add("card");
        root.setPadding(new Insets(30));
        root.setAlignment(Pos.TOP_LEFT);

        // ---------- Header ----------
        HBox header = new HBox(15);
        header.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label("🔔 Reminders");
        titleLabel.getStyleClass().add("label-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button setupBtn = new Button("⚙ Communication Setup");
        setupBtn.getStyleClass().add("btn-secondary");
        setupBtn.setOnAction(e -> {
            IntegrationsSetupDialog.show(stage);
            updateConfigLabel();
        });

        Button backBtn = new Button("Back to Dashboard");
        backBtn.getStyleClass().add("btn-back");
        backBtn.setOnAction(e -> onBack.run());

        header.getChildren().addAll(titleLabel, spacer, setupBtn, backBtn);
        root.getChildren().add(header);

        configLabel.getStyleClass().add("label-subtitle");
        configLabel.setWrapText(true);
        root.getChildren().add(configLabel);

        // ==================== REMINDER RULES ====================
        Label rulesTitle = new Label("Reminder Rules");
        rulesTitle.getStyleClass().add("label-section");
        root.getChildren().add(rulesTitle);

        rulesTable = new TableView<>();
        rulesTable.setItems(rules);
        rulesTable.setPrefHeight(180);
        rulesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<ReminderRule, Long> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("ruleId"));
        idCol.setPrefWidth(50);

        TableColumn<ReminderRule, Integer> stageCol = new TableColumn<>("Stage");
        stageCol.setCellValueFactory(new PropertyValueFactory<>("stage"));
        stageCol.setPrefWidth(70);

        TableColumn<ReminderRule, Integer> daysCol = new TableColumn<>("Days After Due");
        daysCol.setCellValueFactory(new PropertyValueFactory<>("daysAfterDue"));
        daysCol.setPrefWidth(130);

        TableColumn<ReminderRule, String> channelCol = new TableColumn<>("Channel");
        channelCol.setCellValueFactory(new PropertyValueFactory<>("channel"));
        channelCol.setPrefWidth(100);

        TableColumn<ReminderRule, String> messageCol = new TableColumn<>("Message Template");
        messageCol.setCellValueFactory(new PropertyValueFactory<>("messageTemplate"));
        messageCol.setPrefWidth(350);

        TableColumn<ReminderRule, Boolean> enabledCol = new TableColumn<>("Enabled");
        enabledCol.setCellValueFactory(new PropertyValueFactory<>("enabled"));
        enabledCol.setPrefWidth(80);

        rulesTable.getColumns().addAll(idCol, stageCol, daysCol, channelCol, messageCol, enabledCol);
        root.getChildren().add(rulesTable);

        // Rule actions
        HBox ruleActions = new HBox(12);
        ruleActions.setAlignment(Pos.CENTER_LEFT);

        Button toggleBtn = new Button("Enable / Disable");
        toggleBtn.getStyleClass().add("btn-secondary");
        toggleBtn.setOnAction(e -> {
            ReminderRule selected = rulesTable.getSelectionModel().getSelectedItem();
            if (selected == null) { showAlert("Select a rule first"); return; }
            selected.setEnabled(!selected.isEnabled());
            if (reminderDAO.updateRule(selected)) refresh();
        });

        Button deleteRuleBtn = new Button("🗑 Delete Rule");
        deleteRuleBtn.getStyleClass().add("btn-danger");
        deleteRuleBtn.setOnAction(e -> {
            ReminderRule selected = rulesTable.getSelectionModel().getSelectedItem();
            if (selected == null) { showAlert("Select a rule first"); return; }
            if (reminderDAO.deleteRule(selected.getRuleId())) refresh();
        });

        ruleActions.getChildren().addAll(toggleBtn, deleteRuleBtn);
        root.getChildren().add(ruleActions);

        // Add rule form
        HBox ruleForm = new HBox(12);
        ruleForm.setAlignment(Pos.CENTER_LEFT);
        ruleForm.getStyleClass().add("form-box");
        ruleForm.setMaxWidth(1000);

        TextField stageField = new TextField();
        stageField.setPromptText("Stage (1,2,3)");
        stageField.setPrefWidth(110);

        TextField daysField = new TextField();
        daysField.setPromptText("Days after due");
        daysField.setPrefWidth(120);

        ComboBox<String> channelCombo = new ComboBox<>();
        channelCombo.getItems().addAll("SMS", "EMAIL", "WHATSAPP");
        channelCombo.setPromptText("Channel");
        channelCombo.setPrefWidth(140);

        TextField messageField = new TextField();
        messageField.setPromptText("Message template (use {name}, {amount})");
        messageField.setPrefWidth(380);

        Button addRuleBtn = new Button("Add Rule");
        addRuleBtn.getStyleClass().add("btn-success");
        addRuleBtn.setOnAction(e -> {
            if (stageField.getText().isEmpty() || daysField.getText().isEmpty()
                || channelCombo.getValue() == null || messageField.getText().isEmpty()) {
                showAlert("Please fill all rule fields");
                return;
            }
            try {
                ReminderRule rule = new ReminderRule(
                    Integer.parseInt(stageField.getText().trim()),
                    Integer.parseInt(daysField.getText().trim()),
                    channelCombo.getValue(),
                    messageField.getText());
                if (reminderDAO.createRule(rule)) {
                    refresh();
                    stageField.clear();
                    daysField.clear();
                    messageField.clear();
                    channelCombo.setValue(null);
                }
            } catch (NumberFormatException ex) {
                showAlert("Stage and days must be numbers");
            }
        });

        ruleForm.getChildren().addAll(stageField, daysField, channelCombo, messageField, addRuleBtn);
        root.getChildren().add(ruleForm);

        // ==================== NOTIFICATION LOG ====================
        Label logTitle = new Label("Notification History");
        logTitle.getStyleClass().add("label-section");
        root.getChildren().add(logTitle);

        logsTable = new TableView<>();
        logsTable.setItems(logs);
        logsTable.setPrefHeight(160);
        logsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<NotificationLog, Long> nidCol = new TableColumn<>("ID");
        nidCol.setCellValueFactory(new PropertyValueFactory<>("notificationId"));
        nidCol.setPrefWidth(50);

        TableColumn<NotificationLog, String> tenantCol = new TableColumn<>("Tenant");
        tenantCol.setCellValueFactory(new PropertyValueFactory<>("tenantName"));
        tenantCol.setPrefWidth(140);

        TableColumn<NotificationLog, String> recipientCol = new TableColumn<>("Recipient");
        recipientCol.setCellValueFactory(new PropertyValueFactory<>("recipient"));
        recipientCol.setPrefWidth(130);

        TableColumn<NotificationLog, String> nMessageCol = new TableColumn<>("Message");
        nMessageCol.setCellValueFactory(new PropertyValueFactory<>("message"));
        nMessageCol.setPrefWidth(300);

        TableColumn<NotificationLog, String> nChannelCol = new TableColumn<>("Channel");
        nChannelCol.setCellValueFactory(new PropertyValueFactory<>("channel"));
        nChannelCol.setPrefWidth(90);

        TableColumn<NotificationLog, String> nStatusCol = new TableColumn<>("Status");
        nStatusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        nStatusCol.setPrefWidth(90);

        TableColumn<NotificationLog, String> errorCol = new TableColumn<>("Last error");
        errorCol.setCellValueFactory(new PropertyValueFactory<>("errorMessage"));
        errorCol.setPrefWidth(200);

        TableColumn<NotificationLog, String> nDateCol = new TableColumn<>("Created");
        nDateCol.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
        nDateCol.setPrefWidth(140);

        logsTable.getColumns().addAll(nidCol, tenantCol, recipientCol, nMessageCol,
            nChannelCol, nStatusCol, errorCol, nDateCol);
        root.getChildren().add(logsTable);

        // Send reminder form
        HBox sendForm = new HBox(12);
        sendForm.setAlignment(Pos.CENTER_LEFT);
        sendForm.getStyleClass().add("form-box");
        sendForm.setMaxWidth(Double.MAX_VALUE);

        ComboBox<Tenant> tenantCombo = new ComboBox<>();
        tenantCombo.setPromptText("Select Tenant");
        tenantCombo.setPrefWidth(200);
        tenantCombo.getItems().addAll(tenantDAO.getAll());

        TextField sendMessageField = new TextField();
        sendMessageField.setPromptText("Reminder message...");
        sendMessageField.setPrefWidth(380);
        sendMessageField.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(sendMessageField, Priority.ALWAYS);

        ComboBox<String> sendChannelCombo = new ComboBox<>();
        sendChannelCombo.getItems().addAll("SMS", "EMAIL", "WHATSAPP");
        sendChannelCombo.setPromptText("Channel");
        sendChannelCombo.setPrefWidth(140);

        Button sendBtn = new Button("Queue Reminder");
        sendBtn.getStyleClass().add("btn-primary");
        sendBtn.setOnAction(e -> {
            if (tenantCombo.getValue() == null || sendMessageField.getText().isEmpty()
                || sendChannelCombo.getValue() == null) {
                showAlert("Select tenant, message and channel");
                return;
            }
            if (reminderDAO.queueNotification(
                    tenantCombo.getValue().getTenantId(),
                    sendMessageField.getText(),
                    sendChannelCombo.getValue())) {
                refresh();
                sendMessageField.clear();
            }
        });

        Button sendNowBtn = new Button("🚀 Send Now");
        sendNowBtn.getStyleClass().add("btn-primary");
        sendNowBtn.setOnAction(e -> sendSelected(sendNowBtn));

        Button markSentBtn = new Button("✔ Mark Sent (manual)");
        markSentBtn.getStyleClass().add("btn-success");
        markSentBtn.setOnAction(e -> {
            NotificationLog selected = logsTable.getSelectionModel().getSelectedItem();
            if (selected == null) { showAlert("Select a notification first"); return; }
            if ("SENT".equals(selected.getStatus())) { showAlert("Already marked as sent"); return; }
            if (reminderDAO.markSent(selected.getNotificationId())) refresh();
        });

        sendForm.getChildren().addAll(tenantCombo, sendMessageField, sendChannelCombo,
            sendBtn, sendNowBtn, markSentBtn);
        root.getChildren().add(sendForm);

        // Initial load
        refresh();

        return root;
    }

    /** Reloads rules and notification logs. */
    private void refresh() {
        rules.setAll(reminderDAO.getAllRules());
        logs.setAll(reminderDAO.getAllLogs());
        updateConfigLabel();
    }

    private void sendSelected(Button button) {
        NotificationLog selected = logsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Select a notification first");
            return;
        }

        button.setDisable(true);
        Task<NotificationService.SendResult> task = new Task<>() {
            @Override
            protected NotificationService.SendResult call() {
                return NotificationService.send(selected);
            }
        };
        task.setOnSucceeded(e -> {
            button.setDisable(false);
            NotificationService.SendResult result = task.getValue();
            refresh();
            if (result.success()) {
                AuditService.log("NOTIFICATION_SEND",
                    "Sent " + result.channel() + " reminder to " + result.recipient());
                refresh();
                showInfo(result.channel() + " sent to " + result.recipient()
                    + ".\n\n" + result.detail());
            } else {
                AuditService.log("NOTIFICATION_FAIL",
                    "Could not send " + result.channel() + " reminder: " + result.detail());
                refresh();
                showAlert("Sending failed:\n\n" + result.detail());
            }
        });
        task.setOnFailed(e -> {
            button.setDisable(false);
            Throwable error = task.getException();
            refresh();
            showAlert("Sending failed: "
                + (error == null ? "unknown error" : error.getMessage()));
        });
        new Thread(task, "notification-send").start();
    }

    private void updateConfigLabel() {
        String sms;
        if (AppSettings.smsConfigured()) sms = "SMS ready (" + smsProviderLabel() + ")";
        else if (AppSettings.smsEnabled()) sms = "SMS enabled but incomplete";
        else sms = "SMS disabled";

        String whatsapp;
        if (AppSettings.whatsappConfigured()) whatsapp = "WhatsApp ready";
        else if (AppSettings.whatsappEnabled()) whatsapp = "WhatsApp enabled but incomplete";
        else whatsapp = "WhatsApp disabled";

        configLabel.setText(sms + "  •  " + whatsapp
            + "  •  Sending needs internet. Use ⚙ Communication Setup to change these.");
    }

    private String smsProviderLabel() {
        String provider = AppSettings.smsProvider();
        return switch (provider == null ? "" : provider.toUpperCase(java.util.Locale.ROOT)) {
            case "TWILIO" -> "Twilio";
            case "GENERIC" -> "Generic HTTP gateway";
            default -> "Africa's Talking";
        };
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message);
        alert.setHeaderText(null);
        if (stage != null) alert.initOwner(stage);
        alert.showAndWait();
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
        alert.setHeaderText(null);
        if (stage != null) alert.initOwner(stage);
        alert.showAndWait();
    }
}
