package com.view.admin.promotion;

import com.components.DatePickerField;
import com.components.crud.BaseFormDialog;
import com.components.crud.CrudMode;
import com.dao.PromotionDAO;
import com.model.Promotion;
import com.service.AuthService;
import com.validation.FormValidator;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.Frame;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

public class PromotionFormDialog extends BaseFormDialog<Promotion> {

    private static final String[] TYPE_LABELS = {"Phần trăm (%)", "Số tiền cố định (đ)"};

    private final PromotionDAO promotionDAO;

    private JTextField codeField;
    private JTextField nameField;
    private JComboBox<String> typeCombo;
    private JTextField discountValueField;
    private JTextField maxDiscountAmountField;
    private JTextField minOrderAmountField;
    private DatePickerField startDatePicker;
    private DatePickerField endDatePicker;
    private JTextField usageLimitField;
    private JCheckBox activeCheckbox;

    public PromotionFormDialog(Frame owner, CrudMode mode, Promotion editingEntity, PromotionDAO promotionDAO) {
        super(owner, "khuyến mãi", mode, editingEntity);
        this.promotionDAO = promotionDAO;
        init();
    }

    @Override
    protected int getDialogWidth() { return 500; }

    @Override
    protected int getDialogHeight() { return 660; }

    @Override
    protected void buildFields(JPanel panel) {
        codeField = newTextField();
        codeField.setDocument(new UpperCaseDocument());
        typeCombo = new JComboBox<>(TYPE_LABELS);
        fieldRow(panel,
                fieldGroup("Mã khuyến mãi", true, codeField),
                fieldGroup("Loại giảm giá", true, typeCombo));

        nameField = addTextField(panel, "Tên chương trình", true);

        discountValueField = newTextField();
        maxDiscountAmountField = newTextField();
        fieldRow(panel,
                fieldGroup("Giá trị giảm", true, discountValueField),
                fieldGroup("Giảm tối đa (chỉ áp dụng khi chọn %)", false, maxDiscountAmountField));

        minOrderAmountField = addTextField(panel, "Giá trị đơn hàng tối thiểu (để trống = 0đ)", false);

        startDatePicker = new DatePickerField(LocalDate.now(), false);
        endDatePicker = new DatePickerField(LocalDate.now().plusDays(30), false);
        fieldRow(panel,
                fieldGroup("Ngày bắt đầu", true, startDatePicker),
                fieldGroup("Ngày kết thúc", true, endDatePicker));

        usageLimitField = addTextField(panel, "Giới hạn số lần sử dụng (để trống = không giới hạn)", false);

        activeCheckbox = new JCheckBox("Kích hoạt ngay");
        activeCheckbox.setSelected(true);
        activeCheckbox.setOpaque(false);
        activeCheckbox.setFont(nameField.getFont());
        panel.add(activeCheckbox);

        panel.add(hintLabel("Khách/thu ngân nhập đúng \"Mã khuyến mãi\" tại quầy để được áp dụng giảm giá."));
    }

    @Override
    protected void fillForm(Promotion entity) {
        codeField.setText(entity.getCode());
        codeField.setEnabled(mode != CrudMode.EDIT); // tranh doi ma sau khi da phat hanh / da co luot dung
        typeCombo.setSelectedIndex(entity.isPercent() ? 0 : 1);
        nameField.setText(entity.getName());
        discountValueField.setText(plain(entity.getDiscountValue()));
        maxDiscountAmountField.setText(plain(entity.getMaxDiscountAmount()));
        minOrderAmountField.setText(plain(entity.getMinOrderAmount()));
        if (entity.getStartDate() != null) startDatePicker.setValue(entity.getStartDate());
        if (entity.getEndDate() != null) endDatePicker.setValue(entity.getEndDate());
        usageLimitField.setText(entity.getUsageLimit() != null ? String.valueOf(entity.getUsageLimit()) : "");
        activeCheckbox.setSelected(entity.isActive());
    }

    @Override
    protected String validateForm() {
        FormValidator validator = new FormValidator();

        validator.field(codeField.getText())
                .required("Vui lòng nhập mã khuyến mãi.")
                .maxLength(30, "Mã khuyến mãi tối đa 30 ký tự.")
                .matches("^[A-Za-z0-9_-]+$", "Mã khuyến mãi chỉ gồm chữ, số, gạch ngang/gạch dưới (không dấu, không khoảng trắng).");

        validator.field(nameField.getText())
                .required("Vui lòng nhập tên chương trình.")
                .maxLength(150, "Tên chương trình tối đa 150 ký tự.");

        String code = codeField.getText() == null ? "" : codeField.getText().trim();
        if (!code.isEmpty()) {
            Integer excludeId = editingEntity != null ? editingEntity.getPromotionId() : null;
            if (promotionDAO.codeExists(code, excludeId)) {
                return "Mã khuyến mãi \"" + code + "\" đã tồn tại, vui lòng chọn mã khác.";
            }
        }

        boolean percent = typeCombo.getSelectedIndex() == 0;

        BigDecimal discountValue = parsePositiveMoney(discountValueField.getText());
        if (discountValue == null) {
            return "Giá trị giảm phải là số dương hợp lệ.";
        }
        if (percent && discountValue.compareTo(BigDecimal.valueOf(100)) > 0) {
            return "Giá trị giảm theo % không được vượt quá 100.";
        }

        if (!isBlank(maxDiscountAmountField.getText()) && parseNonNegativeMoney(maxDiscountAmountField.getText()) == null) {
            return "Giảm tối đa phải là số không âm hợp lệ.";
        }

        if (!isBlank(minOrderAmountField.getText()) && parseNonNegativeMoney(minOrderAmountField.getText()) == null) {
            return "Giá trị đơn hàng tối thiểu phải là số không âm hợp lệ.";
        }

        LocalDate start = startDatePicker.getValue();
        LocalDate end = endDatePicker.getValue();
        if (start == null || end == null) {
            return "Vui lòng chọn đầy đủ ngày bắt đầu và ngày kết thúc.";
        }
        if (end.isBefore(start)) {
            return "Ngày kết thúc phải sau hoặc bằng ngày bắt đầu.";
        }

        if (!isBlank(usageLimitField.getText())) {
            try {
                int limit = Integer.parseInt(usageLimitField.getText().trim());
                if (limit <= 0) return "Giới hạn số lần sử dụng phải lớn hơn 0 (hoặc để trống nếu không giới hạn).";
            } catch (NumberFormatException e) {
                return "Giới hạn số lần sử dụng phải là số nguyên hợp lệ.";
            }
        }

        return validator.validate();
    }

    @Override
    protected Promotion collectFormData() {
        Promotion p = editingEntity != null ? editingEntity : new Promotion();
        p.setCode(codeField.getText().trim().toUpperCase());
        p.setName(nameField.getText().trim());
        boolean percent = typeCombo.getSelectedIndex() == 0;
        p.setDiscountType(percent ? Promotion.TYPE_PERCENT : Promotion.TYPE_AMOUNT);
        p.setDiscountValue(parsePositiveMoney(discountValueField.getText()));
        p.setMaxDiscountAmount(percent && !isBlank(maxDiscountAmountField.getText())
                ? parseNonNegativeMoney(maxDiscountAmountField.getText()) : null);
        p.setMinOrderAmount(isBlank(minOrderAmountField.getText())
                ? BigDecimal.ZERO : parseNonNegativeMoney(minOrderAmountField.getText()));
        p.setStartDate(startDatePicker.getValue());
        p.setEndDate(endDatePicker.getValue());
        p.setUsageLimit(isBlank(usageLimitField.getText()) ? null : Integer.parseInt(usageLimitField.getText().trim()));
        p.setActive(activeCheckbox.isSelected());
        if (mode == CrudMode.ADD) {
            var me = AuthService.getInstance().isLoggedIn() ? AuthService.getInstance().getCurrentUser() : null;
            p.setCreatedBy(me != null ? me.getUserId() : 0);
        }
        return p;
    }

    @Override
    protected boolean persist(Promotion entity, CrudMode mode) {
        return mode == CrudMode.ADD ? promotionDAO.insert(entity) : promotionDAO.update(entity);
    }

    // ---------------------------------------------------------------

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String plain(BigDecimal value) {
        if (value == null) return "";
        return value.stripTrailingZeros().toPlainString();
    }

    private static BigDecimal parsePositiveMoney(String text) {
        BigDecimal value = parseMoney(text);
        return (value != null && value.signum() > 0) ? value : null;
    }

    private static BigDecimal parseNonNegativeMoney(String text) {
        BigDecimal value = parseMoney(text);
        return (value != null && value.signum() >= 0) ? value : null;
    }

    private static BigDecimal parseMoney(String text) {
        if (isBlank(text)) return null;
        try {
            // Cho phep nguoi dung go dau cham phan cach nghin kieu VN (vd "50.000").
            String normalized = text.trim().replace(".", "").replace(",", ".");
            return new BigDecimal(normalized).setScale(0, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Document tu dong viet hoa moi ky tu go vao - cho o Ma khuyen mai (vd "summer10" -> "SUMMER10"). */
    private static class UpperCaseDocument extends javax.swing.text.PlainDocument {
        @Override
        public void insertString(int offs, String str, javax.swing.text.AttributeSet a)
                throws javax.swing.text.BadLocationException {
            if (str != null) str = str.toUpperCase();
            super.insertString(offs, str, a);
        }
    }
}