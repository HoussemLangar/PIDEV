package com.santea.navigation;

public final class ModuleContext {
    private static Integer teleconsultationId;
    private static Integer documentId;
    private static Integer conversationId;

    private ModuleContext() {
    }

    public static Integer getTeleconsultationId() {
        return teleconsultationId;
    }

    public static void setTeleconsultationId(Integer teleconsultationId) {
        ModuleContext.teleconsultationId = teleconsultationId;
    }

    public static Integer getDocumentId() {
        return documentId;
    }

    public static void setDocumentId(Integer documentId) {
        ModuleContext.documentId = documentId;
    }

    public static Integer getConversationId() {
        return conversationId;
    }

    public static void setConversationId(Integer conversationId) {
        ModuleContext.conversationId = conversationId;
    }
}
