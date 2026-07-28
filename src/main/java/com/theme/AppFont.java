package com.theme;

import java.awt.Font;
import java.awt.GraphicsEnvironment;

public final class AppFont {

    private AppFont() {
    }

    private static final String FAMILY = "Segoe UI";

    // ==========================================================
    // FONT TĨNH (KHÔNG THAY ĐỔI)
    // ==========================================================

    public static final Font BRAND = new Font(FAMILY, Font.BOLD, 30);
    public static final Font TITLE = new Font(FAMILY, Font.BOLD, 28);

    public static final Font HEADING_LG = new Font(FAMILY, Font.BOLD, 20);
    public static final Font HEADING_MD = new Font(FAMILY, Font.BOLD, 16);

    public static final Font DIALOG_TITLE = new Font(FAMILY, Font.BOLD, 17);
    public static final Font TOAST_TITLE = new Font(FAMILY, Font.BOLD, 14);

    public static final Font FIELD = new Font(FAMILY, Font.PLAIN, 14);

    public static final Font BODY = new Font(FAMILY, Font.PLAIN, 13);
    public static final Font BODY_BOLD = new Font(FAMILY, Font.BOLD, 13);

    public static final Font LABEL = new Font(FAMILY, Font.PLAIN, 12);

    public static final Font SMALL = new Font(FAMILY, Font.PLAIN, 12);
    public static final Font SMALL_BOLD = new Font(FAMILY, Font.BOLD, 12);

    public static final Font FOOTER = new Font(FAMILY, Font.PLAIN, 11);

    public static final Font BUTTON = new Font(FAMILY, Font.BOLD, 14);

    // ==========================================================
    // FONT RESPONSIVE (BỔ SUNG)
    // ==========================================================

    /**
     * Font rất lớn.
     */
    public static Font getXXL_Bold() {
        return deriveBold(28);
    }

    /**
     * Font lớn.
     */
    public static Font getXL_Bold() {
        return deriveBold(24);
    }

    /**
     * Font trung bình lớn.
     */
    public static Font getLargeBold() {
        return deriveBold(20);
    }

    /**
     * Font đậm theo kích thước bất kỳ.
     */
    public static Font bold(int size) {
        return deriveBold(size);
    }

    /**
     * Font thường theo kích thước bất kỳ.
     */
    public static Font plain(int size) {
        return derivePlain(size);
    }

    /**
     * Resize từ font hiện có.
     */
    public static Font resize(Font font, int newSize) {
        return font.deriveFont((float) newSize);
    }

    /**
     * Font đậm dùng cho responsive.
     */
    public static Font responsiveBold(int size) {
        return deriveBold(size);
    }

    /**
     * Font thường dùng cho responsive.
     */
    public static Font responsivePlain(int size) {
        return derivePlain(size);
    }

    /**
     * Helper tạo font đậm.
     */
    private static Font deriveBold(float size) {
        return new Font(FAMILY, Font.BOLD, Math.round(size));
    }

    /**
     * Helper tạo font thường.
     */
    private static Font derivePlain(float size) {
        return new Font(FAMILY, Font.PLAIN, Math.round(size));
    }

    /**
     * Kiểm tra font Segoe UI có sẵn không.
     * Nếu không có sẽ fallback về Arial hoặc Dialog.
     */
    public static String getDefaultFontFamily() {

        String[] availableFonts =
                GraphicsEnvironment
                        .getLocalGraphicsEnvironment()
                        .getAvailableFontFamilyNames();

        for (String f : availableFonts) {

            if (f.equalsIgnoreCase("Segoe UI")) {
                return "Segoe UI";
            }

            if (f.equalsIgnoreCase("Arial")) {
                return "Arial";
            }

        }

        return Font.DIALOG;
    }

}