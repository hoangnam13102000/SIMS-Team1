package com.utils;

import com.settings.NotificationSettings;

/**
 * Phát âm thanh thông báo.
 * Dùng ChimePlayer (tự tổng hợp âm bội kiểu chuông qua javax.sound.sampled)
 * thay vì Toolkit.beep(), nên không phụ thuộc chuông hệ điều hành và có thể
 * tùy chỉnh tự do cao độ / trường độ / âm lượng / giai điệu.
 */
public final class NotificationSound {

    private NotificationSound() {}

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