package com.view.forgotpassword;

import com.components.common.PrimaryButton;
import com.components.common.RoundedField;
import com.components.common.RoundedPasswordField;
import com.i18n.Lang;
import com.theme.AppColor;
import com.theme.AppConstant;
import com.theme.AppFont;
import com.theme.AppSpacing;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Cac helper build widget dung chung cho toan bo wizard "quen mat khau".
 * Tach khoi ForgotPasswordDialog de cac step panel (Identify/Otp/Password)
 * dung chung ma khong phai phu thuoc lan nhau.
 */
public final class WizardWidgets {

    public static final int CONTENT_WIDTH = 440;

    private WizardWidgets() {
    }

    public static JPanel createStepPanel() {
        JPanel panel = new JPanel();
        panel.setBackground(AppColor.WHITE);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }

    public static void addHeader(JPanel panel, int step, String titleText, String subtitleText) {
        JLabel stepLabel = new JLabel(Lang.get("forgot.step.counter", step, 3));
        stepLabel.setFont(AppFont.SMALL_BOLD);
        stepLabel.setForeground(AppColor.ACCENT);
        stepLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(stepLabel);
        panel.add(Box.createVerticalStrut(AppSpacing.SM));

        JLabel title = new JLabel(titleText);
        title.setFont(AppFont.TITLE);
        title.setForeground(AppColor.TEXT_TITLE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);
        panel.add(Box.createVerticalStrut(AppSpacing.SM));

        JLabel subtitle = createWrappedLabel(subtitleText, AppFont.BODY, AppColor.TEXT_MUTED);
        panel.add(subtitle);
        panel.add(Box.createVerticalStrut(AppSpacing.XL));
    }

    public static JPanel fieldGroup(String labelText, JComponent field) {
        JPanel group = new JPanel();
        group.setOpaque(false);
        group.setLayout(new BoxLayout(group, BoxLayout.Y_AXIS));
        group.setAlignmentX(Component.LEFT_ALIGNMENT);
        group.setMaximumSize(new Dimension(CONTENT_WIDTH, AppConstant.FIELD_HEIGHT + 28));

        JLabel label = new JLabel(labelText);
        label.setFont(AppFont.LABEL);
        label.setForeground(AppColor.TEXT_MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(0, 0, AppSpacing.XS, 0));
        field.setAlignmentX(Component.LEFT_ALIGNMENT);

        group.add(label);
        group.add(field);
        return group;
    }

    public static RoundedField createTextField(String placeholder) {
        RoundedField field = new RoundedField();
        field.setPreferredSize(new Dimension(CONTENT_WIDTH, AppConstant.FIELD_HEIGHT));
        field.setMaximumSize(new Dimension(CONTENT_WIDTH, AppConstant.FIELD_HEIGHT));
        field.getTextField().putClientProperty("JTextField.placeholderText", placeholder);
        return field;
    }

    public static RoundedPasswordField createPasswordField() {
        final RoundedPasswordField[] holder = new RoundedPasswordField[1];
        FontIcon showIcon = FontIcon.of(FontAwesomeSolid.EYE, AppConstant.ICON_SIZE_SM);
        showIcon.setIconColor(AppColor.ACCENT);
        FontIcon hideIcon = FontIcon.of(FontAwesomeSolid.EYE_SLASH, AppConstant.ICON_SIZE_SM);
        hideIcon.setIconColor(AppColor.ACCENT);

        JLabel toggle = new JLabel(showIcon);
        toggle.setCursor(new Cursor(Cursor.HAND_CURSOR));
        toggle.setToolTipText(Lang.get("forgot.password.show"));
        toggle.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                RoundedPasswordField field = holder[0];
                boolean show = !field.isPasswordShowing();
                field.showPassword(show);
                toggle.setIcon(show ? hideIcon : showIcon);
                toggle.setToolTipText(Lang.get(
                        show ? "forgot.password.hide" : "forgot.password.show"));
                field.getPasswordField().requestFocusInWindow();
            }
        });

        RoundedPasswordField field = new RoundedPasswordField(toggle);
        holder[0] = field;
        field.setPreferredSize(new Dimension(CONTENT_WIDTH, AppConstant.FIELD_HEIGHT));
        field.setMaximumSize(new Dimension(CONTENT_WIDTH, AppConstant.FIELD_HEIGHT));
        return field;
    }

    public static void putPasswordPlaceholder(RoundedPasswordField field, String placeholder) {
        field.getPasswordField().putClientProperty("JTextField.placeholderText", placeholder);
    }

    public static PrimaryButton createPrimaryButton(String text) {
        PrimaryButton button = new PrimaryButton(text);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setPreferredSize(new Dimension(CONTENT_WIDTH, AppConstant.BUTTON_HEIGHT));
        button.setMaximumSize(new Dimension(CONTENT_WIDTH, AppConstant.BUTTON_HEIGHT));
        return button;
    }

    public static JButton createLinkButton(String text) {
        JButton button = new JButton(text);
        button.setFont(AppFont.SMALL_BOLD);
        button.setForeground(AppColor.ACCENT);
        button.setBorder(BorderFactory.createEmptyBorder(
                AppSpacing.SM, AppSpacing.MD, AppSpacing.SM, AppSpacing.MD));
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return button;
    }

    public static JPanel centeredRow(JComponent component) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(CONTENT_WIDTH, 36));
        row.add(component);
        return row;
    }

    public static JLabel createMessageLabel() {
        JLabel label = createWrappedLabel(" ", AppFont.SMALL, AppColor.TEXT_MUTED);
        label.setPreferredSize(new Dimension(CONTENT_WIDTH, 44));
        label.setMaximumSize(new Dimension(CONTENT_WIDTH, 44));
        return label;
    }

    public static JLabel createWrappedLabel(String text, java.awt.Font font, Color color) {
        JLabel label = new JLabel(toHtml(text));
        label.setFont(font);
        label.setForeground(color);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setMaximumSize(new Dimension(CONTENT_WIDTH, 70));
        return label;
    }

    public static String toHtml(String text) {
        String safeText = text == null ? "" : text.replace("\n", "<br>");
        return "<html><div style='width:" + CONTENT_WIDTH + "px'>" + safeText + "</div></html>";
    }

    public static void showMessage(JLabel label, String message, Color color) {
        label.setForeground(color);
        label.setText(toHtml(message));
    }
}