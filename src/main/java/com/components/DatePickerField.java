package com.components;

import com.theme.AppColor;
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
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Ô chọn 1 ngày: textfield có thể gõ/sửa trực tiếp (dd/MM/yyyy) + nút lịch
 * mở popup lịch tháng hiện đại (bo góc, hover, nút Hôm nay).
 * <p>
 * Tự viết (không thêm thư viện ngoài) để đồng bộ phong cách với các component
 * khác trong package này.
 */
public class DatePickerField extends JPanel {

    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String[] DOW_LABELS = {"T2", "T3", "T4", "T5", "T6", "T7", "CN"};
    private static final String PLACEHOLDER = "dd/MM/yyyy";

    private static final int DAY_SIZE = 34;
    private static final int POPUP_PAD = 14;
    private static final int CORNER = 10;

    private final JTextField displayField;
    private final JButton calendarButton;
    private final List<Consumer<LocalDate>> listeners = new ArrayList<>();

    private LocalDate value;
    private final boolean allowEmpty;
    private YearMonth viewMonth;
    private JPopupMenu popup;

    /** Tránh vòng lặp khi code tự set text trong lúc cập nhật hiển thị. */
    private boolean updatingDisplay;

    public DatePickerField() {
        this(LocalDate.now());
    }

    public DatePickerField(LocalDate initialValue) {
        this(initialValue, false);
    }

    /**
     * @param allowEmpty true nếu field được phép KHÔNG có ngày nào (vd Ngày sinh -
     *                   tùy chọn) - khi đó value có thể null, hiện placeholder
     *                   "dd/MM/yyyy" màu nhạt, và popup lịch có thêm nút "Xóa ngày".
     *                   false thì luôn có 1 ngày hợp lệ (mặc định hôm nay).
     */
    public DatePickerField(LocalDate initialValue, boolean allowEmpty) {
        this.allowEmpty = allowEmpty;
        this.value = initialValue != null ? initialValue : (allowEmpty ? null : LocalDate.now());

        setLayout(new BorderLayout());
        setBackground(AppColor.BG_LIGHT);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(4, 10, 4, 6)));
        setPreferredSize(new Dimension(130, 34));

        displayField = new JTextField();
        displayField.setEditable(true);
        displayField.setOpaque(false);
        displayField.setBorder(BorderFactory.createEmptyBorder());
        displayField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        displayField.setColumns(10);

        // Chỉ cho nhập số và dấu /
        ((AbstractDocument) displayField.getDocument()).setDocumentFilter(new DateDocumentFilter());

        displayField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { onTextChanged(); }
            @Override public void removeUpdate(DocumentEvent e) { onTextChanged(); }
            @Override public void changedUpdate(DocumentEvent e) { onTextChanged(); }
        });

        displayField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (value == null && PLACEHOLDER.equals(displayField.getText())) {
                    updatingDisplay = true;
                    displayField.setText("");
                    displayField.setForeground(AppColor.TEXT_PRIMARY);
                    updatingDisplay = false;
                }
                displayField.selectAll();
            }

            @Override
            public void focusLost(FocusEvent e) {
                commitTypedDate();
            }
        });

        displayField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    commitTypedDate();
                    displayField.transferFocus();
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    updateDisplay();
                    if (popup != null && popup.isVisible()) popup.setVisible(false);
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_DOWN
                        || (e.getKeyCode() == KeyEvent.VK_SPACE && e.isControlDown())) {
                    togglePopup();
                    e.consume();
                }
            }
        });

        // Double-click mở lịch
        displayField.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) togglePopup();
            }
        });

        FontIcon icon = FontIcon.of(FontAwesomeSolid.CALENDAR_ALT, 14);
        icon.setIconColor(AppColor.FIELD_BORDER);
        calendarButton = new JButton(icon);
        calendarButton.setBorder(BorderFactory.createEmptyBorder());
        calendarButton.setContentAreaFilled(false);
        calendarButton.setFocusPainted(false);
        calendarButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        calendarButton.setToolTipText("Mở lịch");
        calendarButton.addActionListener(e -> togglePopup());

        add(displayField, BorderLayout.CENTER);
        add(calendarButton, BorderLayout.EAST);

        updateDisplay();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        displayField.setEnabled(enabled);
        calendarButton.setEnabled(enabled);
        setBackground(enabled ? AppColor.BG_LIGHT : AppColor.BG_LIGHTER);
        if (!enabled && popup != null) popup.setVisible(false);
    }

    public LocalDate getValue() { return value; }

    /** Gán ngày mới (vd từ code) và báo cho listener. */
    public void setValue(LocalDate newValue) {
        if (newValue == null && !allowEmpty) return;
        if (newValue != null && newValue.equals(value)) {
            updateDisplay();
            return;
        }
        if (newValue == null && value == null) {
            updateDisplay();
            return;
        }
        this.value = newValue;
        updateDisplay();
        notifyListeners();
    }

    /** Đăng ký nghe mỗi khi người dùng chọn / gõ 1 ngày khác. */
    public void onChange(Consumer<LocalDate> listener) { listeners.add(listener); }

    private void notifyListeners() {
        for (Consumer<LocalDate> l : listeners) l.accept(value);
    }

    private void updateDisplay() {
        updatingDisplay = true;
        try {
            if (value == null) {
                if (!displayField.hasFocus()) {
                    displayField.setText(PLACEHOLDER);
                    displayField.setForeground(AppColor.TEXT_MUTED);
                } else {
                    displayField.setText("");
                    displayField.setForeground(AppColor.TEXT_PRIMARY);
                }
            } else {
                displayField.setText(value.format(DISPLAY_FORMAT));
                displayField.setForeground(AppColor.TEXT_PRIMARY);
            }
            setBorderColor(AppColor.BORDER);
        } finally {
            updatingDisplay = false;
        }
    }

    private void onTextChanged() {
        if (updatingDisplay) return;
        String text = displayField.getText().trim();
        if (text.isEmpty() || PLACEHOLDER.equals(text)) {
            setBorderColor(AppColor.BORDER);
            return;
        }
        LocalDate parsed = tryParse(text);
        if (parsed != null) {
            setBorderColor(AppColor.BORDER);
        } else if (text.length() >= 10) {
            setBorderColor(AppColor.ERROR != null ? AppColor.ERROR : new Color(220, 53, 69));
        } else {
            setBorderColor(AppColor.BORDER);
        }
    }

    /**
     * Khi mất focus hoặc Enter: parse text → cập nhật value.
     * - Text hợp lệ → set value
     * - Text rỗng + allowEmpty → null
     * - Text rỗng + !allowEmpty → giữ value cũ / hôm nay
     * - Text sai → revert về value hiện tại
     */
    private void commitTypedDate() {
        String text = displayField.getText().trim();
        if (text.isEmpty() || PLACEHOLDER.equals(text)) {
            if (allowEmpty) {
                if (value != null) {
                    value = null;
                    updateDisplay();
                    notifyListeners();
                } else {
                    updateDisplay();
                }
            } else {
                if (value == null) value = LocalDate.now();
                updateDisplay();
            }
            return;
        }

        LocalDate parsed = tryParse(text);
        if (parsed != null) {
            if (!parsed.equals(value)) {
                value = parsed;
                updateDisplay();
                notifyListeners();
            } else {
                updateDisplay();
            }
        } else {
            updateDisplay();
        }
    }

    private LocalDate tryParse(String text) {
        if (text == null) return null;
        text = text.trim();
        if (text.isEmpty()) return null;

        String[] patterns = {
                "dd/MM/yyyy",
                "d/M/yyyy",
                "dd-MM-yyyy",
                "d-M-yyyy",
                "dd.MM.yyyy",
                "yyyy-MM-dd"
        };
        for (String p : patterns) {
            try {
                return LocalDate.parse(text, DateTimeFormatter.ofPattern(p));
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private void setBorderColor(Color c) {
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(c, 1, true),
                new EmptyBorder(4, 10, 4, 6)));
    }

    private void togglePopup() {
        if (!isEnabled()) return;
        if (popup != null && popup.isVisible()) {
            popup.setVisible(false);
            return;
        }
        commitTypedDate();
        viewMonth = YearMonth.from(value != null ? value : LocalDate.now());
        popup = new JPopupMenu();
        popup.setBorder(BorderFactory.createEmptyBorder());
        popup.setOpaque(false);
        popup.setBackground(new Color(0, 0, 0, 0));
        popup.add(buildCalendarPanel());
        popup.show(this, 0, getHeight() + 4);
    }

    // ===================== POPUP UI =====================

    private JPanel buildCalendarPanel() {
        JPanel root = new RoundedPanel(CORNER + 2, AppColor.WHITE);
        root.setLayout(new BorderLayout(0, 0));
        root.setBorder(new EmptyBorder(POPUP_PAD, POPUP_PAD, POPUP_PAD - 2, POPUP_PAD));

        JPanel shell = new JPanel(new BorderLayout());
        shell.setOpaque(false);
        shell.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(AppColor.BORDER.getRed(), AppColor.BORDER.getGreen(), AppColor.BORDER.getBlue(), 180), 1, true),
                BorderFactory.createEmptyBorder(0, 0, 0, 0)));
        shell.add(root, BorderLayout.CENTER);

        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildGrid(), BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);

        return shell;
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(6, 0));
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(0, 0, 10, 0));

        JButton prev = iconNavButton(FontAwesomeSolid.CHEVRON_LEFT);
        JButton next = iconNavButton(FontAwesomeSolid.CHEVRON_RIGHT);
        prev.addActionListener(e -> { viewMonth = viewMonth.minusMonths(1); refreshPopup(); });
        next.addActionListener(e -> { viewMonth = viewMonth.plusMonths(1); refreshPopup(); });

        JLabel monthLabel = new JLabel("Tháng " + viewMonth.getMonthValue() + "/" + viewMonth.getYear(), SwingConstants.CENTER);
        monthLabel.setFont(new Font("Segoe UI Semibold", Font.BOLD, 14));
        monthLabel.setForeground(AppColor.TEXT_PRIMARY);
        monthLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        monthLabel.setToolTipText("Nhấp đúp để về tháng hiện tại");
        monthLabel.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    viewMonth = YearMonth.now();
                    refreshPopup();
                }
            }
        });

        header.add(prev, BorderLayout.WEST);
        header.add(monthLabel, BorderLayout.CENTER);
        header.add(next, BorderLayout.EAST);
        return header;
    }

    private JButton iconNavButton(FontAwesomeSolid iconType) {
        FontIcon icon = FontIcon.of(iconType, 12);
        icon.setIconColor(AppColor.TEXT_SECONDARY);
        JButton btn = new JButton(icon);
        btn.setPreferredSize(new Dimension(28, 28));
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setOpaque(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                icon.setIconColor(AppColor.BLUE);
                btn.repaint();
            }
            @Override public void mouseExited(MouseEvent e) {
                icon.setIconColor(AppColor.TEXT_SECONDARY);
                btn.repaint();
            }
        });
        return btn;
    }

    private JPanel buildGrid() {
        JPanel grid = new JPanel(new GridLayout(0, 7, 4, 4));
        grid.setOpaque(false);
        grid.setBorder(new EmptyBorder(0, 0, 8, 0));

        for (String d : DOW_LABELS) {
            JLabel l = new JLabel(d, SwingConstants.CENTER);
            l.setFont(new Font("Segoe UI", Font.BOLD, 11));
            l.setForeground(AppColor.TEXT_MUTED);
            l.setPreferredSize(new Dimension(DAY_SIZE, 22));
            grid.add(l);
        }

        LocalDate firstOfMonth = viewMonth.atDay(1);
        int leading = firstOfMonth.getDayOfWeek().getValue() - 1;
        LocalDate cursor = firstOfMonth.minusDays(leading);
        LocalDate today = LocalDate.now();

        for (int i = 0; i < 42; i++) {
            LocalDate day = cursor.plusDays(i);
            grid.add(new DayCell(day, today));

            if (i >= 34 && !day.isBefore(viewMonth.atEndOfMonth()) && day.getDayOfWeek() == DayOfWeek.SUNDAY) {
                break;
            }
        }
        return grid;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(4, 0, 0, 0));

        JPanel line = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(new Color(AppColor.BORDER.getRed(), AppColor.BORDER.getGreen(), AppColor.BORDER.getBlue(), 120));
                g.fillRect(0, 0, getWidth(), 1);
            }
        };
        line.setPreferredSize(new Dimension(0, 1));
        line.setOpaque(false);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 6));
        actions.setOpaque(false);

        JButton todayBtn = linkButton("Hôm nay", AppColor.BLUE);
        todayBtn.addActionListener(e -> {
            setValue(LocalDate.now());
            if (popup != null) popup.setVisible(false);
        });
        actions.add(todayBtn);

        if (allowEmpty) {
            JButton clear = linkButton("Xóa ngày", AppColor.TEXT_MUTED);
            clear.addActionListener(e -> {
                setValue(null);
                if (popup != null) popup.setVisible(false);
            });
            actions.add(clear);
        }

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(line, BorderLayout.NORTH);
        wrap.add(actions, BorderLayout.CENTER);
        return wrap;
    }

    private JButton linkButton(String text, Color color) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btn.setForeground(color);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setMargin(new Insets(2, 4, 2, 4));
        Color base = color;
        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                btn.setForeground(base.darker());
            }
            @Override public void mouseExited(MouseEvent e) {
                btn.setForeground(base);
            }
        });
        return btn;
    }

    private void refreshPopup() {
        if (popup == null) return;
        popup.removeAll();
        popup.add(buildCalendarPanel());
        popup.revalidate();
        popup.pack();
        popup.repaint();
    }

    // ===================== Day cell =====================

    private class DayCell extends JComponent {
        private final LocalDate day;
        private final boolean inMonth;
        private final boolean isToday;
        private final boolean isSelected;
        private boolean hovered;

        DayCell(LocalDate day, LocalDate today) {
            this.day = day;
            this.inMonth = YearMonth.from(day).equals(viewMonth);
            this.isToday = day.equals(today);
            this.isSelected = value != null && day.equals(value);
            setPreferredSize(new Dimension(DAY_SIZE, DAY_SIZE));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(day.format(DISPLAY_FORMAT));

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) {
                    hovered = true;
                    repaint();
                }
                @Override public void mouseExited(MouseEvent e) {
                    hovered = false;
                    repaint();
                }
                @Override public void mouseClicked(MouseEvent e) {
                    setValue(day);
                    if (popup != null) popup.setVisible(false);
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int size = Math.min(w, h) - 2;
            int x = (w - size) / 2;
            int y = (h - size) / 2;

            if (isSelected) {
                g2.setColor(AppColor.BLUE);
                g2.fillOval(x, y, size, size);
            } else if (hovered && inMonth) {
                Color soft = new Color(
                        AppColor.BLUE.getRed(),
                        AppColor.BLUE.getGreen(),
                        AppColor.BLUE.getBlue(),
                        28);
                g2.setColor(soft);
                g2.fillOval(x, y, size, size);
            } else if (isToday && inMonth) {
                g2.setColor(AppColor.BLUE);
                g2.setStroke(new BasicStroke(1.4f));
                g2.drawOval(x + 1, y + 1, size - 2, size - 2);
            }

            Color fg;
            if (isSelected) {
                fg = Color.WHITE;
            } else if (!inMonth) {
                fg = new Color(AppColor.TEXT_MUTED.getRed(), AppColor.TEXT_MUTED.getGreen(), AppColor.TEXT_MUTED.getBlue(), 140);
            } else if (isToday) {
                fg = AppColor.BLUE;
            } else {
                fg = AppColor.TEXT_PRIMARY;
            }

            g2.setColor(fg);
            g2.setFont(new Font("Segoe UI", isSelected || isToday ? Font.BOLD : Font.PLAIN, 12));
            FontMetrics fm = g2.getFontMetrics();
            String text = String.valueOf(day.getDayOfMonth());
            int tx = (w - fm.stringWidth(text)) / 2;
            int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(text, tx, ty);
            g2.dispose();
        }
    }

    // ===================== Document filter: chỉ số + / =====================

    /**
     * Cho phép gõ số và dấu phân cách. Tự chèn '/' khi gõ dãy số
     * (05082026 → 05/08/2026).
     */
    private static class DateDocumentFilter extends DocumentFilter {

        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
                throws BadLocationException {
            if (string == null) return;
            replace(fb, offset, 0, string, attr);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
            String current = fb.getDocument().getText(0, fb.getDocument().getLength());
            StringBuilder sb = new StringBuilder(current);
            sb.replace(offset, offset + length, text == null ? "" : text);

            String candidate = sb.toString();
            if (!isAllowed(candidate)) return;

            String digits = candidate.replaceAll("[^0-9]", "");
            if (text != null && text.matches("[0-9]+") && digits.length() <= 8) {
                String formatted = autoFormat(digits);
                super.replace(fb, 0, fb.getDocument().getLength(), formatted, attrs);
                return;
            }

            if (candidate.length() > 10) return;
            super.replace(fb, offset, length, text, attrs);
        }

        @Override
        public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
            super.remove(fb, offset, length);
        }

        private boolean isAllowed(String s) {
            return s.matches("[0-9/.\\-]*");
        }

        private String autoFormat(String digits) {
            if (digits.length() <= 2) {
                return digits;
            } else if (digits.length() <= 4) {
                return digits.substring(0, 2) + "/" + digits.substring(2);
            } else {
                return digits.substring(0, 2) + "/"
                        + digits.substring(2, 4) + "/"
                        + digits.substring(4, Math.min(8, digits.length()));
            }
        }
    }

    // ===================== Rounded panel =====================

    private static class RoundedPanel extends JPanel {
        private final int radius;
        private final Color bg;

        RoundedPanel(int radius, Color bg) {
            this.radius = radius;
            this.bg = bg;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bg);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, radius, radius));
            g2.dispose();
            super.paintComponent(g);
        }
    }
}