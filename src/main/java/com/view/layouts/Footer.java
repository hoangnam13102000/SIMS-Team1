package com.view.layouts;


import com.theme.AppColor;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.Year;

public class Footer extends JPanel {

    public Footer() {
        setLayout(new BorderLayout());
        setBackground(AppColor.WHITE);
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, AppColor.BORDER),
            new EmptyBorder(8, 24, 8, 24)
        ));
        add(buildCopyright(), BorderLayout.WEST);
        add(buildStatus(), BorderLayout.EAST);
    }

    private JLabel buildCopyright() {
        JLabel label = new JLabel("© " + Year.now().getValue() + " Phone Store — All rights reserved");
        label.setForeground(AppColor.TEXT_MUTED);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        return label;
    }

    private JPanel buildStatus() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 18, 0));
        panel.setOpaque(false);
        panel.add(buildStatusChip("Đã kết nối CSDL", AppColor.GREEN));

        JLabel version = new JLabel("v1.0.0");
        version.setForeground(AppColor.TEXT_MUTED);
        version.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        panel.add(version);
        return panel;
    }

    private JPanel buildStatusChip(String text, Color dotColor) {
        JPanel chip = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        chip.setOpaque(false);

        FontIcon dotIcon = FontIcon.of(FontAwesomeSolid.CIRCLE, 8);
        dotIcon.setIconColor(dotColor);

        JLabel label = new JLabel(text);
        label.setForeground(AppColor.TEXT_MUTED);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        chip.add(new JLabel(dotIcon));
        chip.add(label);
        return chip;
    }
}