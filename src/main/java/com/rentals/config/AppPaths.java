package com.rentals.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * Central place for every writable path used by the application.
 *
 * Data is stored outside the installation folder so the packaged application
 * still works when it is installed under Program Files (which is read-only for
 * normal users). The folder is:
 *
 *   Windows: %APPDATA%\RentalManagementSystem
 *   Other:   ~/.rentalmanagementsystem
 *
 * An existing database in the working directory is migrated automatically on
 * first launch. Tests can override the folder with -Drentals.dataDir=...
 */
public final class AppPaths {

    private static final String APP_FOLDER = "RentalManagementSystem";

    private static Path dataDir;

    private AppPaths() {}

    /** Root data folder (created on first use). */
    public static synchronized Path dataDir() {
        if (dataDir != null) return dataDir;

        String override = System.getProperty("rentals.dataDir");
        boolean overridden = override != null && !override.isBlank();
        Path dir;
        if (overridden) {
            dir = Path.of(override);
        } else if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            String appData = System.getenv("APPDATA");
            Path base = (appData == null || appData.isBlank())
                ? Path.of(System.getProperty("user.home"))
                : Path.of(appData);
            dir = base.resolve(APP_FOLDER);
        } else {
            dir = Path.of(System.getProperty("user.home"), "." + APP_FOLDER.toLowerCase(Locale.ROOT));
        }

        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create data folder: " + dir, e);
        }

        dataDir = dir.toAbsolutePath().normalize();
        if (!overridden) migrateLegacyDatabase();
        return dataDir;
    }

    public static Path databaseFile() {
        return dataDir().resolve("rentaldb.sqlite");
    }

    public static Path documentsDir() {
        return subDirectory("documents");
    }

    public static Path backupsDir() {
        return subDirectory("backups");
    }

    public static Path receiptsDir() {
        return subDirectory("receipts");
    }

    public static Path reportsDir() {
        return subDirectory("reports");
    }

    /** Creates and returns a named sub-folder of the data folder. */
    public static Path subDirectory(String name) {
        Path dir = dataDir().resolve(name);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create folder: " + dir, e);
        }
        return dir;
    }

    /**
     * One-time migration from the old working-directory database so users do
     * not lose data when they upgrade to the packaged version.
     */
    private static void migrateLegacyDatabase() {
        try {
            Path legacy = Path.of("rentaldb.sqlite").toAbsolutePath().normalize();
            Path target = dataDir.resolve("rentaldb.sqlite");
            if (Files.exists(legacy) && !legacy.equals(target) && !Files.exists(target)) {
                Files.copy(legacy, target, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Migrated existing database to: " + target);
                migrateFolder("documents");
                migrateFolder("backups");
            }
        } catch (IOException e) {
            System.err.println("Legacy data migration skipped: " + e.getMessage());
        }
    }

    private static void migrateFolder(String name) {
        try {
            Path legacy = Path.of(name);
            Path target = dataDir.resolve(name);
            if (Files.isDirectory(legacy) && !Files.exists(target)) {
                try (var stream = Files.walk(legacy)) {
                    for (Path source : (Iterable<Path>) stream::iterator) {
                        Path destination = target.resolve(legacy.relativize(source).toString());
                        if (Files.isDirectory(source)) {
                            Files.createDirectories(destination);
                        } else {
                            Files.createDirectories(destination.getParent());
                            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Could not migrate " + name + " folder: " + e.getMessage());
        }
    }
}
