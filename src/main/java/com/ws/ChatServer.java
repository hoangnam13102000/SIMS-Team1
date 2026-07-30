package com.ws;

import com.google.gson.Gson;
import com.utils.NetworkErrorNotifier;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * WebSocket server chay chat ho tro khach hang real-time: giu ket noi lau
 * dai (persistent) voi tung khach hang dang mo ClientMainFrame, cho phep 2
 * chieu: khach hang gui cau hoi len, nhan vien (Admin/quan ly) tra loi
 * xuong dung khach hang do.
 *
 * Singleton, khoi dong khi mo AdminMainFrame (start()), dung khi dong app
 * quan tri (stopServer()). Cac panel Swing (view.admin.ChatPanel) dang ky
 * lang nghe qua addListener(); callback luon dua ve Swing EDT nen an toan
 * de cap nhat UI truc tiep.
 */
public class ChatServer {

    private static final Gson GSON = new Gson();
    private static ChatServer instance;

    private final int port;
    private final CopyOnWriteArrayList<Consumer<ChatMessage>> listeners = new CopyOnWriteArrayList<>();
    // Anh xa userId cua khach hang -> ket noi WebSocket hien tai cua ho (moi userId chi 1 ket noi tai 1 thoi diem).
    private final ConcurrentHashMap<Integer, WebSocket> connectionsByUserId = new ConcurrentHashMap<>();
    // Chieu nguoc lai, dung khi mot ket noi bi dong de biet do la userId nao (bao onClose/onLeave).
    private final ConcurrentHashMap<WebSocket, ChatMessage> sessionByConnection = new ConcurrentHashMap<>();
    private InternalServer server;
    private final AtomicBoolean bindFailureNotified = new AtomicBoolean(false);

    private ChatServer(int port) {
        this.port = port;
    }

    public static synchronized ChatServer getInstance() {
        if (instance == null) {
            instance = new ChatServer(loadPort());
        }
        return instance;
    }

    private static int loadPort() {
        Properties props = new Properties();
        try (InputStream in = ChatServer.class.getClassLoader().getResourceAsStream("ws.properties")) {
            if (in != null) props.load(in);
        } catch (IOException ignored) {
            // Dung port mac dinh ben duoi neu khong doc duoc file config.
        }
        return Integer.parseInt(props.getProperty("WS_CHAT_PORT", "8890"));
    }

    /** Bat dau lang nghe. Goi nhieu lan khong sao (bo qua neu da chay roi). */
    public synchronized void start() {
        if (server != null) return;
        server = new InternalServer(port);
        server.setReuseAddr(true);
        server.start();

        // Phong khi JVM bi dung dot ngot (vd: Stop trong IDE) ma khong di qua
        // windowClosed() binh thuong cua AdminMainFrame - dam bao cong van
        // duoc giai phong de lan chay sau khong bi "Address already in use".
        // stopServer() da idempotent (an toan goi nhieu lan/khi server=null).
        Runtime.getRuntime().addShutdownHook(new Thread(this::stopServer, "ChatServer-shutdown"));
    }

    public synchronized void stopServer() {
        if (server == null) return;
        try {
            server.stop(500);
        } catch (Exception ignored) {
            // Bo qua loi khi dong, khong anh huong viec thoat app.
        }
        server = null;
        connectionsByUserId.clear();
        sessionByConnection.clear();
    }

    /** Dang ky nhan tat ca su kien chat (JOIN / CHAT / LEAVE) tu moi khach hang. */
    public void addListener(Consumer<ChatMessage> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<ChatMessage> listener) {
        listeners.remove(listener);
    }

    /** Nhan vien quan tri gui tra loi xuong dung khach hang co userId nay. Tra ve false neu khach da roi mang. */
    public boolean sendToCustomer(int userId, String adminName, String text) {
        WebSocket conn = connectionsByUserId.get(userId);
        if (conn == null || !conn.isOpen()) return false;
        conn.send(GSON.toJson(ChatMessage.chatFromAdmin(userId, adminName, text)));
        return true;
    }

    /** Danh sach userId dang ket noi (con online) tai thoi diem goi. */
    public java.util.Set<Integer> onlineCustomerIds() {
        return new java.util.HashSet<>(connectionsByUserId.keySet());
    }

    private void dispatch(ChatMessage message) {
        for (Consumer<ChatMessage> listener : listeners) {
            javax.swing.SwingUtilities.invokeLater(() -> listener.accept(message));
        }
    }

    private class InternalServer extends WebSocketServer {
        InternalServer(int port) {
            super(new InetSocketAddress(port));
        }

        @Override
        public void onOpen(WebSocket conn, ClientHandshake handshake) {
            // Chua biet la khach hang nao cho toi khi nhan duoc message JOIN dau tien.
        }

        @Override
        public void onClose(WebSocket conn, int code, String reason, boolean remote) {
            ChatMessage session = sessionByConnection.remove(conn);
            if (session != null) {
                connectionsByUserId.remove(session.userId, conn);
                dispatch(ChatMessage.leave(session.userId, session.userName));
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
                }
                dispatch(chatMessage);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onError(WebSocket conn, Exception ex) {
            if (NetworkErrorNotifier.isBindFailure(ex)) {
                NetworkErrorNotifier.notifyBindFailureOnce(
                        bindFailureNotified, "ChatServer (chat hỗ trợ)", port);
                return;
            }
            ex.printStackTrace();
        }

        @Override
        public void onStart() {
            // Server da san sang lang nghe.
        }
    }
}