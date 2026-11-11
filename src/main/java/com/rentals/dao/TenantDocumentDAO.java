package com.rentals.dao;

import com.rentals.documents.TenantDocument;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for tenant document attachments.
 */
public class TenantDocumentDAO {

    public boolean create(TenantDocument doc) {
        String sql = "INSERT INTO tenant_documents (tenant_id, document_type, file_name, file_path, file_size) " +
                     "VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, doc.getTenantId());
            pstmt.setString(2, doc.getDocumentType());
            pstmt.setString(3, doc.getFileName());
            pstmt.setString(4, doc.getFilePath());
            pstmt.setLong(5, doc.getFileSize());
            pstmt.executeUpdate();
            doc.setDocumentId(DatabaseConnection.lastInsertId(conn));
            return true;
        } catch (SQLException e) {
            System.err.println("Create document error: " + e.getMessage());
            return false;
        }
    }

    public List<TenantDocument> getByTenant(Long tenantId) {
        List<TenantDocument> docs = new ArrayList<>();
        String sql = "SELECT * FROM tenant_documents WHERE tenant_id = ? ORDER BY uploaded_at DESC, document_id DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, tenantId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) docs.add(map(rs));
        } catch (SQLException e) {
            System.err.println("Get documents error: " + e.getMessage());
        }
        return docs;
    }

    public boolean delete(Long documentId) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                 "DELETE FROM tenant_documents WHERE document_id = ?")) {
            pstmt.setLong(1, documentId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Delete document error: " + e.getMessage());
            return false;
        }
    }

    /** Removes all document rows for a tenant and returns their file paths. */
    public List<String> deleteByTenant(Long tenantId) {
        List<String> paths = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection()) {
            try (PreparedStatement select = conn.prepareStatement(
                    "SELECT file_path FROM tenant_documents WHERE tenant_id = ?")) {
                select.setLong(1, tenantId);
                ResultSet rs = select.executeQuery();
                while (rs.next()) paths.add(rs.getString("file_path"));
            }
            try (PreparedStatement delete = conn.prepareStatement(
                    "DELETE FROM tenant_documents WHERE tenant_id = ?")) {
                delete.setLong(1, tenantId);
                delete.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Delete tenant documents error: " + e.getMessage());
        }
        return paths;
    }

    private TenantDocument map(ResultSet rs) throws SQLException {
        TenantDocument d = new TenantDocument();
        d.setDocumentId(rs.getLong("document_id"));
        d.setTenantId(rs.getLong("tenant_id"));
        d.setDocumentType(rs.getString("document_type"));
        d.setFileName(rs.getString("file_name"));
        d.setFilePath(rs.getString("file_path"));
        try { d.setFileSize(rs.getLong("file_size")); } catch (SQLException e) {}
        d.setUploadedAt(rs.getString("uploaded_at"));
        return d;
    }
}
