package com.view.client;

import com.i18n.Lang;
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
import java.net.URL;

/**
 * Trang gioi thieu tinh danh cho khu vuc khach hang.
 * Noi dung duoc dung lai cung ClientMainFrame khi doi theme hoac ngon ngu.
 *
 * Bo cuc duoc lam mem hon: hero dang gradient co logo trong khung tron,
 * cac khoi gia tri/tinh nang co icon-badge mau sac rieng biet (khong con
 * dong bo 1 mau nhu truoc), va phan ket bai duoc chuyen thanh banner
 * gradient noi bat thay vi mot the trang don gian.
 */
public class AboutPanel extends JPanel {

    private static final int CARD_RADIUS = 20;

    public AboutPanel() {
        setLayout(new BorderLayout());
        setBackground(AppColor.PAGE_BG);

        ScrollablePanel content = buildContent();
        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getViewport().setBackground(AppColor.PAGE_BG);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);
    }

    private ScrollablePanel buildContent() {
        ScrollablePanel content = new ScrollablePanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(AppColor.PAGE_BG);
        content.setBorder(new EmptyBorder(
                AppSpacing.XL, AppSpacing.XL, AppSpacing.XXL, AppSpacing.XL));

        content.add(buildHeroSection());
        content.add(verticalGap(AppSpacing.XL));
        content.add(buildStorySection());
        content.add(verticalGap(AppSpacing.XL));
        content.add(buildCoreValuesSection());
        content.add(verticalGap(AppSpacing.XL));
        content.add(buildFeaturesSection());
        content.add(verticalGap(AppSpacing.XL));
        content.add(buildSystemInfoSection());
        content.add(verticalGap(AppSpacing.XL));
        content.add(buildTeamSection());
        content.add(verticalGap(AppSpacing.XL));
        content.add(buildClosingSection());
        return content;
    }

    // ===================== HERO =====================

    private JComponent buildHeroSection() {
        GradientCard card = new GradientCard(AppColor.ACCENT, AppColor.ACCENT_HOVER);
        card.setLayout(new BorderLayout(AppSpacing.XL, 0));
        card.setBorder(new EmptyBorder(
                AppSpacing.XXL, AppSpacing.XXL, AppSpacing.XXL, AppSpacing.XXL));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 270));

        JPanel logoWrap = new JPanel(new GridBagLayout());
        logoWrap.setOpaque(false);
        LogoBadge logoBadge = new LogoBadge(120);
        logoWrap.add(logoBadge);
        card.add(logoWrap, BorderLayout.WEST);

        JPanel text = transparentColumn();

        JLabel brand = new JLabel(Lang.get("about.hero.brand"));
        brand.setFont(AppFont.HEADING_MD);
        brand.setForeground(new Color(255, 255, 255, 235));
        brand.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextArea title = createTextArea(
                "about.hero.title", AppFont.TITLE, Color.WHITE, 2);
        title.setBorder(new EmptyBorder(AppSpacing.SM, 0, AppSpacing.XS, 0));

        JTextArea subtitle = createTextArea(
                "about.hero.subtitle", AppFont.BODY, new Color(255, 255, 255, 225), 3);

        RoundedCard badge = new RoundedCard(new Color(255, 255, 255, 235), false);
        badge.setLayout(new FlowLayout(FlowLayout.LEFT, AppSpacing.SM, AppSpacing.XS));
        badge.setBorder(new EmptyBorder(2, AppSpacing.SM, 2, AppSpacing.SM));
        badge.setAlignmentX(Component.LEFT_ALIGNMENT);
        badge.setMaximumSize(new Dimension(420, 34));

        JLabel badgeIcon = createIconLabel(FontAwesomeSolid.INFO_CIRCLE, 13, AppColor.ACCENT_HOVER);
        JLabel badgeText = new JLabel(Lang.get("about.hero.badge"));
        badgeText.setFont(AppFont.SMALL_BOLD);
        badgeText.setForeground(AppColor.TEXT_PRIMARY);
        badge.add(badgeIcon);
        badge.add(badgeText);

        text.add(brand);
        text.add(title);
        text.add(subtitle);
        text.add(Box.createVerticalStrut(AppSpacing.MD));
        text.add(badge);
        card.add(text, BorderLayout.CENTER);
        return card;
    }

    // ===================== CAU CHUYEN =====================

    private JComponent buildStorySection() {
        RoundedCard card = new RoundedCard(AppColor.WHITE, false);
        card.setLayout(new BorderLayout(AppSpacing.LG, 0));
        card.setBorder(new EmptyBorder(
                AppSpacing.XL, AppSpacing.XL, AppSpacing.XL, AppSpacing.XL));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 230));

        JPanel iconWrap = new JPanel(new GridBagLayout());
        iconWrap.setOpaque(false);
        iconWrap.add(new IconBadge(FontAwesomeSolid.STORE, 24, AppColor.ACCENT, AppColor.ACCENT_BG_SOFT, 64));
        card.add(iconWrap, BorderLayout.WEST);

        JPanel text = transparentColumn();
        text.add(createSectionTitle("about.story.title"));
        text.add(Box.createVerticalStrut(AppSpacing.SM));
        text.add(createTextArea(
                "about.story.paragraph1", AppFont.BODY, AppColor.TEXT_SECONDARY, 3));
        text.add(Box.createVerticalStrut(AppSpacing.SM));
        text.add(createTextArea(
                "about.story.paragraph2", AppFont.BODY, AppColor.TEXT_SECONDARY, 2));
        card.add(text, BorderLayout.CENTER);
        return card;
    }

    // ===================== GIA TRI COT LOI (moi the mot mau) =====================

    private JComponent buildCoreValuesSection() {
        JPanel cards = new JPanel(new GridLayout(1, 3, AppSpacing.LG, 0));
        cards.setOpaque(false);
        cards.add(createInfoCard(
                FontAwesomeSolid.BOLT, AppColor.ACCENT, AppColor.ACCENT_BG_SOFT,
                "about.values.convenience.title", "about.values.convenience.description"));
        cards.add(createInfoCard(
                FontAwesomeSolid.CHECK_CIRCLE, AppColor.BLUE, AppColor.INFO_BG,
                "about.values.accuracy.title", "about.values.accuracy.description"));
        cards.add(createInfoCard(
                FontAwesomeSolid.SHIELD_ALT, AppColor.SUCCESS, AppColor.SUCCESS_BG,
                "about.values.trust.title", "about.values.trust.description"));
        return createSection("about.values.title", cards);
    }

    // ===================== TINH NANG (4 mau khac nhau) =====================

    private JComponent buildFeaturesSection() {
        JPanel cards = new JPanel(new GridLayout(2, 2, AppSpacing.LG, AppSpacing.LG));
        cards.setOpaque(false);
        cards.add(createInfoCard(
                FontAwesomeSolid.BOX, AppColor.ACCENT, AppColor.ACCENT_BG_SOFT,
                "about.features.inventory.title", "about.features.inventory.description"));
        cards.add(createInfoCard(
                FontAwesomeSolid.SHOPPING_CART, AppColor.TEAL, AppColor.SUCCESS_BG,
                "about.features.sales.title", "about.features.sales.description"));
        cards.add(createInfoCard(
                FontAwesomeSolid.USERS_COG, AppColor.ORANGE, AppColor.WARNING_BG,
                "about.features.rbac.title", "about.features.rbac.description"));
        cards.add(createInfoCard(
                FontAwesomeSolid.CHART_BAR, AppColor.BLUE, AppColor.INFO_BG,
                "about.features.reports.title", "about.features.reports.description"));
        return createSection("about.features.title", cards);
    }

    // ===================== THONG TIN HE THONG =====================

    private JComponent buildSystemInfoSection() {
        JPanel grid = new JPanel(new GridLayout(4, 2, AppSpacing.MD, AppSpacing.MD));
        grid.setOpaque(false);
        String[] keys = {
                "about.system.application",
                "about.system.organization",
                "about.system.version",
                "about.system.platform",
                "about.system.ui",
                "about.system.database",
                "about.system.runtime",
                "about.system.buildTool"
        };
        FontAwesomeSolid[] icons = {
                FontAwesomeSolid.INFO_CIRCLE,
                FontAwesomeSolid.STORE,
                FontAwesomeSolid.TAGS,
                FontAwesomeSolid.LAPTOP,
                FontAwesomeSolid.MOBILE_ALT,
                FontAwesomeSolid.PLUG,
                FontAwesomeSolid.COG,
                FontAwesomeSolid.BOX
        };

        for (int i = 0; i < keys.length; i++) {
            grid.add(createSystemChip(icons[i], keys[i]));
        }
        return createSection("about.system.title", grid);
    }

    // ===================== DOI NGU =====================

    private JComponent buildTeamSection() {
        JPanel cards = new JPanel(new GridLayout(2, 2, AppSpacing.LG, AppSpacing.LG));
        cards.setOpaque(false);
        Color[] ringColors = {AppColor.ACCENT, AppColor.TEAL, AppColor.ORANGE, AppColor.BLUE};
        String[] nameKeys = {
                "about.team.member1.name", "about.team.member2.name",
                "about.team.member3.name", "about.team.member4.name"
        };
        String[] roleKeys = {
                "about.team.member1.role", "about.team.member2.role",
                "about.team.member3.role", "about.team.member4.role"
        };
        for (int i = 0; i < nameKeys.length; i++) {
            cards.add(createTeamCard(nameKeys[i], roleKeys[i], ringColors[i]));
        }
        return createSection("about.team.title", cards);
    }

    // ===================== LOI KET (banner gradient) =====================

    private JComponent buildClosingSection() {
        GradientCard banner = new GradientCard(AppColor.ACCENT_HOVER, AppColor.ACCENT);
        banner.setLayout(new BorderLayout(AppSpacing.LG, 0));
        banner.setBorder(new EmptyBorder(
                AppSpacing.XL, AppSpacing.XXL, AppSpacing.XL, AppSpacing.XXL));
        banner.setAlignmentX(Component.LEFT_ALIGNMENT);
        banner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));

        JPanel iconWrap = new JPanel(new GridBagLayout());
        iconWrap.setOpaque(false);
        iconWrap.add(new IconBadge(FontAwesomeSolid.HEART, 26, Color.WHITE, new Color(255, 255, 255, 55), 72));
        banner.add(iconWrap, BorderLayout.WEST);

        JPanel text = transparentColumn();
        JTextArea title = createTextArea("about.closing.title", AppFont.HEADING_LG, Color.WHITE, 2);
        JTextArea subtitle = createTextArea(
                "about.closing.subtitle", AppFont.BODY, new Color(255, 255, 255, 225), 2);
        subtitle.setBorder(new EmptyBorder(AppSpacing.SM, 0, 0, 0));
        text.add(title);
        text.add(subtitle);
        banner.add(text, BorderLayout.CENTER);
        return banner;
    }

    // ===================== HELPER DUNG CHUNG CHO SECTION =====================

    private JComponent createSection(String titleKey, JComponent body) {
        JPanel section = transparentColumn();
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(createSectionTitle(titleKey));
        section.add(Box.createVerticalStrut(AppSpacing.LG));
        body.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(body);
        return section;
    }

    private JComponent createInfoCard(
            FontAwesomeSolid iconType, Color accent, Color tint, String titleKey, String descriptionKey) {
        RoundedCard card = new RoundedCard(AppColor.WHITE, true);
        card.setLayout(new BorderLayout());
        card.setBorder(new EmptyBorder(
                AppSpacing.LG, AppSpacing.LG, AppSpacing.LG, AppSpacing.LG));

        JPanel badgeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        badgeRow.setOpaque(false);
        badgeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        badgeRow.add(new IconBadge(iconType, 18, accent, tint, 50));

        JPanel text = transparentColumn();
        text.setBorder(new EmptyBorder(AppSpacing.MD, 0, 0, 0));
        JLabel title = new JLabel(Lang.get(titleKey));
        title.setFont(AppFont.HEADING_MD);
        title.setForeground(AppColor.TEXT_TITLE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(title);
        text.add(Box.createVerticalStrut(AppSpacing.SM));
        text.add(createTextArea(descriptionKey, AppFont.BODY, AppColor.TEXT_SECONDARY, 3));

        JPanel column = transparentColumn();
        column.add(badgeRow);
        column.add(text);
        card.add(column, BorderLayout.CENTER);
        return card;
    }

    private JComponent createSystemChip(FontAwesomeSolid iconType, String textKey) {
        RoundedCard chip = new RoundedCard(AppColor.WHITE, true);
        chip.setLayout(new BorderLayout(AppSpacing.MD, 0));
        chip.setBorder(new EmptyBorder(
                AppSpacing.MD, AppSpacing.LG, AppSpacing.MD, AppSpacing.LG));
        chip.add(new IconBadge(iconType, 14, AppColor.ACCENT, AppColor.ACCENT_BG_SOFT, 36), BorderLayout.WEST);

        JLabel text = new JLabel(Lang.get(textKey));
        text.setFont(AppFont.BODY);
        text.setForeground(AppColor.TEXT_PRIMARY);
        chip.add(text, BorderLayout.CENTER);
        return chip;
    }

    private JComponent createTeamCard(String nameKey, String roleKey, Color ringColor) {
        RoundedCard card = new RoundedCard(AppColor.WHITE, true);
        card.setLayout(new BorderLayout(AppSpacing.LG, 0));
        card.setBorder(new EmptyBorder(
                AppSpacing.LG, AppSpacing.LG, AppSpacing.LG, AppSpacing.LG));
        card.setPreferredSize(new Dimension(340, 100));

        String name = Lang.get(nameKey);
        JLabel avatar = new JLabel(initialsOf(name), SwingConstants.CENTER);
        avatar.setFont(AppFont.HEADING_MD);
        avatar.setForeground(ringColor);
        avatar.setOpaque(true);
        avatar.setBackground(tintOf(ringColor));
        avatar.setPreferredSize(new Dimension(52, 52));
        avatar.setBorder(BorderFactory.createLineBorder(ringColor, 2, true));
        card.add(avatar, BorderLayout.WEST);

        JPanel text = transparentColumn();
        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(AppFont.BODY_BOLD);
        nameLabel.setForeground(AppColor.TEXT_TITLE);
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextArea role = createTextArea(roleKey, AppFont.SMALL, AppColor.TEXT_MUTED, 2);
        text.add(nameLabel);
        text.add(Box.createVerticalStrut(AppSpacing.XS));
        text.add(role);
        card.add(text, BorderLayout.CENTER);
        return card;
    }

    private Color tintOf(Color base) {
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), 35);
    }

    private JLabel createSectionTitle(String key) {
        JLabel title = new JLabel(Lang.get(key));
        title.setFont(AppFont.HEADING_LG);
        title.setForeground(AppColor.TEXT_TITLE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        return title;
    }

    private JTextArea createTextArea(String key, Font font, Color color, int rows) {
        JTextArea text = new JTextArea(Lang.get(key), rows, 1);
        text.setEditable(false);
        text.setFocusable(false);
        text.setOpaque(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setFont(font);
        text.setForeground(color);
        text.setBorder(null);
        text.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.setMaximumSize(new Dimension(Integer.MAX_VALUE, text.getPreferredSize().height));
        return text;
    }

    private JLabel createIconLabel(FontAwesomeSolid iconType, int size, Color color) {
        FontIcon icon = FontIcon.of(iconType, size);
        icon.setIconColor(color);
        return new JLabel(icon);
    }

    private Icon loadRawLogoIcon(int size) {
        URL url = AboutPanel.class.getResource("/logo/logo.png");
        if (url != null) {
            ImageIcon raw = new ImageIcon(url);
            Image scaled = raw.getImage().getScaledInstance(size, size, Image.SCALE_SMOOTH);
            return new ImageIcon(scaled);
        }
        FontIcon fallback = FontIcon.of(FontAwesomeSolid.STORE, Math.max(32, size / 2));
        fallback.setIconColor(Color.WHITE);
        return fallback;
    }

    private JPanel transparentColumn() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }

    private Component verticalGap(int height) {
        return Box.createRigidArea(new Dimension(0, height));
    }

    private String initialsOf(String name) {
        StringBuilder initials = new StringBuilder();
        for (String part : name.trim().split("\\s+")) {
            if (!part.isEmpty()) {
                initials.append(Character.toUpperCase(part.charAt(0)));
            }
        }
        if (initials.length() <= 2) {
            return initials.toString();
        }
        return "" + initials.charAt(0) + initials.charAt(initials.length() - 1);
    }

    /** The bo tron, nen phang (mau don), co the bat hover doi sang mau accent nhat. */
    private static final class RoundedCard extends JPanel {
        private final Color fillColor;
        private final boolean hoverEnabled;
        private boolean hovered;

        private RoundedCard(Color fillColor, boolean hoverEnabled) {
            this.fillColor = fillColor;
            this.hoverEnabled = hoverEnabled;
            setOpaque(false);

            if (hoverEnabled) {
                setCursor(new Cursor(Cursor.HAND_CURSOR));
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        hovered = true;
                        repaint();
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        hovered = false;
                        repaint();
                    }
                });
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(hovered ? AppColor.ACCENT_BG_SOFT : fillColor);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), CARD_RADIUS, CARD_RADIUS);
            g2.setColor(AppColor.BORDER);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, CARD_RADIUS, CARD_RADIUS);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /**
     * The bo tron voi nen gradient cheo tu mau from -> to, kem 2 vong tron mo
     * trang trai o goc de tao cam giac huu co (blob).
     */
    private static final class GradientCard extends JPanel {
        private final Color from;
        private final Color to;

        private GradientCard(Color from, Color to) {
            this.from = from;
            this.to = to;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setPaint(new GradientPaint(0, 0, from, getWidth(), getHeight(), to));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), CARD_RADIUS, CARD_RADIUS);
            g2.setColor(new Color(255, 255, 255, 35));
            g2.fillOval(getWidth() - 150, -70, 220, 220);
            g2.setColor(new Color(255, 255, 255, 22));
            g2.fillOval(-50, getHeight() - 90, 150, 150);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Vong tron mau lam nen cho icon, tao cam giac "badge" thay vi icon tho. */
    private static final class IconBadge extends JPanel {
        private final Color background;

        private IconBadge(FontAwesomeSolid iconType, int iconSize, Color iconColor, Color background, int diameter) {
            this.background = background;
            setOpaque(false);
            setLayout(new GridBagLayout());
            setPreferredSize(new Dimension(diameter, diameter));
            setMinimumSize(new Dimension(diameter, diameter));
            setMaximumSize(new Dimension(diameter, diameter));
            FontIcon icon = FontIcon.of(iconType, iconSize);
            icon.setIconColor(iconColor);
            add(new JLabel(icon));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(background);
            g2.fillOval(0, 0, getWidth(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Khung tron trang chua logo, dat tren nen gradient cua hero. */
    private final class LogoBadge extends JPanel {
        private final int diameter;

        private LogoBadge(int diameter) {
            this.diameter = diameter;
            setOpaque(false);
            setLayout(new GridBagLayout());
            setPreferredSize(new Dimension(diameter, diameter));
            int logoSize = (int) Math.round(diameter * 0.66);
            JLabel logo = new JLabel(loadRawLogoIcon(logoSize));
            add(logo);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(255, 255, 255, 235));
            g2.fillOval(0, 0, diameter, diameter);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static final class ScrollablePanel extends JPanel implements Scrollable {
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(
                Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(
                Rectangle visibleRect, int orientation, int direction) {
            return Math.max(16, visibleRect.height - AppSpacing.XL);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
