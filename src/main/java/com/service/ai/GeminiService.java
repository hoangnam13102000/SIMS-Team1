package com.service.ai;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.model.ai.AiChatMessage;
import com.security.AppConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Gọi Gemini API (generateContent) – hỗ trợ cả chat text thuần và function calling.
 * API key đọc từ {@link AppConfig} (GEMINI_API_KEY), không hardcode.
 */
public final class GeminiService {

    private static final String API_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String DEFAULT_MODEL = "gemini-2.5-flash";

    private final HttpClient http = HttpClient.newHttpClient();
    private final String apiKey;
    private final String model;

    public GeminiService() {
        AppConfig config = AppConfig.getInstance();
        this.apiKey = config.get("GEMINI_API_KEY");
        this.model = config.get("GEMINI_MODEL", DEFAULT_MODEL);
    }

    public static boolean isConfigured() {
        try {
            String key = AppConfig.getInstance().get("GEMINI_API_KEY", "");
            return key != null && !key.isBlank();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Chat đơn giản (tương thích code cũ) – không dùng tool.
     */
    public String chat(List<AiChatMessage> history, String systemInstruction)
            throws IOException, InterruptedException {
        GeminiTurnResult r = chatWithTools(history, systemInstruction, null);
        return r.getText() != null ? r.getText() : "";
    }

    /**
     * Một lượt gọi Gemini. Có thể trả về text hoặc danh sách functionCall.
     *
     * @param history           lịch sử user/model (text)
     * @param systemInstruction system prompt
     * @param allowedTools      null/empty = không gửi tools; ngược lại gửi functionDeclarations
     * @param extraContents     các content trung gian (functionCall / functionResponse) đã xảy ra trong vòng lặp tool
     */
    public GeminiTurnResult chatWithTools(
            List<AiChatMessage> history,
            String systemInstruction,
            List<AiTool> allowedTools,
            JsonArray extraContents
    ) throws IOException, InterruptedException {

        JsonArray contents = new JsonArray();
        for (AiChatMessage msg : history) {
            JsonObject content = new JsonObject();
            content.addProperty("role", msg.isUser() ? "user" : "model");
            JsonArray parts = new JsonArray();
            JsonObject part = new JsonObject();
            part.addProperty("text", msg.text);
            parts.add(part);
            content.add("parts", parts);
            contents.add(content);
        }
        if (extraContents != null) {
            for (JsonElement el : extraContents) {
                contents.add(el);
            }
        }

        JsonObject body = new JsonObject();
        body.add("contents", contents);

        if (systemInstruction != null && !systemInstruction.isBlank()) {
            JsonObject sysInstr = new JsonObject();
            JsonArray sysParts = new JsonArray();
            JsonObject sysPart = new JsonObject();
            sysPart.addProperty("text", systemInstruction);
            sysParts.add(sysPart);
            sysInstr.add("parts", sysParts);
            body.add("systemInstruction", sysInstr);
        }

        if (allowedTools != null && !allowedTools.isEmpty()) {
            body.add("tools", buildToolsArray(allowedTools));
            JsonObject toolConfig = new JsonObject();
            JsonObject fcc = new JsonObject();
            fcc.addProperty("mode", "AUTO");
            toolConfig.add("functionCallingConfig", fcc);
            body.add("toolConfig", toolConfig);
        }

        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("temperature", 0.4);
        generationConfig.addProperty("maxOutputTokens", 2048);
        body.add("generationConfig", generationConfig);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + model + ":generateContent"))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .timeout(Duration.ofSeconds(45))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            AppLogger.getInstance().error(ErrorCode.AI_CHAT_FAIL,
                    "GeminiService.chatWithTools - HTTP " + response.statusCode() + ": " + response.body(), null);
            throw new IOException("Gemini API trả lỗi (HTTP " + response.statusCode() + ")");
        }

        return parseTurn(response.body());
    }

    /** Overload không có extraContents. */
    public GeminiTurnResult chatWithTools(
            List<AiChatMessage> history,
            String systemInstruction,
            List<AiTool> allowedTools
    ) throws IOException, InterruptedException {
        return chatWithTools(history, systemInstruction, allowedTools, null);
    }

    private JsonArray buildToolsArray(List<AiTool> tools) {
        JsonArray functionDeclarations = new JsonArray();
        for (AiTool t : tools) {
            JsonObject decl = new JsonObject();
            decl.addProperty("name", t.getName());
            decl.addProperty("description", t.getDescription());
            try {
                decl.add("parameters", JsonParser.parseString(t.getParametersJson()));
            } catch (Exception e) {
                JsonObject empty = new JsonObject();
                empty.addProperty("type", "object");
                decl.add("parameters", empty);
            }
            functionDeclarations.add(decl);
        }
        JsonObject tool = new JsonObject();
        tool.add("functionDeclarations", functionDeclarations);
        JsonArray toolsArr = new JsonArray();
        toolsArr.add(tool);
        return toolsArr;
    }

    private GeminiTurnResult parseTurn(String responseBody) throws IOException {
        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            JsonArray candidates = json.getAsJsonArray("candidates");
            if (candidates == null || candidates.isEmpty()) {
                throw new IOException("Gemini không trả về nội dung nào (có thể bị chặn bởi bộ lọc an toàn).");
            }
            JsonObject first = candidates.get(0).getAsJsonObject();
            JsonObject content = first.getAsJsonObject("content");
            if (content == null) {
                throw new IOException("Gemini không trả về content.");
            }
            JsonArray parts = content.getAsJsonArray("parts");
            if (parts == null || parts.isEmpty()) {
                throw new IOException("Gemini không trả về parts.");
            }

            List<FunctionCall> calls = new ArrayList<>();
            StringBuilder text = new StringBuilder();
            // Giữ nguyên content model để gửi lại trong vòng tool tiếp theo
            JsonObject modelContent = new JsonObject();
            modelContent.addProperty("role", "model");
            modelContent.add("parts", parts);

            for (JsonElement el : parts) {
                JsonObject part = el.getAsJsonObject();
                if (part.has("text") && !part.get("text").isJsonNull()) {
                    text.append(part.get("text").getAsString());
                }
                if (part.has("functionCall")) {
                    JsonObject fc = part.getAsJsonObject("functionCall");
                    String name = fc.has("name") ? fc.get("name").getAsString() : "";
                    String args = "{}";
                    if (fc.has("args")) {
                        args = fc.get("args").toString();
                    }
                    calls.add(new FunctionCall(name, args));
                }
            }

            return new GeminiTurnResult(text.toString().trim(), calls, modelContent);
        } catch (IOException ioe) {
            throw ioe;
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.AI_CHAT_FAIL, "GeminiService.parseTurn", e);
            throw new IOException("Không đọc được phản hồi từ Gemini.", e);
        }
    }

    // ---------- DTOs ----------

    public static final class FunctionCall {
        public final String name;
        public final String argsJson;

        public FunctionCall(String name, String argsJson) {
            this.name = name;
            this.argsJson = argsJson != null ? argsJson : "{}";
        }
    }

    public static final class GeminiTurnResult {
        private final String text;
        private final List<FunctionCall> functionCalls;
        /** Content role=model của lượt này (để append vào extraContents). */
        private final JsonObject modelContent;

        public GeminiTurnResult(String text, List<FunctionCall> functionCalls, JsonObject modelContent) {
            this.text = text;
            this.functionCalls = functionCalls != null ? functionCalls : List.of();
            this.modelContent = modelContent;
        }

        public String getText() {
            return text;
        }

        public List<FunctionCall> getFunctionCalls() {
            return functionCalls;
        }

        public boolean hasFunctionCalls() {
            return functionCalls != null && !functionCalls.isEmpty();
        }

        public JsonObject getModelContent() {
            return modelContent;
        }
    }
}
