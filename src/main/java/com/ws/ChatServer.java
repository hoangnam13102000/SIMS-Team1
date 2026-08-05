package com.ws;

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

    private ChatServer(int port) { this.port = port; }

    public static synchronized ChatServer getInstance() {
        if (instance == null) instance = new ChatServer(loadPort());
        return instance;
    }

    private static int loadPort() {
        Properties props = new Properties();
        try (InputStream in = ChatServer.class.getClassLoader().getResourceAsStream("ws.properties")) {
            if (in != null) props.load(in);
        } catch (IOException ignored) {}
        return Integer.parseInt(props.getProperty("WS_CHAT_PORT", "8890"));
    }

    public synchronized void start() {
        if (server != null) return;
        server = new InternalServer(port);
        server.setReuseAddr(true);
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(this::stopServer, "ChatServer-shutdown"));
    }

    public synchronized void stopServer() {
        if (server == null) return;
        try { server.stop(500); } catch (Exception ignored) {}
        server = null;
        connectionsByUserId.clear();
        sessionByConnection.clear();
        staffConnections.clear();
        staffSessionByConnection.clear();
    }

    public void addListener(Consumer<ChatMessage> listener) { listeners.add(listener); }
    public void removeListener(Consumer<ChatMessage> listener) { listeners.remove(listener); }

    public boolean sendToCustomer(int userId, String adminName, String text) {
        WebSocket conn = connectionsByUserId.get(userId);
        if (conn == null || !conn.isOpen()) return false;
        conn.send(GSON.toJson(ChatMessage.chatFromAdmin(userId, adminName, text)));
        return true;
    }

    public boolean sendImageToCustomer(int userId, String adminName, String text,
                                       String imageBase64, String imageMime) {
        WebSocket conn = connectionsByUserId.get(userId);
        if (conn == null || !conn.isOpen()) return false;
        if (imageBase64 == null || imageBase64.isBlank()) return false;
        conn.send(GSON.toJson(ChatMessage.imageFromAdmin(userId, adminName, text, imageBase64, imageMime)));
        return true;
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
        String json = GSON.toJson(message);
        for (Map.Entry<Integer, WebSocket> e : staffConnections.entrySet()) {
            if (excludeUserId != null && excludeUserId.equals(e.getKey())) continue;
            WebSocket conn = e.getValue();
            if (conn != null && conn.isOpen()) {
                try { conn.send(json); } catch (Exception ignored) {}
            }
        }
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
                    dispatch(chatMessage);
                    return;
                }

                if (chatMessage.isStaffJoin()) {
                    WebSocket old = staffConnections.put(chatMessage.userId, conn);
                    if (old != null && old != conn) {
                        staffSessionByConnection.remove(old);
                        try { old.close(); } catch (Exception ignored) {}
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
                    return;
                }

                if (chatMessage.isLeave()) {
                    connectionsByUserId.remove(chatMessage.userId, conn);
                    sessionByConnection.remove(conn);
                }
                dispatch(chatMessage);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onError(WebSocket conn, Exception ex) {
            if (NetworkErrorNotifier.isBindFailure(ex)) {
                // Cong da bi process khac giu. Khong popup: dung che do client.
                if (bindFailureNotified.compareAndSet(false, true)) {
                    System.out.println("[ChatServer] Cong " + port
                            + " da bi chiem - bo qua bind, dung che do client"
                            + " (ket noi toi WS_HOST trong ws.properties).");
                    synchronized (ChatServer.this) {
                        if (server == this) {
                            try { stop(0); } catch (Exception ignored) {}
                            server = null;
                        }
                    }
                }
                return;
            }
            ex.printStackTrace();
        }

        @Override public void onStart() {}
    }
}