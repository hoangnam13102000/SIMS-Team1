package com.service;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.dao.ChatHistoryDAO;
import com.model.chat.ChatConversation;
import com.model.chat.ChatHistoryMessage;
import com.utils.ImageUtil;
import com.ws.ChatMessage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lưu lịch sử chat real-time (khách–NV, NV–NV).
 * Chạy async để không block WebSocket thread.
 * <p>
 * Chatbot AI (AiAssistantPanel / Gemini) KHÔNG gọi service này.
 */
public final class ChatHistoryService {

    private static final ChatHistoryService INSTANCE = new ChatHistoryService();

    public static ChatHistoryService getInstance() {
        return INSTANCE;
    }

    private final ChatHistoryDAO dao = new ChatHistoryDAO();
    private final ExecutorService pool = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "chat-history-writer");
        t.setDaemon(true);
        return t;
    });

    private ChatHistoryService() {
    }

    /**
     * Lưu tin CHAT khách ↔ hỗ trợ.
     * <ul>
     *   <li>fromAdmin=false: khách gửi → conversation theo userId khách</li>
     *   <li>fromAdmin=true: NV gửi → userId trong message = toUserId (khách nhận)</li>
     * </ul>
     */
    public void saveCustomerChatAsync(ChatMessage msg, int staffSenderUserIdIfAdmin) {
        if (msg == null || !msg.isChat()) return;
        pool.execute(() -> {
            try {
                int customerUserId;
                int senderUserId;
                boolean fromStaff;
                String senderName = msg.userName != null ? msg.userName : "";

                if (msg.fromAdmin) {
                    customerUserId = msg.userId; // trong protocol: userId = khách nhận
                    senderUserId = staffSenderUserIdIfAdmin > 0 ? staffSenderUserIdIfAdmin : 0;
                    fromStaff = true;
                } else {
                    customerUserId = msg.userId;
                    senderUserId = msg.userId;
                    fromStaff = false;
                }
                if (customerUserId <= 0) return;

                ChatConversation conv = dao.findOrCreateCustomerSupport(customerUserId);
                if (conv == null) return;

                String imagePath = saveImageIfPresent(msg);
                String[] fileSaved = saveFileIfPresent(msg);
                dao.insertMessage(
                        conv.getConversationId(),
                        senderUserId > 0 ? senderUserId : customerUserId,
                        senderName,
                        fromStaff,
                        msg.text,
                        imagePath,
                        msg.imageMime,
                        fileSaved != null ? fileSaved[0] : null,
                        fileSaved != null ? fileSaved[1] : null
                );
            } catch (Exception e) {
                AppLogger.getInstance().error(ErrorCode.DB_INSERT_FAIL,
                        "ChatHistoryService.saveCustomerChatAsync", e);
            }
        });
    }

    /** Lưu tin STAFF_CHAT (DM nội bộ). */
    public void saveStaffDmAsync(ChatMessage msg) {
        if (msg == null || !msg.isStaffChat()) return;
        if (msg.userId <= 0 || msg.toUserId <= 0) return;
        pool.execute(() -> {
            try {
                ChatConversation conv = dao.findOrCreateStaffDm(msg.userId, msg.toUserId);
                if (conv == null) return;
                String imagePath = saveImageIfPresent(msg);
                String[] fileSaved = saveFileIfPresent(msg);
                dao.insertMessage(
                        conv.getConversationId(),
                        msg.userId,
                        msg.userName != null ? msg.userName : "",
                        true,
                        msg.text,
                        imagePath,
                        msg.imageMime,
                        fileSaved != null ? fileSaved[0] : null,
                        fileSaved != null ? fileSaved[1] : null
                );
            } catch (Exception e) {
                AppLogger.getInstance().error(ErrorCode.DB_INSERT_FAIL,
                        "ChatHistoryService.saveStaffDmAsync", e);
            }
        });
    }

    public List<ChatHistoryMessage> loadCustomerHistory(int customerUserId, int limit) {
        return dao.listCustomerSupportHistory(customerUserId, limit);
    }

    public List<ChatHistoryMessage> loadStaffDmHistory(int userId1, int userId2, int limit) {
        return dao.listStaffDmHistory(userId1, userId2, limit);
    }

    public List<ChatConversation> listRecentCustomerThreads(int limit) {
        return dao.listRecentCustomerSupport(limit);
    }

    /** Xóa 1 tin nhắn đã lưu DB. */
    public boolean deleteMessage(long messageId) {
        return dao.deleteMessage(messageId);
    }

    /** Xóa toàn bộ lịch sử chat hỗ trợ của 1 khách. */
    public int clearCustomerHistory(int customerUserId) {
        return dao.deleteCustomerSupportMessages(customerUserId);
    }

    /** Xóa toàn bộ lịch sử DM giữa 2 nhân viên. */
    public int clearStaffDmHistory(int userId1, int userId2) {
        return dao.deleteStaffDmMessages(userId1, userId2);
    }


    /** @return [filePath, fileName] hoặc null */
    private String[] saveFileIfPresent(ChatMessage msg) {
        if (msg == null || !msg.hasFile()) return null;
        try {
            byte[] bytes = Base64.getDecoder().decode(msg.fileBase64);
            if (bytes == null || bytes.length == 0) return null;
            Path dir = Path.of("uploads", "chat", "files");
            Files.createDirectories(dir);
            String original = msg.fileName != null ? msg.fileName : "file.bin";
            String safeName = original.replaceAll("[\\/:*?\"<>|]", "_");
            String name = "f_" + System.currentTimeMillis() + "_" + safeName;
            Path out = dir.resolve(name);
            Files.write(out, bytes);
            return new String[] { "uploads/chat/files/" + name, original };
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_INSERT_FAIL,
                    "ChatHistoryService.saveFileIfPresent", e);
            return null;
        }
    }

    private String saveImageIfPresent(ChatMessage msg) {
        if (msg == null || !msg.hasImage()) return null;
        try {
            byte[] bytes = Base64.getDecoder().decode(msg.imageBase64);
            BufferedImage bi = ImageIO.read(new ByteArrayInputStream(bytes));
            if (bi == null) return null;

            Path dir = Path.of("uploads", "chat");
            Files.createDirectories(dir);
            String name = "chat_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8) + ".jpg";
            File out = dir.resolve(name).toFile();
            ImageIO.write(bi, "jpg", out);
            return "uploads/chat/" + name;
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_INSERT_FAIL,
                    "ChatHistoryService.saveImageIfPresent", e);
            return null;
        }
    }
}
