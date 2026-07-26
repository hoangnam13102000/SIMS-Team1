package com.components.common;

import com.theme.AppColor;
import com.theme.AppConstant;
import com.theme.AppFont;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

/**
 * Ô nhập liệu bo tròn cho JPasswordField (mật khẩu)
 */
public class RoundedPasswordField extends JPanel {
    
    private Color borderColor = AppColor.BORDER;
    private final JPasswordField passwordField;
    private char echoChar = '●';
    
    public RoundedPasswordField() {
        this(null);
    }
    
    public RoundedPasswordField(JComponent trailing) {
        setLayout(new BorderLayout());
        setOpaque(false);
        setPreferredSize(new Dimension(AppConstant.FIELD_WIDTH, AppConstant.FIELD_HEIGHT));
        setMaximumSize(new Dimension(AppConstant.FIELD_WIDTH, AppConstant.FIELD_HEIGHT));
        setAlignmentX(Component.LEFT_ALIGNMENT);

        passwordField = new JPasswordField();
        passwordField.setEchoChar(echoChar);
        setupPasswordField(passwordField, trailing);
        
        add(passwordField, BorderLayout.CENTER);
        if (trailing != null) {
            trailing.setBorder(new EmptyBorder(0, 4, 0, 14));
            add(trailing, BorderLayout.EAST);
        }
    }
    
    protected void setupPasswordField(JPasswordField field, JComponent trailing) {
        field.setOpaque(false);
        field.setBorder(new EmptyBorder(0, 14, 0, trailing != null ? 4 : 14));
        field.setFont(AppFont.FIELD);
        field.setForeground(AppColor.TEXT_TITLE);
        field.setCaretColor(AppColor.ACCENT);
        
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                borderColor = AppColor.ACCENT;
                repaint();
            }
            @Override
            public void focusLost(FocusEvent e) {
                borderColor = AppColor.BORDER;
                repaint();
            }
        });
    }
    
    public JPasswordField getPasswordField() {
        return passwordField;
    }
    
    public String getText() {
        return new String(passwordField.getPassword());
    }
    
    public void setText(String text) {
        passwordField.setText(text);
    }
    
    public void setEditable(boolean editable) {
        passwordField.setEditable(editable);
    }
    
    public void setEchoChar(char c) {
        this.echoChar = c;
        passwordField.setEchoChar(c);
    }
    
    public char getEchoChar() {
        return passwordField.getEchoChar();
    }
    
    public void showPassword(boolean show) {
        if (show) {
            passwordField.setEchoChar((char) 0);
        } else {
            passwordField.setEchoChar(echoChar);
        }
    }
    
    public boolean isPasswordShowing() {
        return passwordField.getEchoChar() == 0;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(AppColor.WHITE);
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, AppConstant.RADIUS_MD, AppConstant.RADIUS_MD);
        g2.setColor(borderColor);
        g2.setStroke(new BasicStroke(AppConstant.BORDER_WIDTH));
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, AppConstant.RADIUS_MD, AppConstant.RADIUS_MD);
        g2.dispose();
        super.paintComponent(g);
    }
}