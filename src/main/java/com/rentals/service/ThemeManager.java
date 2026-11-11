package com.rentals.service;

import com.rentals.dao.SettingsDAO;
import javafx.scene.Scene;

/**
 * Theme manager - switches between the light and dark stylesheets.
 * The chosen theme is persisted in the system_settings table.
 */
public class ThemeManager {

    private static final String BASE_CSS = "/css/style.css";
    private static final String DARK_CSS = "/css/dark.css";

    private static boolean dark = false;

    static {
        // Load the saved preference once when the class is first used
        try {
            String saved = new SettingsDAO().get("theme");
            dark = "dark".equalsIgnoreCase(saved);
        } catch (Exception e) {
            dark = false;
        }
    }

    public static boolean isDark() {
        return dark;
    }

    /** Toggles the theme, re-applies it to the scene and persists the choice. */
    public static void toggle(Scene scene) {
        dark = !dark;
        applyTo(scene);
        try {
            new SettingsDAO().save("theme", dark ? "dark" : "light", "UI theme (light or dark)");
        } catch (Exception e) {
            System.err.println("Could not save theme preference: " + e.getMessage());
        }
    }

    /** Applies the current theme to a scene (safe to call repeatedly). */
    public static void applyTo(Scene scene) {
        if (scene == null) return;
        String base = ThemeManager.class.getResource(BASE_CSS).toExternalForm();
        String darkUrl = ThemeManager.class.getResource(DARK_CSS).toExternalForm();

        scene.getStylesheets().removeIf(s -> s.equals(base) || s.equals(darkUrl));
        scene.getStylesheets().add(base);
        if (dark) scene.getStylesheets().add(darkUrl);
    }
}
