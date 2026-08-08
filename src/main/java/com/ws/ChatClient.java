package com.ws;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Properties;
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
    private static ChatClient instance;

    private final CopyOnWriteArrayList<Consumer<ChatMessage>> messageListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<Boolean>> connectionListeners = new CopyOnWriteArrayList<>();

    private WebSocketClient client;
    private int userId;
    private String userName;
    private String roleCode;
    private boolean staffMode;

    private volatile boolean wantConnected = false;
    private volatile boolean connectAttemptInFlight = false;
    private ScheduledExecutorService reconnectScheduler;

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
            // Khong doc duoc ws.properties -> dung fallback WS_HOST/WS_CHAT_PORT ben duoi,
            // nhung van ghi log de biet file config bi thieu/sai quyen thay vi im lang dung sai host.
            AppLogger.getInstance().error(ErrorCode.WS_CONNECTION_FAIL,
                    "ChatClient.attemptConnect - khong doc duoc ws.properties, dung gia tri mac dinh", e);
        }
        String host = props.getProperty("WS_HOST", "localhost");
        int port = Integer.parseInt(props.getProperty("WS_CHAT_PORT", "8890"));

        try {
            connectAttemptInFlight = true;
            client = new WebSocketClient(new URI("ws://" + host + ":" + port)) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    connectAttemptInFlight = false;
                    if (staffMode) {
                        send(GSON.toJson(ChatMessage.staffJoin(
                                ChatClient.this.userId, ChatClient.this.userName, ChatClient.this.roleCode)));
                    } else {
                        send(GSON.toJson(ChatMessage.join(ChatClient.this.userId, ChatClient.this.userName)));
                    }
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
                    connectAttemptInFlight = false;
                    notifyConnection(false);
                }

                @Override
                public void onError(Exception ex) {
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

    public boolean sendMessage(String text) {
        if (client == null || !client.isOpen()) return false;
        client.send(GSON.toJson(ChatMessage.chat(userId, userName, text)));
        return true;
    }

    public boolean sendImage(String text, String imageBase64, String imageMime) {
        if (client == null || !client.isOpen()) return false;
        if (imageBase64 == null || imageBase64.isBlank()) return false;
        client.send(GSON.toJson(ChatMessage.image(userId, userName, text, imageBase64, imageMime)));
        return true;
    }

    public boolean sendStaffMessage(int toUserId, String text) {
        if (client == null || !client.isOpen() || !staffMode) return false;
        client.send(GSON.toJson(ChatMessage.staffChat(userId, userName, toUserId, text)));
        return true;
    }

    public boolean sendStaffImage(int toUserId, String text, String imageBase64, String imageMime) {
        if (client == null || !client.isOpen() || !staffMode) return false;
        if (imageBase64 == null || imageBase64.isBlank()) return false;
        client.send(GSON.toJson(ChatMessage.staffImage(userId, userName, toUserId, text, imageBase64, imageMime)));
        return true;
    }

    public boolean isConnected() { return client != null && client.isOpen(); }

    public synchronized void disconnect() {
        wantConnected = false;
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
            // Dong ket noi luc thoat/dang xuat - khong can chan nguoi dung, nhung ghi log de
            // phan biet voi truong hop mat ket noi bat thuong luc dang chat.
            AppLogger.getInstance().error(ErrorCode.WS_CONNECTION_FAIL,
                    "ChatClient.disconnect - loi khi dong ket noi chat", e);
        }
        client = null;
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