package com.view.client;

import com.components.AppAlert;
import com.components.BaseDialog;
import com.components.LoadingOverlay;
import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.i18n.Lang;
import com.model.User;
import com.service.AuthService;
import com.service.ContactMailService;
import com.service.ContactMailService.ContactRequestData;
import com.theme.AppColor;
import com.theme.AppFont;
import com.theme.AppSpacing;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

/**
 * Trang lien he cua khu vuc khach hang.
 * SMTP luon chay trong SwingWorker de khong chan Event Dispatch Thread.
 *
 * Bo cuc duoc lam mem hon so voi ban dau: hero dang gradient co icon tron,
 * 3 the ho tro voi mau accent rieng biet, va khu vuc form duoc tach thanh
 * 2 cot (form ben trai + sidebar thong tin nhanh ben phai) thay vi 1 khoi
 * trang phang duy nhat.
 */
public class ContactPanel extends JPanel {

    private static final int CARD_RADIUS = 20;
    private static final int MESSAGE_LIMIT = 2000;
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$");
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("^(0\\d{9}|\\+84\\d{9})$");

    private JTextField fullNameField;
    private JTextField emailField;
    private JTextField phoneField;
    private JComboBox<CategoryOption> categoryCombo;
    private JTextField subjectField;
    private JTextArea messageArea;
    private JLabel messageCounter;
    private JButton submitButton;
    private JButton resetButton;
    private LoadingOverlay loadingOverlay;

    private String initialFullName = "";
    private String initialEmail = "";
    private String initialPhone = "";
    private boolean isSending;

    public ContactPanel() {
        setLayout(new BorderLayout());
        setBackground(AppColor.PAGE_BG);

        ScrollablePanel content = buildContent();
        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getViewport().setBackground(AppColor.PAGE_BG);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        loadingOverlay = new LoadingOverlay(Lang.get("contact.form.submitting"));
        add(LoadingOverlay.attach(scrollPane, loadingOverlay), BorderLayout.CENTER);

        prefillCurrentUser();
        updateMessageCounter();
    }

    private ScrollablePanel buildContent() {
        ScrollablePanel content = new ScrollablePanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(AppColor.PAGE_BG);
        content.setBorder(new EmptyBorder(
                AppSpacing.XL, AppSpacing.XL, AppSpacing.XXL, AppSpacing.XL));

        content.add(buildHeroSection());
        content.add(verticalGap(AppSpacing.XL));
        content.add(buildSupportChannelsSection());
        content.add(verticalGap(AppSpacing.XL));
        content.add(buildContactFormSection());
        return content;
    }

    // ===================== HERO =====================

    private JComponent buildHeroSection() {
        GradientCard card = new GradientCard(AppColor.ACCENT, AppColor.ACCENT_HOVER);
        card.setLayout(new BorderLayout(AppSpacing.XL, 0));
        card.setBorder(new EmptyBorder(
                AppSpacing.XL, AppSpacing.XXL, AppSpacing.XL, AppSpacing.XXL));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));

        JPanel iconWrap = new JPanel(new GridBagLayout());
        iconWrap.setOpaque(false);
        iconWrap.add(new IconBadge(FontAwesomeSolid.HEADPHONES, 32, Color.WHITE,
                new Color(255, 255, 255, 55), 88));
        card.add(iconWrap, BorderLayout.WEST);

        JPanel text = transparentColumn();
        JLabel title = new JLabel(Lang.get("contact.hero.title"));
        title.setFont(AppFont.TITLE);
        title.setForeground(Color.WHITE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextArea subtitle = createReadOnlyText(
                Lang.get("contact.hero.subtitle"), AppFont.BODY, new Color(255, 255, 255, 225), 2);
        subtitle.setBorder(new EmptyBorder(AppSpacing.SM, 0, 0, 0));

        RoundedCard badge = new RoundedCard(new Color(255, 255, 255, 235), false);
        badge.setLayout(new FlowLayout(FlowLayout.LEFT, AppSpacing.SM, AppSpacing.XS));
        badge.setBorder(new EmptyBorder(2, AppSpacing.SM, 2, AppSpacing.SM));
        badge.setAlignmentX(Component.LEFT_ALIGNMENT);
        badge.setMaximumSize(new Dimension(260, 34));
        badge.add(createIconLabel(FontAwesomeSolid.HEART, 12, AppColor.ACCENT_HOVER));
        JLabel badgeText = new JLabel(Lang.get("contact.hero.badge"));
        badgeText.setFont(AppFont.SMALL_BOLD);
        badgeText.setForeground(AppColor.TEXT_PRIMARY);
        badge.add(badgeText);

        text.add(title);
        text.add(subtitle);
        text.add(Box.createVerticalStrut(AppSpacing.MD));
        text.add(badge);
        card.add(text, BorderLayout.CENTER);
        return card;
    }

    // ===================== 3 THE HO TRO (mau sac rieng) =====================

    private JComponent buildSupportChannelsSection() {
        JPanel cards = new JPanel(new GridLayout(1, 3, AppSpacing.LG, 0));
        cards.setOpaque(false);
        cards.add(createSupportCard(
                FontAwesomeSolid.ENVELOPE, AppColor.ACCENT, AppColor.ACCENT_BG_SOFT,
                "contact.channel.email.title", "contact.channel.email.description"));
        cards.add(createSupportCard(
                FontAwesomeSolid.COMMENTS, AppColor.BLUE, AppColor.INFO_BG,
                "contact.channel.response.title", "contact.channel.response.description"));
        cards.add(createSupportCard(
                FontAwesomeSolid.SHIELD_ALT, AppColor.SUCCESS, AppColor.SUCCESS_BG,
                "contact.channel.security.title", "contact.channel.security.description"));
        cards.setAlignmentX(Component.LEFT_ALIGNMENT);
        cards.setMaximumSize(new Dimension(Integer.MAX_VALUE, 175));
        return cards;
    }

    private JComponent createSupportCard(
            FontAwesomeSolid icon, Color accent, Color tint, String titleKey, String descriptionKey) {
        RoundedCard card = new RoundedCard(AppColor.WHITE, true);
        card.setLayout(new BorderLayout());
        card.setBorder(new EmptyBorder(
                AppSpacing.LG, AppSpacing.LG, AppSpacing.LG, AppSpacing.LG));
        card.setPreferredSize(new Dimension(260, 165));

        JPanel badgeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        badgeRow.setOpaque(false);
        badgeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        badgeRow.add(new IconBadge(icon, 18, accent, tint, 50));

        JPanel text = transparentColumn();
        text.setBorder(new EmptyBorder(AppSpacing.MD, 0, 0, 0));
        JLabel title = new JLabel(Lang.get(titleKey));
        title.setFont(AppFont.HEADING_MD);
        title.setForeground(AppColor.TEXT_TITLE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(title);
        text.add(Box.createVerticalStrut(AppSpacing.SM));
        text.add(createReadOnlyText(
                Lang.get(descriptionKey), AppFont.BODY, AppColor.TEXT_SECONDARY, 3));

        JPanel column = transparentColumn();
        column.add(badgeRow);
        column.add(text);
        card.add(column, BorderLayout.CENTER);
        return card;
    }

    // ===================== FORM + SIDEBAR =====================

    private JComponent buildContactFormSection() {
        JPanel wrapper = new JPanel(new BorderLayout(AppSpacing.LG, 0));
        wrapper.setOpaque(false);
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 820));

        wrapper.add(buildContactFormCard(), BorderLayout.CENTER);
        wrapper.add(buildContactSidebar(), BorderLayout.EAST);
        return wrapper;
    }

    private JComponent buildContactFormCard() {
        RoundedCard card = new RoundedCard(AppColor.WHITE, false);
        card.setLayout(new BorderLayout());
        card.setBorder(new EmptyBorder(
                AppSpacing.XL, AppSpacing.XL, AppSpacing.XL, AppSpacing.XL));

        JPanel form = transparentColumn();

        JLabel title = new JLabel(Lang.get("contact.form.title"));
        title.setFont(AppFont.HEADING_LG);
        title.setForeground(AppColor.TEXT_TITLE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel(Lang.get("contact.form.subtitle"));
        subtitle.setFont(AppFont.BODY);
        subtitle.setForeground(AppColor.TEXT_MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setBorder(new EmptyBorder(AppSpacing.XS, 0, AppSpacing.LG, 0));

        form.add(title);
        form.add(subtitle);

        fullNameField = createTextField("contact.form.fullName.placeholder");
        emailField = createTextField("contact.form.email.placeholder");
        form.add(createTwoColumnRow(
                createFieldGroup("contact.form.fullName", fullNameField),
                createFieldGroup("contact.form.email", emailField)));
        form.add(Box.createVerticalStrut(AppSpacing.MD));

        phoneField = createTextField("contact.form.phone.placeholder");
        categoryCombo = createCategoryCombo();
        form.add(createTwoColumnRow(
                createFieldGroup("contact.form.phone", phoneField),
                createFieldGroup("contact.form.category", categoryCombo)));
        form.add(Box.createVerticalStrut(AppSpacing.MD));

        subjectField = createTextField("contact.form.subject.placeholder");
        form.add(createFieldGroup("contact.form.subject", subjectField));
        form.add(Box.createVerticalStrut(AppSpacing.MD));

        messageArea = new JTextArea(8, 1);
        styleTextArea(messageArea);
        messageArea.putClientProperty(
                "JTextArea.placeholderText", Lang.get("contact.form.message.placeholder"));
        messageArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { updateMessageCounter(); }
            @Override public void removeUpdate(DocumentEvent e) { updateMessageCounter(); }
            @Override public void changedUpdate(DocumentEvent e) { updateMessageCounter(); }
        });

        JScrollPane messageScroll = new JScrollPane(messageArea);
        messageScroll.setBorder(BorderFactory.createLineBorder(AppColor.FIELD_BORDER, 1, true));
        messageScroll.getViewport().setBackground(AppColor.WHITE);
        messageScroll.getVerticalScrollBar().setUnitIncrement(16);

        JPanel messageGroup = new JPanel(new BorderLayout(0, AppSpacing.XS));
        messageGroup.setOpaque(false);
        messageGroup.setAlignmentX(Component.LEFT_ALIGNMENT);
        messageGroup.add(createFieldLabel("contact.form.message"), BorderLayout.NORTH);
        messageGroup.add(messageScroll, BorderLayout.CENTER);

        messageCounter = new JLabel();
        messageCounter.setFont(AppFont.SMALL);
        messageCounter.setHorizontalAlignment(SwingConstants.RIGHT);
        messageGroup.add(messageCounter, BorderLayout.SOUTH);
        form.add(messageGroup);
        form.add(Box.createVerticalStrut(AppSpacing.LG));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, AppSpacing.SM, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));

        resetButton = createButton(
                Lang.get("contact.form.reset"), AppColor.CANCEL_BG,
                AppColor.CANCEL_HOVER, AppColor.TEXT_PRIMARY);
        submitButton = createButton(
                Lang.get("contact.form.submit"), AppColor.ACCENT,
                AppColor.ACCENT_HOVER, Color.WHITE);
        resetButton.addActionListener(e -> resetFormWithConfirmation());
        submitButton.addActionListener(e -> submitContactRequest());
        actions.add(resetButton);
        actions.add(submitButton);
        form.add(actions);

        card.add(form, BorderLayout.CENTER);
        return card;
    }

    private JComponent buildContactSidebar() {
        RoundedCard card = new RoundedCard(AppColor.ACCENT_BG_SOFT, false);
        card.setLayout(new BorderLayout());
        card.setBorder(new EmptyBorder(
                AppSpacing.XL, AppSpacing.LG, AppSpacing.XL, AppSpacing.LG));
        card.setPreferredSize(new Dimension(250, 10));

        JPanel column = transparentColumn();

        JPanel badgeWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        badgeWrap.setOpaque(false);
        badgeWrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        badgeWrap.add(new IconBadge(FontAwesomeSolid.PAPER_PLANE, 20, Color.WHITE, AppColor.ACCENT, 56));
        column.add(badgeWrap);
        column.add(Box.createVerticalStrut(AppSpacing.MD));

        JLabel title = new JLabel(Lang.get("contact.hero.badge"));
        title.setFont(AppFont.HEADING_MD);
        title.setForeground(AppColor.TEXT_TITLE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        column.add(title);
        column.add(Box.createVerticalStrut(AppSpacing.LG));

        column.add(buildSidebarRow(FontAwesomeSolid.ENVELOPE, AppColor.ACCENT, "contact.channel.email.title"));
        column.add(Box.createVerticalStrut(AppSpacing.MD));
        column.add(buildSidebarRow(FontAwesomeSolid.COMMENTS, AppColor.BLUE, "contact.channel.response.title"));
        column.add(Box.createVerticalStrut(AppSpacing.MD));
        column.add(buildSidebarRow(FontAwesomeSolid.SHIELD_ALT, AppColor.SUCCESS, "contact.channel.security.title"));
        column.add(Box.createVerticalStrut(AppSpacing.XL));

        JSeparator separator = new JSeparator();
        separator.setForeground(AppColor.BORDER);
        separator.setAlignmentX(Component.LEFT_ALIGNMENT);
        separator.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        column.add(separator);
        column.add(Box.createVerticalStrut(AppSpacing.LG));

        JTextArea privacyNote = createReadOnlyText(
                Lang.get("contact.privacy.note"), AppFont.SMALL, AppColor.TEXT_MUTED, 3);
        JLabel privacyIcon = createIconLabel(FontAwesomeSolid.LOCK, 12, AppColor.TEXT_MUTED);
        privacyIcon.setAlignmentX(Component.LEFT_ALIGNMENT);
        column.add(privacyIcon);
        column.add(Box.createVerticalStrut(AppSpacing.XS));
        column.add(privacyNote);

        card.add(column, BorderLayout.NORTH);
        return card;
    }

    private JComponent buildSidebarRow(FontAwesomeSolid icon, Color accent, String textKey) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, AppSpacing.SM, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(createIconLabel(icon, 14, accent));
        JLabel text = new JLabel(Lang.get(textKey));
        text.setFont(AppFont.BODY_BOLD);
        text.setForeground(AppColor.TEXT_PRIMARY);
        row.add(text);
        return row;
    }

    // ===================== FORM FIELD HELPERS (khong doi) =====================

    private JPanel createTwoColumnRow(JComponent left, JComponent right) {
        JPanel row = new JPanel(new GridLayout(1, 2, AppSpacing.LG, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
        row.add(left);
        row.add(right);
        return row;
    }

    private JPanel createFieldGroup(String labelKey, JComponent field) {
        JPanel group = new JPanel(new BorderLayout(0, AppSpacing.XS));
        group.setOpaque(false);
        group.add(createFieldLabel(labelKey), BorderLayout.NORTH);
        group.add(field, BorderLayout.CENTER);
        return group;
    }

    private JLabel createFieldLabel(String key) {
        JLabel label = new JLabel(Lang.get(key));
        label.setFont(AppFont.SMALL_BOLD);
        label.setForeground(AppColor.TEXT_PRIMARY);
        return label;
    }

    private JTextField createTextField(String placeholderKey) {
        JTextField field = new JTextField();
        field.putClientProperty("JTextField.placeholderText", Lang.get(placeholderKey));
        field.setFont(AppFont.FIELD);
        field.setForeground(AppColor.TEXT_PRIMARY);
        field.setCaretColor(AppColor.TEXT_PRIMARY);
        field.setBackground(AppColor.WHITE);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColor.FIELD_BORDER, 1, true),
                new EmptyBorder(8, AppSpacing.MD, 8, AppSpacing.MD)));
        return field;
    }

    private JComboBox<CategoryOption> createCategoryCombo() {
        JComboBox<CategoryOption> combo = new JComboBox<>(new CategoryOption[]{
                new CategoryOption("", Lang.get("contact.category.select")),
                new CategoryOption("account", Lang.get("contact.category.account")),
                new CategoryOption("product", Lang.get("contact.category.product")),
                new CategoryOption("order", Lang.get("contact.category.order")),
                new CategoryOption("feedback", Lang.get("contact.category.feedback")),
                new CategoryOption("other", Lang.get("contact.category.other"))
        });
        combo.setFont(AppFont.FIELD);
        combo.setForeground(AppColor.TEXT_PRIMARY);
        combo.setBackground(AppColor.WHITE);
        combo.setBorder(BorderFactory.createLineBorder(AppColor.FIELD_BORDER, 1, true));
        return combo;
    }

    private void styleTextArea(JTextArea area) {
        area.setFont(AppFont.FIELD);
        area.setForeground(AppColor.TEXT_PRIMARY);
        area.setCaretColor(AppColor.TEXT_PRIMARY);
        area.setBackground(AppColor.WHITE);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(new EmptyBorder(AppSpacing.SM, AppSpacing.MD, AppSpacing.SM, AppSpacing.MD));
    }

    // ===================== NGHIEP VU (khong doi) =====================

    private void prefillCurrentUser() {
        User user = AuthService.getInstance().getCurrentUser();
        if (user != null) {
            initialFullName = nullToEmpty(user.getFullName());
            initialEmail = nullToEmpty(user.getEmail());
            initialPhone = nullToEmpty(user.getPhone());
        }
        restoreIdentityFields();
    }

    private void submitContactRequest() {
        if (isSending || !validateForm()) return;

        User user = AuthService.getInstance().getCurrentUser();
        CategoryOption category = (CategoryOption) categoryCombo.getSelectedItem();
        ContactRequestData data = new ContactRequestData(
                user == null ? null : user.getUserId(),
                user == null ? "" : nullToEmpty(user.getUsername()),
                fullNameField.getText().trim(),
                emailField.getText().trim(),
                phoneField.getText().trim(),
                category == null ? "" : category.label(),
                sanitizeSubject(subjectField.getText()),
                messageArea.getText().trim());

        setSendingState(true);

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                new ContactMailService().sendContactRequest(data);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    clearRequestFields();
                    AppAlert.success(
                            ContactPanel.this,
                            Lang.get("contact.send.success.title"),
                            Lang.get("contact.send.success.message"));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    handleSendFailure(e);
                } catch (ExecutionException e) {
                    handleSendFailure(e.getCause() == null ? e : e.getCause());
                } finally {
                    setSendingState(false);
                }
            }
        };
        worker.execute();
    }

    private boolean validateForm() {
        String fullName = fullNameField.getText().trim();
        if (fullName.isEmpty()) {
            return validationWarning("contact.validation.fullName.required", fullNameField);
        }
        if (fullName.length() < 2 || fullName.length() > 100) {
            return validationWarning("contact.validation.fullName.length", fullNameField);
        }

        String email = emailField.getText().trim();
        if (email.isEmpty()) {
            return validationWarning("contact.validation.email.required", emailField);
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            return validationWarning("contact.validation.email.invalid", emailField);
        }

        String phone = phoneField.getText().trim();
        if (!phone.isEmpty() && !PHONE_PATTERN.matcher(phone).matches()) {
            return validationWarning("contact.validation.phone.invalid", phoneField);
        }

        if (categoryCombo.getSelectedIndex() <= 0) {
            return validationWarning("contact.validation.category.required", categoryCombo);
        }

        String subject = subjectField.getText().trim();
        if (subject.isEmpty()) {
            return validationWarning("contact.validation.subject.required", subjectField);
        }
        if (subject.length() < 5 || subject.length() > 150) {
            return validationWarning("contact.validation.subject.length", subjectField);
        }

        String message = messageArea.getText().trim();
        if (message.isEmpty()) {
            return validationWarning("contact.validation.message.required", messageArea);
        }
        if (message.length() < 20 || message.length() > MESSAGE_LIMIT) {
            return validationWarning("contact.validation.message.length", messageArea);
        }
        return true;
    }

    private boolean validationWarning(String messageKey, JComponent field) {
        AppAlert.warning(
                this,
                Lang.get("contact.validation.title"),
                Lang.get(messageKey));
        SwingUtilities.invokeLater(field::requestFocusInWindow);
        return false;
    }

    private void handleSendFailure(Throwable error) {
        AppLogger.getInstance().error(
                ErrorCode.EMAIL_SEND_FAIL, "ContactPanel.sendContactRequest", error);
        AppAlert.error(
                this,
                Lang.get("contact.send.error.title"),
                Lang.get("contact.send.error.message"));
    }

    private void setSendingState(boolean sending) {
        isSending = sending;
        submitButton.setEnabled(!sending);
        resetButton.setEnabled(!sending);
        submitButton.setText(Lang.get(
                sending ? "contact.form.submitting" : "contact.form.submit"));
        if (sending) {
            loadingOverlay.start(Lang.get("contact.form.submitting"));
        } else {
            loadingOverlay.stop();
        }
    }

    private void updateMessageCounter() {
        if (messageCounter == null || messageArea == null) return;
        int length = messageArea.getText().length();
        messageCounter.setText(
                Lang.get("contact.form.message.counter", length, MESSAGE_LIMIT));
        messageCounter.setForeground(
                length > MESSAGE_LIMIT ? AppColor.ERROR : AppColor.TEXT_MUTED);
    }

    private void resetFormWithConfirmation() {
        if (isSending) return;
        if (hasUserEnteredContent()) {
            boolean confirmed = BaseDialog.confirm(
                    this,
                    Lang.get("contact.reset.confirm.title"),
                    Lang.get("contact.reset.confirm.message"),
                    Lang.get("contact.reset.confirm.button"),
                    AppColor.ACCENT,
                    AppColor.ACCENT_HOVER,
                    FontAwesomeSolid.UNDO);
            if (!confirmed) return;
        }
        resetForm();
    }

    private boolean hasUserEnteredContent() {
        return !fullNameField.getText().trim().equals(initialFullName)
                || !emailField.getText().trim().equals(initialEmail)
                || !phoneField.getText().trim().equals(initialPhone)
                || categoryCombo.getSelectedIndex() > 0
                || !subjectField.getText().trim().isEmpty()
                || !messageArea.getText().trim().isEmpty();
    }

    private void resetForm() {
        restoreIdentityFields();
        clearRequestFields();
    }

    private void restoreIdentityFields() {
        fullNameField.setText(initialFullName);
        emailField.setText(initialEmail);
        phoneField.setText(initialPhone);
    }

    private void clearRequestFields() {
        categoryCombo.setSelectedIndex(0);
        subjectField.setText("");
        messageArea.setText("");
        updateMessageCounter();
    }

    private String sanitizeSubject(String value) {
        return value.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    // ===================== TIEN ICH UI =====================

    private JTextArea createReadOnlyText(String value, Font font, Color color, int rows) {
        JTextArea text = new JTextArea(value, rows, 1);
        text.setEditable(false);
        text.setFocusable(false);
        text.setOpaque(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setFont(font);
        text.setForeground(color);
        text.setBorder(null);
        text.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.setMaximumSize(new Dimension(Integer.MAX_VALUE, text.getPreferredSize().height));
        return text;
    }

    private JPanel transparentColumn() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }

    private JLabel createIconLabel(FontAwesomeSolid iconType, int size, Color color) {
        return new JLabel(createFontIcon(iconType, size, color));
    }

    private FontIcon createFontIcon(FontAwesomeSolid iconType, int size, Color color) {
        FontIcon icon = FontIcon.of(iconType, size);
        icon.setIconColor(color);
        return icon;
    }

    private JButton createButton(String text, Color background, Color hover, Color foreground) {
        JButton button = new JButton(text);
        button.setFont(AppFont.BODY_BOLD);
        button.setForeground(foreground);
        button.setBackground(background);
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(10, AppSpacing.XL, 10, AppSpacing.XL));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (button.isEnabled()) button.setBackground(hover);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(background);
            }
        });
        return button;
    }

    private Component verticalGap(int height) {
        return Box.createRigidArea(new Dimension(0, height));
    }

    private record CategoryOption(String key, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    /** The bo tron, nen phang (mau don), co the bat hover doi sang mau accent nhat. */
    private static final class RoundedCard extends JPanel {
        private final Color fillColor;
        private final boolean hoverEnabled;
        private boolean hovered;

        private RoundedCard(Color fillColor, boolean hoverEnabled) {
            this.fillColor = fillColor;
            this.hoverEnabled = hoverEnabled;
            setOpaque(false);
            if (hoverEnabled) {
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        hovered = true;
                        repaint();
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        hovered = false;
                        repaint();
                    }
                });
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(hovered ? AppColor.ACCENT_BG_SOFT : fillColor);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), CARD_RADIUS, CARD_RADIUS);
            g2.setColor(AppColor.BORDER);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, CARD_RADIUS, CARD_RADIUS);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /**
     * The bo tron voi nen gradient cheo tu mau from -> to, kem 2 vong tron mo
     * trang trai o goc de tao cam giac huu co (blob), mo phong cac banner
     * mau sac trong thiet ke web tham khao.
     */
    private static final class GradientCard extends JPanel {
        private final Color from;
        private final Color to;

        private GradientCard(Color from, Color to) {
            this.from = from;
            this.to = to;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setPaint(new GradientPaint(0, 0, from, getWidth(), getHeight(), to));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), CARD_RADIUS, CARD_RADIUS);
            g2.setColor(new Color(255, 255, 255, 35));
            g2.fillOval(getWidth() - 150, -70, 220, 220);
            g2.setColor(new Color(255, 255, 255, 22));
            g2.fillOval(-50, getHeight() - 90, 150, 150);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Vong tron mau lam nen cho icon, tao cam giac "badge" thay vi icon tho. */
    private static final class IconBadge extends JPanel {
        private final Color background;

        private IconBadge(FontAwesomeSolid iconType, int iconSize, Color iconColor, Color background, int diameter) {
            this.background = background;
            setOpaque(false);
            setLayout(new GridBagLayout());
            setPreferredSize(new Dimension(diameter, diameter));
            setMinimumSize(new Dimension(diameter, diameter));
            setMaximumSize(new Dimension(diameter, diameter));
            FontIcon icon = FontIcon.of(iconType, iconSize);
            icon.setIconColor(iconColor);
            add(new JLabel(icon));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(background);
            g2.fillOval(0, 0, getWidth(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static final class ScrollablePanel extends JPanel implements Scrollable {
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(
                Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(
                Rectangle visibleRect, int orientation, int direction) {
            return Math.max(16, visibleRect.height - AppSpacing.XL);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}