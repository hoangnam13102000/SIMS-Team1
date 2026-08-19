package com.view.admin.permission;

import com.components.crud.BaseFormDialog;
import com.components.crud.CrudMode;
import com.dao.RoleDAO;
import com.model.AppRole;
import com.theme.AppColor;
import com.theme.AppFont;
import com.validation.FormValidator;
import com.validation.Rules;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Dialog thêm / sửa vai trò — tối ưu UX:
 * <ul>
 *   <li>Gợi ý mã ngay lập tức (không chậm)</li>
 *   <li>Validation trễ 300ms sau khi dừng gõ (debounce)</li>
 *   <li>Không truy vấn DB trên mỗi ký tự → không lag</li>
 *   <li>Icon từng field, badge vai trò hệ thống, đếm ký tự</li>
 * </ul>
 */
public class RoleFormDialog extends BaseFormDialog<AppRole> {
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{1,29}$");
    private static final int MAX_DESC_LENGTH = 255;
    private static final int VALIDATION_DELAY_MS = 300; // Chờ 300ms sau khi dừng gõ
    
    private final RoleDAO roleDao;
    private JTextField nameField;
    private JTextField codeField;
    private JTextArea descriptionArea;
    private JLabel codeHintLabel;
    private JLabel descCounterLabel;
    private JLabel codeStatusIcon;
    
    private boolean codeManuallyEdited;
    private boolean applyingSuggestedCode;
    
    // Timer cho debounce validation
    private Timer validationTimer;

    public RoleFormDialog(Frame owner, CrudMode mode, AppRole entity, RoleDAO roleDao) {
        super(owner, "vai trò", mode, entity);
        this.roleDao = roleDao;
        init();
    }

    @Override
    protected int getDialogWidth() {
        return 540;
    }

    @Override
    protected int getDialogHeight() {
        return mode == CrudMode.ADD ? 580 : 560;
    }

    @Override
    protected void buildFields(JPanel panel) {
        panel.add(buildInfoBanner());
        panel.add(Box.createVerticalStrut(16));

        // 1. Tên hiển thị
        panel.add(fieldLabel("Tên hiển thị", true));
        JPanel nameFieldWrapper = createIconTextFieldWrapper(FontAwesomeSolid.USER);
        nameField = (JTextField) nameFieldWrapper.getClientProperty("field");
        panel.add(nameFieldWrapper);
        panel.add(hintUnder("Tên hiện trên sidebar và danh sách nhân viên (vd: Thu ngân, Kế toán)."));
        panel.add(Box.createVerticalStrut(4));

        // 2. Mã vai trò
        panel.add(fieldLabel("Mã vai trò", true));
        JPanel codeFieldWrapper = createIconTextFieldWrapper(FontAwesomeSolid.CODE);
        codeField = (JTextField) codeFieldWrapper.getClientProperty("field");
        installCodeFilter(codeField);
        panel.add(codeFieldWrapper);
        
        // Status icon + hint
        JPanel codeHintRow = new JPanel(new BorderLayout(4, 0));
        codeHintRow.setOpaque(false);
        codeHintRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        codeStatusIcon = new JLabel();
        codeStatusIcon.setPreferredSize(new Dimension(16, 16));
        codeHintLabel = hintLabel(mode == CrudMode.ADD
                ? "Tự gợi ý từ tên. Chỉ A–Z, 0–9, gạch dưới; bắt đầu bằng chữ."
                : "Mã không đổi sau khi tạo — dùng để gán user và phân quyền.");
        codeHintRow.add(codeStatusIcon, BorderLayout.WEST);
        codeHintRow.add(codeHintLabel, BorderLayout.CENTER);
        panel.add(codeHintRow);
        panel.add(Box.createVerticalStrut(4));

        // 3. Mô tả
        panel.add(fieldLabel("Mô tả", false));
        JPanel descFieldWrapper = createIconTextAreaWrapper(FontAwesomeSolid.ALIGN_JUSTIFY, 3);
        descriptionArea = (JTextArea) descFieldWrapper.getClientProperty("field");
        panel.add(descFieldWrapper);
        
        // Đếm ký tự + hint
        JPanel descHintRow = new JPanel(new BorderLayout());
        descHintRow.setOpaque(false);
        descHintRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        descHintRow.add(hintLabel("Tuỳ chọn. Giúp Admin khác hiểu vai trò này dùng để làm gì."), BorderLayout.WEST);
        descCounterLabel = new JLabel("0/" + MAX_DESC_LENGTH);
        descCounterLabel.setFont(AppFont.SMALL);
        descCounterLabel.setForeground(AppColor.TEXT_MUTED);
        descHintRow.add(descCounterLabel, BorderLayout.EAST);
        panel.add(descHintRow);
        
        // Wire description counter
        descriptionArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { updateDescCounter(); }
            @Override public void removeUpdate(DocumentEvent e) { updateDescCounter(); }
            @Override public void changedUpdate(DocumentEvent e) { updateDescCounter(); }
        });

        if (mode == CrudMode.EDIT && editingEntity != null) {
            codeField.setEditable(false);
            codeField.setEnabled(false);
            codeField.setBackground(AppColor.BG_LIGHTER);
            codeField.setForeground(AppColor.TEXT_DISABLED);
            addLockIconToWrapper(codeFieldWrapper);
            
            if (editingEntity.isSystemRole()) {
                panel.add(Box.createVerticalStrut(8));
                panel.add(buildSystemBadge());
            }
        } else if (mode == CrudMode.ADD) {
            // CHỈ gợi ý mã NGAY LẬP TỨC, KHÔNG validate nặng
            wireNameToCodeSuggestion();
            
            // Validation CHẠY SAU KHI DỪNG GÕ 300ms
            codeField.getDocument().addDocumentListener(new DocumentListener() {
                private void mark() {
                    if (!applyingSuggestedCode) codeManuallyEdited = true;
                    scheduleValidation();
                }
                @Override public void insertUpdate(DocumentEvent e) { mark(); }
                @Override public void removeUpdate(DocumentEvent e) { mark(); }
                @Override public void changedUpdate(DocumentEvent e) { mark(); }
            });
        }
    }

    /**
     * ⚡ Gợi ý mã NGAY LẬP TỨC - chỉ xử lý chuỗi, KHÔNG truy vấn DB, KHÔNG đổi màu
     * → tốc độ gõ mượt mà, không lag
     */
    private void wireNameToCodeSuggestion() {
        nameField.getDocument().addDocumentListener(new DocumentListener() {
            private void suggest() {
                if (codeManuallyEdited) return;
                String suggested = suggestCodeFromName(nameField.getText());
                applyingSuggestedCode = true;
                try {
                    codeField.setText(suggested);
                } finally {
                    applyingSuggestedCode = false;
                }
                // CHỈ hiển thị hint cơ bản, KHÔNG validate nặng
                updateBasicCodeHint();
            }
            @Override public void insertUpdate(DocumentEvent e) { suggest(); }
            @Override public void removeUpdate(DocumentEvent e) { suggest(); }
            @Override public void changedUpdate(DocumentEvent e) { suggest(); }
        });
    }

    /**
     * ⏱️ Lên lịch validation: reset timer mỗi lần gõ, chỉ chạy khi dừng gõ 300ms
     * → tránh chạy liên tục, không chặn luồng giao diện
     */
    private void scheduleValidation() {
        if (validationTimer != null && validationTimer.isRunning()) {
            validationTimer.restart();
            return;
        }
        validationTimer = new Timer(VALIDATION_DELAY_MS, e -> validateCodeInBackground());
        validationTimer.setRepeats(false);
        validationTimer.start();
    }

    /**
     * 🔄 Chạy validation nặng (truy vấn DB) trên LUỒNG NỀN, cập nhật UI trên EDT
     * → giao diện không bị đóng băng
     */
    private void validateCodeInBackground() {
        final String code = codeField.getText() != null ? codeField.getText().trim() : "";
        
        // Hiển thị trạng thái "đang kiểm tra..."
        SwingUtilities.invokeLater(() -> {
            codeHintLabel.setText("Đang kiểm tra...");
            codeHintLabel.setForeground(AppColor.TEXT_MUTED);
            codeStatusIcon.setIcon(null);
        });
        
        // Chạy nặng trên luồng nền
        new SwingWorker<ValidationResult, Void>() {
            @Override
            protected ValidationResult doInBackground() {
                return checkCodeValidity(code);
            }
            
            @Override
            protected void done() {
                try {
                    ValidationResult result = get();
                    updateCodeUI(result);
                } catch (Exception ex) {
                    // Fallback nếu lỗi
                    codeHintLabel.setText("Tự gợi ý từ tên. Chỉ A–Z, 0–9, gạch dưới.");
                    codeHintLabel.setForeground(AppColor.TEXT_MUTED);
                }
            }
        }.execute();
    }

    /**
     * Kiểm tra tính hợp lệ của mã (gọi từ luồng nền)
     */
    private ValidationResult checkCodeValidity(String code) {
        if (code.isEmpty()) {
            return new ValidationResult(ValidationState.EMPTY, 
                "Tự gợi ý từ tên. Chỉ A–Z, 0–9, gạch dưới; bắt đầu bằng chữ.");
        }
        
        if (!CODE_PATTERN.matcher(code).matches()) {
            return new ValidationResult(ValidationState.INVALID_FORMAT,
                "Mã chưa hợp lệ (2–30 ký tự, bắt đầu bằng chữ, chỉ A–Z 0–9 _).");
        }
        
        // ⚠️ Truy vấn DB - chạy trên luồng nền, không chặn giao diện
        if (roleDao.findByCode(code) != null) {
            return new ValidationResult(ValidationState.DUPLICATE,
                "Mã \"" + code + "\" đã tồn tại — hãy chọn mã khác.");
        }
        
        return new ValidationResult(ValidationState.VALID, "Mã hợp lệ: " + code);
    }

    /**
     * Cập nhật giao diện theo kết quả validation
     */
    private void updateCodeUI(ValidationResult result) {
        JPanel codeWrapper = (JPanel) SwingUtilities.getAncestorOfClass(JPanel.class, codeField);
        codeHintLabel.setText(result.message);
        
        switch (result.state) {
            case EMPTY:
                codeHintLabel.setForeground(AppColor.TEXT_MUTED);
                setWrapperBorder(codeWrapper, AppColor.FIELD_BORDER);
                codeStatusIcon.setIcon(null);
                break;
            case VALID:
                codeHintLabel.setForeground(AppColor.SUCCESS);
                setWrapperBorder(codeWrapper, AppColor.SUCCESS);
                setStatusIcon(FontAwesomeSolid.CHECK_CIRCLE, AppColor.SUCCESS);
                break;
            case INVALID_FORMAT:
                codeHintLabel.setForeground(AppColor.WARNING);
                setWrapperBorder(codeWrapper, AppColor.WARNING);
                setStatusIcon(FontAwesomeSolid.EXCLAMATION_TRIANGLE, AppColor.WARNING);
                break;
            case DUPLICATE:
                codeHintLabel.setForeground(AppColor.ERROR);
                setWrapperBorder(codeWrapper, AppColor.ERROR);
                setStatusIcon(FontAwesomeSolid.TIMES_CIRCLE, AppColor.ERROR);
                break;
        }
    }

    /**
     * Hint cơ bản khi đang gõ - không truy vấn DB
     */
    private void updateBasicCodeHint() {
        if (codeHintLabel == null || mode != CrudMode.ADD) return;
        String code = codeField.getText() != null ? codeField.getText().trim() : "";
        JPanel codeWrapper = (JPanel) SwingUtilities.getAncestorOfClass(JPanel.class, codeField);
        
        if (code.isEmpty()) {
            codeHintLabel.setText("Tự gợi ý từ tên. Chỉ A–Z, 0–9, gạch dưới; bắt đầu bằng chữ.");
            codeHintLabel.setForeground(AppColor.TEXT_MUTED);
            setWrapperBorder(codeWrapper, AppColor.FIELD_BORDER);
            codeStatusIcon.setIcon(null);
            return;
        }
        
        // Chỉ check định dạng CƠ BẢN, KHÔNG query DB
        if (!CODE_PATTERN.matcher(code).matches()) {
            codeHintLabel.setText("Định dạng: 2–30 ký tự, bắt đầu bằng chữ, A–Z 0–9 _");
            codeHintLabel.setForeground(AppColor.TEXT_MUTED);
            setWrapperBorder(codeWrapper, AppColor.FIELD_BORDER);
            codeStatusIcon.setIcon(null);
        }
        // Nếu hợp lệ định dạng → để validation timer kiểm tra trùng sau
    }

    // === CÁC HÀM HỖ TRỢ KHÁC GIỮ NGUYÊN ===
    
    private JPanel createIconTextFieldWrapper(FontAwesomeSolid iconKey) {
        JPanel wrapper = new JPanel(new BorderLayout(0, 0));
        wrapper.setOpaque(true);
        wrapper.setBackground(AppColor.WHITE);
        wrapper.setBorder(createStyledBorder());
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        FontIcon icon = FontIcon.of(iconKey, 14);
        icon.setIconColor(AppColor.TEXT_MUTED);
        JLabel iconLabel = new JLabel(icon);
        iconLabel.setBorder(new EmptyBorder(0, 8, 0, 6));
        iconLabel.setOpaque(false);
        wrapper.add(iconLabel, BorderLayout.WEST);

        JTextField field = new JTextField();
        field.setFont(AppFont.BODY);
        field.setForeground(AppColor.TEXT_PRIMARY);
        field.setBackground(AppColor.WHITE);
        field.setCaretColor(AppColor.ACCENT);
        field.setBorder(new EmptyBorder(6, 2, 6, 8));
        field.setOpaque(true);
        wrapper.add(field, BorderLayout.CENTER);

        wrapper.putClientProperty("field", field);
        wrapper.putClientProperty("iconLabel", iconLabel);
        return wrapper;
    }

    private JPanel createIconTextAreaWrapper(FontAwesomeSolid iconKey, int rows) {
        JPanel wrapper = new JPanel(new BorderLayout(0, 0));
        wrapper.setOpaque(true);
        wrapper.setBackground(AppColor.WHITE);
        wrapper.setBorder(createStyledBorder());
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);

        FontIcon icon = FontIcon.of(iconKey, 14);
        icon.setIconColor(AppColor.TEXT_MUTED);
        JLabel iconLabel = new JLabel(icon);
        iconLabel.setVerticalAlignment(SwingConstants.TOP);
        iconLabel.setBorder(new EmptyBorder(8, 8, 0, 6));
        iconLabel.setOpaque(false);
        wrapper.add(iconLabel, BorderLayout.WEST);

        JTextArea area = new JTextArea(rows, 20);
        area.setFont(AppFont.BODY);
        area.setForeground(AppColor.TEXT_PRIMARY);
        area.setBackground(AppColor.WHITE);
        area.setCaretColor(AppColor.ACCENT);
        area.setBorder(new EmptyBorder(6, 2, 6, 8));
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(true);
        
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        
        wrapper.add(scroll, BorderLayout.CENTER);
        wrapper.putClientProperty("field", area);
        return wrapper;
    }

    private Border createStyledBorder() {
        return new LineBorder(AppColor.FIELD_BORDER, 1, true);
    }

    private void setWrapperBorder(JPanel wrapper, Color borderColor) {
        if (wrapper == null) return;
        wrapper.setBorder(new LineBorder(borderColor, 1, true));
    }

    private void addLockIconToWrapper(JPanel wrapper) {
        if (wrapper == null) return;
        FontIcon lockIcon = FontIcon.of(FontAwesomeSolid.LOCK, 12);
        lockIcon.setIconColor(AppColor.TEXT_MUTED);
        JLabel lockLabel = new JLabel(lockIcon);
        lockLabel.setBorder(new EmptyBorder(0, 4, 0, 8));
        lockLabel.setOpaque(false);
        wrapper.add(lockLabel, BorderLayout.EAST);
        wrapper.revalidate();
        wrapper.repaint();
    }

    private void setStatusIcon(FontAwesomeSolid icon, Color color) {
        if (codeStatusIcon == null) return;
        FontIcon fi = FontIcon.of(icon, 12);
        fi.setIconColor(color);
        codeStatusIcon.setIcon(fi);
    }

    private JPanel buildInfoBanner() {
        JPanel banner = new JPanel(new BorderLayout(12, 0));
        banner.setOpaque(true);
        banner.setBackground(AppColor.ACCENT_BG_SOFT);
        banner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColor.ACCENT, 1, true),
                new EmptyBorder(12, 14, 12, 14)));
        banner.setAlignmentX(Component.LEFT_ALIGNMENT);
        banner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 76));

        JPanel iconWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        iconWrap.setOpaque(false);
        iconWrap.setPreferredSize(new Dimension(32, 32));
        FontIcon icon = FontIcon.of(
                mode == CrudMode.ADD ? FontAwesomeSolid.USER_PLUS : FontAwesomeSolid.USER_EDIT, 16);
        icon.setIconColor(AppColor.ACCENT);
        JLabel iconLabel = new JLabel(icon);
        iconWrap.add(iconLabel);

        String html = mode == CrudMode.ADD
                ? "<html><b style='color:" + hexColor(AppColor.TEXT_PRIMARY) + "'>Tạo vai trò mới</b><br/>"
                + "<span style='color:" + hexColor(AppColor.TEXT_SECONDARY) + "'>"
                + "Sau khi lưu, mở <b>Phân quyền vai trò</b> để bật các chức năng.</span></html>"
                : "<html><b style='color:" + hexColor(AppColor.TEXT_PRIMARY) + "'>Cập nhật vai trò</b><br/>"
                + "<span style='color:" + hexColor(AppColor.TEXT_SECONDARY) + "'>"
                + "Chỉ đổi tên và mô tả. Quyền chi tiết ở trang Phân quyền.</span></html>";
        JLabel text = new JLabel(html);
        text.setFont(AppFont.BODY);

        banner.add(iconWrap, BorderLayout.WEST);
        banner.add(text, BorderLayout.CENTER);
        return banner;
    }

    private JPanel buildSystemBadge() {
        JPanel badge = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        badge.setOpaque(true);
        badge.setAlignmentX(Component.LEFT_ALIGNMENT);
        badge.setBackground(AppColor.INFO_BG);
        badge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColor.INFO, 1, true),
                new EmptyBorder(6, 10, 6, 10)));

        FontIcon shieldIcon = FontIcon.of(FontAwesomeSolid.SHIELD_ALT, 12);
        shieldIcon.setIconColor(AppColor.INFO);
        JLabel iconLbl = new JLabel(shieldIcon);

        JLabel text = new JLabel("<html><b style='color:" + hexColor(AppColor.INFO) + "'>"
                + "VAI TRÒ HỆ THỐNG</b> "
                + "<span style='color:" + hexColor(AppColor.TEXT_MUTED) + "'>"
                + "· Không đổi mã, không xóa được</span></html>");
        text.setFont(AppFont.SMALL);

        badge.add(iconLbl);
        badge.add(text);
        
        JPanel outer = new JPanel(new BorderLayout());
        outer.setOpaque(false);
        outer.setAlignmentX(Component.LEFT_ALIGNMENT);
        outer.add(badge, BorderLayout.WEST);
        return outer;
    }

    private JLabel hintUnder(String text) {
        JLabel hint = hintLabel(text);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        return hint;
    }

    private void updateDescCounter() {
        if (descCounterLabel == null) return;
        int len = descriptionArea.getText() != null ? descriptionArea.getText().length() : 0;
        descCounterLabel.setText(len + "/" + MAX_DESC_LENGTH);
        if (len > MAX_DESC_LENGTH) {
            descCounterLabel.setForeground(AppColor.ERROR);
        } else if (len > MAX_DESC_LENGTH * 0.9) {
            descCounterLabel.setForeground(AppColor.WARNING);
        } else {
            descCounterLabel.setForeground(AppColor.TEXT_MUTED);
        }
    }

    private void installCodeFilter(JTextField field) {
        AbstractDocument doc = (AbstractDocument) field.getDocument();
        doc.setDocumentFilter(new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
                    throws BadLocationException {
                if (string == null) return;
                super.insertString(fb, offset, sanitizeCodeChunk(string), attr);
            }
            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                    throws BadLocationException {
                if (text == null) {
                    super.replace(fb, offset, length, null, attrs);
                    return;
                }
                super.replace(fb, offset, length, sanitizeCodeChunk(text), attrs);
            }
        });
    }

    private static String sanitizeCodeChunk(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (char c : raw.toCharArray()) {
            char u = Character.toUpperCase(c);
            if ((u >= 'A' && u <= 'Z') || (u >= '0' && u <= '9') || u == '_') {
                sb.append(u);
            }
        }
        return sb.toString();
    }

    static String suggestCodeFromName(String name) {
        if (name == null || name.isBlank()) return "";
        String n = Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        n = n.replace("đ", "d").replace("Đ", "D");
        n = n.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        n = n.replaceAll("^_+|_+$", "").replaceAll("_+", "_");
        if (n.isEmpty()) return "";
        if (n.charAt(0) >= '0' && n.charAt(0) <= '9') {
            n = "R_" + n;
        }
        if (n.length() > 30) n = n.substring(0, 30).replaceAll("_+$", "");
        return n;
    }

    @Override
    protected void fillForm(AppRole entity) {
        nameField.setText(entity.getRoleName() != null ? entity.getRoleName() : "");
        codeField.setText(entity.getRoleCode() != null ? entity.getRoleCode() : "");
        descriptionArea.setText(entity.getDescription() != null ? entity.getDescription() : "");
        codeManuallyEdited = true;
        updateDescCounter();
    }

    @Override
    protected String validateForm() {
        FormValidator validator = new FormValidator();
        validator.field(nameField.getText())
                .required("Vui lòng nhập tên hiển thị.")
                .maxLength(100, "Tên hiển thị tối đa 100 ký tự.");

        if (mode == CrudMode.ADD) {
            String code = codeField.getText() != null ? codeField.getText().trim() : "";
            validator.field(code)
                    .required("Vui lòng nhập mã vai trò.")
                    .maxLength(30, "Mã vai trò tối đa 30 ký tự.")
                    .matches("^[A-Z][A-Z0-9_]{1,29}$",
                            "Mã phải bắt đầu bằng chữ, chỉ gồm A–Z, 0–9 và _ (2–30 ký tự).")
                    .rule(Rules.custom(
                            v -> roleDao.findByCode(v.trim().toUpperCase(Locale.ROOT)) == null,
                            "Mã vai trò này đã tồn tại."));
        }

        String desc = descriptionArea.getText();
        if (desc != null && desc.length() > MAX_DESC_LENGTH) {
            return "Mô tả tối đa " + MAX_DESC_LENGTH + " ký tự.";
        }

        return validator.validate();
    }

    @Override
    protected AppRole collectFormData() {
        AppRole role = editingEntity != null ? editingEntity : new AppRole();
        if (mode == CrudMode.ADD) {
            role.setRoleCode(codeField.getText().trim().toUpperCase(Locale.ROOT));
            role.setSystemRole(false);
        }
        role.setRoleName(nameField.getText().trim());
        String desc = descriptionArea.getText();
        role.setDescription(desc != null && !desc.isBlank() ? desc.trim() : null);
        return role;
    }

    @Override
    protected boolean persist(AppRole entity, CrudMode mode) {
        if (mode == CrudMode.ADD) {
            String err = roleDao.create(entity.getRoleCode(), entity.getRoleName(), entity.getDescription());
            if (err != null) {
                setPersistFailureMessage(err);
                return false;
            }
            return true;
        }
        String err = roleDao.update(entity.getRoleId(), entity.getRoleName(), entity.getDescription());
        if (err != null) {
            setPersistFailureMessage(err);
            return false;
        }
        return true;
    }

    private static String hexColor(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    // === Các lớp hỗ trợ cho validation ===
    
    private enum ValidationState {
        EMPTY, VALID, INVALID_FORMAT, DUPLICATE
    }
    
    private static class ValidationResult {
        final ValidationState state;
        final String message;
        
        ValidationResult(ValidationState state, String message) {
            this.state = state;
            this.message = message;
        }
    }
}