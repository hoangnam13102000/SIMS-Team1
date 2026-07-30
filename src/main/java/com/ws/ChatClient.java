package com.ws;

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

/**
 * Client WebSocket cho khung chat ho tro cua khach hang: giu ket noi lau
 * dai trong suot thoi gian ClientMainFrame dang mo, cho phep nhan tin nhan
 * tra loi tu Admin theo thoi gian thuc.
 *
 * Singleton dung chung cho ca phien dang nhap cua khach hang. Goi
 * connect(userId, userName) khi mo ClientMainFrame, disconnect() khi dong.
 *
 * TU DONG KET NOI LAI: neu lan ket noi dau tien that bai (vd: Admin/ChatServer
 * chua mo), hoac ket noi dang co bi rot, 1 thread nen (daemon) se dinh ky
 * thu ket noi lai moi RECONNECT_INTERVAL_SECONDS giay cho toi khi thanh cong -
 * khong quan trong ben nao mo truoc. Chi dung lai khi disconnect() duoc goi
 * tuong minh (vd: dong ClientMainFrame / dang xuat).
 */
public class ChatClient {

    private static final Gson GSON = new Gson();
    private static final long RECONNECT_INTERVAL_SECONDS = 5;
    private static ChatClient instance;

    private final CopyOnWriteArrayList<Consumer<ChatMessage>> messageListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<Boolean>> connectionListeners = new CopyOnWriteArrayList<>();

    private WebSocketClient client;
    private int userId;
    private String userName;

    /** true tu luc connect() duoc goi toi luc disconnect() - dieu khien vong lap tu ket noi lai. */
    private volatile boolean wantConnected = false;
    /** Tranh 2 lan connect() chong cheo khi 1 lan ket noi truoc van dang "CONNECTING" dang do. */
    private volatile boolean connectAttemptInFlight = false;
    private ScheduledExecutorService reconnectScheduler;

    private ChatClient() {
    }

    public static synchronized ChatClient getInstance() {
        if (instance == null) {
            instance = new ChatClient();
        }
        return instance;
    }

    /** Dang ky nhan tin nhan (tra loi tu Admin) gui ve cho khach hang nay. */
    public void addMessageListener(Consumer<ChatMessage> listener) {
        messageListeners.add(listener);
    }

    public void removeMessageListener(Consumer<ChatMessage> listener) {
        messageListeners.remove(listener);
    }

    /** Dang ky nhan trang thai ket noi (true = da ket noi, false = mat ket noi) de hien thi len UI. */
    public void addConnectionListener(Consumer<Boolean> listener) {
        connectionListeners.add(listener);
    }

    public void removeConnectionListener(Consumer<Boolean> listener) {
        connectionListeners.remove(listener);
    }

    public synchronized void connect(int userId, String userName) {
        this.userId = userId;
        this.userName = userName;
        this.wantConnected = true;
        attemptConnect();
        ensureReconnectSchedulerRunning();
    }

    private synchronized void attemptConnect() {
        if (!wantConnected) return;
        if (client != null && client.isOpen()) return;
        if (connectAttemptInFlight) return;

        Properties props = new Properties();
        try (InputStream in = ChatClient.class.getClassLoader().getResourceAsStream("ws.properties")) {
            if (in != null) props.load(in);
        } catch (IOException ignored) {
            // Dung gia tri mac dinh ben duoi neu khong doc duoc file config.
        }
        String host = props.getProperty("WS_HOST", "localhost");
        int port = Integer.parseInt(props.getProperty("WS_CHAT_PORT", "8890"));

        try {
            connectAttemptInFlight = true;
            client = new WebSocketClient(new URI("ws://" + host + ":" + port)) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    connectAttemptInFlight = false;
                    send(GSON.toJson(ChatMessage.join(ChatClient.this.userId, ChatClient.this.userName)));
                    notifyConnection(true);
                }

                @Override
                public void onMessage(String message) {
                    try {
                        ChatMessage chatMessage = GSON.fromJson(message, ChatMessage.class);
                        if (chatMessage != null) notifyMessage(chatMessage);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    connectAttemptInFlight = false;
                    notifyConnection(false);
                    // Khong tu goi connect() ngay tai day - de reconnectScheduler xu ly.
                }

                @Override
                public void onError(Exception ex) {
                    System.out.println("[ChatClient] Loi ket noi chat: " + ex.getMessage());
                }
            };
            client.connect(); // bat dong bo, khong lam treo UI neu server chua san sang
        } catch (Exception e) {
            connectAttemptInFlight = false;
            System.out.println("[ChatClient] Khong the khoi tao ket noi chat: " + e.getMessage());
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

    /** Gui 1 tin nhan chat len ChatServer. Tra ve false neu hien khong co ket noi. */
    public boolean sendMessage(String text) {
        if (client == null || !client.isOpen()) return false;
        client.send(GSON.toJson(ChatMessage.chat(userId, userName, text)));
        return true;
    }

    public boolean isConnected() {
        return client != null && client.isOpen();
    }

    public synchronized void disconnect() {
        wantConnected = false;
        if (reconnectScheduler != null) {
            reconnectScheduler.shutdown();
            reconnectScheduler = null;
        }
        if (client == null) return;
        try {
            if (client.isOpen()) {
                client.send(GSON.toJson(ChatMessage.leave(userId, userName)));
            }
            client.close();
        } catch (Exception ignored) {
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