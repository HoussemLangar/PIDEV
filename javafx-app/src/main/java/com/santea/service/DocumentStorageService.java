package com.santea.service;

import com.santea.config.DatabaseConfig;
import com.santea.config.SupabaseStorageConfig;
import com.santea.model.SharedDocument;
import com.santea.model.DocumentAccess;
import com.santea.model.User;
import com.santea.repository.UserRepository;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * DocumentStorageService - Section 2.7
 * Manages medical document sharing with access control
 * 
 * Responsibilities:
 * - Document upload and storage with file validation
 * - File metadata management (MIME type, size, timestamp)
 * - Permission-based access control (VIEW, DOWNLOAD, EDIT)
 * - Access history tracking (accessed_at, access_count)
 * - Download and deletion with access validation
 * 
 * Database Logic from Symfony:
 * - Entity SharedDocument: id, fileName, filePath, mimeType, fileSize, owner, description, documentType, uploadedAt, fileContent
 * - Entity DocumentAccess: id, document, sharedWith, permission, sharedAt, expiresAt, isActive, accessedAt, accessCount
 * - Allowed MIME types: pdf, msword, docx, jpeg, png, xls, xlsx
 * - Document types: analysis, report, lab_results, imaging (patients only), prescription, imaging_scan, other
 */
public class DocumentStorageService {
    
    private static final String STORAGE_BASE_PATH = "./documents";
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50 MB
    private static final List<String> ALLOWED_MIME_TYPES = Arrays.asList(
        "application/pdf",
        "image/jpeg",
        "image/png",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );
    
    private static final List<String> PATIENT_ALLOWED_TYPES = Arrays.asList(
        "analysis", "report", "lab_results", "imaging"
    );
    
    // Core storage maps matching database structure
    private static final Map<String, SharedDocument> documents = new HashMap<>();
    private static final Map<String, List<DocumentAccess>> documentAccesses = new HashMap<>();
    private static final Map<String, List<DocumentAccess>> userAccesses = new HashMap<>();
    private static final List<DocumentStorageListener> listeners = new ArrayList<>();

    private final DatabaseService databaseService = new DatabaseService(DatabaseConfig.fromEnvironment());
    private final UserRepository userRepository = new UserRepository(databaseService);
    private final SupabaseStorageClient supabaseStorageClient = new SupabaseStorageClient(SupabaseStorageConfig.fromEnvironment());

    private String getEffectiveRole(User user) {
        return user.getSubscriptionType() != null && !user.getSubscriptionType().isBlank()
            ? user.getSubscriptionType()
            : user.getRole();
    }

    private boolean isPatient(User user) {
        return "ROLE_PATIENT".equals(getEffectiveRole(user));
    }

    private boolean isProfessional(User user) {
        return Arrays.asList("ROLE_MEDECIN", "ROLE_PHARMACIEN", "ROLE_COACH", "ROLE_NUTRITIONNISTE")
            .contains(getEffectiveRole(user));
    }
    
    public DocumentStorageService() {
        initializeStorageDirectory();
    }

    public String formatFileSize(int bytes) {
        if (bytes <= 0) {
            return "0 B";
        }
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int index = (int) Math.min(units.length - 1, Math.floor(Math.log(bytes) / Math.log(1024)));
        double value = bytes / Math.pow(1024, index);
        return String.format("%.2f %s", value, units[index]);
    }
    
    /**
     * Initialize storage directory
     */
    private void initializeStorageDirectory() {
        try {
            Path storagePath = Paths.get(STORAGE_BASE_PATH);
            if (!Files.exists(storagePath)) {
                Files.createDirectories(storagePath);
            }
        } catch (IOException e) {
            System.err.println("Failed to initialize storage directory: " + e.getMessage());
        }
    }
    
    /**
     * Upload a document
     * @param fileName Original file name
     * @param fileContent File content (bytes)
     * @param mimeType MIME type of the file
     * @param owner User uploading the document
     * @param description Document description
     * @param documentType Type of medical document (Analyse, Rapport, Ordonnance, etc.)
     * @return SharedDocument object or null if upload fails
     */
    public SharedDocument uploadDocument(String fileName, byte[] fileContent, String mimeType, 
                                        User owner, String description, String documentType) {
        if (owner == null || owner.getId() == null) {
            return null;
        }
        if (fileName == null || fileName.isBlank() || fileContent == null || fileContent.length == 0) {
            return null;
        }
        if (mimeType == null || mimeType.isBlank()) {
            return null;
        }

        // Validate file size
        if (fileContent.length > MAX_FILE_SIZE) {
            System.err.println("File size exceeds maximum allowed size");
            return null;
        }
        
        // Validate MIME type
        if (!ALLOWED_MIME_TYPES.contains(mimeType)) {
            System.err.println("MIME type not allowed: " + mimeType);
            return null;
        }

        String normalizedType = documentType == null ? "" : documentType.trim().toLowerCase();
        if (normalizedType.isEmpty()) {
            return null;
        }
        if (isPatient(owner) && !PATIENT_ALLOWED_TYPES.contains(normalizedType)) {
            System.err.println("Patients can only upload patient-safe document types");
            return null;
        }
        
        try {
            // Generate unique file path
            String uniqueFileName = generateUniqueFileName(fileName);
            String objectPath = owner.getId() + "/" + uniqueFileName;
            Path localPath = Paths.get(STORAGE_BASE_PATH, owner.getId().toString(), uniqueFileName);

            String persistedPath;
            boolean supabaseEnabled = supabaseStorageClient.isEnabled();
            if (supabaseEnabled && supabaseStorageClient.upload(objectPath, fileContent, mimeType)) {
                persistedPath = buildSupabasePath(objectPath);
            } else {
                if (!supabaseEnabled) {
                    System.err.println("Supabase storage disabled: missing SUPABASE_SERVICE_ROLE_KEY or invalid storage config. Using local filesystem fallback.");
                } else {
                    System.err.println("Supabase upload failed for object " + objectPath + ". Using local filesystem fallback.");
                }
                // Fallback local filesystem
                Files.createDirectories(localPath.getParent());
                Files.write(localPath, fileContent);
                persistedPath = localPath.toString();
            }
            
            // Create document metadata
            SharedDocument document = new SharedDocument();
            document.setFileName(fileName);
            document.setFilePath(persistedPath);
            document.setMimeType(mimeType);
            document.setFileSize(fileContent.length);
            document.setOwner(owner);
            document.setDescription(description);
            document.setDocumentType(normalizedType);
            document.setUploadedAt(LocalDateTime.now());
            document.setPublic(false);

            String insertSql = "INSERT INTO shared_documents "
                + "(owner_id, file_name, file_path, mime_type, file_size, file_content, description, uploaded_at, document_type, is_public) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection connection = databaseService.getConnection();
                 PreparedStatement statement = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setInt(1, owner.getId());
                statement.setString(2, fileName);
                statement.setString(3, persistedPath);
                statement.setString(4, mimeType);
                statement.setInt(5, fileContent.length);
                statement.setBytes(6, fileContent);
                statement.setString(7, description);
                statement.setTimestamp(8, Timestamp.valueOf(document.getUploadedAt()));
                statement.setString(9, normalizedType);
                statement.setBoolean(10, false);
                statement.executeUpdate();

                int generatedId = 0;
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        generatedId = keys.getInt(1);
                    }
                }
                document.setId(generatedId);
            } catch (SQLException sqlException) {
                // fallback to in-memory id when DB insert is not available
                String docId = UUID.randomUUID().toString();
                document.setId(docId.hashCode());
            }

            documents.put(String.valueOf(document.getId()), document);
            documentAccesses.put(String.valueOf(document.getId()), new ArrayList<>());
            notifyListeners("UPLOADED", document);

            return document;
            
        } catch (IOException e) {
            System.err.println("Failed to upload document: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Share a document with a user
     * @param documentId Document ID
     * @param sharedWithUser User to share with
     * @param permission Permission level (VIEW, DOWNLOAD, EDIT)
     * @param expiresAt Optional expiration date
     * @return DocumentAccess object
     */
    public DocumentAccess shareDocument(String documentId, User sharedWithUser, String permission, LocalDateTime expiresAt) {
        SharedDocument document = getDocument(documentId);
        if (document == null || sharedWithUser == null || sharedWithUser.getId() == null) {
            return null;
        }

        if (document.getOwner().getId().equals(sharedWithUser.getId())) {
            return null;
        }

        if (isPatient(document.getOwner()) && !isProfessional(sharedWithUser)) {
            return null;
        }

        if (!isPatient(document.getOwner()) && !isPatient(sharedWithUser)) {
            return null;
        }

        String normalizedPermission = permission == null ? "view" : permission.trim().toLowerCase();
        if (!Arrays.asList("view", "download").contains(normalizedPermission)) {
            return null;
        }
        
        List<DocumentAccess> accesses = documentAccesses.computeIfAbsent(documentId, key -> new ArrayList<>());
        for (DocumentAccess existing : accesses) {
            if (existing.getSharedWith().getId().equals(sharedWithUser.getId())) {
                existing.setPermission(normalizedPermission);
                existing.setExpiresAt(expiresAt);
                existing.setIsActive(true);
                try (Connection connection = databaseService.getConnection();
                     PreparedStatement statement = connection.prepareStatement(
                         "UPDATE document_accesses SET permission = ?, expires_at = ?, is_active = 1 WHERE document_id = ? AND shared_with_id = ?")) {
                    statement.setString(1, normalizedPermission);
                    if (expiresAt == null) {
                        statement.setNull(2, java.sql.Types.TIMESTAMP);
                    } else {
                        statement.setTimestamp(2, Timestamp.valueOf(expiresAt));
                    }
                    statement.setInt(3, Integer.parseInt(documentId));
                    statement.setInt(4, sharedWithUser.getId());
                    statement.executeUpdate();
                } catch (Exception ignored) {
                }
                notifyListeners("SHARED", document);
                return existing;
            }
        }

        DocumentAccess access = new DocumentAccess();
        access.setId(UUID.randomUUID().hashCode());
        access.setDocument(document);
        access.setSharedWith(sharedWithUser);
        access.setPermission(normalizedPermission);
        access.setSharedAt(LocalDateTime.now());
        access.setExpiresAt(expiresAt);
        access.setIsActive(true);
        access.setAccessCount(0);
        accesses.add(access);

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "INSERT INTO document_accesses (document_id, shared_with_id, shared_at, expires_at, accessed_at, access_count, is_active, permission) "
                     + "VALUES (?, ?, ?, ?, NULL, 0, 1, ?)", Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, Integer.parseInt(documentId));
            statement.setInt(2, sharedWithUser.getId());
            statement.setTimestamp(3, Timestamp.valueOf(access.getSharedAt()));
            if (expiresAt == null) {
                statement.setNull(4, java.sql.Types.TIMESTAMP);
            } else {
                statement.setTimestamp(4, Timestamp.valueOf(expiresAt));
            }
            statement.setString(5, normalizedPermission);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    access.setId(keys.getInt(1));
                }
            }
        } catch (Exception ignored) {
        }
        
        // Track user accesses
        userAccesses.computeIfAbsent(sharedWithUser.getId().toString(), k -> new ArrayList<>()).add(access);
        notifyListeners("SHARED", document);
        
        return access;
    }
    
    /**
     * Revoke document access
     * @param accessId Access ID
     * @return true if revoked successfully
     */
    public boolean revokeAccess(String documentId, String userId) {
        if (documentId == null || userId == null || userId.isBlank()) {
            return false;
        }
        int parsedUserId;
        try {
            parsedUserId = Integer.parseInt(userId);
        } catch (NumberFormatException exception) {
            return false;
        }
        List<DocumentAccess> accesses = documentAccesses.get(documentId);
        if (accesses != null) {
            for (DocumentAccess access : accesses) {
                if (access.getSharedWith().getId().equals(parsedUserId)) {
                    access.setIsActive(false);
                    try (Connection connection = databaseService.getConnection();
                         PreparedStatement statement = connection.prepareStatement(
                             "UPDATE document_accesses SET is_active = 0 WHERE document_id = ? AND shared_with_id = ?")) {
                        statement.setInt(1, Integer.parseInt(documentId));
                        statement.setInt(2, parsedUserId);
                        statement.executeUpdate();
                    } catch (Exception ignored) {
                    }
                    notifyListeners("REVOKED", access.getDocument());
                    return true;
                }
            }
        }
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "UPDATE document_accesses SET is_active = 0 WHERE document_id = ? AND shared_with_id = ?")) {
            statement.setInt(1, Integer.parseInt(documentId));
            statement.setInt(2, parsedUserId);
            return statement.executeUpdate() > 0;
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * Delete a shared access entry permanently
     * @param documentId Document ID
     * @param userId Shared user ID
     * @return true if deleted
     */
    public boolean deleteAccess(String documentId, String userId) {
        if (documentId == null || userId == null || userId.isBlank()) {
            return false;
        }

        int parsedUserId;
        try {
            parsedUserId = Integer.parseInt(userId);
        } catch (NumberFormatException exception) {
            return false;
        }

        List<DocumentAccess> accesses = documentAccesses.get(documentId);
        if (accesses != null) {
            accesses.removeIf(access -> access.getSharedWith() != null
                && access.getSharedWith().getId() != null
                && access.getSharedWith().getId().equals(parsedUserId));
        }

        List<DocumentAccess> userList = userAccesses.get(userId);
        if (userList != null) {
            userList.removeIf(access -> access.getDocument() != null
                && access.getDocument().getId() != null
                && String.valueOf(access.getDocument().getId()).equals(documentId));
        }

        boolean deleted = false;
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "DELETE FROM document_accesses WHERE document_id = ? AND shared_with_id = ?")) {
            statement.setInt(1, Integer.parseInt(documentId));
            statement.setInt(2, parsedUserId);
            deleted = statement.executeUpdate() > 0;
        } catch (Exception ignored) {
        }

        if (deleted || accesses != null) {
            SharedDocument document = getDocument(documentId);
            if (document != null) {
                notifyListeners("REVOKED", document);
            }
            return true;
        }
        return false;
    }
    
    /**
     * Check if user has access to document
     * @param documentId Document ID
     * @param userId User ID
     * @param requiredPermission Required permission level
     * @return true if user has access
     */
    public boolean hasAccess(String documentId, String userId, String requiredPermission) {
        SharedDocument document = getDocument(documentId);
        if (document == null) {
            return false;
        }

        String neededPermission = requiredPermission == null || requiredPermission.isBlank()
            ? "view"
            : requiredPermission;
        
        // Owner always has full access
        if (document.getOwner().getId().toString().equals(userId)) {
            return true;
        }

        if (document.isPublic() && ("view".equalsIgnoreCase(neededPermission) || "download".equalsIgnoreCase(neededPermission))) {
            return true;
        }
        
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT permission, expires_at, is_active FROM document_accesses WHERE document_id = ? AND shared_with_id = ? LIMIT 1")) {
            statement.setInt(1, Integer.parseInt(documentId));
            statement.setInt(2, Integer.parseInt(userId));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    if (!resultSet.getBoolean("is_active")) {
                        return false;
                    }
                    Timestamp expiresTs = resultSet.getTimestamp("expires_at");
                    if (expiresTs != null && LocalDateTime.now().isAfter(expiresTs.toLocalDateTime())) {
                        try (PreparedStatement deactivate = connection.prepareStatement(
                            "UPDATE document_accesses SET is_active = 0 WHERE document_id = ? AND shared_with_id = ?")) {
                            deactivate.setInt(1, Integer.parseInt(documentId));
                            deactivate.setInt(2, Integer.parseInt(userId));
                            deactivate.executeUpdate();
                        }
                        return false;
                    }
                    return hasPermission(resultSet.getString("permission"), neededPermission);
                }
            }
        } catch (Exception ignored) {
        }

        List<DocumentAccess> accesses = documentAccesses.get(documentId);
        if (accesses == null) {
            return false;
        }

        for (DocumentAccess access : accesses) {
            if (access.getSharedWith().getId().toString().equals(userId) && access.canAccess()) {
                if (access.getExpiresAt() != null && LocalDateTime.now().isAfter(access.getExpiresAt())) {
                    access.setIsActive(false);
                    return false;
                }
                return hasPermission(access.getPermission(), neededPermission);
            }
        }
        
        return false;
    }
    
    /**
     * Record document access
     * @param documentId Document ID
     * @param userId User ID
     */
    public void recordAccess(String documentId, String userId) {
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "UPDATE document_accesses SET access_count = access_count + 1, accessed_at = ? WHERE document_id = ? AND shared_with_id = ?")) {
            statement.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            statement.setInt(2, Integer.parseInt(documentId));
            statement.setInt(3, Integer.parseInt(userId));
            statement.executeUpdate();
        } catch (Exception ignored) {
        }

        List<DocumentAccess> accesses = documentAccesses.get(documentId);
        if (accesses != null) {
            for (DocumentAccess access : accesses) {
                if (access.getSharedWith().getId().toString().equals(userId)) {
                    access.incrementAccessCount();
                    break;
                }
            }
        }
    }
    
    /**
     * Download a document
     * @param documentId Document ID
     * @param userId User requesting download
     * @return File bytes or null if access denied
     */
    public byte[] downloadDocument(String documentId, String userId) {
        if (!hasAccess(documentId, userId, "download")) {
            System.err.println("User " + userId + " does not have download access to document " + documentId);
            return null;
        }
        
        recordAccess(documentId, userId);
        
        SharedDocument document = getDocument(documentId);
        if (document == null) {
            return null;
        }
        
        try {
            if (isSupabasePath(document.getFilePath())) {
                byte[] remote = supabaseStorageClient.download(extractSupabaseObjectPath(document.getFilePath()));
                if (remote != null) {
                    return remote;
                }
            } else {
                Path path = Paths.get(document.getFilePath());
                if (Files.exists(path)) {
                    return Files.readAllBytes(path);
                }
            }
            try (Connection connection = databaseService.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                     "SELECT file_content FROM shared_documents WHERE id = ? LIMIT 1")) {
                statement.setInt(1, Integer.parseInt(documentId));
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        byte[] bytes = resultSet.getBytes("file_content");
                        if (bytes != null) {
                            return bytes;
                        }
                    }
                }
            }
            return null;
        } catch (IOException e) {
            System.err.println("Failed to download document: " + e.getMessage());
            return null;
        } catch (SQLException ignored) {
            return null;
        }
    }
    
    /**
     * Delete a document (only owner can delete)
     * @param documentId Document ID
     * @param userId User requesting deletion
     * @return true if deleted successfully
     */
    public boolean deleteDocument(String documentId, String userId) {
        SharedDocument document = getDocument(documentId);
        if (document == null || !document.getOwner().getId().toString().equals(userId)) {
            return false;
        }
        
        try {
            // Delete file from storage
            if (isSupabasePath(document.getFilePath())) {
                supabaseStorageClient.delete(extractSupabaseObjectPath(document.getFilePath()));
            } else {
                Files.deleteIfExists(Paths.get(document.getFilePath()));
            }
            
            // Remove from collections
            documents.remove(documentId);
            List<DocumentAccess> removedAccesses = documentAccesses.remove(documentId);
            if (removedAccesses != null && !removedAccesses.isEmpty()) {
                removedAccesses.forEach(access -> {
                    User sharedWith = access.getSharedWith();
                    if (sharedWith != null && sharedWith.getId() != null) {
                        List<DocumentAccess> userList = userAccesses.get(sharedWith.getId().toString());
                        if (userList != null) {
                            userList.removeIf(item -> item.getDocument() != null
                                && documentId.equals(String.valueOf(item.getDocument().getId())));
                            if (userList.isEmpty()) {
                                userAccesses.remove(sharedWith.getId().toString());
                            }
                        }
                    }
                });
            }
            notifyListeners("DELETED", document);

            try (Connection connection = databaseService.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM shared_documents WHERE id = ? AND owner_id = ?")) {
                statement.setInt(1, Integer.parseInt(documentId));
                statement.setInt(2, Integer.parseInt(userId));
                statement.executeUpdate();
            } catch (Exception ignored) {
            }
            
            return true;
        } catch (IOException e) {
            System.err.println("Failed to delete document: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get all documents for a user
     * @param userId User ID
     * @return List of documents owned by user
     */
    public List<SharedDocument> getUserDocuments(String userId) {
        List<SharedDocument> result = new ArrayList<>();
        documents.values().forEach(doc -> {
            if (doc.getOwner().getId().toString().equals(userId)) {
                result.add(doc);
            }
        });
        return result;
    }
    
    /**
     * Get all documents shared with a user
     * @param userId User ID
     * @return List of documents shared with user
     */
    public List<SharedDocument> getSharedDocuments(String userId) {
        List<SharedDocument> result = new ArrayList<>();
        List<DocumentAccess> accesses = userAccesses.get(userId);
        
        if (accesses != null) {
            for (DocumentAccess access : accesses) {
                if (access.canAccess()) {
                    // Check expiration
                    if (access.getExpiresAt() != null && LocalDateTime.now().isAfter(access.getExpiresAt())) {
                        access.setIsActive(false);
                        continue;
                    }
                    if (access.getDocument() != null) {
                        result.add(access.getDocument());
                    }
                }
            }
        }

        return result.stream().distinct().collect(Collectors.toList());
    }
    
    /**
     * Get document by ID
     * @param documentId Document ID
     * @return SharedDocument or null
     */
    public SharedDocument getDocument(String documentId) {
        String sql = "SELECT id, owner_id, file_name, file_path, mime_type, file_size, description, uploaded_at, document_type, is_public "
            + "FROM shared_documents WHERE id = ? LIMIT 1";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Integer.parseInt(documentId));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return documents.get(documentId);
                }
                Optional<User> owner = userRepository.findById(resultSet.getInt("owner_id"));
                if (owner.isEmpty()) {
                    return null;
                }
                SharedDocument document = new SharedDocument();
                document.setId(resultSet.getInt("id"));
                document.setOwner(owner.get());
                document.setFileName(resultSet.getString("file_name"));
                document.setFilePath(resultSet.getString("file_path"));
                document.setMimeType(resultSet.getString("mime_type"));
                document.setFileSize(resultSet.getInt("file_size"));
                document.setDescription(resultSet.getString("description"));
                Timestamp uploadedAt = resultSet.getTimestamp("uploaded_at");
                if (uploadedAt != null) {
                    document.setUploadedAt(uploadedAt.toLocalDateTime());
                }
                document.setDocumentType(resultSet.getString("document_type"));
                document.setPublic(resultSet.getBoolean("is_public"));
                documents.put(String.valueOf(document.getId()), document);
                return document;
            }
        } catch (Exception exception) {
            return documents.get(documentId);
        }
    }
    
    /**
     * Get access history for a document
     * @param documentId Document ID
     * @return List of document accesses
     */
    public List<DocumentAccess> getAccessHistory(String documentId) {
        String sql = "SELECT id, shared_with_id, shared_at, expires_at, accessed_at, access_count, is_active, permission "
            + "FROM document_accesses WHERE document_id = ? ORDER BY shared_at DESC";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Integer.parseInt(documentId));
            List<DocumentAccess> list = new ArrayList<>();
            SharedDocument document = getDocument(documentId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Optional<User> sharedWith = userRepository.findById(resultSet.getInt("shared_with_id"));
                    if (sharedWith.isEmpty()) {
                        continue;
                    }
                    DocumentAccess access = new DocumentAccess();
                    access.setId(resultSet.getInt("id"));
                    access.setDocument(document);
                    access.setSharedWith(sharedWith.get());
                    Timestamp sharedAt = resultSet.getTimestamp("shared_at");
                    if (sharedAt != null) {
                        access.setSharedAt(sharedAt.toLocalDateTime());
                    }
                    Timestamp expiresAt = resultSet.getTimestamp("expires_at");
                    if (expiresAt != null) {
                        access.setExpiresAt(expiresAt.toLocalDateTime());
                    }
                    Timestamp accessedAt = resultSet.getTimestamp("accessed_at");
                    if (accessedAt != null) {
                        access.setAccessedAt(accessedAt.toLocalDateTime());
                    }
                    access.setAccessCount(resultSet.getInt("access_count"));
                    access.setIsActive(resultSet.getBoolean("is_active"));
                    access.setPermission(resultSet.getString("permission"));
                    list.add(access);
                }
            }
            documentAccesses.put(documentId, new ArrayList<>(list));
            return list;
        } catch (Exception exception) {
            return documentAccesses.getOrDefault(documentId, new ArrayList<>());
        }
    }
    
    /**
     * Generate unique file name
     * @param originalFileName Original file name
     * @return Unique file name
     */
    private String generateUniqueFileName(String originalFileName) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        int dotIndex = originalFileName == null ? -1 : originalFileName.lastIndexOf('.');
        String extension = (dotIndex >= 0 && dotIndex < originalFileName.length() - 1)
            ? originalFileName.substring(dotIndex)
            : "";
        return timestamp + extension;
    }

    private boolean isSupabasePath(String filePath) {
        return filePath != null && filePath.startsWith("supabase://");
    }

    private String extractSupabaseObjectPath(String filePath) {
        if (!isSupabasePath(filePath)) {
            return "";
        }
        return filePath.substring("supabase://".length());
    }

    private String buildSupabasePath(String objectPath) {
        return "supabase://" + objectPath;
    }
    
    /**
     * Check if permission level allows required permission (hierarchical)
     * VIEW < DOWNLOAD < EDIT
     * @param userPermission User's permission level
     * @param requiredPermission Required permission level
     * @return true if user has sufficient permission
     */
    private boolean hasPermission(String userPermission, String requiredPermission) {
        Map<String, Integer> permissionLevels = new HashMap<>();
        permissionLevels.put("VIEW", 1);
        permissionLevels.put("DOWNLOAD", 2);
        permissionLevels.put("EDIT", 3);

        String normalizedUserPermission = userPermission == null ? "" : userPermission.trim().toUpperCase();
        String normalizedRequiredPermission = requiredPermission == null ? "" : requiredPermission.trim().toUpperCase();
        Integer userLevel = permissionLevels.getOrDefault(normalizedUserPermission, 0);
        Integer requiredLevel = permissionLevels.getOrDefault(normalizedRequiredPermission, 0);
        
        return userLevel >= requiredLevel;
    }
    
    /**
     * Get total storage used by a user (sum of all owned documents)
     * @param user User object
     * @return Total storage in bytes
     */
    public long getTotalStorageByUser(User user) {
        String sql = "SELECT COALESCE(SUM(file_size), 0) AS total FROM shared_documents WHERE owner_id = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, user.getId());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getLong("total");
                }
            }
        } catch (Exception ignored) {
        }
        return documents.values().stream()
            .filter(doc -> doc.getOwner().getId().equals(user.getId()))
            .mapToLong(SharedDocument::getFileSize)
            .sum();
    }
    
    /**
     * Find documents owned by user
     * @param user User object
     * @return List of documents owned by user
     */
    public List<SharedDocument> findByOwner(User user) {
        String sql = "SELECT id FROM shared_documents WHERE owner_id = ? ORDER BY uploaded_at DESC";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, user.getId());
            List<SharedDocument> list = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    SharedDocument document = getDocument(String.valueOf(resultSet.getInt("id")));
                    if (document != null) {
                        list.add(document);
                    }
                }
            }
            return list;
        } catch (Exception exception) {
            return documents.values().stream()
                .filter(doc -> doc.getOwner().getId().equals(user.getId()))
                .sorted(Comparator.comparing(SharedDocument::getUploadedAt).reversed())
                .collect(Collectors.toList());
        }
    }
    
    /**
     * Find documents shared with user (with active access)
     * @param user User object
     * @return List of documents shared with user
     */
    public List<SharedDocument> findSharedWithUser(User user) {
        String sql = "SELECT d.id FROM shared_documents d "
            + "WHERE EXISTS (SELECT 1 FROM document_accesses a "
            + "WHERE a.document_id = d.id AND a.shared_with_id = ? AND a.is_active = 1 "
            + "AND (a.expires_at IS NULL OR a.expires_at > ?)) "
            + "ORDER BY d.uploaded_at DESC";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, user.getId());
            statement.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            List<SharedDocument> list = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    SharedDocument document = getDocument(String.valueOf(resultSet.getInt("id")));
                    if (document != null) {
                        list.add(document);
                    }
                }
            }
            return list;
        } catch (Exception exception) {
            List<SharedDocument> result = new ArrayList<>();
            List<DocumentAccess> userAccessList = userAccesses.get(user.getId().toString());

            if (userAccessList != null) {
                for (DocumentAccess access : userAccessList) {
                    if (access.canAccess()) {
                        if (access.getExpiresAt() != null &&
                            LocalDateTime.now().isAfter(access.getExpiresAt())) {
                            access.setIsActive(false);
                            continue;
                        }
                        if (access.getDocument() != null) {
                            result.add(access.getDocument());
                        }
                    }
                }
            }

            return result.stream()
                .distinct()
                .sorted(Comparator.comparing(SharedDocument::getUploadedAt).reversed())
                .collect(Collectors.toList());
        }
    }
    
    /**
     * Add listener for document storage events
     * @param listener DocumentStorageListener
     */
    public void addListener(DocumentStorageListener listener) {
        listeners.add(listener);
    }
    
    /**
     * Remove listener
     * @param listener DocumentStorageListener
     */
    public void removeListener(DocumentStorageListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * Notify all listeners of document storage event
     * @param eventType Event type (UPLOADED, SHARED, REVOKED, DELETED, etc.)
     * @param document Affected document
     */
    private void notifyListeners(String eventType, SharedDocument document) {
        listeners.forEach(listener -> listener.onDocumentEvent(eventType, document));
    }
    
    /**
     * Interface for document storage event listeners
     */
    public interface DocumentStorageListener {
        void onDocumentEvent(String eventType, SharedDocument document);
    }
}
