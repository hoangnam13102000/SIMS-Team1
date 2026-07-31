package com.theme;

import java.awt.Color;

public final class AppColor {

    private AppColor() {
    }

    private static ThemeMode currentMode = ThemeMode.LIGHT;

    // ===================== NEN TOI CO DINH (BrandPanel, sidebar login...) =====================
    // 5 mau nay LUON toi bat ke theme dang chon - dung cho panel trang tri
    // (vd AuthLeftPanel/RegisterLeftPanel o man hinh dang nhap/dang ky),
    // khong tham gia vao viec doi Light/Dark cua app.
    public static final Color DARK_TOP = new Color(11, 78, 47);
    public static final Color DARK_BOTTOM = new Color(23, 117, 66);
    public static final Color DARK_TEXT_MUTED = new Color(198, 198, 210);
    public static final Color DARK_FOOTER = new Color(140, 140, 152);
    public static final Color DARK_FEATURE_TEXT = new Color(226, 232, 240);

    // ===================== NEN SANG (Light) =====================
    public static Color WHITE;
    public static Color BG_LIGHT;
    public static Color BG_LIGHTER;
    public static Color PAGE_BG;   // nen content pane cua cac MainFrame/Panel

    // ===================== VIEN (Border) =====================
    public static Color BORDER;
    public static Color FIELD_BORDER;

    // ===================== CHU (Text) =====================
    public static Color TEXT_TITLE;
    public static Color TEXT_PRIMARY;
    public static Color TEXT_SECONDARY;
    public static Color TEXT_MUTED;
    public static Color TEXT_DISABLED;
    public static Color TEXT_MUTED_ALT;   // slate-400 style, dung cho icon/label phu
    public static Color ICON_MUTED;       // gray-400 style

    // ===================== MAU CHU DAO (Accent - tim/indigo) =====================
    public static Color ACCENT;
    public static Color ACCENT_HOVER;
    public static Color ACCENT_SOFT;
    public static Color ACCENT_SELECTION_BG;
    public static Color ACCENT_BG_SOFT;

    // ===================== TRANG THAI (Status) =====================
    public static Color SUCCESS;
    public static Color SUCCESS_BG;

    public static Color ERROR;
    public static Color ERROR_HOVER;
    public static Color ERROR_BG;
    public static Color RED_ALT;

    public static Color WARNING;
    public static Color WARNING_BG;
    public static Color YELLOW;
    public static Color ORANGE;

    public static Color INFO;
    public static Color INFO_BG;
    public static Color BLUE;
    public static Color GREEN;
    public static Color TEAL;
    public static Color OVERLAY_BACKDROP; // nen mo (co alpha) phu len UI luc dang loading

    // ===================== NUT (Button) =====================
    public static Color DISABLED_BTN;
    public static Color CANCEL_BG;
    public static Color CANCEL_HOVER;

    // ===================== BANG (Table) =====================
    public static Color TABLE_HEADER_BG;
    public static Color TABLE_ROW_ODD;
    public static Color TABLE_GRID;
    public static Color TABLE_ROW_TEXT;
    public static Color TABLE_VIEW_ACTION;
    public static Color TABLE_EDIT_ACTION;
    public static Color TABLE_DELETE_ACTION;

    static {
        applyTheme(ThemeMode.LIGHT);
    }

    public static ThemeMode getCurrentMode() {
        return currentMode;
    }

    /** Gan lai toan bo bang mau theo theme duoc chon. Goi tu ThemeManager. */
    public static void applyTheme(ThemeMode mode) {
        currentMode = mode;
        boolean dark = mode == ThemeMode.DARK;

        WHITE = dark ? new Color(28, 31, 38) : Color.WHITE;
        BG_LIGHT = dark ? new Color(15, 17, 21) : new Color(248, 250, 252);
        BG_LIGHTER = dark ? new Color(38, 42, 53) : new Color(241, 245, 249);
        PAGE_BG = dark ? new Color(18, 20, 25) : new Color(244, 246, 249);

        BORDER = dark ? new Color(51, 56, 71) : new Color(226, 232, 240);
        FIELD_BORDER = dark ? new Color(71, 78, 97) : new Color(203, 213, 225);

        TEXT_TITLE = dark ? new Color(241, 245, 249) : new Color(15, 23, 42);
        TEXT_PRIMARY = dark ? new Color(226, 232, 240) : new Color(30, 41, 59);
        TEXT_SECONDARY = dark ? new Color(180, 190, 205) : new Color(71, 85, 105);
        TEXT_MUTED = dark ? new Color(148, 163, 184) : new Color(100, 116, 139);
        TEXT_DISABLED = dark ? new Color(100, 105, 115) : new Color(150, 150, 150);
        TEXT_MUTED_ALT = dark ? new Color(190, 199, 214) : new Color(148, 163, 184);
        ICON_MUTED = dark ? new Color(176, 183, 195) : new Color(156, 163, 175);

        ACCENT = dark ? new Color(52, 211, 153) : new Color(5, 150, 105);
        ACCENT_HOVER = dark ? new Color(16, 185, 129) : new Color(4, 120, 87);
        ACCENT_SOFT = dark ? new Color(52, 211, 153, 50) : new Color(5, 150, 105, 40);
        ACCENT_SELECTION_BG = dark ? new Color(6, 78, 59) : new Color(209, 250, 229);
        ACCENT_BG_SOFT = dark ? new Color(6, 60, 47) : new Color(236, 253, 245);

        SUCCESS = dark ? new Color(74, 222, 128) : new Color(21, 128, 61);
        SUCCESS_BG = dark ? new Color(20, 44, 34) : new Color(236, 253, 245);

        ERROR = dark ? new Color(248, 113, 113) : new Color(220, 38, 38);
        ERROR_HOVER = dark ? new Color(220, 38, 38) : new Color(185, 28, 28);
        ERROR_BG = dark ? new Color(56, 24, 24) : new Color(254, 242, 242);
        RED_ALT = dark ? new Color(248, 113, 113) : new Color(239, 68, 68);

        WARNING = dark ? new Color(251, 191, 36) : new Color(180, 83, 9);
        WARNING_BG = dark ? new Color(56, 41, 15) : new Color(255, 251, 235);
        YELLOW = dark ? new Color(250, 204, 21) : new Color(234, 179, 8);
        ORANGE = dark ? new Color(253, 186, 116) : new Color(251, 146, 60);

        INFO = dark ? new Color(129, 140, 248) : new Color(79, 70, 229);
        INFO_BG = dark ? new Color(30, 27, 60) : new Color(238, 242, 255);
        BLUE = dark ? new Color(96, 165, 250) : new Color(59, 130, 246);
        GREEN = dark ? new Color(74, 222, 128) : new Color(34, 197, 94);
        TEAL = dark ? new Color(52, 211, 153) : new Color(16, 185, 129);
        OVERLAY_BACKDROP = dark ? new Color(15, 17, 21, 220) : new Color(255, 255, 255, 220);

        DISABLED_BTN = dark ? new Color(71, 76, 92) : new Color(165, 165, 180);
        CANCEL_BG = dark ? new Color(38, 42, 53) : new Color(241, 245, 249);
        CANCEL_HOVER = dark ? new Color(51, 56, 71) : new Color(226, 232, 240);

        TABLE_HEADER_BG = dark ? new Color(15, 20, 32) : new Color(30, 41, 59);
        TABLE_ROW_ODD = dark ? new Color(34, 38, 48) : new Color(248, 250, 252);
        TABLE_GRID = dark ? new Color(44, 48, 60) : new Color(241, 245, 249);
        TABLE_ROW_TEXT = dark ? new Color(203, 213, 225) : new Color(51, 65, 85);
        TABLE_VIEW_ACTION = dark ? new Color(148, 163, 184) : new Color(71, 85, 105);
        TABLE_EDIT_ACTION = ACCENT;
        TABLE_DELETE_ACTION = ERROR;
    }
}