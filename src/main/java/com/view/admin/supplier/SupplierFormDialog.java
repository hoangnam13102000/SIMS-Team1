package com.view.admin.supplier;

import com.components.crud.BaseFormDialog;
import com.components.crud.CrudMode;
import com.dao.SupplierDAO;
import com.model.Supplier;
import com.theme.AppColor;
import com.theme.AppFont;
import com.validation.FormValidator;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;
import javax.swing.JComponent;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
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
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Random;

/**
 * Dialog Thêm mới / Cập nhật nhà cung cấp — UX/UI đồng bộ với
 * {@link com.view.admin.category.CategoryFormDialog}:
 * <ul>
 *   <li>Banner ngữ cảnh phía trên</li>
 *   <li>Meta row (mã NCC + số SP liên kết + công nợ) khi Sửa</li>
 *   <li>Field bọc icon, hint, đếm ký tự realtime</li>
 *   <li>Nút điền dữ liệu Demo khi Thêm mới</li>
 * </ul>
 */
public class SupplierFormDialog extends BaseFormDialog<Supplier> {

    private static final Random RANDOM = new Random();
    private static final int MAX_NAME_LENGTH = 150;
    private static final int MAX_PHONE_LENGTH = 15;
    private static final int MAX_EMAIL_LENGTH = 100;
    private static final int MAX_ADDRESS_LENGTH = 255;
    private static final int MAX_ITEMS_LENGTH = 500;

    /** Dữ liệu mẫu để điền nhanh khi Demo — không liên quan logic nghiệp vụ. */
    private static final DemoTemplate[] DEMO_TEMPLATES = {
            new DemoTemplate("Công ty TNHH Thực phẩm & Đồ uống Miền Nam", "thucphammiennam",
                    "123 Nguyễn Văn Linh, Quận 7, TP.HCM", "Nước ngọt, nước suối, nước tăng lực"),
            new DemoTemplate("Công ty CP Sữa & Thực phẩm Dinh dưỡng Việt", "suadinhduongviet",
                    "45 Lê Văn Việt, TP. Thủ Đức, TP.HCM", "Sữa tươi, sữa chua, sữa bột"),
            new DemoTemplate("Công ty TNHH Thực phẩm Ăn liền Hảo Vị", "haovi",
                    "78 Trường Chinh, Quận Tân Bình, TP.HCM", "Mì gói, phở ăn liền, cháo ăn liền"),
            new DemoTemplate("Công ty TNHH Bánh kẹo Phương Nam", "banhkeophuongnam",
                    "12 Cách Mạng Tháng Tám, Quận 3, TP.HCM", "Bánh quy, bánh snack, kẹo các loại"),
            new DemoTemplate("Công ty CP Hóa mỹ phẩm Sài Gòn", "hoamyphamsg",
                    "56 Điện Biên Phủ, Quận Bình Thạnh, TP.HCM", "Nước rửa chén, nước giặt, nước lau sàn"),
            new DemoTemplate("Công ty TNHH Chăm sóc cá nhân Á Châu", "achaucare",
                    "89 Phan Xích Long, Quận Phú Nhuận, TP.HCM", "Dầu gội, sữa tắm, kem đánh răng"),
            new DemoTemplate("Công ty TNHH Thương mại Kẹo & Snack Việt", "keosnackviet",
                    "34 Hoàng Văn Thụ, Quận Tân Bình, TP.HCM", "Kẹo mút, kẹo dẻo, socola"),
            new DemoTemplate("Công ty CP Cà phê & Đồ uống Cao Nguyên", "caphecaonguyen",
                    "67 Nguyễn Trãi, Quận 5, TP.HCM", "Cà phê hòa tan, cà phê rang xay, trà túi lọc"),
    };

    /** Các đầu số di động VN hợp lệ (khớp regex phoneVn trong FormValidator). */
    private static final String[] PHONE_PREFIXES = {
            "032", "033", "034", "035", "036", "037", "038", "039",
            "070", "076", "077", "078", "079",
            "081", "082", "083", "084", "085", "088",
            "090", "091", "092", "093", "094", "096", "097", "098", "099"
    };

    private static final class DemoTemplate {
        final String name;
        final String emailSlug;
        final String address;
        final String suppliedItems;

        DemoTemplate(String name, String emailSlug, String address, String suppliedItems) {
            this.name = name;
            this.emailSlug = emailSlug;
            this.address = address;
            this.suppliedItems = suppliedItems;
        }
    }

    private final SupplierDAO supplierDAO;

    private JTextField nameField;
    private JTextField phoneField;
    private JTextField emailField;
    private JTextField addressField;
    private JTextArea suppliedItemsArea;

    private JLabel nameCounterLabel;
    private JLabel itemsCounterLabel;
    private JLabel productCountLabel;
    private JLabel debtLabel;

    public SupplierFormDialog(Frame owner, CrudMode mode, Supplier editingEntity, SupplierDAO supplierDAO) {
        super(owner, "nhà cung cấp", mode, editingEntity);
        this.supplierDAO = supplierDAO;
        init();
    }

    @Override
    protected int getDialogWidth() {
        return 520;
    }

    @Override
    protected int getDialogHeight() {
        return mode == CrudMode.EDIT ? 680 : 640;
    }

    @Override
    protected void buildFields(JPanel panel) {
        panel.add(buildInfoBanner());
        panel.add(Box.createVerticalStrut(14));

        if (mode == CrudMode.EDIT && editingEntity != null) {
            panel.add(buildMetaRow());
            panel.add(Box.createVerticalStrut(14));
        }

        if (mode == CrudMode.ADD) {
            panel.add(buildDemoBar());
            panel.add(Box.createVerticalStrut(10));
        }

        // ---- Tên nhà cung cấp -------------------------------------------------
        panel.add(fieldLabel("Tên nhà cung cấp", true));
        JPanel nameWrapper = createIconTextFieldWrapper(FontAwesomeSolid.BUILDING);
        nameField = (JTextField) nameWrapper.getClientProperty("field");
        installMaxLengthFilter(nameField, MAX_NAME_LENGTH);
        panel.add(nameWrapper);
        panel.add(Box.createVerticalStrut(4));

        JPanel nameHintRow = new JPanel(new BorderLayout(4, 0));
        nameHintRow.setOpaque(false);
        nameHintRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        nameHintRow.add(hintLabel("Tên công ty / cửa hàng cung cấp hàng hóa."), BorderLayout.CENTER);
        nameCounterLabel = counterLabel(0, MAX_NAME_LENGTH);
        nameHintRow.add(nameCounterLabel, BorderLayout.EAST);
        panel.add(nameHintRow);
        panel.add(Box.createVerticalStrut(14));

        nameField.getDocument().addDocumentListener(simpleListener(() ->
                updateCounter(nameCounterLabel, nameField.getText(), MAX_NAME_LENGTH)));

        // ---- SĐT + Email (1 hàng) ---------------------------------------------
        phoneField = createIconTextField(FontAwesomeSolid.PHONE);
        emailField = createIconTextField(FontAwesomeSolid.ENVELOPE);
        installMaxLengthFilter(phoneField, MAX_PHONE_LENGTH);
        installMaxLengthFilter(emailField, MAX_EMAIL_LENGTH);

        fieldRow(panel,
                fieldGroup("Số điện thoại", false, wrapFieldWithHint(
                        phoneField, "VD: 09xxxxxxxx (tùy chọn)")),
                fieldGroup("Email", false, wrapFieldWithHint(
                        emailField, "VD: contact@company.com (tùy chọn)")));

        // ---- Địa chỉ ----------------------------------------------------------
        panel.add(fieldLabel("Địa chỉ"));
        JPanel addressWrapper = createIconTextFieldWrapper(FontAwesomeSolid.MAP_MARKER_ALT);
        addressField = (JTextField) addressWrapper.getClientProperty("field");
        installMaxLengthFilter(addressField, MAX_ADDRESS_LENGTH);
        panel.add(addressWrapper);
        panel.add(Box.createVerticalStrut(4));
        panel.add(hintLabel("Địa chỉ kho / văn phòng giao dịch (tùy chọn)."));
        panel.add(Box.createVerticalStrut(14));

        // ---- Mặt hàng cung cấp ------------------------------------------------
        panel.add(fieldLabel("Mặt hàng cung cấp"));
        suppliedItemsArea = createStyledTextArea(4);
        installMaxLengthFilter(suppliedItemsArea, MAX_ITEMS_LENGTH);

        JScrollPane itemsScroll = new JScrollPane(suppliedItemsArea);
        itemsScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        itemsScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
        itemsScroll.setBorder(new LineBorder(AppColor.FIELD_BORDER, 1, true));
        itemsScroll.setOpaque(false);
        itemsScroll.getViewport().setBackground(AppColor.WHITE);
        panel.add(itemsScroll);
        panel.add(Box.createVerticalStrut(4));

        JPanel itemsHintRow = new JPanel(new BorderLayout(4, 0));
        itemsHintRow.setOpaque(false);
        itemsHintRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        itemsHintRow.add(hintLabel("Liệt kê nhóm hàng chính NCC này cung cấp (tùy chọn)."), BorderLayout.CENTER);
        itemsCounterLabel = counterLabel(0, MAX_ITEMS_LENGTH);
        itemsHintRow.add(itemsCounterLabel, BorderLayout.EAST);
        panel.add(itemsHintRow);

        suppliedItemsArea.getDocument().addDocumentListener(simpleListener(() ->
                updateCounter(itemsCounterLabel, suppliedItemsArea.getText(), MAX_ITEMS_LENGTH)));
    }

    // ---------------------------------------------------------------
    // Banner ngữ cảnh — đồng bộ CategoryFormDialog / RoleFormDialog
    // ---------------------------------------------------------------

    private JPanel buildInfoBanner() {
        JPanel banner = new JPanel(new BorderLayout(12, 0));
        banner.setOpaque(true);
        banner.setBackground(AppColor.ACCENT_BG_SOFT);
        banner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColor.ACCENT, 1, true),
                new EmptyBorder(12, 14, 12, 14)));
        banner.setAlignmentX(Component.LEFT_ALIGNMENT);
        banner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 76));

        JPanel iconWrap = new JPanel();
        iconWrap.setOpaque(false);
        iconWrap.setPreferredSize(new Dimension(32, 32));
        FontIcon icon = FontIcon.of(
                mode == CrudMode.EDIT ? FontAwesomeSolid.TRUCK : FontAwesomeSolid.PLUS, 16);
        icon.setIconColor(AppColor.ACCENT);
        iconWrap.add(new JLabel(icon));

        String html = mode == CrudMode.ADD
                ? "<html><b style='color:" + hex(AppColor.TEXT_PRIMARY) + "'>Thêm nhà cung cấp mới</b><br/>"
                + "<span style='color:" + hex(AppColor.TEXT_SECONDARY) + "'>"
                + "Nhà cung cấp sẽ xuất hiện trong danh sách nhập hàng ngay sau khi lưu.</span></html>"
                : "<html><b style='color:" + hex(AppColor.TEXT_PRIMARY) + "'>Cập nhật nhà cung cấp</b><br/>"
                + "<span style='color:" + hex(AppColor.TEXT_SECONDARY) + "'>"
                + "Chỉnh thông tin liên hệ, địa chỉ hoặc mặt hàng cung cấp.</span></html>";
        JLabel text = new JLabel(html);
        text.setFont(AppFont.BODY);

        banner.add(iconWrap, BorderLayout.WEST);
        banner.add(text, BorderLayout.CENTER);
        return banner;
    }

    /** Hàng meta: Mã NCC + số SP liên kết + công nợ (chỉ khi Sửa). */
    private JPanel buildMetaRow() {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(true);
        row.setBackground(AppColor.BG_LIGHTER);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        row.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(8, 12, 8, 12)));

        FontIcon hashIcon = FontIcon.of(FontAwesomeSolid.HASHTAG, 11);
        hashIcon.setIconColor(AppColor.TEXT_MUTED);
        JLabel idLabel = new JLabel("Mã NCC " + editingEntity.getSupplierId(), hashIcon, SwingConstants.LEFT);
        idLabel.setIconTextGap(6);
        idLabel.setFont(AppFont.SMALL_BOLD);
        idLabel.setForeground(AppColor.TEXT_MUTED);
        row.add(idLabel, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 0));
        right.setOpaque(false);

        FontIcon boxIcon = FontIcon.of(FontAwesomeSolid.BOXES, 11);
        boxIcon.setIconColor(AppColor.TEXT_MUTED);
        productCountLabel = new JLabel("Đang tải...", boxIcon, SwingConstants.RIGHT);
        productCountLabel.setIconTextGap(6);
        productCountLabel.setFont(AppFont.SMALL_BOLD);
        productCountLabel.setForeground(AppColor.TEXT_MUTED);
        right.add(productCountLabel);

        FontIcon debtIcon = FontIcon.of(FontAwesomeSolid.MONEY_BILL_WAVE, 11);
        debtIcon.setIconColor(AppColor.TEXT_MUTED);
        debtLabel = new JLabel(formatDebt(editingEntity.getDebtBalance()), debtIcon, SwingConstants.RIGHT);
        debtLabel.setIconTextGap(6);
        debtLabel.setFont(AppFont.SMALL_BOLD);
        debtLabel.setForeground(AppColor.TEXT_MUTED);
        right.add(debtLabel);

        row.add(right, BorderLayout.EAST);
        loadProductCountAsync();
        return row;
    }

    private void loadProductCountAsync() {
        final int supplierId = editingEntity.getSupplierId();
        new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() {
                return supplierDAO.countProducts(supplierId);
            }

            @Override
            protected void done() {
                int count = 0;
                try {
                    count = get();
                } catch (Exception ignored) {
                    // giữ 0
                }
                if (productCountLabel != null) {
                    productCountLabel.setText(count == 0
                            ? "Chưa liên kết SP"
                            : count + " sản phẩm liên kết");
                }
            }
        }.execute();
    }

    private static String formatDebt(BigDecimal debt) {
        if (debt == null || debt.compareTo(BigDecimal.ZERO) == 0) {
            return "Công nợ: 0 ₫";
        }
        NumberFormat nf = NumberFormat.getInstance(new Locale("vi", "VN"));
        return "Công nợ: " + nf.format(debt) + " ₫";
    }

    // ---------------------------------------------------------------
    // Nút Demo — chỉ hiện khi Thêm mới
    // ---------------------------------------------------------------

    private JPanel buildDemoBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        bar.setOpaque(false);
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        JButton demoButton = new JButton("Điền dữ liệu Demo",
                FontIcon.of(FontAwesomeSolid.BOLT, 13, Color.WHITE));
        demoButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        demoButton.setFocusPainted(false);
        demoButton.setBackground(AppColor.ACCENT);
        demoButton.setForeground(Color.WHITE);
        demoButton.setBorder(new EmptyBorder(7, 14, 7, 14));
        demoButton.setToolTipText("Tự động điền thông tin mẫu để giảm thời gian demo");
        demoButton.addActionListener(e -> fillDemoData());
        demoButton.getModel().addChangeListener(e -> {
            if (demoButton.isEnabled()) {
                demoButton.setBackground(demoButton.getModel().isRollover()
                        ? AppColor.ACCENT_HOVER : AppColor.ACCENT);
            }
        });
        bar.add(demoButton);
        return bar;
    }

    private void fillDemoData() {
        DemoTemplate t = DEMO_TEMPLATES[RANDOM.nextInt(DEMO_TEMPLATES.length)];
        int suffix = 100 + RANDOM.nextInt(900);

        nameField.setText(t.name + " - CN" + suffix);
        phoneField.setText(randomPhoneVn());
        emailField.setText(t.emailSlug + suffix + "@gmail.com");
        addressField.setText(t.address);
        suppliedItemsArea.setText(t.suppliedItems);

        updateCounter(nameCounterLabel, nameField.getText(), MAX_NAME_LENGTH);
        updateCounter(itemsCounterLabel, suppliedItemsArea.getText(), MAX_ITEMS_LENGTH);
        showMessage(null);
        nameField.requestFocusInWindow();
    }

    private static String randomPhoneVn() {
        String prefix = PHONE_PREFIXES[RANDOM.nextInt(PHONE_PREFIXES.length)];
        StringBuilder sb = new StringBuilder(prefix);
        for (int i = 0; i < 7; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------
    // Field helpers — icon wrapper, counter, max-length filter
    // ---------------------------------------------------------------

    private JPanel createIconTextFieldWrapper(FontAwesomeSolid iconKey) {
        JPanel wrapper = new JPanel(new BorderLayout(0, 0));
        wrapper.setOpaque(true);
        wrapper.setBackground(AppColor.WHITE);
        wrapper.setBorder(new LineBorder(AppColor.FIELD_BORDER, 1, true));
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        FontIcon icon = FontIcon.of(iconKey, 14);
        icon.setIconColor(AppColor.TEXT_MUTED);
        JLabel iconLabel = new JLabel(icon);
        iconLabel.setBorder(new EmptyBorder(0, 8, 0, 6));
        wrapper.add(iconLabel, BorderLayout.WEST);

        JTextField field = new JTextField();
        field.setFont(AppFont.FIELD);
        field.setForeground(AppColor.TEXT_PRIMARY);
        field.setBackground(AppColor.WHITE);
        field.setCaretColor(AppColor.ACCENT);
        field.setBorder(new EmptyBorder(6, 2, 6, 8));
        wrapper.add(field, BorderLayout.CENTER);

        wrapper.putClientProperty("field", field);
        return wrapper;
    }

    /** Tạo JTextField đã bọc icon, trả về field (để dùng trong fieldGroup). */
    private JTextField createIconTextField(FontAwesomeSolid iconKey) {
        JPanel wrapper = createIconTextFieldWrapper(iconKey);
        JTextField field = (JTextField) wrapper.getClientProperty("field");
        field.putClientProperty("iconWrapper", wrapper);
        return field;
    }

    /**
     * Gói field (có thể đã bọc icon) + hint nhỏ phía dưới thành 1 cột,
     * dùng trong fieldRow để SĐT / Email căn đều.
     */
    private JPanel wrapFieldWithHint(JTextField field, String hint) {
        JPanel col = new JPanel();
        col.setLayout(new javax.swing.BoxLayout(col, javax.swing.BoxLayout.Y_AXIS));
        col.setOpaque(false);
        col.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel wrapper = (JPanel) field.getClientProperty("iconWrapper");
        if (wrapper != null) {
            wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
            wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
            col.add(wrapper);
        } else {
            field.setAlignmentX(Component.LEFT_ALIGNMENT);
            col.add(field);
        }
        col.add(hintLabel(hint));
        return col;
    }

    private JTextArea createStyledTextArea(int rows) {
        JTextArea area = new JTextArea(rows, 20);
        area.setFont(AppFont.FIELD);
        area.setForeground(AppColor.TEXT_PRIMARY);
        area.setBackground(AppColor.WHITE);
        area.setCaretColor(AppColor.ACCENT);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(new EmptyBorder(8, 10, 8, 10));
        return area;
    }

    private JLabel counterLabel(int current, int max) {
        JLabel label = new JLabel(current + "/" + max);
        label.setFont(AppFont.SMALL);
        label.setForeground(AppColor.TEXT_MUTED);
        return label;
    }

    private void updateCounter(JLabel counter, String text, int max) {
        if (counter == null) return;
        int len = text != null ? text.length() : 0;
        counter.setText(len + "/" + max);
        counter.setForeground(len >= max ? AppColor.ERROR : AppColor.TEXT_MUTED);
    }

    private static DocumentListener simpleListener(Runnable action) {
        return new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { action.run(); }
            @Override public void removeUpdate(DocumentEvent e) { action.run(); }
            @Override public void changedUpdate(DocumentEvent e) { action.run(); }
        };
    }

    /** Chặn gõ/dán quá maxLength — phản hồi UX ngay lúc gõ. */
    private static void installMaxLengthFilter(JTextField field, int maxLength) {
        ((AbstractDocument) field.getDocument()).setDocumentFilter(new MaxLengthFilter(maxLength));
    }

    private static void installMaxLengthFilter(JTextArea area, int maxLength) {
        ((AbstractDocument) area.getDocument()).setDocumentFilter(new MaxLengthFilter(maxLength));
    }

    private static final class MaxLengthFilter extends DocumentFilter {
        private final int maxLength;

        MaxLengthFilter(int maxLength) {
            this.maxLength = maxLength;
        }

        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
                throws BadLocationException {
            if (string == null) return;
            int over = fb.getDocument().getLength() + string.length() - maxLength;
            super.insertString(fb, offset,
                    over > 0 ? string.substring(0, Math.max(0, string.length() - over)) : string, attr);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
            if (text == null) {
                super.replace(fb, offset, length, null, attrs);
                return;
            }
            int over = fb.getDocument().getLength() - length + text.length() - maxLength;
            super.replace(fb, offset, length,
                    over > 0 ? text.substring(0, Math.max(0, text.length() - over)) : text, attrs);
        }
    }

    private static String hex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    // ---------------------------------------------------------------
    // Override fieldRow để hỗ trợ cột có hint (chiều cao lớn hơn)
    // ---------------------------------------------------------------

    @Override
    protected JPanel fieldRow(JPanel panel, JComponent... groups) {
        JPanel row = new JPanel(new GridLayout(1, groups.length, 16, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 88));
        for (JComponent g : groups) {
            row.add(g);
        }
        panel.add(row);
        panel.add(Box.createVerticalStrut(10));
        return row;
    }

    // ---------------------------------------------------------------
    // Hook BaseFormDialog
    // ---------------------------------------------------------------

    @Override
    protected void fillForm(Supplier entity) {
        nameField.setText(nullToEmpty(entity.getSupplierName()));
        phoneField.setText(nullToEmpty(entity.getPhone()));
        emailField.setText(nullToEmpty(entity.getEmail()));
        addressField.setText(nullToEmpty(entity.getAddress()));
        suppliedItemsArea.setText(nullToEmpty(entity.getSuppliedItems()));

        updateCounter(nameCounterLabel, nameField.getText(), MAX_NAME_LENGTH);
        updateCounter(itemsCounterLabel, suppliedItemsArea.getText(), MAX_ITEMS_LENGTH);
    }

    @Override
    protected String validateForm() {
        FormValidator validator = new FormValidator();

        validator.field(nameField.getText())
                .required("Vui lòng nhập tên nhà cung cấp.")
                .maxLength(MAX_NAME_LENGTH, "Tên nhà cung cấp tối đa " + MAX_NAME_LENGTH + " ký tự.");

        String phone = phoneField.getText();
        if (phone != null && !phone.trim().isEmpty()) {
            validator.field(phone).phoneVn("Số điện thoại không đúng định dạng (vd 09xxxxxxxx).");
        }

        String email = emailField.getText();
        if (email != null && !email.trim().isEmpty()) {
            validator.field(email).email("Email không đúng định dạng.");
        }

        String address = addressField.getText();
        if (address != null && !address.trim().isEmpty()) {
            validator.field(address)
                    .maxLength(MAX_ADDRESS_LENGTH, "Địa chỉ tối đa " + MAX_ADDRESS_LENGTH + " ký tự.");
        }

        String items = suppliedItemsArea.getText();
        if (items != null && !items.trim().isEmpty()) {
            validator.field(items)
                    .maxLength(MAX_ITEMS_LENGTH, "Mặt hàng cung cấp tối đa " + MAX_ITEMS_LENGTH + " ký tự.");
        }

        return validator.validate();
    }

    @Override
    protected Supplier collectFormData() {
        Supplier supplier = editingEntity != null ? editingEntity : new Supplier();
        supplier.setSupplierName(nameField.getText().trim());
        supplier.setPhone(blankToNull(phoneField.getText()));
        supplier.setEmail(blankToNull(emailField.getText()));
        supplier.setAddress(blankToNull(addressField.getText()));
        supplier.setSuppliedItems(blankToNull(suppliedItemsArea.getText()));
        return supplier;
    }

    @Override
    protected boolean persist(Supplier entity, CrudMode mode) {
        return mode == CrudMode.ADD ? supplierDAO.insert(entity) : supplierDAO.update(entity);
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}