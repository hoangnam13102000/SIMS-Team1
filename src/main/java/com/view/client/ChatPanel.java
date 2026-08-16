package com.view.client;

import com.components.AppAlert;
import com.components.BaseDialog;
import com.theme.AppColor;
import com.service.AuthService;
import com.utils.FileUtil;
import com.utils.FileDownloadUI;
import com.ws.ChatClient;
import com.ws.ChatImageUtil;
import com.ws.ChatFileUtil;
import com.model.chat.ChatHistoryMessage;
import com.service.ChatHistoryService;
import com.utils.ImageUtil;
import com.ws.ChatMessage;
import com.service.media.CloudinaryService;
import com.service.media.CloudinaryUploadException;
import com.ws.VoiceNotePlayer;
import com.ws.VoiceNoteSender;
import com.components.common.SoundWaveIcon;
import com.components.common.VoiceMessageBubble;
import com.service.ai.voice.TextToSpeechService;

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
import java.util.Date;
import java.util.function.Consumer;

/** Noi dung khung chat "Ho tro truc tuyen" ben phia khach hang. */
public class ChatPanel extends JPanel {

    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm");
    /** Chieu rong toi da anh trong bubble (px). */
    private static final int IMAGE_MAX_W = 200;

    private final JPanel messagesContainer;
    private final JScrollPane scrollPane;
    private final JTextField inputField;
    private final JButton voiceMicButton;
    private final JButton ttsButton;
    private final VoiceNoteSender voiceSender = new VoiceNoteSender();
    private final SoundWaveIcon soundWave = new SoundWaveIcon();
    private javax.swing.Timer voiceLevelTimer;
    private final TextToSpeechService ttsService = new TextToSpeechService();
    private boolean ttsEnabled;
    private String lastIncomingText;
    private JProgressBar voiceLoadingBar;
    private JLabel voiceLoadingLabel;
    private JPanel voiceLoadingPanel;
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
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getViewport().setBackground(AppColor.WHITE);

        JPanel inputBar = new JPanel(new BorderLayout(8, 0));
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

        JButton imageButton = buildIconButton(FontAwesomeSolid.PAPERCLIP, "Gửi ảnh / file");
        imageButton.addActionListener(e -> pickAndSendAttachment());

        voiceMicButton = buildIconButton(FontAwesomeSolid.MICROPHONE, "Tin nhắn thoại");
        voiceMicButton.addActionListener(e -> toggleVoiceNote());

        ttsEnabled = false;
        lastIncomingText = null;
        ttsButton = buildIconButton(FontAwesomeSolid.VOLUME_UP, "Bật đọc to tin nhắn đến");
        ttsButton.addActionListener(e -> toggleChatTts());

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
        southWrap.add(inputBar, BorderLayout.CENTER);

        add(headerBar, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(southWrap, BorderLayout.SOUTH);

        addWelcomeBubble();
        loadPersistedHistory();

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
                icon.setIconColor(AppColor.ACCENT_HOVER);
                btn.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                icon.setIconColor(AppColor.TEXT_MUTED);
                btn.repaint();
            }
        });
        return btn;
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

        JLabel clearAllButton = new JLabel(iconOf(FontAwesomeSolid.TRASH_ALT, 13, AppColor.TEXT_MUTED));
        clearAllButton.setBorder(new EmptyBorder(4, 8, 4, 4));
        clearAllButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        clearAllButton.setToolTipText("Xóa tất cả tin nhắn");
        clearAllButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                clearAllMessages();
            }
        });

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
        right.add(clearAllButton);
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


    /**
     * Nạp lịch sử chat khách ↔ hỗ trợ từ DB (không gồm chatbot AI).
     * Chạy nền để không block UI.
     */
    private void loadPersistedHistory() {
        if (!AuthService.getInstance().isLoggedIn()) return;
        final int customerUserId = AuthService.getInstance().getCurrentUser().getUserId();
        new javax.swing.SwingWorker<java.util.List<ChatHistoryMessage>, Void>() {
            @Override
            protected java.util.List<ChatHistoryMessage> doInBackground() {
                return ChatHistoryService.getInstance().loadCustomerHistory(customerUserId, 150);
            }

            @Override
            protected void done() {
                try {
                    java.util.List<ChatHistoryMessage> rows = get();
                    if (rows == null || rows.isEmpty()) return;
                    // Xóa bubble chào nếu đã có lịch sử thật
                    messagesContainer.removeAll();
                    for (ChatHistoryMessage h : rows) {
                        boolean mine = h.getSenderUserId() == customerUserId;
                        java.awt.image.BufferedImage image = null;
                        if (h.hasImage()) {
                            image = ImageUtil.readSafe(h.getImagePath());
                        }
                        String fileName = null;
                        String fileB64 = null;
                        if (h.hasFile()) {
                            fileName = h.getFileName() != null ? h.getFileName() : "file";
                            try {
                                byte[] bytes = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(h.getFilePath()));
                                fileB64 = java.util.Base64.getEncoder().encodeToString(bytes);
                            } catch (Exception ignored) {}
                        }
                        String time = h.getCreatedAt() != null
                                ? TIME_FORMAT.format(java.sql.Timestamp.valueOf(h.getCreatedAt()))
                                : TIME_FORMAT.format(new java.util.Date());
                        addBubble(h.getBodyText(), image, fileName, fileB64, null, mine, time, h.getMessageId());
                    }
                    messagesContainer.revalidate();
                    messagesContainer.repaint();
                } catch (Exception e) {
                    com.core.log.AppLogger.getInstance().error(
                            com.core.log.ErrorCode.DB_QUERY_FAIL,
                            "Client ChatPanel.loadPersistedHistory", e);
                }
            }
        }.execute();
    }

    private void clearAllMessages() {
        if (!AuthService.getInstance().isLoggedIn()) {
            messagesContainer.removeAll();
            addWelcomeBubble();
            messagesContainer.revalidate();
            messagesContainer.repaint();
            return;
        }
        boolean ok = BaseDialog.confirm(this, "Xóa tất cả tin nhắn",
                "Bạn có chắc muốn xóa toàn bộ lịch sử chat hỗ trợ?\nHành động này không thể hoàn tác.");
        if (!ok) return;
        final int customerUserId = AuthService.getInstance().getCurrentUser().getUserId();
        new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() {
                return ChatHistoryService.getInstance().clearCustomerHistory(customerUserId);
            }

            @Override
            protected void done() {
                messagesContainer.removeAll();
                addWelcomeBubble();
                messagesContainer.revalidate();
                messagesContainer.repaint();
                AppAlert.success(ChatPanel.this, "Đã xóa", "Đã xóa toàn bộ tin nhắn trong cuộc trò chuyện này.");
            }
        }.execute();
    }

    private void deleteOneMessage(JPanel row, long messageId) {
        boolean ok = BaseDialog.confirm(this, "Xóa tin nhắn",
                "Bạn có chắc muốn xóa tin nhắn này?");
        if (!ok) return;
        if (messageId > 0) {
            new SwingWorker<Boolean, Void>() {
                @Override
                protected Boolean doInBackground() {
                    return ChatHistoryService.getInstance().deleteMessage(messageId);
                }

                @Override
                protected void done() {
                    messagesContainer.remove(row);
                    messagesContainer.revalidate();
                    messagesContainer.repaint();
                }
            }.execute();
        } else {
            messagesContainer.remove(row);
            messagesContainer.revalidate();
            messagesContainer.repaint();
        }
    }

    private void addWelcomeBubble() {
        addBubble("Xin chào! Bạn cần hỗ trợ gì, hãy nhắn cho chúng tôi nhé.",
                null, null, null, null, false, TIME_FORMAT.format(new Date()), 0L);
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


    /** Khung phát tin thoại kiểu hiện đại: nút play tròn + waveform + thời lượng. */
    private JComponent buildVoicePlayControl(String voiceBase64, boolean isMine) {
        return new VoiceMessageBubble(voiceBase64, isMine, this);
    }

    private void toggleVoiceNote() {
        if (voiceSender.isBusy()) return;
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
            // đang processing: dừng animate theo mic
            stopVoiceLevelMonitor();
        }
        if (voiceLoadingPanel != null) {
            if (message != null) voiceLoadingLabel.setText(message);
            voiceLoadingPanel.setVisible(on);
            voiceLoadingBar.setIndeterminate(on);
        }
        if (inputField != null) {
            inputField.setEnabled(!on);
            inputField.putClientProperty("JTextField.placeholderText",
                    on ? (message != null ? message : "Đang xử lý…") : "Nhập câu hỏi của bạn...");
            inputField.repaint();
        }
        if (voiceMicButton != null) voiceMicButton.setEnabled(!on);
        if (ttsButton != null && on) { /* keep tts */ }
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
            boolean sent = ChatClient.getInstance().sendVoice(transcript, b64, "audio/wav", dur);
            String label = (transcript != null && !transcript.isBlank()) ? transcript : "[Tin nhắn thoại]";
            addBubble(label, null, "voice.wav", b64, null, true, TIME_FORMAT.format(new Date()), 0L);
            if (!sent) {
                addBubble("Không thể gửi thoại: hỗ trợ chưa trực tuyến.",
                        null, false, TIME_FORMAT.format(new Date()), 0L);
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

    private void sendCurrentInput() {
        String text = inputField.getText() == null ? "" : inputField.getText().trim();
        if (text.isEmpty()) return;

        boolean sent = ChatClient.getInstance().sendMessage(text);
        addBubble(text, null, true, TIME_FORMAT.format(new Date()), 0L);
        inputField.setText("");

        if (!sent) {
            addBubble("Không thể gửi: bộ phận hỗ trợ hiện chưa trực tuyến. Vui lòng thử lại sau.",
                    null, false, TIME_FORMAT.format(new Date()), 0L);
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

        // Kiểm tra xem có phải ảnh không
        final boolean asImage = ChatFileUtil.isImageExtension(file.getName())
                && ChatImageUtil.isSupportedImage(file);

        // Quyết định chế độ gửi
        final boolean useCloudinary = !asImage && file.length() >= 2 * 1024 * 1024;

        // Kiểm tra giới hạn kích thước
        if (useCloudinary && file.length() > 25 * 1024 * 1024) {
            JOptionPane.showMessageDialog(this,
                    "File quá lớn (tối đa 25 MB khi dùng Cloudinary).",
                    "Quá dung lượng", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!useCloudinary && file.length() > ChatFileUtil.MAX_BYTES) {
            JOptionPane.showMessageDialog(this,
                    "File quá lớn (tối đa " + (ChatFileUtil.MAX_BYTES / 1_000_000) + " MB cho file nhỏ).\n"
                    + "File lớn hơn 2MB sẽ tự động dùng Cloudinary.",
                    "Quá dung lượng", JOptionPane.WARNING_MESSAGE);
            return;
        }

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        inputField.setEnabled(false);

        final String caption = inputField.getText() == null ? "" : inputField.getText().trim();
        final long fileSize = file.length();

        new SwingWorker<Object, Void>() {
            @Override
            protected Object doInBackground() {
                if (asImage) {
                    return ChatImageUtil.encodeForChat(file);
                }
                if (useCloudinary) {
                    try {
                        String cloudUrl = CloudinaryService.getInstance().uploadFile(file);
                        return new CloudinaryFileResult(cloudUrl, file.getName(), fileSize);
                    } catch (CloudinaryUploadException e) {
                        return new UploadError(e.getMessage());
                    }
                }
                return ChatFileUtil.encodeForChat(file);
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                inputField.setEnabled(true);

                Object result;
                try {
                    result = get();
                } catch (Exception ex) {
                    result = null;
                }

                if (result == null) {
                    JOptionPane.showMessageDialog(ChatPanel.this,
                            asImage ? "Không đọc được ảnh." : "Không đọc được file.",
                            "Lỗi", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                if (result instanceof UploadError) {
                    JOptionPane.showMessageDialog(ChatPanel.this,
                            "Lỗi upload file: " + ((UploadError) result).message,
                            "Lỗi Cloudinary", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                ChatClient chatClient = ChatClient.getInstance();
                boolean sent;

                if (asImage) {
                    ChatImageUtil.EncodedImage img = (ChatImageUtil.EncodedImage) result;
                    sent = chatClient.sendImage(caption.isEmpty() ? null : caption, img.base64, img.mime);
                    BufferedImage preview = ChatImageUtil.decodeBase64(img.base64);
                    addBubble(caption.isEmpty() ? null : caption, preview,
                            null, null, null, true,
                            TIME_FORMAT.format(new Date()), 0L);
                } else if (result instanceof CloudinaryFileResult) {
                    CloudinaryFileResult cloudFile = (CloudinaryFileResult) result;
                    sent = chatClient.sendFileUrl(caption.isEmpty() ? null : caption,
                            cloudFile.url, cloudFile.fileName, cloudFile.fileSize);
                    addBubble(caption.isEmpty() ? null : caption, null,
                            cloudFile.fileName, null, cloudFile.url, true,
                            TIME_FORMAT.format(new Date()), 0L);
                } else {
                    ChatFileUtil.EncodedFile f = (ChatFileUtil.EncodedFile) result;
                    sent = chatClient.sendFile(caption.isEmpty() ? null : caption,
                            f.base64, f.fileName, f.mime);
                    addBubble(caption.isEmpty() ? null : caption, null,
                            f.fileName, f.base64, null, true,
                            TIME_FORMAT.format(new Date()), 0L);
                }

                inputField.setText("");
                if (!sent) {
                    AppAlert.warning(ChatPanel.this, "Chưa kết nối máy chủ chat",
                            "Tin của bạn đã được giữ lại và sẽ gửi tự động khi kết nối lại.");
                }
            }
        }.execute();
    }

    private void onMessageReceived(ChatMessage message) {
        if (!message.isChat() || !message.fromAdmin) return;
        BufferedImage image = message.hasImage() ? ChatImageUtil.decodeBase64(message.imageBase64) : null;
        String text = message.text;
        boolean isVoice = message.hasVoice()
                || (message.hasFile() && message.fileName != null
                    && ("voice.wav".equalsIgnoreCase(message.fileName)
                        || message.fileName.toLowerCase().endsWith(".wav")));
        boolean hasFileUrl = message.hasFileUrl();
        boolean hasFile = message.hasFile() && !isVoice;
        String voiceOrFileB64 = isVoice
                ? (message.hasVoice() ? message.voiceBase64 : message.fileBase64)
                : (hasFile && !hasFileUrl ? message.fileBase64 : null);
        String fileUrl = (!isVoice && hasFileUrl) ? message.fileUrl : null;
        String fileName = isVoice ? "voice.wav" : (hasFile ? message.fileName : null);
        if ((text == null || text.isBlank()) && image == null && !hasFile && !isVoice) return;
        addBubble(text, image, fileName, voiceOrFileB64, fileUrl,
                false, TIME_FORMAT.format(new Date(message.timestamp)),
                message.messageId);
        if (text != null && !text.isBlank()) {
            speakIncomingIfEnabled(text);
        }
        if (onIncomingMessageListener != null) onIncomingMessageListener.run();
    }

    private void onConnectionChanged(boolean connected) {
        setConnectionUi(connected);
    }

    private void setConnectionUi(boolean connected) {
        statusDotRef.setForeground(connected ? AppColor.GREEN : AppColor.TEXT_MUTED_ALT);
        statusLabelRef.setText(connected ? "Đang trực tuyến" : "Mất kết nối");
    }

    private void addBubble(String text, BufferedImage image, boolean isMine, String time) {
        addBubble(text, image, null, null, null, isMine, time, 0L);
    }

    /** Overload: text + image + isMine + time + messageId (không có file). */
    private void addBubble(String text, BufferedImage image, boolean isMine, String time, long messageId) {
        addBubble(text, image, null, null, null, isMine, time, messageId);
    }

    private void addBubble(String text, BufferedImage image, String fileName, String fileBase64,
                           boolean isMine, String time) {
        addBubble(text, image, fileName, fileBase64, null, isMine, time, 0L);
    }

    private void addBubble(String text, BufferedImage image, String fileName, String fileBase64,
                           String fileUrl, boolean isMine, String time) {
        addBubble(text, image, fileName, fileBase64, fileUrl, isMine, time, 0L);
    }

    private void addBubble(String text, BufferedImage image, String fileName, String fileBase64,
                           String fileUrl, boolean isMine, String time, long messageId) {
        int viewportW = scrollPane.getViewport().getWidth();
        if (viewportW <= 0) viewportW = 300;
        int maxBubbleW = Math.max(160, Math.min(260, viewportW - 48));
        int htmlW = Math.max(120, maxBubbleW - 40);

        JPanel row = new JPanel(new FlowLayout(isMine ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 6));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.putClientProperty("messageId", messageId);

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

        if (image != null) {
            int w = image.getWidth(), h = image.getHeight();
            int targetW = Math.min(w, Math.min(IMAGE_MAX_W, maxBubbleW - 28));
            int targetH = Math.max(1, (int) Math.round(h * (targetW / (double) w)));
            Image scaled = image.getScaledInstance(targetW, targetH, Image.SCALE_SMOOTH);
            JLabel imageLabel = new JLabel(new ImageIcon(scaled));
            imageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            contentWrap.add(imageLabel);
            if (text != null && !text.isBlank()) contentWrap.add(Box.createVerticalStrut(6));
        }
        if (fileName != null && !fileName.isBlank()) {
            boolean isVoiceFile = "voice.wav".equalsIgnoreCase(fileName)
                    || fileName.toLowerCase().endsWith(".wav");
            if (isVoiceFile && fileBase64 != null && !fileBase64.isBlank()) {
                contentWrap.add(buildVoicePlayControl(fileBase64, isMine));
            } else if (fileUrl != null && !fileUrl.isBlank()) {
                JLabel fileLabel = buildCloudinaryFileLabel(fileName, fileUrl, 0L, isMine);
                fileLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                contentWrap.add(fileLabel);
            } else {
                FontIcon fileIcon = FontIcon.of(FontAwesomeSolid.FILE, 14);
                fileIcon.setIconColor(isMine ? Color.WHITE : AppColor.ACCENT_HOVER);
                JLabel fileLabel = new JLabel(fileName, fileIcon, SwingConstants.LEFT);
                fileLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                fileLabel.setForeground(isMine ? Color.WHITE : AppColor.ACCENT);
                fileLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
                fileLabel.setToolTipText("Nhấp để lưu file");
                fileLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                fileLabel.setBorder(new EmptyBorder(4, 0, 0, 0));
                final String fn = fileName;
                final String fb = fileBase64;
                fileLabel.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        FileDownloadUI.saveBase64WithProgress(ChatPanel.this, fn, fb);
                    }
                });
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
        if (time != null && !time.isBlank()) {
            JLabel timeLabel = new JLabel(time);
            timeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            timeLabel.setForeground(isMine ? new Color(255, 255, 255, 200) : AppColor.TEXT_MUTED);
            timeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            timeLabel.setBorder(new EmptyBorder(4, 0, 0, 0));
            contentWrap.add(timeLabel);
        }

        // Nút xóa từng tin
        JLabel delBtn = new JLabel(iconOf(FontAwesomeSolid.TIMES, 11,
                isMine ? new Color(255, 255, 255, 180) : AppColor.TEXT_MUTED));
        delBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        delBtn.setToolTipText("Xóa tin nhắn");
        delBtn.setBorder(new EmptyBorder(0, 6, 0, 0));
        delBtn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Object idObj = row.getClientProperty("messageId");
                long mid = idObj instanceof Long ? (Long) idObj : 0L;
                deleteOneMessage(row, mid);
            }
        });

        bubble.add(contentWrap, BorderLayout.CENTER);
        row.add(bubble);
        row.add(delBtn);
        messagesContainer.add(row);
        messagesContainer.revalidate();
        messagesContainer.repaint();
        scrollToBottom();
    }



    private JLabel buildImageLabel(BufferedImage src, int maxWidth) {
        int w = src.getWidth();
        int h = src.getHeight();
        int targetW = Math.min(w, Math.min(IMAGE_MAX_W, maxWidth));
        int targetH = (int) Math.round(h * (targetW / (double) w));
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

        int maxW = 720;
        int maxH = 540;
        int w = src.getWidth();
        int h = src.getHeight();
        double scale = Math.min(1.0, Math.min(maxW / (double) w, maxH / (double) h));
        int dw = Math.max(1, (int) Math.round(w * scale));
        int dh = Math.max(1, (int) Math.round(h * scale));
        Image scaled = src.getScaledInstance(dw, dh, Image.SCALE_SMOOTH);

        JLabel label = new JLabel(new ImageIcon(scaled));
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

    // ================================================================
    // CLOUDINARY FILE ATTACHMENT - MỚI
    // ================================================================

    /** Hiển thị file đính kèm dạng URL Cloudinary */
    private JLabel buildCloudinaryFileLabel(String fileName, String fileUrl, long fileSize, boolean isMine) {
        String sizeText = fileSize > 0 ? " (" + formatFileSize(fileSize) + ")" : "";
        JLabel label = new JLabel("📎 " + fileName + sizeText);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        label.setForeground(isMine ? Color.WHITE : AppColor.ACCENT_HOVER);
        label.setCursor(new Cursor(Cursor.HAND_CURSOR));
        label.setToolTipText("<html>Nhấp để tải file từ Cloudinary<br>" + fileUrl + "</html>");
        label.setBorder(new EmptyBorder(6, 0, 0, 0));
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                openCloudinaryFile(fileUrl, fileName);
            }
        });
        return label;
    }

    /** Mở/tải file từ Cloudinary URL */
    private void openCloudinaryFile(String fileUrl, String fileName) {
        int option = JOptionPane.showConfirmDialog(this,
                "Mở file '" + fileName + "' trong trình duyệt?\n"
                + "File sẽ được tải trực tiếp từ Cloudinary.",
                "Tải file",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (option == JOptionPane.YES_OPTION) {
            try {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(fileUrl));
            } catch (Exception ex) {
                JTextField urlField = new JTextField(fileUrl);
                urlField.setEditable(false);
                urlField.selectAll();
                JOptionPane.showMessageDialog(this,
                        new Object[]{"Không thể mở tự động. Copy URL bên dưới:", urlField},
                        "URL file", JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }

    /** Định dạng dung lượng file */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    // ================================================================
    // CLASS HỖ TRỢ CHO UPLOAD CLOUDINARY
    // ================================================================

    /** Kết quả upload Cloudinary thành công */
    private static class CloudinaryFileResult {
        final String url;
        final String fileName;
        final long fileSize;
        CloudinaryFileResult(String url, String fileName, long fileSize) {
            this.url = url;
            this.fileName = fileName;
            this.fileSize = fileSize;
        }
    }

    /** Lỗi upload Cloudinary */
    private static class UploadError {
        final String message;
        UploadError(String message) {
            this.message = message;
        }
    }

}