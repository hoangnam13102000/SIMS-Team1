package com.utils;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Icon dung chung cho toan bo ung dung (thay the icon "coffee cup" mac dinh
 * cua Java/Swing tren taskbar, title bar va alt-tab).
 *
 * Cach dung: goi AppIcon.apply(this) trong constructor cua moi JFrame,
 * ngay sau khi setTitle()/setSize().
 */
public final class AppIcon {

    private AppIcon() {
    }

    // Nap 1 anh goc do logo_icon.png la anh vector-nhu, chat luong cao (515x490);
    // Java se tu resize xuong kich thuoc phu hop cho tung ngu canh (title bar,
    // taskbar, alt-tab...) nen chi can 1 nguon la du, khong can nhieu file kich
    // thuoc rieng.
    private static final Image ICON = loadImage("/logo/logo_icon.png");

    /**
     * Gan icon ung dung cho 1 cua so (title bar + taskbar/alt-tab tren Windows).
     * An toan goi ke ca khi khong nap duoc anh (se khong lam gi ca).
     */
    public static void apply(Window window) {
        if (ICON == null || window == null) return;
        window.setIconImage(ICON);
    }

    /**
     * Goi 1 lan luc khoi dong app (vi du trong main()) de dat icon cho ca
     * Taskbar/Dock cua he dieu hanh (macOS Dock, mot so DE tren Linux). Tren
     * Windows, setIconImage() tren tung JFrame la du.
     */
    public static void applyToTaskbar() {
        if (ICON == null) return;
        if (!Taskbar.isTaskbarSupported()) return;
        Taskbar taskbar = Taskbar.getTaskbar();
        try {
            if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                taskbar.setIconImage(ICON);
            }
        } catch (UnsupportedOperationException | SecurityException ignored) {
            // Mot so he dieu hanh/JRE khong ho tro - bo qua, khong anh huong app.
        }
    }

    private static Image loadImage(String classpathLocation) {
        try (InputStream in = AppIcon.class.getResourceAsStream(classpathLocation)) {
            if (in == null) return null;
            return ImageIO.read(in);
        } catch (IOException e) {
            return null;
        }
    }
}