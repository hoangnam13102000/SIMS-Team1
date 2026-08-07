package com.utils;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

public final class ChimePlayer {

    private static final float SAMPLE_RATE = 44100f;

    private static final double[] PARTIAL_RATIOS = {1.0, 2.0, 2.4, 3.0, 4.2};
    private static final double[] PARTIAL_AMPS   = {1.0, 0.45, 0.30, 0.18, 0.10};
    private static final double[] PARTIAL_DECAY_MULT = {1.0, 1.6, 2.1, 2.8, 3.6};

    /**
     * Hệ số khuếch đại tổng thể, áp dụng SAU khi đã chuẩn hóa âm bội.
     * > 1.0 = to hơn. Vì có soft-limiter (tanh) bên dưới nên tăng số này
     * lên khá cao (2.0 - 3.0) vẫn không bị vỡ tiếng, chỉ "đầy" hơn.
     * Muốn to hơn nữa thì tăng số này thay vì chỉnh từng Note.
     */
    private static final double MASTER_GAIN = 2.2;

    private ChimePlayer() {}

    public static final class Note {
        final double frequencyHz;
        final int durationMs;
        final double volume; // 0.0 - 1.0

        public Note(double frequencyHz, int durationMs, double volume) {
            this.frequencyHz = frequencyHz;
            this.durationMs = durationMs;
            this.volume = Math.max(0.0, Math.min(1.0, volume));
        }

        public Note(double frequencyHz, int durationMs) {
            this(frequencyHz, durationMs, 0.6);
        }
    }

    public static Note silence(int durationMs) {
        return new Note(0, durationMs, 0);
    }

    public static void playMelody(Note... notes) {
        new Thread(() -> renderAndPlay(notes), "ChimePlayer").start();
    }

    private static void renderAndPlay(Note[] notes) {
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
            line.open(format, 8192);
            line.start();
            for (Note note : notes) {
                byte[] samples = renderNote(note);
                line.write(samples, 0, samples.length);
            }
            line.drain();
        } catch (LineUnavailableException | IllegalArgumentException e) {
            java.awt.Toolkit.getDefaultToolkit().beep();
        }
    }

    private static byte[] renderNote(Note note) {
        int totalSamples = (int) (SAMPLE_RATE * note.durationMs / 1000.0);
        byte[] buffer = new byte[Math.max(0, totalSamples) * 2];
        if (totalSamples <= 0 || note.frequencyHz <= 0 || note.volume <= 0) {
            return buffer;
        }

        int attackSamples = (int) (SAMPLE_RATE * 0.006);
        double durationSec = note.durationMs / 1000.0;
        double baseDecayRate = 4.5 / durationSec;

        double sumAmps = 0;
        for (double a : PARTIAL_AMPS) sumAmps += a;

        for (int i = 0; i < totalSamples; i++) {
            double time = i / SAMPLE_RATE;
            double sample = 0;

            for (int p = 0; p < PARTIAL_RATIOS.length; p++) {
                double freq = note.frequencyHz * PARTIAL_RATIOS[p];
                double decay = Math.exp(-baseDecayRate * PARTIAL_DECAY_MULT[p] * time);
                sample += PARTIAL_AMPS[p] * decay * Math.sin(2.0 * Math.PI * freq * time);
            }
            sample /= sumAmps; // chuẩn hóa về [-1, 1]

            double attackEnv = (i < attackSamples) ? (double) i / attackSamples : 1.0;

            // Khuếch đại tổng thể rồi nén mềm bằng tanh -> to hơn nhưng không vỡ tiếng
            double boosted = sample * attackEnv * note.volume * MASTER_GAIN;
            double limited = Math.tanh(boosted);

            short value = (short) (limited * Short.MAX_VALUE);
            buffer[i * 2] = (byte) (value & 0xFF);
            buffer[i * 2 + 1] = (byte) ((value >> 8) & 0xFF);
        }
        return buffer;
    }
}