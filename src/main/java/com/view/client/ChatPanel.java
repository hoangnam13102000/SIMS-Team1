package com.view.client;

import com.theme.AppColor;
import com.service.AuthService;
import com.ws.ChatClient;
import com.ws.ChatMessage;

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
import java.util.function.Consumer;

/** Noi dung khung chat "Ho tro truc tuyen" ben phia khach hang. */
public class ChatPanel extends JPanel {

    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm");

    private final JPanel messagesContainer;
    private final JScrollPane scrollPane;
    private final JTextField inputField;
    private JLabel statusDotRef;
    private JLabel statusLabelRef;

    private final Consumer<ChatMessage> messageListener = this::onMessageReceived;
    private final Consumer<Boolean> connectionListener = this::onConnectionChanged;

    private Runnable onCloseListener;
    private Runnable onIncomingMessageListener;

    public ChatPanel() {
        setLayout(new BorderLayout());
        setBackground(AppColor.WHITE);
        setBorder(new LineBorderRounded(AppColor.BORDER, 16));

        JPanel headerBar = buildHeaderBar();

        messagesContainer = new JPanel();
        messagesContainer.setLayout(new BoxLayout(messagesContainer, BoxLayout.Y_AXIS));
        messagesContainer.setBackground(AppColor.WHITE);
        messagesContainer.setBorder(new EmptyBorder(16, 16, 16, 16));

        scrollPane = new JScrollPane(messagesContainer);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getViewport().setBackground(AppColor.WHITE);

        JPanel inputBar = new JPanel(new BorderLayout(10, 0));
        inputBar.setBackground(AppColor.WHITE);
        inputBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, AppColor.BORDER),
                new EmptyBorder(12, 16, 12, 16)));

        inputField = new JTextField();
        inputField.putClientProperty("JTextField.placeholderText", "Nhập câu hỏi của bạn...");
        inputField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) sendCurrentInput();
            }
        });

        FontIcon sendIcon = FontIcon.of(FontAwesomeSolid.PAPER_PLANE, 13);
        sendIcon.setIconColor(Color.WHITE);
        JButton sendButton = new JButton("Gửi", sendIcon);
        sendButton.setFocusPainted(false);
        sendButton.setBackground(AppColor.ACCENT_HOVER);
        sendButton.setForeground(Color.WHITE);
        sendButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        sendButton.setBorder(new EmptyBorder(8, 18, 8, 18));
        sendButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        sendButton.addActionListener(e -> sendCurrentInput());

        inputBar.add(inputField, BorderLayout.CENTER);
        inputBar.add(sendButton, BorderLayout.EAST);

        add(headerBar, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(inputBar, BorderLayout.SOUTH);

        addWelcomeBubble();

        ChatClient chatClient = ChatClient.getInstance();
        chatClient.addMessageListener(messageListener);
        chatClient.addConnectionListener(connectionListener);

        if (AuthService.getInstance().isLoggedIn()) {
            chatClient.connect(
                    AuthService.getInstance().getCurrentUser().getUserId(),
                    AuthService.getInstance().getCurrentUser().getFullName());
        }
        setConnectionUi(chatClient.isConnected());
    }

    public void onClose(Runnable listener) {
        this.onCloseListener = listener;
    }

    public void onIncomingMessage(Runnable listener) {
        this.onIncomingMessageListener = listener;
    }

    private JPanel buildHeaderBar() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(AppColor.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AppColor.BORDER),
                new EmptyBorder(14, 18, 14, 14)));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.setOpaque(false);
        FontIcon headsetIcon = FontIcon.of(FontAwesomeSolid.HEADSET, 17);
        headsetIcon.setIconColor(AppColor.ACCENT_HOVER);
        JLabel titleLabel = new JLabel("Hỗ trợ trực tuyến");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        titleLabel.setForeground(AppColor.TEXT_PRIMARY);
        left.add(new JLabel(headsetIcon));
        left.add(titleLabel);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);
        statusDotRef = new JLabel("●");
        statusDotRef.setForeground(AppColor.TEXT_MUTED_ALT);
        statusLabelRef = new JLabel("Đang kết nối...");
        statusLabelRef.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        statusLabelRef.setForeground(AppColor.TEXT_MUTED);

        JLabel closeButton = new JLabel(iconOf(FontAwesomeSolid.TIMES, 14, AppColor.TEXT_MUTED));
        closeButton.setBorder(new EmptyBorder(4, 8, 4, 0));
        closeButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        closeButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (onCloseListener != null) onCloseListener.run();
            }
        });

        right.add(statusDotRef);
        right.add(statusLabelRef);
        right.add(closeButton);

        header.add(left, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private FontIcon iconOf(FontAwesomeSolid type, int size, Color color) {
        FontIcon icon = FontIcon.of(type, size);
        icon.setIconColor(color);
        return icon;
    }

    private void addWelcomeBubble() {
        addBubble("Xin chào! Bạn cần hỗ trợ gì, hãy nhắn cho chúng tôi nhé.",
                false, TIME_FORMAT.format(new Date()));
    }

    private void sendCurrentInput() {
        String text = inputField.getText() == null ? "" : inputField.getText().trim();
        if (text.isEmpty()) return;

        boolean sent = ChatClient.getInstance().sendMessage(text);
        addBubble(text, true, TIME_FORMAT.format(new Date()));
        inputField.setText("");

        if (!sent) {
            addBubble("Không thể gửi: bộ phận hỗ trợ hiện chưa trực tuyến. Vui lòng thử lại sau.",
                    false, TIME_FORMAT.format(new Date()));
        }
    }

    private void onMessageReceived(ChatMessage message) {
        if (!message.isChat() || !message.fromAdmin) return;
        addBubble(message.text, false, TIME_FORMAT.format(new Date(message.timestamp)));
        if (onIncomingMessageListener != null) onIncomingMessageListener.run();
    }

    private void onConnectionChanged(boolean connected) {
        setConnectionUi(connected);
    }

    private void setConnectionUi(boolean connected) {
        statusDotRef.setForeground(connected ? AppColor.GREEN : AppColor.TEXT_MUTED_ALT);
        statusLabelRef.setText(connected ? "Đang trực tuyến" : "Mất kết nối");
    }

    private void addBubble(String text, boolean isMine, String time) {
        JPanel row = new JPanel(new FlowLayout(isMine ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 6));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height + 40));

        JPanel bubble = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isMine ? AppColor.ACCENT_HOVER : AppColor.BG_LIGHTER);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        bubble.setOpaque(false);
        bubble.setBorder(new EmptyBorder(10, 14, 10, 14));
        bubble.setMaximumSize(new Dimension(260, Integer.MAX_VALUE));

        JLabel textLabel = new JLabel("<html><body style='width: 190px'>" + escapeHtml(text) + "</body></html>");
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
        messagesContainer.revalidate();
        messagesContainer.repaint();

        SwingUtilities.invokeLater(() -> {
            JScrollBar vBar = scrollPane.getVerticalScrollBar();
            vBar.setValue(vBar.getMaximum());
        });
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
    }

    private static class LineBorderRounded extends javax.swing.border.AbstractBorder {
        private final Color color;
        private final int radius;

        LineBorderRounded(Color color, int radius) {
            this.color = color;
            this.radius = radius;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(1, 1, 1, 1);
        }
    }
}