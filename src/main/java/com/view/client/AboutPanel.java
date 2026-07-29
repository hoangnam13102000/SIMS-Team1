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
 * Trang giới thiệu tĩnh dành cho khu vực khách hàng.
 * Nội dung được dựng lại cùng ClientMainFrame khi đổi theme hoặc ngôn ngữ.
 */
public class AboutPanel extends JPanel {

    private static final int CARD_RADIUS = 18;

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

    private JComponent buildHeroSection() {
        RoundedCard card = new RoundedCard(AppColor.ACCENT_BG_SOFT, false);
        card.setLayout(new BorderLayout(AppSpacing.XL, 0));
        card.setBorder(new EmptyBorder(
                AppSpacing.XXL, AppSpacing.XXL, AppSpacing.XXL, AppSpacing.XXL));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 270));

        JLabel logo = new JLabel(loadLogoIcon(92));
        logo.setPreferredSize(new Dimension(112, 112));
        logo.setHorizontalAlignment(SwingConstants.CENTER);
        card.add(logo, BorderLayout.WEST);

        JPanel text = transparentColumn();

        JLabel brand = new JLabel(Lang.get("about.hero.brand"));
        brand.setFont(AppFont.HEADING_MD);
        brand.setForeground(AppColor.ACCENT_HOVER);
        brand.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextArea title = createTextArea(
                "about.hero.title", AppFont.TITLE, AppColor.TEXT_TITLE, 2);
        title.setBorder(new EmptyBorder(AppSpacing.SM, 0, AppSpacing.XS, 0));

        JTextArea subtitle = createTextArea(
                "about.hero.subtitle", AppFont.BODY, AppColor.TEXT_SECONDARY, 3);

        RoundedCard badge = new RoundedCard(AppColor.WHITE, false);
        badge.setLayout(new FlowLayout(FlowLayout.LEFT, AppSpacing.SM, AppSpacing.XS));
        badge.setBorder(new EmptyBorder(2, AppSpacing.SM, 2, AppSpacing.SM));
        badge.setAlignmentX(Component.LEFT_ALIGNMENT);
        badge.setMaximumSize(new Dimension(420, 34));

        JLabel badgeIcon = createIconLabel(FontAwesomeSolid.INFO_CIRCLE, 13, AppColor.ACCENT);
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

    private JComponent buildStorySection() {
        RoundedCard card = new RoundedCard(AppColor.WHITE, false);
        card.setLayout(new BorderLayout(AppSpacing.LG, 0));
        card.setBorder(new EmptyBorder(
                AppSpacing.XL, AppSpacing.XL, AppSpacing.XL, AppSpacing.XL));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 230));

        card.add(createLargeIcon(FontAwesomeSolid.STORE), BorderLayout.WEST);

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

    private JComponent buildCoreValuesSection() {
        JPanel cards = new JPanel(new GridLayout(1, 3, AppSpacing.LG, 0));
        cards.setOpaque(false);
        cards.add(createInfoCard(
                FontAwesomeSolid.BOLT,
                "about.values.convenience.title",
                "about.values.convenience.description"));
        cards.add(createInfoCard(
                FontAwesomeSolid.CHECK_CIRCLE,
                "about.values.accuracy.title",
                "about.values.accuracy.description"));
        cards.add(createInfoCard(
                FontAwesomeSolid.SHIELD_ALT,
                "about.values.trust.title",
                "about.values.trust.description"));
        return createSection("about.values.title", cards);
    }

    private JComponent buildFeaturesSection() {
        JPanel cards = new JPanel(new GridLayout(2, 2, AppSpacing.LG, AppSpacing.LG));
        cards.setOpaque(false);
        cards.add(createInfoCard(
                FontAwesomeSolid.BOX,
                "about.features.inventory.title",
                "about.features.inventory.description"));
        cards.add(createInfoCard(
                FontAwesomeSolid.SHOPPING_CART,
                "about.features.sales.title",
                "about.features.sales.description"));
        cards.add(createInfoCard(
                FontAwesomeSolid.USERS_COG,
                "about.features.rbac.title",
                "about.features.rbac.description"));
        cards.add(createInfoCard(
                FontAwesomeSolid.CHART_BAR,
                "about.features.reports.title",
                "about.features.reports.description"));
        return createSection("about.features.title", cards);
    }

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

    private JComponent buildTeamSection() {
        JPanel cards = new JPanel(new GridLayout(2, 2, AppSpacing.LG, AppSpacing.LG));
        cards.setOpaque(false);
        for (int i = 1; i <= 4; i++) {
            cards.add(createTeamCard(
                    "about.team.member" + i + ".name",
                    "about.team.member" + i + ".role"));
        }
        return createSection("about.team.title", cards);
    }

    private JComponent buildClosingSection() {
        RoundedCard card = new RoundedCard(AppColor.ACCENT_BG_SOFT, false);
        card.setLayout(new BorderLayout(AppSpacing.LG, 0));
        card.setBorder(new EmptyBorder(
                AppSpacing.XL, AppSpacing.XL, AppSpacing.XL, AppSpacing.XL));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));

        card.add(createLargeIcon(FontAwesomeSolid.HEART), BorderLayout.WEST);

        JPanel text = transparentColumn();
        text.add(createSectionTitle("about.closing.title"));
        text.add(Box.createVerticalStrut(AppSpacing.XS));
        text.add(createTextArea(
                "about.closing.subtitle", AppFont.BODY, AppColor.TEXT_SECONDARY, 2));
        card.add(text, BorderLayout.CENTER);
        return card;
    }

    private JComponent createSection(String titleKey, JComponent body) {
        JPanel section = transparentColumn();
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        section.add(createSectionTitle(titleKey));
        section.add(Box.createVerticalStrut(AppSpacing.MD));
        body.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(body);
        return section;
    }

    private JComponent createInfoCard(
            FontAwesomeSolid iconType, String titleKey, String descriptionKey) {
        RoundedCard card = new RoundedCard(AppColor.WHITE, true);
        card.setLayout(new BorderLayout(AppSpacing.MD, 0));
        card.setBorder(new EmptyBorder(
                AppSpacing.LG, AppSpacing.LG, AppSpacing.LG, AppSpacing.LG));
        card.setPreferredSize(new Dimension(260, 142));

        card.add(createLargeIcon(iconType), BorderLayout.WEST);

        JPanel text = transparentColumn();
        JLabel title = new JLabel(Lang.get(titleKey));
        title.setFont(AppFont.HEADING_MD);
        title.setForeground(AppColor.TEXT_TITLE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(title);
        text.add(Box.createVerticalStrut(AppSpacing.SM));
        text.add(createTextArea(descriptionKey, AppFont.BODY, AppColor.TEXT_SECONDARY, 3));
        card.add(text, BorderLayout.CENTER);
        return card;
    }

    private JComponent createSystemChip(FontAwesomeSolid iconType, String textKey) {
        RoundedCard chip = new RoundedCard(AppColor.WHITE, true);
        chip.setLayout(new BorderLayout(AppSpacing.MD, 0));
        chip.setBorder(new EmptyBorder(
                AppSpacing.MD, AppSpacing.LG, AppSpacing.MD, AppSpacing.LG));
        chip.add(createIconLabel(iconType, 15, AppColor.ACCENT), BorderLayout.WEST);

        JLabel text = new JLabel(Lang.get(textKey));
        text.setFont(AppFont.BODY);
        text.setForeground(AppColor.TEXT_PRIMARY);
        chip.add(text, BorderLayout.CENTER);
        return chip;
    }

    private JComponent createTeamCard(String nameKey, String roleKey) {
        RoundedCard card = new RoundedCard(AppColor.WHITE, true);
        card.setLayout(new BorderLayout(AppSpacing.LG, 0));
        card.setBorder(new EmptyBorder(
                AppSpacing.LG, AppSpacing.LG, AppSpacing.LG, AppSpacing.LG));
        card.setPreferredSize(new Dimension(340, 100));

        String name = Lang.get(nameKey);
        JLabel avatar = new JLabel(initialsOf(name), SwingConstants.CENTER);
        avatar.setFont(AppFont.HEADING_MD);
        avatar.setForeground(AppColor.ACCENT_HOVER);
        avatar.setOpaque(true);
        avatar.setBackground(AppColor.ACCENT_BG_SOFT);
        avatar.setPreferredSize(new Dimension(52, 52));
        avatar.setBorder(BorderFactory.createLineBorder(AppColor.ACCENT_SOFT));
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

    private JLabel createLargeIcon(FontAwesomeSolid iconType) {
        JLabel icon = createIconLabel(iconType, 24, AppColor.ACCENT);
        icon.setHorizontalAlignment(SwingConstants.CENTER);
        icon.setVerticalAlignment(SwingConstants.TOP);
        icon.setPreferredSize(new Dimension(42, 42));
        return icon;
    }

    private JLabel createIconLabel(FontAwesomeSolid iconType, int size, Color color) {
        FontIcon icon = FontIcon.of(iconType, size);
        icon.setIconColor(color);
        return new JLabel(icon);
    }

    private Icon loadLogoIcon(int size) {
        URL url = AboutPanel.class.getResource("/logo/logo.png");
        if (url != null) {
            ImageIcon raw = new ImageIcon(url);
            Image scaled = raw.getImage().getScaledInstance(size, size, Image.SCALE_SMOOTH);
            return new ImageIcon(scaled);
        }

        FontIcon fallback = FontIcon.of(FontAwesomeSolid.STORE, Math.max(32, size / 2));
        fallback.setIconColor(AppColor.ACCENT);
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