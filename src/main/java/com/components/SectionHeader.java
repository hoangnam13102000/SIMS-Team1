package com.components;

import com.theme.AppColor;
import com.theme.AppFont;
import com.theme.AppSpacing;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;


public class SectionHeader extends JPanel {

    public enum ButtonStyle { PRIMARY, OUTLINE, NEUTRAL }

    private final JPanel breadcrumbRow;
    private final JPanel actionsRow;
    private final JLabel titleLabel;
    private final JLabel subtitleLabel;

    // Menu "..." gop cac hanh dong PHU/it dung (export, import, thung rac...)
    // - tao "lazy" (chi tao khi co action overflow dau tien duoc them), tranh
    // header bi tran nut khi 1 trang co nhieu hanh dong (xem addOverflowAction()).
    // Tu ve tung dong bang JPanel (KHONG dung JMenuItem mac dinh) de kiem
    // soat hoan toan giao dien (bo goc, padding, hover) thay vi bi phu thuoc
    // vao style JMenuItem tho/xau cua Look&Feel dang dung.
    private JPopupMenu overflowMenu;
    private JPanel overflowListPanel;
    private JButton overflowButton;

    public SectionHeader(FontAwesomeSolid icon, Color iconColor, String title, String subtitle) {
        setOpaque(true);
        setBackground(AppColor.WHITE);
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(AppSpacing.LG, AppSpacing.XL, AppSpacing.LG, AppSpacing.XL)));

        breadcrumbRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        breadcrumbRow.setOpaque(false);
        breadcrumbRow.setBorder(new EmptyBorder(0, 0, AppSpacing.SM, 0));
        breadcrumbRow.setVisible(false);

        JPanel contentRow = new JPanel(new BorderLayout());
        contentRow.setOpaque(false);

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.X_AXIS));

        JLabel iconLabel = buildIconCircle(icon, iconColor);

        JPanel textCol = new JPanel();
        textCol.setOpaque(false);
        textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));

        titleLabel = new JLabel(title);
        titleLabel.setFont(AppFont.HEADING_LG);
        titleLabel.setForeground(AppColor.TEXT_TITLE);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        subtitleLabel = new JLabel(subtitle == null ? "" : subtitle);
        subtitleLabel.setFont(AppFont.BODY);
        subtitleLabel.setForeground(AppColor.TEXT_MUTED);
        subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        textCol.add(titleLabel);
        if (subtitle != null && !subtitle.isBlank()) {
            textCol.add(Box.createVerticalStrut(3));
            textCol.add(subtitleLabel);
        }

        left.add(iconLabel);
        left.add(Box.createHorizontalStrut(AppSpacing.MD));
        left.add(textCol);

        actionsRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, AppSpacing.SM, 0));
        actionsRow.setOpaque(false);

        contentRow.add(left, BorderLayout.WEST);
        contentRow.add(actionsRow, BorderLayout.EAST);

        add(breadcrumbRow, BorderLayout.NORTH);
        add(contentRow, BorderLayout.CENTER);
    }

    private JLabel buildIconCircle(FontAwesomeSolid iconType, Color color) {
        int size = 44;
        FontIcon fontIcon = FontIcon.of(iconType, 18);
        fontIcon.setIconColor(color);

        JLabel circle = new JLabel(fontIcon) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(softVariant(color));
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        circle.setPreferredSize(new Dimension(size, size));
        circle.setMinimumSize(new Dimension(size, size));
        circle.setMaximumSize(new Dimension(size, size));
        circle.setHorizontalAlignment(SwingConstants.CENTER);
        circle.setVerticalAlignment(SwingConstants.CENTER);
        return circle;
    }

    private static Color softVariant(Color base) {
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), 40);
    }

    /** Hien breadcrumb phia tren tieu de. Goi voi mang rong hoac null de an di. */
    public SectionHeader setBreadcrumb(String... items) {
        breadcrumbRow.removeAll();
        if (items != null && items.length > 0) {
            for (int i = 0; i < items.length; i++) {
                if (i == 0) {
                    FontIcon homeIcon = FontIcon.of(FontAwesomeSolid.HOME, 12);
                    homeIcon.setIconColor(AppColor.TEXT_MUTED);
                    JLabel crumb = new JLabel(items[i], homeIcon, SwingConstants.LEFT);
                    crumb.setIconTextGap(6);
                    crumb.setFont(AppFont.SMALL);
                    crumb.setForeground(AppColor.TEXT_MUTED);
                    breadcrumbRow.add(crumb);
                } else {
                    FontIcon chevron = FontIcon.of(FontAwesomeSolid.CHEVRON_RIGHT, 9);
                    chevron.setIconColor(AppColor.TEXT_DISABLED);
                    breadcrumbRow.add(new JLabel(chevron));

                    boolean isLast = (i == items.length - 1);
                    JLabel crumb = new JLabel(items[i]);
                    crumb.setFont(isLast ? AppFont.SMALL_BOLD : AppFont.SMALL);
                    crumb.setForeground(isLast ? AppColor.TEXT_PRIMARY : AppColor.TEXT_MUTED);
                    if (isLast) {
                        crumb.setOpaque(true);
                        crumb.setBackground(AppColor.BG_LIGHTER);
                        crumb.setBorder(new EmptyBorder(3, 10, 3, 10));
                    }
                    breadcrumbRow.add(crumb);
                }
            }
            breadcrumbRow.setVisible(true);
        } else {
            breadcrumbRow.setVisible(false);
        }
        revalidate();
        repaint();
        return this;
    }

    /** Them 1 component bat ky vao day hanh dong ben phai (search bar, date picker, combo...). */
    public SectionHeader addAction(JComponent component) {
        actionsRow.add(component);
        return this;
    }

    /** Them vach ngan doc "|" giua 2 nhom hanh dong. */
    public SectionHeader addDivider() {
        JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
        sep.setPreferredSize(new Dimension(1, 24));
        sep.setForeground(AppColor.BORDER);
        actionsRow.add(sep);
        return this;
    }

    /**
     * Them 1 hanh dong vao menu "..." GOP CHUNG (thay vi hien thanh 1 nut
     * rieng nhu addButton()) - dung cho cac hanh dong PHU, IT DUNG (xuat
     * file, nhap file, thung rac...) de tranh header bi TRAN NUT khi 1 trang
     * co nhieu hanh dong. Chi nen giu lai addButton() rieng cho 1-2 hanh dong
     * CHINH/hay dung nhat (vd nut "Them..." mau primary).
     * <p>
     * Nut "..." duoc tao "lazy" - chi xuat hien khi co it nhat 1 hanh dong
     * overflow duoc them; goi nhieu lan (ke ca sau khi header da duoc hien
     * thi) deu an toan, cac hanh dong moi se duoc them tiep vao menu co san.
     */
    public SectionHeader addOverflowAction(String text, FontAwesomeSolid icon, Runnable onClick) {
        ensureOverflowButton();
        overflowListPanel.add(buildOverflowRow(text, icon, onClick));

        // An toan cho truong hop goi SAU khi header da hien thi (vd nut
        // "Thùng rác" duoc them muon tu initialLoad(), co the la lan dau
        // tien overflowButton duoc tao khi trang khong co export/import) -
        // revalidate/repaint de dam bao nut moi xuat hien dung.
        revalidate();
        repaint();
        return this;
    }

    private void ensureOverflowButton() {
        if (overflowButton != null) return;

        overflowListPanel = new JPanel();
        overflowListPanel.setLayout(new BoxLayout(overflowListPanel, BoxLayout.Y_AXIS));
        overflowListPanel.setBackground(AppColor.WHITE);
        // Padding DEU 4 phia (truoc chi co tren/duoi) - de dai hover bo goc
        // ben trong khong bao gio cham sat vien card, giong tham khao.
        overflowListPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        overflowMenu = new JPopupMenu();
        overflowMenu.setBackground(AppColor.WHITE);
        overflowMenu.setBorder(BorderFactory.createLineBorder(AppColor.BORDER, 1, true));
        overflowMenu.add(overflowListPanel);

        FontIcon chevronIcon = FontIcon.of(FontAwesomeSolid.CHEVRON_DOWN, 10);
        chevronIcon.setIconColor(AppColor.ACCENT);

        // Nut co NHAN RO RANG ("Tuy chon" + mui ten xuong) thay vi chi icon
        // "..." tron - "..." la quy uoc quen thuoc voi nguoi dung cac app
        // lon (Gmail, YouTube...) nhung voi 1 app noi bo, nguoi dung khong
        // dung lai nhieu quy uoc do moi ngay, nen 1 nhan chu ro nghia giup de
        // nhan biet chuc nang ngay tu lan dau, giam thoi gian lam quen.
        overflowButton = new JButton("Tùy chọn", chevronIcon);
        // Icon (chevron) dat SAU chu ("Tùy chọn v") - horizontalTextPosition
        // LEFT nghia la CHU nam ben trai icon, tuc icon nam ben phai chu.
        overflowButton.setHorizontalTextPosition(SwingConstants.LEFT);
        overflowButton.setIconTextGap(8);
        overflowButton.setFont(AppFont.BUTTON.deriveFont(13f));
        overflowButton.setForeground(AppColor.ACCENT);
        overflowButton.setBackground(AppColor.WHITE);
        overflowButton.setFocusPainted(false);
        overflowButton.setOpaque(true);
        overflowButton.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColor.ACCENT, 1, true),
                new EmptyBorder(8, 14, 8, 14)));
        overflowButton.setCursor(new Cursor(Cursor.HAND_CURSOR));

        Color outlineBg = AppColor.WHITE;
        Color outlineHoverBg = new Color(AppColor.ACCENT.getRed(), AppColor.ACCENT.getGreen(), AppColor.ACCENT.getBlue(), 30);
        overflowButton.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { overflowButton.setBackground(outlineHoverBg); }
            @Override public void mouseExited(MouseEvent e) { overflowButton.setBackground(outlineBg); }
        });
        overflowButton.addActionListener(e -> overflowMenu.show(overflowButton, 0, overflowButton.getHeight() + 4));

        actionsRow.add(overflowButton);
    }


    /**
     * 1 dong trong menu "...": icon nhat mau + chu, padding rong rai. Hover
     * la 1 dai BO GOC THUT VAO MEP (khong phai nen vuong cham sat vien) -
     * tu ve bang paintComponent (khong dung JMenuItem) de dong bo voi ngon
     * ngu thiet ke cua BaseSearch/NotificationBell.
     */
    private JComponent buildOverflowRow(String text, FontAwesomeSolid iconType, Runnable onClick) {
        boolean[] hovered = {false};

        JPanel row = new JPanel(new BorderLayout(12, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                if (hovered[0]) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(AppColor.BG_LIGHTER);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(10, 12, 10, 20));
        row.setCursor(new Cursor(Cursor.HAND_CURSOR));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));

        FontIcon icon = FontIcon.of(iconType, 14);
        icon.setIconColor(AppColor.TEXT_MUTED);
        JLabel iconLabel = new JLabel(icon);

        JLabel textLabel = new JLabel(text);
        textLabel.setFont(AppFont.BODY);
        textLabel.setForeground(AppColor.TEXT_PRIMARY);

        row.add(iconLabel, BorderLayout.WEST);
        row.add(textLabel, BorderLayout.CENTER);

        row.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hovered[0] = true;
                row.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hovered[0] = false;
                row.repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                overflowMenu.setVisible(false);
                if (onClick != null) onClick.run();
            }
        });

        return row;
    }

    public JButton addButton(String text, FontAwesomeSolid icon, ButtonStyle style, Runnable onClick) {
        return addButton(text, icon, style, AppColor.ACCENT, onClick);
    }

    public JButton addButton(String text, FontAwesomeSolid icon, ButtonStyle style, Color accentColor, Runnable onClick) {
        JButton button = buildButton(text, icon, style, accentColor);
        button.addActionListener(e -> { if (onClick != null) onClick.run(); });
        actionsRow.add(button);
        return button;
    }

    private JButton buildButton(String text, FontAwesomeSolid iconType, ButtonStyle style, Color accent) {
        Color bg, hoverBg, fg, borderColor;
        switch (style) {
            case OUTLINE:
                bg = AppColor.WHITE;
                hoverBg = softVariant(accent);
                fg = accent;
                borderColor = accent;
                break;
            case NEUTRAL:
                bg = AppColor.WHITE;
                hoverBg = AppColor.BG_LIGHTER;
                fg = AppColor.TEXT_PRIMARY;
                borderColor = AppColor.FIELD_BORDER;
                break;
            case PRIMARY:
            default:
                bg = accent;
                hoverBg = accent.darker();
                fg = AppColor.WHITE;
                borderColor = accent;
        }

        FontIcon icon = FontIcon.of(iconType, 12);
        icon.setIconColor(fg);

        JButton button = new JButton(text, icon);
        button.setIconTextGap(8);
        button.setFont(AppFont.BUTTON.deriveFont(13f));
        button.setForeground(fg);
        button.setBackground(bg);
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor, 1, true),
                new EmptyBorder(8, 14, 8, 14)));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { button.setBackground(hoverBg); }
            @Override public void mouseExited(MouseEvent e) { button.setBackground(bg); }
        });
        return button;
    }

    public SectionHeader setTitle(String title) {
        titleLabel.setText(title);
        return this;
    }

    public SectionHeader setSubtitle(String subtitle) {
        subtitleLabel.setText(subtitle);
        return this;
    }
}