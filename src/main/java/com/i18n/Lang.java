package com.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Helper tinh (static) de lay chuoi da dich theo ngon ngu hien tai.
 * <p>
 * File dich dat o: src/main/resources/i18n/messages_vi.properties (mac dinh)
 *                  src/main/resources/i18n/messages_en.properties
 * <p>
 * Tu Java 9 tro di, PropertiesResourceBundle doc file .properties bang UTF-8
 * theo mac dinh nen co the go thang tieng Viet co dau vao file .properties
 * ma khong can native2ascii.
 * <p>
 * Cach dung:
 *   label.setText(Lang.get("login.title"));                       // "Đăng nhập"
 *   label.setText(Lang.get("welcome.user", user.getFullName()));   // co tham so
 * <p>
 * Goi Lang.setLocale(...) (thuc hien boi LanguageManager) de nap lai bundle
 * khi nguoi dung doi ngon ngu trong Cai dat.
 */
public final class Lang {

    private static final String BASE_NAME = "i18n.messages";

    private static volatile ResourceBundle bundle = ResourceBundle.getBundle(BASE_NAME, new Locale("vi"));

    private Lang() {
    }

    public static synchronized void setLocale(Locale locale) {
        bundle = ResourceBundle.getBundle(BASE_NAME, locale);
    }

    /** Lay chuoi da dich theo key. Neu thieu key, tra ve chinh key (kem dau
     *  "!!") de de phat hien thieu ban dich thay vi lam vo giao dien. */
    public static String get(String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return "!!" + key + "!!";
        }
    }

    /** Lay chuoi da dich va thay the tham so kieu {0}, {1}... (java.text.MessageFormat). */
    public static String get(String key, Object... args) {
        String pattern = get(key);
        if (pattern.startsWith("!!")) return pattern;
        return MessageFormat.format(pattern, args);
    }
}