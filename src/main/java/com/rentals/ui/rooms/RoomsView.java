package com.rentals.ui.rooms;

import com.rentals.dao.PropertyDAO;
import com.rentals.dao.RoomDAO;
import com.rentals.properties.Property;
import com.rentals.rooms.Room;
import com.rentals.service.AuditService;
import com.rentals.settings.AppSettings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Stage;

/**
 * Rooms management view - content only (returned as Pane).
 * Add, edit, delete and toggle room status using the existing RoomDAO.
 */
public class RoomsView {

    private final RoomDAO roomDAO = new RoomDAO();
    private final PropertyDAO propertyDAO = new PropertyDAO();
    private final ObservableList<Room> rooms = FXCollections.observableArrayList();
    private TableView<Room> table;
    private Room editing;

    public static Pane create(Stage stage, Runnable onBack) {
        return new RoomsView().build(onBack);
    }

    private Pane build(Runnable onBack) {
        VBox root = new VBox(20);
        root.getStyleClass().add("card");
        root.setPadding(new Insets(30));
        root.setAlignment(Pos.TOP_LEFT);

        // ---------- Header ----------
        HBox header = new HBox(15);
        header.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label("Rooms");
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
        table.setItems(rooms);
        table.setPrefHeight(320);

        TableColumn<Room, Long> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("roomId"));
        idCol.setPrefWidth(45);

        TableColumn<Room, String> numberCol = new TableColumn<>("Room #");
        numberCol.setCellValueFactory(new PropertyValueFactory<>("roomNumber"));
        numberCol.setPrefWidth(110);

        TableColumn<Room, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(new PropertyValueFactory<>("roomType"));
        typeCol.setPrefWidth(120);

        TableColumn<Room, Double> rentCol = new TableColumn<>("Rent (" + AppSettings.currency() + ")");
        rentCol.setCellValueFactory(new PropertyValueFactory<>("rentAmount"));
        rentCol.setPrefWidth(140);

        TableColumn<Room, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setPrefWidth(110);

        TableColumn<Room, String> notesCol = new TableColumn<>("Notes");
        notesCol.setCellValueFactory(new PropertyValueFactory<>("conditionNotes"));
        notesCol.setPrefWidth(200);

        table.getColumns().addAll(idCol, numberCol, typeCol, rentCol, statusCol, notesCol);
        root.getChildren().add(table);

        // ---------- Row actions ----------
        HBox actionsBox = new HBox(12);
        actionsBox.setAlignment(Pos.CENTER_LEFT);

        Button toggleStatusBtn = new Button("Toggle Occupied / Vacant");
        toggleStatusBtn.getStyleClass().add("btn-secondary");
        toggleStatusBtn.setOnAction(e -> {
            Room selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { showAlert("Select a room first"); return; }
            selected.setStatus("OCCUPIED".equalsIgnoreCase(selected.getStatus()) ? "VACANT" : "OCCUPIED");
            if (roomDAO.update(selected)) {
                AuditService.log("ROOM_STATUS", "Room " + selected.getRoomNumber() + " -> " + selected.getStatus());
                refresh();
            }
        });

        Button deleteBtn = new Button("🗑 Delete Room");
        deleteBtn.getStyleClass().add("btn-danger");
        deleteBtn.setOnAction(e -> {
            Room selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { showAlert("Select a room first"); return; }
            if (roomDAO.delete(selected.getRoomId())) {
                AuditService.log("ROOM_DELETE", "Deleted room " + selected.getRoomNumber());
                refresh();
            }
        });

        actionsBox.getChildren().addAll(toggleStatusBtn, deleteBtn);
        root.getChildren().add(actionsBox);

        // ---------- Add / edit form ----------
        VBox formContainer = new VBox(12);
        formContainer.getStyleClass().add("form-box");
        formContainer.setMaxWidth(1000);

        HBox formRow = new HBox(12);
        formRow.setAlignment(Pos.CENTER_LEFT);

        ComboBox<Property> propertyCombo = new ComboBox<>();
        propertyCombo.setPromptText("Select Property");
        propertyCombo.setPrefWidth(180);
        propertyCombo.getItems().addAll(propertyDAO.getAll());

        TextField numberField = new TextField();
        numberField.setPromptText("Room Number");
        numberField.setPrefWidth(120);

        TextField typeField = new TextField();
        typeField.setPromptText("Room Type");
        typeField.setPrefWidth(120);

        TextField rentField = new TextField();
        rentField.setPromptText("Rent (" + AppSettings.currency() + ")");
        rentField.setPrefWidth(120);

        TextField notesField = new TextField();
        notesField.setPromptText("Condition notes");
        notesField.setPrefWidth(180);

        Button addBtn = new Button("Add Room");
        addBtn.getStyleClass().add("btn-success");

        Button cancelEditBtn = new Button("Cancel");
        cancelEditBtn.getStyleClass().add("btn-secondary");
        cancelEditBtn.setVisible(false);

        addBtn.setOnAction(e -> {
            if (propertyCombo.getValue() == null || numberField.getText().isEmpty() || rentField.getText().isEmpty()) {
                showAlert("Please select a property, room number and rent");
                return;
            }
            double rent;
            try {
                rent = Double.parseDouble(rentField.getText().trim());
            } catch (NumberFormatException ex) {
                showAlert("Rent amount must be a number");
                return;
            }
            if (editing == null) {
                Room r = new Room(propertyCombo.getValue().getPropertyId(), numberField.getText(),
                    typeField.getText(), rent);
                r.setConditionNotes(notesField.getText());
                if (roomDAO.create(r)) {
                    AuditService.log("ROOM_CREATE", "Added room " + r.getRoomNumber());
                    refresh();
                    clearForm(numberField, typeField, rentField, notesField);
                }
            } else {
                editing.setPropertyId(propertyCombo.getValue().getPropertyId());
                editing.setRoomNumber(numberField.getText());
                editing.setRoomType(typeField.getText());
                editing.setRentAmount(rent);
                editing.setConditionNotes(notesField.getText());
                if (roomDAO.update(editing)) {
                    AuditService.log("ROOM_UPDATE", "Updated room " + editing.getRoomNumber());
                    refresh();
                    resetForm(numberField, typeField, rentField, notesField, addBtn, cancelEditBtn);
                }
            }
        });

        cancelEditBtn.setOnAction(e -> resetForm(numberField, typeField, rentField, notesField, addBtn, cancelEditBtn));

        // Double-click a row to edit
        table.setRowFactory(tv -> {
            TableRow<Room> row = new TableRow<>();
            row.setOnMouseClicked(ev -> {
                if (ev.getClickCount() == 2 && !row.isEmpty()) {
                    editing = row.getItem();
                    numberField.setText(editing.getRoomNumber());
                    typeField.setText(editing.getRoomType());
                    rentField.setText(String.valueOf(editing.getRentAmount()));
                    notesField.setText(editing.getConditionNotes());
                    addBtn.setText("Update Room");
                    cancelEditBtn.setVisible(true);
                }
            });
            return row;
        });

        formRow.getChildren().addAll(propertyCombo, numberField, typeField, rentField, notesField, addBtn, cancelEditBtn);
        formContainer.getChildren().add(formRow);
        root.getChildren().add(formContainer);

        refresh();
        return root;
    }

    private void clearForm(TextField numberField, TextField typeField, TextField rentField, TextField notesField) {
        numberField.clear();
        typeField.clear();
        rentField.clear();
        notesField.clear();
    }

    private void resetForm(TextField numberField, TextField typeField, TextField rentField,
                           TextField notesField, Button addBtn, Button cancelEditBtn) {
        editing = null;
        clearForm(numberField, typeField, rentField, notesField);
        addBtn.setText("Add Room");
        cancelEditBtn.setVisible(false);
    }

    private void refresh() {
        rooms.setAll(roomDAO.getAll());
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
