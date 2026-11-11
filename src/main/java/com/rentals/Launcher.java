package com.rentals;

/**
 * Launcher class - required to run JavaFX applications.
 * 
 * This separate class avoids the "JavaFX runtime components are missing"
 * error that happens when the main class directly extends Application.
 * 
 * Usage:
 *   mvn clean install
 *   mvn exec:java -Dexec.mainClass="com.rentals.Launcher"
 */
public class Launcher {
    public static void main(String[] args) {
        RentalApplication.main(args);
    }
}
