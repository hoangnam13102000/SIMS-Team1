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
 * Trang liên hệ của khu vực khách hàng.
 * SMTP luôn chạy trong SwingWorker để không chặn Event Dispatch Thread.
 */
public class ContactPanel extends JPanel {

    private static final int CARD_RADIUS = 18;
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

    private JComponent buildHeroSection() {
        RoundedCard card = new RoundedCard(AppColor.ACCENT_BG_SOFT, false);
        card.setLayout(new BorderLayout(AppSpacing.XL, 0));
        card.setBorder(new EmptyBorder(
                AppSpacing.XL, AppSpacing.XXL, AppSpacing.XL, AppSpacing.XXL));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));

        JLabel heroIcon = createIconBox(FontAwesomeSolid.HEADPHONES, 34);
        heroIcon.setPreferredSize(new Dimension(72, 72));
        card.add(heroIcon, BorderLayout.WEST);

        JPanel text = transparentColumn();
        JLabel title = new JLabel(Lang.get("contact.hero.title"));
        title.setFont(AppFont.TITLE);
        title.setForeground(AppColor.TEXT_TITLE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextArea subtitle = createReadOnlyText(
                Lang.get("contact.hero.subtitle"), AppFont.BODY, AppColor.TEXT_SECONDARY, 2);
        subtitle.setBorder(new EmptyBorder(AppSpacing.SM, 0, 0, 0));

        RoundedCard badge = new RoundedCard(AppColor.WHITE, false);
        badge.setLayout(new FlowLayout(FlowLayout.LEFT, AppSpacing.SM, AppSpacing.XS));
        badge.setBorder(new EmptyBorder(2, AppSpacing.SM, 2, AppSpacing.SM));
        badge.setAlignmentX(Component.LEFT_ALIGNMENT);
        badge.setMaximumSize(new Dimension(260, 34));
        badge.add(createIconLabel(FontAwesomeSolid.HEART, 12, AppColor.ACCENT));
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

    private JComponent buildSupportChannelsSection() {
        JPanel cards = new JPanel(new GridLayout(1, 3, AppSpacing.LG, 0));
        cards.setOpaque(false);
        cards.add(createSupportCard(
                FontAwesomeSolid.ENVELOPE,
                "contact.channel.email.title",
                "contact.channel.email.description"));
        cards.add(createSupportCard(
                FontAwesomeSolid.COMMENTS,
                "contact.channel.response.title",
                "contact.channel.response.description"));
        cards.add(createSupportCard(
                FontAwesomeSolid.SHIELD_ALT,
                "contact.channel.security.title",
                "contact.channel.security.description"));
        cards.setAlignmentX(Component.LEFT_ALIGNMENT);
        return cards;
    }

    private JComponent buildContactFormSection() {
        RoundedCard card = new RoundedCard(AppColor.WHITE, false);
        card.setLayout(new BorderLayout());
        card.setBorder(new EmptyBorder(
                AppSpacing.XL, AppSpacing.XL, AppSpacing.XL, AppSpacing.XL));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 760));

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

        JLabel privacyNote = new JLabel(Lang.get("contact.privacy.note"));
        privacyNote.setFont(AppFont.SMALL);
        privacyNote.setForeground(AppColor.TEXT_MUTED);
        privacyNote.setIcon(createFontIcon(FontAwesomeSolid.LOCK, 12, AppColor.TEXT_MUTED));
        privacyNote.setIconTextGap(AppSpacing.SM);
        privacyNote.setAlignmentX(Component.LEFT_ALIGNMENT);
        privacyNote.setBorder(new EmptyBorder(AppSpacing.MD, 0, AppSpacing.MD, 0));
        form.add(privacyNote);

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

    private JComponent createSupportCard(
            FontAwesomeSolid icon, String titleKey, String descriptionKey) {
        RoundedCard card = new RoundedCard(AppColor.WHITE, true);
        card.setLayout(new BorderLayout(AppSpacing.MD, 0));
        card.setBorder(new EmptyBorder(
                AppSpacing.LG, AppSpacing.LG, AppSpacing.LG, AppSpacing.LG));
        card.setPreferredSize(new Dimension(260, 145));
        card.add(createIconBox(icon, 20), BorderLayout.WEST);

        JPanel text = transparentColumn();
        JLabel title = new JLabel(Lang.get(titleKey));
        title.setFont(AppFont.HEADING_MD);
        title.setForeground(AppColor.TEXT_TITLE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(title);
        text.add(Box.createVerticalStrut(AppSpacing.SM));
        text.add(createReadOnlyText(
                Lang.get(descriptionKey), AppFont.BODY, AppColor.TEXT_SECONDARY, 3));
        card.add(text, BorderLayout.CENTER);
        return card;
    }

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

    private JLabel createIconBox(FontAwesomeSolid iconType, int size) {
        JLabel label = createIconLabel(iconType, size, AppColor.ACCENT);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setVerticalAlignment(SwingConstants.TOP);
        label.setPreferredSize(new Dimension(44, 44));
        return label;
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
