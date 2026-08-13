package com.view.admin;

import com.components.AppAlert;
import com.components.BaseDialog;
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
import com.utils.FileDownloadUI;
import com.utils.NotificationSound;
import com.ws.ChatClient;
import com.ws.ChatImageUtil;
import com.ws.ChatFileUtil;
import com.ws.ChatMessage;
import com.ws.ChatServer;
import com.ws.VoiceNotePlayer;
import com.ws.VoiceNoteSender;
import com.components.common.SoundWaveIcon;
import com.components.common.VoiceMessageBubble;
import com.service.ai.voice.TextToSpeechService;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ChatPanel extends JPanel {
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm");
    private static final int IMAGE_MAX_W = 240;

    private final Map<Integer, String> onlineCustomers = new LinkedHashMap<>();
    private final Map<Integer, String> customerDisplayNames = new LinkedHashMap<>();
    private final Set<Integer> knownCustomerIds = new LinkedHashSet<>();
    private final Set<Integer> customerHistoryLoaded = new HashSet<>();
    private final Set<Integer> staffHistoryLoaded = new HashSet<>();
    private final Map<Integer, List<ChatMessage>> customerConversations = new LinkedHashMap<>();
    private final Map<Integer, Boolean> customerUnread = new LinkedHashMap<>();
    private final Map<Integer, String> customerLastPreview = new LinkedHashMap<>();
    private final Map<Integer, Long> customerLastTime = new LinkedHashMap<>();
    private final DefaultListModel<Integer> customerListModel = new DefaultListModel<>();
    private final JList<Integer> customerList = new JList<>(customerListModel);
    private Integer selectedCustomerId;

    // ================================================================
    // ====== SEARCH + AUTOCOMPLETE: KHÁCH HÀNG ======
    // ================================================================
    private final JTextField customerSearchField = new JTextField();
    private final JWindow customerSuggestPopup = new JWindow();
    private final JList<String> customerSuggestList = new JList<>(new DefaultListModel<>());
    /** Bộ lọc đang áp dụng cho danh sách khách (null/empty = không lọc). */
    private String customerFilterText = "";

    private final Map<Integer, User> staffDirectory = new LinkedHashMap<>();
    private final Set<Integer> onlineStaffIds = new HashSet<>();
    private final Map<Integer, List<ChatMessage>> staffConversations = new LinkedHashMap<>();
    private final Map<Integer, Boolean> staffUnread = new LinkedHashMap<>();
    private final Map<Integer, String> staffLastPreview = new LinkedHashMap<>();
    private final Map<Integer, Long> staffLastTime = new LinkedHashMap<>();
    private final DefaultListModel<Integer> staffListModel = new DefaultListModel<>();
    private final JList<Integer> staffList = new JList<>(staffListModel);
    private Integer selectedStaffId;

    // ================================================================
    // ====== SEARCH + AUTOCOMPLETE: NHÂN VIÊN ======
    // ================================================================
    private final JTextField staffSearchField = new JTextField();
    private final JWindow staffSuggestPopup = new JWindow();
    private final JList<String> staffSuggestList = new JList<>(new DefaultListModel<>());
    private String staffFilterText = "";

    private final JPanel messagesContainer;
    private final JScrollPane scrollPane;
    private final JTextField inputField;
    private final JButton sendButton;
    private final JButton imageButton;
    private final JButton voiceMicButton;
    private final JButton ttsButton;
    private final VoiceNoteSender voiceSender = new VoiceNoteSender();
    private final SoundWaveIcon soundWave = new SoundWaveIcon();
    private javax.swing.Timer voiceLevelTimer;
    private JProgressBar voiceLoadingBar;
    private JLabel voiceLoadingLabel;
    private JPanel voiceLoadingPanel;
    private final TextToSpeechService ttsService = new TextToSpeechService();
    private boolean ttsEnabled;
    private String lastIncomingText;

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

        sideTabs = new JTabbedPane(JTabbedPane.TOP);
        sideTabs.setFont(new Font("Segoe UI", Font.BOLD, 13));
        sideTabs.setBackground(AppColor.PAGE_BG);
        sideTabs.setForeground(AppColor.TEXT_MUTED);
        sideTabs.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, AppColor.BORDER));
        // Tùy chỉnh màu tab cho dark theme
        UIManager.put("TabbedPane.selected", AppColor.BG_LIGHT);
        UIManager.put("TabbedPane.selectHighlight", AppColor.ACCENT);
        UIManager.put("TabbedPane.contentAreaColor", AppColor.BG_LIGHT);
        UIManager.put("TabbedPane.tabAreaBackground", AppColor.PAGE_BG);
        sideTabs.addTab("  Khách hàng  ", buildCustomerListCard());
        sideTabs.addTab("  Nội bộ  ", buildStaffListCard());
        sideTabs.addChangeListener(e -> {
            staffTabActive = sideTabs.getSelectedIndex() == 1;
            hideAllSuggestPopups(); // ẩn gợi ý khi đổi tab
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

        JLabel clearAllButton = new JLabel();
        FontIcon trashIcon = FontIcon.of(FontAwesomeSolid.TRASH_ALT, 14);
        trashIcon.setIconColor(AppColor.TEXT_MUTED);
        clearAllButton.setIcon(trashIcon);
        clearAllButton.setToolTipText("Xóa tất cả tin nhắn trong cuộc trò chuyện này");
        clearAllButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        clearAllButton.setBorder(new EmptyBorder(4, 8, 4, 4));
        clearAllButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                clearCurrentConversationMessages();
            }
        });
        header.add(clearAllButton, BorderLayout.EAST);

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
        imageButton = buildIconButton(FontAwesomeSolid.PAPERCLIP, "Gửi ảnh / file");
        imageButton.setEnabled(false);
        imageButton.addActionListener(e -> pickAndSendAttachment());
        voiceMicButton = buildIconButton(FontAwesomeSolid.MICROPHONE, "Tin nhắn thoại");
        voiceMicButton.setEnabled(false);
        voiceMicButton.addActionListener(e -> toggleVoiceNote());
        ttsEnabled = false;
        lastIncomingText = null;
        ttsButton = buildIconButton(FontAwesomeSolid.VOLUME_UP, "Bật đọc to tin nhắn đến");
        ttsButton.setEnabled(true);
        ttsButton.addActionListener(e -> toggleChatTts());

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
        rightActions.add(ttsButton);
        rightActions.add(voiceMicButton);
        rightActions.add(imageButton);
        rightActions.add(sendButton);
        inputBar.add(inputField, BorderLayout.CENTER);
        inputBar.add(rightActions, BorderLayout.EAST);

        voiceLoadingLabel = new JLabel("Đang xử lý giọng nói…");
        voiceLoadingLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        voiceLoadingLabel.setForeground(AppColor.TEXT_MUTED);
        voiceLoadingBar = new JProgressBar();
        voiceLoadingBar.setIndeterminate(true);
        voiceLoadingBar.setPreferredSize(new Dimension(100, 4));
        voiceLoadingPanel = new JPanel(new BorderLayout(8, 0));
        voiceLoadingPanel.setBackground(AppColor.BG_LIGHTER);
        voiceLoadingPanel.setBorder(new EmptyBorder(6, 16, 4, 16));
        JPanel waveAndLabel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        waveAndLabel.setOpaque(false);
        soundWave.setPreferredSize(new Dimension(28, 22));
        soundWave.setBarColor(AppColor.ACCENT_HOVER);
        waveAndLabel.add(soundWave);
        waveAndLabel.add(voiceLoadingLabel);
        voiceLoadingPanel.add(waveAndLabel, BorderLayout.WEST);
        voiceLoadingPanel.add(voiceLoadingBar, BorderLayout.CENTER);
        voiceLoadingPanel.setVisible(false);

        JPanel southWrap = new JPanel(new BorderLayout());
        southWrap.setOpaque(false);
        southWrap.add(voiceLoadingPanel, BorderLayout.NORTH);
        southWrap.add(inputBar, BorderLayout.SOUTH);

        conversationCard.add(header, BorderLayout.NORTH);
        conversationCard.add(scrollPane, BorderLayout.CENTER);
        conversationCard.add(southWrap, BorderLayout.SOUTH);
        add(conversationCard, BorderLayout.CENTER);

        loadStaffDirectory();
        loadKnownCustomerThreads();
        refreshCustomerListVisual();
        refreshStaffListVisual();

        // ====== Khởi tạo autocomplete cho 2 ô tìm kiếm ======
        installCustomerSearchAutocomplete();
        installStaffSearchAutocomplete();

        ChatServer.getInstance().addListener(serverListener);
        ChatClient.getInstance().addMessageListener(staffClientListener);
    }

    // ================================================================
    // ========== 1) TÌM KIẾM + AUTOCOMPLETE: KHÁCH HÀNG ==========
    // ================================================================
    private void installCustomerSearchAutocomplete() {
        customerSearchField.putClientProperty("JTextField.placeholderText", "Tìm tên khách hàng…");
        customerSearchField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        customerSuggestList.setFocusable(false);
        customerSuggestList.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        customerSuggestList.setVisibleRowCount(6);
        customerSuggestList.setFixedCellHeight(24);
        customerSuggestList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        customerSuggestList.setBackground(AppColor.WHITE);
        customerSuggestList.setBorder(BorderFactory.createLineBorder(AppColor.BORDER, 1));
        JScrollPane sp = new JScrollPane(customerSuggestList);
        sp.setBorder(null);
        sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        customerSuggestPopup.getContentPane().removeAll();
        customerSuggestPopup.getContentPane().add(sp);
        customerSuggestPopup.setFocusableWindowState(false);

        // Gõ → cập nhật gợi ý + lọc danh sách
        customerSearchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { onCustomerSearch(); }
            @Override public void removeUpdate(DocumentEvent e) { onCustomerSearch(); }
            @Override public void changedUpdate(DocumentEvent e) { onCustomerSearch(); }
        });

        // Chọn 1 gợi ý → điền vào ô tìm kiếm, ẩn popup, lọc danh sách
        customerSuggestList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                String sel = customerSuggestList.getSelectedValue();
                if (sel != null) {
                    customerSearchField.setText(sel);
                    hideCustomerSuggest();
                    customerSearchField.requestFocus();
                }
            }
        });

        // Phím: ↑ ↓ chọn gợi ý, Enter chấp nhận, Esc ẩn popup
        customerSearchField.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    if (customerSuggestPopup.isVisible() && customerSuggestList.getModel().getSize() > 0) {
                        int i = customerSuggestList.getSelectedIndex();
                        customerSuggestList.setSelectedIndex(Math.min(i + 1, customerSuggestList.getModel().getSize() - 1));
                        customerSuggestList.ensureIndexIsVisible(customerSuggestList.getSelectedIndex());
                        e.consume();
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_UP) {
                    if (customerSuggestPopup.isVisible() && customerSuggestList.getModel().getSize() > 0) {
                        int i = customerSuggestList.getSelectedIndex();
                        customerSuggestList.setSelectedIndex(Math.max(i - 1, 0));
                        customerSuggestList.ensureIndexIsVisible(customerSuggestList.getSelectedIndex());
                        e.consume();
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if (customerSuggestPopup.isVisible()) {
                        String sel = customerSuggestList.getSelectedValue();
                        if (sel != null) {
                            customerSearchField.setText(sel);
                            e.consume();
                        }
                    }
                    hideCustomerSuggest();
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    hideCustomerSuggest();
                }
            }
        });

        // Click ra ngoài → ẩn popup
        customerSearchField.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) {
                SwingUtilities.invokeLater(this::hideIfNotInsideSuggest);
            }
            private void hideIfNotInsideSuggest() {
                if (!customerSuggestList.hasFocus()) hideCustomerSuggest();
            }
        });
    }

    private void onCustomerSearch() {
        String q = customerSearchField.getText() == null ? "" : customerSearchField.getText().trim().toLowerCase();
        customerFilterText = q;
        // Lọc danh sách hiển thị theo từ khóa
        refreshCustomerListVisual();
        // Hiện gợi ý autocomplete
        if (q.isEmpty()) {
            hideCustomerSuggest();
            return;
        }
        List<String> allNames = collectAllCustomerNames();
        List<String> matched = allNames.stream()
                .filter(n -> n.toLowerCase().contains(q))
                .distinct()
                .sorted(Comparator.comparingInt(String::length)) // tên ngắn lên trước
                .limit(8)
                .collect(Collectors.toList());
        if (matched.isEmpty()) {
            hideCustomerSuggest();
            return;
        }
        DefaultListModel<String> m = (DefaultListModel<String>) customerSuggestList.getModel();
        m.clear();
        matched.forEach(m::addElement);
        customerSuggestList.setSelectedIndex(0);
        showCustomerSuggestPopup();
    }

    /** Tất cả tên khách đã biết (online + lịch sử DB) → làm nguồn autocomplete. */
    private List<String> collectAllCustomerNames() {
        List<String> names = new ArrayList<>();
        for (Integer id : knownCustomerIds) {
            String n = customerDisplayName(id);
            if (n != null && !n.isBlank() && !n.startsWith("Khách hàng #")) names.add(n);
        }
        for (String n : onlineCustomers.values()) {
            if (n != null && !n.isBlank()) names.add(n);
        }
        for (String n : customerDisplayNames.values()) {
            if (n != null && !n.isBlank()) names.add(n);
        }
        return names;
    }

    private void showCustomerSuggestPopup() {
        try {
            Point loc = customerSearchField.getLocationOnScreen();
            customerSuggestPopup.setLocation(loc.x, loc.y + customerSearchField.getHeight());
            customerSuggestPopup.setSize(customerSearchField.getWidth(), Math.min(200, customerSuggestList.getFixedCellHeight() * 6 + 6));
            customerSuggestPopup.setVisible(true);
        } catch (Exception ignored) { }
    }

    private void hideCustomerSuggest() {
        if (customerSuggestPopup != null) customerSuggestPopup.setVisible(false);
    }

    // ================================================================
    // ========== 2) TÌM KIẾM + AUTOCOMPLETE: NHÂN VIÊN ==========
    // ================================================================
    private void installStaffSearchAutocomplete() {
        staffSearchField.putClientProperty("JTextField.placeholderText", "Tìm tên nhân viên…");
        staffSearchField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        staffSuggestList.setFocusable(false);
        staffSuggestList.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        staffSuggestList.setVisibleRowCount(6);
        staffSuggestList.setFixedCellHeight(24);
        staffSuggestList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        staffSuggestList.setBackground(AppColor.WHITE);
        staffSuggestList.setBorder(BorderFactory.createLineBorder(AppColor.BORDER, 1));
        JScrollPane sp = new JScrollPane(staffSuggestList);
        sp.setBorder(null);
        sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        staffSuggestPopup.getContentPane().removeAll();
        staffSuggestPopup.getContentPane().add(sp);
        staffSuggestPopup.setFocusableWindowState(false);

        staffSearchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { onStaffSearch(); }
            @Override public void removeUpdate(DocumentEvent e) { onStaffSearch(); }
            @Override public void changedUpdate(DocumentEvent e) { onStaffSearch(); }
        });

        staffSuggestList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                String sel = staffSuggestList.getSelectedValue();
                if (sel != null) {
                    staffSearchField.setText(sel);
                    hideStaffSuggest();
                    staffSearchField.requestFocus();
                }
            }
        });

        staffSearchField.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    if (staffSuggestPopup.isVisible() && staffSuggestList.getModel().getSize() > 0) {
                        int i = staffSuggestList.getSelectedIndex();
                        staffSuggestList.setSelectedIndex(Math.min(i + 1, staffSuggestList.getModel().getSize() - 1));
                        staffSuggestList.ensureIndexIsVisible(staffSuggestList.getSelectedIndex());
                        e.consume();
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_UP) {
                    if (staffSuggestPopup.isVisible() && staffSuggestList.getModel().getSize() > 0) {
                        int i = staffSuggestList.getSelectedIndex();
                        staffSuggestList.setSelectedIndex(Math.max(i - 1, 0));
                        staffSuggestList.ensureIndexIsVisible(staffSuggestList.getSelectedIndex());
                        e.consume();
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if (staffSuggestPopup.isVisible()) {
                        String sel = staffSuggestList.getSelectedValue();
                        if (sel != null) {
                            staffSearchField.setText(sel);
                            e.consume();
                        }
                    }
                    hideStaffSuggest();
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    hideStaffSuggest();
                }
            }
        });

        staffSearchField.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) {
                SwingUtilities.invokeLater(() -> {
                    if (!staffSuggestList.hasFocus()) hideStaffSuggest();
                });
            }
        });
    }

    private void onStaffSearch() {
        String q = staffSearchField.getText() == null ? "" : staffSearchField.getText().trim().toLowerCase();
        staffFilterText = q;
        refreshStaffListVisual();
        if (q.isEmpty()) {
            hideStaffSuggest();
            return;
        }
        List<String> matched = staffDirectory.values().stream()
                .map(ChatPanel::displayName)
                .filter(n -> n.toLowerCase().contains(q))
                .distinct()
                .sorted(Comparator.comparingInt(String::length))
                .limit(8)
                .collect(Collectors.toList());
        if (matched.isEmpty()) {
            hideStaffSuggest();
            return;
        }
        DefaultListModel<String> m = (DefaultListModel<String>) staffSuggestList.getModel();
        m.clear();
        matched.forEach(m::addElement);
        staffSuggestList.setSelectedIndex(0);
        try {
            Point loc = staffSearchField.getLocationOnScreen();
            staffSuggestPopup.setLocation(loc.x, loc.y + staffSearchField.getHeight());
            staffSuggestPopup.setSize(staffSearchField.getWidth(), Math.min(200, staffSuggestList.getFixedCellHeight() * 6 + 6));
            staffSuggestPopup.setVisible(true);
        } catch (Exception ignored) { }
    }

    private void hideStaffSuggest() {
        if (staffSuggestPopup != null) staffSuggestPopup.setVisible(false);
    }

    private void hideAllSuggestPopups() {
        hideCustomerSuggest();
        hideStaffSuggest();
    }

    // ================================================================
    // ========== 3) GIAO DIỆN 2 TAB: THÊM Ô TÌM KIẾM PHÍA TRÊN ==========
    // ================================================================
    private JPanel buildCustomerListCard() {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(AppColor.PAGE_BG);

        // ====== Tiêu đề ======
        JLabel title = new JLabel("Khách đang chat");
        title.setFont(new Font("Segoe UI", Font.BOLD, 14));
        title.setForeground(AppColor.TEXT_PRIMARY);
        title.setBorder(new EmptyBorder(14, 16, 10, 16));

        // ====== Ô tìm kiếm khách hàng ======
        JPanel searchWrap = new JPanel(new BorderLayout());
        searchWrap.setOpaque(false);
        searchWrap.setBorder(new EmptyBorder(0, 12, 10, 12));

        JPanel searchBox = new JPanel(new BorderLayout());
        searchBox.setBackground(new Color(0, 0, 0, 0));
        searchBox.setOpaque(false);
        searchBox.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(6, 10, 6, 10)));

        customerSearchField.setBorder(BorderFactory.createEmptyBorder());
        customerSearchField.setOpaque(false);
        customerSearchField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        customerSearchField.setForeground(AppColor.TEXT_PRIMARY);
        customerSearchField.setCaretColor(AppColor.TEXT_PRIMARY);

        FontIcon sIcon = FontIcon.of(FontAwesomeSolid.SEARCH, 12);
        sIcon.setIconColor(AppColor.TEXT_MUTED);
        JLabel searchIconLbl = new JLabel(sIcon);
        searchIconLbl.setBorder(new EmptyBorder(0, 2, 0, 8));

        searchBox.add(searchIconLbl, BorderLayout.WEST);
        searchBox.add(customerSearchField, BorderLayout.CENTER);
        searchWrap.add(searchBox, BorderLayout.CENTER);

        // ====== Danh sách khách hàng ======
        customerList.setCellRenderer(new CustomerCellRenderer());
        customerList.setBackground(AppColor.PAGE_BG);
        customerList.setBorder(new EmptyBorder(2, 4, 4, 4));
        customerList.setFixedCellHeight(68);
        customerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        customerList.setSelectionBackground(AppColor.ACCENT_SELECTION_BG);
        customerList.setSelectionForeground(AppColor.TEXT_PRIMARY);
        customerList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Integer userId = customerList.getSelectedValue();
                if (userId != null) selectCustomer(userId);
            }
        });

        JScrollPane listScroll = new JScrollPane(customerList);
        listScroll.setBorder(null);
        listScroll.getViewport().setBackground(AppColor.PAGE_BG);

        JPanel north = new JPanel(new BorderLayout());
        north.setOpaque(false);
        north.add(title, BorderLayout.NORTH);
        north.add(searchWrap, BorderLayout.CENTER);

        card.add(north, BorderLayout.NORTH);
        card.add(listScroll, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildStaffListCard() {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(AppColor.PAGE_BG);

        // ====== Tiêu đề ======
        JLabel title = new JLabel("Đồng nghiệp");
        title.setFont(new Font("Segoe UI", Font.BOLD, 14));
        title.setForeground(AppColor.TEXT_PRIMARY);
        title.setBorder(new EmptyBorder(14, 16, 10, 16));

        // ====== Ô tìm kiếm nhân viên ======
        JPanel searchWrap = new JPanel(new BorderLayout());
        searchWrap.setOpaque(false);
        searchWrap.setBorder(new EmptyBorder(0, 12, 10, 12));

        JPanel searchBox = new JPanel(new BorderLayout());
        searchBox.setBackground(new Color(0, 0, 0, 0));
        searchBox.setOpaque(false);
        searchBox.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(6, 10, 6, 10)));

        staffSearchField.setBorder(BorderFactory.createEmptyBorder());
        staffSearchField.setOpaque(false);
        staffSearchField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        staffSearchField.setForeground(AppColor.TEXT_PRIMARY);
        staffSearchField.setCaretColor(AppColor.TEXT_PRIMARY);

        FontIcon sIcon = FontIcon.of(FontAwesomeSolid.SEARCH, 12);
        sIcon.setIconColor(AppColor.TEXT_MUTED);
        JLabel searchIconLbl = new JLabel(sIcon);
        searchIconLbl.setBorder(new EmptyBorder(0, 2, 0, 8));

        searchBox.add(searchIconLbl, BorderLayout.WEST);
        searchBox.add(staffSearchField, BorderLayout.CENTER);
        searchWrap.add(searchBox, BorderLayout.CENTER);

        // ====== Danh sách nhân viên ======
        staffList.setCellRenderer(new StaffCellRenderer());
        staffList.setBackground(AppColor.PAGE_BG);
        staffList.setBorder(new EmptyBorder(2, 4, 4, 4));
        staffList.setFixedCellHeight(68);
        staffList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        staffList.setSelectionBackground(AppColor.ACCENT_SELECTION_BG);
        staffList.setSelectionForeground(AppColor.TEXT_PRIMARY);
        staffList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Integer userId = staffList.getSelectedValue();
                if (userId != null) selectStaff(userId);
            }
        });

        JScrollPane listScroll = new JScrollPane(staffList);
        listScroll.setBorder(null);
        listScroll.getViewport().setBackground(AppColor.PAGE_BG);

        JPanel north = new JPanel(new BorderLayout());
        north.setOpaque(false);
        north.add(title, BorderLayout.NORTH);
        north.add(searchWrap, BorderLayout.CENTER);

        card.add(north, BorderLayout.NORTH);
        card.add(listScroll, BorderLayout.CENTER);
        return card;
    }

    // ================================================================
    // ========== 4) LỌC DANH SÁCH THEO TỪ KHÓA (GỌI TRONG refreshXxxListVisual) ==========
    // ================================================================
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
        setInputEnabled(true);
        ensureCustomerHistoryLoaded(userId);
        renderConversation(customerConversations.getOrDefault(userId, new ArrayList<>()), true);
    }

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
        cm.messageId = h.getMessageId();
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
        cm.messageId = h.getMessageId();
        attachHistoryImage(cm, h);
        return cm;
    }

    private void clearCurrentConversationMessages() {
        if (staffTabActive) {
            if (selectedStaffId == null) {
                AppAlert.warning(this, "Chưa chọn", "Hãy chọn một đồng nghiệp trước.");
                return;
            }
            boolean ok = BaseDialog.confirm(this, "Xóa tất cả tin nhắn",
                    "Xóa toàn bộ lịch sử chat nội bộ với người này?\nHành động không thể hoàn tác.");
            if (!ok) return;
            final int peerId = selectedStaffId;
            new SwingWorker<Integer, Void>() {
                @Override
                protected Integer doInBackground() {
                    return ChatHistoryService.getInstance().clearStaffDmHistory(myUserId, peerId);
                }
                @Override
                protected void done() {
                    staffConversations.put(peerId, new ArrayList<>());
                    staffHistoryLoaded.remove(peerId);
                    renderConversation(List.of(), false);
                    AppAlert.success(ChatPanel.this, "Đã xóa", "Đã xóa toàn bộ tin nhắn nội bộ.");
                }
            }.execute();
        } else {
            if (selectedCustomerId == null) {
                AppAlert.warning(this, "Chưa chọn", "Hãy chọn một khách hàng trước.");
                return;
            }
            boolean ok = BaseDialog.confirm(this, "Xóa tất cả tin nhắn",
                    "Xóa toàn bộ lịch sử chat với khách này?\nHành động không thể hoàn tác.");
            if (!ok) return;
            final int customerId = selectedCustomerId;
            new SwingWorker<Integer, Void>() {
                @Override
                protected Integer doInBackground() {
                    return ChatHistoryService.getInstance().clearCustomerHistory(customerId);
                }
                @Override
                protected void done() {
                    customerConversations.put(customerId, new ArrayList<>());
                    customerHistoryLoaded.remove(customerId);
                    renderConversation(List.of(), true);
                    AppAlert.success(ChatPanel.this, "Đã xóa", "Đã xóa toàn bộ tin nhắn với khách.");
                }
            }.execute();
        }
    }

    private void deleteOneMessage(JPanel row, long messageId, ChatMessage linked) {
        boolean ok = BaseDialog.confirm(this, "Xóa tin nhắn", "Bạn có chắc muốn xóa tin nhắn này?");
        if (!ok) return;
        Runnable removeUi = () -> {
            messagesContainer.remove(row);
            if (linked != null) {
                if (staffTabActive && selectedStaffId != null) {
                    List<ChatMessage> list = staffConversations.get(selectedStaffId);
                    if (list != null) list.removeIf(m -> m == linked
                            || (messageId > 0 && m.messageId == messageId));
                } else if (selectedCustomerId != null) {
                    List<ChatMessage> list = customerConversations.get(selectedCustomerId);
                    if (list != null) list.removeIf(m -> m == linked
                            || (messageId > 0 && m.messageId == messageId));
                }
            }
            messagesContainer.revalidate();
            messagesContainer.repaint();
        };
        if (messageId > 0) {
            new SwingWorker<Boolean, Void>() {
                @Override
                protected Boolean doInBackground() {
                    return ChatHistoryService.getInstance().deleteMessage(messageId);
                }
                @Override
                protected void done() {
                    removeUi.run();
                }
            }.execute();
        } else {
            removeUi.run();
        }
    }

    private static long toEpochMillis(java.time.LocalDateTime dt) {
        if (dt == null) return System.currentTimeMillis();
        return dt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

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
        } catch (Exception ignored) { }
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
        voiceMicButton.setEnabled(enabled);
        ttsButton.setEnabled(true);
    }

    private void toggleChatTts() {
        ttsEnabled = !ttsEnabled;
        FontIcon ic = FontIcon.of(
                ttsEnabled ? FontAwesomeSolid.VOLUME_UP : FontAwesomeSolid.VOLUME_MUTE, 16);
        ic.setIconColor(ttsEnabled ? AppColor.ACCENT_HOVER : AppColor.TEXT_MUTED);
        ttsButton.setIcon(ic);
        ttsButton.setToolTipText(ttsEnabled ? "Đang bật đọc to — bấm để tắt" : "Bật đọc to tin nhắn đến");
        if (ttsEnabled) {
            if (lastIncomingText != null && !lastIncomingText.isBlank()) {
                ttsService.speakAsync(lastIncomingText);
            }
        } else {
            ttsService.stop();
        }
    }

    private void speakIncomingIfEnabled(String text) {
        if (text == null || text.isBlank()) return;
        lastIncomingText = text.trim();
        if (ttsEnabled) ttsService.speakAsync(lastIncomingText);
    }

    private JComponent buildVoicePlayControl(String voiceBase64, boolean isMine) {
        return new VoiceMessageBubble(voiceBase64, isMine, this);
    }

    private void toggleVoiceNote() {
        if (voiceSender.isBusy()) return;
        if (staffTabActive) {
            if (selectedStaffId == null) return;
        } else if (selectedCustomerId == null) {
            return;
        }
        if (voiceSender.isRecording()) {
            finishAndSendVoice();
            return;
        }
        try {
            FontIcon stopIcon = FontIcon.of(FontAwesomeSolid.STOP, 16);
            stopIcon.setIconColor(new Color(220, 53, 69));
            voiceMicButton.setIcon(stopIcon);
            voiceMicButton.setToolTipText("Đang ghi… nghỉ 1–2s sẽ gửi (hoặc bấm dừng)");
            if (voiceLoadingPanel != null) {
                voiceLoadingLabel.setText("Đang nghe… hãy nói");
                voiceLoadingPanel.setVisible(true);
                voiceLoadingBar.setIndeterminate(true);
            }
            voiceSender.start(this::finishAndSendVoice);
            startVoiceLevelMonitor();
        } catch (Exception ex) {
            AppAlert.error(this, "Không mở được microphone.\n" + ex.getMessage());
            resetVoiceMicButton();
        }
    }

    private void setVoiceProcessing(boolean on, String message) {
        if (!on) {
            stopVoiceLevelMonitor();
        } else {
            stopVoiceLevelMonitor();
        }
        if (voiceLoadingPanel != null) {
            if (message != null) voiceLoadingLabel.setText(message);
            voiceLoadingPanel.setVisible(on);
            voiceLoadingBar.setIndeterminate(on);
        }
        if (inputField != null) {
            inputField.setEnabled(!on && (staffTabActive ? selectedStaffId != null : selectedCustomerId != null));
            inputField.putClientProperty("JTextField.placeholderText",
                    on ? (message != null ? message : "Đang xử lý…") : "Nhập tin nhắn...");
            inputField.repaint();
        }
        if (voiceMicButton != null) {
            boolean hasTarget = staffTabActive ? selectedStaffId != null : selectedCustomerId != null;
            voiceMicButton.setEnabled(!on && hasTarget);
        }
        revalidate();
        repaint();
    }

    private void finishAndSendVoice() {
        if (voiceSender.isBusy()) return;
        setVoiceProcessing(true, "Đang nhận dạng & gửi tin thoại…");
        voiceSender.finish((transcript, b64) -> {
            setVoiceProcessing(false, null);
            resetVoiceMicButton();
            if (b64 == null || b64.isBlank()) {
                AppAlert.info(this, "Không ghi được âm thanh.");
                return;
            }
            int dur = voiceSender.lastDurationEstimateMs();
            String label = (transcript != null && !transcript.isBlank()) ? transcript : "[Tin nhắn thoại]";
            if (staffTabActive) {
                if (selectedStaffId == null) return;
                boolean sent = ChatClient.getInstance().sendStaffVoice(
                        selectedStaffId, transcript, b64, "audio/wav", dur);
                ChatMessage record = ChatMessage.staffVoice(
                        myUserId, myName, selectedStaffId, transcript, b64, "audio/wav", dur);
                staffConversations.computeIfAbsent(selectedStaffId, k -> new ArrayList<>()).add(record);
                addBubble(label, null, "voice.wav", b64, true, TIME_FORMAT.format(new Date()));
                if (!sent) AppAlert.warning(this, "Gửi thoại nội bộ thất bại.");
            } else {
                if (selectedCustomerId == null) return;
                ChatServer.getInstance().sendVoiceToCustomer(
                        selectedCustomerId, myName, transcript, b64, "audio/wav", dur, myUserId);
                ChatMessage record = ChatMessage.voiceFromAdmin(
                        selectedCustomerId, myName, transcript, b64, "audio/wav", dur);
                customerConversations.computeIfAbsent(selectedCustomerId, k -> new ArrayList<>()).add(record);
                addBubble(label, null, "voice.wav", b64, true, TIME_FORMAT.format(new Date()));
            }
        });
    }

    private void startVoiceLevelMonitor() {
        stopVoiceLevelMonitor();
        soundWave.start();
        voiceLevelTimer = new javax.swing.Timer(50, e -> {
            if (!voiceSender.isRecording()) return;
            soundWave.setLevel(voiceSender.getLastRms());
        });
        voiceLevelTimer.start();
    }

    private void stopVoiceLevelMonitor() {
        if (voiceLevelTimer != null) {
            voiceLevelTimer.stop();
            voiceLevelTimer = null;
        }
        soundWave.stop();
    }

    private void resetVoiceMicButton() {
        FontIcon mic = FontIcon.of(FontAwesomeSolid.MICROPHONE, 16);
        mic.setIconColor(AppColor.TEXT_MUTED);
        voiceMicButton.setIcon(mic);
        voiceMicButton.setToolTipText("Tin nhắn thoại");
    }

    private void sendCurrent() {
        if (staffTabActive) sendStaffReply();
        else sendCustomerReply();
    }

    private void sendCustomerReply() {
        if (selectedCustomerId == null) return;
        String text = inputField.getText() == null ? "" : inputField.getText().trim();
        if (text.isEmpty()) return;
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

    private void pickAndSendAttachment() {
        File file = ChatFileUtil.chooseAttachment(this);
        if (file == null) return;
        if (!ChatFileUtil.isSupportedFile(file)) {
            JOptionPane.showMessageDialog(this, "Định dạng file không được hỗ trợ.",
                    "Không hỗ trợ", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (file.length() > ChatFileUtil.MAX_BYTES) {
            JOptionPane.showMessageDialog(this,
                    "File quá lớn (tối đa " + (ChatFileUtil.MAX_BYTES / 1_000_000) + " MB).",
                    "Quá dung lượng", JOptionPane.WARNING_MESSAGE);
            return;
        }
        final boolean forStaff = staffTabActive;
        final Integer targetId = forStaff ? selectedStaffId : selectedCustomerId;
        if (targetId == null) return;
        final boolean asImage = ChatFileUtil.isImageExtension(file.getName())
                && ChatImageUtil.isSupportedImage(file);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        imageButton.setEnabled(false);
        sendButton.setEnabled(false);
        new SwingWorker<Object, Void>() {
            @Override
            protected Object doInBackground() {
                if (asImage) return ChatImageUtil.encodeForChat(file);
                return ChatFileUtil.encodeForChat(file);
            }
            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                imageButton.setEnabled(true);
                sendButton.setEnabled(true);
                Object encoded;
                try {
                    encoded = get();
                } catch (Exception ex) {
                    encoded = null;
                }
                if (encoded == null) {
                    JOptionPane.showMessageDialog(ChatPanel.this,
                            asImage ? "Không đọc được ảnh." : "Không đọc được file.",
                            "Lỗi", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                String caption = inputField.getText() == null ? "" : inputField.getText().trim();
                boolean sent;
                ChatMessage record;
                BufferedImage preview = null;
                if (asImage) {
                    ChatImageUtil.EncodedImage img = (ChatImageUtil.EncodedImage) encoded;
                    if (forStaff) {
                        sent = ChatClient.getInstance().sendStaffImage(
                                targetId, caption.isEmpty() ? null : caption, img.base64, img.mime);
                        record = ChatMessage.staffImage(myUserId, myName, targetId,
                                caption.isEmpty() ? null : caption, img.base64, img.mime);
                        staffConversations.computeIfAbsent(targetId, k -> new ArrayList<>()).add(record);
                    } else {
                        sent = ChatServer.getInstance().sendImageToCustomer(
                                targetId, myName, caption.isEmpty() ? null : caption, img.base64, img.mime, myUserId);
                        record = ChatMessage.imageFromAdmin(targetId, myName,
                                caption.isEmpty() ? null : caption, img.base64, img.mime);
                        customerConversations.computeIfAbsent(targetId, k -> new ArrayList<>()).add(record);
                    }
                    preview = ChatImageUtil.decodeBase64(img.base64);
                    if ((forStaff && selectedStaffId != null && selectedStaffId.equals(targetId))
                            || (!forStaff && selectedCustomerId != null && selectedCustomerId.equals(targetId))) {
                        addBubble(caption.isEmpty() ? null : caption, preview, null, null, true,
                                TIME_FORMAT.format(new Date(record.timestamp)));
                    }
                } else {
                    ChatFileUtil.EncodedFile f = (ChatFileUtil.EncodedFile) encoded;
                    if (forStaff) {
                        sent = ChatClient.getInstance().sendStaffFile(
                                targetId, caption.isEmpty() ? null : caption, f.base64, f.fileName, f.mime);
                        record = ChatMessage.staffFile(myUserId, myName, targetId,
                                caption.isEmpty() ? null : caption, f.base64, f.fileName, f.mime);
                        staffConversations.computeIfAbsent(targetId, k -> new ArrayList<>()).add(record);
                    } else {
                        sent = ChatServer.getInstance().sendFileToCustomer(
                                targetId, myName, caption.isEmpty() ? null : caption,
                                f.base64, f.fileName, f.mime, myUserId);
                        record = ChatMessage.fileFromAdmin(targetId, myName,
                                caption.isEmpty() ? null : caption, f.base64, f.fileName, f.mime);
                        customerConversations.computeIfAbsent(targetId, k -> new ArrayList<>()).add(record);
                    }
                    if ((forStaff && selectedStaffId != null && selectedStaffId.equals(targetId))
                            || (!forStaff && selectedCustomerId != null && selectedCustomerId.equals(targetId))) {
                        addBubble(caption.isEmpty() ? null : caption, null, f.fileName, f.base64, true,
                                TIME_FORMAT.format(new Date(record.timestamp)));
                    }
                }
                inputField.setText("");
                if (forStaff && !sent) {
                    JOptionPane.showMessageDialog(ChatPanel.this,
                            "Không gửi được (đồng nghiệp có thể đang offline).",
                            "Gửi thất bại", JOptionPane.WARNING_MESSAGE);
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
                addBubble(message.text, image, message.hasFile() ? message.fileName : null, message.hasFile() ? message.fileBase64 : null, false, TIME_FORMAT.format(new Date(message.timestamp)));
                if (message.text != null && !message.text.isBlank()) {
                    speakIncomingIfEnabled(message.text);
                }
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
        if (message.userId == myUserId) return;
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
            addBubble(message.text, image, message.hasFile() ? message.fileName : null, message.hasFile() ? message.fileBase64 : null, false, TIME_FORMAT.format(new Date(message.timestamp)));
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

    // ================================================================
    // ====== refreshCustomerListVisual: ĐÃ THÊM BỘ LỌC THEO TÊN ======
    // ================================================================
    private void refreshCustomerListVisual() {
        Integer prev = selectedCustomerId;
        List<Integer> ordered = new ArrayList<>();
        for (Integer id : onlineCustomers.keySet()) ordered.add(id);
        for (Integer id : knownCustomerIds) {
            if (!onlineCustomers.containsKey(id)) ordered.add(id);
        }

        // ====== LỌC: chỉ giữ những người có tên chứa từ khóa ======
        String q = customerFilterText == null ? "" : customerFilterText.trim().toLowerCase();
        if (!q.isEmpty()) {
            ordered = ordered.stream()
                    .filter(id -> customerDisplayName(id).toLowerCase().contains(q))
                    .collect(Collectors.toList());
        }

        customerListModel.clear();
        for (Integer id : ordered) customerListModel.addElement(id);
        if (prev != null && ordered.contains(prev)) customerList.setSelectedValue(prev, false);
    }

    // ================================================================
    // ====== refreshStaffListVisual: ĐÃ THÊM BỘ LỌC THEO TÊN ======
    // ================================================================
    private void refreshStaffListVisual() {
        Integer prev = selectedStaffId;
        List<Integer> ordered = new ArrayList<>();
        for (Integer id : staffDirectory.keySet()) if (onlineStaffIds.contains(id)) ordered.add(id);
        for (Integer id : staffDirectory.keySet()) if (!onlineStaffIds.contains(id)) ordered.add(id);
        for (Integer id : onlineStaffIds) {
            if (!staffDirectory.containsKey(id) && id != myUserId) ordered.add(id);
        }

        // ====== LỌC: chỉ giữ những nhân viên có tên chứa từ khóa ======
        String q = staffFilterText == null ? "" : staffFilterText.trim().toLowerCase();
        if (!q.isEmpty()) {
            ordered = ordered.stream()
                    .filter(id -> {
                        User u = staffDirectory.get(id);
                        String name = u != null ? displayName(u) : ("Nhân viên #" + id);
                        return name.toLowerCase().contains(q);
                    })
                    .collect(Collectors.toList());
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

    public void setOnUnreadCountChanged(Consumer<Integer> callback) {
        this.onUnreadCountChanged = callback;
        notifyUnreadCountChanged();
    }

    public void setOnUnreadNotifications(Consumer<List<com.model.NotificationItem>> callback) {
        this.onUnreadNotifications = callback;
        notifyUnreadCountChanged();
    }

    public void markNotificationRead(String notificationId) {
        if (notificationId == null) return;
        if (notificationId.startsWith("chat-c-")) {
            try {
                clearCustomerUnread(Integer.parseInt(notificationId.substring("chat-c-".length())));
            } catch (NumberFormatException e) {
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
        for (Integer id : new ArrayList<>(customerUnread.keySet())) customerUnread.put(id, false);
        for (Integer id : new ArrayList<>(staffUnread.keySet())) staffUnread.put(id, false);
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
                addBubbleSilently(message.text, image,
                        message.hasFile() ? message.fileName : null,
                        message.hasFile() ? message.fileBase64 : null,
                        mine, TIME_FORMAT.format(new Date(message.timestamp)),
                        message.messageId, message);
            }
        }
        messagesContainer.revalidate();
        messagesContainer.repaint();
        scrollToBottom();
    }

    private void addBubble(String text, BufferedImage image, boolean isMine, String time) {
        addBubble(text, image, null, null, isMine, time, 0L, null);
    }

    private void addBubble(String text, BufferedImage image, String fileName, String fileBase64,
                           boolean isMine, String time) {
        addBubble(text, image, fileName, fileBase64, isMine, time, 0L, null);
    }

    private void addBubble(String text, BufferedImage image, String fileName, String fileBase64,
                           boolean isMine, String time, long messageId, ChatMessage linked) {
        addBubbleSilently(text, image, fileName, fileBase64, isMine, time, messageId, linked);
        messagesContainer.revalidate();
        messagesContainer.repaint();
        scrollToBottom();
    }

    private void addBubbleSilently(String text, BufferedImage image, boolean isMine, String time) {
        addBubbleSilently(text, image, null, null, isMine, time, 0L, null);
    }

    private void addBubbleSilently(String text, BufferedImage image, String fileName, String fileBase64,
                                  boolean isMine, String time) {
        addBubbleSilently(text, image, fileName, fileBase64, isMine, time, 0L, null);
    }

    private void addBubbleSilently(String text, BufferedImage image, String fileName, String fileBase64,
                                  boolean isMine, String time, long messageId, ChatMessage linked) {
        int viewportW = scrollPane.getViewport().getWidth();
        if (viewportW <= 0) viewportW = 480;
        int maxBubbleW = Math.max(200, Math.min(360, viewportW - 48));
        int htmlW = Math.max(140, maxBubbleW - 40);
        JPanel row = new JPanel(new FlowLayout(isMine ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 6));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.putClientProperty("messageId", messageId);
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
        if (fileName != null && !fileName.isBlank()) {
            boolean isVoiceFile = "voice.wav".equalsIgnoreCase(fileName)
                    || fileName.toLowerCase().endsWith(".wav");
            if (isVoiceFile && fileBase64 != null && !fileBase64.isBlank()) {
                contentWrap.add(buildVoicePlayControl(fileBase64, isMine));
            } else {
                JLabel fileLabel = buildFileAttachmentLabel(fileName, fileBase64, isMine);
                fileLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                contentWrap.add(fileLabel);
            }
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
        FontIcon delIcon = FontIcon.of(FontAwesomeSolid.TIMES, 11);
        delIcon.setIconColor(isMine ? new Color(255, 255, 255, 180) : AppColor.TEXT_MUTED);
        JLabel delBtn = new JLabel(delIcon);
        delBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        delBtn.setToolTipText("Xóa tin nhắn");
        delBtn.setBorder(new EmptyBorder(0, 6, 0, 0));
        delBtn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Object idObj = row.getClientProperty("messageId");
                long mid = idObj instanceof Long ? (Long) idObj : 0L;
                deleteOneMessage(row, mid, linked);
            }
        });
        bubble.add(contentWrap, BorderLayout.CENTER);
        row.add(bubble);
        row.add(delBtn);
        Dimension pref = row.getPreferredSize();
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, pref.height));
        messagesContainer.add(row);
    }

    private JLabel buildFileAttachmentLabel(String fileName, String fileBase64, boolean isMine) {
        JLabel label = new JLabel("📎 " + fileName);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        label.setForeground(isMine ? Color.WHITE : AppColor.ACCENT);
        label.setCursor(new Cursor(Cursor.HAND_CURSOR));
        label.setToolTipText("Nhấp để lưu file");
        label.setBorder(new EmptyBorder(6, 0, 0, 0));
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                saveReceivedFile(fileName, fileBase64);
            }
        });
        return label;
    }

    private void saveReceivedFile(String fileName, String fileBase64) {
        FileDownloadUI.saveBase64WithProgress(this, fileName, fileBase64);
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


    // ================================================================
    // ========== HELPER: Avatar + màu sắc cho danh sách chat ==========
    // ================================================================
    private static final Color[] AVATAR_COLORS = {
            new Color(0x2E7D32), new Color(0x1565C0), new Color(0x6A1B9A),
            new Color(0xC62828), new Color(0xE65100), new Color(0x00838F),
            new Color(0x4E342E), new Color(0x37474F)
    };

    private static Color avatarColorFor(int userId) {
        return AVATAR_COLORS[Math.abs(userId) % AVATAR_COLORS.length];
    }

    private static String initialOf(String name) {
        if (name == null || name.isBlank()) return "?";
        String trimmed = name.trim();
        String[] parts = trimmed.split("\s+");
        String lastName = parts[parts.length - 1];
        return lastName.substring(0, 1).toUpperCase();
    }

    private String formatChatTime(long timestamp) {
        if (timestamp <= 0) return "";
        Date date = new Date(timestamp);
        java.util.Calendar now = java.util.Calendar.getInstance();
        java.util.Calendar msgTime = java.util.Calendar.getInstance();
        msgTime.setTime(date);

        boolean sameDay = now.get(java.util.Calendar.YEAR) == msgTime.get(java.util.Calendar.YEAR)
                && now.get(java.util.Calendar.DAY_OF_YEAR) == msgTime.get(java.util.Calendar.DAY_OF_YEAR);

        if (sameDay) {
            return new SimpleDateFormat("HH:mm").format(date);
        }

        long diffDays = (now.getTimeInMillis() - msgTime.getTimeInMillis()) / (1000L * 60 * 60 * 24);
        if (diffDays == 1) return "Hôm qua";
        if (diffDays < 7) {
            String[] weekdays = {"CN", "T2", "T3", "T4", "T5", "T6", "T7"};
            return weekdays[msgTime.get(java.util.Calendar.DAY_OF_WEEK) - 1];
        }
        return new SimpleDateFormat("dd/MM").format(date);
    }

    private static String truncatePreview(String text, int maxLen) {
        if (text == null || text.isBlank()) return "Chưa có tin nhắn";
        String clean = text.replaceAll("\s+", " ").trim();
        if (clean.length() <= maxLen) return clean;
        return clean.substring(0, maxLen - 3) + "...";
    }

    /**
     * Avatar label tùy chỉnh: hình tròn với chữ cái đầu tên và nền màu theo user ID.
     */
    private class AvatarLabel extends JLabel {
        private int userId = 0;
        private final int size;

        AvatarLabel(int size) {
            this.size = size;
            setFont(new Font("Segoe UI", Font.BOLD, size >= 40 ? 16 : 14));
            setForeground(Color.WHITE);
            setHorizontalAlignment(SwingConstants.CENTER);
            setVerticalAlignment(SwingConstants.CENTER);
            setOpaque(false);
            Dimension dim = new Dimension(size, size);
            setPreferredSize(dim);
            setMinimumSize(dim);
            setMaximumSize(dim);
        }

        void updateFor(String name, int userId) {
            this.userId = userId;
            setText(initialOf(name));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int diameter = Math.min(getWidth(), getHeight());
            int x = (getWidth() - diameter) / 2;
            int y = (getHeight() - diameter) / 2;

            g2.setColor(avatarColorFor(userId));
            g2.fillOval(x, y, diameter, diameter);

            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Badge hình tròn hiển thị số tin chưa đọc */
    private class UnreadBadge extends JLabel {
        UnreadBadge(int count) {
            super(String.valueOf(count));
            setFont(new Font("Segoe UI", Font.BOLD, 11));
            setForeground(Color.WHITE);
            setHorizontalAlignment(SwingConstants.CENTER);
            setVerticalAlignment(SwingConstants.CENTER);
            setOpaque(false);
            int size = count >= 10 ? 20 : 18;
            Dimension dim = new Dimension(size, size);
            setPreferredSize(dim);
            setMinimumSize(dim);
            setMaximumSize(dim);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int diameter = Math.min(w, h);
            int x = (w - diameter) / 2;
            int y = (h - diameter) / 2;

            g2.setColor(AppColor.ERROR);
            g2.fillOval(x, y, diameter, diameter);

            g2.dispose();
            super.paintComponent(g);
        }
    }

    private class CustomerCellRenderer extends JPanel implements ListCellRenderer<Integer> {
        private final AvatarLabel avatarLabel;
        private final JLabel onlineDot;
        private final JLabel nameLabel;
        private final JLabel previewLabel;
        private final JLabel timeLabel;
        private final JPanel badgePanel;

        CustomerCellRenderer() {
            setLayout(new BorderLayout(10, 0));
            setBorder(new EmptyBorder(8, 8, 8, 8));

            // ====== Cột trái: Avatar + chấm trạng thái ======
            JPanel avatarWrap = new JPanel(null) {
                @Override public Dimension getPreferredSize() { return new Dimension(44, 44); }
                @Override public Dimension getMinimumSize() { return new Dimension(44, 44); }
                @Override public Dimension getMaximumSize() { return new Dimension(44, 44); }
            };
            avatarWrap.setOpaque(false);

            avatarLabel = new AvatarLabel(44);
            avatarLabel.setBounds(0, 0, 44, 44);

            onlineDot = new JLabel("●");
            onlineDot.setFont(new Font("Segoe UI", Font.BOLD, 12));
            onlineDot.setBounds(32, 30, 14, 14);

            avatarWrap.add(avatarLabel);
            avatarWrap.add(onlineDot);

            // ====== Cột giữa: Tên + Preview tin nhắn ======
            JPanel centerCol = new JPanel();
            centerCol.setOpaque(false);
            centerCol.setLayout(new BoxLayout(centerCol, BoxLayout.Y_AXIS));

            JPanel nameRow = new JPanel(new BorderLayout(4, 0));
            nameRow.setOpaque(false);

            nameLabel = new JLabel();
            nameLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));

            timeLabel = new JLabel();
            timeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            timeLabel.setForeground(AppColor.TEXT_MUTED);

            nameRow.add(nameLabel, BorderLayout.WEST);
            nameRow.add(timeLabel, BorderLayout.EAST);

            previewLabel = new JLabel();
            previewLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            previewLabel.setForeground(AppColor.TEXT_MUTED);

            centerCol.add(Box.createVerticalStrut(2));
            centerCol.add(nameRow);
            centerCol.add(Box.createVerticalStrut(3));
            centerCol.add(previewLabel);
            centerCol.add(Box.createVerticalGlue());

            // ====== Cột phải: Badge chưa đọc ======
            badgePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            badgePanel.setOpaque(false);

            // ====== Layout chính ======
            add(avatarWrap, BorderLayout.WEST);
            add(centerCol, BorderLayout.CENTER);
            add(badgePanel, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Integer> list, Integer userId,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            String name = customerDisplayName(userId);
            boolean online = onlineCustomers.containsKey(userId);
            boolean unread = Boolean.TRUE.equals(customerUnread.get(userId));
            String preview = customerLastPreview.get(userId);
            Long lastTime = customerLastTime.get(userId);

            // Cập nhật avatar
            avatarLabel.updateFor(name, userId);

            // Trạng thái online
            onlineDot.setForeground(online ? AppColor.GREEN : AppColor.TEXT_MUTED_ALT);

            // Tên người dùng (in đậm nếu có tin chưa đọc)
            nameLabel.setText(name);
            nameLabel.setForeground(AppColor.TEXT_PRIMARY);
            nameLabel.setFont(unread
                    ? new Font("Segoe UI", Font.BOLD, 13)
                    : new Font("Segoe UI", Font.PLAIN, 13));

            // Preview tin nhắn cuối
            previewLabel.setText(truncatePreview(preview, 32));
            previewLabel.setForeground(unread ? AppColor.TEXT_SECONDARY : AppColor.TEXT_MUTED);

            // Thời gian tin nhắn cuối
            timeLabel.setText(lastTime != null ? formatChatTime(lastTime) : "");

            // Badge chưa đọc
            badgePanel.removeAll();
            if (unread) {
                badgePanel.add(new UnreadBadge(1));
            }
            badgePanel.revalidate();

            // Nền: được chọn / dùng màu nền của JList (đồng bộ theme)
            setBackground(isSelected ? AppColor.ACCENT_SELECTION_BG : list.getBackground());
            setOpaque(true);

            return this;
        }
    }

    private class StaffCellRenderer extends JPanel implements ListCellRenderer<Integer> {
        private final AvatarLabel avatarLabel;
        private final JLabel onlineDot;
        private final JLabel nameLabel;
        private final JLabel roleLabel;
        private final JLabel previewLabel;
        private final JLabel timeLabel;
        private final JPanel badgePanel;

        StaffCellRenderer() {
            setLayout(new BorderLayout(10, 0));
            setBorder(new EmptyBorder(8, 8, 8, 8));

            // ====== Cột trái: Avatar + chấm trạng thái ======
            JPanel avatarWrap = new JPanel(null) {
                @Override public Dimension getPreferredSize() { return new Dimension(44, 44); }
                @Override public Dimension getMinimumSize() { return new Dimension(44, 44); }
                @Override public Dimension getMaximumSize() { return new Dimension(44, 44); }
            };
            avatarWrap.setOpaque(false);

            avatarLabel = new AvatarLabel(44);
            avatarLabel.setBounds(0, 0, 44, 44);

            onlineDot = new JLabel("●");
            onlineDot.setFont(new Font("Segoe UI", Font.BOLD, 12));
            onlineDot.setBounds(32, 30, 14, 14);

            avatarWrap.add(avatarLabel);
            avatarWrap.add(onlineDot);

            // ====== Cột giữa: Tên + Chức vụ + Preview ======
            JPanel centerCol = new JPanel();
            centerCol.setOpaque(false);
            centerCol.setLayout(new BoxLayout(centerCol, BoxLayout.Y_AXIS));

            JPanel nameRow = new JPanel(new BorderLayout(4, 0));
            nameRow.setOpaque(false);

            nameLabel = new JLabel();
            nameLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));

            timeLabel = new JLabel();
            timeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            timeLabel.setForeground(AppColor.TEXT_MUTED);

            nameRow.add(nameLabel, BorderLayout.WEST);
            nameRow.add(timeLabel, BorderLayout.EAST);

            roleLabel = new JLabel();
            roleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            roleLabel.setForeground(AppColor.TEXT_MUTED);

            previewLabel = new JLabel();
            previewLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            previewLabel.setForeground(AppColor.TEXT_MUTED);

            centerCol.add(Box.createVerticalStrut(1));
            centerCol.add(nameRow);
            centerCol.add(Box.createVerticalStrut(1));
            centerCol.add(roleLabel);
            centerCol.add(Box.createVerticalStrut(2));
            centerCol.add(previewLabel);
            centerCol.add(Box.createVerticalGlue());

            // ====== Cột phải: Badge chưa đọc ======
            badgePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            badgePanel.setOpaque(false);

            // ====== Layout chính ======
            add(avatarWrap, BorderLayout.WEST);
            add(centerCol, BorderLayout.CENTER);
            add(badgePanel, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Integer> list, Integer userId,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            User u = staffDirectory.get(userId);
            String name = u != null ? displayName(u) : ("Nhân viên #" + userId);
            String role = u != null && u.getRole() != null ? roleLabel(u.getRole()) : "";
            boolean online = onlineStaffIds.contains(userId);
            boolean unread = Boolean.TRUE.equals(staffUnread.get(userId));
            String preview = staffLastPreview.get(userId);
            Long lastTime = staffLastTime.get(userId);

            // Cập nhật avatar
            avatarLabel.updateFor(name, userId);

            // Trạng thái online
            onlineDot.setForeground(online ? AppColor.GREEN : AppColor.TEXT_MUTED_ALT);

            // Tên người dùng (in đậm nếu có tin chưa đọc)
            nameLabel.setText(name);
            nameLabel.setForeground(AppColor.TEXT_PRIMARY);
            nameLabel.setFont(unread
                    ? new Font("Segoe UI", Font.BOLD, 13)
                    : new Font("Segoe UI", Font.PLAIN, 13));

            // Chức vụ
            roleLabel.setText(role);

            // Preview tin nhắn cuối
            previewLabel.setText(truncatePreview(preview, 32));
            previewLabel.setForeground(unread ? AppColor.TEXT_SECONDARY : AppColor.TEXT_MUTED);

            // Thời gian
            timeLabel.setText(lastTime != null ? formatChatTime(lastTime) : "");

            // Badge chưa đọc
            badgePanel.removeAll();
            if (unread) {
                badgePanel.add(new UnreadBadge(1));
            }
            badgePanel.revalidate();

            // Nền: được chọn / dùng màu nền của JList (đồng bộ theme)
            setBackground(isSelected ? AppColor.ACCENT_SELECTION_BG : list.getBackground());
            setOpaque(true);

            return this;
        }
    }
}