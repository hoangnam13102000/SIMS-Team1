package com.service.ai.voice;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Bọc raw PCM 16-bit mono little-endian thành WAV trong bộ nhớ. */
public final class WavUtil {

    private WavUtil() {}

    public static byte[] pcmToWav(byte[] pcm, int sampleRate, int channels, int bitsPerSample)
            throws IOException {
        if (pcm == null) pcm = new byte[0];
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        int blockAlign = channels * bitsPerSample / 8;
        int dataSize = pcm.length;

        ByteArrayOutputStream out = new ByteArrayOutputStream(44 + dataSize);
        out.write(new byte[]{'R', 'I', 'F', 'F'});
        writeIntLE(out, 36 + dataSize);
        out.write(new byte[]{'W', 'A', 'V', 'E'});
        out.write(new byte[]{'f', 'm', 't', ' '});
        writeIntLE(out, 16);
        writeShortLE(out, (short) 1); // PCM
        writeShortLE(out, (short) channels);
        writeIntLE(out, sampleRate);
        writeIntLE(out, byteRate);
        writeShortLE(out, (short) blockAlign);
        writeShortLE(out, (short) bitsPerSample);
        out.write(new byte[]{'d', 'a', 't', 'a'});
        writeIntLE(out, dataSize);
        out.write(pcm);
        return out.toByteArray();
    }

    private static void writeIntLE(ByteArrayOutputStream out, int v) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(v);
        out.write(buf.array());
    }

    private static void writeShortLE(ByteArrayOutputStream out, short v) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort(v);
        out.write(buf.array());
    }
}
