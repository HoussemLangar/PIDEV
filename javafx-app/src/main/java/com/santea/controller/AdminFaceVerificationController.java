package com.santea.controller;

import com.santea.model.User;
import com.santea.navigation.AppNavigator;
import com.santea.service.AuthService;
import com.santea.service.AuthSession;
import com.santea.service.FaceVerificationService;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameGrabber;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class AdminFaceVerificationController {
    private static final int CAMERA_WIDTH = 320;
    private static final int CAMERA_HEIGHT = 240;
    private static final double CAMERA_FPS = 30.0;
    private static final long PREVIEW_FRAME_INTERVAL_MS = 140;
    private static final int CAMERA_OPEN_TIMEOUT_MS = 3000;
    private static final int CAMERA_READ_TIMEOUT_MS = 2500;
    private static final int MAX_EMPTY_FRAMES = 4;
    private static final int STARTUP_FRAME_PROBE_ATTEMPTS = 12;
    private static final int CANDIDATE_INIT_TIMEOUT_SECONDS = 6;

    private final FaceVerificationService faceService = new FaceVerificationService();
    private final AuthService authService = new AuthService();
    private final ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();
    private final Java2DFrameConverter frameConverter = new Java2DFrameConverter();

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
    private FrameGrabber frameGrabber;
    private volatile String activeCameraSource;
    private volatile Image liveFrame;
    private final AtomicBoolean uiFrameUpdatePending = new AtomicBoolean(false);
    private volatile long lastPreviewFrameAtMs;

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
                GrabberSelection selection = openBestGrabber();
                if (selection == null || selection.grabber() == null || selection.previewFrame() == null) {
                    Platform.runLater(() -> {
                        statusLabel.setText("Camera detectee mais aucun flux reel recu. En VM, activez le passthrough USB webcam VMware puis relancez.");
                        stopCameraButton.setDisable(true);
                        startCameraButton.setDisable(false);
                    });
                    return;
                }

                frameGrabber = selection.grabber();
                activeCameraSource = selection.sourceLabel();
                cameraRunning = true;
                liveFrame = selection.previewFrame();

                Platform.runLater(() -> {
                    previewImageView.setImage(selection.previewFrame());
                    statusLabel.setText("Camera active en temps reel (" + CAMERA_WIDTH + "x" + CAMERA_HEIGHT + ") via " + activeCameraSource + ".");
                    stopCameraButton.setDisable(false);
                    startCameraButton.setDisable(true);
                });

                int consecutiveNullFrames = 0;
                while (cameraRunning && frameGrabber != null) {
                    try {
                        Frame grabbed = frameGrabber.grab();
                        BufferedImage buffered = grabbed == null ? null : frameConverter.getBufferedImage(grabbed);
                        if (buffered != null) {
                            consecutiveNullFrames = 0;
                            long now = System.currentTimeMillis();
                            if ((now - lastPreviewFrameAtMs) >= PREVIEW_FRAME_INTERVAL_MS
                                    && uiFrameUpdatePending.compareAndSet(false, true)) {
                                Image frame = SwingFXUtils.toFXImage(buffered, null);
                                liveFrame = frame;
                                lastPreviewFrameAtMs = now;
                                Platform.runLater(() -> {
                                    try {
                                        previewImageView.setImage(frame);
                                    } finally {
                                        uiFrameUpdatePending.set(false);
                                    }
                                });
                            }
                        } else {
                            consecutiveNullFrames++;
                        }
                    } catch (Exception readException) {
                        consecutiveNullFrames++;
                    }

                    if (consecutiveNullFrames >= MAX_EMPTY_FRAMES) {
                        cameraRunning = false;
                        stopFrameGrabber();
                        Platform.runLater(() -> {
                            statusLabel.setText("Aucun flux camera recu (timeout). Verifiez VMware passthrough USB webcam et fermez les apps qui utilisent la camera.");
                            stopCameraButton.setDisable(true);
                            startCameraButton.setDisable(false);
                        });
                        break;
                    }

                    try {
                        TimeUnit.MILLISECONDS.sleep(80);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (Exception exception) {
                stopFrameGrabber();
                Platform.runLater(() -> {
                    statusLabel.setText("Impossible d'ouvrir la camera en temps reel: " + exception.getMessage());
                    stopCameraButton.setDisable(true);
                    startCameraButton.setDisable(false);
                });
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
        stopFrameGrabber();
        activeCameraSource = null;
        liveFrame = null;
        uiFrameUpdatePending.set(false);
        lastPreviewFrameAtMs = 0L;

        Platform.runLater(() -> {
            previewImageView.setImage(null);
            stopCameraButton.setDisable(true);
            startCameraButton.setDisable(false);
        });
    }

    private void stopFrameGrabber() {
        FrameGrabber current = frameGrabber;
        frameGrabber = null;
        if (current == null) {
            return;
        }
        try {
            current.stop();
        } catch (Exception exception) {
        }
        try {
            current.release();
        } catch (Exception ignored) {
        }
    }

    private GrabberSelection openBestGrabber() {
        for (GrabberCandidate candidate : buildCandidates()) {
            FrameGrabber grabber = null;
            try {
                GrabberSelection selection = openCandidateWithTimeout(candidate, CANDIDATE_INIT_TIMEOUT_SECONDS);
                if (selection != null) {
                    return selection;
                }
            } catch (Exception ignored) {
            }

            if (grabber != null) {
                try {
                    grabber.stop();
                } catch (Exception ignored) {
                }
                try {
                    grabber.release();
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    private GrabberSelection openCandidateWithTimeout(GrabberCandidate candidate, int timeoutSeconds) {
        ExecutorService single = Executors.newSingleThreadExecutor();
        Future<GrabberSelection> future = single.submit(() -> {
            FrameGrabber grabber = null;
            try {
                grabber = candidate.build();
                configureGrabber(grabber);
                grabber.start();

                Image preview = probeFirstFrame(grabber, STARTUP_FRAME_PROBE_ATTEMPTS);
                if (preview != null) {
                    return new GrabberSelection(grabber, candidate.label(), preview);
                }

                try {
                    grabber.stop();
                } catch (Exception ignored) {
                }
                try {
                    grabber.release();
                } catch (Exception ignored) {
                }
                return null;
            } catch (Exception e) {
                if (grabber != null) {
                    try {
                        grabber.stop();
                    } catch (Exception ignored) {
                    }
                    try {
                        grabber.release();
                    } catch (Exception ignored) {
                    }
                }
                return null;
            }
        });

        try {
            return future.get(Math.max(2, timeoutSeconds), TimeUnit.SECONDS);
        } catch (TimeoutException timeout) {
            future.cancel(true);
            return null;
        } catch (Exception e) {
            return null;
        } finally {
            single.shutdownNow();
        }
    }

    private void configureGrabber(FrameGrabber grabber) {
        if (grabber == null) {
            return;
        }

        grabber.setImageWidth(CAMERA_WIDTH);
        grabber.setImageHeight(CAMERA_HEIGHT);
        grabber.setFrameRate(CAMERA_FPS);

        if (grabber instanceof OpenCVFrameGrabber) {
            // OpenCV backend keeps default options to stay JPMS-compatible across packaged natives.
        }

        if (grabber instanceof FFmpegFrameGrabber ffmpeg) {
            ffmpeg.setFormat("v4l2");
            ffmpeg.setOption("framerate", String.valueOf((int) CAMERA_FPS));
            ffmpeg.setOption("video_size", CAMERA_WIDTH + "x" + CAMERA_HEIGHT);
            ffmpeg.setOption("fflags", "nobuffer");
            ffmpeg.setOption("flags", "low_delay");
        }
    }

    private Image probeFirstFrame(FrameGrabber grabber, int attempts) {
        if (grabber == null) {
            return null;
        }
        for (int i = 0; i < Math.max(1, attempts); i++) {
            try {
                Frame frame = grabber.grab();
                BufferedImage buffered = frame == null ? null : frameConverter.getBufferedImage(frame);
                if (buffered != null) {
                    return SwingFXUtils.toFXImage(buffered, null);
                }
            } catch (Exception ignored) {
            }

            try {
                TimeUnit.MILLISECONDS.sleep(120);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return null;
    }

    private List<GrabberCandidate> buildCandidates() {
        List<GrabberCandidate> candidates = new ArrayList<>();
        candidates.add(new GrabberCandidate("/dev/video0 (OpenCV)", () -> new OpenCVFrameGrabber("/dev/video0")));
        candidates.add(new GrabberCandidate("/dev/video1 (OpenCV)", () -> new OpenCVFrameGrabber("/dev/video1")));
        candidates.add(new GrabberCandidate("index 0 (CAP_ANY)", () -> new OpenCVFrameGrabber(0)));
        candidates.add(new GrabberCandidate("index 1 (CAP_ANY)", () -> new OpenCVFrameGrabber(1)));
        candidates.add(new GrabberCandidate("/dev/video0 (FFmpeg yuyv422)", () -> createFfmpegGrabber("/dev/video0", "yuyv422")));
        candidates.add(new GrabberCandidate("/dev/video1 (FFmpeg yuyv422)", () -> createFfmpegGrabber("/dev/video1", "yuyv422")));
        candidates.add(new GrabberCandidate("/dev/video0 (FFmpeg mjpeg)", () -> createFfmpegGrabber("/dev/video0", "mjpeg")));
        candidates.add(new GrabberCandidate("/dev/video1 (FFmpeg mjpeg)", () -> createFfmpegGrabber("/dev/video1", "mjpeg")));
        return candidates;
    }

    private FFmpegFrameGrabber createFfmpegGrabber(String devicePath, String inputFormat) {
        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(devicePath);
        if (inputFormat != null && !inputFormat.isBlank()) {
            grabber.setOption("input_format", inputFormat);
        }
        return grabber;
    }

    @FunctionalInterface
    private interface GrabberFactory {
        FrameGrabber create() throws Exception;
    }

    private record GrabberCandidate(String label, GrabberFactory factory) {
        private FrameGrabber build() throws Exception {
            return factory.create();
        }
    }

    private record GrabberSelection(FrameGrabber grabber, String sourceLabel, Image previewFrame) {
    }
}
