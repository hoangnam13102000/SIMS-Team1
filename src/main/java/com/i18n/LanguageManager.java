package com.i18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.prefs.Preferences;

/**
 * Quan ly ngon ngu hien tai cua toan app (Tieng Viet / English).
 *
 * Thiet ke giong het com.theme.ThemeManager de de bao tri:
 *   1) Luu lua chon ngon ngu ben vung bang java.util.prefs (giu nguyen sau
 *      khi tat/mo lai app).
 *   2) Khi doi ngon ngu, goi lai toan bo "rebuild listener" ma
 *      AdminMainFrame/ClientMainFrame da dang ky de xay lai UI voi chuoi
 *      dich moi (Lang.get(...) doc lai theo Locale hien tai).
 *
 * Cach dung trong 1 man hinh:
 *   private final Runnable onLangChanged = this::rebuildContent;
 *   ...
 *   LanguageManager.getInstance().addRebuildListener(onLangChanged);
 *   // luc dong man hinh:
 *   LanguageManager.getInstance().removeRebuildListener(onLangChanged);
 *
 * Cac man hinh KHONG the "rebuild tai cho" (vd LoginFrame/RegisterFrame dang
 * mo dung luc doi ngon ngu) khong bat buoc phai dang ky listener - nguoi
 * dung se thay giao dien cap nhat theo ngon ngu moi tu lan mo lai frame do.
 */
public final class LanguageManager {

    private static final String PREF_KEY = "myshop.language";

    private static LanguageManager instance;

    private Locale currentLocale;
    private final List<Runnable> rebuildListeners = new ArrayList<>();

    private LanguageManager() {
        String saved = Preferences.userRoot().node("myshop").get(PREF_KEY, "vi");
        this.currentLocale = "en".equals(saved) ? Locale.ENGLISH : new Locale("vi");
        Lang.setLocale(currentLocale);
    }

    public static synchronized LanguageManager getInstance() {
        if (instance == null) {
            instance = new LanguageManager();
        }
        return instance;
    }

    public Locale getLocale() {
        return currentLocale;
    }

    public boolean isEnglish() {
        return "en".equals(currentLocale.getLanguage());
    }

    public boolean isVietnamese() {
        return !isEnglish();
    }

    public void toggle() {
        setLocale(isEnglish() ? new Locale("vi") : Locale.ENGLISH);
    }

    public void setLocale(Locale locale) {
        if (locale.getLanguage().equals(currentLocale.getLanguage())) return;
        currentLocale = locale;

        Preferences.userRoot().node("myshop")
                .put(PREF_KEY, "en".equals(locale.getLanguage()) ? "en" : "vi");

        Lang.setLocale(locale);

        for (Runnable listener : new ArrayList<>(rebuildListeners)) {
            listener.run();
        }
    }

    /** Man hinh chinh (AdminMainFrame/ClientMainFrame) goi ham nay khi mo len,
     *  de duoc goi lai moi khi nguoi dung doi ngon ngu. Nho goi
     *  removeRebuildListener luc dong man hinh de tranh memory leak. */
    public void addRebuildListener(Runnable listener) {
        rebuildListeners.add(listener);
    }

    public void removeRebuildListener(Runnable listener) {
        rebuildListeners.remove(listener);
    }
}