package com.rentals;

import com.rentals.dao.DatabaseConnection;
import com.rentals.service.ThemeManager;
import com.rentals.ui.LoginView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Rental Management System - Desktop Application (JavaFX)
 * 
 * Features:
 * - Fullscreen window
 * - Admin login with BCrypt password hashing
 * - Custom application icon
 * - SQLite database (auto-created on first run)
 * 
 * How to Run:
 *   mvn clean compile exec:java "-Dexec.mainClass=com.rentals.Launcher"
 */
public class RentalApplication extends Application {

    @Override
    public void start(Stage primaryStage) {
        // Initialize the database (creates tables and default admin)
        com.rentals.config.DatabaseInitializer.initDatabase();

        // Set window title
        primaryStage.setTitle("Rental Management System");

        // Set application icon
        try {
            Image icon = new Image(getClass().getResourceAsStream("/icon.png"));
            primaryStage.getIcons().add(icon);
        } catch (Exception e) {
            System.out.println("Icon not found, using default");
        }

        // Create the login view
        VBox root = new LoginView();
        Scene scene = new Scene(root, 500, 550);

        // Apply CSS styling (respects the saved light/dark theme)
        ThemeManager.applyTo(scene);

        primaryStage.setScene(scene);

        // Make the window fullscreen (maximized with title bar)
        primaryStage.setMaximized(true);
        primaryStage.setResizable(true);

        primaryStage.show();

        System.out.println("========================================");
        System.out.println("  RENTAL MANAGEMENT SYSTEM");
        System.out.println("  Desktop Application Started");
        System.out.println("  Login: admin / admin123");
        System.out.println("========================================");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
