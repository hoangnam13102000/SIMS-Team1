package com.theme;

import java.awt.Color;
import java.util.Locale;

/**
 * Cac lua chon mau chu dao (accent) nguoi dung co the doi trong Cai dat
 * (SettingsButton) - doc lap voi Light/Dark (ThemeMode).
 * <p>
 * Moi mau co 2 bien the: getLight() dung khi app dang o Light mode,
 * getDark() dung khi app dang o Dark mode (sang hon de du tuong phan tren
 * nen toi) - dung dung cach lam ACCENT truoc day trong AppColor.
 * <p>
 * Them mau moi: chi can them 1 hang trong enum nay + 1 dong dich
 * "settings.accent.<ten>" trong 2 file messages_*.properties.
 */
public enum AccentColor {
    BLUE(new Color(30, 100, 200), new Color(96, 165, 250)),
    PURPLE(new Color(124, 58, 237), new Color(167, 139, 250)),
    GREEN(new Color(5, 150, 105), new Color(52, 211, 153)),
    ORANGE(new Color(194, 65, 12), new Color(251, 146, 60)),
    ROSE(new Color(225, 29, 72), new Color(251, 113, 133)),
    TEAL(new Color(13, 148, 136), new Color(45, 212, 191));

    private final Color light;
    private final Color dark;

    AccentColor(Color light, Color dark) {
        this.light = light;
        this.dark = dark;
    }

    public Color getLight() {
        return light;
    }

    public Color getDark() {
        return dark;
    }

    /** Mau dung de ve swatch tron trong popup Cai dat (luon dung ban Light cho de nhin tren card trang). */
    public Color getSwatch() {
        return light;
    }

    /** Key i18n tuong ung (vd BLUE -> "settings.accent.blue") dung cho tooltip ten mau. */
    public String getI18nKey() {
        return "settings.accent." + name().toLowerCase(Locale.ROOT);
    }
}