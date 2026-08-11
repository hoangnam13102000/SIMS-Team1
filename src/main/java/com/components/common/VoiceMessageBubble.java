package com.components.common;

import com.components.AppAlert;
import com.theme.AppColor;
import com.ws.VoiceNotePlayer;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.swing.*;
import java.awt.*;
import java.io.ByteArrayInputStream;
import java.util.Base64;

/**
 * Khung phát tin nhắn thoại kiểu hiện đại: nút play tròn + waveform (từ audio thật) + thời lượng.
 * Thay thế cho JButton " Nghe tin thoại" cũ (chỉ có icon + chữ, khá thô).
 *
 * Dùng lại {@link VoiceNotePlayer} singleton sẵn có nên không đổi logic phát âm thanh,
 * chỉ đổi giao diện hiển thị + thêm hiệu ứng waveform/progress.
 */
public class VoiceMessageBubble extends JPanel {

    private static final int BAR_COUNT = 26;
    private static final int PLAY_SIZE = 38;

    private final String voiceBase64;
    private final Component ownerForAlerts;

    private final Color playBg;
    private final Color playIconColor;
    private final Color barActiveColor;
    private final Color barIdleColor;
    private final Color textColor;

    private final CircleButton playButton;
    private final Waveform waveform;
    private final JLabel durationLabel;

    private volatile float[] amplitudes;
    private volatile int totalMs = 0;
    private javax.swing.Timer progressTimer;
    private long playStartedAt;
    private boolean playingThis = false;

    public VoiceMessageBubble(String voiceBase64, boolean isMine, Component ownerForAlerts) {
        this.voiceBase64 = voiceBase64;
        this.ownerForAlerts = ownerForAlerts;

        this.playBg = isMine ? new Color(255, 255, 255, 60) : AppColor.ACCENT;
        this.playIconColor = Color.WHITE;
        this.barActiveColor = isMine ? Color.WHITE : AppColor.ACCENT;
        this.barIdleColor = isMine ? new Color(255, 255, 255, 90) : AppColor.BORDER;
        this.textColor = isMine ? new Color(255, 255, 255, 205) : AppColor.TEXT_MUTED;

        setOpaque(false);
        setLayout(new BorderLayout(10, 0));
        setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

        playButton = new CircleButton();
        playButton.setIcon(playIcon());
        playButton.addActionListener(e -> togglePlay());
        JPanel playWrap = new JPanel(new GridBagLayout());
        playWrap.setOpaque(false);
        playWrap.add(playButton);

        waveform = new Waveform();
        waveform.setPreferredSize(new Dimension(150, 32));
        waveform.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

        durationLabel = new JLabel("…");
        durationLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        durationLabel.setForeground(textColor);
        durationLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        waveform.setAlignmentX(Component.LEFT_ALIGNMENT);
        right.add(waveform);
        right.add(Box.createVerticalStrut(4));
        right.add(durationLabel);

        add(playWrap, BorderLayout.WEST);
        add(right, BorderLayout.CENTER);

        setPreferredSize(new Dimension(228, 56));
        setMaximumSize(new Dimension(260, 60));

        loadAudioAsync();
    }

    // ===================== Nạp waveform + thời lượng từ audio thật =====================

    private void loadAudioAsync() {
        if (voiceBase64 == null || voiceBase64.isBlank()) {
            amplitudes = defaultWave();
            durationLabel.setText("0:00");
            waveform.repaint();
            return;
        }
        new SwingWorker<WaveData, Void>() {
            @Override
            protected WaveData doInBackground() {
                return decodeWave(voiceBase64);
            }

            @Override
            protected void done() {
                WaveData data;
                try {
                    data = get();
                } catch (Exception ex) {
                    data = new WaveData(defaultWave(), 0);
                }
                amplitudes = data.amplitudes;
                totalMs = data.durationMs;
                durationLabel.setText(formatMs(totalMs));
                waveform.repaint();
            }
        }.execute();
    }

    // ===================== Phát / dừng =====================

    private void togglePlay() {
        if (voiceBase64 == null || voiceBase64.isBlank()) {
            AppAlert.warning(ownerForAlerts, "Không có dữ liệu âm thanh.");
            return;
        }
        VoiceNotePlayer player = VoiceNotePlayer.getInstance();
        if (playingThis && player.isPlaying()) {
            player.stop();
            return; // giao diện sẽ tự reset khi luồng phát bên dưới return
        }
        playingThis = true;
        playButton.setIcon(pauseIcon());
        startProgressTimer();
        new Thread(() -> {
            try {
                player.play(voiceBase64);
            } catch (Exception ex) {
                String msg = ex.getMessage() != null ? ex.getMessage() : ex.toString();
                SwingUtilities.invokeLater(() -> AppAlert.error(ownerForAlerts, "Không phát được:\n" + msg));
            } finally {
                SwingUtilities.invokeLater(this::onPlaybackEnded);
            }
        }, "play-voice-note").start();
    }

    private void onPlaybackEnded() {
        playingThis = false;
        playButton.setIcon(playIcon());
        stopProgressTimer();
        waveform.setPlayedFraction(0f);
        durationLabel.setText(formatMs(totalMs));
    }

    private void startProgressTimer() {
        stopProgressTimer();
        playStartedAt = System.currentTimeMillis();
        int total = Math.max(totalMs, 400);
        progressTimer = new javax.swing.Timer(60, e -> {
            long elapsed = System.currentTimeMillis() - playStartedAt;
            float frac = Math.min(1f, elapsed / (float) total);
            waveform.setPlayedFraction(frac);
            durationLabel.setText(formatMs((int) Math.min(elapsed, total)));
            if (frac >= 1f) stopProgressTimer();
        });
        progressTimer.start();
    }

    private void stopProgressTimer() {
        if (progressTimer != null) {
            progressTimer.stop();
            progressTimer = null;
        }
    }

    private Icon playIcon() {
        FontIcon icon = FontIcon.of(FontAwesomeSolid.PLAY, 14);
        icon.setIconColor(playIconColor);
        return icon;
    }

    private Icon pauseIcon() {
        FontIcon icon = FontIcon.of(FontAwesomeSolid.PAUSE, 14);
        icon.setIconColor(playIconColor);
        return icon;
    }

    // ===================== Nút play hình tròn =====================

    private class CircleButton extends JButton {
        CircleButton() {
            setPreferredSize(new Dimension(PLAY_SIZE, PLAY_SIZE));
            setMinimumSize(new Dimension(PLAY_SIZE, PLAY_SIZE));
            setMaximumSize(new Dimension(PLAY_SIZE, PLAY_SIZE));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setToolTipText("Nghe tin nhắn thoại");
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(playBg);
            g2.fillOval(0, 0, getWidth(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ===================== Waveform =====================

    private class Waveform extends JComponent {
        private float playedFraction = 0f;

        void setPlayedFraction(float f) {
            this.playedFraction = f;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            float[] amps = amplitudes != null ? amplitudes : defaultWave();
            int n = amps.length;
            int w = getWidth();
            int h = getHeight();
            float gap = 3f;
            float barW = Math.max(2f, (w - gap * (n - 1)) / n);
            int activeUpTo = Math.round(playedFraction * n);

            for (int i = 0; i < n; i++) {
                int barH = Math.max(3, Math.round(amps[i] * (h - 4)));
                int x = Math.round(i * (barW + gap));
                int y = (h - barH) / 2;
                g2.setColor(i < activeUpTo ? barActiveColor : barIdleColor);
                g2.fillRoundRect(x, y, Math.round(barW), barH, 3, 3);
            }
            g2.dispose();
        }
    }

    // ===================== Giải mã audio -> waveform + thời lượng =====================

    private static WaveData decodeWave(String voiceBase64) {
        try {
            String raw = voiceBase64.trim().replaceAll("\\s+", "");
            int comma = raw.indexOf(',');
            if (raw.startsWith("data:") && comma > 0) raw = raw.substring(comma + 1);
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(raw);
            } catch (IllegalArgumentException e) {
                bytes = Base64.getMimeDecoder().decode(raw);
            }
            try (AudioInputStream original = AudioSystem.getAudioInputStream(new ByteArrayInputStream(bytes))) {
                AudioFormat base = original.getFormat();
                float rate = base.getSampleRate() > 0 ? base.getSampleRate() : 16000f;
                AudioFormat pcmFormat = new AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED, rate, 16, 1, 2, rate, false);

                AudioInputStream mono16;
                if (base.matches(pcmFormat)) {
                    mono16 = original;
                } else if (AudioSystem.isConversionSupported(pcmFormat, base)) {
                    mono16 = AudioSystem.getAudioInputStream(pcmFormat, original);
                } else {
                    return new WaveData(defaultWave(), estimateMsFromBytes(bytes.length));
                }

                byte[] pcm = mono16.readAllBytes();
                int sampleCount = pcm.length / 2;
                if (sampleCount <= 0) {
                    return new WaveData(defaultWave(), estimateMsFromBytes(bytes.length));
                }
                int durationMs = (int) (sampleCount / (double) rate * 1000);
                float[] amps = bucketAmplitudes(pcm, sampleCount, BAR_COUNT);
                return new WaveData(amps, Math.max(300, durationMs));
            }
        } catch (Exception ex) {
            return new WaveData(defaultWave(), 0);
        }
    }

    private static int estimateMsFromBytes(int len) {
        return Math.max(500, len / 32);
    }

    private static float[] bucketAmplitudes(byte[] pcm, int sampleCount, int barCount) {
        float[] raw = new float[barCount];
        int samplesPerBar = Math.max(1, sampleCount / barCount);
        float maxPeak = 0.0001f;
        for (int b = 0; b < barCount; b++) {
            int start = b * samplesPerBar;
            int end = Math.min(sampleCount, start + samplesPerBar);
            long sumSq = 0;
            int n = 0;
            for (int i = start; i < end; i++) {
                int idx = i * 2;
                int lo = pcm[idx] & 0xFF;
                int hi = pcm[idx + 1];
                short sample = (short) ((hi << 8) | lo);
                sumSq += (long) sample * sample;
                n++;
            }
            float rms = n > 0 ? (float) (Math.sqrt(sumSq / (double) n) / 32768.0) : 0f;
            raw[b] = rms;
            if (rms > maxPeak) maxPeak = rms;
        }
        float[] result = new float[barCount];
        for (int b = 0; b < barCount; b++) {
            result[b] = Math.max(0.08f, Math.min(1f, raw[b] / maxPeak));
        }
        return result;
    }

    private static float[] defaultWave() {
        float[] arr = new float[BAR_COUNT];
        for (int i = 0; i < BAR_COUNT; i++) {
            double t = i / (double) BAR_COUNT;
            arr[i] = (float) (0.25 + 0.55 * Math.abs(Math.sin(t * Math.PI * 3.1 + 0.6)));
        }
        return arr;
    }

    private static String formatMs(int ms) {
        if (ms <= 0) return "0:00";
        int totalSec = ms / 1000;
        int m = totalSec / 60;
        int s = totalSec % 60;
        return String.format("%d:%02d", m, s);
    }

    private static final class WaveData {
        final float[] amplitudes;
        final int durationMs;

        WaveData(float[] amplitudes, int durationMs) {
            this.amplitudes = amplitudes;
            this.durationMs = durationMs;
        }
    }
}