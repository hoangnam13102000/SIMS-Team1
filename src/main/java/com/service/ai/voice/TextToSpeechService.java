package com.service.ai.voice;

import javazoom.jl.player.Player;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * TTS đa ngôn ngữ (vi / en) qua Google Translate TTS (MP3) + JLayer.
 * Cần: javazoom:jlayer:1.0.1
 */
public final class TextToSpeechService {

    private static final int MAX_CHUNK = 150;
    private static final Pattern VIETNAMESE_CHARS = Pattern.compile(
            "[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ"
                    + "ÀÁẠẢÃÂẦẤẬẨẪĂẰẮẶẲẴÈÉẸẺẼÊỀẾỆỂỄÌÍỊỈĨÒÓỌỎÕÔỒỐỘỔỖƠỜỚỢỞỠÙÚỤỦŨƯỪỨỰỬỮỲÝỴỶỸĐ]");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final AtomicBoolean speaking = new AtomicBoolean(false);
    private volatile Player currentPlayer;

    public void speakAsync(String text) {
        if (text == null || text.isBlank()) return;
        Thread t = new Thread(() -> {
            try {
                speak(text);
            } catch (Exception ignored) {
            }
        }, "tts-speak");
        t.setDaemon(true);
        t.start();
    }

    public synchronized void stop() {
        speaking.set(false);
        Player p = currentPlayer;
        if (p != null) {
            try {
                p.close();
            } catch (Exception ignored) {
            }
            currentPlayer = null;
        }
    }

    public void speak(String text) throws Exception {
        stop();
        speaking.set(true);
        try {
            String cleaned = text
                    .replaceAll("\\[\\[IMG:.*?\\]\\]", " ")
                    .replaceAll("[*_`#>]", " ")
                    .replaceAll("\\s+", " ")
                    .trim();
            if (cleaned.isEmpty()) return;

            String lang = detectLang(cleaned); // "vi" | "en"
            for (String chunk : splitChunks(cleaned, MAX_CHUNK)) {
                if (!speaking.get()) break;
                byte[] mp3 = fetchMp3(chunk, lang);
                if (mp3.length < 100) continue;
                playMp3(mp3);
            }
        } finally {
            speaking.set(false);
            currentPlayer = null;
        }
    }

    /** Có dấu tiếng Việt → vi; không → en (đủ tốt cho chat shop). */
    public static String detectLang(String text) {
        if (text == null || text.isBlank()) return "vi";
        if (VIETNAMESE_CHARS.matcher(text).find()) return "vi";
        // từ Việt không dấu phổ biến
        String lower = text.toLowerCase();
        String[] viHints = {" ban ", " khong ", " duoc ", " san pham ", " gio hang ",
                " toi ", " minh ", " cua hang ", " bao nhieu ", " cam on "};
        String padded = " " + lower + " ";
        for (String h : viHints) {
            if (padded.contains(h)) return "vi";
        }
        return "en";
    }

    private void playMp3(byte[] mp3) throws Exception {
        try (InputStream in = new ByteArrayInputStream(mp3)) {
            Player player = new Player(in);
            currentPlayer = player;
            player.play();
        }
    }

    private byte[] fetchMp3(String text, String lang) throws Exception {
        String q = URLEncoder.encode(text, StandardCharsets.UTF_8);
        String tl = "en".equals(lang) ? "en" : "vi";
        String url = "https://translate.google.com/translate_tts?ie=UTF-8&client=tw-ob&tl="
                + tl + "&q=" + q;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Accept", "*/*")
                .header("Referer", "https://translate.google.com/")
                .timeout(Duration.ofSeconds(25))
                .GET()
                .build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("TTS HTTP " + response.statusCode());
        }
        return response.body();
    }

    private static List<String> splitChunks(String text, int max) {
        List<String> list = new ArrayList<>();
        String[] parts = text.split("(?<=[\\.\\!\\?\\;\\,])\\s+");
        StringBuilder cur = new StringBuilder();
        for (String p : parts) {
            if (p.isBlank()) continue;
            if (p.length() > max) {
                if (cur.length() > 0) {
                    list.add(cur.toString().trim());
                    cur.setLength(0);
                }
                for (int i = 0; i < p.length(); i += max) {
                    list.add(p.substring(i, Math.min(p.length(), i + max)));
                }
            } else if (cur.length() + p.length() + 1 > max) {
                list.add(cur.toString().trim());
                cur.setLength(0);
                cur.append(p);
            } else {
                if (cur.length() > 0) cur.append(' ');
                cur.append(p);
            }
        }
        if (cur.length() > 0) list.add(cur.toString().trim());
        return list;
    }
}
