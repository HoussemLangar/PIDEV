package com.santea.repository;

import com.santea.model.SharedDocument;
import com.santea.model.DocumentAccess;
import java.time.LocalDateTime;
import java.util.*;

/**
 * DocumentRepository
 * Data access layer for SharedDocument and DocumentAccess entities
 */
public class DocumentRepository {
    
    private Map<String, SharedDocument> documents = new HashMap<>();
    private Map<String, List<DocumentAccess>> accesses = new HashMap<>();
    
    /**
     * Save a document
     * @param document Document to save
     * @return Saved document
     */
    public SharedDocument saveDocument(SharedDocument document) {
        String docId = String.valueOf(document.getId());
        documents.put(docId, document);
        if (!accesses.containsKey(docId)) {
            accesses.put(docId, new ArrayList<>());
        }
        return document;
    }
    
    /**
     * Find document by ID
     * @param id Document ID
     * @return SharedDocument or null
     */
    public SharedDocument findDocumentById(String id) {
        return documents.get(id);
    }
    
    /**
     * Find all documents by owner
     * @param ownerId Owner ID
     * @return List of documents
     */
    public List<SharedDocument> findByOwnerId(String ownerId) {
        List<SharedDocument> result = new ArrayList<>();
        documents.values().forEach(doc -> {
            if (doc.getOwner().getId().toString().equals(ownerId)) {
                result.add(doc);
            }
        });
        return result;
    }
    
    /**
     * Find all documents by document type
     * @param documentType Document type
     * @return List of documents
     */
    public List<SharedDocument> findByDocumentType(String documentType) {
        List<SharedDocument> result = new ArrayList<>();
        documents.values().forEach(doc -> {
            if (doc.getDocumentType() != null && doc.getDocumentType().equals(documentType)) {
                result.add(doc);
            }
        });
        return result;
    }
    
    /**
     * Find all documents
     * @return All documents
     */
    public List<SharedDocument> findAllDocuments() {
        return new ArrayList<>(documents.values());
    }
    
    /**
     * Delete document by ID
     * @param id Document ID
     * @return true if deleted
     */
    public boolean deleteDocument(String id) {
        accesses.remove(id);
        return documents.remove(id) != null;
    }
    
    /**
     * Save document access
     * @param access DocumentAccess to save
     * @param documentId Document ID
     * @return Saved access
     */
    public DocumentAccess saveAccess(DocumentAccess access, String documentId) {
        accesses.computeIfAbsent(documentId, k -> new ArrayList<>()).add(access);
        return access;
    }
    
    /**
     * Find all accesses for a document
     * @param documentId Document ID
     * @return List of accesses
     */
    public List<DocumentAccess> findAccessesByDocumentId(String documentId) {
        return new ArrayList<>(accesses.getOrDefault(documentId, new ArrayList<>()));
    }
    
    /**
     * Find active accesses for a document
     * @param documentId Document ID
     * @return List of active accesses
     */
    public List<DocumentAccess> findActiveAccessesByDocumentId(String documentId) {
        List<DocumentAccess> result = new ArrayList<>();
        List<DocumentAccess> allAccesses = accesses.getOrDefault(documentId, new ArrayList<>());
        
        allAccesses.forEach(access -> {
            if (access.getIsActive()) {
                if (access.getExpiresAt() == null || LocalDateTime.now().isBefore(access.getExpiresAt())) {
                    result.add(access);
                }
            }
        });
        
        return result;
    }
    
    /**
     * Find accesses for a user
     * @param userId User ID
     * @return List of accesses
     */
    public List<DocumentAccess> findAccessesByUserId(String userId) {
        List<DocumentAccess> result = new ArrayList<>();
        accesses.values().forEach(accessList -> {
            accessList.forEach(access -> {
                if (access.getSharedWith().getId().toString().equals(userId) && access.getIsActive()) {
                    result.add(access);
                }
            });
        });
        return result;
    }
    
    /**
     * Find access by ID
     * @param accessId Access ID
     * @return DocumentAccess or null
     */
    public DocumentAccess findAccessById(String accessId) {
        for (List<DocumentAccess> accessList : accesses.values()) {
            for (DocumentAccess access : accessList) {
                if (access.getId().toString().equals(accessId)) {
                    return access;
                }
            }
        }
        return null;
    }
    
    /**
     * Delete access by ID
     * @param documentId Document ID
     * @param accessId Access ID
     * @return true if deleted
     */
    public boolean deleteAccess(String documentId, String accessId) {
        List<DocumentAccess> docAccesses = accesses.get(documentId);
        if (docAccesses != null) {
            return docAccesses.removeIf(access -> access.getId().toString().equals(accessId));
        }
        return false;
    }
    
    /**
     * Count documents
     * @return Total document count
     */
    public long countDocuments() {
        return documents.size();
    }
    
    /**
     * Count accesses
     * @return Total access count
     */
    public long countAccesses() {
        return accesses.values().stream().mapToLong(List::size).sum();
    }
}
