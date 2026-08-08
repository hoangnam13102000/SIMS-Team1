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

    private static final int WIDTH = 340;
    private static final int MARGIN_TOP = 20;
    private static final int MARGIN_RIGHT = 20;
    private static final int SPACING = 10;
    private static final int DEFAULT_DURATION_MS = 3200;
    private static final int FADE_STEP_MS = 25;
    private static final float FADE_IN_STEP = 0.15f;
    private static final float FADE_OUT_STEP = 0.18f;

    private static final Map<Window, List<JWindow>> ACTIVE = new WeakHashMap<>();

    private AppAlert() {}

    public static void success(Component anchor, String message) { show(anchor, Type.SUCCESS, "Thành công", message); }
    public static void success(Component anchor, String title, String message) { show(anchor, Type.SUCCESS, title, message); }

    public static void error(Component anchor, String message) { show(anchor, Type.ERROR, "Có lỗi xảy ra", message); }
    public static void error(Component anchor, String title, String message) { show(anchor, Type.ERROR, title, message); }

    public static void warning(Component anchor, String message) { show(anchor, Type.WARNING, "Cảnh báo", message); }
    public static void warning(Component anchor, String title, String message) { show(anchor, Type.WARNING, title, message); }

    public static void info(Component anchor, String message) { show(anchor, Type.INFO, "Thông báo", message); }
    public static void info(Component anchor, String title, String message) { show(anchor, Type.INFO, title, message); }

    public static void show(Component anchor, Type type, String title, String message) {
        show(anchor, type, title, message, DEFAULT_DURATION_MS);
    }

    /** Ban day du: tu chon thoi gian hien thi (ms) truoc khi tu dong bien mat. */
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

    private static JWindow buildToastWindow(Window owner, Style style, String title, String message) {
        JWindow toast = new JWindow(owner);
        toast.setLayout(new BorderLayout());

        JPanel card = new JPanel(new BorderLayout(12, 0));
        card.setBackground(style.background);
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(style.accent, 1, true),
                new EmptyBorder(14, 16, 14, 16)));

        FontIcon fontIcon = FontIcon.of(style.icon, 20);
        fontIcon.setIconColor(style.accent);
        JLabel iconLabel = new JLabel(fontIcon);
        iconLabel.setVerticalAlignment(SwingConstants.TOP);
        iconLabel.setBorder(new EmptyBorder(2, 8, 0, 0));

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(AppFont.TOAST_TITLE);
        titleLabel.setForeground(AppColor.TEXT_PRIMARY);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        textPanel.add(titleLabel);

        if (message != null && !message.isBlank()) {
            JLabel messageLabel = new JLabel("<html><div style='width:225px'>" + message.replace("\n", "<br>") + "</div></html>");
            messageLabel.setFont(AppFont.SMALL);
            messageLabel.setForeground(AppColor.TEXT_SECONDARY);
            messageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            messageLabel.setBorder(new EmptyBorder(3, 0, 0, 0));
            textPanel.add(messageLabel);
        }

        FontIcon closeIcon = FontIcon.of(FontAwesomeSolid.TIMES, 12);
        closeIcon.setIconColor(AppColor.TEXT_SECONDARY);
        JLabel closeLabel = new JLabel(closeIcon);
        closeLabel.setVerticalAlignment(SwingConstants.TOP);
        closeLabel.setBorder(new EmptyBorder(2, 8, 0, 0));
        closeLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));

        card.add(iconLabel, BorderLayout.WEST);
        card.add(textPanel, BorderLayout.CENTER);
        card.add(closeLabel, BorderLayout.EAST);

        
        int preferredHeight = card.getPreferredSize().height;
        card.setPreferredSize(new Dimension(WIDTH, preferredHeight));

        toast.add(card, BorderLayout.CENTER);
        toast.pack();
        return toast;
    }

    /** Bam vao bat ky dau tren toast (ke ca chu tieu de/noi dung) deu dong som duoc, khong can doi het thoi gian. */
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

        // setOpacity() co the nem UnsupportedOperationException/IllegalArgumentException tren
        // he thong khong ho tro per-pixel translucency (vd 1 so config Linux/X11) - im lang bo
        // qua la CO CHU DICH: toast van hien thi binh thuong (chi mat hieu ung fade), khong anh
        // huong chuc nang, va day la hieu ung tham my chay lien tuc nen khong dang ghi log.
        try { toast.setOpacity(0f); } catch (Exception ignored) {}
        toast.setVisible(true);

        float[] opacity = {0f};
        Timer timer = new Timer(FADE_STEP_MS, null);
        timer.addActionListener(e -> {
            opacity[0] += FADE_IN_STEP;
            if (opacity[0] >= 1f) {
                opacity[0] = 1f;
                timer.stop();
            }
            try { toast.setOpacity(opacity[0]); } catch (Exception ignored) {} // xem ghi chu o tren
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
            try { toast.setOpacity(opacity[0]); } catch (Exception ignored) {} // xem ghi chu o positionAndFadeIn()
        });
        timer.start();
    }

    /** Sau khi 1 toast bien mat, day cac toast con lai len tren de khong con khoang trong. */
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