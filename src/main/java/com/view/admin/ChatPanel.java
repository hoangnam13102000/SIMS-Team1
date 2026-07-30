package com.view.admin;

import com.theme.AppColor;
import com.service.AuthService;
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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.Consumer;

/** Man hinh "Chat ho tro" ben phia quan tri. */
public class ChatPanel extends JPanel {

    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm");

    private final Map<Integer, String> onlineCustomers = new LinkedHashMap<>();
    private final Map<Integer, List<ChatMessage>> conversations = new LinkedHashMap<>();
    private final Map<Integer, Boolean> unread = new LinkedHashMap<>();

    private final DefaultListModel<Integer> customerListModel = new DefaultListModel<>();
    private final JList<Integer> customerList = new JList<>(customerListModel);
    private final JPanel messagesContainer;
    private final JScrollPane scrollPane;
    private final JTextField inputField;
    private final JButton sendButton;
    private final JLabel conversationTitle;
    private final JLabel conversationStatus;

    private Integer selectedUserId;

    private final Consumer<ChatMessage> serverListener = this::onServerEvent;
    private Consumer<Integer> onUnreadCountChanged;

    public ChatPanel() {
        setLayout(new BorderLayout(16, 0));
        setBackground(AppColor.PAGE_BG);
        setBorder(new EmptyBorder(20, 24, 20, 24));

        add(buildCustomerListCard(), BorderLayout.WEST);

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
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getViewport().setBackground(AppColor.WHITE);

        JPanel centerWrap = new JPanel(new BorderLayout());
        centerWrap.setBackground(AppColor.WHITE);
        centerWrap.add(scrollPane, BorderLayout.CENTER);

        JPanel inputBar = new JPanel(new BorderLayout(10, 0));
        inputBar.setBackground(AppColor.WHITE);
        inputBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, AppColor.BORDER),
                new EmptyBorder(12, 16, 12, 16)));

        inputField = new JTextField();
        inputField.putClientProperty("JTextField.placeholderText", "Nhập trả lời cho khách hàng...");
        inputField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        inputField.setEnabled(false);
        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) sendReply();
            }
        });

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
        sendButton.addActionListener(e -> sendReply());

        inputBar.add(inputField, BorderLayout.CENTER);
        inputBar.add(sendButton, BorderLayout.EAST);

        conversationCard.add(header, BorderLayout.NORTH);
        conversationCard.add(centerWrap, BorderLayout.CENTER);
        conversationCard.add(inputBar, BorderLayout.SOUTH);

        add(conversationCard, BorderLayout.CENTER);

        refreshCustomerListVisual();

        ChatServer.getInstance().addListener(serverListener);
    }

    public void setOnUnreadCountChanged(Consumer<Integer> callback) {
        this.onUnreadCountChanged = callback;
        notifyUnreadCountChanged();
    }

    private JPanel buildCustomerListCard() {
        JPanel card = new JPanel(new BorderLayout());
        card.setPreferredSize(new Dimension(260, 0));
        card.setBackground(AppColor.WHITE);
        card.setBorder(BorderFactory.createLineBorder(AppColor.BORDER, 1, true));

        JLabel title = new JLabel("Khách hàng đang chat");
        title.setFont(new Font("Segoe UI", Font.BOLD, 14));
        title.setForeground(AppColor.TEXT_PRIMARY);
        title.setBorder(new EmptyBorder(16, 16, 12, 16));

        customerList.setCellRenderer(new CustomerCellRenderer());
        customerList.setBackground(AppColor.WHITE);
        customerList.setBorder(new EmptyBorder(0, 8, 8, 8));
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

    private void selectCustomer(int userId) {
        selectedUserId = userId;
        unread.put(userId, false);
        customerList.repaint();
        notifyUnreadCountChanged();

        String name = onlineCustomers.getOrDefault(userId, "Khách hàng #" + userId);
        conversationTitle.setText(name + " (#" + userId + ")");
        boolean online = onlineCustomers.containsKey(userId);
        conversationStatus.setText(online ? "Đang trực tuyến" : "Đã ngắt kết nối");
        conversationStatus.setForeground(online ? AppColor.GREEN : AppColor.TEXT_MUTED_ALT);

        inputField.setEnabled(online);
        sendButton.setEnabled(online);

        renderConversation(userId);
    }

    private void sendReply() {
        if (selectedUserId == null) return;
        String text = inputField.getText() == null ? "" : inputField.getText().trim();
        if (text.isEmpty()) return;

        String adminName = AuthService.getInstance().isLoggedIn()
                ? AuthService.getInstance().getCurrentUser().getFullName() : "Quản trị viên";
        boolean sent = ChatServer.getInstance().sendToCustomer(selectedUserId, adminName, text);

        ChatMessage record = ChatMessage.chatFromAdmin(selectedUserId, adminName, text);
        conversations.computeIfAbsent(selectedUserId, k -> new ArrayList<>()).add(record);
        addBubble(text, true, TIME_FORMAT.format(new Date(record.timestamp)));
        inputField.setText("");

        if (!sent) {
            JOptionPane.showMessageDialog(this,
                    "Khách hàng đã ngắt kết nối, không gửi được tin nhắn này.",
                    "Không gửi được", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void onServerEvent(ChatMessage message) {
        if (message.isJoin()) {
            onlineCustomers.put(message.userId, message.userName);
            conversations.computeIfAbsent(message.userId, k -> new ArrayList<>());
            refreshCustomerListVisual();
            if (selectedUserId != null && selectedUserId == message.userId) selectCustomer(message.userId);

        } else if (message.isLeave()) {
            onlineCustomers.remove(message.userId);
            refreshCustomerListVisual();
            if (selectedUserId != null && selectedUserId == message.userId) selectCustomer(message.userId);

        } else if (message.isChat() && !message.fromAdmin) {
            conversations.computeIfAbsent(message.userId, k -> new ArrayList<>()).add(message);

            Toolkit.getDefaultToolkit().beep();

            boolean viewingRightNow = isShowing() && selectedUserId != null && selectedUserId == message.userId;
            if (viewingRightNow) {
                addBubble(message.text, false, TIME_FORMAT.format(new Date(message.timestamp)));
            } else {
                unread.put(message.userId, true);
                customerList.repaint();
                notifyUnreadCountChanged();
            }
        }
    }

    private void notifyUnreadCountChanged() {
        if (onUnreadCountChanged == null) return;
        long count = unread.values().stream().filter(Boolean::booleanValue).count();
        onUnreadCountChanged.accept((int) count);
    }

    private void refreshCustomerListVisual() {
        Integer previouslySelected = selectedUserId;
        customerListModel.clear();
        for (Integer userId : onlineCustomers.keySet()) {
            customerListModel.addElement(userId);
        }
        if (previouslySelected != null && onlineCustomers.containsKey(previouslySelected)) {
            customerList.setSelectedValue(previouslySelected, false);
        }
    }

    private void renderConversation(int userId) {
        messagesContainer.removeAll();
        List<ChatMessage> history = conversations.getOrDefault(userId, new ArrayList<>());
        if (history.isEmpty()) {
            JLabel hint = new JLabel("Chưa có tin nhắn nào với khách hàng này.");
            hint.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            hint.setForeground(AppColor.TEXT_MUTED);
            hint.setAlignmentX(Component.CENTER_ALIGNMENT);
            messagesContainer.add(hint);
        } else {
            for (ChatMessage message : history) {
                addBubbleSilently(message.text, message.fromAdmin, TIME_FORMAT.format(new Date(message.timestamp)));
            }
        }
        messagesContainer.revalidate();
        messagesContainer.repaint();
        scrollToBottom();
    }

    private void addBubble(String text, boolean isMine, String time) {
        addBubbleSilently(text, isMine, time);
        messagesContainer.revalidate();
        messagesContainer.repaint();
        scrollToBottom();
    }

    private void addBubbleSilently(String text, boolean isMine, String time) {
        JPanel row = new JPanel(new FlowLayout(isMine ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 6));
        row.setOpaque(false);

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
        bubble.setMaximumSize(new Dimension(360, Integer.MAX_VALUE));

        JLabel textLabel = new JLabel("<html><body style='width: 240px'>" + escapeHtml(text) + "</body></html>");
        textLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        textLabel.setForeground(isMine ? Color.WHITE : AppColor.TEXT_PRIMARY);

        JLabel timeLabel = new JLabel(time);
        timeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        timeLabel.setForeground(isMine ? AppColor.ACCENT_SELECTION_BG : AppColor.TEXT_MUTED);
        timeLabel.setBorder(new EmptyBorder(4, 0, 0, 0));

        JPanel textWrap = new JPanel();
        textWrap.setOpaque(false);
        textWrap.setLayout(new BoxLayout(textWrap, BoxLayout.Y_AXIS));
        textWrap.add(textLabel);
        textWrap.add(timeLabel);

        bubble.add(textWrap, BorderLayout.CENTER);
        row.add(bubble);
        messagesContainer.add(row);
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar vBar = scrollPane.getVerticalScrollBar();
            vBar.setValue(vBar.getMaximum());
        });
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
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
            String name = onlineCustomers.getOrDefault(userId, "Khách hàng #" + userId);
            nameLabel.setText(name);
            nameLabel.setForeground(AppColor.TEXT_PRIMARY);
            dot.setForeground(onlineCustomers.containsKey(userId) ? AppColor.GREEN : AppColor.TEXT_MUTED_ALT);
            unreadDot.setVisible(Boolean.TRUE.equals(unread.get(userId)));
            setBackground(isSelected ? AppColor.ACCENT_SELECTION_BG : AppColor.WHITE);
            setOpaque(true);
            return this;
        }
    }
}