package com.view.layouts;

import com.i18n.Lang;
import com.model.NotificationItem;
import com.model.User;
import com.service.AuthService;
import com.utils.ImageUtil;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class Header extends JPanel {

    // ===== MAU SAC (dong bo phong cach dark voi Sidebar) =====
    private static final Color BG_COLOR = new Color(15, 23, 42);      // slate-900
    private static final Color BORDER_BOTTOM = new Color(30, 41, 59); // slate-800
    private static final Color ACCENT_COLOR = new Color(5, 150, 105);
    private static final Color SUBTITLE_COLOR = new Color(148, 163, 184);

    private static final Color DROPDOWN_BG = new Color(24, 31, 46);       // the tron toi
    private static final Color DROPDOWN_BORDER = new Color(51, 65, 85);   // slate-700
    private static final Color ROW_HOVER = new Color(38, 46, 64);
    private static final Color TEXT_WHITE = new Color(241, 245, 249);
    private static final Color TEXT_MUTED = new Color(148, 163, 184);
    private static final Color DANGER = new Color(248, 113, 113);         // red-400
    private static final Color RED_DOT = new Color(239, 68, 68);          // red-500
    private static final Color HOVER_OVERLAY = new Color(255, 255, 255, 18);

    private static final int AVATAR_SIZE = 36;
    private static final int EMAIL_MAX_WIDTH = 150;

    private final List<NotificationItem> notifications = new ArrayList<>();

    private Runnable profileListener;
    private Runnable logoutListener;
    private Runnable bellListener;
    private Consumer<NotificationItem> notificationClickListener;
    private Consumer<NotificationItem> notificationDismissListener;
    private Runnable clearAllListener;

    private BellIcon bellIcon;
    private FontIcon chevron;
    /** Panel danh sach ben trong dropdown chuong - giu lai de xoa tung dong (swipe/x) ma khong dong popup. */
    private JPanel notificationListPanel;
    private JPopupMenu notificationPopup;

    public Header() {
        this("Cửa hàng điện thoại trực tuyến");
    }

    public Header(String subtitle) {
        setLayout(new BorderLayout());
        setBackground(BG_COLOR);
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_BOTTOM),
            new EmptyBorder(12, 28, 12, 24)
        ));
        add(buildBrandSection(subtitle), BorderLayout.WEST);
        add(buildRightSection(), BorderLayout.EAST);
    }

    /** Tuong thich nguoc voi cac ban truoc (bell tung gan voi don hang/bao mat). */
    @Deprecated
    public Header(String subtitle, Runnable onBellViewOrders) {
        this(subtitle);
        onBellClick(onBellViewOrders);
    }

    @Deprecated
    public Header(String subtitle, Runnable onBellViewOrders, Runnable onBellViewSecurity) {
        this(subtitle);
        onBellClick(onBellViewOrders);
    }

    // ==================== Brand (trai) ====================

    private JPanel buildBrandSection(String subtitle) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        panel.setOpaque(false);

        // Logo từ classpath: src/main/resources/logo/logo.png
        JLabel logoLabel = new JLabel(loadLogoIcon(42));
        logoLabel.setPreferredSize(new Dimension(42, 42));

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("SIMS");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Segoe UI", Font.BOLD, 19));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitleLabel = new JLabel(subtitle);
        subtitleLabel.setForeground(SUBTITLE_COLOR);
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        textPanel.add(title);
        textPanel.add(Box.createVerticalStrut(2));
        textPanel.add(subtitleLabel);

        panel.add(logoLabel);
        panel.add(textPanel);
        return panel;
    }

    /** Load logo.png từ resources, scale về size x size. Fallback icon nếu thiếu file. */
    private static ImageIcon loadLogoIcon(int size) {
        java.net.URL url = Header.class.getResource("/logo/logo.png");
        if (url != null) {
            ImageIcon raw = new ImageIcon(url);
            Image scaled = raw.getImage().getScaledInstance(size, size, Image.SCALE_SMOOTH);
            return new ImageIcon(scaled);
        }
        // Fallback: badge + icon mobile nếu không tìm thấy logo
        BufferedImage fallback = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = fallback.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(ACCENT_COLOR);
        g2.fillRoundRect(0, 0, size, size, 12, 12);
        FontIcon icon = FontIcon.of(FontAwesomeSolid.MOBILE_ALT, Math.max(14, size / 2));
        icon.setIconColor(Color.WHITE);
        icon.paintIcon(null, g2, (size - icon.getIconWidth()) / 2, (size - icon.getIconHeight()) / 2);
        g2.dispose();
        return new ImageIcon(fallback);
    }

    // ==================== Ben phai: chuong + tai khoan ====================

    private JPanel buildRightSection() {
        JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT, 18, 0));
        wrapper.setOpaque(false);
        wrapper.add(buildBellButton());
        wrapper.add(buildAccountBadge());
        return wrapper;
    }

    // ---------- Chuong thong bao ----------

    private JComponent buildBellButton() {
        bellIcon = new BellIcon();
        bellIcon.setPreferredSize(new Dimension(38, 38));
        bellIcon.setCursor(new Cursor(Cursor.HAND_CURSOR));
        bellIcon.setToolTipText(bellTooltip());

        bellIcon.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (bellListener != null) {
                    bellListener.run();
                } else {
                    showNotificationDropdown();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                bellIcon.hover = true;
                bellIcon.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                bellIcon.hover = false;
                bellIcon.repaint();
            }
        });

        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setOpaque(false);
        wrapper.add(bellIcon);
        return wrapper;
    }

    /** Panel tu ve icon chuong (outline) + cham do khi co thong bao chua doc. */
    private class BellIcon extends JPanel {
        boolean hover = false;

        BellIcon() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (hover) {
                g2.setColor(HOVER_OVERLAY);
                g2.fillOval(0, 0, getWidth(), getHeight());
            }

            FontIcon icon = FontIcon.of(FontAwesomeSolid.BELL, 17);
            icon.setIconColor(new Color(203, 213, 225));
            int ix = (getWidth() - icon.getIconWidth()) / 2;
            int iy = (getHeight() - icon.getIconHeight()) / 2;
            icon.paintIcon(this, g2, ix, iy);

            if (!notifications.isEmpty()) {
                int dotSize = 9;
                int dx = ix + icon.getIconWidth() - dotSize + 3;
                int dy = iy - 2;
                g2.setColor(BG_COLOR);
                g2.fillOval(dx - 1, dy - 1, dotSize + 2, dotSize + 2);
                g2.setColor(RED_DOT);
                g2.fillOval(dx, dy, dotSize, dotSize);
            }
            g2.dispose();
        }
    }

    private static final int NOTIF_WIDTH = 320;
    private static final int SWIPE_DISMISS_THRESHOLD = 90;

    /** Mau nen + icon rieng cho tung loai thong bao (giong Facebook: moi loai 1 mau). */
    private static Color colorForType(NotificationItem.Type type) {
        switch (type) {
            case ORDER: return new Color(37, 99, 235);      // blue-600
            case INVOICE: return new Color(217, 119, 6);    // amber-600
            case STOCK: return new Color(220, 38, 38);       // red-600
            case MESSAGE: return new Color(5, 150, 105);     // emerald-600 (ACCENT_COLOR)
            case RETURN: return new Color(124, 58, 237);      // violet-600
            default: return new Color(100, 116, 139);        // slate-500
        }
    }

    private static FontAwesomeSolid iconForType(NotificationItem.Type type) {
        switch (type) {
            case ORDER: return FontAwesomeSolid.SHOPPING_CART;
            case INVOICE: return FontAwesomeSolid.FILE_INVOICE;
            case STOCK: return FontAwesomeSolid.EXCLAMATION_TRIANGLE;
            case MESSAGE: return FontAwesomeSolid.COMMENT_DOTS;
            case RETURN: return FontAwesomeSolid.EXCHANGE_ALT;
            default: return FontAwesomeSolid.BELL;
        }
    }

    private void showNotificationDropdown() {
        JPopupMenu popup = new JPopupMenu();
        popup.setBorder(new EmptyBorder(0, 0, 0, 0));
        popup.setOpaque(false);
        notificationPopup = popup;

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(DROPDOWN_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(DROPDOWN_BORDER, 1, true),
            new EmptyBorder(10, 0, 6, 0)
        ));

        JPanel headerRow = new JPanel(new BorderLayout());
        headerRow.setOpaque(false);
        headerRow.setBorder(new EmptyBorder(0, 12, 8, 12));
        headerRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerRow.setMaximumSize(new Dimension(NOTIF_WIDTH, 24));

        JLabel title = new JLabel(Lang.get("admin.header.notifications"));
        title.setFont(new Font("Segoe UI", Font.BOLD, 13));
        title.setForeground(TEXT_WHITE);
        headerRow.add(title, BorderLayout.WEST);

        if (!notifications.isEmpty()) {
            JLabel clearAll = new JLabel(Lang.get("admin.header.notifications.clearAll"));
            clearAll.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            clearAll.setForeground(ACCENT_COLOR);
            clearAll.setCursor(new Cursor(Cursor.HAND_CURSOR));
            clearAll.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    List<NotificationItem> snapshot = new ArrayList<>(notifications);
                    notifications.clear();
                    if (bellIcon != null) bellIcon.repaint();
                    popup.setVisible(false);
                    if (clearAllListener != null) {
                        clearAllListener.run();
                    } else if (notificationDismissListener != null) {
                        for (NotificationItem it : snapshot) notificationDismissListener.accept(it);
                    }
                }
            });
            headerRow.add(clearAll, BorderLayout.EAST);
        }
        card.add(headerRow);

        notificationListPanel = new JPanel();
        notificationListPanel.setLayout(new BoxLayout(notificationListPanel, BoxLayout.Y_AXIS));
        notificationListPanel.setOpaque(false);
        notificationListPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        rebuildNotificationRows();
        card.add(notificationListPanel);

        popup.add(card);
        popup.pack();
        popup.show(bellIcon, bellIcon.getWidth() - popup.getPreferredSize().width + 20, bellIcon.getHeight() + 8);
    }

    /** Ve lai toan bo danh sach dong thong bao ben trong dropdown (goi khi mo dropdown hoac sau khi xoa 1 dong). */
    private void rebuildNotificationRows() {
        if (notificationListPanel == null) return;
        notificationListPanel.removeAll();
        if (notifications.isEmpty()) {
            JLabel empty = new JLabel(Lang.get("admin.header.notifications.empty"));
            empty.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            empty.setForeground(TEXT_MUTED);
            empty.setBorder(new EmptyBorder(6, 16, 12, 16));
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            notificationListPanel.add(empty);
        } else {
            for (NotificationItem item : new ArrayList<>(notifications)) {
                notificationListPanel.add(new NotificationRow(item));
            }
        }
        notificationListPanel.revalidate();
        notificationListPanel.repaint();
        if (notificationPopup != null && notificationPopup.isVisible()) {
            notificationPopup.pack();
        }
    }

    /** Xoa 1 thong bao (do bam nut x hoac vuot qua nguong) khoi danh sach + goi callback ra ngoai. */
    private void dismissNotification(NotificationItem item) {
        notifications.remove(item);
        if (bellIcon != null) {
            bellIcon.setToolTipText(bellTooltip());
            bellIcon.repaint();
        }
        rebuildNotificationRows();
        if (notificationDismissListener != null) notificationDismissListener.accept(item);
    }

    /**
     * Mot dong thong bao: icon mau theo loai + tieu de/noi dung/thoi gian + nut "x", ho tro:
     *  - Bam vao dong (ngoai vung nut x) -> notificationClickListener + dong popup.
     *  - Keo ngang (chuot trai giu + ru) qua nguong -> tu dong xoa dong (giong vuot xoa tren dien thoai).
     *  - Bam nut x -> xoa ngay dong nay.
     * Ky thuat: outer track dung null-layout, content la 1 JPanel duoc doi vi tri (setLocation) khi keo
     * de tao cam giac "truot" ma khong can lam lai toan bo layout cua popup.
     */
    private class NotificationRow extends JPanel {
        private final JPanel content;
        private int dragStartX = -1;
        private int currentOffset = 0;

        NotificationRow(NotificationItem item) {
            super(null);
            setOpaque(false);
            int rowHeight = (item.getMessage() != null && !item.getMessage().isBlank()) ? 62 : 46;
            setPreferredSize(new Dimension(NOTIF_WIDTH, rowHeight));
            setMaximumSize(new Dimension(NOTIF_WIDTH, rowHeight));
            setAlignmentX(Component.LEFT_ALIGNMENT);

            content = buildContent(item, rowHeight);
            content.setBounds(0, 0, NOTIF_WIDTH, rowHeight);
            add(content);

            MouseAdapter dragHandler = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    dragStartX = e.getXOnScreen();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (dragStartX < 0) return;
                    boolean dragged = Math.abs(currentOffset) > 6;
                    if (Math.abs(currentOffset) >= SWIPE_DISMISS_THRESHOLD) {
                        dismissNotification(item);
                    } else {
                        currentOffset = 0;
                        content.setBounds(0, 0, NOTIF_WIDTH, rowHeight);
                        content.setBackground(DROPDOWN_BG);
                    }
                    dragStartX = -1;
                    if (!dragged) {
                        // Coi la click (khong keo): dieu huong + dong dropdown.
                        if (notificationPopup != null) notificationPopup.setVisible(false);
                        if (notificationClickListener != null) notificationClickListener.accept(item);
                    }
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    if (currentOffset == 0) content.setBackground(ROW_HOVER);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    if (currentOffset == 0) content.setBackground(DROPDOWN_BG);
                }
            };
            content.addMouseListener(dragHandler);
            content.addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseDragged(MouseEvent e) {
                    if (dragStartX < 0) return;
                    int delta = e.getXOnScreen() - dragStartX;
                    currentOffset = Math.max(-NOTIF_WIDTH, Math.min(NOTIF_WIDTH, delta));
                    content.setBounds(currentOffset, 0, NOTIF_WIDTH, rowHeight);
                }
            });
        }

        private JPanel buildContent(NotificationItem item, int rowHeight) {
            JPanel row = new JPanel(new BorderLayout(10, 0));
            row.setOpaque(true);
            row.setBackground(DROPDOWN_BG);
            row.setBorder(new EmptyBorder(8, 16, 8, 10));
            row.setCursor(new Cursor(Cursor.HAND_CURSOR));

            // Icon tron mau theo loai thong bao.
            JLabel iconLabel = new JLabel();
            iconLabel.setPreferredSize(new Dimension(34, 34));
            iconLabel.setOpaque(false);
            Color typeColor = colorForType(item.getType());
            FontAwesomeSolid iconType = iconForType(item.getType());
            iconLabel.setIcon(circleIcon(iconType, typeColor, 34));
            JPanel iconWrap = new JPanel(new GridBagLayout());
            iconWrap.setOpaque(false);
            iconWrap.add(iconLabel);
            row.add(iconWrap, BorderLayout.WEST);

            JPanel textPanel = new JPanel();
            textPanel.setOpaque(false);
            textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

            JLabel titleLabel = new JLabel(item.getTitle());
            titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
            titleLabel.setForeground(TEXT_WHITE);
            titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            textPanel.add(titleLabel);

            if (item.getMessage() != null && !item.getMessage().isBlank()) {
                JLabel msgLabel = new JLabel("<html><div style='width:200px'>" + item.getMessage() + "</div></html>");
                msgLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                msgLabel.setForeground(TEXT_MUTED);
                msgLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                textPanel.add(msgLabel);
            }

            String time = item.getRelativeTime();
            if (time != null && !time.isBlank()) {
                JLabel timeLabel = new JLabel(time);
                timeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                timeLabel.setForeground(TEXT_MUTED);
                timeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                textPanel.add(Box.createVerticalStrut(2));
                textPanel.add(timeLabel);
            }

            row.add(textPanel, BorderLayout.CENTER);

            // Nut "x" xoa rieng dong nay.
            JLabel closeBtn = new JLabel(FontIcon.of(FontAwesomeSolid.TIMES, 11));
            closeBtn.setForeground(TEXT_MUTED);
            ((FontIcon) closeBtn.getIcon()).setIconColor(TEXT_MUTED);
            closeBtn.setBorder(new EmptyBorder(2, 6, 2, 2));
            closeBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            closeBtn.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    dismissNotification(item);
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    ((FontIcon) closeBtn.getIcon()).setIconColor(DANGER);
                    closeBtn.repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    ((FontIcon) closeBtn.getIcon()).setIconColor(TEXT_MUTED);
                    closeBtn.repaint();
                }
            });
            JPanel closeWrap = new JPanel(new GridBagLayout());
            closeWrap.setOpaque(false);
            closeWrap.add(closeBtn);
            row.add(closeWrap, BorderLayout.EAST);

            return row;
        }
    }

    /** Ve icon dang tron mau (nen mau + icon trang o giua) - dung cho tung dong thong bao. */
    private static ImageIcon circleIcon(FontAwesomeSolid iconType, Color bg, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(bg);
        g2.fillOval(0, 0, size, size);
        FontIcon icon = FontIcon.of(iconType, (int) (size * 0.5));
        icon.setIconColor(Color.WHITE);
        icon.paintIcon(null, g2, (size - icon.getIconWidth()) / 2, (size - icon.getIconHeight()) / 2);
        g2.dispose();
        return new ImageIcon(img);
    }

    /** Thay danh sach thong bao hien trong dropdown chuong; rong -> hien "Chưa có thông báo mới". */
    public void setNotifications(List<NotificationItem> items) {
        notifications.clear();
        if (items != null) notifications.addAll(items);
        if (bellIcon != null) {
            bellIcon.setToolTipText(bellTooltip());
            bellIcon.repaint();
        }
        if (notificationPopup != null && notificationPopup.isVisible()) {
            rebuildNotificationRows();
        }
    }

    /**
     * Bat/tat cham do tren chuong. KHONG xoa danh sach (de gop order + chat).
     * Cham do khi ve dua vao !notifications.isEmpty().
     */
    public void setNotificationBadge(boolean hasUnread) {
        if (bellIcon != null) {
            bellIcon.setToolTipText(bellTooltip());
            bellIcon.repaint();
        }
    }

    public void upsertNotification(NotificationItem item) {
        if (item == null) return;
        notifications.removeIf(n -> item.getId().equals(n.getId()));
        notifications.add(0, item);
        if (bellIcon != null) {
            bellIcon.setToolTipText(bellTooltip());
            bellIcon.repaint();
        }
        if (notificationPopup != null && notificationPopup.isVisible()) {
            rebuildNotificationRows();
        }
    }

    public void removeNotificationById(String id) {
        if (id == null) return;
        notifications.removeIf(n -> id.equals(n.getId()));
        if (bellIcon != null) bellIcon.repaint();
        if (notificationPopup != null && notificationPopup.isVisible()) {
            rebuildNotificationRows();
        }
    }

    /** Tooltip phan anh dung trang thai hien tai cua chuong. */
    private String bellTooltip() {
        return notifications.isEmpty()
            ? Lang.get("admin.header.notifications.empty")
            : Lang.get("admin.header.notifications");
    }

    /** Neu duoc gan, bam chuong se chay callback nay thay vi mo dropdown mac dinh. */
    public void onBellClick(Runnable listener) {
        this.bellListener = listener;
    }

    /** Goi khi nguoi dung bam vao mot dong thong bao (khong phai nut x) - dung de dieu huong. */
    public void onNotificationClick(Consumer<NotificationItem> listener) {
        this.notificationClickListener = listener;
    }

    /** Goi khi mot dong thong bao bi xoa (bam x, vuot qua nguong, hoac "Xóa tất cả" tung dong). */
    public void onNotificationDismiss(Consumer<NotificationItem> listener) {
        this.notificationDismissListener = listener;
    }

    /** Neu duoc gan, se duoc goi rieng khi bam "Xóa tất cả" thay vi lap qua onNotificationDismiss. */
    public void onClearAllNotifications(Runnable listener) {
        this.clearAllListener = listener;
    }

    // ---------- Khoi tai khoan (avatar + ten + email + dropdown) ----------

    private JPanel buildAccountBadge() {
        User user = AuthService.getInstance().isLoggedIn() ? AuthService.getInstance().getCurrentUser() : null;
        String displayName = user != null ? user.getFullName() : Lang.get("admin.header.guest");
        String email = user != null ? user.getEmail() : "";

        JPanel badge = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getMousePosition() != null) {
                    g2.setColor(HOVER_OVERLAY);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        badge.setOpaque(false);
        badge.setBorder(new EmptyBorder(4, 8, 4, 8));
        badge.setCursor(new Cursor(Cursor.HAND_CURSOR));

        String initials = (displayName == null || displayName.isBlank()) ? "?" : displayName.trim();
        ImageIcon avatarIcon = ImageUtil.circularIcon(user != null ? user.getAvatarUrl() : null, AVATAR_SIZE, initials);
        JLabel avatarLabel = new JLabel(avatarIcon);

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

        JLabel nameLabel = new JLabel(displayName);
        nameLabel.setForeground(TEXT_WHITE);
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel emailLabel = new JLabel(truncate(email, new Font("Segoe UI", Font.PLAIN, 11), EMAIL_MAX_WIDTH));
        emailLabel.setForeground(TEXT_MUTED);
        emailLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        emailLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        textPanel.add(nameLabel);
        if (email != null && !email.isBlank()) {
            textPanel.add(Box.createVerticalStrut(1));
            textPanel.add(emailLabel);
        }

        chevron = FontIcon.of(FontAwesomeSolid.CHEVRON_DOWN, 10);
        chevron.setIconColor(TEXT_MUTED);
        JLabel chevronLabel = new JLabel(chevron);

        badge.add(avatarLabel);
        badge.add(textPanel);
        badge.add(chevronLabel);

        JPopupMenu menu = buildAccountPopup(user, displayName, email, avatarIcon);
        menu.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                chevron.setIkon(FontAwesomeSolid.CHEVRON_UP);
                chevronLabel.repaint();
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                chevron.setIkon(FontAwesomeSolid.CHEVRON_DOWN);
                chevronLabel.repaint();
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
            }
        });

        badge.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                menu.show(badge, badge.getWidth() - 190, badge.getHeight() + 8);
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                badge.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                badge.repaint();
            }
        });

        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setOpaque(false);
        wrapper.add(badge);
        return wrapper;
    }

    private JPopupMenu buildAccountPopup(User user, String displayName, String email, ImageIcon avatarIcon) {
        JPopupMenu popup = new JPopupMenu();
        popup.setBorder(new EmptyBorder(0, 0, 0, 0));
        popup.setOpaque(false);

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(DROPDOWN_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(DROPDOWN_BORDER, 1, true),
            new EmptyBorder(8, 8, 8, 8)
        ));

        card.add(buildInfoSection(user, displayName, email, avatarIcon));
        card.add(Box.createVerticalStrut(6));
        card.add(buildDivider());
        card.add(Box.createVerticalStrut(6));

        card.add(buildMenuRow(Lang.get("admin.header.profile"), FontAwesomeSolid.USER, TEXT_WHITE, popup, () -> {
            if (profileListener != null) profileListener.run();
        }));
        card.add(Box.createVerticalStrut(6));
        card.add(buildDivider());
        card.add(Box.createVerticalStrut(6));
        card.add(buildMenuRow(Lang.get("admin.header.logout"), FontAwesomeSolid.SIGN_OUT_ALT, DANGER, popup, () -> {
            if (logoutListener != null) logoutListener.run();
        }));

        popup.add(card);
        popup.pack();
        return popup;
    }

    /** Khoi thong tin ca nhan hien o dau dropdown: avatar + ho ten + email + SDT (neu co) - du lieu that tu User dang dang nhap. */
    private JPanel buildInfoSection(User user, String displayName, String email, ImageIcon avatarIcon) {
        JPanel section = new JPanel(new BorderLayout(10, 0));
        section.setOpaque(false);
        section.setBorder(new EmptyBorder(4, 6, 4, 6));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.setMaximumSize(new Dimension(240, 60));

        JLabel avatarLabel = new JLabel(avatarIcon);
        section.add(avatarLabel, BorderLayout.WEST);

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

        JLabel nameLabel = new JLabel(displayName);
        nameLabel.setForeground(TEXT_WHITE);
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        textPanel.add(nameLabel);

        if (email != null && !email.isBlank()) {
            JLabel emailLabel = new JLabel(truncate(email, new Font("Segoe UI", Font.PLAIN, 11), 170));
            emailLabel.setForeground(TEXT_MUTED);
            emailLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            emailLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            textPanel.add(Box.createVerticalStrut(2));
            textPanel.add(emailLabel);
        }

        String phone = user != null ? user.getPhone() : null;
        if (phone != null && !phone.isBlank()) {
            JLabel phoneLabel = new JLabel(phone);
            phoneLabel.setForeground(TEXT_MUTED);
            phoneLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            phoneLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            textPanel.add(Box.createVerticalStrut(2));
            textPanel.add(phoneLabel);
        }

        section.add(textPanel, BorderLayout.CENTER);
        return section;
    }

    private JPanel buildMenuRow(String label, FontAwesomeSolid iconType, Color fg, JPopupMenu popup, Runnable action) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(true);
        row.setBackground(DROPDOWN_BG);
        row.setBorder(new EmptyBorder(9, 10, 9, 14));
        row.setCursor(new Cursor(Cursor.HAND_CURSOR));
        row.setMaximumSize(new Dimension(220, 40));
        row.setPreferredSize(new Dimension(190, 38));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        FontIcon icon = FontIcon.of(iconType, 14);
        icon.setIconColor(fg);
        row.add(new JLabel(icon), BorderLayout.WEST);

        JLabel text = new JLabel(label);
        text.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        text.setForeground(fg);
        text.setBorder(new EmptyBorder(0, 8, 0, 0));
        row.add(text, BorderLayout.CENTER);

        row.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                popup.setVisible(false);
                action.run();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                row.setBackground(ROW_HOVER);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                row.setBackground(DROPDOWN_BG);
            }
        });
        return row;
    }

    private JComponent buildDivider() {
        JSeparator sep = new JSeparator();
        sep.setForeground(DROPDOWN_BORDER);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        return sep;
    }

    /** Cat chuoi + them "..." neu vuot qua maxWidth (px) theo font truyen vao - dung cho email dai. */
    private String truncate(String text, Font font, int maxWidth) {
        if (text == null || text.isBlank()) return "";
        FontMetrics fm = getFontMetrics(font);
        if (fm.stringWidth(text) <= maxWidth) return text;

        String ellipsis = "...";
        int ellipsisWidth = fm.stringWidth(ellipsis);
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (fm.stringWidth(sb.toString() + c) + ellipsisWidth > maxWidth) break;
            sb.append(c);
        }
        return sb + ellipsis;
    }

    // ==================== Callback tu ben ngoai ====================

    public void onProfile(Runnable listener) {
        this.profileListener = listener;
    }

    public void onLogout(Runnable listener) {
        this.logoutListener = listener;
    }
}