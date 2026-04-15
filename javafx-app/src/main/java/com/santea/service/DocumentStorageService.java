package com.santea.service;

import com.santea.model.SharedDocument;
import com.santea.model.DocumentAccess;
import com.santea.model.User;
import java.io.File;
import java.io.IOException;
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
            Path filePath = Paths.get(STORAGE_BASE_PATH, owner.getId().toString(), uniqueFileName);
            
            // Create user directory if needed
            Files.createDirectories(filePath.getParent());
            
            // Save file
            Files.write(filePath, fileContent);
            
            // Create document metadata
            SharedDocument document = new SharedDocument();
            document.setFileName(fileName);
            document.setFilePath(filePath.toString());
            document.setMimeType(mimeType);
            document.setFileSize(fileContent.length);
            document.setOwner(owner);
            document.setDescription(description);
            document.setDocumentType(normalizedType);
            document.setUploadedAt(LocalDateTime.now());
            document.setPublic(false);
            
            // Generate and set unique ID
            String docId = UUID.randomUUID().toString();
            document.setId(docId.hashCode()); // Convert to integer ID
            
            // Store document
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
        SharedDocument document = documents.get(documentId);
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
        
        List<DocumentAccess> accesses = documentAccesses.get(documentId);
        for (DocumentAccess existing : accesses) {
            if (existing.getSharedWith().getId().equals(sharedWithUser.getId())) {
                existing.setPermission(normalizedPermission);
                existing.setExpiresAt(expiresAt);
                existing.setIsActive(true);
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
        List<DocumentAccess> accesses = documentAccesses.get(documentId);
        if (accesses != null) {
            for (DocumentAccess access : accesses) {
                if (access.getSharedWith().getId().equals(Integer.parseInt(userId))) {
                    access.setIsActive(false);
                    notifyListeners("REVOKED", access.getDocument());
                    return true;
                }
            }
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
        SharedDocument document = documents.get(documentId);
        if (document == null) {
            return false;
        }
        
        // Owner always has full access
        if (document.getOwner().getId().toString().equals(userId)) {
            return true;
        }

        if (document.isPublic() && ("view".equalsIgnoreCase(requiredPermission) || "download".equalsIgnoreCase(requiredPermission))) {
            return true;
        }
        
        List<DocumentAccess> accesses = documentAccesses.get(documentId);
        if (accesses == null) {
            return false;
        }
        
        for (DocumentAccess access : accesses) {
            if (access.getSharedWith().getId().toString().equals(userId) && access.canAccess()) {
                // Check expiration
                if (access.getExpiresAt() != null && LocalDateTime.now().isAfter(access.getExpiresAt())) {
                    access.setIsActive(false);
                    return false;
                }
                
                // Check permission
                return hasPermission(access.getPermission(), requiredPermission);
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
        
        SharedDocument document = documents.get(documentId);
        if (document == null) {
            return null;
        }
        
        try {
            return Files.readAllBytes(Paths.get(document.getFilePath()));
        } catch (IOException e) {
            System.err.println("Failed to download document: " + e.getMessage());
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
        SharedDocument document = documents.get(documentId);
        if (document == null || !document.getOwner().getId().toString().equals(userId)) {
            return false;
        }
        
        try {
            // Delete file from storage
            Files.deleteIfExists(Paths.get(document.getFilePath()));
            
            // Remove from collections
            documents.remove(documentId);
            documentAccesses.remove(documentId);
            notifyListeners("DELETED", document);
            
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
                if (access.getIsActive()) {
                    // Check expiration
                    if (access.getExpiresAt() != null && LocalDateTime.now().isAfter(access.getExpiresAt())) {
                        access.setIsActive(false);
                        continue;
                    }
                    result.add(access.getDocument());
                }
            }
        }
        
        return result;
    }
    
    /**
     * Get document by ID
     * @param documentId Document ID
     * @return SharedDocument or null
     */
    public SharedDocument getDocument(String documentId) {
        return documents.get(documentId);
    }
    
    /**
     * Get access history for a document
     * @param documentId Document ID
     * @return List of document accesses
     */
    public List<DocumentAccess> getAccessHistory(String documentId) {
        return documentAccesses.getOrDefault(documentId, new ArrayList<>());
    }
    
    /**
     * Generate unique file name
     * @param originalFileName Original file name
     * @return Unique file name
     */
    private String generateUniqueFileName(String originalFileName) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String extension = originalFileName.substring(originalFileName.lastIndexOf('.'));
        return timestamp + extension;
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
        return documents.values().stream()
            .filter(doc -> doc.getOwner().getId().equals(user.getId()))
            .sorted(Comparator.comparing(SharedDocument::getUploadedAt).reversed())
            .collect(Collectors.toList());
    }
    
    /**
     * Find documents shared with user (with active access)
     * @param user User object
     * @return List of documents shared with user
     */
    public List<SharedDocument> findSharedWithUser(User user) {
        List<SharedDocument> result = new ArrayList<>();
        List<DocumentAccess> userAccessList = userAccesses.get(user.getId().toString());
        
        if (userAccessList != null) {
            for (DocumentAccess access : userAccessList) {
                if (access.getIsActive()) {
                    // Check expiration
                    if (access.getExpiresAt() != null && 
                        LocalDateTime.now().isAfter(access.getExpiresAt())) {
                        access.setIsActive(false);
                        continue;
                    }
                    result.add(access.getDocument());
                }
            }
        }
        
        return result.stream()
            .distinct()
            .sorted(Comparator.comparing(SharedDocument::getUploadedAt).reversed())
            .collect(Collectors.toList());
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
