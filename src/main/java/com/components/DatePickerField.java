package com.components;


import com.theme.AppColor;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * O chon 1 ngay duy nhat: textfield hien thi (chi doc) + nut lich, bam vao se
 * mo popup lich thang de chon truc quan (co nut lui/toi thang, bam thang de
 * chon ngay) - thay vi phai bam mui ten len/xuong tung ngay mot nhu JSpinner.
 * <p>
 * Tu viet (khong them thu vien ngoai) de dong bo phong cach voi cac component
 * khac trong package nay (BaseSearch, Pagination...) va tranh phai them
 * dependency + kiem tra tuong thich Java 8 cho 1 thu vien datepicker rieng.
 */
public class DatePickerField extends JPanel {

    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String[] DOW_LABELS = {"T2", "T3", "T4", "T5", "T6", "T7", "CN"};

    private final JTextField displayField;
    private final JButton calendarButton;
    private final List<Consumer<LocalDate>> listeners = new ArrayList<>();

    private LocalDate value;
    private YearMonth viewMonth;
    private JPopupMenu popup;

    public DatePickerField() {
        this(LocalDate.now());
    }

    public DatePickerField(LocalDate initialValue) {
        this.value = initialValue != null ? initialValue : LocalDate.now();

        setLayout(new BorderLayout());
        setBackground(AppColor.BG_LIGHT);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(4, 10, 4, 6)));
        setPreferredSize(new Dimension(112, 34));

        displayField = new JTextField();
        displayField.setEditable(false);
        displayField.setOpaque(false);
        displayField.setBorder(BorderFactory.createEmptyBorder());
        displayField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        displayField.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        displayField.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { togglePopup(); }
        });

        FontIcon icon = FontIcon.of(FontAwesomeSolid.CALENDAR_ALT, 14);
        icon.setIconColor(AppColor.FIELD_BORDER);
        calendarButton = new JButton(icon);
        calendarButton.setBorder(BorderFactory.createEmptyBorder());
        calendarButton.setContentAreaFilled(false);
        calendarButton.setFocusPainted(false);
        calendarButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
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
        displayField.setCursor(Cursor.getPredefinedCursor(enabled ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        if (!enabled && popup != null) popup.setVisible(false);
    }

    public LocalDate getValue() { return value; }

    /** Gan ngay moi (vd tu code, khong phai nguoi dung bam chon) va bao cho listener. */
    public void setValue(LocalDate newValue) {
        if (newValue == null || newValue.equals(value)) return;
        this.value = newValue;
        updateDisplay();
        notifyListeners();
    }

    /** Dang ky nghe moi khi nguoi dung chon 1 ngay khac trong popup lich. */
    public void onChange(Consumer<LocalDate> listener) { listeners.add(listener); }

    private void notifyListeners() {
        for (Consumer<LocalDate> l : listeners) l.accept(value);
    }

    private void updateDisplay() {
        displayField.setText(value.format(DISPLAY_FORMAT));
    }

    private void togglePopup() {
        if (!isEnabled()) return;
        if (popup != null && popup.isVisible()) {
            popup.setVisible(false);
            return;
        }
        viewMonth = YearMonth.from(value);
        popup = new JPopupMenu();
        popup.setBorder(BorderFactory.createLineBorder(AppColor.BORDER, 1));
        popup.add(buildCalendarPanel());
        popup.show(this, 0, getHeight());
    }

    private JPanel buildCalendarPanel() {
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBackground(AppColor.WHITE);
        root.setBorder(new EmptyBorder(10, 10, 10, 10));
        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildGrid(), BorderLayout.CENTER);
        return root;
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JButton prev = navButton("‹");
        JButton next = navButton("›");
        prev.addActionListener(e -> { viewMonth = viewMonth.minusMonths(1); refreshPopup(); });
        next.addActionListener(e -> { viewMonth = viewMonth.plusMonths(1); refreshPopup(); });

        JLabel monthLabel = new JLabel("Tháng " + viewMonth.getMonthValue() + "/" + viewMonth.getYear(), SwingConstants.CENTER);
        monthLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));

        header.add(prev, BorderLayout.WEST);
        header.add(monthLabel, BorderLayout.CENTER);
        header.add(next, BorderLayout.EAST);
        return header;
    }

    private JButton navButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setBorder(new EmptyBorder(0, 8, 0, 8));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JPanel buildGrid() {
        JPanel grid = new JPanel(new GridLayout(0, 7, 2, 2));
        grid.setOpaque(false);

        for (String d : DOW_LABELS) {
            JLabel l = new JLabel(d, SwingConstants.CENTER);
            l.setFont(new Font("Segoe UI", Font.BOLD, 11));
            l.setForeground(AppColor.FIELD_BORDER);
            grid.add(l);
        }

        LocalDate firstOfMonth = viewMonth.atDay(1);
        // ISO: Thu 2 = 1 ... Chu nhat = 7 -> so o trong can ve truoc ngay 1
        int leading = firstOfMonth.getDayOfWeek().getValue() - 1;
        LocalDate cursor = firstOfMonth.minusDays(leading);

        LocalDate today = LocalDate.now();
        for (int i = 0; i < 42; i++) {
            LocalDate day = cursor.plusDays(i);
            grid.add(buildDayButton(day, today));

            // Da ve du so tuan chua het thang, dung lai o cuoi tuan chua ngay
            // cuoi cung (khong ve du 6 tuan neu 5 tuan la da du).
            if (i >= 34 && !day.isBefore(viewMonth.atEndOfMonth()) && day.getDayOfWeek() == DayOfWeek.SUNDAY) {
                break;
            }
        }
        return grid;
    }

    private JButton buildDayButton(LocalDate day, LocalDate today) {
        JButton dayBtn = new JButton(String.valueOf(day.getDayOfMonth()));
        dayBtn.setOpaque(true);
        dayBtn.setFocusPainted(false);
        dayBtn.setMargin(new Insets(2, 2, 2, 2));
        dayBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        dayBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        boolean inMonth = YearMonth.from(day).equals(viewMonth);
        boolean isSelected = day.equals(value);

        if (isSelected) {
            dayBtn.setBackground(AppColor.BLUE);
            dayBtn.setForeground(Color.WHITE);
        } else {
            dayBtn.setBackground(AppColor.WHITE);
            dayBtn.setForeground(inMonth ? AppColor.TEXT_PRIMARY : AppColor.FIELD_BORDER);
        }
        dayBtn.setBorder(!isSelected && day.equals(today)
                ? BorderFactory.createLineBorder(AppColor.BLUE, 1)
                : BorderFactory.createEmptyBorder(1, 1, 1, 1));

        dayBtn.addActionListener(e -> {
            setValue(day);
            if (popup != null) popup.setVisible(false);
        });
        return dayBtn;
    }

    private void refreshPopup() {
        if (popup == null) return;
        popup.removeAll();
        popup.add(buildCalendarPanel());
        popup.revalidate();
        popup.repaint();
    }
}