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

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridLayout;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Dialog thêm / sửa vai trò — layout theo {@link BaseFormDialog}, UX tối ưu:
 * <ul>
 *   <li>Thứ tự: <b>Tên hiển thị</b> → <b>Mã</b> (gợi ý tự động từ tên) → Mô tả</li>
 *   <li>Mã luôn chữ hoa, chỉ A–Z / 0–9 / _</li>
 *   <li>Banner hướng dẫn ngắn; ô mã khóa khi sửa</li>
 *   <li>Validate bằng {@link FormValidator} giống các form khác trong project</li>
 * </ul>
 */
public class RoleFormDialog extends BaseFormDialog<AppRole> {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{1,29}$");

    private final RoleDAO roleDao;

    private JTextField nameField;
    private JTextField codeField;
    private JTextArea descriptionArea;
    private JLabel codeHintLabel;

    /** true khi user đã tự sửa mã — không ghi đè bằng gợi ý từ tên. */
    private boolean codeManuallyEdited;
    private boolean applyingSuggestedCode;

    public RoleFormDialog(Frame owner, CrudMode mode, AppRole entity, RoleDAO roleDao) {
        super(owner, "vai trò", mode, entity);
        this.roleDao = roleDao;
        init();
    }

    @Override
    protected int getDialogWidth() {
        return 520;
    }

    @Override
    protected int getDialogHeight() {
        return mode == CrudMode.ADD ? 520 : 480;
    }

    // ---------------------------------------------------------------
    // Fields
    // ---------------------------------------------------------------

    @Override
    protected void buildFields(JPanel panel) {
        panel.add(buildInfoBanner());
        panel.add(Box.createVerticalStrut(16));

        // 1. Tên hiển thị (ưu tiên — dễ hiểu với Admin)
        nameField = addTextField(panel, "Tên hiển thị", true);
        panel.add(hintUnder("Tên hiện trên sidebar và danh sách nhân viên (vd: Thu ngân, Kế toán)."));
        panel.add(Box.createVerticalStrut(4));

        // 2. Mã vai trò
        codeField = addTextField(panel, "Mã vai trò", true);
        installCodeFilter(codeField);
        codeHintLabel = hintUnder(mode == CrudMode.ADD
                ? "Tự gợi ý từ tên. Chỉ A–Z, 0–9, gạch dưới; bắt đầu bằng chữ (vd: CASHIER)."
                : "Mã không đổi sau khi tạo — dùng để gán user và phân quyền.");
        panel.add(codeHintLabel);
        panel.add(Box.createVerticalStrut(4));

        // 3. Mô tả
        descriptionArea = addTextArea(panel, "Mô tả");
        panel.add(hintUnder("Tuỳ chọn. Giúp Admin khác hiểu vai trò này dùng để làm gì."));

        if (mode == CrudMode.EDIT && editingEntity != null) {
            codeField.setEditable(false);
            codeField.setEnabled(false);
            codeField.setBackground(AppColor.BG_LIGHTER);
            if (editingEntity.isSystemRole()) {
                panel.add(Box.createVerticalStrut(8));
                panel.add(buildSystemBadge());
            }
        } else if (mode == CrudMode.ADD) {
            wireNameToCodeSuggestion();
            codeField.getDocument().addDocumentListener(new DocumentListener() {
                private void mark() {
                    if (!applyingSuggestedCode) codeManuallyEdited = true;
                    updateCodeHintLive();
                }
                @Override public void insertUpdate(DocumentEvent e) { mark(); }
                @Override public void removeUpdate(DocumentEvent e) { mark(); }
                @Override public void changedUpdate(DocumentEvent e) { mark(); }
            });
        }
    }

    private JPanel buildInfoBanner() {
        JPanel banner = new JPanel(new BorderLayout(12, 0));
        banner.setOpaque(true);
        banner.setBackground(AppColor.ACCENT_SOFT != null ? AppColor.ACCENT_SOFT : new Color(232, 240, 254));
        banner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(12, 14, 12, 14)));
        banner.setAlignmentX(Component.LEFT_ALIGNMENT);
        banner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));

        FontIcon icon = FontIcon.of(
                mode == CrudMode.ADD ? FontAwesomeSolid.USER_PLUS : FontAwesomeSolid.USER_EDIT, 18);
        icon.setIconColor(AppColor.ACCENT);
        JLabel iconLabel = new JLabel(icon);

        String html = mode == CrudMode.ADD
                ? "<html><b>Tạo vai trò mới</b><br/>"
                + "Sau khi lưu, mở <b>Phân quyền vai trò</b> để bật các chức năng cho vai trò này.</html>"
                : "<html><b>Cập nhật vai trò</b><br/>"
                + "Chỉ đổi tên và mô tả. Quyền chi tiết cấu hình ở trang Phân quyền.</html>";

        JLabel text = new JLabel(html);
        text.setFont(AppFont.BODY);
        text.setForeground(AppColor.TEXT_PRIMARY);

        banner.add(iconLabel, BorderLayout.WEST);
        banner.add(text, BorderLayout.CENTER);
        return banner;
    }

    private JPanel buildSystemBadge() {
        JPanel row = new JPanel(new GridLayout(1, 1));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel badge = new JLabel("<html><span style='color:#0B6E99'>"
                + "Vai trò hệ thống — không đổi mã, không xóa được."
                + "</span></html>");
        badge.setFont(AppFont.SMALL);
        row.add(badge);
        return row;
    }

    private JLabel hintUnder(String text) {
        JLabel hint = hintLabel(text);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        return hint;
    }

    // ---------------------------------------------------------------
    // Mã: filter + gợi ý từ tên
    // ---------------------------------------------------------------

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
                updateCodeHintLive();
            }

            @Override public void insertUpdate(DocumentEvent e) { suggest(); }
            @Override public void removeUpdate(DocumentEvent e) { suggest(); }
            @Override public void changedUpdate(DocumentEvent e) { suggest(); }
        });
    }

    /**
     * "Thu ngân quầy 1" → THU_NGAN_QUAY_1 ; bỏ dấu tiếng Việt.
     */
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

    private void updateCodeHintLive() {
        if (codeHintLabel == null || mode != CrudMode.ADD) return;
        String code = codeField.getText() != null ? codeField.getText().trim() : "";
        if (code.isEmpty()) {
            codeHintLabel.setText("Tự gợi ý từ tên. Chỉ A–Z, 0–9, gạch dưới; bắt đầu bằng chữ.");
            codeHintLabel.setForeground(AppColor.TEXT_MUTED);
            return;
        }
        if (CODE_PATTERN.matcher(code).matches()) {
            if (roleDao.findByCode(code) != null) {
                codeHintLabel.setText("Mã \"" + code + "\" đã tồn tại — hãy chọn mã khác.");
                codeHintLabel.setForeground(AppColor.ERROR);
            } else {
                codeHintLabel.setText("Mã hợp lệ: " + code);
                codeHintLabel.setForeground(AppColor.SUCCESS);
            }
        } else {
            codeHintLabel.setText("Mã chưa hợp lệ (2–30 ký tự, bắt đầu bằng chữ, chỉ A–Z 0–9 _).");
            codeHintLabel.setForeground(AppColor.WARNING);
        }
    }

    // ---------------------------------------------------------------
    // BaseFormDialog hooks
    // ---------------------------------------------------------------

    @Override
    protected void fillForm(AppRole entity) {
        nameField.setText(entity.getRoleName() != null ? entity.getRoleName() : "");
        codeField.setText(entity.getRoleCode() != null ? entity.getRoleCode() : "");
        descriptionArea.setText(entity.getDescription() != null ? entity.getDescription() : "");
        codeManuallyEdited = true; // không ghi đè khi edit
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
        if (desc != null && desc.length() > 255) {
            return "Mô tả tối đa 255 ký tự.";
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
}
