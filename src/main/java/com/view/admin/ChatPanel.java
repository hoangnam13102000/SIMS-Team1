package com.view.admin;

import com.dao.UserDAO;
import com.model.chat.ChatConversation;
import com.model.chat.ChatHistoryMessage;
import com.service.ChatHistoryService;
import com.utils.ImageUtil;
import com.model.Role;
import com.model.User;
import com.service.AuthService;
import com.theme.AppColor;
import com.utils.FileUtil;
import com.utils.NotificationSound;
import com.ws.ChatClient;
import com.ws.ChatImageUtil;
import com.ws.ChatMessage;
import com.ws.ChatServer;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Man hinh Chat admin:
 * - Tab "Khách hàng": chat ho tro customer real-time.
 * - Tab "Nội bộ": chat giua cac tai khoan nhan vien.
 */
public class ChatPanel extends JPanel {

    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm");
    private static final int IMAGE_MAX_W = 240;

    private final Map<Integer, String> onlineCustomers = new LinkedHashMap<>();
    /** Tên khách hàng đã biết (kể cả khi khách đang offline), lấy từ lịch sử DB. */
    private final Map<Integer, String> customerDisplayNames = new LinkedHashMap<>();
    /** Tất cả customerId từng có hội thoại (online hoặc đã lưu DB) để hiển thị trong danh sách bên trái. */
    private final Set<Integer> knownCustomerIds = new LinkedHashSet<>();
    /** Tránh gọi DB lại nhiều lần khi bấm chọn lại cùng 1 khách/đồng nghiệp. */
    private final Set<Integer> customerHistoryLoaded = new HashSet<>();
    private final Set<Integer> staffHistoryLoaded = new HashSet<>();
    private final Map<Integer, List<ChatMessage>> customerConversations = new LinkedHashMap<>();
    private final Map<Integer, Boolean> customerUnread = new LinkedHashMap<>();
    private final Map<Integer, String> customerLastPreview = new LinkedHashMap<>();
    private final Map<Integer, Long> customerLastTime = new LinkedHashMap<>();
    private final DefaultListModel<Integer> customerListModel = new DefaultListModel<>();
    private final JList<Integer> customerList = new JList<>(customerListModel);
    private Integer selectedCustomerId;

    private final Map<Integer, User> staffDirectory = new LinkedHashMap<>();
    private final Set<Integer> onlineStaffIds = new HashSet<>();
    private final Map<Integer, List<ChatMessage>> staffConversations = new LinkedHashMap<>();
    private final Map<Integer, Boolean> staffUnread = new LinkedHashMap<>();
    private final Map<Integer, String> staffLastPreview = new LinkedHashMap<>();
    private final Map<Integer, Long> staffLastTime = new LinkedHashMap<>();
    private final DefaultListModel<Integer> staffListModel = new DefaultListModel<>();
    private final JList<Integer> staffList = new JList<>(staffListModel);
    private Integer selectedStaffId;

    private final JPanel messagesContainer;
    private final JScrollPane scrollPane;
    private final JTextField inputField;
    private final JButton sendButton;
    private final JButton imageButton;
    private final JLabel conversationTitle;
    private final JLabel conversationStatus;
    private final JTabbedPane sideTabs;

    private final UserDAO userDAO = new UserDAO();
    private final int myUserId;
    private final String myName;

    private final Consumer<ChatMessage> serverListener = this::onServerEvent;
    private final Consumer<ChatMessage> staffClientListener = this::onStaffClientEvent;
    private Consumer<Integer> onUnreadCountChanged;
    private Consumer<List<com.model.NotificationItem>> onUnreadNotifications;
    private boolean staffTabActive;

    public ChatPanel() {
        User me = AuthService.getInstance().isLoggedIn()
                ? AuthService.getInstance().getCurrentUser() : null;
        myUserId = me != null ? me.getUserId() : -1;
        myName = me != null && me.getFullName() != null ? me.getFullName() : "Nhân viên";

        setLayout(new BorderLayout(16, 0));
        setBackground(AppColor.PAGE_BG);
        setBorder(new EmptyBorder(20, 24, 20, 24));

        sideTabs = new JTabbedPane();
        sideTabs.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        sideTabs.addTab("  Khách hàng  ", buildCustomerListCard());
        sideTabs.addTab("  Nội bộ  ", buildStaffListCard());
        sideTabs.addChangeListener(e -> {
            staffTabActive = sideTabs.getSelectedIndex() == 1;
            if (staffTabActive) {
                if (selectedStaffId != null) selectStaff(selectedStaffId);
                else clearConversation("Chọn một đồng nghiệp để chat nội bộ");
            } else {
                if (selectedCustomerId != null) selectCustomer(selectedCustomerId);
                else clearConversation("Chọn một khách hàng để bắt đầu trả lời");
            }
            notifyUnreadCountChanged();
        });

        JPanel leftWrap = new JPanel(new BorderLayout());
        leftWrap.setPreferredSize(new Dimension(280, 0));
        leftWrap.setOpaque(false);
        leftWrap.add(sideTabs, BorderLayout.CENTER);
        add(leftWrap, BorderLayout.WEST);

        JPanel conversationCard = new JPanel(new BorderLayout());
        conversationCard.setBackground(AppColor.WHITE);
        conversationCard.setBorder(BorderFactory.createLineBorder(AppColor.BORDER, 1, true));

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(AppColor.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AppColor.BORDER),
                new EmptyBorder(16, 20, 16, 20)));
        conversationTitle = new JLabel("Chọn một khách hàng để bắt đầu trả lời");
        conversationTitle.setFont(new Font("Segoe UI", Font.BOLD, 15));
        conversationTitle.setForeground(AppColor.TEXT_PRIMARY);
        conversationStatus = new JLabel(" ");
        conversationStatus.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        conversationStatus.setForeground(AppColor.TEXT_MUTED);
        JPanel titleWrap = new JPanel();
        titleWrap.setOpaque(false);
        titleWrap.setLayout(new BoxLayout(titleWrap, BoxLayout.Y_AXIS));
        titleWrap.add(conversationTitle);
        titleWrap.add(conversationStatus);
        header.add(titleWrap, BorderLayout.WEST);

        messagesContainer = new JPanel();
        messagesContainer.setLayout(new BoxLayout(messagesContainer, BoxLayout.Y_AXIS));
        messagesContainer.setBackground(AppColor.WHITE);
        messagesContainer.setBorder(new EmptyBorder(16, 16, 16, 16));

        scrollPane = new JScrollPane(messagesContainer);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getViewport().setBackground(AppColor.WHITE);

        JPanel inputBar = new JPanel(new BorderLayout(8, 0));
        inputBar.setBackground(AppColor.WHITE);
        inputBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, AppColor.BORDER),
                new EmptyBorder(12, 16, 12, 16)));

        inputField = new JTextField();
        inputField.putClientProperty("JTextField.placeholderText", "Nhập tin nhắn...");
        inputField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        inputField.setEnabled(false);
        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) sendCurrent();
            }
        });

        imageButton = buildIconButton(FontAwesomeSolid.IMAGE, "Gửi ảnh");
        imageButton.setEnabled(false);
        imageButton.addActionListener(e -> pickAndSendImage());

        FontIcon sendIcon = FontIcon.of(FontAwesomeSolid.PAPER_PLANE, 13);
        sendIcon.setIconColor(Color.WHITE);
        sendButton = new JButton("Gửi", sendIcon);
        sendButton.setFocusPainted(false);
        sendButton.setBackground(AppColor.ACCENT);
        sendButton.setForeground(Color.WHITE);
        sendButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        sendButton.setBorder(new EmptyBorder(8, 18, 8, 18));
        sendButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        sendButton.setEnabled(false);
        sendButton.addActionListener(e -> sendCurrent());

        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        rightActions.setOpaque(false);
        rightActions.add(imageButton);
        rightActions.add(sendButton);
        inputBar.add(inputField, BorderLayout.CENTER);
        inputBar.add(rightActions, BorderLayout.EAST);

        conversationCard.add(header, BorderLayout.NORTH);
        conversationCard.add(scrollPane, BorderLayout.CENTER);
        conversationCard.add(inputBar, BorderLayout.SOUTH);
        add(conversationCard, BorderLayout.CENTER);

        loadStaffDirectory();
        loadKnownCustomerThreads();
        refreshCustomerListVisual();
        refreshStaffListVisual();

        ChatServer.getInstance().addListener(serverListener);
        ChatClient.getInstance().addMessageListener(staffClientListener);
    }

    public void setOnUnreadCountChanged(Consumer<Integer> callback) {
        this.onUnreadCountChanged = callback;
        notifyUnreadCountChanged();
    }
    
    public void setOnUnreadNotifications(Consumer<List<com.model.NotificationItem>> callback) {
        this.onUnreadNotifications = callback;
        notifyUnreadCountChanged();
    }

    /** id: "chat-c-{userId}" (khách) hoặc "chat-s-{userId}" (nội bộ). */
    public void markNotificationRead(String notificationId) {
        if (notificationId == null) return;
        if (notificationId.startsWith("chat-c-")) {
            try {
                clearCustomerUnread(Integer.parseInt(notificationId.substring("chat-c-".length())));
            } catch (NumberFormatException e) {
                // notificationId khong dung dinh dang "chat-c-{userId}" nhu ky vong - co the do noi
                // sinh notification khac dang sinh sai id. Ghi log de phat hien loi o noi sinh ra id.
                com.core.log.AppLogger.getInstance().error(com.core.log.ErrorCode.UI_ACTION_FAIL,
                        "ChatPanel.markNotificationRead - id khach hang khong hop le: " + notificationId, e);
            }
        } else if (notificationId.startsWith("chat-s-")) {
            try {
                clearStaffUnread(Integer.parseInt(notificationId.substring("chat-s-".length())));
            } catch (NumberFormatException e) {
                com.core.log.AppLogger.getInstance().error(com.core.log.ErrorCode.UI_ACTION_FAIL,
                        "ChatPanel.markNotificationRead - id nhan vien khong hop le: " + notificationId, e);
            }
        }
    }

    public void clearCustomerUnread(int userId) {
        customerUnread.put(userId, false);
        customerList.repaint();
        notifyUnreadCountChanged();
    }

    public void clearStaffUnread(int userId) {
        staffUnread.put(userId, false);
        staffList.repaint();
        notifyUnreadCountChanged();
    }

    public void clearAllUnread() {
        for (Integer id : new ArrayList<>(customerUnread.keySet())) {
            customerUnread.put(id, false);
        }
        for (Integer id : new ArrayList<>(staffUnread.keySet())) {
            staffUnread.put(id, false);
        }
        customerList.repaint();
        staffList.repaint();
        notifyUnreadCountChanged();
    }

    public List<com.model.NotificationItem> getUnreadNotifications() {
        List<com.model.NotificationItem> items = new ArrayList<>();
        for (Map.Entry<Integer, Boolean> e : customerUnread.entrySet()) {
            if (!Boolean.TRUE.equals(e.getValue())) continue;
            int uid = e.getKey();
            String name = customerDisplayName(uid);
            String preview = customerLastPreview.getOrDefault(uid, "Tin nhắn mới");
            long ts = customerLastTime.getOrDefault(uid, System.currentTimeMillis());
            items.add(new com.model.NotificationItem(
                    "chat-c-" + uid,
                    com.model.NotificationItem.Type.MESSAGE,
                    "Tin nhắn từ " + name,
                    preview,
                    java.time.LocalDateTime.ofInstant(
                            java.time.Instant.ofEpochMilli(ts), java.time.ZoneId.systemDefault()),
                    uid));
        }
        for (Map.Entry<Integer, Boolean> e : staffUnread.entrySet()) {
            if (!Boolean.TRUE.equals(e.getValue())) continue;
            int uid = e.getKey();
            User u = staffDirectory.get(uid);
            String name = u != null ? displayName(u) : ("Nhân viên #" + uid);
            String preview = staffLastPreview.getOrDefault(uid, "Tin nhắn nội bộ");
            long ts = staffLastTime.getOrDefault(uid, System.currentTimeMillis());
            items.add(new com.model.NotificationItem(
                    "chat-s-" + uid,
                    com.model.NotificationItem.Type.MESSAGE,
                    "Nội bộ: " + name,
                    preview,
                    java.time.LocalDateTime.ofInstant(
                            java.time.Instant.ofEpochMilli(ts), java.time.ZoneId.systemDefault()),
                    uid));
        }
        return items;
    }

    private JPanel buildCustomerListCard() {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(AppColor.WHITE);
        JLabel title = new JLabel("Khách đang chat");
        title.setFont(new Font("Segoe UI", Font.BOLD, 13));
        title.setForeground(AppColor.TEXT_PRIMARY);
        title.setBorder(new EmptyBorder(12, 14, 8, 14));
        customerList.setCellRenderer(new CustomerCellRenderer());
        customerList.setBackground(AppColor.WHITE);
        customerList.setBorder(new EmptyBorder(0, 6, 6, 6));
        customerList.setFixedCellHeight(52);
        customerList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Integer userId = customerList.getSelectedValue();
                if (userId != null) selectCustomer(userId);
            }
        });
        JScrollPane listScroll = new JScrollPane(customerList);
        listScroll.setBorder(null);
        card.add(title, BorderLayout.NORTH);
        card.add(listScroll, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildStaffListCard() {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(AppColor.WHITE);
        JLabel title = new JLabel("Đồng nghiệp");
        title.setFont(new Font("Segoe UI", Font.BOLD, 13));
        title.setForeground(AppColor.TEXT_PRIMARY);
        title.setBorder(new EmptyBorder(12, 14, 8, 14));
        staffList.setCellRenderer(new StaffCellRenderer());
        staffList.setBackground(AppColor.WHITE);
        staffList.setBorder(new EmptyBorder(0, 6, 6, 6));
        staffList.setFixedCellHeight(56);
        staffList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Integer userId = staffList.getSelectedValue();
                if (userId != null) selectStaff(userId);
            }
        });
        JScrollPane listScroll = new JScrollPane(staffList);
        listScroll.setBorder(null);
        card.add(title, BorderLayout.NORTH);
        card.add(listScroll, BorderLayout.CENTER);
        return card;
    }

    private void loadStaffDirectory() {
        staffDirectory.clear();
        for (User u : userDAO.findActiveStaff()) {
            if (u.getUserId() == myUserId) continue;
            staffDirectory.put(u.getUserId(), u);
        }
        for (Integer id : ChatServer.getInstance().onlineStaff().keySet()) {
            if (id != myUserId) onlineStaffIds.add(id);
        }
    }

    /**
     * Nạp danh sách khách hàng đã từng có hội thoại hỗ trợ (kể cả khách hiện không online),
     * để nhân viên vẫn thấy và chọn được họ trong danh sách bên trái.
     */
    private void loadKnownCustomerThreads() {
        for (ChatConversation c : ChatHistoryService.getInstance().listRecentCustomerThreads(100)) {
            Integer customerId = c.getCustomerUserId();
            if (customerId != null) knownCustomerIds.add(customerId);
        }
    }

    private void selectCustomer(int userId) {
        selectedCustomerId = userId;
        selectedStaffId = null;
        customerUnread.put(userId, false);
        customerList.repaint();
        notifyUnreadCountChanged();
        String name = customerDisplayName(userId);
        conversationTitle.setText(name + " (#" + userId + ")");
        boolean online = onlineCustomers.containsKey(userId);
        conversationStatus.setText(online ? "Đang trực tuyến" : "Đã ngắt kết nối");
        conversationStatus.setForeground(online ? AppColor.GREEN : AppColor.TEXT_MUTED_ALT);
        // Vẫn cho nhập/gửi khi khách offline: tin nhắn sẽ được lưu lại và khách thấy
        // ngay khi họ mở lại chat, thay vì chặn nhân viên nhắn tin lúc khách không hoạt động.
        setInputEnabled(true);
        ensureCustomerHistoryLoaded(userId);
        renderConversation(customerConversations.getOrDefault(userId, new ArrayList<>()), true);
    }

    /** Tên hiển thị của khách: ưu tiên tên online hiện tại, sau đó tên đã biết từ lịch sử, cuối cùng là mã số. */
    private String customerDisplayName(int userId) {
        String online = onlineCustomers.get(userId);
        if (online != null && !online.isBlank()) return online;
        String known = customerDisplayNames.get(userId);
        if (known != null && !known.isBlank()) return known;
        return "Khách hàng #" + userId;
    }

    private void selectStaff(int userId) {
        selectedStaffId = userId;
        selectedCustomerId = null;
        staffUnread.put(userId, false);
        staffList.repaint();
        notifyUnreadCountChanged();
        User u = staffDirectory.get(userId);
        String name = u != null ? displayName(u) : ("Nhân viên #" + userId);
        String role = u != null && u.getRole() != null ? roleLabel(u.getRole()) : "";
        conversationTitle.setText(name + (role.isEmpty() ? "" : " · " + role));
        boolean online = onlineStaffIds.contains(userId);
        conversationStatus.setText(online ? "Đang trực tuyến" : "Ngoại tuyến");
        conversationStatus.setForeground(online ? AppColor.GREEN : AppColor.TEXT_MUTED_ALT);
        setInputEnabled(true);
        ensureStaffHistoryLoaded(userId);
        renderConversation(staffConversations.getOrDefault(userId, new ArrayList<>()), false);
    }

    /**
     * Nạp lịch sử chat khách–NV từ DB (bảng ChatMessages qua ChatHistoryService) vào bộ nhớ,
     * để nhân viên đọc được tin nhắn cũ kể cả khi khách hiện đang không hoạt động (offline).
     * Chỉ nạp 1 lần cho mỗi khách trong phiên làm việc này.
     */
    private void ensureCustomerHistoryLoaded(int userId) {
        if (!customerHistoryLoaded.add(userId)) return;
        List<ChatHistoryMessage> history = ChatHistoryService.getInstance().loadCustomerHistory(userId, 200);
        if (history.isEmpty()) return;

        knownCustomerIds.add(userId);
        List<ChatMessage> existing = customerConversations.computeIfAbsent(userId, k -> new ArrayList<>());
        long earliestExisting = existing.isEmpty() ? Long.MAX_VALUE : existing.get(0).timestamp;

        List<ChatMessage> older = new ArrayList<>();
        String lastCustomerName = null;
        for (ChatHistoryMessage h : history) {
            ChatMessage cm = toCustomerChatMessage(h, userId);
            if (!h.isFromStaff() && h.getSenderName() != null && !h.getSenderName().isBlank()) {
                lastCustomerName = h.getSenderName();
            }
            if (cm.timestamp < earliestExisting) older.add(cm);
        }
        existing.addAll(0, older);
        if (lastCustomerName != null && !onlineCustomers.containsKey(userId)) {
            customerDisplayNames.put(userId, lastCustomerName);
        }
    }

    /**
     * Nạp lịch sử chat nội bộ (DM giữa 2 nhân viên) từ DB, để đọc được tin nhắn cũ
     * kể cả khi đồng nghiệp hiện đang ngoại tuyến.
     */
    private void ensureStaffHistoryLoaded(int userId) {
        if (!staffHistoryLoaded.add(userId)) return;
        List<ChatHistoryMessage> history = ChatHistoryService.getInstance().loadStaffDmHistory(myUserId, userId, 200);
        if (history.isEmpty()) return;

        List<ChatMessage> existing = staffConversations.computeIfAbsent(userId, k -> new ArrayList<>());
        long earliestExisting = existing.isEmpty() ? Long.MAX_VALUE : existing.get(0).timestamp;

        List<ChatMessage> older = new ArrayList<>();
        for (ChatHistoryMessage h : history) {
            ChatMessage cm = toStaffChatMessage(h, userId);
            if (cm.timestamp < earliestExisting) older.add(cm);
        }
        existing.addAll(0, older);
    }

    private ChatMessage toCustomerChatMessage(ChatHistoryMessage h, int customerUserId) {
        ChatMessage cm = new ChatMessage();
        cm.type = "CHAT";
        cm.userId = customerUserId;
        cm.userName = h.getSenderName();
        cm.text = h.getBodyText();
        cm.fromAdmin = h.isFromStaff();
        cm.timestamp = toEpochMillis(h.getCreatedAt());
        attachHistoryImage(cm, h);
        return cm;
    }

    private ChatMessage toStaffChatMessage(ChatHistoryMessage h, int peerUserId) {
        ChatMessage cm = new ChatMessage();
        cm.type = "STAFF_CHAT";
        cm.staff = true;
        cm.userId = h.getSenderUserId() > 0 ? h.getSenderUserId() : peerUserId;
        cm.toUserId = cm.userId == myUserId ? peerUserId : myUserId;
        cm.userName = h.getSenderName();
        cm.text = h.getBodyText();
        cm.timestamp = toEpochMillis(h.getCreatedAt());
        attachHistoryImage(cm, h);
        return cm;
    }

    private static long toEpochMillis(java.time.LocalDateTime dt) {
        if (dt == null) return System.currentTimeMillis();
        return dt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /** Đọc ảnh đã lưu trên đĩa (ImagePath) và mã hoá lại base64 chỉ để hiển thị lịch sử. */
    private void attachHistoryImage(ChatMessage cm, ChatHistoryMessage h) {
        if (!h.hasImage()) return;
        try {
            File imgFile = new File(h.getImagePath());
            if (!imgFile.isFile()) return;
            ChatImageUtil.EncodedImage encoded = ChatImageUtil.encodeForChat(imgFile);
            if (encoded != null) {
                cm.imageBase64 = encoded.base64;
                cm.imageMime = encoded.mime;
            }
        } catch (Exception ignored) {
            // Không có ảnh cũng không sao — vẫn hiển thị được phần text của tin nhắn.
        }
    }

    private void clearConversation(String title) {
        conversationTitle.setText(title);
        conversationStatus.setText(" ");
        setInputEnabled(false);
        messagesContainer.removeAll();
        messagesContainer.revalidate();
        messagesContainer.repaint();
    }

    private void setInputEnabled(boolean enabled) {
        inputField.setEnabled(enabled);
        sendButton.setEnabled(enabled);
        imageButton.setEnabled(enabled);
    }

    private void sendCurrent() {
        if (staffTabActive) sendStaffReply();
        else sendCustomerReply();
    }

    private void sendCustomerReply() {
        if (selectedCustomerId == null) return;
        String text = inputField.getText() == null ? "" : inputField.getText().trim();
        if (text.isEmpty()) return;
        // Không cần quan tâm khách có đang online hay không: sendToCustomer() luôn lưu lịch sử,
        // khách offline vẫn sẽ thấy tin nhắn này khi họ mở lại chat, nên không cần popup báo.
        ChatServer.getInstance().sendToCustomer(selectedCustomerId, myName, text, myUserId);
        ChatMessage record = ChatMessage.chatFromAdmin(selectedCustomerId, myName, text);
        customerConversations.computeIfAbsent(selectedCustomerId, k -> new ArrayList<>()).add(record);
        addBubble(text, null, true, TIME_FORMAT.format(new Date(record.timestamp)));
        inputField.setText("");
    }

    private void sendStaffReply() {
        if (selectedStaffId == null) return;
        String text = inputField.getText() == null ? "" : inputField.getText().trim();
        if (text.isEmpty()) return;
        boolean sent = ChatClient.getInstance().sendStaffMessage(selectedStaffId, text);
        ChatMessage record = ChatMessage.staffChat(myUserId, myName, selectedStaffId, text);
        staffConversations.computeIfAbsent(selectedStaffId, k -> new ArrayList<>()).add(record);
        addBubble(text, null, true, TIME_FORMAT.format(new Date(record.timestamp)));
        inputField.setText("");
        if (!sent) {
            JOptionPane.showMessageDialog(this,
                    "Chưa kết nối chat nội bộ. Kiểm tra ChatServer đang chạy và WS_HOST/WS_CHAT_PORT.",
                    "Không gửi được", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void pickAndSendImage() {
        File file = FileUtil.chooseImageFile(this);
        if (file == null) return;
        if (!ChatImageUtil.isSupportedImage(file)) {
            JOptionPane.showMessageDialog(this, "Định dạng ảnh không được hỗ trợ.",
                    "Không hỗ trợ", JOptionPane.WARNING_MESSAGE);
            return;
        }
        final boolean forStaff = staffTabActive;
        final Integer targetId = forStaff ? selectedStaffId : selectedCustomerId;
        if (targetId == null) return;
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        imageButton.setEnabled(false);
        sendButton.setEnabled(false);
        new SwingWorker<ChatImageUtil.EncodedImage, Void>() {
            @Override
            protected ChatImageUtil.EncodedImage doInBackground() {
                return ChatImageUtil.encodeForChat(file);
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                imageButton.setEnabled(true);
                sendButton.setEnabled(true);
                ChatImageUtil.EncodedImage encoded;
                try {
                    encoded = get();
                } catch (Exception ex) {
                    encoded = null;
                }
                if (encoded == null) {
                    JOptionPane.showMessageDialog(ChatPanel.this, "Không đọc được ảnh.",
                            "Lỗi ảnh", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                String caption = inputField.getText() == null ? "" : inputField.getText().trim();
                boolean sent;
                ChatMessage record;
                if (forStaff) {
                    sent = ChatClient.getInstance().sendStaffImage(
                            targetId, caption.isEmpty() ? null : caption, encoded.base64, encoded.mime);
                    record = ChatMessage.staffImage(myUserId, myName, targetId,
                            caption.isEmpty() ? null : caption, encoded.base64, encoded.mime);
                    staffConversations.computeIfAbsent(targetId, k -> new ArrayList<>()).add(record);
                } else {
                    sent = ChatServer.getInstance().sendImageToCustomer(
                            targetId, myName, caption.isEmpty() ? null : caption, encoded.base64, encoded.mime, myUserId);
                    record = ChatMessage.imageFromAdmin(targetId, myName,
                            caption.isEmpty() ? null : caption, encoded.base64, encoded.mime);
                    customerConversations.computeIfAbsent(targetId, k -> new ArrayList<>()).add(record);
                }
                if ((forStaff && selectedStaffId != null && selectedStaffId.equals(targetId))
                        || (!forStaff && selectedCustomerId != null && selectedCustomerId.equals(targetId))) {
                    BufferedImage preview = ChatImageUtil.decodeBase64(encoded.base64);
                    addBubble(caption.isEmpty() ? null : caption, preview, true,
                            TIME_FORMAT.format(new Date(record.timestamp)));
                    inputField.setText("");
                }
                // Chỉ báo lỗi khi gửi nội bộ NV-NV thất bại thật sự (không có nơi lưu tạm).
                // Với khách hàng, sendImageToCustomer() đã lưu lịch sử nên không cần popup dù khách offline.
                if (!sent && forStaff) {
                    JOptionPane.showMessageDialog(ChatPanel.this,
                            "Không gửi được ảnh (đối phương offline hoặc mất kết nối).",
                            "Không gửi được", JOptionPane.WARNING_MESSAGE);
                }
            }
        }.execute();
    }

    private void onServerEvent(ChatMessage message) {
        if (message.isJoin()) {
            onlineCustomers.put(message.userId, message.userName);
            knownCustomerIds.add(message.userId);
            customerConversations.computeIfAbsent(message.userId, k -> new ArrayList<>());
            refreshCustomerListVisual();
            if (!staffTabActive && selectedCustomerId != null && selectedCustomerId == message.userId)
                selectCustomer(message.userId);
        } else if (message.isLeave() && !message.staff) {
            onlineCustomers.remove(message.userId);
            refreshCustomerListVisual();
            if (!staffTabActive && selectedCustomerId != null && selectedCustomerId == message.userId)
                selectCustomer(message.userId);
        } else if (message.isChat() && !message.fromAdmin) {
            customerConversations.computeIfAbsent(message.userId, k -> new ArrayList<>()).add(message);
            onlineCustomers.putIfAbsent(message.userId,
                    message.userName != null ? message.userName : ("Khách #" + message.userId));
            knownCustomerIds.add(message.userId);
            String preview = previewOf(message);
            customerLastPreview.put(message.userId, preview);
            customerLastTime.put(message.userId,
                    message.timestamp > 0 ? message.timestamp : System.currentTimeMillis());
            NotificationSound.playMessageSound();
            boolean viewing = !staffTabActive && isShowing()
                    && selectedCustomerId != null && selectedCustomerId == message.userId;
            if (viewing) {
                BufferedImage image = message.hasImage() ? ChatImageUtil.decodeBase64(message.imageBase64) : null;
                addBubble(message.text, image, false, TIME_FORMAT.format(new Date(message.timestamp)));
            } else {
                customerUnread.put(message.userId, true);
                customerList.repaint();
                notifyUnreadCountChanged();
            }
        }
        if (message.isStaffJoin() && message.userId != myUserId) {
            onlineStaffIds.add(message.userId);
            ensureStaffInDirectory(message);
            refreshStaffListVisual();
            if (staffTabActive && selectedStaffId != null && selectedStaffId == message.userId)
                selectStaff(message.userId);
        } else if (message.isStaffLeave() && message.userId != myUserId) {
            onlineStaffIds.remove(message.userId);
            refreshStaffListVisual();
            if (staffTabActive && selectedStaffId != null && selectedStaffId == message.userId)
                selectStaff(message.userId);
        }
    }

    private void onStaffClientEvent(ChatMessage message) {
        if (message.isStaffJoin() && message.userId != myUserId) {
            onlineStaffIds.add(message.userId);
            ensureStaffInDirectory(message);
            refreshStaffListVisual();
            if (staffTabActive && selectedStaffId != null && selectedStaffId == message.userId)
                selectStaff(message.userId);
            return;
        }
        if (message.isStaffLeave() && message.userId != myUserId) {
            onlineStaffIds.remove(message.userId);
            refreshStaffListVisual();
            if (staffTabActive && selectedStaffId != null && selectedStaffId == message.userId)
                selectStaff(message.userId);
            return;
        }
        if (!message.isStaffChat()) return;
        if (message.userId == myUserId) return; // ignore echo of own message
        int peerId = message.userId;
        staffConversations.computeIfAbsent(peerId, k -> new ArrayList<>()).add(message);
        ensureStaffInDirectory(message);
        String preview = previewOf(message);
        staffLastPreview.put(peerId, preview);
        staffLastTime.put(peerId, message.timestamp > 0 ? message.timestamp : System.currentTimeMillis());
        NotificationSound.playMessageSound();
        boolean viewing = staffTabActive && isShowing()
                && selectedStaffId != null && selectedStaffId == peerId;
        if (viewing) {
            BufferedImage image = message.hasImage() ? ChatImageUtil.decodeBase64(message.imageBase64) : null;
            addBubble(message.text, image, false, TIME_FORMAT.format(new Date(message.timestamp)));
        } else {
            staffUnread.put(peerId, true);
            staffList.repaint();
            notifyUnreadCountChanged();
        }
        refreshStaffListVisual();
    }

    private void ensureStaffInDirectory(ChatMessage message) {
        if (staffDirectory.containsKey(message.userId)) return;
        User stub = new User();
        stub.setUserId(message.userId);
        stub.setFullName(message.userName);
        stub.setUsername(message.userName);
        if (message.roleCode != null && !message.roleCode.isBlank()) {
            try {
                stub.setRole(Role.valueOf(message.roleCode));
            } catch (Exception ignored) {
            }
        }
        staffDirectory.put(message.userId, stub);
    }

    private void refreshCustomerListVisual() {
        Integer prev = selectedCustomerId;
        List<Integer> ordered = new ArrayList<>();
        // Khách đang online hiển thị trước, sau đó tới khách đã từng chat nhưng hiện offline
        // (trước đây danh sách chỉ lấy từ onlineCustomers nên khách vừa ngắt kết nối sẽ biến mất
        // khỏi danh sách và nhân viên không thể bấm vào để xem lại tin nhắn cũ của họ).
        for (Integer id : onlineCustomers.keySet()) ordered.add(id);
        for (Integer id : knownCustomerIds) {
            if (!onlineCustomers.containsKey(id)) ordered.add(id);
        }
        customerListModel.clear();
        for (Integer id : ordered) customerListModel.addElement(id);
        if (prev != null && ordered.contains(prev)) customerList.setSelectedValue(prev, false);
    }

    private void refreshStaffListVisual() {
        Integer prev = selectedStaffId;
        List<Integer> ordered = new ArrayList<>();
        for (Integer id : staffDirectory.keySet()) if (onlineStaffIds.contains(id)) ordered.add(id);
        for (Integer id : staffDirectory.keySet()) if (!onlineStaffIds.contains(id)) ordered.add(id);
        for (Integer id : onlineStaffIds) {
            if (!staffDirectory.containsKey(id) && id != myUserId) ordered.add(id);
        }
        staffListModel.clear();
        for (Integer id : ordered) staffListModel.addElement(id);
        if (prev != null && ordered.contains(prev)) staffList.setSelectedValue(prev, false);
    }

    private void notifyUnreadCountChanged() {
        long c = customerUnread.values().stream().filter(Boolean::booleanValue).count();
        long s = staffUnread.values().stream().filter(Boolean::booleanValue).count();
        if (onUnreadCountChanged != null) {
            onUnreadCountChanged.accept((int) (c + s));
        }
        if (onUnreadNotifications != null) {
            onUnreadNotifications.accept(getUnreadNotifications());
        }
    }

    private static String previewOf(ChatMessage message) {
        if (message.hasImage()) {
            if (message.text != null && !message.text.isBlank()) return "[Ảnh] " + message.text;
            return "[Ảnh]";
        }
        if (message.text != null && !message.text.isBlank()) {
            String tx = message.text.trim();
            return tx.length() > 80 ? tx.substring(0, 80) + "…" : tx;
        }
        return "Tin nhắn mới";
    }

    private void renderConversation(List<ChatMessage> history, boolean customerMode) {
        messagesContainer.removeAll();
        if (history == null || history.isEmpty()) {
            JLabel hint = new JLabel(customerMode
                    ? "Chưa có tin nhắn nào với khách hàng này."
                    : "Chưa có tin nhắn. Hãy gửi lời chào!");
            hint.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            hint.setForeground(AppColor.TEXT_MUTED);
            hint.setAlignmentX(Component.CENTER_ALIGNMENT);
            messagesContainer.add(hint);
        } else {
            for (ChatMessage message : history) {
                boolean mine = customerMode ? message.fromAdmin : (message.userId == myUserId);
                BufferedImage image = message.hasImage() ? ChatImageUtil.decodeBase64(message.imageBase64) : null;
                addBubbleSilently(message.text, image, mine, TIME_FORMAT.format(new Date(message.timestamp)));
            }
        }
        messagesContainer.revalidate();
        messagesContainer.repaint();
        scrollToBottom();
    }

    private void addBubble(String text, BufferedImage image, boolean isMine, String time) {
        addBubbleSilently(text, image, isMine, time);
        messagesContainer.revalidate();
        messagesContainer.repaint();
        scrollToBottom();
    }

    private void addBubbleSilently(String text, BufferedImage image, boolean isMine, String time) {
        int viewportW = scrollPane.getViewport().getWidth();
        if (viewportW <= 0) viewportW = 480;
        int maxBubbleW = Math.max(200, Math.min(360, viewportW - 48));
        int htmlW = Math.max(140, maxBubbleW - 40);

        JPanel row = new JPanel(new FlowLayout(isMine ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 6));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel bubble = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isMine ? AppColor.ACCENT : AppColor.BG_LIGHTER);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        bubble.setOpaque(false);
        bubble.setBorder(new EmptyBorder(10, 14, 10, 14));
        bubble.setMaximumSize(new Dimension(maxBubbleW, Integer.MAX_VALUE));

        JPanel contentWrap = new JPanel();
        contentWrap.setOpaque(false);
        contentWrap.setLayout(new BoxLayout(contentWrap, BoxLayout.Y_AXIS));

        if (image != null) {
            JLabel imageLabel = buildImageLabel(image, maxBubbleW - 28);
            imageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            contentWrap.add(imageLabel);
            if (text != null && !text.isBlank()) contentWrap.add(Box.createVerticalStrut(6));
        }
        if (text != null && !text.isBlank()) {
            JLabel textLabel = new JLabel("<html><body style='width: " + htmlW + "px'>"
                    + escapeHtml(text) + "</body></html>");
            textLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            textLabel.setForeground(isMine ? Color.WHITE : AppColor.TEXT_PRIMARY);
            textLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            contentWrap.add(textLabel);
        }
        JLabel timeLabel = new JLabel(time);
        timeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        timeLabel.setForeground(isMine ? AppColor.ACCENT_SELECTION_BG : AppColor.TEXT_MUTED);
        timeLabel.setBorder(new EmptyBorder(4, 0, 0, 0));
        timeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentWrap.add(timeLabel);

        bubble.add(contentWrap, BorderLayout.CENTER);
        row.add(bubble);
        Dimension pref = row.getPreferredSize();
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, pref.height));
        messagesContainer.add(row);
    }

    private JLabel buildImageLabel(BufferedImage src, int maxWidth) {
        int w = src.getWidth(), h = src.getHeight();
        int targetW = Math.min(w, Math.min(IMAGE_MAX_W, maxWidth));
        int targetH = Math.max(1, (int) Math.round(h * (targetW / (double) w)));
        Image scaled = src.getScaledInstance(targetW, targetH, Image.SCALE_SMOOTH);
        JLabel label = new JLabel(new ImageIcon(scaled));
        label.setCursor(new Cursor(Cursor.HAND_CURSOR));
        label.setToolTipText("Nhấp để xem ảnh lớn");
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showImagePreview(src);
            }
        });
        return label;
    }

    private void showImagePreview(BufferedImage src) {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Xem ảnh", Dialog.ModalityType.MODELESS);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        double scale = Math.min(1.0, Math.min(900.0 / src.getWidth(), 650.0 / src.getHeight()));
        int dw = Math.max(1, (int) Math.round(src.getWidth() * scale));
        int dh = Math.max(1, (int) Math.round(src.getHeight() * scale));
        JLabel label = new JLabel(new ImageIcon(src.getScaledInstance(dw, dh, Image.SCALE_SMOOTH)));
        label.setBorder(new EmptyBorder(12, 12, 12, 12));
        dialog.add(new JScrollPane(label));
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar vBar = scrollPane.getVerticalScrollBar();
            vBar.setValue(vBar.getMaximum());
        });
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
    }

    private static String displayName(User u) {
        if (u.getFullName() != null && !u.getFullName().isBlank()) return u.getFullName();
        return u.getUsername() != null ? u.getUsername() : ("#" + u.getUserId());
    }

    private static String roleLabel(Role role) {
        if (role == null) return "";
        switch (role) {
            case ADMIN: return "Quản trị";
            case SALES_MANAGER: return "QL bán hàng";
            case INVENTORY_MANAGER: return "QL kho";
            case SALES_STAFF: return "NV bán hàng";
            default: return role.name();
        }
    }

    private JButton buildIconButton(FontAwesomeSolid iconType, String tooltip) {
        FontIcon icon = FontIcon.of(iconType, 16);
        icon.setIconColor(AppColor.TEXT_MUTED);
        JButton btn = new JButton(icon);
        btn.setToolTipText(tooltip);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(36, 36));
        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (btn.isEnabled()) {
                    icon.setIconColor(AppColor.ACCENT);
                    btn.repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                icon.setIconColor(AppColor.TEXT_MUTED);
                btn.repaint();
            }
        });
        return btn;
    }

    private class CustomerCellRenderer extends JPanel implements ListCellRenderer<Integer> {
        private final JLabel dot = new JLabel("●");
        private final JLabel nameLabel = new JLabel();
        private final JLabel unreadDot = new JLabel("●");

        CustomerCellRenderer() {
            setLayout(new BorderLayout(8, 0));
            setBorder(new EmptyBorder(8, 8, 8, 8));
            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            left.setOpaque(false);
            left.add(dot);
            left.add(nameLabel);
            nameLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            unreadDot.setForeground(AppColor.RED_ALT);
            add(left, BorderLayout.WEST);
            add(unreadDot, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Integer> list, Integer userId,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            nameLabel.setText(customerDisplayName(userId));
            nameLabel.setForeground(AppColor.TEXT_PRIMARY);
            dot.setForeground(onlineCustomers.containsKey(userId) ? AppColor.GREEN : AppColor.TEXT_MUTED_ALT);
            unreadDot.setVisible(Boolean.TRUE.equals(customerUnread.get(userId)));
            setBackground(isSelected ? AppColor.ACCENT_SELECTION_BG : AppColor.WHITE);
            setOpaque(true);
            return this;
        }
    }

    private class StaffCellRenderer extends JPanel implements ListCellRenderer<Integer> {
        private final JLabel dot = new JLabel("●");
        private final JLabel nameLabel = new JLabel();
        private final JLabel roleLbl = new JLabel();
        private final JLabel unreadDot = new JLabel("●");

        StaffCellRenderer() {
            setLayout(new BorderLayout(8, 0));
            setBorder(new EmptyBorder(6, 8, 6, 8));
            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            nameLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            roleLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            roleLbl.setForeground(AppColor.TEXT_MUTED);
            text.add(nameLabel);
            text.add(roleLbl);
            JPanel left = new JPanel(new BorderLayout(6, 0));
            left.setOpaque(false);
            left.add(dot, BorderLayout.WEST);
            left.add(text, BorderLayout.CENTER);
            unreadDot.setForeground(AppColor.RED_ALT);
            add(left, BorderLayout.CENTER);
            add(unreadDot, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Integer> list, Integer userId,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            User u = staffDirectory.get(userId);
            nameLabel.setText(u != null ? displayName(u) : ("Nhân viên #" + userId));
            nameLabel.setForeground(AppColor.TEXT_PRIMARY);
            roleLbl.setText(u != null && u.getRole() != null ? roleLabel(u.getRole()) : "");
            dot.setForeground(onlineStaffIds.contains(userId) ? AppColor.GREEN : AppColor.TEXT_MUTED_ALT);
            unreadDot.setVisible(Boolean.TRUE.equals(staffUnread.get(userId)));
            setBackground(isSelected ? AppColor.ACCENT_SELECTION_BG : AppColor.WHITE);
            setOpaque(true);
            return this;
        }
    }
}