package com.santea.controller;

import com.santea.model.Teleconsultation;
import com.santea.model.User;
import com.santea.navigation.AppNavigator;
import com.santea.navigation.ModuleContext;
import com.santea.service.AuthSession;
import com.santea.service.TeleconsultationService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.web.WebView;

public class TeleconsultationJoinController extends AppBaseViewController {
    @FXML private Button backButton;
    @FXML private WebView jitsiWebView;

    private final TeleconsultationService service = new TeleconsultationService();
    private Teleconsultation consultation;

    @Override
    public void initialize(java.net.URL location, java.util.ResourceBundle resources) {
        super.initialize(location, resources);
        Integer id = ModuleContext.getTeleconsultationId();
        if (id == null) {
            throw new IllegalStateException("Aucune téléconsultation sélectionnée");
        }
        consultation = service.getTeleconsultation(String.valueOf(id));
        if (consultation == null) {
            throw new IllegalStateException("Téléconsultation introuvable");
        }
        User currentUser = AuthSession.getCurrentUser();
        if (currentUser == null || !isParticipant(consultation, currentUser)) {
            throw new IllegalStateException("Accès refusé à cette téléconsultation");
        }
        if (!service.canStartConsultation(consultation) && !consultation.isOngoing()) {
            throw new IllegalStateException("Cette téléconsultation n'est pas encore disponible");
        }
        if (!consultation.isOngoing()) {
            Teleconsultation updated = service.startTeleconsultation(String.valueOf(id));
            if (updated != null) {
                consultation = updated;
            }
        }
        jitsiWebView.getEngine().load(consultation.getJitsiRoomUrl());
        backButton.setOnAction(event -> AppNavigator.showTeleconsultationShow(consultation.getId()));
    }

    private boolean isParticipant(Teleconsultation teleconsultation, User user) {
        return teleconsultation != null && user != null && user.getId() != null && (
            (teleconsultation.getInitiator() != null
                && teleconsultation.getInitiator().getId() != null
                && teleconsultation.getInitiator().getId().equals(user.getId()))
            || (teleconsultation.getRecipient() != null
                && teleconsultation.getRecipient().getId() != null
                && teleconsultation.getRecipient().getId().equals(user.getId()))
        );
    }
}
