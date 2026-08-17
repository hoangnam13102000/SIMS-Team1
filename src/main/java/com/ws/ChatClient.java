package com.ws;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

public class ChatClient {
    private static final Gson GSON = new Gson();
    private static final long RECONNECT_INTERVAL_SECONDS = 5;
    private static final int MAX_PENDING_MESSAGES = 100;
    private static ChatClient instance;
    private final CopyOnWriteArrayList<Consumer<ChatMessage>> messageListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<Boolean>> connectionListeners = new CopyOnWriteArrayList<>();
    private volatile WebSocketClient client;
    private int userId;
    private String userName;
    private String roleCode;
    private boolean staffMode;
    private volatile boolean wantConnected = false;
    private volatile boolean connectAttemptInFlight = false;
    private ScheduledExecutorService reconnectScheduler;
    /** Tin người dùng gửi ngay lúc socket đang bắt tay được giữ lại để gửi sau onOpen. */
    private final ConcurrentLinkedQueue<String> pendingMessages = new ConcurrentLinkedQueue<>();

    private ChatClient() {}

    public static synchronized ChatClient getInstance() {
        if (instance == null) instance = new ChatClient();
        return instance;
    }

    public void addMessageListener(Consumer<ChatMessage> listener) { messageListeners.add(listener); }
    public void removeMessageListener(Consumer<ChatMessage> listener) { messageListeners.remove(listener); }
    public void addConnectionListener(Consumer<Boolean> listener) { connectionListeners.add(listener); }
    public void removeConnectionListener(Consumer<Boolean> listener) { connectionListeners.remove(listener); }

    public synchronized void connect(int userId, String userName) {
        this.userId = userId;
        this.userName = userName;
        this.roleCode = null;
        this.staffMode = false;
        this.wantConnected = true;
        attemptConnect();
        ensureReconnectSchedulerRunning();
    }

    public synchronized void connectStaff(int userId, String userName, String roleCode) {
        this.userId = userId;
        this.userName = userName;
        this.roleCode = roleCode;
        this.staffMode = true;
        this.wantConnected = true;
        attemptConnect();
        ensureReconnectSchedulerRunning();
    }

    public boolean isStaffMode() { return staffMode; }
    public int getUserId() { return userId; }

    private synchronized void attemptConnect() {
        if (!wantConnected) return;
        if (client != null && client.isOpen()) return;
        if (connectAttemptInFlight) return;
        Properties props = new Properties();
        try (InputStream in = ChatClient.class.getClassLoader().getResourceAsStream("ws.properties")) {
            if (in != null) props.load(in);
        } catch (IOException e) {
            AppLogger.getInstance().error(ErrorCode.WS_CONNECTION_FAIL,
                    "ChatClient.attemptConnect - khong doc duoc ws.properties, dung gia tri mac dinh", e);
        }
        String configuredUrl = firstNonBlank(
                System.getProperty("ws.chat.url"),
                System.getenv("WS_CHAT_URL"),
                props.getProperty("WS_CHAT_URL"));
        String host = firstNonBlank(System.getProperty("ws.chat.host"),
                System.getenv("WS_CHAT_HOST"), props.getProperty("WS_HOST"), "localhost");
        String configuredPort = firstNonBlank(System.getProperty("ws.chat.port"),
                System.getenv("WS_CHAT_PORT"), props.getProperty("WS_CHAT_PORT"), "8890");
        int port = Integer.parseInt(configuredPort);
        try {
            connectAttemptInFlight = true;
            URI endpoint = configuredUrl != null
                    ? new URI(configuredUrl)
                    : new URI("ws://" + host + ":" + port);
            client = new WebSocketClient(endpoint) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    if (ChatClient.this.client != this) {
                        close();
                        return;
                    }
                    connectAttemptInFlight = false;
                    if (staffMode) {
                        send(GSON.toJson(ChatMessage.staffJoin(
                                ChatClient.this.userId, ChatClient.this.userName, ChatClient.this.roleCode)));
                    } else {
                        send(GSON.toJson(ChatMessage.join(ChatClient.this.userId, ChatClient.this.userName)));
                    }
                    flushPendingMessages(this);
                    notifyConnection(true);
                }
                @Override
                public void onMessage(String message) {
                    try {
                        ChatMessage chatMessage = GSON.fromJson(message, ChatMessage.class);
                        if (chatMessage != null) notifyMessage(chatMessage);
                    } catch (Exception e) {
                        AppLogger.getInstance().error(ErrorCode.WS_MESSAGE_FAIL,
                                "ChatClient.onMessage - khong parse duoc payload chat", e);
                    }
                }
                @Override
                public void onClose(int code, String reason, boolean remote) {
                    if (ChatClient.this.client == this) {
                        ChatClient.this.client = null;
                        connectAttemptInFlight = false;
                        notifyConnection(false);
                    }
                }
                @Override
                public void onError(Exception ex) {
                    connectAttemptInFlight = false;
                    AppLogger.getInstance().error(ErrorCode.WS_CONNECTION_FAIL,
                            "ChatClient - loi ket noi chat (se tu dong thu lai)", ex);
                }
            };
            client.connect();
        } catch (Exception e) {
            connectAttemptInFlight = false;
            AppLogger.getInstance().error(ErrorCode.WS_CONNECTION_FAIL,
                    "ChatClient.attemptConnect - khong the khoi tao ket noi chat", e);
        }
    }

    private synchronized void ensureReconnectSchedulerRunning() {
        if (reconnectScheduler != null && !reconnectScheduler.isShutdown()) return;
        ThreadFactory daemonFactory = r -> {
            Thread t = new Thread(r, "chat-client-reconnect");
            t.setDaemon(true);
            return t;
        };
        reconnectScheduler = Executors.newSingleThreadScheduledExecutor(daemonFactory);
        reconnectScheduler.scheduleWithFixedDelay(() -> {
            if (!wantConnected) return;
            if (client != null && client.isOpen()) return;
            attemptConnect();
        }, RECONNECT_INTERVAL_SECONDS, RECONNECT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    // ================================================================
    // GỬI TIN NHẮN VĂN BẢN
    // ================================================================

    public boolean sendMessage(String text) {
        return sendOrQueue(ChatMessage.chat(userId, userName, text), false);
    }

    public boolean sendStaffMessage(int toUserId, String text) {
        return sendOrQueue(ChatMessage.staffChat(userId, userName, toUserId, text), true);
    }

    public boolean sendCustomerMessage(int customerUserId, String text) {
        return sendOrQueue(ChatMessage.chatFromAdmin(customerUserId, userName, text), true);
    }

    // ================================================================
    // GỬI ẢNH (Base64)
    // ================================================================

    public boolean sendImage(String text, String imageBase64, String imageMime) {
        if (imageBase64 == null || imageBase64.isBlank()) return false;
        return sendOrQueue(ChatMessage.image(userId, userName, text, imageBase64, imageMime), false);
    }

    public boolean sendStaffImage(int toUserId, String text, String imageBase64, String imageMime) {
        if (imageBase64 == null || imageBase64.isBlank()) return false;
        return sendOrQueue(ChatMessage.staffImage(userId, userName, toUserId, text, imageBase64, imageMime), true);
    }

    public boolean sendCustomerImage(int customerUserId, String text, String imageBase64, String imageMime) {
        if (imageBase64 == null || imageBase64.isBlank()) return false;
        return sendOrQueue(ChatMessage.imageFromAdmin(customerUserId, userName, text, imageBase64, imageMime), true);
    }

    // ================================================================
    // GỬI FILE DẠNG BASE64
    // ================================================================

    public boolean sendFile(String text, String fileBase64, String fileName, String fileMime) {
        if (fileBase64 == null || fileBase64.isBlank() || fileName == null || fileName.isBlank()) return false;
        return sendOrQueue(ChatMessage.file(userId, userName, text, fileBase64, fileName, fileMime), false);
    }

    public boolean sendStaffFile(int toUserId, String text, String fileBase64, String fileName, String fileMime) {
        if (fileBase64 == null || fileBase64.isBlank() || fileName == null || fileName.isBlank()) return false;
        return sendOrQueue(ChatMessage.staffFile(userId, userName, toUserId, text, fileBase64, fileName, fileMime), true);
    }

    public boolean sendCustomerFile(int customerUserId, String text, String fileBase64, String fileName, String fileMime) {
        if (fileBase64 == null || fileBase64.isBlank() || fileName == null || fileName.isBlank()) return false;
        return sendOrQueue(ChatMessage.fileFromAdmin(customerUserId, userName, text, fileBase64, fileName, fileMime), true);
    }

    // ================================================================
    // GỬI FILE DẠNG URL CLOUDINARY (MỚI)
    // ================================================================

    public boolean sendFileUrl(String text, String fileUrl, String fileName, long fileSize) {
        if (fileUrl == null || fileUrl.isBlank() || fileName == null || fileName.isBlank()) return false;
        return sendOrQueue(ChatMessage.fileUrl(userId, userName, text, fileUrl, fileName, fileSize), false);
    }

    public boolean sendStaffFileUrl(int toUserId, String text, String fileUrl, String fileName, long fileSize) {
        if (fileUrl == null || fileUrl.isBlank() || fileName == null || fileName.isBlank()) return false;
        return sendOrQueue(ChatMessage.staffFileUrl(userId, userName, toUserId, text, fileUrl, fileName, fileSize), true);
    }

    public boolean sendCustomerFileUrl(int customerUserId, String text, String fileUrl, String fileName, long fileSize) {
        if (fileUrl == null || fileUrl.isBlank() || fileName == null || fileName.isBlank()) return false;
        return sendOrQueue(ChatMessage.fileUrlFromAdmin(customerUserId, userName, text, fileUrl, fileName, fileSize), true);
    }

    // ================================================================
    // GỬI TIN NHẮN THOẠI
    // ================================================================

    public boolean sendVoice(String transcript, String voiceBase64, String voiceMime, int durationMs) {
        if (voiceBase64 == null || voiceBase64.isBlank()) return false;
        return sendOrQueue(ChatMessage.voice(
                userId, userName, transcript, voiceBase64, voiceMime, durationMs), false);
    }

    public boolean sendStaffVoice(int toUserId, String transcript, String voiceBase64, String voiceMime, int durationMs) {
        if (voiceBase64 == null || voiceBase64.isBlank()) return false;
        return sendOrQueue(ChatMessage.staffVoice(
                userId, userName, toUserId, transcript, voiceBase64, voiceMime, durationMs), true);
    }

    public boolean sendCustomerVoice(int customerUserId, String transcript, String voiceBase64,
                                     String voiceMime, int durationMs) {
        if (voiceBase64 == null || voiceBase64.isBlank()) return false;
        return sendOrQueue(ChatMessage.voiceFromAdmin(customerUserId, userName, transcript,
                voiceBase64, voiceMime, durationMs), true);
    }

    // ================================================================
    // KẾT NỐI & GỬI NỘI BỘ
    // ================================================================

    public boolean isConnected() { return client != null && client.isOpen(); }

    public synchronized void disconnect() {
        wantConnected = false;
        pendingMessages.clear();
        if (reconnectScheduler != null) {
            reconnectScheduler.shutdown();
            reconnectScheduler = null;
        }
        if (client == null) return;
        try {
            if (client.isOpen()) {
                if (staffMode) client.send(GSON.toJson(ChatMessage.staffLeave(userId, userName)));
                else client.send(GSON.toJson(ChatMessage.leave(userId, userName)));
            }
            client.close();
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.WS_CONNECTION_FAIL,
                    "ChatClient.disconnect - loi khi dong ket noi chat", e);
        }
        client = null;
    }

    private boolean sendOrQueue(ChatMessage message, boolean requiresStaffMode) {
        if (message == null || (requiresStaffMode && !staffMode)) return false;
        String json = GSON.toJson(message);
        WebSocketClient current = client;
        if (current != null && current.isOpen()) {
            try {
                current.send(json);
                return true;
            } catch (Exception e) {
                AppLogger.getInstance().error(ErrorCode.WS_MESSAGE_FAIL,
                        "ChatClient.sendOrQueue - khong gui duoc tin qua socket dang mo", e);
            }
        }
        synchronized (this) {
            if (!wantConnected || pendingMessages.size() >= MAX_PENDING_MESSAGES) return false;
            pendingMessages.offer(json);
            attemptConnect();
            return true;
        }
    }

    private void flushPendingMessages(WebSocketClient connection) {
        String json;
        while ((json = pendingMessages.poll()) != null) {
            try {
                connection.send(json);
            } catch (Exception e) {
                pendingMessages.offer(json);
                AppLogger.getInstance().error(ErrorCode.WS_MESSAGE_FAIL,
                        "ChatClient.flushPendingMessages - se thu gui lai sau khi ket noi lai", e);
                break;
            }
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }

    private void notifyMessage(ChatMessage message) {
        for (Consumer<ChatMessage> listener : messageListeners) {
            javax.swing.SwingUtilities.invokeLater(() -> listener.accept(message));
        }
    }

    private void notifyConnection(boolean connected) {
        for (Consumer<Boolean> listener : connectionListeners) {
            javax.swing.SwingUtilities.invokeLater(() -> listener.accept(connected));
        }
    }
}