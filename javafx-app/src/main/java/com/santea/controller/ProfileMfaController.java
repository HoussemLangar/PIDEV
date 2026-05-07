package com.santea.controller;

import com.santea.model.User;
import com.santea.navigation.AppNavigator;
import com.santea.service.AuthService;
import com.santea.service.AuthSession;
import com.santea.service.ProfileService;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class ProfileMfaController {
    private final AuthService authService = new AuthService();
    private final ProfileService profileService = new ProfileService();

    @FXML
    private Label feedbackLabel;

    @FXML
    private Label mfaStatusLabel;

    @FXML
    private Label qrUriLabel;

    @FXML
    private ImageView qrImageView;

    @FXML
    private VBox qrCard;

    @FXML
    private VBox inactiveCard;

    @FXML
    private VBox enableModal;

    @FXML
    private VBox disableModal;

    @FXML
    private PasswordField enablePasswordField;

    @FXML
    private PasswordField disablePasswordField;

    @FXML
    private TextField enablePasswordVisibleField;

    @FXML
    private TextField disablePasswordVisibleField;

    @FXML
    private TextField enableCodeField;

    @FXML
    private Label enableQrUriLabel;

    @FXML
    private ImageView enableQrImageView;

    @FXML
    private VBox enableSetupBox;

    @FXML
    private Button enableConfirmButton;

    private AuthService.ProfileMfaSetupResult pendingSetup;

    @FXML
    private void initialize() {
        refreshState();
    }

    @FXML
    private void handleBackHome() {
        AppNavigator.showHome();
    }

    @FXML
    private void handleOpenProfilePage() {
        AppNavigator.showProfileSettings();
    }

    @FXML
    private void handleOpenSubscriptionPage() {
        AppNavigator.showSubscriptionPage();
    }

    @FXML
    private void handleOpenConfidentialite() {
        showFeedback("Section Confidentialite disponible prochainement.", true);
    }

    @FXML
    private void handleOpenEnableModal() {
        pendingSetup = null;

        enablePasswordField.clear();
        enablePasswordVisibleField.clear();
        enableCodeField.clear();
        enablePasswordVisibleField.setVisible(false);
        enablePasswordVisibleField.setManaged(false);
        enablePasswordField.setVisible(true);
        enablePasswordField.setManaged(true);

        enableQrUriLabel.setText("-");
        enableQrImageView.setImage(null);
        enableSetupBox.setVisible(false);
        enableSetupBox.setManaged(false);
        enableConfirmButton.setDisable(true);

        enableModal.setVisible(true);
        enableModal.setManaged(true);
    }

    @FXML
    private void handlePrepareEnableMfa() {
        User user = AuthSession.getCurrentUser();
        if (user == null) {
            showFeedback("Session invalide.", false);
            return;
        }

        String password = enablePasswordField.isVisible() ? enablePasswordField.getText() : enablePasswordVisibleField.getText();
        AuthService.ActionResult passwordCheck = authService.verifyCurrentPassword(user, password);
        if (!passwordCheck.success()) {
            showFeedback(passwordCheck.message(), false);
            return;
        }

        pendingSetup = authService.generateMfaSetup(user);
        if (!pendingSetup.success()) {
            showFeedback(pendingSetup.message(), false);
            return;
        }

        enableQrUriLabel.setText(pendingSetup.provisioningUri());
        Image qr = buildQrImage(pendingSetup.provisioningUri());
        enableQrImageView.setImage(qr);
        enableSetupBox.setVisible(true);
        enableSetupBox.setManaged(true);
        enableConfirmButton.setDisable(false);
        showFeedback("Mot de passe valide. Scannez le QR puis entrez le code OTP.", true);
    }

    @FXML
    private void handleOpenDisableModal() {
        disablePasswordField.clear();
        disablePasswordVisibleField.clear();
        disablePasswordVisibleField.setVisible(false);
        disablePasswordVisibleField.setManaged(false);
        disablePasswordField.setVisible(true);
        disablePasswordField.setManaged(true);
        disableModal.setVisible(true);
        disableModal.setManaged(true);
    }

    @FXML
    private void handleCloseEnableModal() {
        pendingSetup = null;
        enableSetupBox.setVisible(false);
        enableSetupBox.setManaged(false);
        enableConfirmButton.setDisable(true);
        enableModal.setVisible(false);
        enableModal.setManaged(false);
    }

    @FXML
    private void handleCloseDisableModal() {
        disableModal.setVisible(false);
        disableModal.setManaged(false);
    }

    @FXML
    private void handleEnableMfa() {
        User user = AuthSession.getCurrentUser();
        if (user == null) {
            showFeedback("Session invalide.", false);
            return;
        }

        if (pendingSetup == null || !pendingSetup.success()) {
            showFeedback("Configuration MFA indisponible. Reessayez.", false);
            return;
        }

        String code = enableCodeField.getText() == null ? "" : enableCodeField.getText().trim();
        if (!code.matches("\\d{6}")) {
            showFeedback("Entrez un code Google Authenticator a 6 chiffres.", false);
            return;
        }

        AuthService.ActionResult result = authService.enableMfa(user, pendingSetup.secret(), code);
        showFeedback(result.message(), result.success());
        if (result.success()) {
            handleCloseEnableModal();
            refreshState();
        }
    }

    @FXML
    private void handleDisableMfa() {
        User user = AuthSession.getCurrentUser();
        if (user == null) {
            showFeedback("Session invalide.", false);
            return;
        }

        String password = disablePasswordField.isVisible() ? disablePasswordField.getText() : disablePasswordVisibleField.getText();
        AuthService.ActionResult passwordCheck = authService.verifyCurrentPassword(user, password);
        if (!passwordCheck.success()) {
            showFeedback(passwordCheck.message(), false);
            return;
        }

        AuthService.ActionResult result = authService.disableMfa(user);
        showFeedback(result.message(), result.success());
        handleCloseDisableModal();
        refreshState();
    }

    private void refreshState() {
        User user = AuthSession.getCurrentUser();
        if (user == null) {
            showFeedback("Aucun utilisateur connecte.", false);
            return;
        }

        ProfileService.ProfileResult profile = profileService.loadCurrentUserProfile(user);
        if (!profile.success() || profile.user() == null) {
            showFeedback(profile.message(), false);
            return;
        }

        User fresh = profile.user();
        boolean enabled = Boolean.TRUE.equals(fresh.getMfaEnabled());

        mfaStatusLabel.setText(enabled ? "MFA active." : "MFA non active");
        inactiveCard.setVisible(!enabled);
        inactiveCard.setManaged(!enabled);
        qrCard.setVisible(enabled);
        qrCard.setManaged(enabled);

        if (enabled) {
            String secret = fresh.getGoogleAuthenticatorSecret();
            if (secret == null || secret.isBlank()) {
                showFeedback("Secret MFA manquant. Desactivez puis reactivez la MFA.", false);
                qrUriLabel.setText("-");
                qrImageView.setImage(null);
                return;
            }
            String account = (fresh.getEmail() == null || fresh.getEmail().isBlank()) ? fresh.getUsername() : fresh.getEmail();
            String uri = authService.getTwoFactorService().provisioningUri("SANTEA", account, secret);
            qrUriLabel.setText(uri);
            Image image = buildQrImage(uri);
            if (image != null) {
                qrImageView.setImage(image);
            }
        } else {
            qrUriLabel.setText("-");
            qrImageView.setImage(null);
        }
    }

    private Image buildQrImage(String otpauthUri) {
        if (otpauthUri == null || otpauthUri.isBlank()) {
            return null;
        }
        try {
            String encoded = URLEncoder.encode(otpauthUri, StandardCharsets.UTF_8);
            return new Image("https://api.qrserver.com/v1/create-qr-code/?size=220x220&data=" + encoded, true);
        } catch (Exception exception) {
            return null;
        }
    }

    @FXML
    private void toggleEnablePasswordVisibility() {
        if (enablePasswordVisibleField.isVisible()) {
            enablePasswordField.setText(enablePasswordVisibleField.getText());
            enablePasswordVisibleField.setVisible(false);
            enablePasswordVisibleField.setManaged(false);
            enablePasswordField.setVisible(true);
            enablePasswordField.setManaged(true);
            return;
        }
        enablePasswordVisibleField.setText(enablePasswordField.getText());
        enablePasswordField.setVisible(false);
        enablePasswordField.setManaged(false);
        enablePasswordVisibleField.setVisible(true);
        enablePasswordVisibleField.setManaged(true);
    }

    @FXML
    private void toggleDisablePasswordVisibility() {
        if (disablePasswordVisibleField.isVisible()) {
            disablePasswordField.setText(disablePasswordVisibleField.getText());
            disablePasswordVisibleField.setVisible(false);
            disablePasswordVisibleField.setManaged(false);
            disablePasswordField.setVisible(true);
            disablePasswordField.setManaged(true);
            return;
        }
        disablePasswordVisibleField.setText(disablePasswordField.getText());
        disablePasswordField.setVisible(false);
        disablePasswordField.setManaged(false);
        disablePasswordVisibleField.setVisible(true);
        disablePasswordVisibleField.setManaged(true);
    }

    private void showFeedback(String message, boolean success) {
        feedbackLabel.setText(message);
        feedbackLabel.getStyleClass().removeAll("alert-success", "alert-danger");
        feedbackLabel.getStyleClass().add(success ? "alert-success" : "alert-danger");
        feedbackLabel.setVisible(true);
        feedbackLabel.setManaged(true);
    }
}
