package com.view.admin.supplier;

import com.components.crud.BaseFormDialog;
import com.components.crud.CrudMode;
import com.dao.SupplierDAO;
import com.model.Supplier;
import com.theme.AppColor;
import com.validation.FormValidator;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.util.Random;

public class SupplierFormDialog extends BaseFormDialog<Supplier> {

    private static final Random RANDOM = new Random();

    /** Du lieu mau de dien nhanh khi Demo project - khong lien quan logic nghiep vu. */
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

    /** Các đầu số di động VN hợp lệ (khớp regex phoneVn trong FormValidator/Rules), dùng để sinh SĐT demo. */
    private static final String[] PHONE_PREFIXES = {
            "032", "033", "034", "035", "036", "037", "038", "039",
            "070", "076", "077", "078", "079",
            "081", "082", "083", "084", "085", "088",
            "090", "091", "092", "093", "094", "096", "097", "098", "099"
    };

    /** Ban ghi don gian chua 1 mau du lieu Demo cho nha cung cap. */
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

    public SupplierFormDialog(Frame owner, CrudMode mode, Supplier editingEntity, SupplierDAO supplierDAO) {
        super(owner, "nhà cung cấp", mode, editingEntity);
        this.supplierDAO = supplierDAO;
        init();
    }

    @Override
    protected int getDialogWidth() { return 480; }

    @Override
    protected int getDialogHeight() { return 560; }

    @Override
    protected void buildFields(JPanel panel) {
        if (mode == CrudMode.ADD) {
            panel.add(buildDemoBar());
            panel.add(Box.createVerticalStrut(10));
        }

        nameField = addTextField(panel, "Tên nhà cung cấp", true);

        phoneField = newTextField();
        emailField = newTextField();
        fieldRow(panel,
                fieldGroup("Số điện thoại", false, phoneField),
                fieldGroup("Email", false, emailField));

        addressField = addTextField(panel, "Địa chỉ", false);

        suppliedItemsArea = addTextArea(panel, "Mặt hàng cung cấp");
    }

    // ---------------------------------------------------------------
    // Nút Demo — chỉ hiện khi Thêm mới, tự động điền dữ liệu mẫu
    // ---------------------------------------------------------------

    private JPanel buildDemoBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        bar.setOpaque(false);
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        JButton demoButton = new JButton("Điền dữ liệu Demo", FontIcon.of(FontAwesomeSolid.BOLT, 13, Color.WHITE));
        demoButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        demoButton.setFocusPainted(false);
        demoButton.setBackground(AppColor.ACCENT);
        demoButton.setForeground(Color.WHITE);
        demoButton.setBorder(new EmptyBorder(7, 14, 7, 14));
        demoButton.setToolTipText("Tự động điền thông tin mẫu để giảm thời gian demo");
        demoButton.addActionListener(e -> fillDemoData());
        bar.add(demoButton);
        return bar;
    }

    /** Dien nhanh mot bo du lieu mau ngau nhien vao form de phuc vu demo, khong dung cho du lieu thuc te. */
    private void fillDemoData() {
        DemoTemplate t = DEMO_TEMPLATES[RANDOM.nextInt(DEMO_TEMPLATES.length)];
        int suffix = 100 + RANDOM.nextInt(900);

        nameField.setText(t.name + " - CN" + suffix);
        phoneField.setText(randomPhoneVn());
        emailField.setText(t.emailSlug + suffix + "@gmail.com");
        addressField.setText(t.address);
        suppliedItemsArea.setText(t.suppliedItems);

        showMessage(null);
        nameField.requestFocusInWindow();
    }

    /** Sinh 1 SDT di dong VN ngau nhien hop le (0 + 9 chu so), chi de demo nhanh. */
    private static String randomPhoneVn() {
        String prefix = PHONE_PREFIXES[RANDOM.nextInt(PHONE_PREFIXES.length)];
        StringBuilder sb = new StringBuilder(prefix);
        for (int i = 0; i < 7; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    @Override
    protected void fillForm(Supplier entity) {
        nameField.setText(entity.getSupplierName());
        phoneField.setText(entity.getPhone());
        emailField.setText(entity.getEmail());
        addressField.setText(entity.getAddress());
        suppliedItemsArea.setText(entity.getSuppliedItems());
    }

    @Override
    protected String validateForm() {
        FormValidator validator = new FormValidator();

        validator.field(nameField.getText())
                .required("Vui lòng nhập tên nhà cung cấp.")
                .maxLength(150, "Tên nhà cung cấp tối đa 150 ký tự.");

        String phone = phoneField.getText();
        if (phone != null && !phone.trim().isEmpty()) {
            validator.field(phone).phoneVn("Số điện thoại không đúng định dạng (vd 09xxxxxxxx).");
        }

        String email = emailField.getText();
        if (email != null && !email.trim().isEmpty()) {
            validator.field(email).email("Email không đúng định dạng.");
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
}