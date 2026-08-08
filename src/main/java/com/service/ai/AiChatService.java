package com.service.ai;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.model.Role;
import com.model.User;
import com.model.ai.AiChatMessage;
import com.permission.PermissionManager;
import com.service.AuthService;
import com.service.ai.GeminiService.FunctionCall;
import com.service.ai.GeminiService.GeminiTurnResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrator: system prompt theo role + function calling loop + ToolExecutor.
 * UI chỉ cần gọi {@link #chat(List, boolean)}.
 */
public final class AiChatService {

    private static final int MAX_TOOL_ROUNDS = 4;

    private final GeminiService gemini = new GeminiService();
    private final AiToolExecutor toolExecutor = new AiToolExecutor();

    public String chat(List<AiChatMessage> history, boolean clientSide) {
        if (!GeminiService.isConfigured()) {
            return "Trợ lý AI chưa được cấu hình (thiếu GEMINI_API_KEY). Vui lòng liên hệ quản trị viên.";
        }

        boolean isCustomer = resolveIsCustomer(clientSide);
        String systemInstruction = AiPromptBuilder.forCurrentSession(clientSide);
        List<AiTool> allowedTools = resolveAllowedTools(isCustomer);

        JsonArray extraContents = new JsonArray();

        try {
            for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
                GeminiTurnResult turn = gemini.chatWithTools(
                        history, systemInstruction, allowedTools, extraContents);

                if (!turn.hasFunctionCalls()) {
                    String text = turn.getText();
                    return (text == null || text.isBlank())
                            ? "Xin lỗi, mình chưa có câu trả lời phù hợp cho câu hỏi này."
                            : text;
                }

                if (turn.getModelContent() != null) {
                    extraContents.add(turn.getModelContent());
                }

                JsonObject userFnContent = new JsonObject();
                userFnContent.addProperty("role", "user");
                JsonArray responseParts = new JsonArray();

                for (FunctionCall fc : turn.getFunctionCalls()) {
                    String result = toolExecutor.execute(fc.name, fc.argsJson, isCustomer);

                    JsonObject fr = new JsonObject();
                    JsonObject frBody = new JsonObject();
                    frBody.addProperty("name", fc.name);
                    JsonObject responseObj = new JsonObject();
                    responseObj.addProperty("result", result);
                    frBody.add("response", responseObj);
                    fr.add("functionResponse", frBody);
                    responseParts.add(fr);
                }
                userFnContent.add("parts", responseParts);
                extraContents.add(userFnContent);
            }

            GeminiTurnResult finalTurn = gemini.chatWithTools(
                    history, systemInstruction, null, extraContents);
            String text = finalTurn.getText();
            return (text == null || text.isBlank())
                    ? "Xin lỗi, mình chưa tổng hợp được câu trả lời. Bạn thử hỏi lại rõ hơn nhé."
                    : text;

        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.AI_CHAT_FAIL, "AiChatService.chat", e);
            return "Không thể kết nối tới trợ lý AI lúc này. Vui lòng thử lại sau.";
        }
    }

    private boolean resolveIsCustomer(boolean clientSide) {
        if (clientSide) return true;
        User u = AuthService.getInstance().getCurrentUser();
        return u == null || u.getRole() == null || u.getRole() == Role.CUSTOMER;
    }

    private List<AiTool> resolveAllowedTools(boolean isCustomer) {
        List<AiTool> list = new ArrayList<>();
        for (AiTool t : AiTool.values()) {
            if (t.isAllowedFor(isCustomer, p -> PermissionManager.getInstance().can(p))) {
                list.add(t);
            }
        }
        return list;
    }
}