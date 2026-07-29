package com.view.admin.product;

import com.components.crud.BaseFormDialog;
import com.components.crud.CrudMode;
import com.dao.CategoryDAO;
import com.dao.ProductDAO;
import com.model.Category;
import com.model.Product;
import com.theme.AppColor;
import com.utils.FileUtil;
import com.utils.ImageUtil;
import com.validation.FormValidator;
import com.validation.Rules;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.io.File;
import java.math.BigDecimal;
import java.util.List;

public class ProductFormDialog extends BaseFormDialog<Product> {

    private static final String UPLOAD_DIR = "uploads/products";
    private static final String[] STATUS_LABELS = {"Đang bán", "Ngừng bán"};
    private static final int PREVIEW_SIZE = 110;

    private final ProductDAO productDAO;
    private final List<Category> categories;

    private JTextField productNameField;
    private JComboBox<Category> categoryCombo;
    private JTextField importPriceField;
    private JTextField sellPriceField;
    private JTextField stockField;
    private JTextField minStockField;
    private JComboBox<String> statusCombo;

    private JLabel imagePreviewLabel;
    private JLabel imageHintLabel;
    private File pendingImageFile;
    private String currentImageUrl;

    public ProductFormDialog(Frame owner, CrudMode mode, Product editingEntity, ProductDAO productDAO) {
        super(owner, "sản phẩm", mode, editingEntity);
        this.productDAO = productDAO;
        this.categories = new CategoryDAO().findAll();
        this.currentImageUrl = editingEntity != null ? editingEntity.getImageUrl() : null;
        init();
    }

    @Override
    protected int getDialogWidth() { return 560; }

    @Override
    protected int getDialogHeight() { return 640; }

    @Override
    protected void buildFields(JPanel panel) {
        panel.add(fieldLabel("Hình ảnh sản phẩm"));
        panel.add(buildImagePicker());

        productNameField = addTextField(panel, "Tên sản phẩm", true);

        categoryCombo = addComboBox(panel, "Danh mục", categories.toArray(new Category[0]));
        categoryCombo.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(value == null ? "" : value.getCategoryName());
            label.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            label.setOpaque(true);
            label.setBackground(isSelected ? AppColor.ACCENT : AppColor.WHITE);
            label.setForeground(isSelected ? java.awt.Color.WHITE : AppColor.TEXT_PRIMARY);
            label.setBorder(new EmptyBorder(4, 8, 4, 8));
            return label;
        });
        if (categories.isEmpty()) {
            panel.add(hintLabel("Chưa có danh mục nào - vui lòng tạo danh mục trước khi thêm sản phẩm."));
        }

        importPriceField = newTextField();
        sellPriceField = newTextField();
        fieldRow(panel,
                fieldGroup("Giá nhập (VNĐ)", true, importPriceField),
                fieldGroup("Giá bán (VNĐ)", true, sellPriceField));
        panel.add(hintLabel("Giá bán phải lớn hơn hoặc bằng giá nhập."));
        panel.add(Box.createVerticalStrut(10));

        stockField = newTextField();
        minStockField = newTextField();
        fieldRow(panel,
                fieldGroup("Tồn kho", true, stockField),
                fieldGroup("Tồn kho tối thiểu", true, minStockField));

        statusCombo = addComboBox(panel, "Trạng thái", STATUS_LABELS);
    }

    private JPanel buildImagePicker() {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, PREVIEW_SIZE));
        row.setBorder(new EmptyBorder(0, 0, 14, 0));

        imagePreviewLabel = new JLabel();
        imagePreviewLabel.setHorizontalAlignment(JLabel.CENTER);
        imagePreviewLabel.setPreferredSize(new Dimension(PREVIEW_SIZE, PREVIEW_SIZE));
        imagePreviewLabel.setMaximumSize(new Dimension(PREVIEW_SIZE, PREVIEW_SIZE));
        imagePreviewLabel.setMinimumSize(new Dimension(PREVIEW_SIZE, PREVIEW_SIZE));
        imagePreviewLabel.setBorder(new LineBorder(AppColor.BORDER, 1, true));
        imagePreviewLabel.setIcon(ImageUtil.loadIcon(null, PREVIEW_SIZE, PREVIEW_SIZE));
        row.add(imagePreviewLabel);
        row.add(Box.createHorizontalStrut(16));

        JPanel side = new JPanel();
        side.setOpaque(false);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));

        JButton chooseButton = new JButton("Chọn ảnh", FontIcon.of(FontAwesomeSolid.IMAGE, 13, AppColor.ACCENT));
        chooseButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        chooseButton.setFocusPainted(false);
        chooseButton.setBackground(AppColor.WHITE);
        chooseButton.setForeground(AppColor.ACCENT);
        chooseButton.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColor.ACCENT, 1, true),
                new EmptyBorder(6, 14, 6, 14)));
        chooseButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        chooseButton.addActionListener(e -> chooseImage());
        side.add(chooseButton);

        side.add(Box.createVerticalStrut(6));

        imageHintLabel = hintLabel("Chưa chọn ảnh (tùy chọn)");
        imageHintLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(imageHintLabel);

        row.add(side);
        return row;
    }

    private void chooseImage() {
        File selected = FileUtil.chooseImageFile(this);
        if (selected == null) return;

        if (!FileUtil.isWithinSizeLimit(selected, 5)) {
            showMessage("Ảnh vượt quá 5MB, vui lòng chọn ảnh khác.");
            return;
        }
        if (!ImageUtil.isSupportedImage(selected)) {
            showMessage("Định dạng ảnh không được hỗ trợ.");
            return;
        }

        pendingImageFile = selected;
        imagePreviewLabel.setIcon(ImageUtil.loadIcon(selected.getPath(), PREVIEW_SIZE, PREVIEW_SIZE));
        imageHintLabel.setText(selected.getName());
        showMessage(null);
    }

    @Override
    protected void fillForm(Product entity) {
        productNameField.setText(entity.getProductName());
        selectCategoryById(entity.getCategoryId());
        importPriceField.setText(entity.getImportPrice() != null ? entity.getImportPrice().toPlainString() : "");
        sellPriceField.setText(entity.getSellPrice() != null ? entity.getSellPrice().toPlainString() : "");
        stockField.setText(String.valueOf(entity.getStock()));
        minStockField.setText(String.valueOf(entity.getMinStock()));
        statusCombo.setSelectedIndex(entity.isActive() ? 0 : 1);

        if (entity.getImageUrl() != null && !entity.getImageUrl().isBlank()) {
            imagePreviewLabel.setIcon(ImageUtil.loadIcon(entity.getImageUrl(), PREVIEW_SIZE, PREVIEW_SIZE));
            imageHintLabel.setText("Ảnh hiện tại");
        }
    }

    private void selectCategoryById(int categoryId) {
        for (int i = 0; i < categoryCombo.getItemCount(); i++) {
            Category c = categoryCombo.getItemAt(i);
            if (c.getCategoryId() == categoryId) {
                categoryCombo.setSelectedIndex(i);
                return;
            }
        }
    }

    @Override
    protected String validateForm() {
        if (categoryCombo.getSelectedItem() == null) {
            return "Vui lòng chọn danh mục (hoặc tạo danh mục trước).";
        }

        FormValidator validator = new FormValidator();

        validator.field(productNameField.getText())
                .required("Vui lòng nhập tên sản phẩm.")
                .maxLength(150, "Tên sản phẩm tối đa 150 ký tự.");

        validator.field(importPriceField.getText())
                .required("Vui lòng nhập giá nhập.")
                .rule(v -> isValidNonNegativeAmount(v) ? null : "Giá nhập phải là số nguyên không âm.");

        validator.field(sellPriceField.getText())
                .required("Vui lòng nhập giá bán.")
                .rule(v -> isValidNonNegativeAmount(v) ? null : "Giá bán phải là số nguyên không âm.");

        validator.field(stockField.getText())
                .required("Vui lòng nhập tồn kho.")
                .rule(Rules.custom(ProductFormDialog::isValidNonNegativeInt, "Tồn kho phải là số nguyên không âm."));

        validator.field(minStockField.getText())
                .required("Vui lòng nhập tồn kho tối thiểu.")
                .rule(Rules.custom(ProductFormDialog::isValidNonNegativeInt, "Tồn kho tối thiểu phải là số nguyên không âm."));

        String error = validator.validate();
        if (error != null) return error;

        BigDecimal importPrice = parseAmount(importPriceField.getText());
        BigDecimal sellPrice = parseAmount(sellPriceField.getText());
        if (importPrice != null && sellPrice != null && sellPrice.compareTo(importPrice) < 0) {
            return "Giá bán phải lớn hơn hoặc bằng giá nhập.";
        }

        return null;
    }

    @Override
    protected Product collectFormData() {
        Product product = editingEntity != null ? editingEntity : new Product();
        product.setProductName(productNameField.getText().trim());

        Category selectedCategory = (Category) categoryCombo.getSelectedItem();
        product.setCategoryId(selectedCategory.getCategoryId());
        product.setCategoryName(selectedCategory.getCategoryName());

        product.setImportPrice(parseAmount(importPriceField.getText()));
        product.setSellPrice(parseAmount(sellPriceField.getText()));
        product.setStock(Integer.parseInt(stockField.getText().trim()));
        product.setMinStock(Integer.parseInt(minStockField.getText().trim()));
        product.setStatus(statusCombo.getSelectedIndex() == 1 ? "DISABLED" : "ACTIVE");

        if (pendingImageFile != null) {
            File saved = FileUtil.copyToDirectory(pendingImageFile, UPLOAD_DIR);
            product.setImageUrl(saved != null ? saved.getPath() : currentImageUrl);
        } else {
            product.setImageUrl(currentImageUrl);
        }

        return product;
    }

    @Override
    protected boolean persist(Product entity, CrudMode mode) {
        return mode == CrudMode.ADD ? productDAO.insert(entity) : productDAO.update(entity);
    }

    private static boolean isValidNonNegativeAmount(String value) {
        return parseAmount(value) != null;
    }

    private static BigDecimal parseAmount(String value) {
        if (value == null) return null;
        String cleaned = value.trim().replace(".", "").replace(",", "");
        if (cleaned.isEmpty() || !cleaned.matches("\\d+")) return null;
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isValidNonNegativeInt(String value) {
        if (value == null) return false;
        String trimmed = value.trim();
        if (!trimmed.matches("\\d+")) return false;
        try {
            return Integer.parseInt(trimmed) >= 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}