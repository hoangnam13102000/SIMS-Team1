package com.components.common;

import com.theme.AppColor;
import com.theme.AppConstant;
import com.theme.AppFont;

import javax.swing.*;
import java.awt.*;

/**
 * Base Frame cho tất cả các Frame trong ứng dụng
 */
public abstract class BaseFrame extends JFrame {
    
    // Chỉ giữ lại những gì thực sự cần thiết
    // Font - sử dụng trực tiếp từ AppFont
    protected static final Font FONT_TITLE = AppFont.TITLE;
    protected static final Font FONT_BRAND = AppFont.BRAND;
    protected static final Font FONT_LABEL = AppFont.LABEL;
    protected static final Font FONT_FIELD = AppFont.FIELD;
    protected static final Font FONT_BODY = AppFont.BODY;
    protected static final Font FONT_SMALL = AppFont.SMALL;
    protected static final Font FONT_BTN = AppFont.BUTTON;

    public BaseFrame(String title) {
        setTitle(title);
        setSize(1000, 620);
        setMinimumSize(new Dimension(860, 560));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new GridBagLayout());
        
        initComponents();
    }

    protected abstract void initComponents();
}