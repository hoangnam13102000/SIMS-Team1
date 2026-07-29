package com.view.admin.category;

import com.components.crud.BaseFormDialog;
import com.components.crud.CrudMode;
import com.dao.CategoryDAO;
import com.model.Category;
import com.validation.FormValidator;
import com.validation.Rules;

import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.Frame;

/**
 * Dialog Thêm mới / Cập nhật 1 danh mục, dùng trong {@link CategoryPanel}.
 * Chỉ 2 trường: Tên danh mục (duy nhất, không trùng) và Trạng thái
 * (ACTIVE/DISABLED) - giống cấu trúc bảng Categories trong SIMS.sql.
 * <p>
 * Không hỗ trợ xóa cứng (xem {@link CategoryPanel#supportsDelete()}): danh
 * mục đang được sản phẩm tham chiếu (FOREIGN KEY) nên "Vô hiệu hóa" ở đây
 * (đổi Trạng thái = DISABLED) là cách an toàn để ẩn danh mục khỏi phía
 * khách hàng mà không vi phạm ràng buộc khóa ngoại.
 */
public class CategoryFormDialog extends BaseFormDialog<Category> {

    private static final String[] STATUS_LABELS = {"Đang hoạt động", "Vô hiệu hóa"};

    private final CategoryDAO categoryDAO;

    private JTextField categoryNameField;
    private JComboBox<String> statusCombo;

    public CategoryFormDialog(Frame owner, CrudMode mode, Category editingEntity, CategoryDAO categoryDAO) {
        super(owner, "danh mục", mode, editingEntity);
        this.categoryDAO = categoryDAO;
        init();
    }

    @Override
    protected int getDialogWidth() { return 440; }

    @Override
    protected int getDialogHeight() { return 320; }

    @Override
    protected void buildFields(JPanel panel) {
        categoryNameField = addTextField(panel, "Tên danh mục", true);
        statusCombo = addComboBox(panel, "Trạng thái", STATUS_LABELS);

        // Danh muc moi luon o trang thai "Dang hoat dong" - chi cho phep
        // chon "Vo hieu hoa" khi Sua (giong Customer/Employee: khong ai vo
        // hieu hoa 1 thu vua tao ra).
        if (mode == CrudMode.ADD) {
            statusCombo.setEnabled(false);
        }
    }

    @Override
    protected void fillForm(Category entity) {
        categoryNameField.setText(entity.getCategoryName());
        statusCombo.setSelectedIndex(entity.isActive() ? 0 : 1);
    }

    @Override
    protected String validateForm() {
        int excludeId = editingEntity != null ? editingEntity.getCategoryId() : -1;

        FormValidator validator = new FormValidator();

        validator.field(categoryNameField.getText())
                .required("Vui lòng nhập tên danh mục.")
                .maxLength(100, "Tên danh mục không được vượt quá 100 ký tự.")
                .rule(Rules.custom(v -> !categoryDAO.nameExistsExcluding(v.trim(), excludeId),
                        "Tên danh mục này đã tồn tại."));

        return validator.validate();
    }

    @Override
    protected Category collectFormData() {
        Category category = editingEntity != null ? editingEntity : new Category();
        category.setCategoryName(categoryNameField.getText().trim());
        category.setStatus(statusCombo.getSelectedIndex() == 1 ? "DISABLED" : "ACTIVE");
        return category;
    }

    @Override
    protected boolean persist(Category entity, CrudMode mode) {
        return mode == CrudMode.ADD ? categoryDAO.insertCategory(entity) : categoryDAO.updateCategory(entity);
    }
}