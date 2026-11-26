package com.rentals.ui.tenants;

import com.rentals.communications.CommunicationLog;
import com.rentals.dao.CommunicationLogDAO;
import com.rentals.tenants.Tenant;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;

/**
 * Read-only timeline of every message recorded for one tenant.
 */
public final class TenantCommunicationDialog {

    private TenantCommunicationDialog() {}

    public static void show(Stage owner, Tenant tenant) {
        if (tenant == null) return;

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Communication History - " + tenant.getFullName());
        dialog.setHeaderText("Messages recorded for " + tenant.getFullName()
            + " (phone " + safe(tenant.getPhone()) + ").");
        dialog.initOwner(owner);
        dialog.setResizable(true);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        CommunicationLogDAO dao = new CommunicationLogDAO();
        List<CommunicationLog> entries = dao.getByTenant(tenant.getTenantId(), 200);

        Label summary = new Label(entries.isEmpty()
            ? "No messages have been recorded for this tenant yet."
            : entries.size() + " recent message(s). Delivery reports, replies and call outcomes "
                + "need a hosted webhook relay and are not available in this desktop build.");
        summary.getStyleClass().add("label-subtitle");
        summary.setWrapText(true);

        TableView<CommunicationLog> table = new TableView<>();
        table.getItems().setAll(entries);
        table.setPrefHeight(430);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<CommunicationLog, String> whenCol = new TableColumn<>("When");
        whenCol.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
        whenCol.setPrefWidth(140);

        TableColumn<CommunicationLog, String> eventCol = new TableColumn<>("Event");
        eventCol.setCellValueFactory(new PropertyValueFactory<>("eventType"));
        eventCol.setPrefWidth(100);

        TableColumn<CommunicationLog, String> channelCol = new TableColumn<>("Channel");
        channelCol.setCellValueFactory(new PropertyValueFactory<>("channel"));
        channelCol.setPrefWidth(90);

        TableColumn<CommunicationLog, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setPrefWidth(80);

        TableColumn<CommunicationLog, String> recipientCol = new TableColumn<>("Recipient");
        recipientCol.setCellValueFactory(new PropertyValueFactory<>("recipient"));
        recipientCol.setPrefWidth(130);

        TableColumn<CommunicationLog, String> providerCol = new TableColumn<>("Provider");
        providerCol.setCellValueFactory(new PropertyValueFactory<>("provider"));
        providerCol.setPrefWidth(160);

        TableColumn<CommunicationLog, String> providerIdCol = new TableColumn<>("Provider ID");
        providerIdCol.setCellValueFactory(new PropertyValueFactory<>("providerMessageId"));
        providerIdCol.setPrefWidth(150);

        TableColumn<CommunicationLog, String> messageCol = new TableColumn<>("Message");
        messageCol.setCellValueFactory(new PropertyValueFactory<>("message"));
        messageCol.setPrefWidth(300);

        TableColumn<CommunicationLog, String> errorCol = new TableColumn<>("Error");
        errorCol.setCellValueFactory(new PropertyValueFactory<>("errorMessage"));
        errorCol.setPrefWidth(200);

        table.getColumns().addAll(whenCol, eventCol, channelCol, statusCol, recipientCol,
            providerCol, providerIdCol, messageCol, errorCol);

        VBox content = new VBox(12, summary, table);
        content.setPadding(new Insets(10));
        content.setPrefWidth(1080);
        dialog.getDialogPane().setContent(content);

        dialog.setOnShown(e -> com.rentals.service.ThemeManager.applyTo(
            dialog.getDialogPane().getScene()));
        dialog.showAndWait();
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "not set" : value;
    }
}
