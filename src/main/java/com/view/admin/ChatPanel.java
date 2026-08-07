package com.view.admin;

import com.dao.UserDAO;
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
            } catch (NumberFormatException ignored) {}
        } else if (notificationId.startsWith("chat-s-")) {
            try {
                clearStaffUnread(Integer.parseInt(notificationId.substring("chat-s-".length())));
            } catch (NumberFormatException ignored) {}
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
            String name = onlineCustomers.getOrDefault(uid, "Khách hàng #" + uid);
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

    private void selectCustomer(int userId) {
        selectedCustomerId = userId;
        selectedStaffId = null;
        customerUnread.put(userId, false);
        customerList.repaint();
        notifyUnreadCountChanged();
        String name = onlineCustomers.getOrDefault(userId, "Khách hàng #" + userId);
        conversationTitle.setText(name + " (#" + userId + ")");
        boolean online = onlineCustomers.containsKey(userId);
        conversationStatus.setText(online ? "Đang trực tuyến" : "Đã ngắt kết nối");
        conversationStatus.setForeground(online ? AppColor.GREEN : AppColor.TEXT_MUTED_ALT);
        setInputEnabled(online);
        renderConversation(customerConversations.getOrDefault(userId, new ArrayList<>()), true);
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
        renderConversation(staffConversations.getOrDefault(userId, new ArrayList<>()), false);
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
        boolean sent = ChatServer.getInstance().sendToCustomer(selectedCustomerId, myName, text);
        ChatMessage record = ChatMessage.chatFromAdmin(selectedCustomerId, myName, text);
        customerConversations.computeIfAbsent(selectedCustomerId, k -> new ArrayList<>()).add(record);
        addBubble(text, null, true, TIME_FORMAT.format(new Date(record.timestamp)));
        inputField.setText("");
        if (!sent) {
            JOptionPane.showMessageDialog(this,
                    "Khách hàng đã ngắt kết nối, không gửi được tin nhắn này.",
                    "Không gửi được", JOptionPane.WARNING_MESSAGE);
        }
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
                            targetId, myName, caption.isEmpty() ? null : caption, encoded.base64, encoded.mime);
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
                if (!sent) {
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
        customerListModel.clear();
        for (Integer userId : onlineCustomers.keySet()) customerListModel.addElement(userId);
        if (prev != null && onlineCustomers.containsKey(prev)) customerList.setSelectedValue(prev, false);
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
            nameLabel.setText(onlineCustomers.getOrDefault(userId, "Khách #" + userId));
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