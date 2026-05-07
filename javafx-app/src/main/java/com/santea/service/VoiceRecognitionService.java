package com.santea.service;

import org.vosk.Model;
import org.vosk.Recognizer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class VoiceRecognitionService implements AutoCloseable {
    public interface Listener {
        void onListening(boolean listening);
        void onTranscript(String text);
        void onPartial(String text);
        void onError(String message);
    }

    private static final float SAMPLE_RATE = 16000f;
    private static final AudioFormat FORMAT = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);

    private Model model;
    private String modelPath;
    private Recognizer recognizer;
    private TargetDataLine line;
    private Thread worker;
    private volatile boolean running;
    private Listener listener;
    private boolean remoteMode;
    private ByteArrayOutputStream remoteBuffer;
    private byte[] remotePcm;
    private final VoiceTranscriptionClient transcriptionClient = new VoiceTranscriptionClient();

    public synchronized boolean start(Listener nextListener) {
        if (running) {
            return true;
        }
        if (nextListener == null) {
            return false;
        }
        listener = nextListener;

        String mode = resolveTranscriptionMode();
        boolean remoteConfigured = transcriptionClient.isConfigured();
        boolean forceRemote = "remote".equals(mode) || "cloud".equals(mode);
        boolean forceLocal = "local".equals(mode) || "vosk".equals(mode);

        if (forceRemote) {
            if (remoteConfigured) {
                return startRemote();
            }
            listener.onError(AppointmentService.VOICE_ERR_REMOTE_UNAVAILABLE);
            return false;
        }

        String resolvedModelPath = resolveModelPath();
        boolean localAvailable = !resolvedModelPath.isBlank() && Files.isDirectory(Path.of(resolvedModelPath));
        if (!forceLocal && !localAvailable && remoteConfigured) {
            return startRemote();
        }
        if (!localAvailable) {
            listener.onError(AppointmentService.VOICE_ERR_MODEL_MISSING);
            return false;
        }

        try {
            if (model == null || !resolvedModelPath.equals(modelPath)) {
                closeModel();
                model = new Model(resolvedModelPath);
                modelPath = resolvedModelPath;
            }
        } catch (IOException exception) {
            listener.onError(AppointmentService.VOICE_ERR_MODEL_LOAD);
            return false;
        }

        if (!openLine()) {
            return false;
        }

        try {
            recognizer = new Recognizer(model, SAMPLE_RATE);
            recognizer.setWords(true);
        } catch (IOException exception) {
            listener.onError(AppointmentService.VOICE_ERR_INIT);
            shutdownLine();
            return false;
        }
        running = true;
        worker = new Thread(this::runLoop, "voice-recognition");
        worker.setDaemon(true);
        worker.start();
        listener.onListening(true);
        return true;
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        shutdownLine();
        Thread current = worker;
        if (current != null && current != Thread.currentThread()) {
            try {
                current.join(1000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        if (remoteMode) {
            byte[] pcm = remotePcm;
            remotePcm = null;
            remoteMode = false;
            if (pcm == null || pcm.length == 0) {
                if (listener != null) {
                    listener.onError(AppointmentService.VOICE_ERR_NO_AUDIO);
                }
                return;
            }
            submitRemoteTranscription(pcm);
        }
    }

    public boolean isRunning() {
        return running;
    }

    @Override
    public void close() {
        stop();
        closeModel();
    }

    private void runLoop() {
        byte[] buffer = new byte[4096];
        String lastPartial = "";
        String lastFinal = "";
        try {
            while (running) {
                int read = line.read(buffer, 0, buffer.length);
                if (read <= 0) {
                    continue;
                }
                if (recognizer.acceptWaveForm(buffer, read)) {
                    String text = extractText(recognizer.getResult(), "text");
                    if (!text.isBlank() && listener != null) {
                        lastFinal = text;
                        lastPartial = "";
                        listener.onTranscript(text);
                    }
                } else {
                    String partial = extractText(recognizer.getPartialResult(), "partial");
                    if (!partial.isBlank() && !partial.equals(lastPartial) && listener != null) {
                        lastPartial = partial;
                        listener.onPartial(partial);
                    }
                }
            }
        } catch (RuntimeException exception) {
            if (listener != null) {
                listener.onError(AppointmentService.VOICE_ERR_MIC);
            }
        } finally {
            if (recognizer != null && listener != null) {
                try {
                    String finalText = extractText(recognizer.getFinalResult(), "text");
                    if (!finalText.isBlank() && !finalText.equals(lastFinal)) {
                        listener.onTranscript(finalText);
                    }
                } catch (RuntimeException ignored) {
                }
            }
            shutdownLine();
            closeRecognizer();
            synchronized (this) {
                worker = null;
            }
            if (listener != null) {
                listener.onListening(false);
            }
        }
    }

    private void runRemoteLoop() {
        byte[] buffer = new byte[4096];
        try {
            while (running) {
                int read = line.read(buffer, 0, buffer.length);
                if (read <= 0) {
                    continue;
                }
                if (remoteBuffer != null) {
                    remoteBuffer.write(buffer, 0, read);
                }
            }
        } catch (RuntimeException exception) {
            if (listener != null) {
                listener.onError(AppointmentService.VOICE_ERR_MIC);
            }
        } finally {
            shutdownLine();
            if (remoteBuffer != null) {
                remotePcm = remoteBuffer.toByteArray();
                remoteBuffer = null;
            }
            synchronized (this) {
                worker = null;
            }
            if (listener != null) {
                listener.onListening(false);
            }
        }
    }

    private void shutdownLine() {
        TargetDataLine current = line;
        if (current == null) {
            return;
        }
        line = null;
        try {
            current.stop();
        } catch (RuntimeException ignored) {
        }
        try {
            current.close();
        } catch (RuntimeException ignored) {
        }
    }

    private void closeRecognizer() {
        if (recognizer == null) {
            return;
        }
        recognizer.close();
        recognizer = null;
    }

    private boolean openLine() {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
        if (!AudioSystem.isLineSupported(info)) {
            listener.onError(AppointmentService.VOICE_ERR_MIC_UNAVAILABLE);
            return false;
        }

        try {
            line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(FORMAT);
            line.start();
        } catch (LineUnavailableException exception) {
            listener.onError(AppointmentService.VOICE_ERR_MIC_UNAVAILABLE);
            return false;
        }
        return true;
    }

    private boolean startRemote() {
        if (!openLine()) {
            return false;
        }
        remoteMode = true;
        remoteBuffer = new ByteArrayOutputStream();
        running = true;
        worker = new Thread(this::runRemoteLoop, "voice-recognition-remote");
        worker.setDaemon(true);
        worker.start();
        listener.onListening(true);
        return true;
    }

    private void submitRemoteTranscription(byte[] pcm) {
        byte[] wav = buildWav(pcm);
        Thread workerThread = new Thread(() ->
            transcriptionClient.transcribe(wav).ifPresentOrElse(
                text -> {
                    if (listener != null && !text.isBlank()) {
                        listener.onTranscript(text);
                    } else if (listener != null) {
                        listener.onError(AppointmentService.VOICE_ERR_TRANSCRIPTION_EMPTY);
                    }
                },
                () -> {
                    if (listener != null) {
                        listener.onError(AppointmentService.VOICE_ERR_REMOTE_UNAVAILABLE);
                    }
                }
            ),
            "voice-transcription"
        );
        workerThread.setDaemon(true);
        workerThread.start();
    }

    private void closeModel() {
        if (model == null) {
            return;
        }
        model.close();
        model = null;
        modelPath = null;
    }

    private String resolveModelPath() {
        String fromProperty = System.getProperty("voice.model.path");
        if (fromProperty != null && !fromProperty.isBlank()) {
            return fromProperty.trim();
        }
        String fromEnv = System.getenv("VOICE_MODEL_PATH");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        List<Path> candidates = List.of(
            Path.of("models/vosk-model-ar-mgb2-0.4"),
            Path.of("javafx-app/models/vosk-model-ar-mgb2-0.4")
        );
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) {
                return candidate.toString();
            }
        }
        return "";
    }

    private String resolveTranscriptionMode() {
        String fromProperty = System.getProperty("voice.transcription.mode");
        if (fromProperty != null && !fromProperty.isBlank()) {
            return fromProperty.trim().toLowerCase(Locale.ROOT);
        }
        String fromEnv = System.getenv("VOICE_TRANSCRIBE_MODE");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim().toLowerCase(Locale.ROOT);
        }
        return "auto";
    }

    private byte[] buildWav(byte[] pcm) {
        int dataSize = pcm.length;
        int byteRate = (int) SAMPLE_RATE * 2;
        int blockAlign = 2;
        int totalSize = 36 + dataSize;

        ByteBuffer buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(new byte[] { 'R', 'I', 'F', 'F' });
        buffer.putInt(totalSize);
        buffer.put(new byte[] { 'W', 'A', 'V', 'E' });
        buffer.put(new byte[] { 'f', 'm', 't', ' ' });
        buffer.putInt(16);
        buffer.putShort((short) 1);
        buffer.putShort((short) 1);
        buffer.putInt((int) SAMPLE_RATE);
        buffer.putInt(byteRate);
        buffer.putShort((short) blockAlign);
        buffer.putShort((short) 16);
        buffer.put(new byte[] { 'd', 'a', 't', 'a' });
        buffer.putInt(dataSize);
        buffer.put(pcm);
        return buffer.array();
    }

    private String extractText(String json, String key) {
        if (json == null || json.isBlank() || key == null || key.isBlank()) {
            return "";
        }
        String token = "\"" + key + "\"";
        int keyIndex = json.indexOf(token);
        if (keyIndex < 0) {
            return "";
        }
        int colon = json.indexOf(':', keyIndex);
        if (colon < 0) {
            return "";
        }
        int firstQuote = json.indexOf('"', colon + 1);
        if (firstQuote < 0) {
            return "";
        }
        int secondQuote = json.indexOf('"', firstQuote + 1);
        if (secondQuote < 0) {
            return "";
        }
        return json.substring(firstQuote + 1, secondQuote).trim();
    }
}
