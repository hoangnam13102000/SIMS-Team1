package com.ws;

/**
 * Du lieu 1 tin nhan trao doi qua WebSocket giua khach hang (ChatClient) va
 * man hinh quan tri (ChatServer), duoc serialize/deserialize bang Gson.
 *
 * type:
 *  - "JOIN"  : client vua ket noi, bao userId/userName de admin biet dang chat voi ai.
 *  - "CHAT"  : mot tin nhan noi dung (text).
 *  - "LEAVE" : client chu dong ngat ket noi (dong cua so / thoat app / dang xuat).
 */
public class ChatMessage {

    public String type;
    public int userId;
    public String userName;
    public String text;
    public long timestamp;
    /** true neu tin nhan nay la cua nhan vien quan tri gui xuong cho khach hang. */
    public boolean fromAdmin;

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

    public static ChatMessage join(int userId, String userName) {
        return new ChatMessage("JOIN", userId, userName, null, false);
    }

    public static ChatMessage leave(int userId, String userName) {
        return new ChatMessage("LEAVE", userId, userName, null, false);
    }

    public static ChatMessage chat(int userId, String userName, String text) {
        return new ChatMessage("CHAT", userId, userName, text, false);
    }

    public static ChatMessage chatFromAdmin(int toUserId, String adminName, String text) {
        return new ChatMessage("CHAT", toUserId, adminName, text, true);
    }

    public boolean isJoin() {
        return "JOIN".equals(type);
    }

    public boolean isChat() {
        return "CHAT".equals(type);
    }

    public boolean isLeave() {
        return "LEAVE".equals(type);
    }
}