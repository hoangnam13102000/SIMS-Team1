package com.service.ai.voice;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.security.AppConfig;
import com.service.ai.GeminiService;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * STT qua Gemini (inlineData audio/wav) — hỗ trợ tiếng Việt và English.
 */
public final class SpeechToTextService {

    private static final String API_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String PROMPT =
            "You are a speech recognition engine. Transcribe the audio to text. "
                    + "Detect the spoken language automatically. "
                    + "If the speaker speaks Vietnamese, transcribe in Vietnamese. "
                    + "If the speaker speaks English, transcribe in English. "
                    + "Return ONLY the transcript text, no quotes, no markdown, no explanation. "
                    + "If unintelligible, return an empty string.";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public String transcribeWav(byte[] wavBytes) throws Exception {
        if (wavBytes == null || wavBytes.length < 44) {
            return "";
        }
        if (!GeminiService.isConfigured()) {
            throw new IllegalStateException("Chưa cấu hình GEMINI_API_KEY.");
        }

        AppConfig config = AppConfig.getInstance();
        String apiKey = config.get("GEMINI_API_KEY");
        String model = config.get("GEMINI_MODEL", "gemini-2.0-flash");

        String b64 = Base64.getEncoder().encodeToString(wavBytes);

        JsonObject body = new JsonObject();
        JsonArray contents = new JsonArray();
        JsonObject content = new JsonObject();
        content.addProperty("role", "user");
        JsonArray parts = new JsonArray();

        JsonObject inlinePart = new JsonObject();
        JsonObject inlineData = new JsonObject();
        inlineData.addProperty("mimeType", "audio/wav");
        inlineData.addProperty("data", b64);
        inlinePart.add("inlineData", inlineData);
        parts.add(inlinePart);

        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", PROMPT);
        parts.add(textPart);

        content.add("parts", parts);
        contents.add(content);
        body.add("contents", contents);

        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("temperature", 0.1);
        generationConfig.addProperty("maxOutputTokens", 512);
        body.add("generationConfig", generationConfig);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + model + ":generateContent"))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("STT Gemini HTTP " + response.statusCode() + ": " + response.body());
        }

        return extractText(response.body()).trim();
    }

    private static String extractText(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray candidates = root.getAsJsonArray("candidates");
            if (candidates == null || candidates.isEmpty()) return "";
            JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
            if (content == null) return "";
            JsonArray parts = content.getAsJsonArray("parts");
            if (parts == null) return "";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.size(); i++) {
                JsonObject p = parts.get(i).getAsJsonObject();
                if (p.has("text")) sb.append(p.get("text").getAsString());
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
