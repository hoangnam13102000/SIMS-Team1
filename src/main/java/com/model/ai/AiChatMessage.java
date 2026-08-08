package com.model.ai;

/**
 * Một lượt trong hội thoại với trợ lý AI (Gemini).
 * role: "user" (người dùng gõ) hoặc "model" (Gemini trả lời) - đúng theo
 * quy ước role của Gemini API để có thể build lại lịch sử hội thoại khi gọi API.
 */
public class AiChatMessage {

    public final String role;
    public final String text;
    public final long timestamp;

    public AiChatMessage(String role, String text) {
        this.role = role;
        this.text = text;
        this.timestamp = System.currentTimeMillis();
    }

    public boolean isUser() {
        return "user".equals(role);
    }
}