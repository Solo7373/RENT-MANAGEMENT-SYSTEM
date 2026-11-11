package com.rentals.ui.properties;

import com.rentals.dao.PropertyDAO;
import com.rentals.properties.Property;
import com.rentals.service.AuditService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Stage;

/**
 * Properties management view - content only (returned as Pane).
 * Add, edit and delete properties using the existing PropertyDAO.
 */
public class PropertiesView {

    private final PropertyDAO propertyDAO = new PropertyDAO();
    private final ObservableList<Property> properties = FXCollections.observableArrayList();
    private TableView<Property> table;
    private Property editing;   // null = adding, non-null = updating

    public static Pane create(Stage stage, Runnable onBack) {
        return new PropertiesView().build(onBack);
    }

    private Pane build(Runnable onBack) {
        VBox root = new VBox(20);
        root.getStyleClass().add("card");
        root.setPadding(new Insets(30));
        root.setAlignment(Pos.TOP_LEFT);

        // ---------- Header ----------
        HBox header = new HBox(15);
        header.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label("Properties");
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
        table.setItems(properties);
        table.setPrefHeight(330);

        TableColumn<Property, Long> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("propertyId"));
        idCol.setPrefWidth(50);

        TableColumn<Property, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(250);

        TableColumn<Property, String> addressCol = new TableColumn<>("Address");
        addressCol.setCellValueFactory(new PropertyValueFactory<>("address"));
        addressCol.setPrefWidth(380);

        TableColumn<Property, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setPrefWidth(120);

        table.getColumns().addAll(idCol, nameCol, addressCol, statusCol);
        root.getChildren().add(table);

        // ---------- Row actions ----------
        HBox actionsBox = new HBox(12);
        actionsBox.setAlignment(Pos.CENTER_LEFT);

        Button deleteBtn = new Button("🗑 Delete Property");
        deleteBtn.getStyleClass().add("btn-danger");
        deleteBtn.setOnAction(e -> {
            Property selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { showAlert("Select a property first"); return; }
            if (propertyDAO.delete(selected.getPropertyId())) {
                AuditService.log("PROPERTY_DELETE", "Deleted property " + selected.getName());
                refresh();
            }
        });

        actionsBox.getChildren().add(deleteBtn);
        root.getChildren().add(actionsBox);

        // ---------- Add / edit form ----------
        HBox formBox = new HBox(15);
        formBox.setAlignment(Pos.CENTER_LEFT);
        formBox.getStyleClass().add("form-box");
        formBox.setMaxWidth(800);

        TextField nameField = new TextField();
        nameField.setPromptText("Property Name");
        nameField.setPrefWidth(200);

        TextField addressField = new TextField();
        addressField.setPromptText("Address");
        addressField.setPrefWidth(300);

        Button addBtn = new Button("Add Property");
        addBtn.getStyleClass().add("btn-success");

        Button cancelEditBtn = new Button("Cancel");
        cancelEditBtn.getStyleClass().add("btn-secondary");
        cancelEditBtn.setVisible(false);

        addBtn.setOnAction(e -> {
            if (nameField.getText().isEmpty()) {
                showAlert("Please enter a property name");
                return;
            }
            if (editing == null) {
                Property p = new Property(nameField.getText(), addressField.getText(), "");
                if (propertyDAO.create(p)) {
                    AuditService.log("PROPERTY_CREATE", "Added property " + p.getName());
                    refresh();
                    nameField.clear();
                    addressField.clear();
                }
            } else {
                editing.setName(nameField.getText());
                editing.setAddress(addressField.getText());
                if (propertyDAO.update(editing)) {
                    AuditService.log("PROPERTY_UPDATE", "Updated property " + editing.getName());
                    refresh();
                    resetForm(nameField, addressField, addBtn, cancelEditBtn);
                }
            }
        });

        cancelEditBtn.setOnAction(e -> resetForm(nameField, addressField, addBtn, cancelEditBtn));

        // Double-click a row to edit it
        table.setRowFactory(tv -> {
            TableRow<Property> row = new TableRow<>();
            row.setOnMouseClicked(ev -> {
                if (ev.getClickCount() == 2 && !row.isEmpty()) {
                    editing = row.getItem();
                    nameField.setText(editing.getName());
                    addressField.setText(editing.getAddress());
                    addBtn.setText("Update Property");
                    cancelEditBtn.setVisible(true);
                }
            });
            return row;
        });

        formBox.getChildren().addAll(nameField, addressField, addBtn, cancelEditBtn);
        root.getChildren().add(formBox);

        refresh();
        return root;
    }

    private void resetForm(TextField nameField, TextField addressField, Button addBtn, Button cancelEditBtn) {
        editing = null;
        nameField.clear();
        addressField.clear();
        addBtn.setText("Add Property");
        cancelEditBtn.setVisible(false);
    }

    private void refresh() {
        properties.setAll(propertyDAO.getAll());
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
