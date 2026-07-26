package com.theme;

import javax.swing.BorderFactory;
import javax.swing.border.EmptyBorder;

public final class AppSpacing {

    private AppSpacing() {}

    // Padding
    public static final int XS = 4;
    public static final int SM = 8;
    public static final int MD = 12;
    public static final int LG = 16;
    public static final int XL = 24;
    public static final int XXL = 32;

    // Border tiện dụng
    public static EmptyBorder xsBorder() {
        return (EmptyBorder) BorderFactory.createEmptyBorder(XS, XS, XS, XS);
    }

    public static EmptyBorder smBorder() {
        return (EmptyBorder) BorderFactory.createEmptyBorder(SM, SM, SM, SM);
    }

    public static EmptyBorder mdBorder() {
        return (EmptyBorder) BorderFactory.createEmptyBorder(MD, MD, MD, MD);
    }

    public static EmptyBorder lgBorder() {
        return (EmptyBorder) BorderFactory.createEmptyBorder(LG, LG, LG, LG);
    }

    public static EmptyBorder custom(int top, int left, int bottom, int right) {
        return (EmptyBorder) BorderFactory.createEmptyBorder(top, left, bottom, right);
    }
}