package com.components;

import com.i18n.Lang;
import com.i18n.LanguageManager;
import com.settings.NotificationSettings;
import com.theme.AccentColor;
import com.theme.AppColor;
import com.theme.AppShadow;
import com.theme.ThemeManager;
import com.theme.ThemeMode;

import java.util.Locale;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class SettingsButton extends JPanel {

    private static final int SIZE = 52;
    private static final int MARGIN = 24;
    /** Chieu rong dong menu: vua khop nhan dai nhat (khong cat chu, khong thua trang). */
    private static final int MENU_ROW_WIDTH = 310;

    private boolean hover = false;
    private boolean showOrderMuteOption = true;

    private SettingsButton() {
        setOpaque(false);
        setCursor(new Cursor(Cursor.HAND_CURSOR));
        setToolTipText(Lang.get("settings.tooltip"));

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
            new EmptyBorder(12, 10, 12, 10)
        ));

        card.add(buildSectionTitle(Lang.get("settings.section.appearance")));
        card.add(buildThemeOption(Lang.get("settings.theme.light"), FontAwesomeSolid.SUN, ThemeMode.LIGHT, tm, popup));
        card.add(Box.createVerticalStrut(4));
        card.add(buildThemeOption(Lang.get("settings.theme.dark"), FontAwesomeSolid.MOON, ThemeMode.DARK, tm, popup));

        // ==== Mau chu dao (Accent color) ====
        card.add(Box.createVerticalStrut(10));
        card.add(buildSectionTitle(Lang.get("settings.section.accent")));
        card.add(buildAccentSwatches(tm, popup));

        card.add(Box.createVerticalStrut(10));
        card.add(buildDivider());
        card.add(Box.createVerticalStrut(8));

        card.add(buildSectionTitle(Lang.get("settings.section.notification")));
        card.add(buildToggleRow(Lang.get("settings.notification.sound"), FontAwesomeSolid.VOLUME_UP,
            ns.isSoundEnabled(), ns::setSoundEnabled));

        if (showOrderMuteOption) {
            card.add(Box.createVerticalStrut(4));
            card.add(buildToggleRow(Lang.get("settings.notification.muteOrders"), FontAwesomeSolid.BELL_SLASH,
                ns.isOrdersMuted(), ns::setOrdersMuted));
        }

        card.add(Box.createVerticalStrut(10));
        card.add(buildDivider());
        card.add(Box.createVerticalStrut(8));

        // ==== Ngôn ngữ / Language ====
        LanguageManager lm = LanguageManager.getInstance();
        card.add(buildSectionTitle(Lang.get("settings.section.language")));
        card.add(buildLanguageOption(Lang.get("settings.language.vi"), new Locale("vi"), lm, popup));
        card.add(Box.createVerticalStrut(4));
        card.add(buildLanguageOption(Lang.get("settings.language.en"), Locale.ENGLISH, lm, popup));

        popup.add(card);
        popup.pack();
        popup.show(this, getWidth() - popup.getPreferredSize().width, -popup.getPreferredSize().height - 8);
    }

    private JLabel buildSectionTitle(String text) {
        JLabel title = new JLabel(text);
        title.setFont(new Font("Segoe UI", Font.BOLD, 12));
        title.setForeground(AppColor.TEXT_MUTED);
        title.setBorder(new EmptyBorder(4, 8, 10, 8));
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
        row.setBorder(new EmptyBorder(10, 12, 10, 12));
        row.setCursor(new Cursor(Cursor.HAND_CURSOR));
        row.setMaximumSize(new Dimension(MENU_ROW_WIDTH, 46));
        row.setPreferredSize(new Dimension(MENU_ROW_WIDTH, 42));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        FontIcon icon = FontIcon.of(iconType, 14);
        icon.setIconColor(active ? AppColor.ACCENT : AppColor.TEXT_MUTED);
        JLabel iconLabel = new JLabel(icon);
        iconLabel.setPreferredSize(new Dimension(24, 20));
        iconLabel.setMinimumSize(new Dimension(24, 20));
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);

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

    /** Hang cac vien tron mau (swatch) de nguoi dung chon mau chu dao (accent) cho toan app. */
    private JPanel buildAccentSwatches(ThemeManager tm, JPopupMenu popup) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(2, 8, 2, 8));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(MENU_ROW_WIDTH, 40));

        for (AccentColor accent : AccentColor.values()) {
            row.add(buildAccentSwatch(accent, tm, popup));
        }
        return row;
    }

    /** 1 vien tron mau bam duoc - dau check hien khi dang la mau dang chon. */
    private JComponent buildAccentSwatch(AccentColor accent, ThemeManager tm, JPopupMenu popup) {
        final int DOT = 26;
        Color swatchColor = accent.getSwatch();

        JComponent swatch = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                boolean active = tm.getAccent() == accent;
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                g2.setColor(swatchColor);
                g2.fillOval(3, 3, DOT - 6, DOT - 6);

                if (active) {
                    g2.setColor(AppColor.TEXT_PRIMARY);
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawOval(1, 1, DOT - 3, DOT - 3);

                    FontIcon check = FontIcon.of(FontAwesomeSolid.CHECK, 10);
                    check.setIconColor(isLightColor(swatchColor) ? Color.BLACK : Color.WHITE);
                    int cx = (DOT - check.getIconWidth()) / 2;
                    int cy = (DOT - check.getIconHeight()) / 2;
                    check.paintIcon(this, g2, cx, cy);
                }
                g2.dispose();
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(DOT, DOT);
            }
        };
        swatch.setToolTipText(Lang.get(accent.getI18nKey()));
        swatch.setCursor(new Cursor(Cursor.HAND_CURSOR));
        swatch.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                popup.setVisible(false);
                if (tm.getAccent() != accent) {
                    tm.setAccent(accent);
                }
            }
        });
        return swatch;
    }

    /** Do sang xap xi (YIQ) de biet nen ve dau check mau den hay trang cho de nhin tren swatch. */
    private static boolean isLightColor(Color c) {
        double yiq = (c.getRed() * 299 + c.getGreen() * 587 + c.getBlue() * 114) / 1000.0;
        return yiq > 150;
    }

    /** Giong het buildThemeOption(...) nhung cho lua chon ngon ngu (Locale). */
    private JPanel buildLanguageOption(String label, Locale locale, LanguageManager lm, JPopupMenu popup) {
        boolean active = lm.getLocale().getLanguage().equals(locale.getLanguage());

        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(true);
        row.setBackground(active ? AppColor.ACCENT_SELECTION_BG : AppColor.WHITE);
        row.setBorder(new EmptyBorder(10, 12, 10, 12));
        row.setCursor(new Cursor(Cursor.HAND_CURSOR));
        row.setMaximumSize(new Dimension(MENU_ROW_WIDTH, 46));
        row.setPreferredSize(new Dimension(MENU_ROW_WIDTH, 42));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        FontIcon icon = FontIcon.of(FontAwesomeSolid.GLOBE, 14);
        icon.setIconColor(active ? AppColor.ACCENT : AppColor.TEXT_MUTED);
        JLabel iconLabel = new JLabel(icon);
        iconLabel.setPreferredSize(new Dimension(24, 20));
        iconLabel.setMinimumSize(new Dimension(24, 20));
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);

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
                if (!lm.getLocale().getLanguage().equals(locale.getLanguage())) {
                    lm.setLocale(locale);
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
        row.setBorder(new EmptyBorder(10, 14, 10, 12));
        row.setCursor(new Cursor(Cursor.HAND_CURSOR));
        row.setMaximumSize(new Dimension(MENU_ROW_WIDTH, 48));
        row.setPreferredSize(new Dimension(MENU_ROW_WIDTH, 44));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        // O chua icon co dinh: JLabel khong bi ep size -> FontIcon ve day du, khong cat.
        FontIcon icon = FontIcon.of(iconType, 16);
        icon.setIconColor(AppColor.TEXT_MUTED);
        JLabel iconLabel = new JLabel(icon);
        JPanel iconBox = new JPanel(new GridBagLayout());
        iconBox.setOpaque(false);
        iconBox.setPreferredSize(new Dimension(32, 24));
        iconBox.setMinimumSize(new Dimension(32, 24));
        iconBox.setMaximumSize(new Dimension(32, 24));
        iconBox.add(iconLabel);

        JLabel text = new JLabel(label);
        text.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        text.setForeground(AppColor.TEXT_PRIMARY);

        JPanel left = new JPanel(new BorderLayout(12, 0));
        left.setOpaque(false);
        left.add(iconBox, BorderLayout.WEST);
        left.add(text, BorderLayout.CENTER);

        ToggleSwitch toggle = new ToggleSwitch(initialValue);
        toggle.onChange(onChange);

        // Boc ToggleSwitch bang FlowLayout (thay vi GridBagLayout truoc day):
        // FlowLayout KHONG bao gio keo gian/bop nho component con - no luon
        // giu component o dung getPreferredSize() va chi can giua theo chieu
        // doc. Day la mot trong nhung nguyen nhan khien icon bi cat: khi
        // GridBagLayout long trong BorderLayout.EAST ket hop voi Windows
        // display scaling (125%/150%), ToggleSwitch co the nhan duoc bien
        // width/height nho hon 46x26 thiet ke, khien phan pill/nut tron bi
        // Swing clip cung theo bounds (nhin nhu "bi cat ngang"). FlowLayout
        // tranh rui ro nay; ben canh do ToggleSwitch.paintComponent() cung
        // da duoc sua de tu gioi han ve trong kich thuoc THAT cua no du co
        // bi bop nho di nua (xem ToggleSwitch.java).
        JPanel toggleWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        toggleWrap.setOpaque(false);
        Dimension toggleSize = toggle.getPreferredSize();
        toggleWrap.setPreferredSize(toggleSize);
        toggleWrap.setMinimumSize(toggleSize);
        toggleWrap.setMaximumSize(toggleSize);
        toggleWrap.add(toggle);

        row.add(left, BorderLayout.CENTER);
        row.add(toggleWrap, BorderLayout.EAST);

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