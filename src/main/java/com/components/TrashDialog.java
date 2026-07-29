package com.components;

import com.theme.AppColor;
import com.theme.AppFont;
import com.theme.AppRadius;
import com.theme.AppSpacing;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Dialog "Thung rac" dung chung cho moi trang co bat TrashConfig (xem
 * BaseCrudPanel.getTrashConfig()) - hien danh sach ban ghi da xoa mem duoi
 * dang the (card) bo goc + bong do nhe + avatar chu cai dau, dong bo phong
 * cach voi CategoryCard/ProductCard/EmptyState de giao dien nhat quan toan
 * he thong thay vi 1 list JLabel don gian nhu truoc.
 */
public final class TrashDialog {

    private TrashDialog() {}

    public static <T> void show(Window owner, String title, List<T> items,
                                 Function<T, String> displayName,
                                 Function<T, Boolean> onRestore,
                                 Function<T, Boolean> onHardDelete,
                                 Runnable onChanged) {

        List<T> data = new ArrayList<>(items);

        JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setSize(560, 560);
        dialog.setMinimumSize(new Dimension(440, 380));
        dialog.setLocationRelativeTo(owner);
        dialog.setLayout(new BorderLayout());
        dialog.getContentPane().setBackground(AppColor.WHITE);

        // ----- Header: icon badge tron + tieu de + phu de dem so luong -----
        JLabel subtitleLabel = new JLabel(" ");
        subtitleLabel.setFont(AppFont.BODY);
        subtitleLabel.setForeground(AppColor.TEXT_MUTED);
        subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        dialog.add(buildHeader(title, subtitleLabel), BorderLayout.NORTH);

        // ----- Danh sach -----
        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(AppColor.BG_LIGHT);
        listPanel.setBorder(new EmptyBorder(AppSpacing.MD, AppSpacing.LG, AppSpacing.LG, AppSpacing.LG));

        JScrollPane scrollPane = new JScrollPane(listPanel);
        scrollPane.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, AppColor.BORDER));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getViewport().setBackground(AppColor.BG_LIGHT);
        dialog.add(scrollPane, BorderLayout.CENTER);

        Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            listPanel.removeAll();
            subtitleLabel.setText(data.isEmpty() ? "Không có mục nào" : data.size() + " mục đã xóa \u00b7 có thể khôi phục");

            if (data.isEmpty()) {
                listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
                listPanel.add(Box.createVerticalGlue());
                EmptyState empty = new EmptyState(FontAwesomeSolid.TRASH, "Thùng rác trống",
                        "Các mục bạn xóa sẽ xuất hiện tại đây và có thể khôi phục bất cứ lúc nào.");
                empty.setAlignmentX(Component.CENTER_ALIGNMENT);
                listPanel.add(empty);
                listPanel.add(Box.createVerticalGlue());
            } else {
                listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
                for (T item : new ArrayList<>(data)) {
                    JComponent card = buildRow(dialog, item, displayName, onRestore, onHardDelete, data, refresh, onChanged);
                    card.setAlignmentX(Component.LEFT_ALIGNMENT);
                    listPanel.add(card);
                    listPanel.add(Box.createVerticalStrut(AppSpacing.SM));
                }
            }
            listPanel.revalidate();
            listPanel.repaint();
        };
        refresh[0].run();

        // ----- Footer -----
        JButton closeButton = new PillButton("Đóng", null, AppColor.CANCEL_BG, AppColor.CANCEL_HOVER, AppColor.TEXT_PRIMARY);
        closeButton.addActionListener(e -> dialog.dispose());

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        footer.setBackground(AppColor.WHITE);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, AppColor.BORDER),
                new EmptyBorder(AppSpacing.MD, AppSpacing.LG, AppSpacing.MD, AppSpacing.LG)));
        footer.add(closeButton);
        dialog.add(footer, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    // ---------------------------------------------------------------
    // Header
    // ---------------------------------------------------------------

    private static JPanel buildHeader(String title, JLabel subtitleLabel) {
        JPanel header = new JPanel(new BorderLayout(AppSpacing.MD, 0));
        header.setBackground(AppColor.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AppColor.BORDER),
                new EmptyBorder(AppSpacing.LG, AppSpacing.XL, AppSpacing.LG, AppSpacing.XL)));

        FontIcon trashIcon = FontIcon.of(FontAwesomeSolid.TRASH, 18);
        trashIcon.setIconColor(AppColor.ERROR);
        JLabel iconBadge = new JLabel(trashIcon, SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(AppColor.ERROR_BG);
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        iconBadge.setPreferredSize(new Dimension(44, 44));

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(AppFont.DIALOG_TITLE);
        titleLabel.setForeground(AppColor.TEXT_TITLE);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        titleBox.add(titleLabel);
        titleBox.add(Box.createVerticalStrut(2));
        titleBox.add(subtitleLabel);

        header.add(iconBadge, BorderLayout.WEST);
        header.add(titleBox, BorderLayout.CENTER);
        return header;
    }

    // ---------------------------------------------------------------
    // 1 dong (the) trong danh sach
    // ---------------------------------------------------------------

    private static <T> JComponent buildRow(JDialog dialog, T item, Function<T, String> displayName,
                                            Function<T, Boolean> onRestore, Function<T, Boolean> onHardDelete,
                                            List<T> data, Runnable[] refresh, Runnable onChanged) {

        String name = displayName.apply(item);
        boolean[] hover = {false};

        JPanel card = new JPanel(new BorderLayout(AppSpacing.MD, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                int w = getWidth() - 2;
                int h = getHeight() - 2;
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.translate(1, 1);

                g2.setColor(new Color(15, 23, 42, hover[0] ? 16 : 8));
                g2.fill(new RoundRectangle2D.Float(0, 2, w, h, AppRadius.MEDIUM, AppRadius.MEDIUM));

                g2.setColor(AppColor.WHITE);
                g2.fill(new RoundRectangle2D.Float(0, 0, w, h, AppRadius.MEDIUM, AppRadius.MEDIUM));

                g2.setColor(hover[0] ? AppColor.ACCENT_SOFT : AppColor.BORDER);
                g2.setStroke(new BasicStroke(hover[0] ? 1.4f : 1f));
                g2.draw(new RoundRectangle2D.Float(0, 0, w, h, AppRadius.MEDIUM, AppRadius.MEDIUM));

                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(AppSpacing.MD, AppSpacing.MD, AppSpacing.MD, AppSpacing.MD));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 68));
        card.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hover[0] = true; card.repaint(); }
            @Override public void mouseExited(MouseEvent e) { hover[0] = false; card.repaint(); }
        });

        card.add(buildInitialAvatar(name), BorderLayout.WEST);

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setBorder(new EmptyBorder(0, AppSpacing.MD, 0, AppSpacing.MD));

        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(AppFont.BODY_BOLD);
        nameLabel.setForeground(AppColor.TEXT_PRIMARY);
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel hintLabel = new JLabel("Đã chuyển vào thùng rác");
        hintLabel.setFont(AppFont.SMALL);
        hintLabel.setForeground(AppColor.TEXT_MUTED);
        hintLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        info.add(Box.createVerticalGlue());
        info.add(nameLabel);
        info.add(Box.createVerticalStrut(2));
        info.add(hintLabel);
        info.add(Box.createVerticalGlue());
        card.add(info, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setOpaque(false);

        JButton restoreBtn = new PillButton("Khôi phục", FontAwesomeSolid.UNDO,
                AppColor.SUCCESS_BG, AppColor.SUCCESS, AppColor.SUCCESS);
        restoreBtn.addActionListener(e -> {
            if (Boolean.TRUE.equals(onRestore.apply(item))) {
                data.remove(item);
                refresh[0].run();
                if (onChanged != null) onChanged.run();
            } else {
                BaseDialog.error(card, "Không thể khôi phục", "Khôi phục thất bại, vui lòng thử lại.");
            }
        });
        actions.add(restoreBtn);

        if (onHardDelete != null) {
            JButton hardDeleteBtn = new CircleIconButton(FontAwesomeSolid.TRASH, AppColor.ERROR, AppColor.ERROR_BG, "Xóa vĩnh viễn");
            hardDeleteBtn.addActionListener(e -> {
                boolean confirmed = BaseDialog.confirm(card, "Xóa vĩnh viễn",
                        "Xóa VĨNH VIỄN \"" + name + "\"? Hành động này không thể hoàn tác, kể cả từ Thùng rác.",
                        "Xóa vĩnh viễn", AppColor.ERROR, AppColor.ERROR_HOVER, FontAwesomeSolid.TRASH);
                if (!confirmed) return;

                if (Boolean.TRUE.equals(onHardDelete.apply(item))) {
                    data.remove(item);
                    refresh[0].run();
                    if (onChanged != null) onChanged.run();
                } else {
                    BaseDialog.error(card, "Không thể xóa vĩnh viễn", "Xóa vĩnh viễn thất bại, vui lòng thử lại.");
                }
            });
            actions.add(hardDeleteBtn);
        }

        card.add(actions, BorderLayout.EAST);
        return card;
    }

    // ---------------------------------------------------------------
    // Avatar chu cai dau (mau sac suy ra tu ten, cung bang mau voi
    // CategoryCard de dong bo giao dien)
    // ---------------------------------------------------------------

    private static JLabel buildInitialAvatar(String name) {
        String initial = (name == null || name.isBlank()) ? "?" : name.trim().substring(0, 1).toUpperCase();
        int size = 40;
        Color bg = avatarBg(name);
        Color fg = avatarFg(name);

        JLabel avatar = new JLabel(initial, SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bg);
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        avatar.setPreferredSize(new Dimension(size, size));
        avatar.setMinimumSize(new Dimension(size, size));
        avatar.setMaximumSize(new Dimension(size, size));
        avatar.setFont(AppFont.BODY_BOLD);
        avatar.setForeground(fg);
        return avatar;
    }

    private static Color avatarBg(String name) {
        int hue = name == null ? 0 : Math.floorMod(name.toLowerCase().hashCode(), 5);
        return switch (hue) {
            case 1 -> new Color(255, 244, 230);
            case 2 -> new Color(232, 245, 233);
            case 3 -> new Color(232, 240, 254);
            case 4 -> new Color(253, 235, 240);
            default -> AppColor.ACCENT_BG_SOFT;
        };
    }

    private static Color avatarFg(String name) {
        int hue = name == null ? 0 : Math.floorMod(name.toLowerCase().hashCode(), 5);
        return switch (hue) {
            case 1 -> new Color(217, 119, 6);
            case 2 -> new Color(21, 128, 61);
            case 3 -> new Color(37, 99, 235);
            case 4 -> new Color(219, 39, 119);
            default -> AppColor.ACCENT_HOVER;
        };
    }

    // ---------------------------------------------------------------
    // Nut bo tron kieu "vien mem -> to dam khi hover" (Khoi phuc / Dong)
    // ---------------------------------------------------------------

    private static final class PillButton extends JButton {

        private final Color softBg;
        private final Color solidBg;
        private final Color accentFg;
        private boolean hover = false;

        PillButton(String text, FontAwesomeSolid icon, Color softBg, Color solidBg, Color accentFg) {
            super(text);
            this.softBg = softBg;
            this.solidBg = solidBg;
            this.accentFg = accentFg;

            if (icon != null) {
                FontIcon normalIcon = FontIcon.of(icon, 12);
                normalIcon.setIconColor(accentFg);
                setIcon(normalIcon);
                setIconTextGap(6);
            }

            setFont(AppFont.SMALL_BOLD);
            setForeground(accentFg);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(7, 16, 7, 16));

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) {
                    hover = true;
                    setForeground(Color.WHITE);
                    if (icon != null) {
                        FontIcon hoverIcon = FontIcon.of(icon, 12);
                        hoverIcon.setIconColor(Color.WHITE);
                        setIcon(hoverIcon);
                    }
                    repaint();
                }
                @Override public void mouseExited(MouseEvent e) {
                    hover = false;
                    setForeground(PillButton.this.accentFg);
                    if (icon != null) {
                        FontIcon normalIcon = FontIcon.of(icon, 12);
                        normalIcon.setIconColor(PillButton.this.accentFg);
                        setIcon(normalIcon);
                    }
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(hover ? solidBg : softBg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ---------------------------------------------------------------
    // Nut icon tron (Xoa vinh vien) - chi noi bat mau khi hover
    // ---------------------------------------------------------------

    private static final class CircleIconButton extends JButton {

        private final Color hoverBg;
        private boolean hover = false;

        CircleIconButton(FontAwesomeSolid icon, Color color, Color hoverBg, String tooltip) {
            this.hoverBg = hoverBg;

            FontIcon fontIcon = FontIcon.of(icon, 14);
            fontIcon.setIconColor(color);
            setIcon(fontIcon);
            setToolTipText(tooltip);
            setPreferredSize(new Dimension(34, 34));
            setBorderPainted(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (hover) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(hoverBg);
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }
}