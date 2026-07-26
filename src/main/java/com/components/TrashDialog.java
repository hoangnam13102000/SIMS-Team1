package com.components;

import com.theme.AppColor;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class TrashDialog {

    private TrashDialog() {}

  
    public static <T> void show(Window owner, String title, List<T> items,
                                 Function<T, String> displayName,
                                 Function<T, Boolean> onRestore,
                                 Function<T, Boolean> onHardDelete,
                                 Runnable onChanged) {

        List<T> data = new ArrayList<>(items);

        JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setSize(520, 500);
        dialog.setLocationRelativeTo(owner);
        dialog.setLayout(new BorderLayout());
        dialog.getContentPane().setBackground(AppColor.WHITE);

        // ----- Header -----
        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setBorder(new EmptyBorder(18, 20, 14, 20));
        header.setBackground(AppColor.WHITE);

        FontIcon trashIcon = FontIcon.of(FontAwesomeSolid.TRASH, 18);
        trashIcon.setIconColor(AppColor.TEXT_MUTED);
        header.add(new JLabel(trashIcon), BorderLayout.WEST);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        titleLabel.setForeground(AppColor.TEXT_TITLE);
        header.add(titleLabel, BorderLayout.CENTER);

        dialog.add(header, BorderLayout.NORTH);

        // ----- Danh sach -----
        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(AppColor.WHITE);
        listPanel.setBorder(new EmptyBorder(0, 20, 20, 20));

        JScrollPane scrollPane = new JScrollPane(listPanel);
        scrollPane.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, AppColor.BORDER));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getViewport().setBackground(AppColor.WHITE);
        dialog.add(scrollPane, BorderLayout.CENTER);

        Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            listPanel.removeAll();
            if (data.isEmpty()) {
                JLabel empty = new JLabel("Thùng rác trống", SwingConstants.CENTER);
                empty.setForeground(AppColor.TEXT_MUTED);
                empty.setFont(new Font("Segoe UI", Font.PLAIN, 13));
                empty.setBorder(new EmptyBorder(50, 0, 50, 0));
                empty.setAlignmentX(Component.CENTER_ALIGNMENT);
                listPanel.add(empty);
            } else {
                listPanel.add(Box.createVerticalStrut(12));
                for (T item : new ArrayList<>(data)) {
                    listPanel.add(buildRow(dialog, item, displayName, onRestore, onHardDelete, data, refresh, onChanged));
                    listPanel.add(Box.createVerticalStrut(8));
                }
            }
            listPanel.revalidate();
            listPanel.repaint();
        };
        refresh[0].run();

        // ----- Footer -----
        JButton closeButton = new JButton("Đóng");
        closeButton.addActionListener(e -> dialog.dispose());
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.setBackground(AppColor.WHITE);
        footer.setBorder(new EmptyBorder(12, 20, 12, 20));
        footer.add(closeButton);
        dialog.add(footer, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    private static <T> JComponent buildRow(JDialog dialog, T item, Function<T, String> displayName,
                                            Function<T, Boolean> onRestore, Function<T, Boolean> onHardDelete,
                                            List<T> data, Runnable[] refresh, Runnable onChanged) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setBackground(AppColor.BG_LIGHT);
        row.setBorder(new EmptyBorder(10, 14, 10, 14));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel nameLabel = new JLabel(displayName.apply(item));
        nameLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        nameLabel.setForeground(AppColor.TEXT_PRIMARY);
        row.add(nameLabel, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setOpaque(false);

        JButton restoreBtn = iconButton(FontAwesomeSolid.UNDO, AppColor.SUCCESS, "Khôi phục");
        restoreBtn.addActionListener(e -> {
            if (Boolean.TRUE.equals(onRestore.apply(item))) {
                data.remove(item);
                refresh[0].run();
                if (onChanged != null) onChanged.run();
            } else {
                JOptionPane.showMessageDialog(dialog, "Khôi phục thất bại, vui lòng thử lại.", "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        });
        actions.add(restoreBtn);

        if (onHardDelete != null) {
            JButton hardDeleteBtn = iconButton(FontAwesomeSolid.TRASH, AppColor.ERROR, "Xóa vĩnh viễn");
            hardDeleteBtn.addActionListener(e -> {
                int confirm = JOptionPane.showConfirmDialog(dialog,
                        "Xóa VĨNH VIỄN \"" + displayName.apply(item) + "\"?\nHành động này không thể hoàn tác, kể cả từ Thùng rác.",
                        "Xác nhận xóa vĩnh viễn", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (confirm != JOptionPane.YES_OPTION) return;

                if (Boolean.TRUE.equals(onHardDelete.apply(item))) {
                    data.remove(item);
                    refresh[0].run();
                    if (onChanged != null) onChanged.run();
                } else {
                    JOptionPane.showMessageDialog(dialog, "Xóa vĩnh viễn thất bại, vui lòng thử lại.", "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            });
            actions.add(hardDeleteBtn);
        }

        row.add(actions, BorderLayout.EAST);
        return row;
    }

    private static JButton iconButton(FontAwesomeSolid icon, Color color, String tooltip) {
        FontIcon fontIcon = FontIcon.of(icon, 14);
        fontIcon.setIconColor(color);
        JButton button = new JButton(fontIcon);
        button.setToolTipText(tooltip);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return button;
    }
}