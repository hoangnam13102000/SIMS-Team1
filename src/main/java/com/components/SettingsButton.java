package com.components;

import com.settings.NotificationSettings;
import com.theme.AppColor;
import com.theme.AppShadow;
import com.theme.ThemeManager;
import com.theme.ThemeMode;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Nut tron (FAB) noi goc man hinh, bam vao mo popup Cai dat gom:
 *  1. Giao dien: Sang / Toi
 *  2. Thong bao: bat/tat am thanh, va (chi o Admin) an thong bao don hang moi
 * <p>
 * Dung JLayeredPane cua JFrame (KHONG dung glass pane) nen co the gan them
 * vao bat ky JFrame nao ke ca frame da dung glass pane cho muc dich khac
 * (vd ChatWidget o ClientMainFrame) ma khong xung dot.
 *
 * Cach dung:
 *   SettingsButton.attach(this);                      // Admin: day du muc, goc phai duoi
 *   SettingsButton.attach(this, 76, false);            // Client: an muc "an don hang" (khong lien quan),
 *                                                       // lui vao 76px de tranh de len bong bong chat
 */
public class SettingsButton extends JPanel {

    private static final int SIZE = 52;
    private static final int MARGIN = 24;

    private boolean hover = false;
    private boolean showOrderMuteOption = true;

    private SettingsButton() {
        setOpaque(false);
        setCursor(new Cursor(Cursor.HAND_CURSOR));
        setToolTipText("Cài đặt");

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showSettingsMenu();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                hover = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = false;
                repaint();
            }
        });
    }

    /** Gan vao goc phai duoi cua frame, hien day du cac muc cai dat. */
    public static SettingsButton attach(JFrame frame) {
        return attach(frame, 0, true);
    }

    /**
     * Gan vao goc phai duoi cua frame, lui sang trai them rightOffset (px) -
     * dung khi man hinh da co 1 widget noi khac (vd bong bong chat) o dung
     * vi tri goc phai duoi de tranh de icon len nhau.
     *
     * @param showOrderMuteOption co hien muc "Ẩn thông báo đơn hàng mới"
     *                            khong - chi nen bat o man hinh Admin, vi
     *                            day la khai niem gan voi ban hang.
     */
    public static SettingsButton attach(JFrame frame, int rightOffset, boolean showOrderMuteOption) {
        SettingsButton btn = new SettingsButton();
        btn.showOrderMuteOption = showOrderMuteOption;
        JLayeredPane layeredPane = frame.getLayeredPane();
        layeredPane.add(btn, JLayeredPane.PALETTE_LAYER);

        Runnable reposition = () -> {
            int w = layeredPane.getWidth();
            int h = layeredPane.getHeight();
            btn.setBounds(w - MARGIN - SIZE - rightOffset, h - MARGIN - SIZE, SIZE, SIZE);
        };
        reposition.run();
        layeredPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                reposition.run();
            }
        });
        return btn;
    }

    private void showSettingsMenu() {
        ThemeManager tm = ThemeManager.getInstance();
        NotificationSettings ns = NotificationSettings.getInstance();

        JPopupMenu popup = new JPopupMenu();
        popup.setBorder(new EmptyBorder(0, 0, 0, 0));
        popup.setOpaque(false);

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(AppColor.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(AppColor.BORDER, 1, true),
            new EmptyBorder(8, 8, 8, 8)
        ));

        card.add(buildSectionTitle("Giao diện"));
        card.add(buildThemeOption("Sáng", FontAwesomeSolid.SUN, ThemeMode.LIGHT, tm, popup));
        card.add(Box.createVerticalStrut(2));
        card.add(buildThemeOption("Tối", FontAwesomeSolid.MOON, ThemeMode.DARK, tm, popup));

        card.add(Box.createVerticalStrut(6));
        card.add(buildDivider());
        card.add(Box.createVerticalStrut(4));

        card.add(buildSectionTitle("Thông báo"));
        card.add(buildToggleRow("Âm thanh thông báo", FontAwesomeSolid.VOLUME_UP,
            ns.isSoundEnabled(), ns::setSoundEnabled));

        if (showOrderMuteOption) {
            card.add(Box.createVerticalStrut(2));
            card.add(buildToggleRow("Ẩn thông báo đơn hàng mới", FontAwesomeSolid.BELL_SLASH,
                ns.isOrdersMuted(), ns::setOrdersMuted));

            JLabel hint = new JLabel("<html><div style='width:170px'>Đơn hàng vẫn được ghi lại đầy đủ, chỉ tạm ẩn số đếm và âm báo</div></html>");
            hint.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            hint.setForeground(AppColor.TEXT_MUTED);
            hint.setBorder(new EmptyBorder(4, 10, 2, 8));
            card.add(hint);
        }

        popup.add(card);
        popup.pack();
        popup.show(this, getWidth() - popup.getPreferredSize().width, -popup.getPreferredSize().height - 8);
    }

    private JLabel buildSectionTitle(String text) {
        JLabel title = new JLabel(text);
        title.setFont(new Font("Segoe UI", Font.BOLD, 12));
        title.setForeground(AppColor.TEXT_MUTED);
        title.setBorder(new EmptyBorder(2, 8, 8, 8));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        return title;
    }

    private JComponent buildDivider() {
        JSeparator sep = new JSeparator();
        sep.setForeground(AppColor.BORDER);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        return sep;
    }

    private JPanel buildThemeOption(String label, FontAwesomeSolid iconType, ThemeMode mode,
                                     ThemeManager tm, JPopupMenu popup) {
        boolean active = tm.getMode() == mode;

        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(true);
        row.setBackground(active ? AppColor.ACCENT_SELECTION_BG : AppColor.WHITE);
        row.setBorder(new EmptyBorder(8, 10, 8, 10));
        row.setCursor(new Cursor(Cursor.HAND_CURSOR));
        row.setMaximumSize(new Dimension(220, 40));
        row.setPreferredSize(new Dimension(200, 36));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        FontIcon icon = FontIcon.of(iconType, 14);
        icon.setIconColor(active ? AppColor.ACCENT : AppColor.TEXT_MUTED);
        JLabel iconLabel = new JLabel(icon);

        JLabel text = new JLabel(label);
        text.setFont(new Font("Segoe UI", active ? Font.BOLD : Font.PLAIN, 13));
        text.setForeground(active ? AppColor.ACCENT : AppColor.TEXT_PRIMARY);

        row.add(iconLabel, BorderLayout.WEST);
        row.add(text, BorderLayout.CENTER);

        if (active) {
            FontIcon check = FontIcon.of(FontAwesomeSolid.CHECK, 12);
            check.setIconColor(AppColor.ACCENT);
            row.add(new JLabel(check), BorderLayout.EAST);
        }

        row.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                popup.setVisible(false);
                if (tm.getMode() != mode) {
                    tm.setMode(mode);
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                row.setBackground(AppColor.BG_LIGHTER);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                row.setBackground(active ? AppColor.ACCENT_SELECTION_BG : AppColor.WHITE);
            }
        });
        return row;
    }

    /**
     * 1 dong cai dat dang bat/tat (label + icon ben trai, ToggleSwitch ben
     * phai). Bam vao BAT KY DAU trong dong (khong chi rieng cai cong tac)
     * cung doi trang thai, cho de bam hon tren man hinh nho.
     */
    private JPanel buildToggleRow(String label, FontAwesomeSolid iconType,
                                   boolean initialValue, java.util.function.Consumer<Boolean> onChange) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(true);
        row.setBackground(AppColor.WHITE);
        row.setBorder(new EmptyBorder(8, 10, 8, 8));
        row.setCursor(new Cursor(Cursor.HAND_CURSOR));
        row.setMaximumSize(new Dimension(220, 40));
        row.setPreferredSize(new Dimension(200, 36));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        FontIcon icon = FontIcon.of(iconType, 14);
        icon.setIconColor(AppColor.TEXT_MUTED);
        JLabel iconLabel = new JLabel(icon);

        JLabel text = new JLabel(label);
        text.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        text.setForeground(AppColor.TEXT_PRIMARY);

        JPanel left = new JPanel(new BorderLayout(8, 0));
        left.setOpaque(false);
        left.add(iconLabel, BorderLayout.WEST);
        left.add(text, BorderLayout.CENTER);

        ToggleSwitch toggle = new ToggleSwitch(initialValue);
        toggle.onChange(onChange);

        row.add(left, BorderLayout.CENTER);
        row.add(toggle, BorderLayout.EAST);

        MouseAdapter clickThrough = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                toggle.setSelected(!toggle.isSelected());
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                row.setBackground(AppColor.BG_LIGHTER);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                row.setBackground(AppColor.WHITE);
            }
        };
        row.addMouseListener(clickThrough);
        left.addMouseListener(clickThrough);

        return row;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Bong do nhe
        g2.setColor(AppShadow.MEDIUM);
        g2.fillOval(2, 3, SIZE - 4, SIZE - 4);

        g2.setColor(hover ? AppColor.ACCENT_HOVER : AppColor.ACCENT);
        g2.fillOval(0, 0, SIZE, SIZE);

        FontIcon icon = FontIcon.of(FontAwesomeSolid.COG, 20);
        icon.setIconColor(Color.WHITE);
        Icon i = icon;
        int ix = (SIZE - i.getIconWidth()) / 2;
        int iy = (SIZE - i.getIconHeight()) / 2;
        i.paintIcon(this, g2, ix, iy);

        g2.dispose();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(SIZE, SIZE);
    }
}