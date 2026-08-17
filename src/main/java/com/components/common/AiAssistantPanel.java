package com.components.common;

import com.components.AppAlert;
import com.model.ai.AiChatMessage;
import com.service.ai.AiChatService;
import com.service.ai.GeminiService;
import com.service.ai.voice.AudioRecorder;
import com.service.ai.voice.SpeechToTextService;
import com.service.ai.voice.TextToSpeechService;
import com.security.FileSecurityScanner;
import com.security.ScanResult;
import com.theme.AppColor;
import com.utils.FileDownloadUI;
import com.utils.ImageUtil;
import com.ws.ChatFileUtil;
import com.ws.ChatImageUtil;

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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Panel chat với trợ lý AI (Gemini).
 * Hỗ trợ nhiều ảnh + nhiều file (Excel/Word/…) trong 1 tin.
 * Icon Word/Excel/PDF… dùng FontAwesome (FILE_WORD, FILE_EXCEL…).
 * <p>
 * Giọng nói: nút mic (STT qua Gemini) + nút loa (TTS đọc câu trả lời).
 */
public class AiAssistantPanel extends JPanel {

    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm");
    private static final Pattern IMG_MARKER = Pattern.compile("\\[\\[IMG:(.+?)\\]\\]");
    private static final int PRODUCT_IMG_MAX = 180;
    private static final int USER_IMG_MAX_W = 200;

    private final JPanel messagesContainer;
    private final JScrollPane scrollPane;
    private final JTextField inputField;
    private final JButton sendButton;
    private final JButton attachButton;
    private final JButton micButton;
    private final JButton ttsButton;
    private boolean ttsEnabled;
    private String lastAiReply;
    private final JPanel pendingAttachChip;
    private final JPanel pendingListPanel;
    private final String headerTitle;
    private final String welcomeText;
    private final boolean clientSide;

    private final AiChatService chatService = new AiChatService();
    private final AudioRecorder audioRecorder = new AudioRecorder();
    private final SpeechToTextService sttService = new SpeechToTextService();
    private final TextToSpeechService ttsService = new TextToSpeechService();
    private final List<AiChatMessage> history = new ArrayList<>();
    private JPanel typingBubbleRef;
    private JPanel voiceLoadingBubbleRef;
    private Runnable onCloseListener;
    private boolean voiceBusy;
    private final SoundWaveIcon soundWave = new SoundWaveIcon();
    private javax.swing.Timer voiceLevelTimer;

    /** Nhiều file/ảnh đang chờ gửi. */
    private final List<PendingAttachment> pendingAttachments = new ArrayList<>();

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
                new EmptyBorder(10, 12, 10, 12)));

        FontIcon paperclip = FontIcon.of(FontAwesomeSolid.PAPERCLIP, 14);
        paperclip.setIconColor(AppColor.TEXT_SECONDARY);
        attachButton = new JButton(paperclip);
        attachButton.setToolTipText("Gửi ảnh / file (có thể chọn nhiều)");
        attachButton.setFocusPainted(false);
        attachButton.setBorderPainted(false);
        attachButton.setContentAreaFilled(false);
        attachButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        attachButton.setPreferredSize(new Dimension(36, 36));
        attachButton.addActionListener(e -> pickAttachment());

        // Mic: giọng nói → text → gửi chat
        FontIcon micIcon = FontIcon.of(FontAwesomeSolid.MICROPHONE, 14);
        micIcon.setIconColor(AppColor.TEXT_SECONDARY);
        micButton = new JButton(micIcon);
        micButton.setToolTipText("Nhấn để nói (nhấn lại để gửi)");
        micButton.setFocusPainted(false);
        micButton.setBorderPainted(false);
        micButton.setContentAreaFilled(false);
        micButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        micButton.setPreferredSize(new Dimension(36, 36));
        micButton.addActionListener(e -> toggleVoiceInput());

        // Bật/tắt đọc câu trả lời AI (JButton — tránh JToggleButton LAF khó bấm)
        ttsEnabled = false;
        lastAiReply = null;
        FontIcon volIcon = FontIcon.of(FontAwesomeSolid.VOLUME_UP, 14);
        volIcon.setIconColor(AppColor.TEXT_SECONDARY);
        ttsButton = new JButton(volIcon);
        ttsButton.setToolTipText("Bật đọc to câu trả lời AI (bấm lại để tắt)");
        ttsButton.setFocusPainted(false);
        ttsButton.setBorderPainted(false);
        ttsButton.setContentAreaFilled(false);
        ttsButton.setOpaque(false);
        ttsButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        ttsButton.setPreferredSize(new Dimension(36, 36));
        ttsButton.setEnabled(true);
        ttsButton.addActionListener(e -> toggleTts());

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

        // Chip list: mỗi file 1 dòng [icon Word/Excel/…] tên [x]
        pendingListPanel = new JPanel();
        pendingListPanel.setOpaque(false);
        pendingListPanel.setLayout(new BoxLayout(pendingListPanel, BoxLayout.Y_AXIS));

        pendingAttachChip = new JPanel(new BorderLayout());
        pendingAttachChip.setOpaque(false);
        pendingAttachChip.setBorder(new EmptyBorder(0, 4, 6, 4));
        pendingAttachChip.add(pendingListPanel, BorderLayout.CENTER);
        pendingAttachChip.setVisible(false);

        JPanel rightActions = new JPanel();
        rightActions.setOpaque(false);
        rightActions.setLayout(new BoxLayout(rightActions, BoxLayout.X_AXIS));
        rightActions.add(Box.createHorizontalStrut(4));
        rightActions.add(ttsButton);
        rightActions.add(Box.createHorizontalStrut(2));
        rightActions.add(micButton);
        rightActions.add(Box.createHorizontalStrut(4));
        rightActions.add(attachButton);
        rightActions.add(Box.createHorizontalStrut(6));
        rightActions.add(sendButton);
        ttsButton.setAlignmentY(Component.CENTER_ALIGNMENT);
        micButton.setAlignmentY(Component.CENTER_ALIGNMENT);
        attachButton.setAlignmentY(Component.CENTER_ALIGNMENT);
        sendButton.setAlignmentY(Component.CENTER_ALIGNMENT);

        JPanel centerCol = new JPanel(new BorderLayout());
        centerCol.setOpaque(false);
        centerCol.add(pendingAttachChip, BorderLayout.NORTH);
        centerCol.add(inputField, BorderLayout.CENTER);

        inputBar.add(centerCol, BorderLayout.CENTER);
        inputBar.add(rightActions, BorderLayout.EAST);

        add(headerBar, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(inputBar, BorderLayout.SOUTH);

        addBubble(welcomeText, false, TIME_FORMAT.format(new Date()), null, null, null, null);

        if (!GeminiService.isConfigured()) {
            setInputEnabled(false);
            addBubble("Trợ lý AI chưa được cấu hình (thiếu GEMINI_API_KEY). Vui lòng liên hệ quản trị viên.",
                    false, TIME_FORMAT.format(new Date()), null, null, null, null);
        }
    }

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
        attachButton.setEnabled(enabled);
        micButton.setEnabled(enabled && !voiceBusy);
        // Nút loa luôn bấm được
        ttsButton.setEnabled(true);
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
                    stopVoiceResources();
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

    // ------------------------------------------------------------------
    // Giọng nói: STT (VAD tự dừng) + TTS
    // ------------------------------------------------------------------

    private void toggleTts() {
        ttsEnabled = !ttsEnabled;
        FontIcon volIcon = FontIcon.of(
                ttsEnabled ? FontAwesomeSolid.VOLUME_UP : FontAwesomeSolid.VOLUME_MUTE, 14);
        volIcon.setIconColor(ttsEnabled ? AppColor.ACCENT_HOVER : AppColor.TEXT_SECONDARY);
        ttsButton.setIcon(volIcon);
        ttsButton.setToolTipText(ttsEnabled
                ? "Đang bật đọc to — bấm để tắt"
                : "Bật đọc to câu trả lời AI");
        ttsButton.repaint();
        if (ttsEnabled) {
            if (lastAiReply != null && !lastAiReply.isBlank()) {
                ttsService.speakAsync(lastAiReply);
            }
        } else {
            ttsService.stop();
        }
    }

    private void toggleVoiceInput() {
        if (voiceBusy) return;
        if (audioRecorder.isRecording()) {
            finishVoiceInput();
        } else {
            startVoiceInput();
        }
    }

    private void startVoiceInput() {
        try {
            ttsService.stop();
            audioRecorder.setSilenceMs(700);
            // Quan trọng: invokeLater để không deadlock với thread recorder
            audioRecorder.setOnAutoStop(() -> SwingUtilities.invokeLater(() -> {
                if (!voiceBusy) {
                    finishVoiceInput();
                }
            }));
            audioRecorder.start();

            FontIcon stopIcon = FontIcon.of(FontAwesomeSolid.STOP, 14);
            stopIcon.setIconColor(AppColor.ERROR);
            micButton.setIcon(stopIcon);
            micButton.setToolTipText("Đang nghe… nghỉ ~0.7s sẽ tự gửi (hoặc bấm dừng)");
            inputField.putClientProperty("JTextField.placeholderText",
                    "Đang nghe… nói xong nghỉ ngắn sẽ tự gửi");
            inputField.repaint();
            showVoiceLoadingBubble("Đang nghe… nói xong nghỉ ngắn sẽ gửi");
            startVoiceLevelMonitor();

            // Timer dự phòng: nếu VAD đã dừng mà callback sót, vẫn finish
            javax.swing.Timer watchdog = new javax.swing.Timer(300, null);
            watchdog.addActionListener(ev -> {
                if (voiceBusy) {
                    watchdog.stop();
                    return;
                }
                if (!audioRecorder.isRecording() && audioRecorder.wasStoppedByVad()) {
                    watchdog.stop();
                    finishVoiceInput();
                }
                // hết 35s vẫn đang ghi → dừng
                if (!audioRecorder.isRecording() && !audioRecorder.wasStoppedByVad()) {
                    // user đã stop tay — worker finishVoice sẽ lo
                }
            });
            watchdog.setRepeats(true);
            watchdog.start();
            // tự tắt watchdog sau 35s
            javax.swing.Timer stopWatch = new javax.swing.Timer(35_000, e -> watchdog.stop());
            stopWatch.setRepeats(false);
            stopWatch.start();
        } catch (Exception ex) {
            AppAlert.error(this, "Không mở được microphone.\n" + rootMessage(ex));
        }
    }

    private void finishVoiceInput() {
        if (voiceBusy) return;
        stopVoiceLevelMonitor();
        voiceBusy = true;
        setInputEnabled(false);
        micButton.setEnabled(false);
        inputField.putClientProperty("JTextField.placeholderText", "Đang nhận dạng giọng nói…");
        inputField.repaint();
        showVoiceLoadingBubble("Đang nhận dạng giọng nói…");

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                byte[] wav = audioRecorder.stopAndGetWav();
                if (wav.length < 1000) return "";
                return sttService.transcribeWav(wav);
            }

            @Override
            protected void done() {
                voiceBusy = false;
                hideVoiceLoadingBubble();
                resetMicButton();
                try {
                    String spoken = get();
                    setInputEnabled(true);
                    if (spoken == null || spoken.isBlank()) {
                        AppAlert.info(AiAssistantPanel.this,
                                "Không nhận được lời nói. Thử nói rõ hơn hoặc kiểm tra mic.");
                        inputField.requestFocusInWindow();
                        return;
                    }
                    inputField.setText(spoken);
                    sendCurrentInput(); // sẽ hiện "Đang trả lời..." khi gửi AI
                } catch (Exception ex) {
                    setInputEnabled(true);
                    AppAlert.error(AiAssistantPanel.this,
                            "Nhận dạng giọng nói thất bại.\n" + rootMessage(ex));
                }
            }
        }.execute();
    }

    private void showVoiceLoadingBubble(String message) {
        hideVoiceLoadingBubble();
        boolean listening = message != null && message.toLowerCase().contains("nghe");
        if (listening) {
            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setOpaque(false);
            row.setBorder(new EmptyBorder(4, 0, 4, 0));
            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            left.setOpaque(false);
            soundWave.setPreferredSize(new Dimension(32, 28));
            soundWave.setBarColor(AppColor.ACCENT_HOVER);
            left.add(soundWave);
            JLabel lbl = new JLabel(message);
            lbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            lbl.setForeground(AppColor.TEXT_SECONDARY);
            left.add(lbl);
            row.add(left, BorderLayout.WEST);
            // align like other AI bubbles
            JPanel wrap = new JPanel(new BorderLayout());
            wrap.setOpaque(false);
            wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
            wrap.add(row, BorderLayout.WEST);
            voiceLoadingBubbleRef = wrap;
        } else {
            voiceLoadingBubbleRef = buildBubbleRow(message, false, "", null, null, null);
        }
        messagesContainer.add(voiceLoadingBubbleRef);
        messagesContainer.revalidate();
        messagesContainer.repaint();
        scrollToBottom();
    }

    private void hideVoiceLoadingBubble() {
        if (voiceLoadingBubbleRef != null) {
            messagesContainer.remove(voiceLoadingBubbleRef);
            voiceLoadingBubbleRef = null;
            messagesContainer.revalidate();
            messagesContainer.repaint();
        }
    }

    private void startVoiceLevelMonitor() {
        stopVoiceLevelMonitor();
        soundWave.start();
        voiceLevelTimer = new javax.swing.Timer(50, e -> {
            if (!audioRecorder.isRecording()) {
                // vẫn animate nhẹ nếu đang processing
                return;
            }
            soundWave.setLevel(audioRecorder.getLastRms());
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

    private void resetMicButton() {
        FontIcon micIcon = FontIcon.of(FontAwesomeSolid.MICROPHONE, 14);
        micIcon.setIconColor(AppColor.TEXT_SECONDARY);
        micButton.setIcon(micIcon);
        micButton.setToolTipText("Nhấn để nói — nghỉ ngắn sẽ tự gửi");
        inputField.putClientProperty("JTextField.placeholderText", "Nhập câu hỏi cho trợ lý AI...");
        inputField.repaint();
    }

    private void stopVoiceResources() {
        try {
            audioRecorder.setOnAutoStop(null);
            audioRecorder.cancel();
        } catch (Exception ignored) {
        }
        ttsService.stop();
        voiceBusy = false;
        stopVoiceLevelMonitor();
        hideVoiceLoadingBubble();
        resetMicButton();
    }

    private static String rootMessage(Exception ex) {
        Throwable t = ex;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t.getMessage() != null ? t.getMessage() : t.toString();
    }

    // ------------------------------------------------------------------
    // Đính kèm nhiều ảnh / file
    // ------------------------------------------------------------------

    private void pickAttachment() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setDialogTitle("Chọn ảnh / Excel / Word (có thể chọn nhiều)");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Ảnh, Excel, Word, file",
                "jpg", "jpeg", "png", "gif", "webp", "bmp",
                "xlsx", "xls", "docx", "doc", "pdf", "zip", "txt", "csv"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File[] selected = chooser.getSelectedFiles();
        if (selected == null || selected.length == 0) {
            File one = chooser.getSelectedFile();
            if (one != null) selected = new File[]{one};
        }
        if (selected == null || selected.length == 0) return;

        setInputEnabled(false);
        final File[] files = selected;
        new SwingWorker<List<PendingAttachment>, String>() {
            /** Thông báo tổng hợp khi có file bị chặn / bỏ qua. */
            private final StringBuilder notes = new StringBuilder();

            @Override
            protected List<PendingAttachment> doInBackground() {
                List<PendingAttachment> out = new ArrayList<>();
                for (File file : files) {
                    if (file == null || !file.isFile()) continue;
                    String lower = file.getName().toLowerCase();
                    boolean spreadsheet = lower.endsWith(".xlsx") || lower.endsWith(".docx")
                            || lower.endsWith(".xls") || lower.endsWith(".doc");
                    if (!ChatFileUtil.isSupportedFile(file) && !spreadsheet) {
                        notes.append("• ").append(file.getName())
                                .append(": định dạng không hỗ trợ.\n");
                        continue;
                    }
                    if (file.length() > ChatFileUtil.MAX_BYTES) {
                        notes.append("• ").append(file.getName())
                                .append(": vượt dung lượng tối đa.\n");
                        continue;
                    }

                    // 1) Quét virus / heuristic (Windows Defender nếu có)
                    ScanResult scan = FileSecurityScanner.getInstance().scan(file);
                    if (scan.isBlocked()) {
                        notes.append("• ").append(file.getName()).append(": BỊ CHẶN — ")
                                .append(scan.getMessage() != null ? scan.getMessage() : scan.getStatus())
                                .append('\n');
                        // Vẫn mở preview (nút Đính kèm khóa) để user thấy lý do
                        final ScanResult scanUi = scan;
                        final File fileUi = file;
                        try {
                            javax.swing.SwingUtilities.invokeAndWait(() ->
                                    AttachmentPreviewDialog.showPreview(AiAssistantPanel.this, fileUi, scanUi));
                        } catch (Exception ignored) {
                        }
                        continue;
                    }

                    // 2) Dialog xem trước nội dung — user phải xác nhận mới đính kèm
                    final ScanResult scanUi = scan;
                    final File fileUi = file;
                    boolean[] accepted = {false};
                    try {
                        javax.swing.SwingUtilities.invokeAndWait(() ->
                                accepted[0] = AttachmentPreviewDialog.showPreview(
                                        AiAssistantPanel.this, fileUi, scanUi));
                    } catch (Exception e) {
                        notes.append("• ").append(file.getName())
                                .append(": không mở được hộp xem trước.\n");
                        continue;
                    }
                    if (!accepted[0]) {
                        notes.append("• ").append(file.getName()).append(": đã bỏ qua (không xác nhận).\n");
                        continue;
                    }

                    // 3) Encode sau khi user đồng ý
                    boolean asImage = ChatFileUtil.isImageExtension(file.getName())
                            && ChatImageUtil.isSupportedImage(file);
                    if (asImage) {
                        ChatImageUtil.EncodedImage img = ChatImageUtil.encodeForChat(file);
                        if (img != null) {
                            out.add(PendingAttachment.image(img.base64, img.mime, file.getName()));
                        } else {
                            notes.append("• ").append(file.getName()).append(": không đọc được ảnh.\n");
                        }
                    } else {
                        ChatFileUtil.EncodedFile f = ChatFileUtil.encodeForChat(file);
                        if (f == null) {
                            notes.append("• ").append(file.getName()).append(": không đọc được file.\n");
                            continue;
                        }
                        String localPath = null;
                        if (lower.endsWith(".xlsx") || lower.endsWith(".docx")) {
                            try {
                                java.nio.file.Path dir = java.nio.file.Path.of("uploads", "ai_import");
                                java.nio.file.Files.createDirectories(dir);
                                String safe = file.getName().replaceAll("[\\\\/:*?\"<>|]", "_");
                                String name = "imp_" + System.currentTimeMillis() + "_" + safe;
                                java.nio.file.Path dest = dir.resolve(name);
                                java.nio.file.Files.write(dest,
                                        java.util.Base64.getDecoder().decode(f.base64));
                                localPath = "uploads/ai_import/" + name;
                            } catch (Exception ignored) {
                                localPath = null;
                            }
                        }
                        out.add(PendingAttachment.file(f.base64, f.fileName, f.mime, localPath));
                    }
                }
                return out;
            }

            @Override
            protected void done() {
                setInputEnabled(true);
                try {
                    List<PendingAttachment> added = get();
                    if (notes.length() > 0) {
                        AppAlert.warning(AiAssistantPanel.this, "Một số file không được đính kèm",
                                notes.toString().trim());
                    }
                    if (added == null || added.isEmpty()) {
                        if (notes.length() == 0) {
                            AppAlert.warning(AiAssistantPanel.this, "Không đọc được",
                                    "Không xử lý được file đã chọn (định dạng/dung lượng).");
                        }
                        return;
                    }
                    pendingAttachments.addAll(added);
                    updatePendingChip();
                    inputField.requestFocusInWindow();
                } catch (Exception ex) {
                    AppAlert.error(AiAssistantPanel.this, "Lỗi", "Không xử lý được file.");
                }
            }
        }.execute();
    }

    private void clearPendingAttachment() {
        pendingAttachments.clear();
        updatePendingChip();
    }

    private void removePendingAt(int index) {
        if (index >= 0 && index < pendingAttachments.size()) {
            pendingAttachments.remove(index);
            updatePendingChip();
        }
    }

    /**
     * Danh sách file đang chờ gửi: mỗi dòng [icon loại] tên file [× bỏ đính kèm].
     * Icon × nằm bên phải tên — click để gỡ file khỏi danh sách đính kèm.
     */
    private void updatePendingChip() {
        pendingListPanel.removeAll();
        if (pendingAttachments.isEmpty()) {
            pendingAttachChip.setVisible(false);
        } else {
            for (int i = 0; i < pendingAttachments.size(); i++) {
                final int idx = i;
                PendingAttachment att = pendingAttachments.get(i);

                FontAwesomeSolid iconType = att.isImage
                        ? FontAwesomeSolid.IMAGE
                        : iconForFile(att.displayName);
                FontIcon typeIcon = FontIcon.of(iconType, 13);
                typeIcon.setIconColor(AppColor.ACCENT);

                JLabel nameLabel = new JLabel(att.displayName, typeIcon, SwingConstants.LEFT);
                nameLabel.setIconTextGap(6);
                nameLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                nameLabel.setForeground(AppColor.TEXT_PRIMARY);
                nameLabel.setToolTipText(att.displayName);

                // Nút × bỏ đính kèm — rõ, hover đỏ
                FontIcon removeIcon = FontIcon.of(FontAwesomeSolid.TIMES_CIRCLE, 14);
                removeIcon.setIconColor(AppColor.TEXT_MUTED);
                JLabel removeLabel = new JLabel(removeIcon);
                removeLabel.setBorder(new EmptyBorder(0, 8, 0, 2));
                removeLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
                removeLabel.setToolTipText("Bỏ đính kèm file này");
                removeLabel.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        removePendingAt(idx);
                    }

                    @Override
                    public void mouseEntered(MouseEvent e) {
                        FontIcon hot = FontIcon.of(FontAwesomeSolid.TIMES_CIRCLE, 14);
                        hot.setIconColor(AppColor.ERROR);
                        removeLabel.setIcon(hot);
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        FontIcon normal = FontIcon.of(FontAwesomeSolid.TIMES_CIRCLE, 14);
                        normal.setIconColor(AppColor.TEXT_MUTED);
                        removeLabel.setIcon(normal);
                    }
                });

                JPanel row = new JPanel(new BorderLayout(6, 0));
                row.setOpaque(true);
                row.setBackground(AppColor.ACCENT_BG_SOFT != null ? AppColor.ACCENT_BG_SOFT : AppColor.CANCEL_BG);
                row.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(AppColor.BORDER, 1, true),
                        new EmptyBorder(4, 8, 4, 6)));
                row.add(nameLabel, BorderLayout.CENTER);
                row.add(removeLabel, BorderLayout.EAST);

                // Khoảng cách giữa các chip
                JPanel wrap = new JPanel(new BorderLayout());
                wrap.setOpaque(false);
                wrap.setBorder(new EmptyBorder(0, 0, 4, 0));
                wrap.add(row, BorderLayout.CENTER);
                pendingListPanel.add(wrap);
            }
            pendingAttachChip.setVisible(true);
        }
        pendingListPanel.revalidate();
        pendingListPanel.repaint();
        revalidate();
    }

    // ------------------------------------------------------------------
    // Gửi tin
    // ------------------------------------------------------------------

    private void sendCurrentInput() {
        String text = inputField.getText() == null ? "" : inputField.getText().trim();
        List<PendingAttachment> atts = new ArrayList<>(pendingAttachments);
        if ((text.isEmpty() && atts.isEmpty()) || !sendButton.isEnabled()) return;

        List<AiChatMessage.ImagePart> images = new ArrayList<>();
        List<AiChatMessage.FilePart> files = new ArrayList<>();
        BufferedImage firstPreview = null;
        String firstFileName = null;
        String firstFileB64 = null;

        for (PendingAttachment att : atts) {
            if (att.isImage) {
                images.add(new AiChatMessage.ImagePart(att.base64, att.mime));
                if (firstPreview == null) {
                    firstPreview = ChatImageUtil.decodeBase64(att.base64);
                }
            } else {
                files.add(new AiChatMessage.FilePart(
                        att.displayName, att.base64, att.mime, att.localFilePath));
                if (firstFileName == null) {
                    firstFileName = att.displayName;
                    firstFileB64 = att.base64;
                }
            }
        }

        String bubbleText = text;
        if (atts.size() > 1) {
            String summary = atts.size() + " tệp đính kèm";
            bubbleText = text.isEmpty() ? summary : text + "\n(" + summary + ")";
        }

        // Bubble: text + ảnh đầu + file đầu (các file còn lại hiển thị thêm trong bubble)
        addBubble(bubbleText.isEmpty() ? null : bubbleText, true, TIME_FORMAT.format(new Date()),
                firstPreview, firstFileName, firstFileB64, null);

        // Nếu có nhiều file: thêm chip tên + icon Word/Excel cho từng file còn lại
        if (files.size() > 1) {
            for (int i = 1; i < files.size(); i++) {
                AiChatMessage.FilePart fp = files.get(i);
                addBubble(null, true, TIME_FORMAT.format(new Date()),
                        null, fp.fileName, fp.base64, null);
            }
        }
        // Nhiều ảnh: hiện thêm preview các ảnh còn lại
        if (images.size() > 1) {
            for (int i = 1; i < images.size(); i++) {
                BufferedImage img = ChatImageUtil.decodeBase64(images.get(i).base64);
                if (img != null) {
                    addBubble(null, true, TIME_FORMAT.format(new Date()), img, null, null, null);
                }
            }
        }

        history.add(new AiChatMessage("user", text, images, files));
        inputField.setText("");
        clearPendingAttachment();
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
                addBubble(reply, false, TIME_FORMAT.format(new Date()), null, null, null, null);
                lastAiReply = reply;
                if (ttsEnabled && reply != null && !reply.isBlank()) {
                    ttsService.speakAsync(reply);
                }
                inputField.requestFocusInWindow();
            }
        }.execute();
    }

    private void showTypingBubble() {
        typingBubbleRef = buildBubbleRow("Đang trả lời...", false, "", null, null, null);
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

    private void addBubble(String text, boolean isMine, String time,
                           BufferedImage image, String fileName, String fileBase64,
                           String unused) {
        JPanel row = buildBubbleRow(text, isMine, time, image, fileName, fileBase64);
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

    private JPanel buildBubbleRow(String text, boolean isMine, String time,
                                  BufferedImage userImage, String fileName, String fileBase64) {
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

        if (userImage != null) {
            int w = userImage.getWidth(), h = userImage.getHeight();
            if (w > 0 && h > 0) {
                int targetW = Math.min(w, Math.min(USER_IMG_MAX_W, maxBubbleW - 28));
                int targetH = Math.max(1, (int) (h * (targetW / (double) w)));
                Image scaled = userImage.getScaledInstance(targetW, targetH, Image.SCALE_SMOOTH);
                JLabel imageLabel = new JLabel(new ImageIcon(scaled));
                imageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                contentWrap.add(imageLabel);
            }
        }

        // File — icon Word / Excel / PDF… (giống bản cũ)
        if (fileName != null && !fileName.isBlank()) {
            Color fileFg = isMine ? Color.WHITE : AppColor.ACCENT;
            FontIcon fileIcon = FontIcon.of(iconForFile(fileName), 15);
            fileIcon.setIconColor(fileFg);
            JLabel fileLabel = new JLabel(fileName, fileIcon, SwingConstants.LEFT);
            fileLabel.setIconTextGap(6);
            fileLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            fileLabel.setForeground(fileFg);
            fileLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
            fileLabel.setToolTipText("Nhấp để lưu file");
            fileLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            fileLabel.setBorder(new EmptyBorder(4, 0, 4, 0));
            final String fn = fileName;
            final String fb = fileBase64;
            fileLabel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (fb != null && !fb.isBlank()) {
                        FileDownloadUI.saveBase64WithProgress(AiAssistantPanel.this, fn, fb);
                    }
                }
            });
            contentWrap.add(fileLabel);
        }

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

    /**
     * Icon FontAwesome theo phần mở rộng (Word / Excel / PDF…).
     * Giữ nguyên logic bản cũ của bạn.
     */
    private static FontAwesomeSolid iconForFile(String fileName) {
        String ext = ChatFileUtil.extensionOf(fileName);
        return switch (ext) {
            case "doc", "docx" -> FontAwesomeSolid.FILE_WORD;
            case "xls", "xlsx", "csv" -> FontAwesomeSolid.FILE_EXCEL;
            case "ppt", "pptx" -> FontAwesomeSolid.FILE_POWERPOINT;
            case "pdf" -> FontAwesomeSolid.FILE_PDF;
            case "zip", "rar", "7z" -> FontAwesomeSolid.FILE_ARCHIVE;
            case "txt" -> FontAwesomeSolid.FILE_ALT;
            default -> FontAwesomeSolid.FILE;
        };
    }

    private static final class PendingAttachment {
        final boolean isImage;
        final String base64;
        final String mime;
        final String displayName;
        final String localFilePath;

        private PendingAttachment(boolean isImage, String base64, String mime, String displayName,
                                  String localFilePath) {
            this.isImage = isImage;
            this.base64 = base64;
            this.mime = mime;
            this.displayName = displayName;
            this.localFilePath = localFilePath;
        }

        static PendingAttachment image(String base64, String mime, String name) {
            return new PendingAttachment(true, base64, mime, name != null ? name : "ảnh.jpg", null);
        }

        static PendingAttachment file(String base64, String name, String mime) {
            return new PendingAttachment(false, base64, mime, name != null ? name : "file", null);
        }

        static PendingAttachment file(String base64, String name, String mime, String localPath) {
            return new PendingAttachment(false, base64, mime, name != null ? name : "file", localPath);
        }
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
