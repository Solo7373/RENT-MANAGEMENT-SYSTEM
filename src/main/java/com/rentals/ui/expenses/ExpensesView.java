package com.rentals.ui.expenses;

import com.rentals.dao.ExpenseDAO;
import com.rentals.dao.PropertyDAO;
import com.rentals.expenses.Expense;
import com.rentals.properties.Property;
import com.rentals.settings.AppSettings;
import com.rentals.util.FormatUtils;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Stage;

/**
 * Expenses view - record and track property expenses.
 */
public class ExpensesView {

    private final ExpenseDAO expenseDAO = new ExpenseDAO();
    private final PropertyDAO propertyDAO = new PropertyDAO();
    private final ObservableList<Expense> expenses = FXCollections.observableArrayList();
    private TableView<Expense> table;
    private Label totalLabel;

    public static Pane create(Stage stage, Runnable onBack) {
        return new ExpensesView().build(onBack);
    }

    private Pane build(Runnable onBack) {
        VBox root = new VBox(20);
        root.getStyleClass().add("card");
        root.setPadding(new Insets(30));
        root.setAlignment(Pos.TOP_LEFT);

        // ---------- Header ----------
        HBox header = new HBox(15);
        header.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label("💸 Expenses");
        titleLabel.getStyleClass().add("label-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button backBtn = new Button("Back to Dashboard");
        backBtn.getStyleClass().add("btn-back");
        backBtn.setOnAction(e -> onBack.run());

        header.getChildren().addAll(titleLabel, spacer, backBtn);
        root.getChildren().add(header);

        // ---------- Total card ----------
        VBox totalCard = new VBox(8);
        totalCard.getStyleClass().add("stat-card");
        totalCard.setAlignment(Pos.CENTER_LEFT);
        totalCard.setPadding(new Insets(18));
        totalCard.setPrefWidth(240);

        totalLabel = new Label(money(expenseDAO.total()));
        totalLabel.getStyleClass().add("stat-value");
        totalLabel.setStyle("-fx-font-size: 22px;");

        Label totalText = new Label("Total Expenses");
        totalText.getStyleClass().add("stat-label");

        totalCard.getChildren().addAll(totalLabel, totalText);
        root.getChildren().add(totalCard);

        // ---------- Table ----------
        table = new TableView<>();
        table.setItems(expenses);
        table.setPrefHeight(320);

        TableColumn<Expense, Long> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("expenseId"));
        idCol.setPrefWidth(45);

        TableColumn<Expense, String> propertyCol = new TableColumn<>("Property");
        propertyCol.setCellValueFactory(new PropertyValueFactory<>("propertyName"));
        propertyCol.setPrefWidth(170);

        TableColumn<Expense, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(new PropertyValueFactory<>("description"));
        descCol.setPrefWidth(280);

        TableColumn<Expense, String> categoryCol = new TableColumn<>("Category");
        categoryCol.setCellValueFactory(new PropertyValueFactory<>("category"));
        categoryCol.setPrefWidth(130);

        TableColumn<Expense, Double> amountCol = new TableColumn<>("Amount (" + AppSettings.currency() + ")");
        amountCol.setCellValueFactory(new PropertyValueFactory<>("amount"));
        amountCol.setPrefWidth(140);

        TableColumn<Expense, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("expenseDate"));
        dateCol.setPrefWidth(120);

        table.getColumns().addAll(idCol, propertyCol, descCol, categoryCol, amountCol, dateCol);
        root.getChildren().add(table);

        // ---------- Row actions ----------
        HBox actionsBox = new HBox(12);
        actionsBox.setAlignment(Pos.CENTER_LEFT);

        Button deleteBtn = new Button("🗑 Delete Expense");
        deleteBtn.getStyleClass().add("btn-danger");
        deleteBtn.setOnAction(e -> {
            Expense selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { showAlert("Select an expense first"); return; }
            if (expenseDAO.delete(selected.getExpenseId())) refresh();
        });

        actionsBox.getChildren().add(deleteBtn);
        root.getChildren().add(actionsBox);

        // ---------- Add form ----------
        HBox formBox = new HBox(12);
        formBox.setAlignment(Pos.CENTER_LEFT);
        formBox.getStyleClass().add("form-box");
        formBox.setMaxWidth(1050);

        ComboBox<Property> propertyCombo = new ComboBox<>();
        propertyCombo.setPromptText("Select Property");
        propertyCombo.setPrefWidth(180);
        propertyCombo.getItems().addAll(propertyDAO.getAll());

        TextField descField = new TextField();
        descField.setPromptText("Description");
        descField.setPrefWidth(260);

        ComboBox<String> categoryCombo = new ComboBox<>();
        categoryCombo.getItems().addAll("Repairs", "Utilities", "Maintenance", "Supplies", "Taxes", "Other");
        categoryCombo.setPromptText("Category");
        categoryCombo.setPrefWidth(130);

        TextField amountField = new TextField();
        amountField.setPromptText("Amount (" + AppSettings.currency() + ")");
        amountField.setPrefWidth(130);

        Button addBtn = new Button("Add Expense");
        addBtn.getStyleClass().add("btn-success");
        addBtn.setOnAction(e -> {
            if (descField.getText().isEmpty() || amountField.getText().isEmpty()) {
                showAlert("Please enter a description and amount");
                return;
            }
            double amount;
            try {
                amount = Double.parseDouble(amountField.getText().trim());
                if (amount <= 0) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                showAlert("Amount must be a positive number");
                return;
            }
            Long propertyId = propertyCombo.getValue() == null ? null : propertyCombo.getValue().getPropertyId();
            Expense ex = new Expense(propertyId, descField.getText(), amount,
                categoryCombo.getValue() == null ? "Other" : categoryCombo.getValue());
            if (expenseDAO.create(ex)) {
                refresh();
                descField.clear();
                amountField.clear();
                categoryCombo.setValue(null);
                propertyCombo.setValue(null);
            }
        });

        formBox.getChildren().addAll(propertyCombo, descField, categoryCombo, amountField, addBtn);
        root.getChildren().add(formBox);

        refresh();
        return root;
    }

    private void refresh() {
        expenses.setAll(expenseDAO.getAll());
        if (totalLabel != null) totalLabel.setText(money(expenseDAO.total()));
    }

    /** Formats a number using the configured currency. */
    private String money(double amount) {
        return FormatUtils.money(amount);
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
