package com.rentals.ui;

import com.rentals.service.AuditService;
import com.rentals.service.AuthenticationService;
import com.rentals.service.MainLayout;
import com.rentals.service.SessionService;
import com.rentals.service.ThemeManager;
import com.rentals.settings.AppSettings;
import com.rentals.ui.settings.IntegrationsSetupDialog;
import com.rentals.users.User;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

/**
 * Modern Login View with professional styling.
 * Features: Show/Hide password, Enter key support, gradient background.
 */
public class LoginView extends VBox {

    private final TextField usernameField;
    private final PasswordField passwordField;
    private final TextField passwordVisibleField;
    private final Label errorLabel;

    public LoginView() {
        // Main layout
        setSpacing(0);
        setPadding(Insets.EMPTY);
        setAlignment(Pos.CENTER);
        setStyle("-fx-background-color: linear-gradient(to bottom right, #1976D2, #0D47A1);");

        // Card container
        VBox card = new VBox(20);
        card.setPadding(new Insets(40));
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(420);
        card.getStyleClass().add("login-card");

        // Logo/Icon area
        Label iconLabel = new Label("🏠");
        iconLabel.setFont(Font.font("Arial", 48));

        // Title
        Label titleLabel = new Label("Rental Management System");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        titleLabel.getStyleClass().add("login-title");

        Label subtitleLabel = new Label("Admin Login");
        subtitleLabel.setFont(Font.font("Arial", 14));
        subtitleLabel.getStyleClass().add("login-subtitle");

        // Username field with icon
        HBox usernameBox = new HBox(10);
        usernameBox.setAlignment(Pos.CENTER_LEFT);
        Label userIcon = new Label("👤");
        userIcon.setFont(Font.font("Arial", 18));
        usernameField = new TextField();
        usernameField.setPromptText("Username");
        usernameField.setPrefWidth(280);
        usernameField.setPrefHeight(45);
        usernameBox.getChildren().addAll(userIcon, usernameField);

        // Password field with icon and show/hide
        HBox passwordBox = new HBox(10);
        passwordBox.setAlignment(Pos.CENTER_LEFT);
        Label passIcon = new Label("🔒");
        passIcon.setFont(Font.font("Arial", 18));

        passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        passwordField.setPrefWidth(250);
        passwordField.setPrefHeight(45);

        passwordVisibleField = new TextField();
        passwordVisibleField.setPromptText("Password");
        passwordVisibleField.setPrefWidth(250);
        passwordVisibleField.setPrefHeight(45);
        passwordVisibleField.setVisible(false);

        StackPane passwordContainer = new StackPane();
        passwordContainer.setPrefWidth(300);
        passwordContainer.getChildren().addAll(passwordField, passwordVisibleField);

        CheckBox showPassword = new CheckBox("Show Password");
        showPassword.getStyleClass().add("login-check");
        showPassword.setOnAction(e -> {
            if (showPassword.isSelected()) {
                passwordVisibleField.setText(passwordField.getText());
                passwordVisibleField.setVisible(true);
                passwordField.setVisible(false);
            } else {
                passwordField.setText(passwordVisibleField.getText());
                passwordField.setVisible(true);
                passwordVisibleField.setVisible(false);
            }
        });

        passwordBox.getChildren().addAll(passIcon, passwordContainer);

        // Error message
        errorLabel = new Label("");
        errorLabel.setTextFill(Color.RED);
        errorLabel.setFont(Font.font("Arial", 13));
        errorLabel.setMaxWidth(350);
        errorLabel.setWrapText(true);

        // Login button
        Button loginBtn = new Button("LOGIN");
        loginBtn.setMaxWidth(350);
        loginBtn.setPrefHeight(50);
        loginBtn.setStyle("-fx-background-color: #1976D2; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 16px; -fx-background-radius: 8px; -fx-cursor: hand;");

        loginBtn.setOnAction(e -> handleLogin());

        // Enter key support
        usernameField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) passwordField.requestFocus();
        });
        passwordField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) handleLogin();
        });
        passwordVisibleField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) handleLogin();
        });

        // Add components to card
        card.getChildren().addAll(iconLabel, titleLabel, subtitleLabel, usernameBox, passwordBox, showPassword, errorLabel, loginBtn);
        getChildren().add(card);
    }

    private void handleLogin() {
        String password = passwordField.isVisible() ? passwordField.getText() : passwordVisibleField.getText();
        String username = usernameField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Please enter username and password");
            return;
        }

        AuthenticationService authService = new AuthenticationService();
        User user = authService.authenticateAdmin(username, password);

        if (user != null) {
            SessionService sessionService = new SessionService();
            sessionService.createAdminSession(user);
            AuditService.log("LOGIN", "Admin logged in");

            Stage stage = (Stage) getScene().getWindow();
            MainLayout mainLayout = new MainLayout(stage);
            Scene scene = new Scene(mainLayout.getRoot(), 1200, 800);
            ThemeManager.applyTo(scene);
            stage.setScene(scene);
            stage.setTitle("Rental Management System - Dashboard");
            stage.show();

            // First login: ask the admin for the contact, SMS/WhatsApp and cloud
            // backup details. The wizard can be reopened from Settings later.
            if (!AppSettings.integrationSetupComplete()) {
                Platform.runLater(() -> IntegrationsSetupDialog.showIfNeeded(stage));
            }
        } else {
            showError("Invalid username or password");
            passwordField.clear();
            passwordVisibleField.clear();
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
    }
}
