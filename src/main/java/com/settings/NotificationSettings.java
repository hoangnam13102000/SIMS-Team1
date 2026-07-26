package com.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Cai dat thong bao cua nguoi dung (rieng cho may/tai khoan dang dang nhap
 * tren may nay) - luu ben vung bang java.util.prefs, giong cach
 * com.theme.ThemeManager luu Light/Dark.
 * <p>
 * Gom 2 cong tac doc lap:
 * <p>
 * 1) {@link #isSoundEnabled()} - "Am thanh thong bao": bat/tat tieng "ting"
 *    khi co don hang moi hoac su co moi. Anh huong CA don hang LAN su co.
 * <p>
 * 2) {@link #isOrdersMuted()} - "An thong bao don hang" (co che AN, khong
 *    phai XOA): khi BAT, thong bao don hang moi se KHONG lam so dem tren
 *    chuong tang len va KHONG phat am thanh - nhung don hang VAN duoc
 *    OrderNotifyServer ghi lai lich su binh thuong. Nguoi dung tat che do
 *    nay di se thay lai day du nhung gi da bo lo, khong mat du lieu. Day la
 *    kieu "Do Not Disturb" pho bien trong cac app chuyen nghiep (Slack,
 *    macOS...), khac voi viec tat han thong bao (se lam mat du lieu neu
 *    lich su bi gioi han dung luong).
 * <p>
 * CO Y: canh bao su co bao mat/he thong muc HIGH/CRITICAL (tu IncidentHistory)
 * KHONG bi anh huong boi isOrdersMuted() - day la quyet dinh thiet ke co
 * chu dich: canh bao mat ket noi DB, nghi ngo truy cap trai phep... can
 * duoc quan tri vien nhan biet ngay ca khi ho dang "an" thong bao don hang
 * ban thong thuong, tranh truong hop tat nham thong bao roi bo lo su co
 * nghiem trong. Chi rieng am thanh (isSoundEnabled) la ap dung chung cho ca
 * 2 loai.
 */
public final class NotificationSettings {

    private static final String KEY_SOUND_ENABLED = "myshop.notification.soundEnabled";
    private static final String KEY_ORDERS_MUTED = "myshop.notification.ordersMuted";

    private static NotificationSettings instance;

    private boolean soundEnabled;
    private boolean ordersMuted;
    private final List<Runnable> listeners = new ArrayList<>();

    private NotificationSettings() {
        Preferences prefs = prefs();
        this.soundEnabled = prefs.getBoolean(KEY_SOUND_ENABLED, true);
        this.ordersMuted = prefs.getBoolean(KEY_ORDERS_MUTED, false);
    }

    public static synchronized NotificationSettings getInstance() {
        if (instance == null) {
            instance = new NotificationSettings();
        }
        return instance;
    }

    private static Preferences prefs() {
        return Preferences.userRoot().node("myshop");
    }

    public boolean isSoundEnabled() {
        return soundEnabled;
    }

    public void setSoundEnabled(boolean enabled) {
        if (this.soundEnabled == enabled) return;
        this.soundEnabled = enabled;
        prefs().putBoolean(KEY_SOUND_ENABLED, enabled);
        notifyListeners();
    }

    public boolean isOrdersMuted() {
        return ordersMuted;
    }

    public void setOrdersMuted(boolean muted) {
        if (this.ordersMuted == muted) return;
        this.ordersMuted = muted;
        prefs().putBoolean(KEY_ORDERS_MUTED, muted);
        notifyListeners();
    }

    /** Man hinh nao can cap nhat lai UI (vd icon chuong) khi cai dat doi thi dang ky o day. */
    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (Runnable l : new ArrayList<>(listeners)) {
            l.run();
        }
    }
}