package com.view.admin.inventory;

import com.components.DatePickerField;
import com.components.crud.BaseFormDialog;
import com.components.crud.CrudMode;
import com.dao.InventoryBatchDAO;
import com.dao.ProductDAO;
import com.dao.SupplierDAO;
import com.model.InventoryBatch;
import com.model.Product;
import com.model.Supplier;
import com.service.AuthService;
import com.theme.AppColor;
import com.validation.FormValidator;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Form nhap / xem lo hang — layout ngang 2 cot.
 *
 * Hang 1: San pham | Nha cung cap
 * Hang 2: So lo NCC | (VIEW: Ma lo / SL con lai)
 * Hang 3: NSX | HSD
 * Hang 4: SL nhap | Gia nhap
 */
public class InventoryBatchFormDialog extends BaseFormDialog<InventoryBatch> {

    private final InventoryBatchDAO batchDAO;
    private final List<Product> products;
    private final List<Supplier> suppliers;

    private JTextField batchCodeField;   // chi hien o VIEW, chi doc
    private JComboBox<Product> productCombo;
    private JComboBox<Supplier> supplierCombo;
    private JTextField lotNumberField;
    private DatePickerField manufactureDatePicker;
    private DatePickerField expiryDatePicker;
    private JTextField quantityField;
    private JTextField importPriceField;
    private JTextField remainingQtyField; // chi hien o VIEW, chi doc

    public InventoryBatchFormDialog(Frame owner, CrudMode mode, InventoryBatch editingEntity, InventoryBatchDAO batchDAO) {
        super(owner, "lô hàng", mode, editingEntity);
        this.batchDAO = batchDAO;
        this.products = new ProductDAO().findAllActive();
        this.suppliers = new SupplierDAO().findAllOrderByName();
        init();
    }

    @Override
    protected int getDialogWidth() { return 720; }

    @Override
    protected int getDialogHeight() {
        return mode == CrudMode.VIEW ? 540 : 500;
    }

    @Override
    protected void buildFields(JPanel panel) {
        // ---------- Hang 1: San pham | Nha cung cap ----------
        productCombo = newCombo(products.toArray(new Product[0]));
        productCombo.setRenderer((list, value, index, isSelected, cellHasFocus) ->
                simpleLabel(value == null ? "" : value.getProductName(), isSelected));

        supplierCombo = newCombo(suppliers.toArray(new Supplier[0]));
        supplierCombo.setRenderer((list, value, index, isSelected, cellHasFocus) ->
                simpleLabel(value == null ? "" : value.getSupplierName(), isSelected));

        fieldRow(panel,
                fieldGroup("Sản phẩm", true, productCombo),
                fieldGroup("Nhà cung cấp", true, supplierCombo));

        // ---------- Hang 2: So lo NCC | (VIEW: Ma lo / SL con lai) ----------
        lotNumberField = newTextField();

        if (mode == CrudMode.VIEW) {
            batchCodeField = newTextField();
            batchCodeField.setEnabled(false);
            remainingQtyField = newTextField();
            remainingQtyField.setEnabled(false);

            fieldRow(panel,
                    fieldGroup("Mã lô", false, batchCodeField),
                    fieldGroup("Số lượng còn lại", false, remainingQtyField));

            fieldRow(panel,
                    fieldGroup("Số lô (theo NCC)", false, lotNumberField),
                    spacerGroup());
        } else {
            fieldRow(panel,
                    fieldGroup("Số lô (theo NCC)", false, lotNumberField),
                    spacerGroup());
        }

        // ---------- Hang 3: NSX | HSD ----------
        manufactureDatePicker = new DatePickerField(LocalDate.now(), true);
        expiryDatePicker = new DatePickerField(null, true);
        fieldRow(panel,
                fieldGroup("Ngày sản xuất", false, manufactureDatePicker),
                fieldGroup("Hạn sử dụng", false, expiryDatePicker));

        // ---------- Hang 4: SL nhap | Gia nhap ----------
        quantityField = newTextField();
        importPriceField = newTextField();
        fieldRow(panel,
                fieldGroup("Số lượng nhập", true, quantityField),
                fieldGroup("Giá nhập / đơn vị", true, importPriceField));

        // ---------- Hint / canh bao ----------
        if (mode != CrudMode.VIEW) {
            JLabel hint = hintLabel("Lô hàng sẽ được ghi nhận qua 1 phiếu nhập kho mới và cộng thẳng vào tồn kho sản phẩm.");
            panel.add(hint);
        }

        productCombo.setEnabled(mode != CrudMode.VIEW && !products.isEmpty());
        supplierCombo.setEnabled(mode != CrudMode.VIEW && !suppliers.isEmpty());
        if (mode == CrudMode.ADD && (products.isEmpty() || suppliers.isEmpty())) {
            panel.add(Box.createVerticalStrut(10));
            panel.add(infoBanner("Cần có ít nhất 1 sản phẩm và 1 nhà cung cấp trước khi nhập lô hàng."));
        }
    }

    @Override
    protected void fillForm(InventoryBatch entity) {
        if (batchCodeField != null) batchCodeField.setText(entity.getBatchCode());

        selectProductById(productCombo, entity.getProductId());
        selectSupplierById(supplierCombo, entity.getSupplierId());

        lotNumberField.setText(entity.getLotNumber());
        manufactureDatePicker.setValue(entity.getManufactureDate());
        expiryDatePicker.setValue(entity.getExpiryDate());
        quantityField.setText(String.valueOf(entity.getQuantity()));
        importPriceField.setText(entity.getImportPrice() != null
                ? entity.getImportPrice().toBigInteger().toString() : "");

        if (remainingQtyField != null) {
            remainingQtyField.setText(String.valueOf(entity.getRemainingQty()));
        }
    }

    @Override
    protected String validateForm() {
        if (mode == CrudMode.VIEW) return null;

        if (productCombo.getSelectedItem() == null) {
            return "Vui lòng chọn sản phẩm (hoặc tạo sản phẩm trước).";
        }
        if (supplierCombo.getSelectedItem() == null) {
            return "Vui lòng chọn nhà cung cấp (hoặc tạo nhà cung cấp trước).";
        }

        FormValidator validator = new FormValidator();

        validator.field(lotNumberField.getText())
                .maxLength(50, "Số lô tối đa 50 ký tự.");

        validator.field(quantityField.getText())
                .required("Vui lòng nhập số lượng.")
                .rule(v -> isPositiveInt(v) ? null : "Số lượng phải là số nguyên dương.");

        validator.field(importPriceField.getText())
                .required("Vui lòng nhập giá nhập.")
                .rule(v -> isNonNegativeAmount(v) ? null : "Giá nhập phải là số nguyên không âm.");

        String error = validator.validate();
        if (error != null) return error;

        LocalDate mfg = manufactureDatePicker.getValue();
        LocalDate exp = expiryDatePicker.getValue();
        if (mfg != null && exp != null && !exp.isAfter(mfg)) {
            return "Hạn sử dụng phải sau ngày sản xuất.";
        }

        return null;
    }

    @Override
    protected InventoryBatch collectFormData() {
        InventoryBatch batch = new InventoryBatch();
        Product product = (Product) productCombo.getSelectedItem();
        Supplier supplier = (Supplier) supplierCombo.getSelectedItem();

        batch.setProductId(product.getProductId());
        batch.setProductName(product.getProductName());
        batch.setSupplierId(supplier.getSupplierId());
        batch.setSupplierName(supplier.getSupplierName());
        batch.setLotNumber(blankToNull(lotNumberField.getText()));
        batch.setManufactureDate(manufactureDatePicker.getValue());
        batch.setExpiryDate(expiryDatePicker.getValue());
        batch.setQuantity(Integer.parseInt(quantityField.getText().trim()));
        batch.setImportPrice(parseAmount(importPriceField.getText()));
        return batch;
    }

    @Override
    protected boolean persist(InventoryBatch entity, CrudMode mode) {
        if (mode != CrudMode.ADD) return true; // VIEW khong luu gi ca
        int userId = AuthService.getInstance().getCurrentUser().getUserId();
        return batchDAO.receiveBatch(entity, userId);
    }

    // ---------------------------------------------------------------
    // Helper UI
    // ---------------------------------------------------------------

    /** Tao JComboBox da style, chua add vao panel — dung voi fieldGroup / fieldRow. */
    private <E> JComboBox<E> newCombo(E[] items) {
        JComboBox<E> combo = new JComboBox<>(items);
        combo.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        combo.setAlignmentX(Component.LEFT_ALIGNMENT);
        combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        combo.setBackground(AppColor.WHITE);
        return combo;
    }

    /** O trong de giu can doi 2 cot khi chi co 1 field tren hang. */
    private JPanel spacerGroup() {
        JPanel spacer = new JPanel();
        spacer.setOpaque(false);
        return spacer;
    }

    private JLabel simpleLabel(String text, boolean isSelected) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        label.setOpaque(true);
        label.setBackground(isSelected ? AppColor.ACCENT : AppColor.WHITE);
        label.setForeground(isSelected ? java.awt.Color.WHITE : AppColor.TEXT_PRIMARY);
        label.setBorder(new EmptyBorder(4, 8, 4, 8));
        return label;
    }

    private JPanel infoBanner(String text) {
        JPanel banner = new JPanel();
        banner.setLayout(new BoxLayout(banner, BoxLayout.Y_AXIS));
        banner.setAlignmentX(Component.LEFT_ALIGNMENT);
        banner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        banner.setBackground(AppColor.WARNING_BG);
        banner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColor.WARNING, 1, true),
                new EmptyBorder(8, 10, 8, 10)));
        JLabel label = new JLabel("<html>" + text + "</html>");
        label.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        label.setForeground(AppColor.WARNING);
        banner.add(label);
        return banner;
    }

    private static void selectProductById(JComboBox<Product> combo, int productId) {
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (combo.getItemAt(i).getProductId() == productId) {
                combo.setSelectedIndex(i);
                return;
            }
        }
    }

    private static void selectSupplierById(JComboBox<Supplier> combo, int supplierId) {
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (combo.getItemAt(i).getSupplierId() == supplierId) {
                combo.setSelectedIndex(i);
                return;
            }
        }
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isPositiveInt(String value) {
        if (value == null) return false;
        String trimmed = value.trim();
        if (!trimmed.matches("\\d+")) return false;
        try {
            return Integer.parseInt(trimmed) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isNonNegativeAmount(String value) {
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
}