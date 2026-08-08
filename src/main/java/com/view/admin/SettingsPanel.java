package com.view.admin;

import com.components.BaseDialog;
import com.components.LoadingOverlay;
import com.components.SectionHeader;
import com.dao.StoreConfigDAO;
import com.event.AppEventBus;
import com.event.DataChangedEvent;
import com.theme.AppColor;
import com.theme.AppFont;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.math.BigDecimal;
import java.text.ParseException;
import java.util.Map;

public class SettingsPanel extends JPanel {

    private final StoreConfigDAO storeConfigDAO = new StoreConfigDAO();
    private final LoadingOverlay loadingOverlay = new LoadingOverlay("Đang tải cấu hình...");

    private JFormattedTextField vatRateField;
    private JFormattedTextField defaultMarginField;
    private JTextField storeNameField;
    private JFormattedTextField returnPolicyDaysField;
    private JTextField defaultUnitField;
    private JFormattedTextField approvalThresholdField;
    private JButton saveButton;

    public SettingsPanel() {
        setLayout(new BorderLayout());
        setBackground(AppColor.PAGE_BG);
        setBorder(new EmptyBorder(20, 24, 20, 24));

        SectionHeader header = new SectionHeader(FontAwesomeSolid.COGS, AppColor.ACCENT,
                "Cài đặt hệ thống", "Cấu hình chung áp dụng cho toàn bộ cửa hàng");
        saveButton = header.addButton("Lưu thay đổi", FontAwesomeSolid.SAVE,
                SectionHeader.ButtonStyle.PRIMARY, this::onSaveClicked);

        JPanel form = buildForm();

        JScrollPane scroll = new JScrollPane(form);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);

        add(header, BorderLayout.NORTH);
        add(LoadingOverlay.attach(scroll, loadingOverlay), BorderLayout.CENTER);

        loadCurrentConfig();
    }

    private JPanel buildForm() {
        JPanel wrapper = new JPanel();
        wrapper.setOpaque(false);
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setBorder(new EmptyBorder(16, 0, 0, 0));

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(AppColor.WHITE);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(560, Integer.MAX_VALUE));
        card.setBorder(new CompoundBorder(
                new LineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(20, 20, 20, 20)));

        JLabel cardTitle = new JLabel("Thuế & chính sách bán hàng");
        cardTitle.setFont(AppFont.HEADING_MD);
        cardTitle.setForeground(AppColor.TEXT_TITLE);
        cardTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        cardTitle.setBorder(new EmptyBorder(0, 0, 14, 0));
        card.add(cardTitle);

        vatRateField = numberField();
        card.add(fieldGroup("Thuế GTGT - VAT (%)",
                "Áp dụng cho hoá đơn tại quầy (POS) và đơn hàng đặt online. Ví dụ: 8 nghĩa là 8%.",
                vatRateField));
        card.add(Box.createVerticalStrut(16));

        defaultMarginField = numberField();
        card.add(fieldGroup("Chênh lệch giá bán mặc định (VNĐ)",
                "Áp dụng cho SP không đặt chênh lệch riêng: Giá bán = Giá nhập + số này, tự động cập nhật mỗi khi nhập hàng làm đổi Giá nhập.",
                defaultMarginField));
        card.add(Box.createVerticalStrut(16));

        returnPolicyDaysField = numberField();
        card.add(fieldGroup("Số ngày được đổi/trả hàng",
                "Số ngày kể từ ngày mua khách được phép đổi/trả sản phẩm.",
                returnPolicyDaysField));
        card.add(Box.createVerticalStrut(16));

        approvalThresholdField = numberField();
        card.add(fieldGroup("Ngưỡng giá trị cần duyệt đổi/trả (VNĐ)",
                "Phiếu đổi/trả có tổng giá trị hàng khách trả lớn hơn số này sẽ ở trạng thái \"Chờ duyệt\", "
                        + "cần Quản lý bán hàng duyệt trước khi kho/hoá đơn gốc được điều chỉnh. Để 0 nghĩa là mọi phiếu đều cần duyệt.",
                approvalThresholdField));
        card.add(Box.createVerticalStrut(16));

        storeNameField = textField();
        card.add(fieldGroup("Tên cửa hàng", null, storeNameField));
        card.add(Box.createVerticalStrut(16));

        defaultUnitField = textField();
        card.add(fieldGroup("Đơn vị tính mặc định",
                "Dùng khi thêm sản phẩm mới mà chưa chọn đơn vị cụ thể (vd: cái, hộp, chai...).",
                defaultUnitField));

        wrapper.add(card);
        return wrapper;
    }

    private JPanel fieldGroup(String label, String hint, JComponent field) {
        JPanel group = new JPanel();
        group.setOpaque(false);
        group.setLayout(new BoxLayout(group, BoxLayout.Y_AXIS));
        group.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel labelComp = new JLabel(label);
        labelComp.setFont(AppFont.BODY_BOLD);
        labelComp.setForeground(AppColor.TEXT_PRIMARY);
        labelComp.setAlignmentX(Component.LEFT_ALIGNMENT);
        group.add(labelComp);
        group.add(Box.createVerticalStrut(6));

        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setMaximumSize(new Dimension(240, 36));
        group.add(field);

        if (hint != null) {
            group.add(Box.createVerticalStrut(4));
            JLabel hintComp = new JLabel("<html><body style='width: 460px'>" + hint + "</body></html>");
            hintComp.setFont(AppFont.SMALL);
            hintComp.setForeground(AppColor.TEXT_MUTED);
            hintComp.setAlignmentX(Component.LEFT_ALIGNMENT);
            group.add(hintComp);
        }
        return group;
    }

    private JTextField textField() {
        JTextField field = new JTextField();
        styleField(field);
        return field;
    }

    private JFormattedTextField numberField() {
        java.text.NumberFormat format = java.text.NumberFormat.getNumberInstance();
        format.setMaximumFractionDigits(2);
        format.setGroupingUsed(false);
        JFormattedTextField field = new JFormattedTextField(format);
        field.setValue(0);
        styleField(field);
        return field;
    }

    private void styleField(JTextField field) {
        field.setFont(AppFont.FIELD);
        field.setBorder(new CompoundBorder(
                new LineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(6, 10, 6, 10)));
    }

    // ---------------- Tai / luu du lieu ----------------

    private void loadCurrentConfig() {
        loadingOverlay.start("Đang tải cấu hình...");
        saveButton.setEnabled(false);

        SwingWorker<Map<String, String>, Void> worker = new SwingWorker<>() {
            @Override
            protected Map<String, String> doInBackground() {
                return storeConfigDAO.getAll();
            }

            @Override
            protected void done() {
                loadingOverlay.stop();
                saveButton.setEnabled(true);
                try {
                    applyToForm(get());
                } catch (Exception e) {
                    BaseDialog.error(SettingsPanel.this, "Lỗi", "Không thể tải cấu hình hệ thống.");
                }
            }
        };
        worker.execute();
    }

    private void applyToForm(Map<String, String> config) {
        vatRateField.setValue(parseOrDefault(config.get(StoreConfigDAO.KEY_VAT_RATE), new BigDecimal("8")));
        defaultMarginField.setValue(parseOrDefault(config.get(StoreConfigDAO.KEY_DEFAULT_MARGIN), new BigDecimal("5000")));
        returnPolicyDaysField.setValue(parseOrDefault(config.get("RETURN_POLICY_DAYS"), 7));
        approvalThresholdField.setValue(parseOrDefault(config.get(StoreConfigDAO.KEY_APPROVAL_THRESHOLD), new BigDecimal("0")));
        storeNameField.setText(config.getOrDefault("STORE_NAME", ""));
        defaultUnitField.setText(config.getOrDefault("DEFAULT_UNIT", ""));
    }

    private static BigDecimal parseOrDefault(String raw, BigDecimal fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int parseOrDefault(String raw, int fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void onSaveClicked() {
        // Ep commit gia tri dang go do (JFormattedTextField chi cap nhat value
        // luc mat focus/Enter, chua chac da xay ra truoc khi bam nut Luu).
        try {
            vatRateField.commitEdit();
            defaultMarginField.commitEdit();
            returnPolicyDaysField.commitEdit();
            approvalThresholdField.commitEdit();
        } catch (ParseException ignored) {
            // Giu nguyen gia tri cu neu dang go do (khong parse duoc) - validate ben duoi se bat loi.
        }

        BigDecimal vatRate = toBigDecimal(vatRateField.getValue());
        if (vatRate == null || vatRate.compareTo(BigDecimal.ZERO) < 0 || vatRate.compareTo(new BigDecimal("100")) > 0) {
            BaseDialog.error(this, "Dữ liệu không hợp lệ", "Thuế VAT phải là số từ 0 đến 100.");
            return;
        }
        BigDecimal defaultMargin = toBigDecimal(defaultMarginField.getValue());
        if (defaultMargin == null || defaultMargin.compareTo(BigDecimal.ZERO) < 0) {
            BaseDialog.error(this, "Dữ liệu không hợp lệ", "Chênh lệch giá bán mặc định phải là số không âm.");
            return;
        }
        Integer returnDays = toInteger(returnPolicyDaysField.getValue());
        if (returnDays == null || returnDays < 0) {
            BaseDialog.error(this, "Dữ liệu không hợp lệ", "Số ngày đổi/trả phải là số nguyên không âm.");
            return;
        }
        BigDecimal approvalThreshold = toBigDecimal(approvalThresholdField.getValue());
        if (approvalThreshold == null || approvalThreshold.compareTo(BigDecimal.ZERO) < 0) {
            BaseDialog.error(this, "Dữ liệu không hợp lệ", "Ngưỡng giá trị cần duyệt đổi/trả phải là số không âm.");
            return;
        }
        String storeName = storeNameField.getText().trim();
        if (storeName.isEmpty()) {
            BaseDialog.error(this, "Dữ liệu không hợp lệ", "Tên cửa hàng không được để trống.");
            return;
        }
        String defaultUnit = defaultUnitField.getText().trim();
        if (defaultUnit.isEmpty()) {
            BaseDialog.error(this, "Dữ liệu không hợp lệ", "Đơn vị tính mặc định không được để trống.");
            return;
        }

        boolean confirmed = BaseDialog.confirm(this, "Xác nhận lưu",
                "Áp dụng thuế VAT " + vatRate.stripTrailingZeros().toPlainString()
                        + "% cho các hoá đơn/đơn hàng MỚI kể từ bây giờ (không ảnh hưởng hoá đơn đã lập). "
                        + "Chênh lệch giá bán mặc định mới (" + defaultMargin.stripTrailingZeros().toPlainString()
                        + "đ) sẽ áp dụng ngay cho các SP dùng mức chênh lệch chung ở lần nhập hàng/sửa giá tiếp theo?");
        if (!confirmed) return;

        Map<String, String> values = Map.of(
                StoreConfigDAO.KEY_VAT_RATE, vatRate.stripTrailingZeros().toPlainString(),
                StoreConfigDAO.KEY_DEFAULT_MARGIN, defaultMargin.stripTrailingZeros().toPlainString(),
                StoreConfigDAO.KEY_APPROVAL_THRESHOLD, approvalThreshold.stripTrailingZeros().toPlainString(),
                "RETURN_POLICY_DAYS", String.valueOf(returnDays),
                "STORE_NAME", storeName,
                "DEFAULT_UNIT", defaultUnit
        );

        saveButton.setEnabled(false);
        loadingOverlay.start("Đang lưu...");
        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            @Override
            protected Boolean doInBackground() {
                return storeConfigDAO.setValues(values);
            }

            @Override
            protected void done() {
                loadingOverlay.stop();
                saveButton.setEnabled(true);
                boolean ok;
                try {
                    ok = Boolean.TRUE.equals(get());
                } catch (Exception e) {
                    ok = false;
                }
                if (ok) {
                    BaseDialog.success(SettingsPanel.this, "Đã lưu",
                            "Cấu hình hệ thống đã được cập nhật.");
                    AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.CONFIG));
                } else {
                    BaseDialog.error(SettingsPanel.this, "Lỗi",
                            "Không thể lưu cấu hình. Vui lòng thử lại.");
                }
            }
        };
        worker.execute();
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return new BigDecimal(n.toString());
        if (value instanceof String s) {
            try {
                return new BigDecimal(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static Integer toInteger(Object value) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}