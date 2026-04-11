package com.santea.controller;

import com.santea.model.User;
import com.santea.navigation.AppNavigator;
import com.santea.service.AuthService;
import com.santea.service.AuthSession;
import com.santea.service.FaceVerificationService;
import com.santea.ui.ConfirmDialogs;
import com.github.sarxos.webcam.Webcam;
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

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class AdminFaceVerificationController {
    private static final int CAMERA_WIDTH = 320;
    private static final int CAMERA_HEIGHT = 240;
    private static final long PREVIEW_FRAME_INTERVAL_MS = 140;
    private static final int WEBCAM_BACKEND_TIMEOUT_SECONDS = 8;
    private static final int WEBCAM_STARTUP_PROBE_ATTEMPTS = 30;
    private static final int WEBCAM_MAX_EMPTY_FRAMES = 60;
    private static final int OPENCV_BACKEND_TIMEOUT_SECONDS = 2;
    private static final int OPENCV_STARTUP_PROBE_ATTEMPTS = 10;
    private static final int OPENCV_MAX_EMPTY_FRAMES = 10;

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
    private Webcam webcamDevice;
    private volatile String activeCameraSource;
    private volatile Image liveFrame;
    private volatile String lastCameraFailureHint = "Camera detectee mais aucun flux reel recu. En VM, activez le passthrough USB webcam VMware puis relancez.";
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
                WebcamSelection webcamSelection = openBestWebcamCaptureWithTimeout(WEBCAM_BACKEND_TIMEOUT_SECONDS);
                if (webcamSelection != null && webcamSelection.webcam() != null) {
                    webcamDevice = webcamSelection.webcam();
                    activeCameraSource = webcamSelection.sourceLabel();
                    cameraRunning = true;
                    liveFrame = webcamSelection.previewFrame();

                    Platform.runLater(() -> {
                        if (webcamSelection.previewFrame() != null) {
                            previewImageView.setImage(webcamSelection.previewFrame());
                            statusLabel.setText("Camera active en temps reel (" + CAMERA_WIDTH + "x" + CAMERA_HEIGHT + ") via " + activeCameraSource + ".");
                        } else {
                            statusLabel.setText("Camera ouverte via " + activeCameraSource + ", en attente du premier flux...");
                        }
                        stopCameraButton.setDisable(false);
                        startCameraButton.setDisable(true);
                    });

                    runWebcamCaptureLoop();
                    return;
                }

                OpenCvSelection openCvSelection = openBestOpenCvCaptureWithTimeout(OPENCV_BACKEND_TIMEOUT_SECONDS);
                if (openCvSelection != null && openCvSelection.grabber() != null && openCvSelection.previewFrame() != null) {
                    frameGrabber = openCvSelection.grabber();
                    activeCameraSource = openCvSelection.sourceLabel();
                    cameraRunning = true;
                    liveFrame = openCvSelection.previewFrame();

                    Platform.runLater(() -> {
                        previewImageView.setImage(openCvSelection.previewFrame());
                        statusLabel.setText("Camera active en temps reel (" + CAMERA_WIDTH + "x" + CAMERA_HEIGHT + ") via " + activeCameraSource + ".");
                        stopCameraButton.setDisable(false);
                        startCameraButton.setDisable(true);
                    });

                    runOpenCvCaptureLoop();
                    return;
                }

                Platform.runLater(() -> {
                    statusLabel.setText(lastCameraFailureHint);
                    stopCameraButton.setDisable(true);
                    startCameraButton.setDisable(false);
                });
            } catch (Exception exception) {
                stopWebcamCapture();
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
        ConfirmDialogs.confirmLogout(statusLabel, () -> {
            stopCamera();
            authService.logout();
            AppNavigator.showLogin();
        });
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
        stopWebcamCapture();
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

    private void stopWebcamCapture() {
        Webcam current = webcamDevice;
        webcamDevice = null;
        if (current == null) {
            return;
        }
        try {
            current.close();
        } catch (Exception ignored) {
        }
    }

    private void stopFrameGrabber() {
        FrameGrabber current = frameGrabber;
        frameGrabber = null;
        if (current == null) {
            return;
        }
        try {
            current.stop();
        } catch (Exception ignored) {
        }
        try {
            current.release();
        } catch (Exception ignored) {
        }
    }

    private void runWebcamCaptureLoop() {
        int consecutiveNullFrames = 0;
        while (cameraRunning && webcamDevice != null) {
            try {
                BufferedImage buffered = webcamDevice.getImage();
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

            if (consecutiveNullFrames >= WEBCAM_MAX_EMPTY_FRAMES) {
                cameraRunning = false;
                stopWebcamCapture();
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
    }

    private void runOpenCvCaptureLoop() {
        int consecutiveNullFrames = 0;
        while (cameraRunning && frameGrabber != null) {
            try {
                Frame frame = frameGrabber.grab();
                BufferedImage buffered = frame == null ? null : frameConverter.getBufferedImage(frame);
                if (buffered != null) {
                    consecutiveNullFrames = 0;
                    long now = System.currentTimeMillis();
                    if ((now - lastPreviewFrameAtMs) >= PREVIEW_FRAME_INTERVAL_MS
                            && uiFrameUpdatePending.compareAndSet(false, true)) {
                        Image fxFrame = SwingFXUtils.toFXImage(buffered, null);
                        liveFrame = fxFrame;
                        lastPreviewFrameAtMs = now;
                        Platform.runLater(() -> {
                            try {
                                previewImageView.setImage(fxFrame);
                            } finally {
                                uiFrameUpdatePending.set(false);
                            }
                        });
                    }
                } else {
                    consecutiveNullFrames++;
                }
            } catch (Exception ex) {
                consecutiveNullFrames++;
            }

            if (consecutiveNullFrames >= OPENCV_MAX_EMPTY_FRAMES) {
                cameraRunning = false;
                stopFrameGrabber();
                Platform.runLater(() -> {
                    statusLabel.setText("Aucun flux camera recu (OpenCV timeout). Verifiez passthrough USB VMware.");
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
    }

    private OpenCvSelection openBestOpenCvCaptureWithTimeout(int timeoutSeconds) {
        List<String> devices = listVideoDevices();
        if (devices.isEmpty()) {
            return null;
        }

        for (String device : devices) {
            ExecutorService single = Executors.newSingleThreadExecutor();
            Future<OpenCvSelection> future = single.submit(() -> {
                OpenCVFrameGrabber grabber = null;
                try {
                    grabber = new OpenCVFrameGrabber(device);
                    grabber.setImageWidth(CAMERA_WIDTH);
                    grabber.setImageHeight(CAMERA_HEIGHT);
                    grabber.start();

                    for (int i = 0; i < OPENCV_STARTUP_PROBE_ATTEMPTS; i++) {
                        Frame frame = grabber.grab();
                        BufferedImage buffered = frame == null ? null : frameConverter.getBufferedImage(frame);
                        if (buffered != null) {
                            return new OpenCvSelection(grabber, device + " (OpenCV)", SwingFXUtils.toFXImage(buffered, null));
                        }
                        TimeUnit.MILLISECONDS.sleep(100);
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
                return null;
            });

            try {
                OpenCvSelection selection = future.get(Math.max(2, timeoutSeconds), TimeUnit.SECONDS);
                if (selection != null) {
                    return selection;
                }
            } catch (TimeoutException timeout) {
                future.cancel(true);
            } catch (Exception ignored) {
            } finally {
                single.shutdownNow();
            }
        }

        return null;
    }

    private List<String> listVideoDevices() {
        List<String> devices = new ArrayList<>();
        Path devPath = Path.of("/dev");
        if (!Files.isDirectory(devPath)) {
            return devices;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(devPath, "video*")) {
            for (Path path : stream) {
                if (Files.isReadable(path)) {
                    devices.add(path.toString());
                }
            }
        } catch (Exception ignored) {
        }
        return devices;
    }

    private WebcamSelection openBestWebcamCapture() {
        List<String> failures = new ArrayList<>();
        try {
            List<Webcam> webcams = Webcam.getWebcams();
            if (webcams == null || webcams.isEmpty()) {
                lastCameraFailureHint = "Aucune webcam detectee par le pilote systeme. Verifiez VMware > Removable Devices > Connect (Disconnect from Host).";
                return null;
            }

            for (Webcam webcam : webcams) {
                if (webcam == null) {
                    continue;
                }
                List<Dimension> candidateSizes = buildCandidateViewSizes(webcam);
                for (Dimension size : candidateSizes) {
                    try {
                        if (size != null) {
                            webcam.setViewSize(size);
                        }
                        webcam.open();
                        BufferedImage image = probeWebcamFirstFrame(webcam, WEBCAM_STARTUP_PROBE_ATTEMPTS);
                        if (image != null) {
                            return new WebcamSelection(webcam, webcam.getName(), SwingFXUtils.toFXImage(image, null));
                        }

                        failures.add(webcam.getName() + " " + formatDimension(size) + " : flux vide");
                    } catch (Exception ex) {
                        failures.add(webcam.getName() + " " + formatDimension(size) + " : " + sanitizeMessage(ex));
                    } finally {
                        try {
                            if (webcam.isOpen()) {
                                webcam.close();
                            }
                        } catch (Exception closeIgnored) {
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            failures.add("Erreur pilote webcam: " + sanitizeMessage(ignored));
        }

        if (!failures.isEmpty()) {
            String detail = failures.get(0);
            if (failures.size() > 1) {
                detail = detail + " | +" + (failures.size() - 1) + " autres essais";
            }
            lastCameraFailureHint = "Webcam detectee mais flux indisponible: " + detail;
        }
        return null;
    }

    private WebcamSelection openBestWebcamCaptureWithTimeout(int timeoutSeconds) {
        ExecutorService single = Executors.newSingleThreadExecutor();
        Future<WebcamSelection> future = single.submit(this::openBestWebcamCapture);
        try {
            return future.get(Math.max(2, timeoutSeconds), TimeUnit.SECONDS);
        } catch (TimeoutException timeout) {
            lastCameraFailureHint = "Initialisation camera expiree (timeout). En VM, reconnectez la webcam USB puis reessayez.";
            future.cancel(true);
            return null;
        } catch (Exception ignored) {
            return null;
        } finally {
            single.shutdownNow();
        }
    }

    private BufferedImage probeWebcamFirstFrame(Webcam webcam, int attempts) {
        if (webcam == null) {
            return null;
        }
        for (int i = 0; i < Math.max(1, attempts); i++) {
            try {
                BufferedImage image = webcam.getImage();
                if (image != null) {
                    return image;
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

    private List<Dimension> buildCandidateViewSizes(Webcam webcam) {
        List<Dimension> sizes = new ArrayList<>();
        Dimension preferred = new Dimension(CAMERA_WIDTH, CAMERA_HEIGHT);
        sizes.add(preferred);

        try {
            Dimension[] supported = webcam.getViewSizes();
            if (supported != null) {
                for (Dimension size : supported) {
                    if (size != null && !containsDimension(sizes, size)) {
                        sizes.add(size);
                    }
                }
            }
        } catch (Exception ignored) {
        }

        if (sizes.isEmpty()) {
            sizes.add(null);
        }
        return sizes;
    }

    private boolean containsDimension(List<Dimension> sizes, Dimension candidate) {
        if (candidate == null) {
            return false;
        }
        for (Dimension size : sizes) {
            if (size != null && size.width == candidate.width && size.height == candidate.height) {
                return true;
            }
        }
        return false;
    }

    private String formatDimension(Dimension size) {
        if (size == null) {
            return "default";
        }
        return size.width + "x" + size.height;
    }

    private String sanitizeMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()) {
            return "erreur sans detail";
        }
        return throwable.getMessage().replace('\n', ' ').replace('\r', ' ');
    }

    private record WebcamSelection(Webcam webcam, String sourceLabel, Image previewFrame) {
    }

    private record OpenCvSelection(FrameGrabber grabber, String sourceLabel, Image previewFrame) {
    }
}
