package com.theme;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Quan ly theme Light/Dark cho toan app.
 *
 * Cach hoat dong: vi mau sac trong AppColor la CAC TRUONG STATIC duoc doc 1
 * LAN luc component duoc khoi tao (vd setBackground(AppColor.WHITE) trong
 * constructor), doi gia tri AppColor sau do KHONG tu lam cac component dang
 * hien thi doi mau. Vi vay khi toggle theme, ThemeManager se:
 *   1) Doi AppColor sang bang mau moi (applyTheme)
 *   2) Doi FlatLaf Look&Feel (Light/Dark) - cac component Swing chuan (nut,
 *      scrollbar, o nhap...) se dung UI moi cho component nao duoc TAO SAU
 *      thoi diem nay.
 *   3) Goi "rebuild callback" ma man hinh chinh (AdminMainFrame/ClientMainFrame)
 *      da dang ky - callback nay dung nhiem vu XAY LAI toan bo noi dung cua
 *      frame (khong dong/mo cua so moi, chi thay content pane) de moi
 *      component duoc tao moi hoan toan va tu dong doc dung AppColor + UI
 *      moi nhat.
 *
 * Lua chon theme duoc luu lai (java.util.prefs) de lan mo app sau giu nguyen.
 */
public final class ThemeManager {

    private static ThemeManager instance;

    private static final String PREF_KEY = "myshop.theme.mode";
    private static final String PREF_KEY_ACCENT = "myshop.theme.accent";

    private ThemeMode currentMode;
    private final List<Runnable> rebuildListeners = new ArrayList<>();

    private ThemeManager() {
        String saved = Preferences.userRoot().node("myshop").get(PREF_KEY, ThemeMode.LIGHT.name());
        ThemeMode mode;
        try {
            mode = ThemeMode.valueOf(saved);
        } catch (IllegalArgumentException e) {
            mode = ThemeMode.LIGHT;
        }
        this.currentMode = mode;
        AppColor.applyTheme(mode);

        String savedAccent = Preferences.userRoot().node("myshop").get(PREF_KEY_ACCENT, AccentColor.BLUE.name());
        AccentColor accent;
        try {
            accent = AccentColor.valueOf(savedAccent);
        } catch (IllegalArgumentException e) {
            accent = AccentColor.BLUE;
        }
        AppColor.applyAccent(accent);
    }

    public static synchronized ThemeManager getInstance() {
        if (instance == null) {
            instance = new ThemeManager();
        }
        return instance;
    }

    public ThemeMode getMode() {
        return currentMode;
    }

    public boolean isDark() {
        return currentMode == ThemeMode.DARK;
    }

    public AccentColor getAccent() {
        return AppColor.getCurrentAccent();
    }

    /**
     * Doi mau chu dao (accent) - doc lap voi Light/Dark, khong doi FlatLaf
     * Look&Feel, chi tinh lai bang mau ACCENT_* trong AppColor roi goi lai
     * rebuild listener de UI dang mo ve lai voi mau moi (giong het co che
     * setMode ben duoi).
     */
    public void setAccent(AccentColor accent) {
        if (accent == AppColor.getCurrentAccent()) return;

        Preferences.userRoot().node("myshop").put(PREF_KEY_ACCENT, accent.name());
        AppColor.applyAccent(accent);

        for (Runnable listener : new ArrayList<>(rebuildListeners)) {
            listener.run();
        }
    }

    /**
     * Duoc goi 1 LAN, tai luc khoi dong app (truoc khi mo LoginFrame), de
     * ap dung dung FlatLaf Light/Dark theo lua chon da luu tu lan truoc.
     */
    public void applyStartupLookAndFeel() {
        applyLookAndFeel(currentMode);
    }

    /** Man hinh chinh (AdminMainFrame/ClientMainFrame) goi ham nay khi mo len,
     *  de duoc goi lai moi khi nguoi dung doi theme. Nho goi removeRebuildListener
     *  luc dong man hinh de tranh memory leak / rebuild nham frame da dong. */
    public void addRebuildListener(Runnable listener) {
        rebuildListeners.add(listener);
    }

    public void removeRebuildListener(Runnable listener) {
        rebuildListeners.remove(listener);
    }

    public void toggle() {
        setMode(currentMode == ThemeMode.DARK ? ThemeMode.LIGHT : ThemeMode.DARK);
    }

    public void setMode(ThemeMode mode) {
        if (mode == currentMode) return;
        currentMode = mode;

        Preferences.userRoot().node("myshop").put(PREF_KEY, mode.name());

        AppColor.applyTheme(mode);
        applyLookAndFeel(mode);

        // Sao chep list de tranh ConcurrentModificationException neu 1 listener
        // tu add/remove listener khac trong luc dang chay.
        for (Runnable listener : new ArrayList<>(rebuildListeners)) {
            listener.run();
        }
    }

    private void applyLookAndFeel(ThemeMode mode) {
        try {
            if (mode == ThemeMode.DARK) {
                FlatDarkLaf.setup();
            } else {
                FlatLightLaf.setup();
            }
            UIManager.put("Component.focusWidth", 1);
            UIManager.put("ScrollBar.thumbArc", 999);
            UIManager.put("ScrollBar.thumbInsets", new java.awt.Insets(2, 2, 2, 2));
        } catch (Exception ignored) {
            // Neu FlatLaf loi vi ly do nao do, giu nguyen L&F hien tai thay vi crash app.
        }
    }
}