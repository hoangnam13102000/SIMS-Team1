package com.components;

import com.dao.PromotionDAO;
import com.model.PromoBannerItem;
import com.model.Promotion;
import com.theme.AppFont;
import com.theme.AppRadius;
import com.theme.AppSpacing;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Carousel quảng bá mã giảm giá — trượt tự động, nút prev/next và dot.
 * Dùng trên trang chủ client. Không phụ thuộc bảng mới (lấy từ Promotions.ShowOnBanner).
 */
public class PromoBannerCarousel extends JPanel {

    private static final Color CARD_BG_START = new Color(16, 185, 129);
    private static final Color CARD_BG_END = new Color(5, 150, 105);
    private static final Color CARD_ACCENT = new Color(250, 204, 21);
    private static final Color TEXT_WHITE = Color.WHITE;
    private static final Color TEXT_MUTED = new Color(220, 252, 231);

    private static final int AUTO_SLIDE_MS = 4500;
    private static final int CARD_HEIGHT = 148;

    private final List<PromoBannerItem> items = new ArrayList<>();
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cardsPanel = new JPanel(cardLayout);
    private final JPanel dotsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
    private final List<JComponent> dots = new ArrayList<>();

    private int currentIndex = 0;
    private Timer autoTimer;
    private Consumer<String> onCodeCopied;

    public PromoBannerCarousel() {
        setOpaque(false);
        setLayout(new BorderLayout(0, AppSpacing.SM));
        setBorder(new EmptyBorder(AppSpacing.MD, 0, AppSpacing.SM, 0));

        cardsPanel.setOpaque(false);

        JPanel header = buildHeader();
        JPanel body = new JPanel(new BorderLayout(AppSpacing.SM, 0));
        body.setOpaque(false);

        JButton prev = navButton(FontAwesomeSolid.CHEVRON_LEFT);
        prev.addActionListener(e -> showPrev());
        JButton next = navButton(FontAwesomeSolid.CHEVRON_RIGHT);
        next.addActionListener(e -> showNext());

        body.add(prev, BorderLayout.WEST);
        body.add(cardsPanel, BorderLayout.CENTER);
        body.add(next, BorderLayout.EAST);

        dotsPanel.setOpaque(false);

        add(header, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
        add(dotsPanel, BorderLayout.SOUTH);

        setVisible(false);
    }

    private JPanel buildHeader() {
        JPanel h = new JPanel(new BorderLayout());
        h.setOpaque(false);
        h.setBorder(new EmptyBorder(0, AppSpacing.SM, AppSpacing.XS, AppSpacing.SM));

        Color titleColor = new Color(15, 118, 110);
        FontIcon giftIcon = FontIcon.of(FontAwesomeSolid.TAGS, 16);
        giftIcon.setIconColor(titleColor);

        JLabel title = new JLabel("Mã giảm giá đang áp dụng");
        title.setIcon(giftIcon);
        title.setIconTextGap(8);
        title.setFont(AppFont.bold(16));
        title.setForeground(titleColor);

        JLabel hint = new JLabel("Chạm vào mã để sao chép");
        hint.setFont(AppFont.plain(12));
        hint.setForeground(new Color(100, 116, 139));

        h.add(title, BorderLayout.WEST);
        h.add(hint, BorderLayout.EAST);
        return h;
    }

    private JButton navButton(FontAwesomeSolid glyph) {
        JButton b = new JButton();
        FontIcon icon = FontIcon.of(glyph, 18);
        icon.setIconColor(new Color(15, 118, 110));
        b.setIcon(icon);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setPreferredSize(new Dimension(36, CARD_HEIGHT));
        return b;
    }

    public void setOnCodeCopied(Consumer<String> onCodeCopied) {
        this.onCodeCopied = onCodeCopied;
    }

    public void reload() {
        items.clear();
        cardsPanel.removeAll();
        dots.clear();
        dotsPanel.removeAll();
        currentIndex = 0;

        List<Promotion> promos = new PromotionDAO().findBannerPromotions();
        for (Promotion p : promos) {
            PromoBannerItem item = PromoBannerItem.from(p);
            if (item != null) items.add(item);
        }

        if (items.isEmpty()) {
            stopAuto();
            setVisible(false);
            revalidate();
            repaint();
            return;
        }

        for (int i = 0; i < items.size(); i++) {
            PromoBannerItem item = items.get(i);
            cardsPanel.add(buildCard(item), String.valueOf(i));

            JComponent dot = createDot(i == 0);
            final int idx = i;
            dot.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    showIndex(idx);
                }
            });
            dots.add(dot);
            dotsPanel.add(dot);
        }

        cardLayout.show(cardsPanel, "0");
        setVisible(true);
        startAuto();
        revalidate();
        repaint();
    }

    private JPanel buildCard(PromoBannerItem item) {
        JPanel card = new JPanel(new BorderLayout(16, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                RoundRectangle2D.Float shape = new RoundRectangle2D.Float(0, 0, w, h, AppRadius.LARGE, AppRadius.LARGE);
                g2.setClip(shape);
                g2.setPaint(new GradientPaint(0, 0, CARD_BG_START, w, h, CARD_BG_END));
                g2.fill(shape);
                g2.setColor(new Color(255, 255, 255, 28));
                g2.fillOval(w - 90, -30, 140, 140);
                g2.fillOval(-40, h - 60, 100, 100);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        card.setPreferredSize(new Dimension(10, CARD_HEIGHT));
        card.setBorder(new EmptyBorder(18, 24, 18, 24));

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));

        JLabel name = new JLabel(item.getName() != null ? item.getName() : "Ưu đãi đặc biệt");
        name.setFont(AppFont.bold(15));
        name.setForeground(TEXT_WHITE);
        name.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel discount = new JLabel(item.getDiscountLabel());
        discount.setFont(AppFont.bold(22));
        discount.setForeground(CARD_ACCENT);
        discount.setAlignmentX(Component.LEFT_ALIGNMENT);
        discount.setBorder(new EmptyBorder(6, 0, 4, 0));

        JLabel cond = new JLabel(item.getConditionLabel()
                + (item.getExpiryLabel().isEmpty() ? "" : "  ·  " + item.getExpiryLabel()));
        cond.setFont(AppFont.plain(12));
        cond.setForeground(TEXT_MUTED);
        cond.setAlignmentX(Component.LEFT_ALIGNMENT);

        left.add(name);
        left.add(discount);
        left.add(cond);

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setAlignmentY(Component.CENTER_ALIGNMENT);

        JLabel codeChip = new JLabel(item.getCode()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(255, 255, 255, 40));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(new Color(255, 255, 255, 120));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 12, 12);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        codeChip.setFont(AppFont.bold(16));
        codeChip.setForeground(TEXT_WHITE);
        codeChip.setBorder(new EmptyBorder(10, 18, 10, 18));
        codeChip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        codeChip.setAlignmentX(Component.CENTER_ALIGNMENT);
        codeChip.setToolTipText("Nhấn để sao chép mã");

        JLabel copyHint = new JLabel("Nhấn để copy");
        copyHint.setFont(AppFont.plain(11));
        copyHint.setForeground(TEXT_MUTED);
        copyHint.setAlignmentX(Component.CENTER_ALIGNMENT);
        copyHint.setBorder(new EmptyBorder(6, 0, 0, 0));

        String code = item.getCode();
        codeChip.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                copyCode(code);
            }
        });

        right.add(Box.createVerticalGlue());
        right.add(codeChip);
        right.add(copyHint);
        right.add(Box.createVerticalGlue());

        card.add(left, BorderLayout.CENTER);
        card.add(right, BorderLayout.EAST);
        return card;
    }

    private JComponent createDot(boolean active) {
        JPanel d = new JPanel() {
            private final boolean on = active;

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(on ? 18 : 8, 8);
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(on ? CARD_BG_START : new Color(203, 213, 225));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.dispose();
            }
        };
        d.setOpaque(false);
        d.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return d;
    }

    private void updateDots() {
        dotsPanel.removeAll();
        dots.clear();
        for (int i = 0; i < items.size(); i++) {
            JComponent dot = createDot(i == currentIndex);
            final int idx = i;
            dot.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    showIndex(idx);
                }
            });
            dots.add(dot);
            dotsPanel.add(dot);
        }
        dotsPanel.revalidate();
        dotsPanel.repaint();
    }

    private void showIndex(int idx) {
        if (items.isEmpty()) return;
        currentIndex = ((idx % items.size()) + items.size()) % items.size();
        cardLayout.show(cardsPanel, String.valueOf(currentIndex));
        updateDots();
        restartAuto();
    }

    private void showNext() {
        showIndex(currentIndex + 1);
    }

    private void showPrev() {
        showIndex(currentIndex - 1);
    }

    private void copyCode(String code) {
        if (code == null || code.isBlank()) return;
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(code), null);
        if (onCodeCopied != null) {
            onCodeCopied.accept(code);
        } else {
            AppAlert.success(this, "Đã sao chép", "Mã \"" + code + "\" đã được copy vào clipboard.");
        }
    }

    private void startAuto() {
        stopAuto();
        if (items.size() <= 1) return;
        autoTimer = new Timer(AUTO_SLIDE_MS, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showNext();
            }
        });
        autoTimer.setRepeats(true);
        autoTimer.start();
    }

    private void restartAuto() {
        startAuto();
    }

    private void stopAuto() {
        if (autoTimer != null) {
            autoTimer.stop();
            autoTimer = null;
        }
    }

    @Override
    public void removeNotify() {
        stopAuto();
        super.removeNotify();
    }
}