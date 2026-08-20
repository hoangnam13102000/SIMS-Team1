package com.utils;

import com.settings.NotificationSettings;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.WindowEvent;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Phát âm thanh thông báo.
 * Dùng ChimePlayer (tự tổng hợp âm bội kiểu chuông qua javax.sound.sampled)
 * thay vì Toolkit.beep(), nên không phụ thuộc chuông hệ điều hành và có thể
 * tùy chỉnh tự do cao độ / trường độ / âm lượng / giai điệu.
 */
public final class NotificationSound {

    private static final AtomicBoolean DIALOG_HOOK_INSTALLED = new AtomicBoolean(false);

    private NotificationSound() {}

    /**
     * Cai 1 hook toan app cho cac JOptionPane thong bao cu con sot lai trong project.
     * Chi phat chuong voi INFORMATION/WARNING/ERROR; confirm/input (QUESTION/PLAIN)
     * khong phat de tranh moi lan nguoi dung thao tac form cung bi "ting".
     * <p>
     * AppAlert da goi playDing() truc tiep, con hook nay giup bao phu cac man hinh
     * legacy van dang dung JOptionPane.showMessageDialog().
     */
    public static void installGlobalDialogSoundHook() {
        if (!DIALOG_HOOK_INSTALLED.compareAndSet(false, true)) return;

        // Tat sound mac dinh cua Look&Feel neu co de khong bi 2 tieng (OS beep + chime).
        UIManager.put("OptionPane.errorSound", null);
        UIManager.put("OptionPane.warningSound", null);
        UIManager.put("OptionPane.informationSound", null);

        AWTEventListener listener = event -> {
            if (!(event instanceof WindowEvent)) return;
            WindowEvent we = (WindowEvent) event;
            if (we.getID() != WindowEvent.WINDOW_OPENED) return;
            if (!(we.getWindow() instanceof JDialog)) return;

            JOptionPane pane = findOptionPane((Container) we.getWindow());
            if (pane == null) return;

            int type = pane.getMessageType();
            if (type == JOptionPane.INFORMATION_MESSAGE
                    || type == JOptionPane.WARNING_MESSAGE
                    || type == JOptionPane.ERROR_MESSAGE) {
                playDing();
            }
        };

        Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.WINDOW_EVENT_MASK);
    }

    private static JOptionPane findOptionPane(Container root) {
        if (root instanceof JOptionPane) return (JOptionPane) root;
        for (Component child : root.getComponents()) {
            if (child instanceof JOptionPane) return (JOptionPane) child;
            if (child instanceof Container) {
                JOptionPane found = findOptionPane((Container) child);
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * Âm thanh thông báo tin nhắn mới từ khách hàng.
     * Giai điệu 2 nốt đi lên, nốt sau ngân dài như tiếng chuông thật.
     */
    public static void playMessageSound() {
        if (!NotificationSettings.getInstance().isSoundEnabled()) return;

        ChimePlayer.playMelody(
                new ChimePlayer.Note(659.25, 160, 0.55), // E5
                new ChimePlayer.Note(880.00, 480, 0.55)  // A5 - ngân dài
        );
    }

    /**
     * Âm thanh mặc định cho các thông báo khác (đơn hàng, sự cố...).
     * Một tiếng chuông "ding" ngắn, ấm.
     */
    public static void playDing() {
        if (!NotificationSettings.getInstance().isSoundEnabled()) return;

        ChimePlayer.playMelody(
                new ChimePlayer.Note(784.0, 300, 0.5) // G5
        );
    }
}