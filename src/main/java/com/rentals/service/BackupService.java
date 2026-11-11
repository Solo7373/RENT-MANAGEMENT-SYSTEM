package com.rentals.service;

import com.rentals.config.AppPaths;
import com.rentals.dao.DatabaseConnection;
import com.rentals.settings.AppSettings;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Consistent, verifiable backups.
 *
 * SQLite's VACUUM INTO creates a clean snapshot while the app is running. The
 * snapshot is integrity-checked, then zipped together with the tenant documents
 * folder and a small info file. A SHA-256 checksum is written next to the zip.
 * Old backups are pruned according to the backup_retention setting.
 */
public class BackupService {

    /** One backup file for the settings table. */
    public record BackupInfo(Path path, long sizeBytes, String modified) {
        public String name() { return path.getFileName().toString(); }
        public double sizeMb() { return sizeBytes / (1024.0 * 1024.0); }
    }

    /** Result of a backup that may also have been copied to the cloud folder. */
    public record BackupOutcome(
        Path local,
        Path cloudCopy,
        boolean cloudAttempted,
        boolean cloudOk,
        String cloudMessage
    ) {}

    public static Path createBackup() throws Exception {
        Path dir = AppPaths.backupsDir();
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
        Path snapshot = dir.resolve(".snapshot-" + UUID.randomUUID() + ".sqlite");
        Path zip = dir.resolve("rental-backup-" + stamp + ".zip");

        try {
            // 1. Consistent database snapshot (safe while the UI is using the DB)
            try (Connection conn = DatabaseConnection.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("VACUUM INTO '" + sqlPath(snapshot) + "'");
            }

            // 2. Refuse to ship a corrupt snapshot
            verifyIntegrity(snapshot);

            // 3. Zip the snapshot + documents + a readable info file
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            int documentCount = 0;
            try (OutputStream fileOut = Files.newOutputStream(zip);
                 DigestOutputStream digestOut = new DigestOutputStream(fileOut, digest);
                 ZipOutputStream zos = new ZipOutputStream(digestOut, StandardCharsets.UTF_8)) {

                addFile(zos, snapshot, "rentaldb.sqlite");

                Path documents = AppPaths.documentsDir();
                if (Files.isDirectory(documents)) {
                    try (var stream = Files.walk(documents)) {
                        for (Path file : (Iterable<Path>) stream::iterator) {
                            if (Files.isRegularFile(file)) {
                                String entry = documents.relativize(file).toString().replace('\\', '/');
                                addFile(zos, file, "documents/" + entry);
                                documentCount++;
                            }
                        }
                    }
                }

                String info = "Rental Management System backup\n" +
                              "Created: " + LocalDateTime.now() + "\n" +
                              "Company: " + AppSettings.companyName() + "\n" +
                              "Database integrity: ok\n" +
                              "Documents included: " + documentCount + "\n";
                zos.putNextEntry(new ZipEntry("backup-info.txt"));
                zos.write(info.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }

            // 4. Checksum for tamper/corruption detection
            String hash = HexFormat.of().formatHex(digest.digest());
            Files.writeString(dir.resolve(zip.getFileName() + ".sha256"),
                hash + "  " + zip.getFileName() + System.lineSeparator(),
                StandardCharsets.UTF_8);

            // 5. Retention
            pruneOldBackups();

            return zip;
        } catch (Exception e) {
            try { Files.deleteIfExists(zip); } catch (IOException ignored) {}
            throw e;
        } finally {
            try { Files.deleteIfExists(snapshot); } catch (IOException ignored) {}
        }
    }

    /** Creates a local backup and, when enabled, copies it to the cloud folder. */
    public static BackupOutcome createBackupAndUpload() throws Exception {
        Path local = createBackup();

        if (!AppSettings.cloudBackupEnabled()) {
            return new BackupOutcome(local, null, false, true, "Cloud backup is disabled.");
        }
        if (!AppSettings.cloudAutoUpload()) {
            return new BackupOutcome(local, null, false, true,
                "Automatic cloud upload is off. Use Upload Latest when ready.");
        }

        CloudBackupService.Result cloud = CloudBackupService.upload(local);
        return new BackupOutcome(local, cloud.uploaded(), true, cloud.success(),
            cloud.message());
    }

    /** Newest first. */
    public static List<BackupInfo> listBackups() {        List<BackupInfo> backups = new ArrayList<>();
        Path dir = AppPaths.backupsDir();
        try (var stream = Files.list(dir)) {
            for (Path file : (Iterable<Path>) stream::iterator) {
                String name = file.getFileName().toString();
                if (name.startsWith("rental-backup-") && name.endsWith(".zip")) {
                    backups.add(new BackupInfo(
                        file,
                        Files.size(file),
                        Files.getLastModifiedTime(file).toString()));
                }
            }
        } catch (IOException e) {
            System.err.println("Could not list backups: " + e.getMessage());
        }
        backups.sort(Comparator.comparing(BackupInfo::modified).reversed());
        return backups;
    }

    /**
     * Re-computes the SHA-256 of a backup zip and compares it with the
     * checksum file written during the backup.
     * @return true when the file is intact and matches its checksum
     */
    public static boolean verify(Path zip) throws Exception {
        Path checksumFile = zip.resolveSibling(zip.getFileName() + ".sha256");
        if (!Files.exists(checksumFile)) return false;

        String line = Files.readString(checksumFile, StandardCharsets.UTF_8).trim();
        if (line.isBlank()) return false;
        String expected = line.split("\\s+")[0].toLowerCase(Locale.ROOT);

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(zip);
             DigestInputStream digestIn = new DigestInputStream(in, digest)) {
            digestIn.transferTo(OutputStream.nullOutputStream());
        }
        String actual = HexFormat.of().formatHex(digest.digest());
        return actual.equalsIgnoreCase(expected);
    }

    private static void verifyIntegrity(Path snapshot) throws Exception {
        String url = "jdbc:sqlite:" + snapshot.toAbsolutePath().toString().replace('\\', '/');
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA integrity_check")) {
            if (!rs.next() || !"ok".equalsIgnoreCase(rs.getString(1))) {
                throw new IllegalStateException("Backup integrity check failed.");
            }
        }
    }

    private static void addFile(ZipOutputStream zos, Path file, String entryName) throws IOException {
        zos.putNextEntry(new ZipEntry(entryName));
        Files.copy(file, zos);
        zos.closeEntry();
    }

    private static void pruneOldBackups() {
        int keep = AppSettings.backupRetention();
        List<BackupInfo> backups = listBackups(); // newest first
        for (int i = keep; i < backups.size(); i++) {
            Path old = backups.get(i).path();
            try {
                Files.deleteIfExists(old);
                Files.deleteIfExists(old.resolveSibling(old.getFileName() + ".sha256"));
            } catch (IOException e) {
                System.err.println("Could not prune old backup " + old + ": " + e.getMessage());
            }
        }
    }

    private static String sqlPath(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/').replace("'", "''");
    }
}
