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
 * Ô nhập liệu bo tròn cho JTextField (text thường)
 */
public class RoundedField extends JPanel {
    
    private Color borderColor = AppColor.BORDER;
    private final JTextField textField;
    
    public RoundedField() {
        this(null);
    }
    
    public RoundedField(JComponent trailing) {
        setLayout(new BorderLayout());
        setOpaque(false);
        setPreferredSize(new Dimension(AppConstant.FIELD_WIDTH, AppConstant.FIELD_HEIGHT));
        setMaximumSize(new Dimension(AppConstant.FIELD_WIDTH, AppConstant.FIELD_HEIGHT));
        setAlignmentX(Component.LEFT_ALIGNMENT);

        textField = new JTextField();
        setupTextField(textField, trailing);
        
        add(textField, BorderLayout.CENTER);
        if (trailing != null) {
            trailing.setBorder(new EmptyBorder(0, 4, 0, 14));
            add(trailing, BorderLayout.EAST);
        }
    }
    
    protected void setupTextField(JTextField field, JComponent trailing) {
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
    
    public JTextField getTextField() {
        return textField;
    }
    
    public String getText() {
        return textField.getText();
    }
    
    public void setText(String text) {
        textField.setText(text);
    }
    
    public void setEditable(boolean editable) {
        textField.setEditable(editable);
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