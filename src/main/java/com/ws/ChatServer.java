package com.ws;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.service.ChatHistoryService;
import com.google.gson.Gson;
import com.utils.NetworkErrorNotifier;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class ChatServer {

    private static final Gson GSON = new Gson();
    private static ChatServer instance;

    private final int port;
    private final CopyOnWriteArrayList<Consumer<ChatMessage>> listeners = new CopyOnWriteArrayList<>();

    private final ConcurrentHashMap<Integer, WebSocket> connectionsByUserId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<WebSocket, ChatMessage> sessionByConnection = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<Integer, WebSocket> staffConnections = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<WebSocket, ChatMessage> staffSessionByConnection = new ConcurrentHashMap<>();

    private InternalServer server;
    private final AtomicBoolean bindFailureNotified = new AtomicBoolean(false);

    ChatServer(int port) { this.port = port; }

    public static synchronized ChatServer getInstance() {
        if (instance == null) instance = new ChatServer(loadPort());
        return instance;
    }

    private static int loadPort() {
        Properties props = new Properties();
        try (InputStream in = ChatServer.class.getClassLoader().getResourceAsStream("ws.properties")) {
            if (in != null) props.load(in);
        } catch (IOException e) {
            AppLogger.getInstance().error(ErrorCode.WS_SERVER_START_FAIL,
                    "ChatServer.loadPort - khong doc duoc ws.properties, dung cong mac dinh", e);
        }
        return Integer.parseInt(props.getProperty("WS_CHAT_PORT", "8890"));
    }

    public synchronized void start() {
        if (server != null) return;
        server = new InternalServer(port);
        server.setReuseAddr(true);
        try {
            server.setConnectionLostTimeout(90);
        } catch (Exception ignored) {}
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(this::stopServer, "ChatServer-shutdown"));
    }

    public synchronized void stopServer() {
        if (server == null) return;
        try {
            server.stop(500);
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.WS_SERVER_START_FAIL,
                    "ChatServer.stopServer - loi khi dung WebSocket server", e);
        }
        server = null;
        connectionsByUserId.clear();
        sessionByConnection.clear();
        staffConnections.clear();
        staffSessionByConnection.clear();
    }

    public void addListener(Consumer<ChatMessage> listener) { listeners.add(listener); }
    public void removeListener(Consumer<ChatMessage> listener) { listeners.remove(listener); }

    public boolean sendToCustomer(int userId, String adminName, String text) {
        return sendToCustomer(userId, adminName, text, 0);
    }

    /**
     * Gửi tin nhắn NV -> khách. Luôn lưu vào lịch sử (DB) dù khách có đang online hay không,
     * để tin nhắn không bị mất và khách sẽ thấy lại khi họ kết nối lại / mở lại chat.
     * Trả về true nếu gửi realtime thành công (khách đang online), false nếu khách offline
     * (tin nhắn vẫn được lưu, chỉ là chưa hiển thị ngay cho khách).
     */
    public boolean sendToCustomer(int userId, String adminName, String text, int staffSenderUserId) {
        ChatMessage msg = ChatMessage.chatFromAdmin(userId, adminName, text);
        WebSocket conn = connectionsByUserId.get(userId);
        boolean delivered = conn != null && conn.isOpen();
        if (delivered) {
            conn.send(GSON.toJson(msg));
        }
        if (text != null && !text.isBlank()) {
            ChatHistoryService.getInstance().saveCustomerChatAsync(msg, staffSenderUserId);
        }
        return delivered;
    }

    public boolean sendImageToCustomer(int userId, String adminName, String text,
                                       String imageBase64, String imageMime) {
        return sendImageToCustomer(userId, adminName, text, imageBase64, imageMime, 0);
    }

    /** Gửi ảnh NV -> khách. Cũng luôn lưu lịch sử như {@link #sendToCustomer}. */
    public boolean sendImageToCustomer(int userId, String adminName, String text,
                                       String imageBase64, String imageMime, int staffSenderUserId) {
        if (imageBase64 == null || imageBase64.isBlank()) return false;
        ChatMessage msg = ChatMessage.imageFromAdmin(userId, adminName, text, imageBase64, imageMime);
        WebSocket conn = connectionsByUserId.get(userId);
        boolean delivered = conn != null && conn.isOpen();
        if (delivered) {
            conn.send(GSON.toJson(msg));
        }
        ChatHistoryService.getInstance().saveCustomerChatAsync(msg, staffSenderUserId);
        return delivered;
    }

    /** Gui file NV -> khach. Luon luu lich su. */
    public boolean sendFileToCustomer(int userId, String adminName, String text,
                                      String fileBase64, String fileName, String fileMime, int staffSenderUserId) {
        if (fileBase64 == null || fileBase64.isBlank() || fileName == null || fileName.isBlank()) return false;
        ChatMessage msg = ChatMessage.fileFromAdmin(userId, adminName, text, fileBase64, fileName, fileMime);
        WebSocket conn = connectionsByUserId.get(userId);
        boolean delivered = conn != null && conn.isOpen();
        if (delivered) {
            conn.send(GSON.toJson(msg));
        }
        ChatHistoryService.getInstance().saveCustomerChatAsync(msg, staffSenderUserId);
        return delivered;
    }
    public boolean sendVoiceToCustomer(int userId, String adminName, String transcript,
                                       String voiceBase64, String voiceMime, int durationMs, int staffSenderUserId) {
        if (voiceBase64 == null || voiceBase64.isBlank()) return false;
        ChatMessage msg = ChatMessage.voiceFromAdmin(
                userId, adminName, transcript, voiceBase64, voiceMime, durationMs);
        WebSocket conn = connectionsByUserId.get(userId);
        boolean delivered = conn != null && conn.isOpen();
        if (delivered) {
            conn.send(GSON.toJson(msg));
        }
        ChatHistoryService.getInstance().saveCustomerChatAsync(msg, staffSenderUserId);
        return delivered;
    }

    public java.util.Set<Integer> onlineCustomerIds() {
        return new java.util.HashSet<>(connectionsByUserId.keySet());
    }

    public Map<Integer, String[]> onlineStaff() {
        Map<Integer, String[]> result = new HashMap<>();
        for (Map.Entry<WebSocket, ChatMessage> e : staffSessionByConnection.entrySet()) {
            if (e.getKey() != null && e.getKey().isOpen() && e.getValue() != null) {
                ChatMessage s = e.getValue();
                result.put(s.userId, new String[]{s.userName, s.roleCode != null ? s.roleCode : ""});
            }
        }
        return result;
    }

    public boolean isStaffOnline(int userId) {
        WebSocket conn = staffConnections.get(userId);
        return conn != null && conn.isOpen();
    }

    private void dispatch(ChatMessage message) {
        for (Consumer<ChatMessage> listener : listeners) {
            javax.swing.SwingUtilities.invokeLater(() -> listener.accept(message));
        }
    }

    private void sendToStaff(int userId, ChatMessage message) {
        WebSocket conn = staffConnections.get(userId);
        if (conn != null && conn.isOpen()) conn.send(GSON.toJson(message));
    }

    private void broadcastToStaff(ChatMessage message, Integer excludeUserId) {
        if (message == null) return;
        final String json = GSON.toJson(message);
        // Snapshot để tránh ConcurrentModification khi staff join/leave trong lúc gửi
        java.util.List<java.util.Map.Entry<Integer, WebSocket>> snapshot =
                new java.util.ArrayList<>(staffConnections.entrySet());
        // Gửi async: tránh re-entrancy trên thread WebSocket (onMessage đang chạy)
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            for (java.util.Map.Entry<Integer, WebSocket> e : snapshot) {
                if (excludeUserId != null && excludeUserId.equals(e.getKey())) continue;
                WebSocket conn = e.getValue();
                if (conn != null && conn.isOpen()) {
                    try {
                        conn.send(json);
                    } catch (Exception ex) {
                        AppLogger.getInstance().error(ErrorCode.WS_MESSAGE_FAIL,
                                "ChatServer.broadcastToStaff - gui that bai toi staff userId=" + e.getKey(), ex);
                    }
                }
            }
        });
    }

    private class InternalServer extends WebSocketServer {
        InternalServer(int port) { super(new InetSocketAddress(port)); }

        @Override public void onOpen(WebSocket conn, ClientHandshake handshake) {}

        @Override
        public void onClose(WebSocket conn, int code, String reason, boolean remote) {
            ChatMessage session = sessionByConnection.remove(conn);
            if (session != null) {
                connectionsByUserId.remove(session.userId, conn);
                dispatch(ChatMessage.leave(session.userId, session.userName));
            }
            ChatMessage staffSession = staffSessionByConnection.remove(conn);
            if (staffSession != null) {
                staffConnections.remove(staffSession.userId, conn);
                ChatMessage leave = ChatMessage.staffLeave(staffSession.userId, staffSession.userName);
                broadcastToStaff(leave, staffSession.userId);
                dispatch(leave);
            }
        }

        @Override
        public void onMessage(WebSocket conn, String message) {
            try {
                ChatMessage chatMessage = GSON.fromJson(message, ChatMessage.class);
                if (chatMessage == null) return;

                if (chatMessage.isJoin()) {
                    connectionsByUserId.put(chatMessage.userId, conn);
                    sessionByConnection.put(conn, chatMessage);
                    // Báo staff có khách online
                    broadcastToStaff(chatMessage, null);
                    dispatch(chatMessage);
                    return;
                }

                if (chatMessage.isStaffJoin()) {
                    WebSocket old = staffConnections.put(chatMessage.userId, conn);
                    if (old != null && old != conn) {
                        staffSessionByConnection.remove(old);
                        try {
                            old.close();
                        } catch (Exception e) {
                            AppLogger.getInstance().error(ErrorCode.WS_MESSAGE_FAIL,
                                    "ChatServer.onMessage(staffJoin) - loi dong ket noi cu userId=" + chatMessage.userId, e);
                        }
                    }
                    staffSessionByConnection.put(conn, chatMessage);
                    broadcastToStaff(chatMessage, chatMessage.userId);
                    for (Map.Entry<WebSocket, ChatMessage> e : staffSessionByConnection.entrySet()) {
                        ChatMessage other = e.getValue();
                        if (other != null && other.userId != chatMessage.userId && e.getKey().isOpen()) {
                            conn.send(GSON.toJson(ChatMessage.staffJoin(other.userId, other.userName, other.roleCode)));
                        }
                    }
                    dispatch(chatMessage);
                    return;
                }

                if (chatMessage.isStaffLeave()) {
                    staffConnections.remove(chatMessage.userId, conn);
                    staffSessionByConnection.remove(conn);
                    broadcastToStaff(chatMessage, chatMessage.userId);
                    dispatch(chatMessage);
                    return;
                }

                if (chatMessage.isStaffChat()) {
                    sendToStaff(chatMessage.toUserId, chatMessage);
                    sendToStaff(chatMessage.userId, chatMessage);
                    dispatch(chatMessage);
                    // Lưu lịch sử chat nội bộ NV–NV
                    ChatHistoryService.getInstance().saveStaffDmAsync(chatMessage);
                    return;
                }

                // Tin hỗ trợ từ nhân viên phải đi qua WebSocket server này. Trước đây màn hình
                // quản trị gọi sendToCustomer() cục bộ nên khác máy/process không thể chuyển tiếp.
                if (chatMessage.isChat() && chatMessage.fromAdmin) {
                    ChatMessage staffSession = staffSessionByConnection.get(conn);
                    if (staffSession == null) {
                        AppLogger.getInstance().error(ErrorCode.WS_MESSAGE_FAIL,
                                "ChatServer.onMessage - tu choi tin gui khach tu socket khong phai nhan vien", null);
                        return;
                    }
                    // Không tin userName do client gửi lên; dùng danh tính đã đăng ký khi STAFF_JOIN.
                    chatMessage.userName = staffSession.userName;
                    WebSocket customer = connectionsByUserId.get(chatMessage.userId);
                    if (customer != null && customer.isOpen()) {
                        customer.send(GSON.toJson(chatMessage));
                    }
                    ChatHistoryService.getInstance().saveCustomerChatAsync(chatMessage, staffSession.userId);
                    dispatch(chatMessage);
                    return;
                }

                if (chatMessage.isLeave()) {
                    connectionsByUserId.remove(chatMessage.userId, conn);
                    sessionByConnection.remove(conn);
                    // Báo staff khách offline
                    broadcastToStaff(chatMessage, null);
                    dispatch(chatMessage);
                    return;
                }

                // Lưu lịch sử khách ↔ hỗ trợ (text / ảnh / file / thoại)
                if (chatMessage.isChat()
                        && ((chatMessage.text != null && !chatMessage.text.isBlank())
                            || chatMessage.hasImage() || chatMessage.hasFile() || chatMessage.hasVoice())) {
                    ChatHistoryService.getInstance().saveCustomerChatAsync(chatMessage, 0);
                }

                // FIX: đẩy tin khách (kể cả thoại) tới MỌI nhân viên đang online qua WebSocket.
                // Trước đây chỉ dispatch() local → process/máy khác không nhận, không có chuông.
                if (chatMessage.isChat() && !chatMessage.fromAdmin) {
                    broadcastToStaff(chatMessage, null);
                }

                dispatch(chatMessage);
            } catch (Exception e) {
                AppLogger.getInstance().error(ErrorCode.WS_MESSAGE_FAIL,
                        "ChatServer.onMessage - khong xu ly duoc payload chat", e);
            }
        }

        @Override
        public void onError(WebSocket conn, Exception ex) {
            if (NetworkErrorNotifier.isBindFailure(ex)) {
                // Cong da bi process khac giu. Khong popup: dung che do client.
                if (bindFailureNotified.compareAndSet(false, true)) {
                    AppLogger.getInstance().error(ErrorCode.WS_SERVER_START_FAIL,
                            "ChatServer - cong " + port + " da bi chiem, bo qua bind, dung che do client"
                                    + " (ket noi toi WS_HOST trong ws.properties).", ex);
                    synchronized (ChatServer.this) {
                        if (server == this) {
                            try {
                                stop(0);
                            } catch (Exception stopEx) {
                                AppLogger.getInstance().error(ErrorCode.WS_SERVER_START_FAIL,
                                        "ChatServer - loi khi dung server sau bind failure", stopEx);
                            }
                            server = null;
                        }
                    }
                }
                return;
            }
            AppLogger.getInstance().error(ErrorCode.WS_CONNECTION_FAIL,
                    "ChatServer.onError - loi ket noi tu client", ex);
        }

        @Override public void onStart() {}
    }
}
