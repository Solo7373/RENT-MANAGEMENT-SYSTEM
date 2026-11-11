package com.rentals.ui.tenants;

import com.rentals.documents.TenantDocument;
import com.rentals.service.DocumentService;
import com.rentals.service.ThemeManager;
import com.rentals.tenants.Tenant;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Path;

/**
 * Dialog for attaching, opening and deleting a tenant's documents.
 * Files are copied into the application's documents folder.
 */
public final class TenantDocumentsDialog {

    private TenantDocumentsDialog() {}

    public static void show(Window owner, Tenant tenant) {
        DocumentService service = new DocumentService();
        ObservableList<TenantDocument> documents = FXCollections.observableArrayList();

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Documents - " + tenant.getFullName());
        dialog.initOwner(owner);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        Label subtitle = new Label(
            "Attach scanned IDs, leases and other paperwork for " + tenant.getFullName() +
            ". Files are stored locally in the application data folder.");
        subtitle.setWrapText(true);
        subtitle.getStyleClass().add("label-subtitle");

        TableView<TenantDocument> table = new TableView<>();
        table.setItems(documents);
        table.setPrefHeight(260);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<TenantDocument, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(d -> new SimpleStringProperty(safe(d.getValue().getDocumentType())));

        TableColumn<TenantDocument, String> nameCol = new TableColumn<>("File");
        nameCol.setCellValueFactory(d -> new SimpleStringProperty(safe(d.getValue().getFileName())));

        TableColumn<TenantDocument, String> sizeCol = new TableColumn<>("Size");
        sizeCol.setCellValueFactory(d -> new SimpleStringProperty(size(d.getValue().getFileSize())));

        TableColumn<TenantDocument, String> uploadedCol = new TableColumn<>("Uploaded");
        uploadedCol.setCellValueFactory(d -> new SimpleStringProperty(safe(d.getValue().getUploadedAt())));

        table.getColumns().addAll(typeCol, nameCol, sizeCol, uploadedCol);

        ComboBox<String> typeBox = new ComboBox<>();
        typeBox.getItems().addAll(
            "National ID", "School ID", "Refugee ID", "Lease Agreement",
            "Employment Letter", "Reference Letter", "Other");
        typeBox.setValue("National ID");
        typeBox.setPrefWidth(160);

        Button attachBtn = new Button("📎 Attach File");
        attachBtn.getStyleClass().add("btn-primary");
        attachBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Choose a document for " + tenant.getFullName());
            chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Documents",
                    "*.pdf", "*.png", "*.jpg", "*.jpeg", "*.webp", "*.gif", "*.bmp",
                    "*.txt", "*.doc", "*.docx"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
            File selected = chooser.showOpenDialog(owner);
            if (selected == null) return;

            try {
                TenantDocument doc = service.attach(tenant.getTenantId(), typeBox.getValue(),
                    Path.of(selected.getAbsolutePath()));
                documents.add(0, doc);
                com.rentals.service.AuditService.log("DOCUMENT_ATTACH",
                    "Attached " + doc.getDocumentType() + " for tenant " + tenant.getFullName());
            } catch (IllegalArgumentException ex) {
                alert(Alert.AlertType.WARNING, ex.getMessage());
            } catch (Exception ex) {
                alert(Alert.AlertType.ERROR, "Could not attach the file: " + ex.getMessage());
            }
        });

        Button openBtn = new Button("📂 Open");
        openBtn.getStyleClass().add("btn-secondary");
        openBtn.setOnAction(e -> {
            TenantDocument selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { alert(Alert.AlertType.WARNING, "Select a document first"); return; }
            try {
                service.open(selected);
            } catch (IllegalStateException ex) {
                alert(Alert.AlertType.WARNING, ex.getMessage());
            }
        });

        Button deleteBtn = new Button("🗑 Delete");
        deleteBtn.getStyleClass().add("btn-danger");
        deleteBtn.setOnAction(e -> {
            TenantDocument selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { alert(Alert.AlertType.WARNING, "Select a document first"); return; }
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete '" + selected.getFileName() + "'? This cannot be undone.",
                ButtonType.YES, ButtonType.NO);
            confirm.setHeaderText(null);
            confirm.initOwner(dialog.getOwner());
            if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;

            if (service.delete(selected)) {
                documents.remove(selected);
                com.rentals.service.AuditService.log("DOCUMENT_DELETE",
                    "Deleted " + selected.getDocumentType() + " for tenant " + tenant.getFullName());
            } else {
                alert(Alert.AlertType.ERROR, "Could not delete the document record");
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(10, typeBox, attachBtn, openBtn, deleteBtn, spacer);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(14, subtitle, table, actions);
        content.setPadding(new Insets(18));
        content.setPrefWidth(720);

        dialog.getDialogPane().setContent(content);
        dialog.setOnShown(e -> ThemeManager.applyTo(dialog.getDialogPane().getScene()));
        try {
            documents.setAll(service.list(tenant.getTenantId()));
        } catch (Exception ex) {
            alert(Alert.AlertType.ERROR, "Could not load documents: " + ex.getMessage());
        }
        dialog.showAndWait();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String size(long bytes) {
        if (bytes <= 0) return "-";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.0f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private static void alert(Alert.AlertType type, String message) {
        Alert alert = new Alert(type, message);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
