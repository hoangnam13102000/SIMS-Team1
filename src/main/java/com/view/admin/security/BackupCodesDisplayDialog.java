package com.view.admin.security;

import com.i18n.Lang;
import com.theme.AppColor;
import com.theme.AppFont;
import com.theme.AppSpacing;
import com.view.forgotpassword.WizardWidgets;
import com.components.common.PrimaryButton;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

public class BackupCodesDisplayDialog extends JDialog {

    public BackupCodesDisplayDialog(Window owner, List<String> codes) {
        super(owner, Lang.get("twofa.enroll.backup.title"), ModalityType.APPLICATION_MODAL);
        setResizable(false);
        setSize(440, 420);
        setLocationRelativeTo(owner);
        getContentPane().setBackground(AppColor.WHITE);
        setLayout(new BorderLayout());

        JPanel panel = WizardWidgets.createStepPanel();
        panel.setBorder(new EmptyBorder(AppSpacing.XL, AppSpacing.XL, AppSpacing.XL, AppSpacing.XL));

        JLabel subtitle = WizardWidgets.createWrappedLabel(
                Lang.get("twofa.enroll.backup.subtitle"), AppFont.BODY, AppColor.TEXT_MUTED);
        panel.add(subtitle);
        panel.add(Box.createVerticalStrut(AppSpacing.LG));

        JPanel grid = new JPanel(new GridLayout(5, 2, AppSpacing.MD, AppSpacing.SM));
        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (String code : codes) {
            JLabel codeLabel = new JLabel(code, SwingConstants.CENTER);
            codeLabel.setFont(AppFont.SMALL_BOLD);
            codeLabel.setForeground(AppColor.TEXT_TITLE);
            codeLabel.setBorder(BorderFactory.createLineBorder(AppColor.BORDER));
            grid.add(codeLabel);
        }
        panel.add(grid);
        panel.add(Box.createVerticalStrut(AppSpacing.LG));

        PrimaryButton closeButton = WizardWidgets.createPrimaryButton(Lang.get("twofa.enroll.backup.finish"));
        closeButton.addActionListener(e -> dispose());
        panel.add(closeButton);

        add(panel, BorderLayout.CENTER);
    }
}