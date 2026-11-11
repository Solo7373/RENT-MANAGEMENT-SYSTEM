package com.rentals.ui.dashboard;

import com.rentals.dao.PaymentDAO;
import com.rentals.dao.PropertyDAO;
import com.rentals.dao.ReportDAO;
import com.rentals.dao.RoomDAO;
import com.rentals.dao.TenantDAO;
import com.rentals.util.FormatUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

import java.util.function.Consumer;

/**
 * Dashboard content view - shows live stats cards and quick actions.
 * This is the first screen shown after login.
 * Returns a Pane to be displayed in MainLayout's content area.
 */
public class DashboardView {

    private final PropertyDAO propertyDAO = new PropertyDAO();
    private final RoomDAO roomDAO = new RoomDAO();
    private final TenantDAO tenantDAO = new TenantDAO();
    private final PaymentDAO paymentDAO = new PaymentDAO();
    private final ReportDAO reportDAO = new ReportDAO();

    /**
     * Creates the dashboard pane. Called by MainLayout.
     * @param navigate callback that receives a module name (e.g. "Tenants")
     */
    public static Pane create(Consumer<String> navigate) {
        return new DashboardView().build(navigate);
    }

    private Pane build(Consumer<String> navigate) {
        VBox root = new VBox(22);
        root.getStyleClass().add("card");
        root.setPadding(new Insets(30));
        root.setAlignment(Pos.TOP_LEFT);

        // ---------- Header ----------
        Label titleLabel = new Label("Dashboard");
        titleLabel.getStyleClass().add("label-title");

        Label subtitleLabel = new Label("Welcome back, Admin — here's an overview of your rental management system");
        subtitleLabel.getStyleClass().add("label-subtitle");

        root.getChildren().addAll(titleLabel, subtitleLabel);

        // ---------- Stats cards (live data) ----------
        HBox statsRow = new HBox(18);
        statsRow.setAlignment(Pos.TOP_LEFT);
        statsRow.getChildren().addAll(
            createStatCard("🏢  Properties", String.valueOf(propertyDAO.getAll().size())),
            createStatCard("🛏️  Rooms", String.valueOf(roomDAO.getAll().size())),
            createStatCard("👥  Tenants", String.valueOf(tenantDAO.getAll().size())),
            createStatCard("📄  Active Tenancies", String.valueOf(reportDAO.activeTenancies()))
        );
        root.getChildren().add(statsRow);

        // ---------- Money cards (live data) ----------
        HBox moneyRow = new HBox(18);
        moneyRow.setAlignment(Pos.TOP_LEFT);
        moneyRow.getChildren().addAll(
            createMoneyCard("💰  Total Collected", money(paymentDAO.totalCollected())),
            createMoneyCard("📅  Collected This Month", money(paymentDAO.totalThisMonth())),
            createMoneyCard("⚠️  Outstanding Arrears", money(reportDAO.arrears()))
        );
        root.getChildren().add(moneyRow);

        // ---------- Quick actions ----------
        Label actionsTitle = new Label("Quick Actions");
        actionsTitle.getStyleClass().add("label-section");
        root.getChildren().add(actionsTitle);

        FlowPane actionsRow = new FlowPane(15, 12);
        actionsRow.setAlignment(Pos.TOP_LEFT);

        Button addTenantBtn = quickButton("➕ Add Tenant", "btn-primary", () -> navigate.accept("Tenants"));
        Button addRoomBtn = quickButton("🛏️  Add Room", "btn-primary", () -> navigate.accept("Rooms"));
        Button addPropertyBtn = quickButton("🏢  Add Property", "btn-primary", () -> navigate.accept("Properties"));
        Button recordPaymentBtn = quickButton("💰  Record Payment", "btn-success", () -> navigate.accept("Payments"));
        Button expensesBtn = quickButton("💸  Expenses", "btn-secondary", () -> navigate.accept("Expenses"));
        Button viewReportsBtn = quickButton("📊  View Reports", "btn-secondary", () -> navigate.accept("Reports"));

        actionsRow.getChildren().addAll(addTenantBtn, addRoomBtn, addPropertyBtn, recordPaymentBtn, expensesBtn, viewReportsBtn);
        root.getChildren().add(actionsRow);

        // ---------- Help text ----------
        Label infoLabel = new Label("Use the hamburger menu (☰) to navigate between modules.\n" +
            "Keyboard shortcuts: P=Properties, R=Rooms, T=Tenants, A=Tenancies, M=Payments, B=Reports, E=Reminders, S=Settings");
        infoLabel.getStyleClass().add("label-subtitle");
        infoLabel.setWrapText(true);
        infoLabel.setMaxWidth(750);
        root.getChildren().add(infoLabel);

        return root;
    }

    private VBox createStatCard(String label, String value) {
        VBox card = new VBox(8);
        card.getStyleClass().add("stat-card");
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(18));
        card.setPrefWidth(200);

        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("stat-value");

        Label labelLabel = new Label(label);
        labelLabel.getStyleClass().add("stat-label");

        card.getChildren().addAll(valueLabel, labelLabel);
        return card;
    }

    private VBox createMoneyCard(String label, String value) {
        VBox card = new VBox(8);
        card.getStyleClass().add("stat-card");
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(18));
        card.setPrefWidth(240);

        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("stat-value");
        valueLabel.setStyle("-fx-font-size: 20px;");

        Label labelLabel = new Label(label);
        labelLabel.getStyleClass().add("stat-label");

        card.getChildren().addAll(valueLabel, labelLabel);
        return card;
    }

    private Button quickButton(String text, String styleClass, Runnable action) {
        Button btn = new Button(text);
        btn.getStyleClass().add(styleClass);
        btn.setPrefWidth(185);
        btn.setMinWidth(Region.USE_PREF_SIZE);
        btn.setPrefHeight(45);
        btn.setOnAction(e -> action.run());
        return btn;
    }

    private String money(double amount) {
        return FormatUtils.money(amount);
    }
}
