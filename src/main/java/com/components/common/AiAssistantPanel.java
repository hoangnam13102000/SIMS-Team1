package com.components.common;

import com.model.ai.AiChatMessage;
import com.service.ai.AiChatService;
import com.service.ai.GeminiService;
import com.theme.AppColor;
import com.utils.ImageUtil;

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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Panel chat với trợ lý AI (Gemini). Dùng chung cho:
 * - Khách hàng: đặt trong {@code AiAssistantWidget} (bong bóng nổi).
 * - Admin/Staff: đặt làm 1 trang trong sidebar (full panel, không có nút đóng).
 * <p>
 * Gọi {@link AiChatService} trên background thread (SwingWorker). Service tự
 * chọn system prompt theo Role, đăng ký tool theo Permission, query DB an toàn.
 */
public class AiAssistantPanel extends JPanel {

    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm");
    /** Marker tool nhúng path ảnh: [[IMG:uploads/products/x.jpg]] */
    private static final Pattern IMG_MARKER = Pattern.compile("\\[\\[IMG:(.+?)\\]\\]");
    private static final int PRODUCT_IMG_MAX = 180;

    private final JPanel messagesContainer;
    private final JScrollPane scrollPane;
    private final JTextField inputField;
    private final JButton sendButton;
    private final String headerTitle;
    private final String welcomeText;
    /** true = phía client (khách), false = nhân viên/admin đã login. */
    private final boolean clientSide;
    private final AiChatService chatService = new AiChatService();

    private final List<AiChatMessage> history = new ArrayList<>();
    private JPanel typingBubbleRef;
    private Runnable onCloseListener;

    /**
     * Constructor khuyến nghị.
     *
     * @param headerTitle     tiêu đề header
     * @param welcomeText     câu chào
     * @param showCloseButton true nếu hiện nút đóng (widget nổi)
     * @param clientSide      true nếu dùng cho khách hàng
     */
    public AiAssistantPanel(String headerTitle, String welcomeText,
                            boolean showCloseButton, boolean clientSide) {
        this.headerTitle = headerTitle;
        this.welcomeText = welcomeText;
        this.clientSide = clientSide;

        setLayout(new BorderLayout());
        setBackground(AppColor.WHITE);
        setBorder(new LineBorderRounded(AppColor.BORDER, 16));

        JPanel headerBar = buildHeaderBar(showCloseButton);

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
        inputField.putClientProperty("JTextField.placeholderText", "Nhập câu hỏi cho trợ lý AI...");
        inputField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) sendCurrentInput();
            }
        });

        FontIcon sendIcon = FontIcon.of(FontAwesomeSolid.PAPER_PLANE, 13);
        sendIcon.setIconColor(Color.WHITE);
        sendButton = new JButton("Gửi", sendIcon);
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

        addBubble(welcomeText, false, TIME_FORMAT.format(new Date()));

        if (!GeminiService.isConfigured()) {
            setInputEnabled(false);
            addBubble("Trợ lý AI chưa được cấu hình (thiếu GEMINI_API_KEY). Vui lòng liên hệ quản trị viên.",
                    false, TIME_FORMAT.format(new Date()));
        }
    }

    /**
     * Constructor tương thích ngược – systemInstruction bị bỏ qua
     * (AiChatService tự sinh prompt theo role). clientSide = showCloseButton.
     */
    @Deprecated
    public AiAssistantPanel(String headerTitle, String systemInstruction,
                            String welcomeText, boolean showCloseButton) {
        this(headerTitle, welcomeText, showCloseButton, showCloseButton);
    }

    public void onClose(Runnable listener) {
        this.onCloseListener = listener;
    }

    private void setInputEnabled(boolean enabled) {
        inputField.setEnabled(enabled);
        sendButton.setEnabled(enabled);
    }

    private JPanel buildHeaderBar(boolean showCloseButton) {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(AppColor.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AppColor.BORDER),
                new EmptyBorder(14, 18, 14, 14)));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.setOpaque(false);
        FontIcon robotIcon = FontIcon.of(FontAwesomeSolid.ROBOT, 17);
        robotIcon.setIconColor(AppColor.ACCENT_HOVER);
        JLabel titleLabel = new JLabel(headerTitle);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        titleLabel.setForeground(AppColor.TEXT_PRIMARY);
        left.add(new JLabel(robotIcon));
        left.add(titleLabel);
        header.add(left, BorderLayout.WEST);

        if (showCloseButton) {
            JLabel closeButton = new JLabel(iconOf(FontAwesomeSolid.TIMES, 14, AppColor.TEXT_MUTED));
            closeButton.setBorder(new EmptyBorder(4, 8, 4, 0));
            closeButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
            closeButton.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (onCloseListener != null) onCloseListener.run();
                }
            });
            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
            right.setOpaque(false);
            right.add(closeButton);
            header.add(right, BorderLayout.EAST);
        }

        return header;
    }

    private FontIcon iconOf(FontAwesomeSolid type, int size, Color color) {
        FontIcon icon = FontIcon.of(type, size);
        icon.setIconColor(color);
        return icon;
    }

    private void sendCurrentInput() {
        String text = inputField.getText() == null ? "" : inputField.getText().trim();
        if (text.isEmpty() || !sendButton.isEnabled()) return;

        addBubble(text, true, TIME_FORMAT.format(new Date()));
        history.add(new AiChatMessage("user", text));
        inputField.setText("");
        setInputEnabled(false);
        showTypingBubble();

        List<AiChatMessage> historySnapshot = new ArrayList<>(history);
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return chatService.chat(historySnapshot, clientSide);
            }

            @Override
            protected void done() {
                hideTypingBubble();
                setInputEnabled(true);
                String reply;
                try {
                    reply = get();
                    if (reply == null || reply.isBlank()) {
                        reply = "Xin lỗi, mình chưa có câu trả lời phù hợp cho câu hỏi này.";
                    }
                } catch (Exception ex) {
                    reply = "Không thể kết nối tới trợ lý AI lúc này. Vui lòng thử lại sau.";
                }
                history.add(new AiChatMessage("model", reply));
                addBubble(reply, false, TIME_FORMAT.format(new Date()));
                inputField.requestFocusInWindow();
            }
        }.execute();
    }

    private void showTypingBubble() {
        typingBubbleRef = buildBubbleRow("Đang trả lời...", false, "");
        messagesContainer.add(typingBubbleRef);
        messagesContainer.revalidate();
        scrollToBottom();
    }

    private void hideTypingBubble() {
        if (typingBubbleRef != null) {
            messagesContainer.remove(typingBubbleRef);
            messagesContainer.revalidate();
            messagesContainer.repaint();
            typingBubbleRef = null;
        }
    }

    private void addBubble(String text, boolean isMine, String time) {
        JPanel row = buildBubbleRow(text, isMine, time);
        messagesContainer.add(row);
        messagesContainer.revalidate();
        scrollToBottom();
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar vbar = scrollPane.getVerticalScrollBar();
            vbar.setValue(vbar.getMaximum());
        });
    }

    private JPanel buildBubbleRow(String text, boolean isMine, String time) {
        int viewportW = scrollPane.getViewport().getWidth();
        if (viewportW <= 0) viewportW = 300;
        int maxBubbleW = Math.max(160, Math.min(280, viewportW - 48));
        int htmlW = Math.max(120, maxBubbleW - 40);

        JPanel row = new JPanel(new FlowLayout(isMine ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 6));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

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
        bubble.setMaximumSize(new Dimension(maxBubbleW, Integer.MAX_VALUE));

        JPanel contentWrap = new JPanel();
        contentWrap.setOpaque(false);
        contentWrap.setLayout(new BoxLayout(contentWrap, BoxLayout.Y_AXIS));

        // Tách text + danh sách path ảnh từ marker [[IMG:...]]
        List<String> imagePaths = new ArrayList<>();
        String displayText = extractImages(text, imagePaths);

        if (displayText != null && !displayText.isBlank()) {
            JLabel textLabel = new JLabel("<html><body style='width: " + htmlW + "px'>"
                    + escapeHtml(displayText) + "</body></html>");
            textLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            textLabel.setForeground(isMine ? Color.WHITE : AppColor.TEXT_PRIMARY);
            textLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            contentWrap.add(textLabel);
        }

        // Ảnh sản phẩm (chỉ bubble AI / không phải tin nhắn user)
        if (!isMine) {
            for (String path : imagePaths) {
                JLabel imgLabel = buildProductImageLabel(path, maxBubbleW - 28);
                if (imgLabel != null) {
                    imgLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                    imgLabel.setBorder(new EmptyBorder(8, 0, 0, 0));
                    contentWrap.add(imgLabel);
                }
            }
        }

        if (time != null && !time.isBlank()) {
            JLabel timeLabel = new JLabel(time);
            timeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            timeLabel.setForeground(isMine ? new Color(255, 255, 255, 200) : AppColor.TEXT_MUTED);
            timeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            timeLabel.setBorder(new EmptyBorder(4, 0, 0, 0));
            contentWrap.add(timeLabel);
        }

        bubble.add(contentWrap, BorderLayout.CENTER);
        row.add(bubble);
        return row;
    }

    /**
     * Lấy mọi [[IMG:path]] ra khỏi text, path cho vào outPaths, trả về text còn lại.
     * Cũng nhận dòng "Ảnh: path" từ tool cũ (tương thích).
     */
    private static String extractImages(String text, List<String> outPaths) {
        if (text == null || text.isBlank()) return "";
        StringBuilder cleaned = new StringBuilder();
        Matcher m = IMG_MARKER.matcher(text);
        int last = 0;
        while (m.find()) {
            cleaned.append(text, last, m.start());
            String path = m.group(1).trim();
            if (!path.isEmpty()) outPaths.add(path);
            last = m.end();
        }
        cleaned.append(text.substring(last));

        // Tương thích tool cũ: dòng "Ảnh: xxx"
        String[] lines = cleaned.toString().split("\n", -1);
        StringBuilder withoutAnh = new StringBuilder();
        for (String line : lines) {
            String t = line.trim();
            if (t.regionMatches(true, 0, "Ảnh:", 0, 4) || t.regionMatches(true, 0, "Anh:", 0, 4)) {
                String path = t.substring(t.indexOf(':') + 1).trim();
                if (!path.isEmpty()) outPaths.add(path);
                continue;
            }
            if (withoutAnh.length() > 0) withoutAnh.append('\n');
            withoutAnh.append(line);
        }
        return withoutAnh.toString().trim();
    }

    private static JLabel buildProductImageLabel(String pathOrUrl, int maxW) {
        if (pathOrUrl == null || pathOrUrl.isBlank()) return null;
        int w = Math.min(PRODUCT_IMG_MAX, Math.max(80, maxW));
        ImageIcon icon = ImageUtil.loadIcon(pathOrUrl, w, w);
        if (icon == null || icon.getIconWidth() <= 0) return null;
        JLabel label = new JLabel(icon);
        label.setToolTipText(pathOrUrl);
        return label;
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("\n", "<br>");
    }

    /** Border bo góc dùng chung cho panel này (giống LineBorderRounded trong ChatPanel). */
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