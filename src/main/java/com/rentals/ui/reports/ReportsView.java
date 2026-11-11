package com.rentals.ui.reports;

import com.rentals.dao.RentChargeDAO;
import com.rentals.dao.ReportDAO;
import com.rentals.rent.RentCharge;
import com.rentals.service.AuditService;
import com.rentals.service.ReportExportService;
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

import java.nio.file.Path;
import java.util.List;

/**
 * Reports view - live reports calculated from the database.
 * Shows revenue, occupancy and arrears statistics.
 */
public class ReportsView {

    private final ReportDAO reportDAO = new ReportDAO();
    private final RentChargeDAO rentChargeDAO = new RentChargeDAO();
    private final ReportExportService exportService = new ReportExportService();
    private Stage stage;

    public static Pane create(Stage stage, Runnable onBack) {
        return new ReportsView().build(stage, onBack);
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

        Label titleLabel = new Label("📊 Reports");
        titleLabel.getStyleClass().add("label-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button refreshBtn = new Button("🔄 Refresh");
        refreshBtn.getStyleClass().add("btn-primary");

        Button backBtn = new Button("Back to Dashboard");
        backBtn.getStyleClass().add("btn-back");
        backBtn.setOnAction(e -> onBack.run());

        header.getChildren().addAll(titleLabel, spacer, refreshBtn, backBtn);
        root.getChildren().add(header);

        // ---------- Export bar ----------
        ComboBox<ReportExportService.ReportType> reportBox = new ComboBox<>();
        reportBox.getItems().addAll(ReportExportService.ReportType.values());
        reportBox.setValue(ReportExportService.ReportType.SUMMARY);
        reportBox.setPrefWidth(230);

        Button csvBtn = new Button("📄 Export CSV");
        csvBtn.getStyleClass().add("btn-secondary");
        csvBtn.setOnAction(e -> export(reportBox.getValue(), true));

        Button pdfBtn = new Button("🖨 Export PDF");
        pdfBtn.getStyleClass().add("btn-primary");
        pdfBtn.setOnAction(e -> export(reportBox.getValue(), false));

        Label exportLabel = new Label("Export:");
        exportLabel.getStyleClass().add("stat-label");

        HBox exportBar = new HBox(10, exportLabel, reportBox, csvBtn, pdfBtn);
        exportBar.setAlignment(Pos.CENTER_LEFT);
        root.getChildren().add(exportBar);

        // ---------- Content container (refreshed on demand) ----------
        VBox content = new VBox(20);
        root.getChildren().add(content);

        Runnable load = () -> content.getChildren().setAll(buildReportContent());
        refreshBtn.setOnAction(e -> load.run());
        load.run();

        return root;
    }

    /** Builds all report sections from live data. */
    private VBox buildReportContent() {
        VBox container = new VBox(20);

        // ---------- Summary stat cards ----------
        HBox statsRow = new HBox(18);
        statsRow.getChildren().addAll(
            statCard("Total Revenue", money(reportDAO.totalRevenue()), "💰"),
            statCard("Revenue This Month", money(reportDAO.revenueThisMonth()), "📅"),
            statCard("Occupancy Rate", String.format("%.0f%%", reportDAO.occupancyRate()), "🏠"),
            statCard("Outstanding Arrears", money(reportDAO.arrears()), "⚠️")
        );
        container.getChildren().add(statsRow);

        // ---------- Second row: portfolio stats ----------
        HBox statsRow2 = new HBox(18);
        statsRow2.getChildren().addAll(
            statCard("Properties", String.valueOf(reportDAO.totalProperties()), "🏢"),
            statCard("Rooms (Occupied / Total)", reportDAO.occupiedRooms() + " / " + reportDAO.totalRooms(), "🛏️"),
            statCard("Vacant Rooms", String.valueOf(reportDAO.vacantRooms()), "🔑"),
            statCard("Active Tenancies", String.valueOf(reportDAO.activeTenancies()), "📄")
        );
        container.getChildren().add(statsRow2);

        // ---------- Third row: expenses / net ----------
        double revenue = reportDAO.totalRevenue();
        double expenses = reportDAO.totalExpenses();
        HBox statsRow3 = new HBox(18);
        statsRow3.getChildren().addAll(
            statCard("Total Expenses", money(expenses), "💸"),
            statCard("Net Income", money(revenue - expenses), "📈"),
            statCard("Expected Monthly Rent", money(reportDAO.expectedMonthlyRent()), "📆")
        );
        container.getChildren().add(statsRow3);

        // ---------- Property summary table ----------
        Label propertyTitle = new Label("Property Occupancy Summary");
        propertyTitle.getStyleClass().add("label-section");
        container.getChildren().add(propertyTitle);

        TableView<Object[]> propertyTable = new TableView<>();
        propertyTable.setPrefHeight(180);
        propertyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Object[], String> nameCol = new TableColumn<>("Property");
        nameCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty((String) data.getValue()[0]));

        TableColumn<Object[], Integer> totalCol = new TableColumn<>("Total Rooms");
        totalCol.setCellValueFactory(data -> new javafx.beans.property.SimpleIntegerProperty((Integer) data.getValue()[1]).asObject());

        TableColumn<Object[], Integer> occCol = new TableColumn<>("Occupied");
        occCol.setCellValueFactory(data -> new javafx.beans.property.SimpleIntegerProperty((Integer) data.getValue()[2]).asObject());

        TableColumn<Object[], Integer> vacCol = new TableColumn<>("Vacant");
        vacCol.setCellValueFactory(data -> new javafx.beans.property.SimpleIntegerProperty((Integer) data.getValue()[3]).asObject());

        propertyTable.getColumns().addAll(nameCol, totalCol, occCol, vacCol);
        propertyTable.getItems().setAll(reportDAO.propertySummary());
        container.getChildren().add(propertyTable);

        // ---------- Outstanding rent charges table ----------
        Label chargesTitle = new Label("Outstanding Rent Charges");
        chargesTitle.getStyleClass().add("label-section");
        container.getChildren().add(chargesTitle);

        TableView<RentCharge> chargesTable = new TableView<>();
        chargesTable.setPrefHeight(170);
        chargesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<RentCharge, String> tenantCol = new TableColumn<>("Tenant");
        tenantCol.setCellValueFactory(new PropertyValueFactory<>("tenantName"));

        TableColumn<RentCharge, String> roomCol = new TableColumn<>("Room");
        roomCol.setCellValueFactory(new PropertyValueFactory<>("roomNumber"));

        TableColumn<RentCharge, String> periodCol = new TableColumn<>("Period");
        periodCol.setCellValueFactory(new PropertyValueFactory<>("periodMonth"));

        TableColumn<RentCharge, Double> amountCol = new TableColumn<>("Amount");
        amountCol.setCellValueFactory(new PropertyValueFactory<>("amount"));

        TableColumn<RentCharge, Double> paidCol = new TableColumn<>("Paid");
        paidCol.setCellValueFactory(new PropertyValueFactory<>("paidAmount"));

        TableColumn<RentCharge, Double> balanceCol = new TableColumn<>("Balance");
        balanceCol.setCellValueFactory(new PropertyValueFactory<>("balance"));

        TableColumn<RentCharge, String> dueCol = new TableColumn<>("Due Date");
        dueCol.setCellValueFactory(new PropertyValueFactory<>("dueDate"));

        TableColumn<RentCharge, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));

        chargesTable.getColumns().addAll(tenantCol, roomCol, periodCol, amountCol,
                                          paidCol, balanceCol, dueCol, statusCol);
        chargesTable.getItems().setAll(rentChargeDAO.getOutstanding());
        container.getChildren().add(chargesTable);

        // ---------- Monthly revenue table ----------
        Label revenueTitle = new Label("Monthly Revenue (Last 6 Months)");
        revenueTitle.getStyleClass().add("label-section");
        container.getChildren().add(revenueTitle);

        TableView<Object[]> revenueTable = new TableView<>();
        revenueTable.setPrefHeight(180);
        revenueTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Object[], String> monthCol = new TableColumn<>("Month");
        monthCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty((String) data.getValue()[0]));

        TableColumn<Object[], String> collectedCol = new TableColumn<>("Collected");
        collectedCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
            money((Double) data.getValue()[1])));

        revenueTable.getColumns().addAll(monthCol, collectedCol);

        List<Object[]> monthly = reportDAO.monthlyRevenue();
        revenueTable.getItems().setAll(monthly);
        container.getChildren().add(revenueTable);

        return container;
    }

    private VBox statCard(String label, String value, String icon) {
        VBox card = new VBox(6);
        card.getStyleClass().add("stat-card");
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(16));
        card.setPrefWidth(200);

        Label iconLabel = new Label(icon);
        iconLabel.setStyle("-fx-font-size: 20px;");

        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("stat-value");
        valueLabel.setStyle("-fx-font-size: 20px;");

        Label labelLabel = new Label(label);
        labelLabel.getStyleClass().add("stat-label");

        card.getChildren().addAll(iconLabel, valueLabel, labelLabel);
        return card;
    }

    private String money(double amount) {
        return FormatUtils.money(amount);
    }

    private void export(ReportExportService.ReportType type, boolean csv) {
        if (type == null) {
            showAlert("Please choose a report to export.");
            return;
        }
        try {
            Path file = csv ? exportService.exportCsv(type) : exportService.exportPdf(type);
            AuditService.log("REPORT_EXPORT",
                "Exported " + type.title() + " as " + (csv ? "CSV" : "PDF"));
            FormatUtils.open(file);

            Alert info = new Alert(Alert.AlertType.INFORMATION,
                type.title() + " exported to:\n" + file.toAbsolutePath(), ButtonType.OK);
            info.setHeaderText(null);
            if (stage != null) info.initOwner(stage);
            info.showAndWait();
        } catch (Exception ex) {
            showAlert("Export failed: " + ex.getMessage());
        }
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message);
        alert.setHeaderText(null);
        if (stage != null) alert.initOwner(stage);
        alert.showAndWait();
    }
}
