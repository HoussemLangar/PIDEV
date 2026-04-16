package com.santea.navigation;

public final class ModuleContext {
    private static Integer teleconsultationId;
    private static Integer documentId;
    private static Integer conversationId;
    private static Integer accompanimentPlanId;
    private static Integer accompanimentPatientUserId;

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

    public static Integer getAccompanimentPlanId() {
        return accompanimentPlanId;
    }

    public static void setAccompanimentPlanId(Integer accompanimentPlanId) {
        ModuleContext.accompanimentPlanId = accompanimentPlanId;
    }

    public static Integer getAccompanimentPatientUserId() {
        return accompanimentPatientUserId;
    }

    public static void setAccompanimentPatientUserId(Integer accompanimentPatientUserId) {
        ModuleContext.accompanimentPatientUserId = accompanimentPatientUserId;
    }
}
