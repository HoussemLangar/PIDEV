package com.santea.controller;

import com.santea.model.User;
import com.santea.navigation.AppNavigator;
import com.santea.service.AuthService;
import com.santea.service.AuthSession;
import com.santea.service.FaceVerificationService;
import com.github.sarxos.webcam.Webcam;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class AdminFaceVerificationController {
    private static final int CAMERA_OPEN_TIMEOUT_SECONDS = 6;
    private static final int CAMERA_DISCOVERY_TIMEOUT_SECONDS = 5;

    private final FaceVerificationService faceService = new FaceVerificationService();
    private final AuthService authService = new AuthService();
    private final ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();

    @FXML
    private Label infoBannerLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private ImageView previewImageView;

    @FXML
    private Button registerFaceButton;

    @FXML
    private Button verifyFaceButton;

    @FXML
    private Button startCameraButton;

    @FXML
    private Button stopCameraButton;

    private volatile boolean cameraRunning;
    private Webcam webcam;
    private volatile Image liveFrame;

    @FXML
    private void initialize() {
        refreshState();

        previewImageView.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null && newScene.getWindow() != null) {
                newScene.getWindow().setOnHidden(event -> stopCamera());
            }
        });
    }

    @FXML
    private void handleStartCamera() {
        if (cameraRunning) {
            return;
        }

        statusLabel.setText("Initialisation de la camera...");
        cameraExecutor.submit(() -> {
            try {
                closeAnyOpenWebcams();
                Webcam defaultWebcam = findDefaultWebcamWithTimeout(CAMERA_DISCOVERY_TIMEOUT_SECONDS);
                if (defaultWebcam == null) {
                    Platform.runLater(() -> statusLabel.setText(
                            "Camera introuvable ou non repondante. Verifiez /dev/video0, permissions et qu'aucune autre application n'utilise la webcam."
                    ));
                    return;
                }

                webcam = defaultWebcam;
                webcam.setViewSize(selectStableResolution(webcam));
                if (!openCameraWithTimeout(webcam, CAMERA_OPEN_TIMEOUT_SECONDS)) {
                    Platform.runLater(() -> statusLabel.setText(
                            "Camera detectee mais flux indisponible. Fermez les autres apps camera puis reessayez."
                    ));
                    return;
                }
                cameraRunning = true;

                Platform.runLater(() -> {
                    statusLabel.setText("Camera active en temps reel (" + webcam.getViewSize().width + "x" + webcam.getViewSize().height + ").");
                    stopCameraButton.setDisable(false);
                    startCameraButton.setDisable(true);
                });

                int consecutiveNullFrames = 0;
                while (cameraRunning && webcam.isOpen()) {
                    try {
                        BufferedImage buffered = webcam.getImage();
                        if (buffered != null) {
                            consecutiveNullFrames = 0;
                            Image frame = SwingFXUtils.toFXImage(buffered, null);
                            liveFrame = frame;
                            Platform.runLater(() -> previewImageView.setImage(frame));
                        } else {
                            consecutiveNullFrames++;
                        }
                    } catch (Exception readException) {
                        consecutiveNullFrames++;
                    }

                    if (consecutiveNullFrames >= 20) {
                        Platform.runLater(() -> statusLabel.setText("Flux camera instable. Essayez une autre resolution ou redemarrez la camera."));
                        consecutiveNullFrames = 0;
                    }

                    try {
                        TimeUnit.MILLISECONDS.sleep(80);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (Exception exception) {
                Platform.runLater(() -> statusLabel.setText("Impossible d'ouvrir la camera: " + exception.getMessage()));
            }
        });
    }

    @FXML
    private void handleStopCamera() {
        stopCamera();
        statusLabel.setText("Camera arretee.");
    }

    @FXML
    private void handleRegisterFace() {
        User user = AuthSession.getCurrentUser();
        if (user == null || user.getId() == null) {
            statusLabel.setText("Session invalide.");
            return;
        }

        if (liveFrame == null) {
            statusLabel.setText("Demarrez la camera puis cadrez votre visage avant enregistrement.");
            return;
        }

        FaceVerificationService.FaceProcessResult result = faceService.registerFace(user.getId(), liveFrame);
        statusLabel.setText(result.message());

        if (result.success()) {
            AuthSession.setFaceVerified(true);
            stopCamera();
            AppNavigator.showAdminDashboard();
        }
    }

    @FXML
    private void handleVerifyFace() {
        User user = AuthSession.getCurrentUser();
        if (user == null || user.getId() == null) {
            statusLabel.setText("Session invalide.");
            return;
        }

        if (liveFrame == null) {
            statusLabel.setText("Demarrez la camera puis cadrez votre visage avant verification.");
            return;
        }

        FaceVerificationService.FaceProcessResult result = faceService.verifyFace(user.getId(), liveFrame);
        if (result.success()) {
            statusLabel.setText(result.message() + " Distance: " + Math.round(result.distance() * 100.0) / 100.0);
            AuthSession.setFaceVerified(true);
            stopCamera();
            AppNavigator.showAdminDashboard();
            return;
        }

        statusLabel.setText(result.message());
    }

    @FXML
    private void handleLogout() {
        stopCamera();
        authService.logout();
        AppNavigator.showLogin();
    }

    private void refreshState() {
        User user = AuthSession.getCurrentUser();
        if (user == null || user.getId() == null) {
            infoBannerLabel.setText("Utilisateur admin non connecte.");
            registerFaceButton.setDisable(true);
            verifyFaceButton.setDisable(true);
            return;
        }

        boolean hasFace = faceService.hasRegisteredFace(user.getId());
        if (hasFace) {
            infoBannerLabel.setText("Visage deja enregistre. Verifiez votre identite pour acceder au dashboard admin.");
            registerFaceButton.setText("Face ID deja enregistre");
            registerFaceButton.setDisable(true);
            verifyFaceButton.setDisable(false);
        } else {
            infoBannerLabel.setText("Vous devez d'abord enregistrer votre visage pour activer la reconnaissance faciale.");
            registerFaceButton.setText("Enregistrer mon visage");
            registerFaceButton.setDisable(false);
            verifyFaceButton.setDisable(true);
        }

        stopCameraButton.setDisable(true);
        startCameraButton.setDisable(false);
    }

    private void stopCamera() {
        cameraRunning = false;

        if (webcam != null) {
            try {
                webcam.close();
            } catch (Exception ignored) {
            }
        }

        webcam = null;
        liveFrame = null;

        Platform.runLater(() -> {
            stopCameraButton.setDisable(true);
            startCameraButton.setDisable(false);
        });
    }

    private boolean openCameraWithTimeout(Webcam targetWebcam, int timeoutSeconds) {
        try {
            targetWebcam.open(true);
            long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000L);

            while (System.currentTimeMillis() < deadline) {
                if (targetWebcam.isOpen()) {
                    try {
                        BufferedImage warmup = targetWebcam.getImage();
                        if (warmup != null) {
                            liveFrame = SwingFXUtils.toFXImage(warmup, null);
                            return true;
                        }
                    } catch (Exception ignored) {
                    }
                }
                TimeUnit.MILLISECONDS.sleep(120);
            }

            safeClose(targetWebcam);
            return false;
        } catch (Exception exception) {
            safeClose(targetWebcam);
            return false;
        }
    }

    private Webcam findDefaultWebcamWithTimeout(int timeoutSeconds) {
        ExecutorService finder = Executors.newSingleThreadExecutor();
        try {
            Future<Webcam> future = finder.submit(() -> Webcam.getDefault());
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception exception) {
            return null;
        } finally {
            finder.shutdownNow();
        }
    }

    private void closeAnyOpenWebcams() {
        try {
            for (Webcam candidate : Webcam.getWebcams()) {
                if (candidate != null && candidate.isOpen()) {
                    safeClose(candidate);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void safeClose(Webcam targetWebcam) {
        if (targetWebcam == null) {
            return;
        }
        try {
            targetWebcam.close();
        } catch (Exception ignored) {
        }
    }


    private Dimension selectStableResolution(Webcam webcamInstance) {
        Dimension[] supported = webcamInstance.getViewSizes();
        if (supported == null || supported.length == 0) {
            return new Dimension(640, 480);
        }

        // Prefer lower/standard resolutions first to reduce MJPEG decode errors on some Linux UVC webcams.
        int[][] preferred = new int[][] {
                {320, 240},
                {640, 480},
                {800, 600},
                {1280, 720}
        };

        for (int[] pref : preferred) {
            for (Dimension candidate : supported) {
                if (candidate.width == pref[0] && candidate.height == pref[1]) {
                    return candidate;
                }
            }
        }

        return Arrays.stream(supported)
                .min(Comparator.comparingInt(d -> d.width * d.height))
                .orElse(new Dimension(640, 480));
    }
}
