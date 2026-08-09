package com.model.chat;

import java.time.LocalDateTime;

/** Một tin đã lưu DB (không dùng cho chatbot AI). */
public class ChatHistoryMessage {

    private long messageId;
    private int conversationId;
    private int senderUserId;
    private String senderName;
    private boolean fromStaff;
    private String bodyText;
    private String imagePath;
    private String imageMime;
    private String filePath;
    private String fileName;
    private LocalDateTime createdAt;
    private boolean readByPeer;

    public long getMessageId() { return messageId; }
    public void setMessageId(long messageId) { this.messageId = messageId; }

    public int getConversationId() { return conversationId; }
    public void setConversationId(int conversationId) { this.conversationId = conversationId; }

    public int getSenderUserId() { return senderUserId; }
    public void setSenderUserId(int senderUserId) { this.senderUserId = senderUserId; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public boolean isFromStaff() { return fromStaff; }
    public void setFromStaff(boolean fromStaff) { this.fromStaff = fromStaff; }

    public String getBodyText() { return bodyText; }
    public void setBodyText(String bodyText) { this.bodyText = bodyText; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public String getImageMime() { return imageMime; }
    public void setImageMime(String imageMime) { this.imageMime = imageMime; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public boolean isReadByPeer() { return readByPeer; }
    public void setReadByPeer(boolean readByPeer) { this.readByPeer = readByPeer; }

    public boolean hasImage() {
        return imagePath != null && !imagePath.isBlank();
    }

    public boolean hasFile() {
        return filePath != null && !filePath.isBlank();
    }
}
