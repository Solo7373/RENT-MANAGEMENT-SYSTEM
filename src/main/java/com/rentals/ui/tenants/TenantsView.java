package com.rentals.ui.tenants;

import com.rentals.dao.TenantDAO;
import com.rentals.service.AuditService;
import com.rentals.service.DocumentService;
import com.rentals.settings.AppSettings;
import com.rentals.tenants.Tenant;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Stage;

/**
 * Tenants management view with document fields.
 * Search, add, edit and delete tenants using the existing TenantDAO.
 */
public class TenantsView {

    private final TenantDAO tenantDAO = new TenantDAO();
    private final DocumentService documentService = new DocumentService();
    private final ObservableList<Tenant> tenants = FXCollections.observableArrayList();
    private TableView<Tenant> table;
    private Tenant editing;

    public static Pane create(Stage stage, Runnable onBack) {
        return new TenantsView().build(stage, onBack);
    }

    private Pane build(Stage stage, Runnable onBack) {
        VBox root = new VBox(16);
        root.getStyleClass().add("card");
        root.setPadding(new Insets(25));
        root.setAlignment(Pos.TOP_LEFT);

        // ---------- Header ----------
        HBox header = new HBox(15);
        header.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label("Tenants");
        titleLabel.getStyleClass().add("label-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        TextField searchField = new TextField();
        searchField.setPromptText("🔍 Search name, phone or ID...");
        searchField.setPrefWidth(250);
        searchField.textProperty().addListener((obs, oldV, newV) -> {
            if (newV == null || newV.isBlank()) {
                refresh();
            } else {
                tenants.setAll(tenantDAO.search(newV.trim()));
            }
        });

        Button backBtn = new Button("Back to Dashboard");
        backBtn.getStyleClass().add("btn-back");
        backBtn.setOnAction(e -> onBack.run());

        header.getChildren().addAll(titleLabel, spacer, searchField, backBtn);
        root.getChildren().add(header);

        // ---------- Table ----------
        table = new TableView<>();
        table.setItems(tenants);
        table.setPrefHeight(260);

        TableColumn<Tenant, Long> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("tenantId"));
        idCol.setPrefWidth(40);

        TableColumn<Tenant, String> nameCol = new TableColumn<>("Full Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        nameCol.setPrefWidth(170);

        TableColumn<Tenant, String> phoneCol = new TableColumn<>("Phone");
        phoneCol.setCellValueFactory(new PropertyValueFactory<>("phone"));
        phoneCol.setPrefWidth(110);

        TableColumn<Tenant, String> whatsappCol = new TableColumn<>("WhatsApp");
        whatsappCol.setCellValueFactory(new PropertyValueFactory<>("whatsappNumber"));
        whatsappCol.setPrefWidth(110);

        TableColumn<Tenant, String> nationalIdCol = new TableColumn<>("National ID");
        nationalIdCol.setCellValueFactory(new PropertyValueFactory<>("idNumber"));
        nationalIdCol.setPrefWidth(110);

        TableColumn<Tenant, String> schoolIdCol = new TableColumn<>("School ID");
        schoolIdCol.setCellValueFactory(new PropertyValueFactory<>("schoolId"));
        schoolIdCol.setPrefWidth(100);

        TableColumn<Tenant, String> refugeeIdCol = new TableColumn<>("Refugee ID");
        refugeeIdCol.setCellValueFactory(new PropertyValueFactory<>("refugeeId"));
        refugeeIdCol.setPrefWidth(100);

        TableColumn<Tenant, String> roomCol = new TableColumn<>("Room #");
        roomCol.setCellValueFactory(new PropertyValueFactory<>("roomNumber"));
        roomCol.setPrefWidth(70);

        TableColumn<Tenant, Double> rentCol = new TableColumn<>("Rent (" + AppSettings.currency() + ")");
        rentCol.setCellValueFactory(new PropertyValueFactory<>("rentAmount"));
        rentCol.setPrefWidth(90);

        TableColumn<Tenant, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setPrefWidth(75);

        table.getColumns().addAll(idCol, nameCol, phoneCol, whatsappCol, nationalIdCol,
                                   schoolIdCol, refugeeIdCol, roomCol, rentCol, statusCol);
        root.getChildren().add(table);

        // ---------- Row actions ----------
        HBox actionsBox = new HBox(12);
        actionsBox.setAlignment(Pos.CENTER_LEFT);

        Button docsBtn = new Button("📎 Documents");
        docsBtn.getStyleClass().add("btn-secondary");
        docsBtn.setOnAction(e -> {
            Tenant selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { showAlert("Select a tenant first"); return; }
            TenantDocumentsDialog.show(stage, selected);
        });

        Button deleteBtn = new Button("🗑 Delete Tenant");
        deleteBtn.getStyleClass().add("btn-danger");
        deleteBtn.setOnAction(e -> {
            Tenant selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { showAlert("Select a tenant first"); return; }

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete " + selected.getFullName() + " and all attached documents?",
                ButtonType.YES, ButtonType.NO);
            confirm.setHeaderText(null);
            confirm.initOwner(stage);
            if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;

            if (tenantDAO.delete(selected.getTenantId())) {
                documentService.deleteAllForTenant(selected.getTenantId());
                AuditService.log("TENANT_DELETE", "Deleted tenant " + selected.getFullName());
                refresh();
            } else {
                showAlert("Could not delete the tenant. Check that no linked records block it.");
            }
        });

        actionsBox.getChildren().addAll(docsBtn, deleteBtn);
        root.getChildren().add(actionsBox);

        // ---------- Add / edit form ----------
        VBox formContainer = new VBox(10);
        formContainer.getStyleClass().add("form-box");
        formContainer.setMaxWidth(1050);

        // Row 1: identity + contact
        HBox row1 = new HBox(10);
        row1.setAlignment(Pos.CENTER_LEFT);

        TextField nameField = field("Full Name", 160);
        TextField phoneField = field("Phone", 110);
        TextField whatsappField = field("WhatsApp", 110);
        TextField emailField = field("Email", 160);
        TextField emergencyContactField = field("Emergency Contact", 140);
        TextField emergencyPhoneField = field("Emergency Phone", 120);

        row1.getChildren().addAll(nameField, phoneField, whatsappField, emailField,
                                  emergencyContactField, emergencyPhoneField);

        // Row 2: documents + room/pricing
        HBox row2 = new HBox(10);
        row2.setAlignment(Pos.CENTER_LEFT);

        TextField nationalIdField = field("National ID", 130);
        TextField schoolIdField = field("School ID", 110);
        TextField refugeeIdField = field("Refugee ID", 110);
        TextField roomNumberField = field("Room Number", 110);
        TextField rentField = field("Rent Amount", 110);

        Button addBtn = new Button("Add Tenant");
        addBtn.getStyleClass().add("btn-success");

        Button cancelEditBtn = new Button("Cancel");
        cancelEditBtn.getStyleClass().add("btn-secondary");
        cancelEditBtn.setVisible(false);

        row2.getChildren().addAll(nationalIdField, schoolIdField, refugeeIdField,
                                  roomNumberField, rentField, addBtn, cancelEditBtn);

        addBtn.setOnAction(e -> {
            if (nameField.getText().isEmpty() || phoneField.getText().isEmpty()) {
                showAlert("Please enter name and phone");
                return;
            }
            Double rent = null;
            if (!rentField.getText().isEmpty()) {
                try {
                    rent = Double.parseDouble(rentField.getText().trim());
                } catch (NumberFormatException ex) {
                    showAlert("Rent amount must be a number");
                    return;
                }
            }

            Tenant t;
            if (editing == null) {
                t = new Tenant();
            } else {
                t = editing;
            }
            t.setFullName(nameField.getText());
            t.setPhone(phoneField.getText());
            t.setWhatsappNumber(whatsappField.getText());
            t.setEmail(emailField.getText());
            t.setEmergencyContact(emergencyContactField.getText());
            t.setEmergencyPhone(emergencyPhoneField.getText());
            t.setIdNumber(nationalIdField.getText());
            t.setSchoolId(schoolIdField.getText());
            t.setRefugeeId(refugeeIdField.getText());
            t.setRoomNumber(roomNumberField.getText());
            t.setRentAmount(rent);

            boolean ok = (editing == null) ? tenantDAO.create(t) : tenantDAO.update(t);
            if (ok) {
                AuditService.log(editing == null ? "TENANT_CREATE" : "TENANT_UPDATE",
                    (editing == null ? "Added tenant " : "Updated tenant ") + t.getFullName());
                refresh();
                resetForm(nameField, phoneField, whatsappField, emailField,
                          emergencyContactField, emergencyPhoneField,
                          nationalIdField, schoolIdField, refugeeIdField,
                          roomNumberField, rentField, addBtn, cancelEditBtn);
            }
        });

        cancelEditBtn.setOnAction(e -> resetForm(nameField, phoneField, whatsappField, emailField,
                emergencyContactField, emergencyPhoneField,
                nationalIdField, schoolIdField, refugeeIdField,
                roomNumberField, rentField, addBtn, cancelEditBtn));

        // Double-click a row to edit
        table.setRowFactory(tv -> {
            TableRow<Tenant> row = new TableRow<>();
            row.setOnMouseClicked(ev -> {
                if (ev.getClickCount() == 2 && !row.isEmpty()) {
                    editing = row.getItem();
                    nameField.setText(editing.getFullName());
                    phoneField.setText(editing.getPhone());
                    whatsappField.setText(editing.getWhatsappNumber());
                    emailField.setText(editing.getEmail());
                    emergencyContactField.setText(editing.getEmergencyContact());
                    emergencyPhoneField.setText(editing.getEmergencyPhone());
                    nationalIdField.setText(editing.getIdNumber());
                    schoolIdField.setText(editing.getSchoolId());
                    refugeeIdField.setText(editing.getRefugeeId());
                    roomNumberField.setText(editing.getRoomNumber());
                    rentField.setText(editing.getRentAmount() == null ? "" : String.valueOf(editing.getRentAmount()));
                    addBtn.setText("Update Tenant");
                    cancelEditBtn.setVisible(true);
                }
            });
            return row;
        });

        formContainer.getChildren().addAll(row1, row2);
        root.getChildren().add(formContainer);

        refresh();
        return root;
    }

    private TextField field(String prompt, double width) {
        TextField f = new TextField();
        f.setPromptText(prompt);
        f.setPrefWidth(width);
        return f;
    }

    private void resetForm(TextField nameField, TextField phoneField, TextField whatsappField,
                           TextField emailField, TextField emergencyContactField, TextField emergencyPhoneField,
                           TextField nationalIdField, TextField schoolIdField, TextField refugeeIdField,
                           TextField roomNumberField, TextField rentField,
                           Button addBtn, Button cancelEditBtn) {
        editing = null;
        nameField.clear(); phoneField.clear(); whatsappField.clear(); emailField.clear();
        emergencyContactField.clear(); emergencyPhoneField.clear();
        nationalIdField.clear(); schoolIdField.clear(); refugeeIdField.clear();
        roomNumberField.clear(); rentField.clear();
        addBtn.setText("Add Tenant");
        cancelEditBtn.setVisible(false);
    }

    private void refresh() {
        tenants.setAll(tenantDAO.getAll());
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
