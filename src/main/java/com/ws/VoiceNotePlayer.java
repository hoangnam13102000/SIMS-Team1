package com.ws;

import javax.sound.sampled.*;
import java.awt.Desktop;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Phát tin nhắn thoại (WAV base64).
 * 1) SourceDataLine  2) Clip  3) Ghi file tạm + mở bằng app hệ thống
 */
public final class VoiceNotePlayer {

    private static final VoiceNotePlayer INSTANCE = new VoiceNotePlayer();

    public static VoiceNotePlayer getInstance() {
        return INSTANCE;
    }

    private final AtomicBoolean playing = new AtomicBoolean(false);
    private volatile SourceDataLine line;
    private volatile Clip clip;
    private volatile String lastError;

    private VoiceNotePlayer() {}

    public boolean isPlaying() {
        return playing.get();
    }

    public String getLastError() {
        return lastError;
    }

    public void stop() {
        playing.set(false);
        SourceDataLine l = line;
        line = null;
        if (l != null) {
            try { l.stop(); l.flush(); l.close(); } catch (Exception ignored) {}
        }
        Clip c = clip;
        clip = null;
        if (c != null) {
            try { c.stop(); c.close(); } catch (Exception ignored) {}
        }
    }

    public void playAsync(String voiceBase64) {
        new Thread(() -> {
            try {
                play(voiceBase64);
            } catch (Exception ex) {
                lastError = ex.getMessage();
                ex.printStackTrace();
            }
        }, "voice-note-play").start();
    }

    public void play(String voiceBase64) throws Exception {
        stop();
        lastError = null;
        if (voiceBase64 == null || voiceBase64.isBlank()) {
            throw new IllegalArgumentException("Dữ liệu audio trống");
        }

        String raw = voiceBase64.trim().replaceAll("\\s+", "");
        int comma = raw.indexOf(',');
        if (raw.startsWith("data:") && comma > 0) {
            raw = raw.substring(comma + 1);
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(raw);
        } catch (IllegalArgumentException e) {
            bytes = Base64.getMimeDecoder().decode(raw);
        }
        System.out.println("[VoiceNotePlayer] decoded bytes=" + bytes.length);

        if (bytes.length < 44) {
            throw new IllegalArgumentException("Audio quá ngắn (" + bytes.length + " bytes). Hãy ghi lại tin thoại.");
        }

        playing.set(true);
        try {
            try {
                playWithLine(bytes);
                return;
            } catch (Exception e1) {
                System.err.println("[VoiceNotePlayer] SourceDataLine fail: " + e1.getMessage());
            }
            try {
                playWithClip(bytes);
                return;
            } catch (Exception e2) {
                System.err.println("[VoiceNotePlayer] Clip fail: " + e2.getMessage());
            }
            // Fallback: mở bằng app mặc định của OS
            playWithSystemPlayer(bytes);
        } finally {
            playing.set(false);
        }
    }

    private void playWithLine(byte[] wavBytes) throws Exception {
        AudioInputStream ais = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wavBytes));
        AudioFormat src = ais.getFormat();
        AudioFormat fmt = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                src.getSampleRate() > 0 ? src.getSampleRate() : 16000f,
                16,
                Math.max(1, src.getChannels()),
                Math.max(1, src.getChannels()) * 2,
                src.getSampleRate() > 0 ? src.getSampleRate() : 16000f,
                false);
        if (!src.matches(fmt) && AudioSystem.isConversionSupported(fmt, src)) {
            ais = AudioSystem.getAudioInputStream(fmt, ais);
        } else {
            fmt = src;
        }
        byte[] pcm = readAll(ais);
        ais.close();
        if (pcm.length < 100) throw new IllegalStateException("PCM rỗng");

        DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);
        SourceDataLine sdl = (SourceDataLine) AudioSystem.getLine(info);
        sdl.open(fmt);
        sdl.start();
        line = sdl;
        int off = 0;
        while (playing.get() && off < pcm.length) {
            int n = sdl.write(pcm, off, Math.min(4096, pcm.length - off));
            if (n < 0) break;
            off += n;
        }
        if (playing.get()) sdl.drain();
        stop();
    }

    private void playWithClip(byte[] wavBytes) throws Exception {
        AudioInputStream ais = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wavBytes));
        Clip c = AudioSystem.getClip();
        c.open(ais);
        clip = c;
        c.start();
        while (c.isRunning() && playing.get()) {
            Thread.sleep(40);
        }
        stop();
    }

    private void playWithSystemPlayer(byte[] wavBytes) throws Exception {
        File tmp = File.createTempFile("sims-voice-", ".wav");
        tmp.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(tmp)) {
            fos.write(wavBytes);
        }
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            throw new IllegalStateException("Không phát được audio trên máy này (thử mở file: " + tmp.getAbsolutePath() + ")");
        }
        Desktop.getDesktop().open(tmp);
        // chờ một lúc ước lượng
        long ms = Math.min(60_000, Math.max(1500, wavBytes.length / 32));
        long end = System.currentTimeMillis() + ms;
        while (playing.get() && System.currentTimeMillis() < end) {
            Thread.sleep(100);
        }
    }

    private static byte[] readAll(AudioInputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) >= 0) {
            if (n > 0) bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }
}
