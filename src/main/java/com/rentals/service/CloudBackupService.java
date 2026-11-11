package com.rentals.service;

import com.rentals.settings.AppSettings;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Copies verified backup archives into a cloud-synced folder.
 *
 * <p>RentalSys does not ask for a cloud password. The admin signs in to the
 * provider's desktop client (OneDrive, Google Drive or Dropbox) once, then
 * chooses that sync folder here. After each backup the zip and its SHA-256 file
 * are copied into the folder and the provider syncs them to the cloud.</p>
 *
 * <p>Old cloud copies are pruned using the same retention setting as local
 * backups.</p>
 */
public final class CloudBackupService {

    /** Outcome of a cloud test or upload. */
    public record Result(boolean success, String message, Path uploaded) {}

    private CloudBackupService() {}

    /** Checks that a folder exists (or can be created) and is writable. */
    public static Result testFolder(Path folder) {
        if (folder == null || folder.toString().isBlank()) {
            return new Result(false, "Choose a cloud sync folder first.", null);
        }
        try {
            Files.createDirectories(folder);
            Path probe = folder.resolve(".rental-backup-test-" + UUID.randomUUID() + ".tmp");
            Files.writeString(probe, "RentalSys cloud backup test", StandardCharsets.UTF_8);
            Files.deleteIfExists(probe);
            return new Result(true, "Folder is available and writable:\n" + folder, null);
        } catch (Exception e) {
            return new Result(false,
                "Cannot write to " + folder + ": " + e.getMessage(), null);
        }
    }

    /** Copies a local backup zip and its checksum into the configured cloud folder. */
    public static Result upload(Path zip) {
        if (zip == null || !Files.exists(zip)) {
            return new Result(false, "The local backup file was not found.", null);
        }

        String folderText = AppSettings.cloudFolder();
        if (folderText == null || folderText.isBlank()) {
            return new Result(false,
                "No cloud backup folder is configured. Open Settings > Communication.", null);
        }

        Path folder = Path.of(folderText.trim());
        Result folderCheck = testFolder(folder);
        if (!folderCheck.success()) return folderCheck;

        try {
            Path cloudZip = folder.resolve(zip.getFileName().toString());
            Path localChecksum = zip.resolveSibling(zip.getFileName() + ".sha256");
            Path cloudChecksum = folder.resolve(cloudZip.getFileName() + ".sha256");

            Files.copy(zip, cloudZip, StandardCopyOption.REPLACE_EXISTING);

            if (Files.exists(localChecksum)) {
                Files.copy(localChecksum, cloudChecksum, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.writeString(cloudChecksum,
                    sha256(cloudZip) + "  " + cloudZip.getFileName() + System.lineSeparator(),
                    StandardCharsets.UTF_8);
            }

            if (!BackupService.verify(cloudZip)) {
                Files.deleteIfExists(cloudZip);
                Files.deleteIfExists(cloudChecksum);
                throw new IOException("Checksum verification failed after copying.");
            }

            pruneCloudBackups(folder);

            return new Result(true,
                "Backup copied to the cloud folder:\n" + cloudZip, cloudZip);
        } catch (Exception e) {
            return new Result(false, "Cloud upload failed: " + e.getMessage(), null);
        }
    }

    /** Best-effort detection of a signed-in cloud client's sync folder. */
    public static String suggestFolder(String providerCode) {
        String home = System.getProperty("user.home", "");
        String code = providerCode == null ? "" : providerCode.toUpperCase(Locale.ROOT);

        return switch (code) {
            case "ONEDRIVE" -> existingFolder(
                System.getenv("OneDrive"),
                System.getenv("OneDriveCommercial"),
                Path.of(home, "OneDrive").toString());
            case "GOOGLE_DRIVE" -> existingFolder(
                System.getenv("GoogleDrive"),
                Path.of(home, "Google Drive").toString(),
                Path.of(home, "My Drive").toString());
            case "DROPBOX" -> existingFolder(
                System.getenv("Dropbox"),
                Path.of(home, "Dropbox").toString());
            default -> "";
        };
    }

    /** Number of backup zips currently in the folder. */
    public static int countCloudBackups(Path folder) {
        return listCloudBackups(folder).size();
    }

    private static String existingFolder(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                Path path = Path.of(candidate);
                if (Files.isDirectory(path)) {
                    return path.resolve("RentalBackups").toString();
                }
            }
        }
        return "";
    }

    private static List<Path> listCloudBackups(Path folder) {
        List<Path> backups = new ArrayList<>();
        if (folder == null || !Files.isDirectory(folder)) return backups;
        try (var stream = Files.list(folder)) {
            for (Path file : (Iterable<Path>) stream::iterator) {
                String name = file.getFileName().toString();
                if (name.startsWith("rental-backup-") && name.endsWith(".zip")) {
                    backups.add(file);
                }
            }
        } catch (IOException e) {
            System.err.println("Could not list cloud backups: " + e.getMessage());
        }
        backups.sort(Comparator.comparing(CloudBackupService::modified).reversed());
        return backups;
    }

    private static String modified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toString();
        } catch (IOException e) {
            return "";
        }
    }

    private static void pruneCloudBackups(Path folder) {
        int keep = AppSettings.backupRetention();
        List<Path> backups = listCloudBackups(folder);
        for (int i = keep; i < backups.size(); i++) {
            Path old = backups.get(i);
            try {
                Files.deleteIfExists(old);
                Files.deleteIfExists(old.resolveSibling(old.getFileName() + ".sha256"));
            } catch (IOException e) {
                System.err.println("Could not prune cloud backup " + old + ": " + e.getMessage());
            }
        }
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file);
             DigestInputStream digestIn = new DigestInputStream(in, digest)) {
            digestIn.transferTo(java.io.OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
