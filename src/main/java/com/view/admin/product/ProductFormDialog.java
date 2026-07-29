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
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.io.File;
import java.math.BigDecimal;
import java.util.List;

/**
 * Dialog Thêm/Sửa sản phẩm — layout ngang giống EmployeeFormDialog:
 * <ul>
 *   <li>(EDIT) thẻ Mã SP full-width phía trên</li>
 *   <li>3 cột: Ảnh | Thông tin sản phẩm | Giá &amp; tồn kho</li>
 * </ul>
 * Rộng hơn, thấp hơn form dọc cũ — ít phải cuộn.
 */
public class ProductFormDialog extends BaseFormDialog<Product> {

    private static final String UPLOAD_DIR = "uploads/products";
    private static final String[] STATUS_LABELS = {"Đang bán", "Ngừng bán"};
    private static final String[] UNIT_SUGGESTIONS = {"Cái", "Kg", "Hộp", "Chai", "Gói", "Lốc", "Thùng", "Lon"};
    private static final int PREVIEW_SIZE = 140;

    private final ProductDAO productDAO;
    private final List<Category> categories;

    private JTextField productCodeField; // chỉ đọc, chỉ hiện khi Sửa
    private JTextField productNameField;
    private JComboBox<Category> categoryCombo;
    private JTextField brandField;
    private JComboBox<String> unitCombo;
    private JTextField weightVolumeField;
    private JTextField importPriceField;
    private JTextField sellPriceField;
    private JTextField stockField;
    private JTextField minStockField;
    private JComboBox<String> statusCombo;
    private JTextArea descriptionArea;

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
    protected int getDialogWidth() {
        return 900;
    }

    @Override
    protected int getDialogHeight() {
        return mode == CrudMode.ADD ? 540 : 560;
    }

    /**
     * Layout ngang:
     *  - (EDIT) thẻ Mã SP full-width phía trên
     *  - 3 cột: Ảnh | Thông tin sản phẩm | Giá & tồn kho
     */
    @Override
    protected void buildFields(JPanel panel) {
        if (mode == CrudMode.EDIT) {
            productCodeField = newTextField();
            productCodeField.setEnabled(false);
            panel.add(buildCodeCard());
            panel.add(Box.createVerticalStrut(14));
        }

        JPanel columns = new JPanel();
        columns.setOpaque(false);
        columns.setLayout(new BoxLayout(columns, BoxLayout.X_AXIS));
        columns.setAlignmentX(Component.LEFT_ALIGNMENT);
        columns.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        columns.add(buildImageColumn());
        columns.add(Box.createHorizontalStrut(20));
        columns.add(buildInfoColumn());
        columns.add(Box.createHorizontalStrut(20));
        columns.add(buildPriceStockColumn());
        panel.add(columns);

        if (categories.isEmpty()) {
            panel.add(Box.createVerticalStrut(12));
            panel.add(infoBanner("Chưa có danh mục nào — vui lòng tạo danh mục trước khi thêm sản phẩm."));
        }
    }

    // ---------------------------------------------------------------
    // Cột 1 — Ảnh sản phẩm
    // ---------------------------------------------------------------

    private JPanel buildImageColumn() {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setAlignmentY(Component.TOP_ALIGNMENT);

        JLabel caption = iconFieldLabel(FontAwesomeSolid.IMAGE, "Hình ảnh", false);
        caption.setAlignmentX(Component.CENTER_ALIGNMENT);
        col.add(caption);
        col.add(Box.createVerticalStrut(8));

        imagePreviewLabel = new JLabel();
        imagePreviewLabel.setHorizontalAlignment(JLabel.CENTER);
        imagePreviewLabel.setPreferredSize(new Dimension(PREVIEW_SIZE, PREVIEW_SIZE));
        imagePreviewLabel.setMaximumSize(new Dimension(PREVIEW_SIZE, PREVIEW_SIZE));
        imagePreviewLabel.setMinimumSize(new Dimension(PREVIEW_SIZE, PREVIEW_SIZE));
        imagePreviewLabel.setBorder(new LineBorder(AppColor.BORDER, 1, true));
        imagePreviewLabel.setIcon(ImageUtil.loadIcon(null, PREVIEW_SIZE, PREVIEW_SIZE));
        imagePreviewLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        col.add(imagePreviewLabel);
        col.add(Box.createVerticalStrut(12));

        JButton chooseButton = new JButton("Chọn ảnh", FontIcon.of(FontAwesomeSolid.IMAGE, 13, AppColor.ACCENT));
        chooseButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        chooseButton.setFocusPainted(false);
        chooseButton.setBackground(AppColor.WHITE);
        chooseButton.setForeground(AppColor.ACCENT);
        chooseButton.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColor.ACCENT, 1, true),
                new EmptyBorder(8, 14, 8, 14)));
        chooseButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        chooseButton.addActionListener(e -> chooseImage());
        col.add(chooseButton);
        col.add(Box.createVerticalStrut(8));

        imageHintLabel = new JLabel("Tùy chọn · tối đa 5MB");
        imageHintLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        imageHintLabel.setForeground(AppColor.TEXT_MUTED);
        imageHintLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        col.add(imageHintLabel);

        return col;
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

    // ---------------------------------------------------------------
    // Cột 2 — Thông tin sản phẩm
    // ---------------------------------------------------------------

    private JPanel buildInfoColumn() {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setAlignmentY(Component.TOP_ALIGNMENT);
        col.setAlignmentX(Component.LEFT_ALIGNMENT);

        addSectionHeader(col, FontAwesomeSolid.BOX, "Thông tin sản phẩm");

        productNameField = newTextField();
        col.add(fieldGroupIcon(FontAwesomeSolid.TAG, "Tên sản phẩm", true, productNameField));
        col.add(Box.createVerticalStrut(12));

        categoryCombo = newComboBox(categories.toArray(new Category[0]));
        categoryCombo.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(value == null ? "" : value.getCategoryName());
            label.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            label.setOpaque(true);
            label.setBackground(isSelected ? AppColor.ACCENT : AppColor.WHITE);
            label.setForeground(isSelected ? Color.WHITE : AppColor.TEXT_PRIMARY);
            label.setBorder(new EmptyBorder(4, 8, 4, 8));
            return label;
        });
        col.add(fieldGroupIcon(FontAwesomeSolid.LAYER_GROUP, "Danh mục", true, categoryCombo));
        col.add(Box.createVerticalStrut(12));

        brandField = newTextField();
        col.add(fieldGroupIcon(FontAwesomeSolid.COPYRIGHT, "Thương hiệu", false, brandField));
        col.add(Box.createVerticalStrut(12));

        unitCombo = newUnitCombo();
        weightVolumeField = newTextField();
        JPanel unitRow = new JPanel(new GridLayout(1, 2, 12, 0));
        unitRow.setOpaque(false);
        unitRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        unitRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
        unitRow.add(fieldGroupIcon(FontAwesomeSolid.RULER, "Đơn vị tính", false, unitCombo));
        unitRow.add(fieldGroupIcon(FontAwesomeSolid.BALANCE_SCALE, "Khối lượng / Dung tích", false, weightVolumeField));
        col.add(unitRow);
        col.add(Box.createVerticalStrut(12));

        descriptionArea = new JTextArea(3, 20);
        descriptionArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        descriptionArea.setBorder(new EmptyBorder(8, 10, 8, 10));
        JScrollPane descScroll = new JScrollPane(descriptionArea);
        descScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        descScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        descScroll.setBorder(new LineBorder(AppColor.BORDER, 1, true));
        col.add(fieldGroupIcon(FontAwesomeSolid.ALIGN_LEFT, "Mô tả sản phẩm", false, descScroll));

        return col;
    }

    // ---------------------------------------------------------------
    // Cột 3 — Giá & tồn kho
    // ---------------------------------------------------------------

    private JPanel buildPriceStockColumn() {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setAlignmentY(Component.TOP_ALIGNMENT);
        col.setAlignmentX(Component.LEFT_ALIGNMENT);

        addSectionHeader(col, FontAwesomeSolid.DOLLAR_SIGN, "Giá & tồn kho");

        importPriceField = newTextField();
        col.add(fieldGroupIcon(FontAwesomeSolid.ARROW_DOWN, "Giá nhập", true, wrapWithSuffix(importPriceField, "VNĐ")));
        col.add(Box.createVerticalStrut(12));

        sellPriceField = newTextField();
        col.add(fieldGroupIcon(FontAwesomeSolid.ARROW_UP, "Giá bán", true, wrapWithSuffix(sellPriceField, "VNĐ")));
        col.add(Box.createVerticalStrut(4));
        JLabel priceHint = new JLabel("Giá bán ≥ giá nhập");
        priceHint.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        priceHint.setForeground(AppColor.TEXT_MUTED);
        priceHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        priceHint.setBorder(new EmptyBorder(0, 2, 0, 0));
        col.add(priceHint);
        col.add(Box.createVerticalStrut(12));

        stockField = newTextField();
        col.add(fieldGroupIcon(FontAwesomeSolid.WAREHOUSE, "Tồn kho", true, stockField));
        col.add(Box.createVerticalStrut(12));

        minStockField = newTextField();
        col.add(fieldGroupIcon(FontAwesomeSolid.EXCLAMATION_TRIANGLE, "Tồn kho tối thiểu", true, minStockField));
        col.add(Box.createVerticalStrut(12));

        statusCombo = newComboBox(STATUS_LABELS);
        col.add(fieldGroupIcon(FontAwesomeSolid.CHECK_CIRCLE, "Trạng thái", true, statusCombo));

        return col;
    }

    // ---------------------------------------------------------------
    // Helpers UI (cùng phong cách EmployeeFormDialog)
    // ---------------------------------------------------------------

    private JComboBox<String> newUnitCombo() {
        JComboBox<String> combo = new JComboBox<>(UNIT_SUGGESTIONS);
        combo.setEditable(true);
        combo.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        combo.setAlignmentX(Component.LEFT_ALIGNMENT);
        combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        combo.setBackground(AppColor.WHITE);
        return combo;
    }

    private <E> JComboBox<E> newComboBox(E[] items) {
        JComboBox<E> combo = new JComboBox<>(items);
        combo.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        combo.setAlignmentX(Component.LEFT_ALIGNMENT);
        combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        combo.setBackground(AppColor.WHITE);
        return combo;
    }

    private void addSectionHeader(JPanel panel, FontAwesomeSolid iconType, String text) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

        FontIcon icon = FontIcon.of(iconType, 13);
        icon.setIconColor(AppColor.ACCENT);
        JLabel iconLabel = new JLabel(icon);

        JLabel textLabel = new JLabel(text.toUpperCase());
        textLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        textLabel.setForeground(AppColor.ACCENT);
        textLabel.setBorder(new EmptyBorder(0, 8, 0, 10));

        JSeparator sep = new JSeparator(SwingConstants.HORIZONTAL);
        sep.setForeground(AppColor.BORDER);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setAlignmentY(Component.CENTER_ALIGNMENT);

        row.add(iconLabel);
        row.add(textLabel);
        row.add(sep);

        panel.add(row);
        panel.add(Box.createVerticalStrut(12));
    }

    private JLabel iconFieldLabel(FontAwesomeSolid iconType, String text, boolean required) {
        FontIcon icon = FontIcon.of(iconType, 12);
        icon.setIconColor(AppColor.TEXT_MUTED_ALT);

        String html = "<html>" + text
                + (required ? " <font color='" + toHex(AppColor.ERROR) + "'>*</font>" : "")
                + "</html>";
        JLabel label = new JLabel(html, icon, SwingConstants.LEFT);
        label.setIconTextGap(6);
        label.setFont(new Font("Segoe UI", Font.BOLD, 12));
        label.setForeground(AppColor.TEXT_MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(0, 2, 4, 0));
        return label;
    }

    private JPanel fieldGroupIcon(FontAwesomeSolid iconType, String label, boolean required, JComponent field) {
        JPanel group = new JPanel();
        group.setLayout(new BoxLayout(group, BoxLayout.Y_AXIS));
        group.setOpaque(false);
        group.setAlignmentX(Component.LEFT_ALIGNMENT);
        group.add(iconFieldLabel(iconType, label, required));
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        group.add(field);
        return group;
    }

    private JPanel wrapWithSuffix(JTextField field, String suffix) {
        field.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 0));

        JLabel suffixLabel = new JLabel(suffix);
        suffixLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        suffixLabel.setForeground(AppColor.TEXT_MUTED);
        suffixLabel.setBorder(new EmptyBorder(0, 6, 0, 10));

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(true);
        wrapper.setBackground(AppColor.WHITE);
        wrapper.setBorder(new LineBorder(AppColor.BORDER, 1, true));
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        wrapper.add(field, BorderLayout.CENTER);
        wrapper.add(suffixLabel, BorderLayout.EAST);
        return wrapper;
    }

    /** Thẻ chỉ đọc hiển thị Mã SP khi Sửa. */
    private JPanel buildCodeCard() {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(AppColor.BG_LIGHTER);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(8, 16, 8, 16)));

        productCodeField.setBorder(BorderFactory.createEmptyBorder());
        productCodeField.setOpaque(false);
        productCodeField.setFont(new Font("Segoe UI", Font.BOLD, 14));
        productCodeField.setDisabledTextColor(AppColor.TEXT_PRIMARY);

        FontIcon icon = FontIcon.of(FontAwesomeSolid.HASHTAG, 11);
        icon.setIconColor(AppColor.TEXT_MUTED);
        JLabel label = new JLabel("Mã sản phẩm", icon, SwingConstants.LEFT);
        label.setIconTextGap(5);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        label.setForeground(AppColor.TEXT_MUTED);

        JPanel inner = new JPanel();
        inner.setOpaque(false);
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.add(label);
        inner.add(Box.createVerticalStrut(2));
        inner.add(productCodeField);
        card.add(inner, BorderLayout.CENTER);
        return card;
    }

    private JPanel infoBanner(String text) {
        JPanel banner = new JPanel(new BorderLayout(10, 0));
        banner.setBackground(AppColor.INFO_BG);
        banner.setAlignmentX(Component.LEFT_ALIGNMENT);
        banner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        banner.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColor.INFO, 1, true),
                new EmptyBorder(10, 12, 10, 12)));

        FontIcon icon = FontIcon.of(FontAwesomeSolid.INFO_CIRCLE, 15);
        icon.setIconColor(AppColor.INFO);
        JLabel iconLabel = new JLabel(icon);
        iconLabel.setVerticalAlignment(SwingConstants.TOP);

        JLabel textLabel = new JLabel("<html>" + text + "</html>");
        textLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        textLabel.setForeground(AppColor.TEXT_PRIMARY);

        banner.add(iconLabel, BorderLayout.WEST);
        banner.add(textLabel, BorderLayout.CENTER);
        return banner;
    }

    private static String toHex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    // ---------------------------------------------------------------
    // Fill / Validate / Collect / Persist
    // ---------------------------------------------------------------

    @Override
    protected void fillForm(Product entity) {
        if (productCodeField != null) {
            productCodeField.setText(entity.getProductCode());
        }
        productNameField.setText(entity.getProductName());
        selectCategoryById(entity.getCategoryId());
        brandField.setText(entity.getBrand() != null ? entity.getBrand() : "");
        unitCombo.setSelectedItem(entity.getUnit() != null ? entity.getUnit() : "");
        weightVolumeField.setText(entity.getWeightVolume() != null ? entity.getWeightVolume() : "");
        importPriceField.setText(entity.getImportPrice() != null ? entity.getImportPrice().toPlainString() : "");
        sellPriceField.setText(entity.getSellPrice() != null ? entity.getSellPrice().toPlainString() : "");
        stockField.setText(String.valueOf(entity.getStock()));
        minStockField.setText(String.valueOf(entity.getMinStock()));
        statusCombo.setSelectedIndex(entity.isActive() ? 0 : 1);
        descriptionArea.setText(entity.getDescription() != null ? entity.getDescription() : "");

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

        validator.field(brandField.getText())
                .maxLength(100, "Thương hiệu tối đa 100 ký tự.");

        validator.field(String.valueOf(unitCombo.getEditor().getItem()))
                .maxLength(30, "Đơn vị tính tối đa 30 ký tự.");

        validator.field(weightVolumeField.getText())
                .maxLength(50, "Khối lượng/dung tích tối đa 50 ký tự.");

        validator.field(descriptionArea.getText())
                .maxLength(1000, "Mô tả tối đa 1000 ký tự.");

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

        product.setBrand(blankToNull(brandField.getText()));
        product.setUnit(blankToNull(String.valueOf(unitCombo.getEditor().getItem())));
        product.setWeightVolume(blankToNull(weightVolumeField.getText()));
        product.setDescription(blankToNull(descriptionArea.getText()));

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

    private static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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