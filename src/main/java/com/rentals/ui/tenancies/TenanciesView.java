package com.rentals.ui.tenancies;

import com.rentals.dao.RoomDAO;
import com.rentals.dao.TenantDAO;
import com.rentals.dao.TenancyDAO;
import com.rentals.rooms.Room;
import com.rentals.service.AuditService;
import com.rentals.settings.AppSettings;
import com.rentals.tenants.Tenant;
import com.rentals.tenancies.Tenancy;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Stage;

/**
 * Tenancies management view - content only (returned as Pane).
 * Creating a tenancy marks the room OCCUPIED; terminating frees it.
 */
public class TenanciesView {

    private final TenancyDAO tenancyDAO = new TenancyDAO();
    private final TenantDAO tenantDAO = new TenantDAO();
    private final RoomDAO roomDAO = new RoomDAO();
    private final ObservableList<Tenancy> tenancies = FXCollections.observableArrayList();
    private TableView<Tenancy> table;

    public static Pane create(Stage stage, Runnable onBack) {
        return new TenanciesView().build(onBack);
    }

    private Pane build(Runnable onBack) {
        VBox root = new VBox(20);
        root.getStyleClass().add("card");
        root.setPadding(new Insets(30));
        root.setAlignment(Pos.TOP_LEFT);

        // ---------- Header ----------
        HBox header = new HBox(15);
        header.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label("Tenancies");
        titleLabel.getStyleClass().add("label-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button backBtn = new Button("Back to Dashboard");
        backBtn.getStyleClass().add("btn-back");
        backBtn.setOnAction(e -> onBack.run());

        header.getChildren().addAll(titleLabel, spacer, backBtn);
        root.getChildren().add(header);

        // ---------- Table ----------
        table = new TableView<>();
        table.setItems(tenancies);
        table.setPrefHeight(320);

        TableColumn<Tenancy, Long> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("tenancyId"));
        idCol.setPrefWidth(45);

        TableColumn<Tenancy, String> tenantCol = new TableColumn<>("Tenant");
        tenantCol.setCellValueFactory(new PropertyValueFactory<>("tenantName"));
        tenantCol.setPrefWidth(190);

        TableColumn<Tenancy, String> roomCol = new TableColumn<>("Room");
        roomCol.setCellValueFactory(new PropertyValueFactory<>("roomNumber"));
        roomCol.setPrefWidth(90);

        TableColumn<Tenancy, String> startCol = new TableColumn<>("Start Date");
        startCol.setCellValueFactory(new PropertyValueFactory<>("startDate"));
        startCol.setPrefWidth(120);

        TableColumn<Tenancy, String> endCol = new TableColumn<>("End Date");
        endCol.setCellValueFactory(new PropertyValueFactory<>("endDate"));
        endCol.setPrefWidth(120);

        TableColumn<Tenancy, Double> rentCol = new TableColumn<>("Monthly Rent");
        rentCol.setCellValueFactory(new PropertyValueFactory<>("monthlyRent"));
        rentCol.setPrefWidth(130);

        TableColumn<Tenancy, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setPrefWidth(100);

        table.getColumns().addAll(idCol, tenantCol, roomCol, startCol, endCol, rentCol, statusCol);
        root.getChildren().add(table);

        // ---------- Row actions ----------
        HBox actionsBox = new HBox(12);
        actionsBox.setAlignment(Pos.CENTER_LEFT);

        Button terminateBtn = new Button("⏹ Terminate Tenancy");
        terminateBtn.getStyleClass().add("btn-danger");
        terminateBtn.setOnAction(e -> {
            Tenancy selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { showAlert("Select a tenancy first"); return; }
            if ("ENDED".equalsIgnoreCase(selected.getStatus())) {
                showAlert("This tenancy has already ended");
                return;
            }
            if (tenancyDAO.terminate(selected.getTenancyId(), selected.getRoomId())) {
                AuditService.log("TENANCY_TERMINATE",
                    "Terminated tenancy #" + selected.getTenancyId() + " (" + selected.getTenantName() + ")");
                refresh();
            }
        });

        actionsBox.getChildren().add(terminateBtn);
        root.getChildren().add(actionsBox);

        // ---------- Add form ----------
        HBox formBox = new HBox(15);
        formBox.setAlignment(Pos.CENTER_LEFT);
        formBox.getStyleClass().add("form-box");
        formBox.setMaxWidth(900);

        ComboBox<Tenant> tenantCombo = new ComboBox<>();
        tenantCombo.setPromptText("Select Tenant");
        tenantCombo.setPrefWidth(200);
        tenantCombo.getItems().addAll(tenantDAO.getAll());

        ComboBox<Room> roomCombo = new ComboBox<>();
        roomCombo.setPromptText("Select Vacant Room");
        roomCombo.setPrefWidth(200);
        roomCombo.getItems().addAll(roomDAO.getVacant());

        TextField rentField = new TextField();
        rentField.setPromptText("Monthly Rent (" + AppSettings.currency() + ")");
        rentField.setPrefWidth(170);

        TextField depositField = new TextField();
        depositField.setPromptText("Deposit (" + AppSettings.currency() + ")");
        depositField.setPrefWidth(130);

        // Auto-fill rent when a room is chosen
        roomCombo.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) rentField.setText(String.valueOf(newV.getRentAmount()));
        });

        Button addBtn = new Button("Create Tenancy");
        addBtn.getStyleClass().add("btn-success");
        addBtn.setOnAction(e -> {
            if (tenantCombo.getValue() == null || roomCombo.getValue() == null || rentField.getText().isEmpty()) {
                showAlert("Please select a tenant, room and enter the rent");
                return;
            }
            double rent;
            try {
                rent = Double.parseDouble(rentField.getText().trim());
            } catch (NumberFormatException ex) {
                showAlert("Rent must be a number");
                return;
            }
            Tenancy t = new Tenancy(tenantCombo.getValue().getTenantId(),
                roomCombo.getValue().getRoomId(),
                java.time.LocalDate.now().toString(), rent);
            if (!depositField.getText().isEmpty()) {
                try {
                    t.setDeposit(Double.parseDouble(depositField.getText().trim()));
                } catch (NumberFormatException ex) {
                    showAlert("Deposit must be a number");
                    return;
                }
            }
            if (tenancyDAO.create(t)) {
                AuditService.log("TENANCY_CREATE",
                    "Created tenancy for " + tenantCombo.getValue().getFullName() +
                    " in room " + roomCombo.getValue().getRoomNumber());
                refresh();
                // Reload vacant rooms (the chosen room is now occupied)
                roomCombo.getItems().setAll(roomDAO.getVacant());
                rentField.clear();
                depositField.clear();
                tenantCombo.setValue(null);
                roomCombo.setValue(null);
            } else {
                showAlert("Could not create the tenancy - check the console for details");
            }
        });

        formBox.getChildren().addAll(tenantCombo, roomCombo, rentField, depositField, addBtn);
        root.getChildren().add(formBox);

        refresh();
        return root;
    }

    private void refresh() {
        tenancies.setAll(tenancyDAO.getAll());
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
