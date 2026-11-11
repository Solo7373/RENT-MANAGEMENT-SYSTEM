package com.rentals.ui.payments;

import com.rentals.dao.PaymentDAO;
import com.rentals.dao.RentChargeDAO;
import com.rentals.dao.TenantDAO;
import com.rentals.payments.Payment;
import com.rentals.service.AuditService;
import com.rentals.service.ReceiptService;
import com.rentals.settings.AppSettings;
import com.rentals.tenants.Tenant;
import com.rentals.util.FormatUtils;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.nio.file.Path;

/**
 * Payments view - real payment recording and tracking.
 * Uses PaymentDAO + TenantDAO (live database data).
 */
public class PaymentsView {

    private final PaymentDAO paymentDAO = new PaymentDAO();
    private final TenantDAO tenantDAO = new TenantDAO();
    private final RentChargeDAO rentChargeDAO = new RentChargeDAO();
    private final ReceiptService receiptService = new ReceiptService();
    private final ObservableList<Payment> payments = FXCollections.observableArrayList();
    private TableView<Payment> table;
    private Label totalCollectedLabel;
    private Label thisMonthLabel;
    private Label arrearsLabel;
    private Stage stage;

    public static Pane create(Stage stage, Runnable onBack) {
        return new PaymentsView().build(stage, onBack);
    }

    private Pane build(Stage stage, Runnable onBack) {
        this.stage = stage;
        VBox root = new VBox(20);
        root.getStyleClass().add("card");
        root.setPadding(new Insets(30));
        root.setAlignment(Pos.TOP_LEFT);

        // ---------- Header ----------
        HBox header = new HBox(15);
        header.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label("💰 Payments");
        titleLabel.getStyleClass().add("label-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button backBtn = new Button("Back to Dashboard");
        backBtn.getStyleClass().add("btn-back");
        backBtn.setOnAction(e -> onBack.run());

        header.getChildren().addAll(titleLabel, spacer, backBtn);
        root.getChildren().add(header);

        // ---------- Summary cards ----------
        HBox statsRow = new HBox(20);
        statsRow.setAlignment(Pos.TOP_LEFT);

        Label totalCollected = new Label(money(paymentDAO.totalCollected()));
        Label thisMonth = new Label(money(paymentDAO.totalThisMonth()));
        Label arrears = new Label(money(rentChargeDAO.totalOutstanding()));
        totalCollectedLabel = totalCollected;
        thisMonthLabel = thisMonth;
        arrearsLabel = arrears;

        statsRow.getChildren().addAll(
            createStatCard("Total Collected", totalCollected),
            createStatCard("Collected This Month", thisMonth),
            createStatCard("Outstanding Arrears", arrears)
        );
        root.getChildren().add(statsRow);

        // ---------- Table ----------
        table = new TableView<>();
        table.setItems(payments);
        table.setPrefHeight(260);

        TableColumn<Payment, Long> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("paymentId"));
        idCol.setPrefWidth(45);

        TableColumn<Payment, String> tenantCol = new TableColumn<>("Tenant");
        tenantCol.setCellValueFactory(new PropertyValueFactory<>("tenantName"));
        tenantCol.setPrefWidth(180);

        TableColumn<Payment, Double> amountCol = new TableColumn<>("Amount (" + AppSettings.currency() + ")");
        amountCol.setCellValueFactory(new PropertyValueFactory<>("amount"));
        amountCol.setPrefWidth(130);

        TableColumn<Payment, String> methodCol = new TableColumn<>("Method");
        methodCol.setCellValueFactory(new PropertyValueFactory<>("paymentMethod"));
        methodCol.setPrefWidth(130);

        TableColumn<Payment, String> refCol = new TableColumn<>("Reference");
        refCol.setCellValueFactory(new PropertyValueFactory<>("referenceNumber"));
        refCol.setPrefWidth(140);

        TableColumn<Payment, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setPrefWidth(100);

        TableColumn<Payment, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("paymentDate"));
        dateCol.setPrefWidth(120);

        table.getColumns().addAll(idCol, tenantCol, amountCol, methodCol, refCol, statusCol, dateCol);
        root.getChildren().add(table);

        // ---------- Row actions ----------
        HBox actionsBox = new HBox(12);
        actionsBox.setAlignment(Pos.CENTER_LEFT);

        Button verifyBtn = new Button("✔ Verify Payment");
        verifyBtn.getStyleClass().add("btn-success");
        verifyBtn.setOnAction(e -> {
            Payment selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                showAlert("Select a payment first");
                return;
            }
            if ("VERIFIED".equals(selected.getStatus())) {
                showAlert("This payment is already verified");
                return;
            }
            if (paymentDAO.updateStatus(selected.getPaymentId(), "VERIFIED")) {
                // Spread the money across the tenant's oldest unpaid rent charges
                double allocated = rentChargeDAO.allocatePayment(
                    selected.getPaymentId(), selected.getTenantId(), selected.getAmount());
                AuditService.log("PAYMENT_VERIFY",
                    "Verified payment #" + selected.getPaymentId() + " (" + money(selected.getAmount()) +
                    ", allocated " + money(allocated) + ")");
                refresh();
            }
        });

        Button receiptBtn = new Button("🧾 Receipt");
        receiptBtn.getStyleClass().add("btn-secondary");
        receiptBtn.setOnAction(e -> {
            Payment selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                showAlert("Select a payment first");
                return;
            }
            try {
                Path receipt = receiptService.generate(selected);
                AuditService.log("RECEIPT_GENERATE",
                    "Generated receipt for payment #" + selected.getPaymentId());
                FormatUtils.open(receipt);
                Alert info = new Alert(Alert.AlertType.INFORMATION,
                    "Receipt saved to:\n" + receipt.toAbsolutePath(), ButtonType.OK);
                info.setHeaderText(null);
                info.initOwner(stage);
                info.showAndWait();
            } catch (Exception ex) {
                showAlert("Could not create receipt: " + ex.getMessage());
            }
        });

        Button lateFeesBtn = new Button("⏰ Apply Late Fees");
        lateFeesBtn.getStyleClass().add("btn-secondary");
        lateFeesBtn.setOnAction(e -> {
            int candidates = rentChargeDAO.countLateFeeCandidates();
            if (candidates == 0) {
                showInfo("No overdue charges need a late fee right now.");
                return;
            }
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                candidates + " overdue charge(s) will receive a " +
                AppSettings.lateFeePercent() + "% late fee.\nThis can only be applied once per charge.",
                ButtonType.YES, ButtonType.NO);
            confirm.setHeaderText(null);
            confirm.initOwner(stage);
            if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;

            RentChargeDAO.LateFeeResult result = rentChargeDAO.applyLateFees();
            AuditService.log("LATE_FEE_APPLY",
                "Applied late fees to " + result.charges() + " charge(s), total " +
                FormatUtils.money(result.totalFees()));
            refresh();
            showInfo(result.charges() == 0
                ? "No late fees were applied."
                : result.charges() + " charge(s) updated. Late fees added: " +
                  FormatUtils.money(result.totalFees()));
        });

        Button deleteBtn = new Button("🗑 Delete Payment");
        deleteBtn.getStyleClass().add("btn-danger");
        deleteBtn.setOnAction(e -> {
            Payment selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                showAlert("Select a payment first");
                return;
            }
            if (paymentDAO.delete(selected.getPaymentId())) {
                // If money had been allocated to rent charges, undo that first
                rentChargeDAO.reverseAllocations(selected.getPaymentId());
                AuditService.log("PAYMENT_DELETE", "Deleted payment #" + selected.getPaymentId());
                refresh();
            }
        });

        Button generateChargesBtn = new Button("📅 Generate This Month's Charges");
        generateChargesBtn.getStyleClass().add("btn-secondary");
        generateChargesBtn.setOnAction(e -> {
            int created = rentChargeDAO.generateForCurrentMonth();
            AuditService.log("RENT_CHARGE_GENERATE", "Generated " + created + " rent charges");
            refresh();
            Alert info = new Alert(Alert.AlertType.INFORMATION,
                created == 0
                    ? "No new charges were needed - every active tenancy already has this month's charge."
                    : created + " rent charge(s) created for the current month.",
                ButtonType.OK);
            info.setHeaderText(null);
            info.showAndWait();
        });

        actionsBox.getChildren().addAll(verifyBtn, receiptBtn, lateFeesBtn, deleteBtn, generateChargesBtn);
        root.getChildren().add(actionsBox);

        // ---------- Record payment form ----------
        VBox formContainer = new VBox(12);
        formContainer.getStyleClass().add("form-box");
        formContainer.setMaxWidth(950);

        Label formTitle = new Label("Record New Payment");
        formTitle.getStyleClass().add("label-section");

        FlowPane formRow = new FlowPane(12, 10);
        formRow.setAlignment(Pos.CENTER_LEFT);

        ComboBox<Tenant> tenantCombo = new ComboBox<>();
        tenantCombo.setPromptText("Select Tenant");
        tenantCombo.setPrefWidth(200);
        tenantCombo.getItems().addAll(tenantDAO.getAll());

        TextField amountField = new TextField();
        amountField.setPromptText("Amount (" + AppSettings.currency() + ")");
        amountField.setPrefWidth(140);

        ComboBox<String> methodCombo = new ComboBox<>();
        methodCombo.setPromptText("Payment Method");
        methodCombo.setPrefWidth(160);
        methodCombo.getItems().addAll("MTN Mobile Money", "Airtel Money", "Cash", "Bank Transfer");

        TextField refField = new TextField();
        refField.setPromptText("Reference Number");
        refField.setPrefWidth(160);

        TextField notesField = new TextField();
        notesField.setPromptText("Notes (optional)");
        notesField.setPrefWidth(180);

        Button recordBtn = new Button("Record Payment");
        recordBtn.getStyleClass().add("btn-success");
        recordBtn.setOnAction(e -> {
            if (tenantCombo.getValue() == null || amountField.getText().isEmpty() || methodCombo.getValue() == null) {
                showAlert("Please select a tenant, enter an amount and choose a payment method");
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
            Payment p = new Payment(tenantCombo.getValue().getTenantId(), amount, methodCombo.getValue());
            p.setReferenceNumber(refField.getText());
            p.setNotes(notesField.getText());
            p.setStatus("PENDING");   // admin verifies later
            if (paymentDAO.create(p)) {
                AuditService.log("PAYMENT_RECORD",
                    "Recorded payment " + money(amount) + " via " + methodCombo.getValue());
                refresh();
                amountField.clear();
                refField.clear();
                notesField.clear();
                methodCombo.setValue(null);
                tenantCombo.setValue(null);
            } else {
                showAlert("Could not save the payment - check the console for details");
            }
        });

        formRow.getChildren().addAll(tenantCombo, amountField, methodCombo, refField, notesField, recordBtn);

        formContainer.getChildren().addAll(formTitle, formRow);
        root.getChildren().add(formContainer);

        // Initial load
        refresh();

        return root;
    }

    /** Reloads payments and refreshes the summary cards. */
    private void refresh() {
        payments.setAll(paymentDAO.getAll());
        if (totalCollectedLabel != null) totalCollectedLabel.setText(money(paymentDAO.totalCollected()));
        if (thisMonthLabel != null) thisMonthLabel.setText(money(paymentDAO.totalThisMonth()));
        if (arrearsLabel != null) arrearsLabel.setText(money(rentChargeDAO.totalOutstanding()));
    }

    private VBox createStatCard(String label, Label valueLabel) {
        VBox card = new VBox(8);
        card.getStyleClass().add("stat-card");
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(18));
        card.setPrefWidth(220);

        valueLabel.getStyleClass().add("stat-value");
        valueLabel.setStyle("-fx-font-size: 22px;");

        Label labelLabel = new Label(label);
        labelLabel.getStyleClass().add("stat-label");

        card.getChildren().addAll(valueLabel, labelLabel);
        return card;
    }

    /** Formats a number using the configured currency. */
    private String money(double amount) {
        return FormatUtils.money(amount);
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
