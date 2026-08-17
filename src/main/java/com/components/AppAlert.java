package com.components;

import com.theme.AppColor;
import com.theme.AppFont;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class AppAlert {

    public enum Type { SUCCESS, ERROR, WARNING, INFO }

    // === KICH THUOC MOI ===
    // Chieu rong toi thieu + toi da: alert se tu dong co gian trong khoang nay
    private static final int MIN_WIDTH = 400;
    private static final int MAX_WIDTH = 560;
    // Chieu cao toi da: neu vuot qua se hien thi thanh cuon
    private static final int MAX_HEIGHT = 280;
    private static final int TEXT_SAFETY_MARGIN = 16;
    private static final int MARGIN_TOP = 20;
    private static final int MARGIN_RIGHT = 20;
    private static final int SPACING = 10;
    private static final int DEFAULT_DURATION_MS = 4000; // Tang them thoi gian hien thi
    private static final int FADE_STEP_MS = 25;
    private static final float FADE_IN_STEP = 0.15f;
    private static final float FADE_OUT_STEP = 0.18f;

    private static final Map<Window, List<JWindow>> ACTIVE = new WeakHashMap<>();

    private AppAlert() {}

    public static void success(Component anchor, String message) {
        show(anchor, Type.SUCCESS, "Thành công", message);
    }

    public static void success(Component anchor, String title, String message) {
        show(anchor, Type.SUCCESS, title, message);
    }

    public static void error(Component anchor, String message) {
        show(anchor, Type.ERROR, "Có lỗi xảy ra", message);
    }

    public static void error(Component anchor, String title, String message) {
        show(anchor, Type.ERROR, title, message);
    }

    public static void warning(Component anchor, String message) {
        show(anchor, Type.WARNING, "Cảnh báo", message);
    }

    public static void warning(Component anchor, String title, String message) {
        show(anchor, Type.WARNING, title, message);
    }

    public static void info(Component anchor, String message) {
        show(anchor, Type.INFO, "Thông báo", message);
    }

    public static void info(Component anchor, String title, String message) {
        show(anchor, Type.INFO, title, message);
    }

    public static void show(Component anchor, Type type, String title, String message) {
        show(anchor, type, title, message, DEFAULT_DURATION_MS);
    }

    public static void show(Component anchor, Type type, String title, String message, int durationMs) {
        Window owner = SwingUtilities.getWindowAncestor(anchor);
        if (owner == null) return;
        Style style = styleFor(type);
        JWindow toast = buildToastWindow(owner, style, title, message);
        List<JWindow> siblings = ACTIVE.computeIfAbsent(owner, w -> new ArrayList<>());
        Runnable dismiss = () -> dismiss(owner, toast, siblings);
        installDismissTriggers(toast, dismiss);
        positionAndFadeIn(owner, toast, siblings);
        siblings.add(toast);
        Timer lifeTimer = new Timer(durationMs, e -> dismiss.run());
        lifeTimer.setRepeats(false);
        lifeTimer.start();
    }

    /**
     * Tinh chieu rong vung text dua tren chieu rong alert hien tai (MIN_WIDTH).
     * Neu noi dung qua dai, alert se tu tang chieu rong den MAX_WIDTH.
     */
    private static int computeTextWidth(int alertWidth, JLabel iconLabel, JLabel closeLabel) {
        int cardHorizontalInsets = 2 * (1 + 16);
        int hgap = 12 * 2;
        int iconColumnWidth = iconLabel.getPreferredSize().width;
        int closeColumnWidth = closeLabel.getPreferredSize().width;
        int reserved = cardHorizontalInsets + hgap + iconColumnWidth + closeColumnWidth;
        return alertWidth - reserved - TEXT_SAFETY_MARGIN;
    }

    /**
     * Tinh kich thuoc alert dua tren noi dung that:
     * - Chieu rong: trong khoang [MIN_WIDTH, MAX_WIDTH]
     * - Chieu cao: tu dong theo noi dung, toi da MAX_HEIGHT (vuot qua se cuon)
     */
    private static Dimension computeAlertSize(JPanel card, JComponent content) {
        Dimension pref = content.getPreferredSize();
        int width = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, pref.width + 60)); // +60 cho padding/icon
        int height = Math.min(MAX_HEIGHT, pref.height + 40); // +40 cho padding
        return new Dimension(width, height);
    }

    private static JWindow buildToastWindow(Window owner, Style style, String title, String message) {
        JWindow toast = new JWindow(owner);
        toast.setLayout(new BorderLayout());

        JPanel card = new JPanel(new BorderLayout(12, 0));
        card.setBackground(style.background);
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(style.accent, 1, true),
                new EmptyBorder(14, 16, 14, 16)));

        // Icon ben trai
        FontIcon fontIcon = FontIcon.of(style.icon, 20);
        fontIcon.setIconColor(style.accent);
        JLabel iconLabel = new JLabel(fontIcon);
        iconLabel.setVerticalAlignment(SwingConstants.TOP);
        iconLabel.setBorder(new EmptyBorder(2, 8, 0, 0));

        // Nut dong ben phai
        FontIcon closeIcon = FontIcon.of(FontAwesomeSolid.TIMES, 12);
        closeIcon.setIconColor(AppColor.TEXT_SECONDARY);
        JLabel closeLabel = new JLabel(closeIcon);
        closeLabel.setVerticalAlignment(SwingConstants.TOP);
        closeLabel.setBorder(new EmptyBorder(2, 8, 0, 0));
        closeLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // === VUNG NOI DUNG TEXT: co the cuon neu qua dai ===
        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(AppFont.TOAST_TITLE);
        titleLabel.setForeground(AppColor.TEXT_PRIMARY);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        textPanel.add(titleLabel);

        if (message != null && !message.isBlank()) {
            int textWidth = computeTextWidth(MIN_WIDTH, iconLabel, closeLabel);
            // Dung JEditorPane thay JLabel de ho tro HTML va tu dong xuong dong tot hon
            JEditorPane messagePane = new JEditorPane("text/html", "");
            messagePane.setEditable(false);
            messagePane.setOpaque(false);
            messagePane.setFont(AppFont.SMALL);
            messagePane.setForeground(AppColor.TEXT_SECONDARY);
            messagePane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
            messagePane.setText("<html><body style='width:" + textWidth + "px; font-family:"
                    + AppFont.SMALL.getFamily() + "; font-size:" + AppFont.SMALL.getSize() + "pt; color:"
                    + colorToHex(AppColor.TEXT_SECONDARY) + "'>"
                    + message.replace("\n", "<br>") + "</body></html>");
            messagePane.setAlignmentX(Component.LEFT_ALIGNMENT);
            messagePane.setBorder(new EmptyBorder(3, 0, 0, 0));

            // Dat kich thuoc toi da cho vung text
            messagePane.setMaximumSize(new Dimension(textWidth, MAX_HEIGHT - 60));

            // Neu chieu cao vuot qua MAX_HEIGHT, bo trong JScrollPane
            int msgHeight = messagePane.getPreferredSize().height;
            if (msgHeight > MAX_HEIGHT - 80) {
                JScrollPane scrollPane = new JScrollPane(messagePane);
                scrollPane.setOpaque(false);
                scrollPane.getViewport().setOpaque(false);
                scrollPane.setBorder(null);
                scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
                scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
                scrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);
                scrollPane.setMaximumSize(new Dimension(textWidth, MAX_HEIGHT - 80));
                scrollPane.getVerticalScrollBar().setUnitIncrement(12);
                textPanel.add(scrollPane);
            } else {
                textPanel.add(messagePane);
            }
        }

        card.add(iconLabel, BorderLayout.WEST);
        card.add(textPanel, BorderLayout.CENTER);
        card.add(closeLabel, BorderLayout.EAST);

        // === Tinh kich thuoc cuoi cung ===
        Dimension finalSize = computeAlertSize(card, textPanel);
        card.setPreferredSize(finalSize);

        toast.add(card, BorderLayout.CENTER);
        toast.pack();

        try { toast.setAlwaysOnTop(true); } catch (Exception ignored) {}

        return toast;
    }

    private static String colorToHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    private static void installDismissTriggers(JWindow toast, Runnable dismiss) {
        java.awt.event.MouseAdapter clickToClose = new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) { dismiss.run(); }
        };
        attachRecursively(toast.getContentPane(), clickToClose);
    }

    private static void attachRecursively(Container container, java.awt.event.MouseAdapter listener) {
        container.addMouseListener(listener);
        for (Component c : container.getComponents()) {
            if (c instanceof Container) {
                attachRecursively((Container) c, listener);
            } else {
                c.addMouseListener(listener);
            }
        }
    }

    private static void positionAndFadeIn(Window owner, JWindow toast, List<JWindow> siblings) {
        Point ownerLoc;
        try {
            ownerLoc = owner.getLocationOnScreen();
        } catch (Exception e) {
            return;
        }
        int y = ownerLoc.y + MARGIN_TOP;
        for (JWindow sibling : siblings) {
            y += sibling.getHeight() + SPACING;
        }
        int x = ownerLoc.x + owner.getWidth() - toast.getWidth() - MARGIN_RIGHT;
        toast.setLocation(x, y);

        try { toast.setOpacity(0f); } catch (Exception ignored) {}
        toast.setVisible(true);
        toast.toFront();

        float[] opacity = {0f};
        Timer timer = new Timer(FADE_STEP_MS, null);
        timer.addActionListener(e -> {
            opacity[0] += FADE_IN_STEP;
            if (opacity[0] >= 1f) {
                opacity[0] = 1f;
                timer.stop();
            }
            try { toast.setOpacity(opacity[0]); } catch (Exception ignored) {}
        });
        timer.start();
    }

    private static void dismiss(Window owner, JWindow toast, List<JWindow> siblings) {
        if (!toast.isDisplayable()) return;
        float[] opacity = {1f};
        Timer timer = new Timer(FADE_STEP_MS, null);
        timer.addActionListener(e -> {
            opacity[0] -= FADE_OUT_STEP;
            if (opacity[0] <= 0f) {
                timer.stop();
                siblings.remove(toast);
                toast.dispose();
                reflow(owner, siblings);
                return;
            }
            try { toast.setOpacity(opacity[0]); } catch (Exception ignored) {}
        });
        timer.start();
    }

    private static void reflow(Window owner, List<JWindow> siblings) {
        Point ownerLoc;
        try {
            ownerLoc = owner.getLocationOnScreen();
        } catch (Exception e) {
            return;
        }
        int y = ownerLoc.y + MARGIN_TOP;
        for (JWindow sibling : siblings) {
            sibling.setLocation(sibling.getX(), y);
            y += sibling.getHeight() + SPACING;
        }
    }

    private static Style styleFor(Type type) {
        if (type == Type.SUCCESS) return new Style(AppColor.SUCCESS, AppColor.SUCCESS_BG, FontAwesomeSolid.CHECK_CIRCLE);
        if (type == Type.ERROR) return new Style(AppColor.ERROR, AppColor.ERROR_BG, FontAwesomeSolid.TIMES_CIRCLE);
        if (type == Type.WARNING) return new Style(AppColor.WARNING, AppColor.WARNING_BG, FontAwesomeSolid.EXCLAMATION_TRIANGLE);
        return new Style(AppColor.INFO, AppColor.INFO_BG, FontAwesomeSolid.INFO_CIRCLE);
    }

    private static final class Style {
        final Color accent;
        final Color background;
        final FontAwesomeSolid icon;

        Style(Color accent, Color background, FontAwesomeSolid icon) {
            this.accent = accent;
            this.background = background;
            this.icon = icon;
        }
    }
}