package com.santea.controller;

import com.santea.model.User;
import com.santea.navigation.AppNavigator;
import com.santea.service.AuthService;
import com.santea.ui.ConfirmDialogs;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class BannedViewController {
    private static final DateTimeFormatter BAN_UNTIL_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy 'a' HH:mm");
    private static final List<String> MEME_IMAGES = List.of(
            "/com/santea/images/carton1.gif",
            "/com/santea/images/carton2.gif",
            "/com/santea/images/arja3.gif"
    );

    private final AuthService authService = new AuthService();

    @FXML
    private StackPane memeOverlay;

    @FXML
    private ImageView memeImageView;

    @FXML
    private Label banReasonLabel;

    @FXML
    private Label banUntilLabel;

    @FXML
    private void initialize() {
        playMemePopup();
    }

    public void setUser(User user) {
        if (user == null) {
            banReasonLabel.setText("Violation des regles de la plateforme");
            banUntilLabel.setText("Non specifiee");
            return;
        }

        String reason = safe(user.getBanReason());
        banReasonLabel.setText(reason.isBlank() ? "Suspension administrative" : reason);

        LocalDateTime until = user.getBanUntil();
        banUntilLabel.setText(until == null ? "Non specifiee" : BAN_UNTIL_FORMAT.format(until));
    }

    @FXML
    private void handleLogout() {
        ConfirmDialogs.confirmLogout(banReasonLabel, () -> {
            authService.logout();
            AppNavigator.showLogin();
        });
    }

    @FXML
    private void handleGoHome() {
        AppNavigator.showHome();
    }

    private void playMemePopup() {
        if (memeOverlay == null || memeImageView == null) {
            return;
        }

        String imagePath = MEME_IMAGES.get(ThreadLocalRandom.current().nextInt(MEME_IMAGES.size()));
        Image image = new Image(getClass().getResourceAsStream(imagePath));
        memeImageView.setImage(image);

        PauseTransition pause = new PauseTransition(Duration.seconds(4));
        pause.setOnFinished(event -> {
            FadeTransition fadeOut = new FadeTransition(Duration.millis(800), memeOverlay);
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);
            fadeOut.setOnFinished(done -> {
                memeOverlay.setVisible(false);
                memeOverlay.setManaged(false);
            });
            fadeOut.play();
        });
        pause.play();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
