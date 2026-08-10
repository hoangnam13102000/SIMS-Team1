package com.ws;

import com.service.ai.voice.AudioRecorder;
import com.service.ai.voice.SpeechToTextService;

import javax.swing.*;
import java.util.Base64;
import java.util.function.BiConsumer;

/**
 * Thu âm tin nhắn thoại cho chat realtime:
 * - Ghi WAV
 * - STT (transcript) — có thể tắt
 * - Callback (transcript, voiceBase64) để UI gọi ChatClient.sendVoice / sendStaffVoice
 *
 * Cách dùng trên ChatPanel:
 * <pre>
 * VoiceNoteSender sender = new VoiceNoteSender();
 * micButton.addActionListener(e -> {
 *   if (sender.isRecording()) sender.finish(this::sendVoiceMessage);
 *   else sender.start();
 * });
 * </pre>
 */
public final class VoiceNoteSender {

    private final AudioRecorder recorder = new AudioRecorder();
    private final SpeechToTextService stt = new SpeechToTextService();
    private boolean busy;
    private long startedAt;

    /** true = gửi cả audio + transcript; false = chỉ audio, text = nhãn mặc định */
    private boolean enableTranscript = true;

    public void setEnableTranscript(boolean enableTranscript) {
        this.enableTranscript = enableTranscript;
    }

    public boolean isRecording() {
        return recorder.isRecording();
    }

    public boolean isBusy() {
        return busy;
    }

    public void start() throws Exception {
        if (busy || recorder.isRecording()) return;
        startedAt = System.currentTimeMillis();
        recorder.setSilenceMs(750);
        recorder.setOnAutoStop(() ->
                SwingUtilities.invokeLater(() -> {
                    // UI nên gắn finish qua listener; auto chỉ đánh dấu
                    // Panel gọi finish khi nhận auto — set callback:
                }));
        recorder.start();
    }

    /**
     * Bắt đầu ghi; khi VAD tự dừng sẽ gọi onAutoFinished trên EDT.
     */
    public void start(Runnable onAutoFinished) throws Exception {
        if (busy || recorder.isRecording()) return;
        startedAt = System.currentTimeMillis();
        recorder.setSilenceMs(750);
        recorder.setOnAutoStop(() -> {
            if (onAutoFinished != null) {
                SwingUtilities.invokeLater(onAutoFinished);
            }
        });
        recorder.start();
    }

    /**
     * Dừng, STT (nếu bật), rồi callback(transcript, base64Wav).
     * Chạy STT nền — callback trên EDT.
     */
    public void finish(BiConsumer<String, String> onReady) {
        if (busy) return;
        busy = true;
        final int durationMs = (int) Math.max(0, System.currentTimeMillis() - startedAt);

        new SwingWorker<String[], Void>() {
            @Override
            protected String[] doInBackground() throws Exception {
                byte[] wav = recorder.stopAndGetWav();
                if (wav.length < 1000) {
                    return new String[]{"", ""};
                }
                String b64 = Base64.getEncoder().encodeToString(wav);
                String transcript = "";
                if (enableTranscript) {
                    try {
                        transcript = stt.transcribeWav(wav);
                    } catch (Exception ignored) {
                        transcript = "";
                    }
                }
                return new String[]{transcript != null ? transcript : "", b64, String.valueOf(durationMs)};
            }

            @Override
            protected void done() {
                busy = false;
                try {
                    String[] r = get();
                    String transcript = r[0];
                    String b64 = r[1];
                    if (b64 == null || b64.isBlank()) {
                        onReady.accept("", "");
                        return;
                    }
                    onReady.accept(transcript, b64);
                } catch (Exception e) {
                    onReady.accept("", "");
                }
            }
        }.execute();
    }

    public void cancel() {
        try {
            recorder.cancel();
        } catch (Exception ignored) {
        }
        busy = false;
    }

    public double getLastRms() {
        return recorder.getLastRms();
    }

    public int lastDurationEstimateMs() {
        return (int) Math.max(0, System.currentTimeMillis() - startedAt);
    }
}
