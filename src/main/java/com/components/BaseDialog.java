package com.components;

import com.theme.AppColor;
import com.theme.AppConstant;
import com.theme.AppFont;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;

public final class BaseDialog {

    // Constants for modern button design
    private static final int BUTTON_CORNER_RADIUS = AppConstant.RADIUS_LG;
    private static final int BUTTON_PADDING_TOP = 10;
    private static final int BUTTON_PADDING_BOTTOM = 10;
    private static final int BUTTON_PADDING_LEFT = 24;
    private static final int BUTTON_PADDING_RIGHT = 24;

    private BaseDialog() {}

    // ---------------------------------------------------------------------
    // XAC NHAN XOA
    // ---------------------------------------------------------------------
    /** Xac nhan xoa 1 doi tuong theo ten. Vi du: confirmDelete(this, "điện thoại", "iPhone 15"). */
    public static boolean confirmDelete(Component parent, String itemType, String itemName) {
        String message = "Bạn có chắc muốn xóa " + itemType + " \"" + itemName + "\"?\nHành động này không thể hoàn tác.";
        return confirm(parent, "Xác nhận xóa", message, "Xóa", AppColor.ERROR, AppColor.ERROR_HOVER, FontAwesomeSolid.TRASH);
    }

    /** Xac nhan chung (khong danger), vi du xac nhan cap nhat trang thai don hang. */
    public static boolean confirm(Component parent, String title, String message) {
        return confirm(parent, title, message, "Xác nhận", AppColor.ACCENT, AppColor.ACCENT_HOVER, FontAwesomeSolid.QUESTION_CIRCLE);
    }

    public static boolean confirm(Component parent, String title, String message,
                                   String confirmText, Color confirmColor, Color confirmHover, FontAwesomeSolid icon) {
        boolean[] resultHolder = {false};
        JDialog dialog = buildBaseDialog(parent, title);
        JPanel body = buildBody(icon, confirmColor, title, message, null);
        dialog.add(body, BorderLayout.CENTER);
        JButton cancelButton = createModernButton("Hủy", AppColor.CANCEL_BG, AppColor.CANCEL_HOVER, AppColor.TEXT_PRIMARY);
        JButton confirmButton = createModernButton(confirmText, confirmColor, confirmHover, Color.WHITE);
        cancelButton.addActionListener(e -> dialog.dispose());
        confirmButton.addActionListener(e -> { resultHolder[0] = true; dialog.dispose(); });
        dialog.add(buildFooter(cancelButton, confirmButton), BorderLayout.SOUTH);
        dialog.getRootPane().setDefaultButton(confirmButton);
        showCentered(dialog);
        return resultHolder[0];
    }

    // ---------------------------------------------------------------------
    // NHAP LIEU DANG TEXT (thay cho JOptionPane.showInputDialog)
    // ---------------------------------------------------------------------
    /** Vi du: BaseDialog.inputText(this, "Thêm danh mục", "Tên danh mục:", "", "Thêm"). Tra ve null neu bam Hủy. */
    public static String inputText(Component parent, String title, String label, String initialValue, String confirmText) {
        String[] resultHolder = {null};
        JDialog dialog = buildBaseDialog(parent, title);
        JTextField field = new JTextField(initialValue == null ? "" : initialValue);
        styleField(field);
        JPanel body = buildBody(FontAwesomeSolid.EDIT, AppColor.ACCENT, title, label, field);
        dialog.add(body, BorderLayout.CENTER);
        JButton cancelButton = createModernButton("Hủy", AppColor.CANCEL_BG, AppColor.CANCEL_HOVER, AppColor.TEXT_PRIMARY);
        JButton confirmButton = createModernButton(confirmText, AppColor.ACCENT, AppColor.ACCENT_HOVER, Color.WHITE);
        cancelButton.addActionListener(e -> dialog.dispose());
        Runnable submit = () -> {
            String value = field.getText().trim();
            if (!value.isEmpty()) { resultHolder[0] = value; dialog.dispose(); }
        };
        confirmButton.addActionListener(e -> submit.run());
        field.addActionListener(e -> submit.run());
        dialog.add(buildFooter(cancelButton, confirmButton), BorderLayout.SOUTH);
        dialog.getRootPane().setDefaultButton(confirmButton);
        SwingUtilities.invokeLater(field::requestFocusInWindow);
        showCentered(dialog);
        return resultHolder[0];
    }

    // ---------------------------------------------------------------------
    // NHAP LIEU DANG NHIEU DONG (JTextArea, danh cho noi dung dai - vd bao cao, mo ta)
    // ---------------------------------------------------------------------
    /**
     * Vi du: BaseDialog.inputTextArea(this, "Gửi báo cáo ngoại lệ", "Nội dung báo cáo",
     *   "VD: khách cần mua SP chưa có trong hệ thống, khách yêu cầu SP đặc biệt...",
     *   "", "Gửi báo cáo", 500).
     * Tra ve null neu bam Hủy. Truyen hint = null neu khong can dong goi y duoi nhan.
     * Truyen maxLength <= 0 neu khong gioi han so ky tu.
     */
    public static String inputTextArea(Component parent, String title, String label, String hint,
                                        String initialValue, String confirmText, int maxLength) {
        String[] resultHolder = {null};
        JDialog dialog = buildBaseDialog(parent, title);

        JPanel body = new JPanel();
        body.setBackground(AppColor.WHITE);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(24, 24, 8, 24));

        JPanel headerRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        headerRow.setOpaque(false);
        headerRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        FontIcon headerIcon = FontIcon.of(FontAwesomeSolid.EDIT, 26);
        headerIcon.setIconColor(AppColor.ACCENT);
        headerRow.add(new JLabel(headerIcon));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(AppFont.DIALOG_TITLE);
        titleLabel.setForeground(AppColor.TEXT_PRIMARY);
        headerRow.add(titleLabel);
        body.add(headerRow);
        body.add(Box.createVerticalStrut(18));

        JLabel fieldLabel = new JLabel(label);
        fieldLabel.setFont(AppFont.BODY_BOLD);
        fieldLabel.setForeground(AppColor.TEXT_PRIMARY);
        fieldLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(fieldLabel);

        if (hint != null && !hint.isBlank()) {
            body.add(Box.createVerticalStrut(3));
            JLabel hintLabel = new JLabel("<html><div style='width:360px'>" + hint + "</div></html>");
            hintLabel.setFont(AppFont.SMALL);
            hintLabel.setForeground(AppColor.TEXT_MUTED);
            hintLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(hintLabel);
        }

        body.add(Box.createVerticalStrut(10));

        JTextArea textArea = new JTextArea(initialValue == null ? "" : initialValue, 6, 32);
        textArea.setFont(AppFont.FIELD);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setForeground(AppColor.TEXT_PRIMARY);
        textArea.setBackground(AppColor.WHITE);
        textArea.setCaretColor(AppColor.ACCENT);
        textArea.setBorder(new EmptyBorder(10, 12, 10, 12));

        if (maxLength > 0) {
            ((AbstractDocument) textArea.getDocument()).setDocumentFilter(new DocumentFilter() {
                @Override
                public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
                    if (string == null) return;
                    if (fb.getDocument().getLength() + string.length() <= maxLength) {
                        super.insertString(fb, offset, string, attr);
                    }
                }
                @Override
                public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
                    String safeText = text == null ? "" : text;
                    if (fb.getDocument().getLength() - length + safeText.length() <= maxLength) {
                        super.replace(fb, offset, length, safeText, attrs);
                    }
                }
            });
        }

        JScrollPane scrollPane = new JScrollPane(textArea, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        scrollPane.setBorder(BorderFactory.createLineBorder(AppColor.FIELD_BORDER, 1, true));
        scrollPane.setViewportBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(AppColor.WHITE);
        scrollPane.setPreferredSize(new Dimension(400, 130));
        scrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        body.add(scrollPane);

        textArea.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                scrollPane.setBorder(BorderFactory.createLineBorder(AppColor.ACCENT, 2, true));
            }
            @Override
            public void focusLost(FocusEvent e) {
                scrollPane.setBorder(BorderFactory.createLineBorder(AppColor.FIELD_BORDER, 1, true));
            }
        });

        JLabel counterLabel = new JLabel();
        counterLabel.setFont(AppFont.SMALL);
        counterLabel.setForeground(AppColor.TEXT_MUTED);
        JPanel counterRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 4));
        counterRow.setOpaque(false);
        counterRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        counterRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        counterRow.add(counterLabel);
        body.add(counterRow);

        JButton cancelButton = createModernButton("Hủy", AppColor.CANCEL_BG, AppColor.CANCEL_HOVER, AppColor.TEXT_PRIMARY);
        JButton confirmButton = createModernButton(confirmText, AppColor.ACCENT, AppColor.ACCENT_HOVER, Color.WHITE);

        Runnable updateState = () -> {
            int len = textArea.getDocument().getLength();
            if (maxLength > 0) {
                counterLabel.setText(len + "/" + maxLength);
                counterLabel.setForeground(len >= maxLength ? AppColor.ERROR
                        : len >= (int) (maxLength * 0.9) ? AppColor.WARNING : AppColor.TEXT_MUTED);
            } else {
                counterLabel.setText(len + " ký tự");
            }
            confirmButton.setEnabled(!textArea.getText().trim().isEmpty());
        };
        updateState.run();

        textArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { updateState.run(); }
            @Override public void removeUpdate(DocumentEvent e) { updateState.run(); }
            @Override public void changedUpdate(DocumentEvent e) { updateState.run(); }
        });

        cancelButton.addActionListener(e -> dialog.dispose());
        confirmButton.addActionListener(e -> {
            String value = textArea.getText().trim();
            if (!value.isEmpty()) {
                resultHolder[0] = value;
                dialog.dispose();
            }
        });

        dialog.add(body, BorderLayout.CENTER);
        dialog.add(buildFooter(cancelButton, confirmButton), BorderLayout.SOUTH);
        dialog.getRootPane().setDefaultButton(confirmButton);
        SwingUtilities.invokeLater(textArea::requestFocusInWindow);
        showCentered(dialog);
        return resultHolder[0];
    }

    // ---------------------------------------------------------------------
    // XAC NHAN HANH DONG KEM GHI CHU KHONG BAT BUOC
    // (thay cho cap JOptionPane.showInputDialog + showConfirmDialog rieng le)
    // ---------------------------------------------------------------------
    /**
     * Gop 1 dialog duy nhat: vua nhap ghi chu (co the bo trong) vua xac nhan hanh dong,
     * thay vi 2 hop thoai JOptionPane rieng biet nhu truoc. Tra ve Optional.empty()
     * neu nguoi dung bam Huy/dong dialog; tra ve Optional cua chuoi (co the la "")
     * neu nguoi dung xac nhan.
     * <p>
     * Vi du: BaseDialog.confirmWithNote(this, "Tạm giữ giỏ hàng",
     *   "Giỏ hàng hiện tại sẽ được lưu tạm để phục vụ khách khác.",
     *   "Ghi chú cho giỏ tạm giữ", "VD: khách quay lại lấy hàng sau...",
     *   "Tạm giữ giỏ hàng", FontAwesomeSolid.PAUSE, 300);
     */
    public static java.util.Optional<String> confirmWithNote(Component parent, String title, String message,
                                                               String noteLabel, String noteHint,
                                                               String confirmText, FontAwesomeSolid icon, int maxLength) {
        Object[] resultHolder = {null}; // null = huy; String (co the rong) = da xac nhan
        JDialog dialog = buildBaseDialog(parent, title);

        JPanel body = new JPanel();
        body.setBackground(AppColor.WHITE);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(24, 24, 8, 24));

        JPanel headerRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        headerRow.setOpaque(false);
        headerRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        FontIcon headerIcon = FontIcon.of(icon, 26);
        headerIcon.setIconColor(AppColor.ACCENT);
        headerRow.add(new JLabel(headerIcon));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(AppFont.DIALOG_TITLE);
        titleLabel.setForeground(AppColor.TEXT_PRIMARY);
        headerRow.add(titleLabel);
        body.add(headerRow);
        body.add(Box.createVerticalStrut(14));

        if (message != null && !message.isBlank()) {
            JLabel messageLabel = new JLabel("<html><div style='width:380px'>" + message + "</div></html>");
            messageLabel.setFont(AppFont.BODY);
            messageLabel.setForeground(AppColor.TEXT_MUTED);
            messageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(messageLabel);
            body.add(Box.createVerticalStrut(16));
        }

        JLabel fieldLabel = new JLabel(noteLabel);
        fieldLabel.setFont(AppFont.BODY_BOLD);
        fieldLabel.setForeground(AppColor.TEXT_PRIMARY);
        fieldLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(fieldLabel);
        body.add(Box.createVerticalStrut(6));

        JTextArea textArea = new JTextArea(3, 32);
        textArea.setFont(AppFont.FIELD);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setForeground(AppColor.TEXT_PRIMARY);
        textArea.setBackground(AppColor.WHITE);
        textArea.setCaretColor(AppColor.ACCENT);
        textArea.setBorder(new EmptyBorder(10, 12, 10, 12));

        if (maxLength > 0) {
            ((AbstractDocument) textArea.getDocument()).setDocumentFilter(new DocumentFilter() {
                @Override
                public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
                    if (string == null) return;
                    if (fb.getDocument().getLength() + string.length() <= maxLength) {
                        super.insertString(fb, offset, string, attr);
                    }
                }
                @Override
                public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
                    String safeText = text == null ? "" : text;
                    if (fb.getDocument().getLength() - length + safeText.length() <= maxLength) {
                        super.replace(fb, offset, length, safeText, attrs);
                    }
                }
            });
        }

        JScrollPane scrollPane = new JScrollPane(textArea, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        scrollPane.setBorder(BorderFactory.createLineBorder(AppColor.FIELD_BORDER, 1, true));
        scrollPane.setViewportBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(AppColor.WHITE);
        scrollPane.setPreferredSize(new Dimension(400, 90));
        scrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        body.add(scrollPane);

        textArea.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                scrollPane.setBorder(BorderFactory.createLineBorder(AppColor.ACCENT, 2, true));
            }
            @Override
            public void focusLost(FocusEvent e) {
                scrollPane.setBorder(BorderFactory.createLineBorder(AppColor.FIELD_BORDER, 1, true));
            }
        });

        if (noteHint != null && !noteHint.isBlank()) {
            body.add(Box.createVerticalStrut(4));
            JLabel hintLabel = new JLabel("<html><div style='width:380px'>" + noteHint + "</div></html>");
            hintLabel.setFont(AppFont.SMALL);
            hintLabel.setForeground(AppColor.TEXT_MUTED);
            hintLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(hintLabel);
        }

        JLabel counterLabel = new JLabel();
        counterLabel.setFont(AppFont.SMALL);
        counterLabel.setForeground(AppColor.TEXT_MUTED);
        JPanel counterRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 4));
        counterRow.setOpaque(false);
        counterRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        counterRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        counterRow.add(counterLabel);
        body.add(counterRow);

        Runnable updateCounter = () -> {
            int len = textArea.getDocument().getLength();
            counterLabel.setText(maxLength > 0 ? (len + "/" + maxLength) : (len + " ký tự"));
            if (maxLength > 0) {
                counterLabel.setForeground(len >= maxLength ? AppColor.ERROR
                        : len >= (int) (maxLength * 0.9) ? AppColor.WARNING : AppColor.TEXT_MUTED);
            }
        };
        updateCounter.run();
        textArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { updateCounter.run(); }
            @Override public void removeUpdate(DocumentEvent e) { updateCounter.run(); }
            @Override public void changedUpdate(DocumentEvent e) { updateCounter.run(); }
        });

        dialog.add(body, BorderLayout.CENTER);

        JButton cancelButton = createModernButton("Hủy", AppColor.CANCEL_BG, AppColor.CANCEL_HOVER, AppColor.TEXT_PRIMARY);
        JButton confirmButton = createModernButton(confirmText, AppColor.ACCENT, AppColor.ACCENT_HOVER, Color.WHITE);
        cancelButton.addActionListener(e -> dialog.dispose());
        confirmButton.addActionListener(e -> {
            resultHolder[0] = textArea.getText().trim();
            dialog.dispose();
        });
        dialog.add(buildFooter(cancelButton, confirmButton), BorderLayout.SOUTH);
        dialog.getRootPane().setDefaultButton(confirmButton);

        SwingUtilities.invokeLater(textArea::requestFocusInWindow);
        showCentered(dialog);
        return java.util.Optional.ofNullable((String) resultHolder[0]);
    }

    // ---------------------------------------------------------------------
    // CHON TU DANH SACH (thay cho JOptionPane.showInputDialog voi mang lua chon)
    // ---------------------------------------------------------------------
    /** Vi du: BaseDialog.select(this, "Cập nhật trạng thái", "Trạng thái mới:", STATUSES, order.getStatus()). */
    public static String select(Component parent, String title, String label, String[] options, String initialValue) {
        String[] resultHolder = {null};
        JDialog dialog = buildBaseDialog(parent, title);
        JComboBox<String> combo = new JComboBox<>(options);
        combo.setSelectedItem(initialValue);
        combo.setFont(AppFont.FIELD);
        combo.setBackground(AppColor.WHITE);
        combo.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColor.FIELD_BORDER, 1, true),
                new EmptyBorder(4, 8, 4, 8)));
        JPanel body = buildBody(FontAwesomeSolid.LIST_UL, AppColor.ACCENT, title, label, combo);
        dialog.add(body, BorderLayout.CENTER);
        JButton cancelButton = createModernButton("Hủy", AppColor.CANCEL_BG, AppColor.CANCEL_HOVER, AppColor.TEXT_PRIMARY);
        JButton confirmButton = createModernButton("Cập nhật", AppColor.ACCENT, AppColor.ACCENT_HOVER, Color.WHITE);
        cancelButton.addActionListener(e -> dialog.dispose());
        confirmButton.addActionListener(e -> {
            resultHolder[0] = (String) combo.getSelectedItem();
            dialog.dispose();
        });
        dialog.add(buildFooter(cancelButton, confirmButton), BorderLayout.SOUTH);
        dialog.getRootPane().setDefaultButton(confirmButton);
        showCentered(dialog);
        return resultHolder[0];
    }

    // ---------------------------------------------------------------------
    // THONG BAO
    // ---------------------------------------------------------------------
    public static void info(Component parent, String title, String message) {
        AppAlert.info(parent, title, message);
    }

    public static void success(Component parent, String title, String message) {
        AppAlert.success(parent, title, message);
    }

    public static void error(Component parent, String title, String message) {
        AppAlert.error(parent, title, message);
    }

    // ---------------------------------------------------------------------
    // HELPERS DUNG CHUNG
    // ---------------------------------------------------------------------
    private static JDialog buildBaseDialog(Component parent, String title) {
        Window owner = SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout());
        dialog.getContentPane().setBackground(AppColor.WHITE);
        dialog.setResizable(false);
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        return dialog;
    }

    /** extraField: field/combo nhap lieu tuy chon, dat ngay duoi phan message. Truyen null neu chi la thong bao/xac nhan. */
    private static JPanel buildBody(FontAwesomeSolid iconType, Color iconColor, String title, String message, JComponent extraField) {
        JPanel body = new JPanel();
        body.setBackground(AppColor.WHITE);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(24, 24, 8, 24));

        JPanel headerRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        headerRow.setOpaque(false);
        headerRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        FontIcon icon = FontIcon.of(iconType, 26);
        icon.setIconColor(iconColor);
        JLabel iconLabel = new JLabel(icon);
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(AppFont.DIALOG_TITLE);
        titleLabel.setForeground(AppColor.TEXT_PRIMARY);
        headerRow.add(iconLabel);
        headerRow.add(titleLabel);
        body.add(headerRow);
        body.add(Box.createVerticalStrut(14));

        // === NOI DUNG MESSAGE: TANG CHIEU RONG + HO TRO CUON NEU QUA DAI ===
        final int MESSAGE_WIDTH = 420;
        final int MESSAGE_MAX_HEIGHT = 240;

        String htmlMessage = "<html><div style='width:" + MESSAGE_WIDTH + "px'>"
                + message.replace("\n", "<br>") + "</div></html>";

        JEditorPane tempPane = new JEditorPane("text/html", htmlMessage);
        tempPane.setEditable(false);
        tempPane.setOpaque(false);
        tempPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        tempPane.setFont(AppFont.BODY);
        int msgHeight = tempPane.getPreferredSize().height;

        JComponent messageComponent;
        if (msgHeight > MESSAGE_MAX_HEIGHT) {
            JEditorPane messagePane = new JEditorPane("text/html", htmlMessage);
            messagePane.setEditable(false);
            messagePane.setOpaque(false);
            messagePane.setFont(AppFont.BODY);
            messagePane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
            messagePane.setForeground(AppColor.TEXT_MUTED);

            JScrollPane scrollPane = new JScrollPane(messagePane);
            scrollPane.setOpaque(false);
            scrollPane.getViewport().setOpaque(false);
            scrollPane.setBorder(null);
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            scrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);
            scrollPane.setMaximumSize(new Dimension(MESSAGE_WIDTH + 30, MESSAGE_MAX_HEIGHT));
            scrollPane.setPreferredSize(new Dimension(MESSAGE_WIDTH + 30, MESSAGE_MAX_HEIGHT));
            scrollPane.getVerticalScrollBar().setUnitIncrement(12);
            messageComponent = scrollPane;
        } else {
            JLabel messageLabel = new JLabel(htmlMessage);
            messageLabel.setFont(AppFont.BODY);
            messageLabel.setForeground(AppColor.TEXT_MUTED);
            messageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            messageComponent = messageLabel;
        }
        body.add(messageComponent);

        if (extraField != null) {
            body.add(Box.createVerticalStrut(12));
            extraField.setAlignmentX(Component.LEFT_ALIGNMENT);
            extraField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
            body.add(extraField);
        }

        return body;
    }

    private static JPanel buildFooter(JButton... buttons) {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        footer.setBackground(AppColor.WHITE);
        footer.setBorder(new EmptyBorder(8, 16, 16, 16));
        for (JButton button : buttons) footer.add(button);
        return footer;
    }

    private static void styleField(JTextField field) {
        field.setFont(AppFont.FIELD);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColor.FIELD_BORDER, 1, true),
                new EmptyBorder(6, 10, 6, 10)));
    }

    /**
     * Creates a modern button with rounded corners and smooth hover effects
     */
    private static JButton createModernButton(String text, Color bg, Color hover, Color fg) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), BUTTON_CORNER_RADIUS, BUTTON_CORNER_RADIUS);

                g2.setColor(getForeground());
                FontMetrics fm = g2.getFontMetrics();
                int textX = (getWidth() - fm.stringWidth(getText())) / 2;
                int textY = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(getText(), textX, textY);

                g2.dispose();
            }
        };

        button.setFont(AppFont.BODY_BOLD);
        button.setForeground(fg);
        button.setBackground(bg);
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(BUTTON_PADDING_TOP, BUTTON_PADDING_LEFT,
                                        BUTTON_PADDING_BOTTOM, BUTTON_PADDING_RIGHT));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setOpaque(false);
        button.setContentAreaFilled(false);

        button.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                button.setBackground(hover);
                button.repaint();
            }
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                button.setBackground(bg);
                button.repaint();
            }
        });

        return button;
    }

    /**
     * Alternative modern button creation using JButton with rounded border
     */
    private static JButton createModernButtonAlt(String text, Color bg, Color hover, Color fg) {
        JButton button = new JButton(text);
        button.setFont(AppFont.BODY_BOLD);
        button.setForeground(fg);
        button.setBackground(bg);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(bg, 1, true),
            new EmptyBorder(BUTTON_PADDING_TOP, BUTTON_PADDING_LEFT,
                          BUTTON_PADDING_BOTTOM, BUTTON_PADDING_RIGHT)
        ));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setOpaque(true);

        button.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                button.setBackground(hover);
                button.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(hover, 1, true),
                    new EmptyBorder(BUTTON_PADDING_TOP, BUTTON_PADDING_LEFT,
                                  BUTTON_PADDING_BOTTOM, BUTTON_PADDING_RIGHT)
                ));
            }
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                button.setBackground(bg);
                button.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(bg, 1, true),
                    new EmptyBorder(BUTTON_PADDING_TOP, BUTTON_PADDING_LEFT,
                                  BUTTON_PADDING_BOTTOM, BUTTON_PADDING_RIGHT)
                ));
            }
        });

        return button;
    }

    private static void showCentered(JDialog dialog) {
        dialog.pack();
        if (dialog.getWidth() < 460) dialog.setSize(460, dialog.getHeight());
        if (dialog.getHeight() < 140) dialog.setSize(dialog.getWidth(), 140);
        dialog.setLocationRelativeTo(dialog.getOwner());
        dialog.setVisible(true);
    }
}