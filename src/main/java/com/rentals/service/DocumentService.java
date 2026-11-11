package com.rentals.service;

import com.rentals.config.AppPaths;
import com.rentals.dao.TenantDocumentDAO;
import com.rentals.documents.TenantDocument;
import com.rentals.util.FormatUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Copies tenant documents into a managed folder and keeps the database in sync.
 * Files stay on this computer; nothing is uploaded anywhere.
 */
public class DocumentService {

    private static final long MAX_BYTES = 10L * 1024 * 1024; // 10 MB
    private static final Set<String> ALLOWED = Set.of(
        "pdf", "png", "jpg", "jpeg", "webp", "gif", "bmp", "txt", "doc", "docx"
    );

    private final TenantDocumentDAO dao = new TenantDocumentDAO();

    public List<TenantDocument> list(Long tenantId) {
        return dao.getByTenant(tenantId);
    }

    public Path tenantFolder(Long tenantId) {
        return AppPaths.documentsDir().resolve("tenant-" + tenantId);
    }

    /**
     * Validates and copies a file into the tenant's folder, then records it.
     * @throws IllegalArgumentException when the file type or size is not allowed
     */
    public TenantDocument attach(Long tenantId, String documentType, Path source) throws IOException {
        if (source == null || !Files.isRegularFile(source)) {
            throw new IllegalArgumentException("Please choose an existing file.");
        }
        if (Files.size(source) > MAX_BYTES) {
            throw new IllegalArgumentException("File is larger than 10 MB.");
        }

        String original = source.getFileName().toString();
        String extension = extensionOf(original);
        if (!ALLOWED.contains(extension)) {
            throw new IllegalArgumentException(
                "Unsupported file type. Allowed: PDF, images, Word documents and text files.");
        }

        Path folder = tenantFolder(tenantId);
        Files.createDirectories(folder);

        String safeType = (documentType == null || documentType.isBlank() ? "OTHER" : documentType)
            .toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "-");
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String safeName = stamp + "-" + safeType + "-" + UUID.randomUUID().toString().substring(0, 8)
            + "." + extension;
        Path target = folder.resolve(safeName);

        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);

        TenantDocument doc = new TenantDocument();
        doc.setTenantId(tenantId);
        doc.setDocumentType(documentType == null ? "OTHER" : documentType);
        doc.setFileName(original);
        doc.setFilePath(target.toAbsolutePath().toString());
        doc.setFileSize(Files.size(target));

        if (!dao.create(doc)) {
            Files.deleteIfExists(target);
            throw new IOException("Could not save the document record.");
        }
        return doc;
    }

    public void open(TenantDocument doc) {
        Path path = Path.of(doc.getFilePath());
        if (!Files.exists(path)) {
            throw new IllegalStateException("The file is missing from the documents folder.");
        }
        FormatUtils.open(path);
    }

    public boolean delete(TenantDocument doc) {
        try {
            Files.deleteIfExists(Path.of(doc.getFilePath()));
        } catch (IOException e) {
            System.err.println("Could not delete document file: " + e.getMessage());
        }
        return dao.delete(doc.getDocumentId());
    }

    /** Deletes every document row and file belonging to a tenant. */
    public void deleteAllForTenant(Long tenantId) {
        List<String> paths = dao.deleteByTenant(tenantId);
        for (String path : paths) {
            try {
                Files.deleteIfExists(Path.of(path));
            } catch (IOException e) {
                System.err.println("Could not delete document file: " + e.getMessage());
            }
        }
        try {
            Path folder = tenantFolder(tenantId);
            if (Files.isDirectory(folder)) {
                try (var entries = Files.list(folder)) {
                    if (entries.findAny().isEmpty()) Files.deleteIfExists(folder);
                }
            }
        } catch (IOException e) {
            System.err.println("Could not remove documents folder: " + e.getMessage());
        }
    }

    private String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
