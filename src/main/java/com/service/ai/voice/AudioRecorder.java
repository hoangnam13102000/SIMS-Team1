package com.service.ai.voice;

import javax.sound.sampled.*;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Ghi mic + endpointing (VAD) theo máy trạng thái:
 * WAIT_SPEECH → IN_SPEECH → IN_SILENCE → auto-stop.
 * Dùng năng lượng tương đối so với noise nền (không phụ thuộc ngưỡng cố định).
 */
public final class AudioRecorder {

    public static final int SAMPLE_RATE = 16_000;
    public static final int CHANNELS = 1;
    public static final int BITS = 16;

    private static final int CALIBRATE_MS = 400;
    private static final int MIN_SPEECH_MS = 250;
    private static final int DEFAULT_SILENCE_MS = 750;
    private static final int MAX_RECORD_MS = 25_000;
    private static final int MAX_WAIT_SPEECH_MS = 12_000;

    private TargetDataLine line;
    private Thread worker;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final AtomicBoolean recording = new AtomicBoolean(false);

    private int silenceMs = DEFAULT_SILENCE_MS;
    private volatile Runnable onAutoStop;
    private volatile boolean stoppedByVad;
    private volatile double lastRms;

    public void setSilenceMs(int silenceMs) {
        this.silenceMs = Math.max(500, Math.min(2500, silenceMs));
    }

    public void setOnAutoStop(Runnable onAutoStop) {
        this.onAutoStop = onAutoStop;
    }

    public boolean wasStoppedByVad() {
        return stoppedByVad;
    }

    public double getLastRms() {
        return lastRms;
    }

    public synchronized void start() throws LineUnavailableException {
        if (recording.get()) return;

        AudioFormat format = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                SAMPLE_RATE, BITS, CHANNELS,
                CHANNELS * BITS / 8,
                SAMPLE_RATE, false);

        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        if (!AudioSystem.isLineSupported(info)) {
            // Thử format mặc định của mic rồi convert
            line = AudioSystem.getTargetDataLine(null);
            line.open();
        } else {
            line = (TargetDataLine) AudioSystem.getLine(info);
            int buf = SAMPLE_RATE / 10 * format.getFrameSize(); // ~100ms
            line.open(format, Math.max(buf, 2048));
        }

        line.start();
        try {
            line.flush();
        } catch (Exception ignored) {
        }

        buffer.reset();
        stoppedByVad = false;
        lastRms = 0;
        recording.set(true);

        final int silenceLimit = silenceMs;
        final AudioFormat lineFormat = line.getFormat();

        worker = new Thread(() -> {
            int frameSize = Math.max(1, lineFormat.getFrameSize());
            // ~20ms
            int bytesPer20ms = Math.max(
                    (int) (lineFormat.getSampleRate() * frameSize / 50),
                    frameSize * 8);
            byte[] chunk = new byte[bytesPer20ms];

            // 0=calibrate, 1=wait speech, 2=in speech, 3=in silence
            int state = 0;
            long speechMs = 0;
            long silenceAcc = 0;
            long startedAt = System.currentTimeMillis();
            long calibrateUntil = startedAt + CALIBRATE_MS;

            double noiseSum = 0;
            int noiseN = 0;
            double noiseFloor = 0.01;
            double speechGate = 0.02;
            double silenceGate = 0.015;
            double peakInSpeech = 0;

            try {
                while (recording.get()) {
                    int n = line.read(chunk, 0, chunk.length);
                    if (n <= 0) {
                        Thread.sleep(5);
                        continue;
                    }

                    synchronized (buffer) {
                        buffer.write(chunk, 0, n);
                    }

                    long now = System.currentTimeMillis();
                    int frameMs = Math.max(1, (int) (n * 1000.0
                            / (lineFormat.getSampleRate() * frameSize)));

                    double rms = rmsOf(chunk, n, lineFormat);
                    lastRms = rms;

                    if (state == 0) {
                        noiseSum += rms;
                        noiseN++;
                        if (now >= calibrateUntil && noiseN > 0) {
                            noiseFloor = Math.max(0.003, noiseSum / noiseN);
                            // Nói: cao hơn nền rõ; im: gần nền
                            speechGate = Math.max(noiseFloor * 2.2, noiseFloor + 0.012);
                            silenceGate = Math.max(noiseFloor * 1.35, noiseFloor + 0.004);
                            state = 1;
                        }
                        continue;
                    }

                    if (state == 1) { // chờ bắt đầu nói
                        if (rms >= speechGate) {
                            speechMs += frameMs;
                            if (speechMs >= MIN_SPEECH_MS) {
                                state = 2;
                                peakInSpeech = rms;
                                silenceAcc = 0;
                            }
                        } else {
                            speechMs = Math.max(0, speechMs - frameMs);
                        }
                        if (now - startedAt > MAX_WAIT_SPEECH_MS) {
                            // Không nói gì → vẫn auto-stop để UI không treo
                            stoppedByVad = true;
                            recording.set(false);
                            break;
                        }
                    } else if (state == 2) { // đang nói
                        if (rms > peakInSpeech) peakInSpeech = rms;
                        // Cổng im lặng: dưới silenceGate HOẶC < 28% đỉnh câu
                        double dropGate = Math.max(silenceGate, peakInSpeech * 0.28);
                        if (rms < dropGate) {
                            silenceAcc += frameMs;
                            if (silenceAcc >= 80) {
                                state = 3;
                            }
                        } else {
                            silenceAcc = 0;
                        }
                    } else if (state == 3) { // đang im sau khi nói
                        double dropGate = Math.max(silenceGate, peakInSpeech * 0.28);
                        if (rms < dropGate) {
                            silenceAcc += frameMs;
                            if (silenceAcc >= silenceLimit) {
                                stoppedByVad = true;
                                recording.set(false);
                                break;
                            }
                        } else {
                            // Nói tiếp
                            state = 2;
                            silenceAcc = 0;
                            if (rms > peakInSpeech) peakInSpeech = rms;
                        }
                    }

                    if (now - startedAt >= MAX_RECORD_MS) {
                        stoppedByVad = true;
                        recording.set(false);
                        break;
                    }
                }
            } catch (Exception ignored) {
                recording.set(false);
            }

            if (stoppedByVad) {
                Runnable cb = onAutoStop;
                if (cb != null) {
                    try {
                        cb.run();
                    } catch (Exception ignored) {
                    }
                }
            }
        }, "mic-recorder-vad");
        worker.setDaemon(true);
        worker.start();
    }

    public boolean isRecording() {
        return recording.get();
    }

    public synchronized byte[] stopAndGetWav() throws Exception {
        recording.set(false);
        Thread w = worker;
        if (w != null && Thread.currentThread() != w) {
            w.join(3000);
        }
        worker = null;

        AudioFormat srcFormat = null;
        if (line != null) {
            srcFormat = line.getFormat();
            try {
                line.stop();
                line.flush();
                line.close();
            } catch (Exception ignored) {
            }
            line = null;
        }

        byte[] pcm;
        synchronized (buffer) {
            pcm = buffer.toByteArray();
            buffer.reset();
        }
        if (pcm.length < 64) return new byte[0];

        // Chuẩn hóa về 16kHz mono 16-bit LE WAV cho Gemini STT
        if (srcFormat != null && !isTargetFormat(srcFormat)) {
            pcm = convertToTargetPcm(pcm, srcFormat);
        }
        return WavUtil.pcmToWav(pcm, SAMPLE_RATE, CHANNELS, BITS);
    }

    public synchronized void cancel() {
        stoppedByVad = false;
        onAutoStop = null;
        try {
            stopAndGetWav();
        } catch (Exception ignored) {
        }
    }

    private static boolean isTargetFormat(AudioFormat f) {
        return f.getSampleRate() == SAMPLE_RATE
                && f.getChannels() == CHANNELS
                && f.getSampleSizeInBits() == BITS
                && !f.isBigEndian()
                && AudioFormat.Encoding.PCM_SIGNED.equals(f.getEncoding());
    }

    private static byte[] convertToTargetPcm(byte[] data, AudioFormat src) {
        try {
            AudioFormat target = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    SAMPLE_RATE, BITS, CHANNELS,
                    CHANNELS * BITS / 8, SAMPLE_RATE, false);
            try (AudioInputStream in = new AudioInputStream(
                    new java.io.ByteArrayInputStream(data), src,
                    data.length / Math.max(1, src.getFrameSize()))) {
                if (AudioSystem.isConversionSupported(target, src)) {
                    try (AudioInputStream converted = AudioSystem.getAudioInputStream(target, in)) {
                        return converted.readAllBytes();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return data;
    }

    private static double rmsOf(byte[] data, int len, AudioFormat format) {
        int bits = format.getSampleSizeInBits();
        boolean be = format.isBigEndian();
        int channels = Math.max(1, format.getChannels());
        int bytesPerSample = Math.max(1, bits / 8);
        int frameSize = bytesPerSample * channels;

        if (bits == 16 && frameSize >= 2) {
            long sumSq = 0;
            int samples = 0;
            for (int i = 0; i + frameSize <= len; i += frameSize) {
                // chỉ lấy kênh 0
                int b0 = data[i] & 0xff;
                int b1 = data[i + 1] & 0xff;
                short s = be ? (short) ((b0 << 8) | b1) : (short) (b0 | (b1 << 8));
                sumSq += (long) s * s;
                samples++;
            }
            if (samples == 0) return 0;
            return Math.sqrt(sumSq / (double) samples) / 32768.0;
        }

        // 8-bit unsigned fallback
        if (bits == 8) {
            long sumSq = 0;
            int samples = 0;
            for (int i = 0; i < len; i += frameSize) {
                int s = (data[i] & 0xff) - 128;
                sumSq += (long) s * s;
                samples++;
            }
            if (samples == 0) return 0;
            return Math.sqrt(sumSq / (double) samples) / 128.0;
        }
        return 0;
    }
}
