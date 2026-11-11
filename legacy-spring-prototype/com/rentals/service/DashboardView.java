package com.rentals.service;

import com.rentals.ui.LoginView;
import com.rentals.ui.properties.PropertiesView;
import com.rentals.ui.rooms.RoomsView;
import com.rentals.ui.tenants.TenantsView;
import com.rentals.ui.tenancies.TenanciesView;
import com.rentals.ui.payments.PaymentsView;
import com.rentals.ui.reports.ReportsView;
import com.rentals.ui.reminders.RemindersView;
import com.rentals.ui.settings.SettingsView;
import com.rentals.service.SessionService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Main dashboard after login.
 * Features a modern layout with sidebar navigation, top bar, and stats cards.
 * Keyboard shortcuts: P=Properties, R=Rooms, T=Tenants, M=Payments, B=Reports,
 *   E=Reminders, S=Settings, L=Logout
 */
public class DashboardView {

    private static Stage primaryStage;

    public static void showDashboard(Stage stage) {
        primaryStage = stage;

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");

        // ---------- Sidebar ----------
        VBox sidebar = new VBox(10);
        sidebar.getStyleClass().add("sidebar");

        Label appTitle = new Label("🏠 RentalSys");
        appTitle.getStyleClass().add("sidebar-title");

        sidebar.getChildren().add(appTitle);

        // Navigation buttons with icons and keyboard shortcuts
        Button propertiesBtn = createSidebarButton("🏢  Properties   (P)", "sidebar-button", () -> PropertiesView.show(stage));
        Button roomsBtn = createSidebarButton("🛏️  Rooms   (R)", "sidebar-button", () -> RoomsView.show(stage));
        Button tenantsBtn = createSidebarButton("👥  Tenants   (T)", "sidebar-button", () -> TenantsView.show(stage));
        Button tenanciesBtn = createSidebarButton("📄  Tenancies   (A)", "sidebar-button", () -> TenanciesView.show(stage));
        Button paymentsBtn = createSidebarButton("💰  Payments   (M)", "sidebar-button", () -> PaymentsView.show(stage));
        Button reportsBtn = createSidebarButton("📊  Reports   (B)", "sidebar-button", () -> ReportsView.show(stage));
        Button remindersBtn = createSidebarButton("🔔  Reminders   (E)", "sidebar-button", () -> RemindersView.show(stage));
        Button settingsBtn = createSidebarButton("⚙️  Settings   (S)", "sidebar-button", () -> SettingsView.show(stage));

        sidebar.getChildren().addAll(propertiesBtn, roomsBtn, tenantsBtn, tenanciesBtn,
                                     paymentsBtn, reportsBtn, remindersBtn, settingsBtn);

        // Logout button at bottom of sidebar
        Button logoutBtn = createSidebarButton("🚪  Logout   (L)", "sidebar-button", () -> {
            new SessionService().destroySession();
            stage.close();
            Stage loginStage = new Stage();
            loginStage.setMaximized(true);
            loginStage.setScene(new Scene(new LoginView(), 500, 550));
            loginStage.setTitle("Rental Management System - Login");
            loginStage.show();
        });
        logoutBtn.getStyleClass().add("btn-danger");
        logoutBtn.setStyle("-fx-text-fill: white; -fx-background-color: #D32F2F;");

        VBox logoutContainer = new VBox(logoutBtn);
        logoutContainer.setPadding(new Insets(20, 0, 0, 0));
        logoutContainer.setAlignment(Pos.TOP_LEFT);
        sidebar.getChildren().add(logoutContainer);

        // ---------- Top Bar ----------
        HBox topBar = new HBox();
        topBar.getStyleClass().add("top-bar");

        Label titleLabel = new Label("Dashboard");
        titleLabel.getStyleClass().add("top-bar-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label userLabel = new Label("Admin");
        userLabel.getStyleClass().add("top-bar-title");
        userLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 14px;");

        topBar.getChildren().addAll(titleLabel, spacer, userLabel);
        root.setTop(topBar);

        // ---------- Center Content ----------
        VBox center = new VBox(20);
        center.getStyleClass().add("card");
        center.setPadding(new Insets(30));
        center.setAlignment(Pos.TOP_LEFT);

        Label welcomeLabel = new Label("Welcome back, Admin");
        welcomeLabel.getStyleClass().add("label-title");

        Label subtitleLabel = new Label("Manage your rental properties from one place");
        subtitleLabel.getStyleClass().add("label-subtitle");

        center.getChildren().addAll(welcomeLabel, subtitleLabel);

        // Stats cards row
        HBox statsRow = new HBox(20);
        statsRow.setAlignment(Pos.TOP_LEFT);

        statsRow.getChildren().addAll(
            createStatCard("Properties", "0", "🏢"),
            createStatCard("Rooms", "0", "🛏️"),
            createStatCard("Tenants", "0", "👥"),
            createStatCard("Active Tenancies", "0", "📄")
        );

        center.getChildren().add(statsRow);

        root.setCenter(center);

        // Keyboard shortcuts
        stage.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            switch (e.getCode()) {
                case P -> PropertiesView.show(stage);
                case R -> RoomsView.show(stage);
                case T -> TenantsView.show(stage);
                case A -> TenanciesView.show(stage);
                case M -> PaymentsView.show(stage);
                case B -> ReportsView.show(stage);
                case E -> RemindersView.show(stage);
                case S -> SettingsView.show(stage);
                case L -> logoutBtn.fire();
            }
        });

        Scene scene = new Scene(root, 1100, 700);
        scene.getStylesheets().add(DashboardView.class.getResource("/css/style.css").toExternalForm());
        stage.setScene(scene);
        stage.setTitle("Rental Management System - Dashboard");
        stage.show();
    }

    private static Button createSidebarButton(String text, String styleClass, Runnable action) {
        Button btn = new Button(text);
        btn.getStyleClass().add(styleClass);
        btn.setPrefWidth(180);
        btn.setPrefHeight(45);
        btn.setMaxWidth(180);
        btn.setOnAction(e -> action.run());
        return btn;
    }

    private static VBox createStatCard(String label, String value, String icon) {
        VBox card = new VBox(10);
        card.getStyleClass().add("stat-card");
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(20));
        card.setPrefWidth(200);

        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add("stat-icon");

        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("stat-value");

        Label labelLabel = new Label(label);
        labelLabel.getStyleClass().add("stat-label");

        card.getChildren().addAll(iconLabel, valueLabel, labelLabel);
        return card;
    }
}
