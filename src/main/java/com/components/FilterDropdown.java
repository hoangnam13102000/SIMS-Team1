package com.components;

import com.theme.AppColor;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.function.Consumer;

/**
 * Dropdown lọc dùng chung cho toolbar các trang CRUD (BaseCrudPanel), đứng
 * cạnh {@link BaseSearch}: icon + JComboBox bọc trong 1 khung bo góc, cùng
 * chiều cao (38px) và cùng màu viền/nền với BaseSearch, để cụm "tìm kiếm +
 * lọc" nhìn như 1 khối thống nhất thay vì JComboBox mặc định vuông vức, rời
 * rạc của Swing.
 * <p>
 * Cách dùng (trong constructor subclass của BaseCrudPanel, sau super()):
 * <pre>{@code
 * FilterDropdown<CategoryOption> categoryFilter =
 *         new FilterDropdown<>(FontAwesomeSolid.LAYER_GROUP, categoryOptions);
 * categoryFilter.onChange(opt -> applyFilters());
 * addToolbarFilter(categoryFilter);
 * }</pre>
 */
public class FilterDropdown<T> extends JPanel {

    private final JComboBox<T> combo;

    public FilterDropdown(FontAwesomeSolid icon, T[] items) {
        setLayout(new BorderLayout(0, 0));
        setBackground(AppColor.BG_LIGHT);
        setOpaque(true);
        // Padding trái 12px giống BaseSearch; phải 4px đủ cho mũi tên dropdown.
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(0, 12, 0, 4)));
        setPreferredSize(new Dimension(190, 38));
        setMaximumSize(new Dimension(220, 38));

        FontIcon fontIcon = FontIcon.of(icon, 13);
        fontIcon.setIconColor(AppColor.TEXT_MUTED);
        JLabel iconLabel = new JLabel(fontIcon);
        // Khoảng cách icon → text: 6px.
        iconLabel.setBorder(new EmptyBorder(0, 0, 0, 6));

        combo = new JComboBox<>(items);
        combo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        combo.setBackground(AppColor.BG_LIGHT);
        combo.setForeground(AppColor.TEXT_PRIMARY);
        combo.setBorder(BorderFactory.createEmptyBorder());
        combo.setFocusable(false);
        combo.setOpaque(false);
        combo.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // FlatLaf: padding 0 để không thừa khoảng trắng giữa icon và chữ.
        // buttonBackground phải CÙNG màu panel (không null, không để mặc định trắng)
        // để vùng nút mũi tên chiếm hết chiều cao/nền — tránh khe trắng.
        // Đồng thời set màu mũi tên rõ ràng để hover không làm mất mũi tên.
        String bg = hex(AppColor.BG_LIGHT);
        String hoverBg = hex(AppColor.BG_LIGHTER);
        String arrow = hex(AppColor.TEXT_MUTED);
        String arrowHover = hex(AppColor.TEXT_PRIMARY);
        combo.putClientProperty("FlatLaf.style",
                "padding: 0,0,0,0;"
                + "background: " + bg + ";"
                + "buttonBackground: " + bg + ";"
                + "buttonHoverBackground: " + hoverBg + ";"
                + "buttonPressedBackground: " + hoverBg + ";"
                + "buttonArrowColor: " + arrow + ";"
                + "buttonHoverArrowColor: " + arrowHover + ";"
                + "buttonPressedArrowColor: " + arrowHover + ";"
                + "buttonSeparatorWidth: 0");

        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                label.setFont(new Font("Segoe UI", Font.PLAIN, 13));
                // index == -1: chữ trong khung combo (cạnh icon) → không padding trái.
                // index >= 0: dòng trong popup → cần padding trái cho dễ đọc.
                int leftPad = index < 0 ? 0 : 10;
                label.setBorder(new EmptyBorder(4, leftPad, 4, 8));
                if (isSelected) {
                    label.setBackground(AppColor.ACCENT_BG_SOFT);
                    label.setForeground(AppColor.ACCENT_HOVER);
                } else {
                    label.setBackground(AppColor.WHITE);
                    label.setForeground(AppColor.TEXT_PRIMARY);
                }
                return label;
            }
        });

        add(iconLabel, BorderLayout.WEST);
        add(combo, BorderLayout.CENTER);
    }

    private static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    /** Gọi listener mỗi khi lựa chọn thay đổi (kể cả do code gọi setSelectedItem). */
    public void onChange(Consumer<T> listener) {
        combo.addActionListener(e -> listener.accept(getSelected()));
    }

    @SuppressWarnings("unchecked")
    public T getSelected() {
        return (T) combo.getSelectedItem();
    }

    public void setSelected(T value) {
        combo.setSelectedItem(value);
    }

    /** true nếu lựa chọn hiện tại KHÔNG phải phần tử đầu tiên (quy ước: phần tử 0 = "Tất cả"). */
    public boolean isFilterActive() {
        return combo.getItemCount() > 0 && combo.getSelectedIndex() > 0;
    }

    public void resetToAll() {
        if (combo.getItemCount() > 0) combo.setSelectedIndex(0);
    }
}