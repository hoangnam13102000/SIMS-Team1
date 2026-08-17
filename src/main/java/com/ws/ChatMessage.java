package com.ws;

public class ChatMessage {
    public String type;
    public int userId;
    public String userName;
    public String text;
    public long timestamp;
    /** ID tin đã lưu DB (0 nếu tin realtime chưa persist / không có id). */
    public long messageId;
    /** true nếu tin nhắn này là của nhân viên quản trị gửi xuống cho khách hàng. */
    public boolean fromAdmin;
    public String imageBase64;
    public String imageMime;
    
    /** File đính kèm (dạng Base64 - giữ lại cho tương thích ngược & file nhỏ < 3MB) */
    public String fileBase64;
    public String fileName;
    public String fileMime;
    
    /** File đính kèm (dạng URL Cloudinary - ưu tiên dùng cho file lớn) */
    public String fileUrl;
    /** Dung lượng file tính bằng bytes, 0 nếu không biết */
    public long fileSize;
    
    /**
     * Tin nhắn thoại: audio + (tuỳ chọn) transcript.
     * text thường chứa transcript hoặc nhãn "[Tin nhắn thoại]".
     */
    public String voiceBase64;
    public String voiceMime;
    /** Thời lượng ms (ước lượng), 0 nếu không biết. */
    public int voiceDurationMs;
    
    /** STAFF_CHAT: userId = người gửi, toUserId = người nhận. */
    public int toUserId;
    public String roleCode;
    public boolean staff;

    public ChatMessage() {
    }

    public ChatMessage(String type, int userId, String userName, String text, boolean fromAdmin) {
        this.type = type;
        this.userId = userId;
        this.userName = userName;
        this.text = text;
        this.fromAdmin = fromAdmin;
        this.timestamp = System.currentTimeMillis();
    }

    // ================================================================
    // HỆ THỐNG
    // ================================================================

    public static ChatMessage join(int userId, String userName) {
        return new ChatMessage("JOIN", userId, userName, null, false);
    }

    public static ChatMessage leave(int userId, String userName) {
        return new ChatMessage("LEAVE", userId, userName, null, false);
    }

    // ================================================================
    // TIN NHẮN VĂN BẢN
    // ================================================================

    public static ChatMessage chat(int userId, String userName, String text) {
        return new ChatMessage("CHAT", userId, userName, text, false);
    }

    public static ChatMessage chatFromAdmin(int toUserId, String adminName, String text) {
        return new ChatMessage("CHAT", toUserId, adminName, text, true);
    }

    // ================================================================
    // ẢNH (Base64)
    // ================================================================

    public static ChatMessage image(int userId, String userName, String text,
                                    String imageBase64, String imageMime) {
        ChatMessage m = new ChatMessage("CHAT", userId, userName, text, false);
        m.imageBase64 = imageBase64;
        m.imageMime = imageMime != null ? imageMime : "image/jpeg";
        return m;
    }

    public static ChatMessage imageFromAdmin(int toUserId, String adminName, String text,
                                             String imageBase64, String imageMime) {
        ChatMessage m = new ChatMessage("CHAT", toUserId, adminName, text, true);
        m.imageBase64 = imageBase64;
        m.imageMime = imageMime != null ? imageMime : "image/jpeg";
        return m;
    }

    // ================================================================
    // FILE DẠNG BASE64 (giữ lại tương thích ngược)
    // ================================================================

    public static ChatMessage file(int userId, String userName, String text,
                                   String fileBase64, String fileName, String fileMime) {
        ChatMessage m = new ChatMessage("CHAT", userId, userName, text, false);
        m.fileBase64 = fileBase64;
        m.fileName = fileName;
        m.fileMime = fileMime != null ? fileMime : "application/octet-stream";
        return m;
    }

    public static ChatMessage fileFromAdmin(int toUserId, String adminName, String text,
                                            String fileBase64, String fileName, String fileMime) {
        ChatMessage m = new ChatMessage("CHAT", toUserId, adminName, text, true);
        m.fileBase64 = fileBase64;
        m.fileName = fileName;
        m.fileMime = fileMime != null ? fileMime : "application/octet-stream";
        return m;
    }

    // ================================================================
    // FILE DẠNG URL CLOUDINARY (MỚI)
    // ================================================================

    public static ChatMessage fileUrl(int userId, String userName, String text,
                                      String fileUrl, String fileName, long fileSize) {
        ChatMessage m = new ChatMessage("CHAT", userId, userName, text, false);
        m.fileUrl = fileUrl;
        m.fileName = fileName;
        m.fileSize = fileSize;
        return m;
    }

    public static ChatMessage fileUrlFromAdmin(int toUserId, String adminName, String text,
                                               String fileUrl, String fileName, long fileSize) {
        ChatMessage m = new ChatMessage("CHAT", toUserId, adminName, text, true);
        m.fileUrl = fileUrl;
        m.fileName = fileName;
        m.fileSize = fileSize;
        return m;
    }

    // ================================================================
    // TIN NHẮN THOẠI
    // ================================================================

    public static ChatMessage voice(int userId, String userName, String transcript,
                                    String voiceBase64, String voiceMime, int durationMs) {
        String label = (transcript != null && !transcript.isBlank())
                ? transcript : "[Tin nhắn thoại]";
        ChatMessage m = new ChatMessage("CHAT", userId, userName, label, false);
        m.voiceBase64 = voiceBase64;
        m.voiceMime = voiceMime != null ? voiceMime : "audio/wav";
        m.voiceDurationMs = Math.max(0, durationMs);
        // Đồng bộ file fields để lịch sử DB lưu như file đính kèm
        m.fileBase64 = voiceBase64;
        m.fileName = "voice.wav";
        m.fileMime = m.voiceMime;
        return m;
    }

    public static ChatMessage voiceFromAdmin(int toUserId, String adminName, String transcript,
                                             String voiceBase64, String voiceMime, int durationMs) {
        String label = (transcript != null && !transcript.isBlank())
                ? transcript : "[Voice message]";
        ChatMessage m = new ChatMessage("CHAT", toUserId, adminName, label, true);
        m.voiceBase64 = voiceBase64;
        m.voiceMime = voiceMime != null ? voiceMime : "audio/wav";
        m.voiceDurationMs = Math.max(0, durationMs);
        m.fileBase64 = voiceBase64;
        m.fileName = "voice.wav";
        m.fileMime = m.voiceMime;
        return m;
    }

    // ================================================================
    // NHÂN VIÊN / STAFF
    // ================================================================

    public static ChatMessage staffJoin(int userId, String userName, String roleCode) {
        ChatMessage m = new ChatMessage("STAFF_JOIN", userId, userName, null, false);
        m.staff = true;
        m.roleCode = roleCode;
        return m;
    }

    public static ChatMessage staffLeave(int userId, String userName) {
        ChatMessage m = new ChatMessage("STAFF_LEAVE", userId, userName, null, false);
        m.staff = true;
        return m;
    }

    public static ChatMessage staffChat(int fromUserId, String fromName, int toUserId, String text) {
        ChatMessage m = new ChatMessage("STAFF_CHAT", fromUserId, fromName, text, false);
        m.staff = true;
        m.toUserId = toUserId;
        return m;
    }

    public static ChatMessage staffImage(int fromUserId, String fromName, int toUserId,
                                         String text, String imageBase64, String imageMime) {
        ChatMessage m = new ChatMessage("STAFF_CHAT", fromUserId, fromName, text, false);
        m.staff = true;
        m.toUserId = toUserId;
        m.imageBase64 = imageBase64;
        m.imageMime = imageMime != null ? imageMime : "image/jpeg";
        return m;
    }

    public static ChatMessage staffFile(int fromUserId, String fromName, int toUserId,
                                        String text, String fileBase64, String fileName, String fileMime) {
        ChatMessage m = new ChatMessage("STAFF_CHAT", fromUserId, fromName, text, false);
        m.staff = true;
        m.toUserId = toUserId;
        m.fileBase64 = fileBase64;
        m.fileName = fileName;
        m.fileMime = fileMime != null ? fileMime : "application/octet-stream";
        return m;
    }

    public static ChatMessage staffFileUrl(int fromUserId, String fromName, int toUserId,
                                           String text, String fileUrl, String fileName, long fileSize) {
        ChatMessage m = new ChatMessage("STAFF_CHAT", fromUserId, fromName, text, false);
        m.staff = true;
        m.toUserId = toUserId;
        m.fileUrl = fileUrl;
        m.fileName = fileName;
        m.fileSize = fileSize;
        return m;
    }

    public static ChatMessage staffVoice(int fromUserId, String fromName, int toUserId,
                                         String transcript, String voiceBase64, String voiceMime, int durationMs) {
        String label = (transcript != null && !transcript.isBlank())
                ? transcript : "[Tin nhắn thoại]";
        ChatMessage m = new ChatMessage("STAFF_CHAT", fromUserId, fromName, label, false);
        m.staff = true;
        m.toUserId = toUserId;
        m.voiceBase64 = voiceBase64;
        m.voiceMime = voiceMime != null ? voiceMime : "audio/wav";
        m.voiceDurationMs = Math.max(0, durationMs);
        m.fileBase64 = voiceBase64;
        m.fileName = "voice.wav";
        m.fileMime = m.voiceMime;
        return m;
    }

    // ================================================================
    // KIỂM TRA LOẠI TIN NHẮN
    // ================================================================

    public boolean isJoin() { return "JOIN".equals(type); }
    public boolean isChat() { return "CHAT".equals(type); }
    public boolean isLeave() { return "LEAVE".equals(type); }
    public boolean isStaffJoin() { return "STAFF_JOIN".equals(type); }
    public boolean isStaffLeave() { return "STAFF_LEAVE".equals(type); }
    public boolean isStaffChat() { return "STAFF_CHAT".equals(type); }
    public boolean hasImage() { return imageBase64 != null && !imageBase64.isBlank(); }
    
    /** Kiểm tra tin nhắn có file đính kèm (dạng URL hoặc Base64) */
    public boolean hasFile() {
        return (fileUrl != null && !fileUrl.isBlank())
                || (fileBase64 != null && !fileBase64.isBlank()
                    && fileName != null && !fileName.isBlank());
    }
    
    /** Kiểm tra file dạng URL Cloudinary */
    public boolean hasFileUrl() {
        return fileUrl != null && !fileUrl.isBlank();
    }
    
    public boolean hasVoice() {
        return voiceBase64 != null && !voiceBase64.isBlank();
    }
}