package com.view.admin;

import com.components.BaseDialog;
import com.components.LoadingOverlay;
import com.components.SectionHeader;
import com.dao.StoreConfigDAO;
import com.event.AppEventBus;
import com.event.DataChangedEvent;
import com.theme.AppColor;
import com.theme.AppFont;
import com.theme.AppRadius;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

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
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);

        add(header, BorderLayout.NORTH);
        add(LoadingOverlay.attach(scroll, loadingOverlay), BorderLayout.CENTER);

        loadCurrentConfig();
    }

    private JPanel buildForm() {
        // ScrollableFormPanel: ép panel này luôn bằng đúng chiều rộng viewport
        // (không bao giờ rộng hơn) => nội dung tự co giãn theo cửa sổ,
        // không bao giờ phát sinh thanh cuộn ngang.
        JPanel wrapper = new ScrollableFormPanel();
        wrapper.setOpaque(false);
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setBorder(new EmptyBorder(12, 0, 12, 0));

        // ====== Card 1: Thông tin cửa hàng (full width, gọn nhẹ) ======
        wrapper.add(buildSettingsCard(
                FontAwesomeSolid.STORE,
                AppColor.ACCENT,
                "Thông tin cửa hàng",
                "Thông tin cơ bản hiển thị trên hoá đơn, báo cáo và giao diện",
                new Component[][]{
                        {fieldGroup("Tên cửa hàng",
                                "Tên hiển thị trên hoá đơn, màn hình POS và các báo cáo.",
                                storeNameField = textField(), true)},
                        {fieldGroup("Đơn vị tính mặc định",
                                "Dùng khi thêm SP mới (vd: cái, hộp, chai...).",
                                defaultUnitField = textField(), true)}
                },
                true  // compact mode
        ));

        wrapper.add(Box.createVerticalStrut(10));

        // ====== Hàng chứa 2 card nhỏ bên cạnh nhau ======
        // Dùng GridLayout(1,2) khi đủ rộng, tự chuyển GridLayout(2,1) (xếp chồng)
        // khi panel bị thu hẹp dưới ngưỡng TWO_COL_MIN_WIDTH => luôn vừa khung,
        // không tràn ngang.
        final int rowGap = 12;
        JPanel twoColRow = new JPanel(new GridLayout(1, 2, rowGap, rowGap));
        twoColRow.setOpaque(false);
        twoColRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        twoColRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        twoColRow.addComponentListener(new java.awt.event.ComponentAdapter() {
            private static final int TWO_COL_MIN_WIDTH = 620;
            private Boolean stacked = null;

            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                boolean shouldStack = twoColRow.getWidth() < TWO_COL_MIN_WIDTH;
                if (stacked == null || stacked != shouldStack) {
                    stacked = shouldStack;
                    GridLayout gl = (GridLayout) twoColRow.getLayout();
                    gl.setRows(shouldStack ? 2 : 1);
                    gl.setColumns(shouldStack ? 1 : 2);
                    twoColRow.revalidate();
                }
            }
        });

        // Card 2: Thuế & chính sách giá
        twoColRow.add(buildSettingsCard(
                FontAwesomeSolid.PERCENTAGE,
                AppColor.SUCCESS,
                "Thuế & chính sách giá",
                "Cấu hình thuế và quy tắc tính giá bán",
                new Component[][]{
                        {fieldGroup("Thuế GTGT - VAT (%)",
                                "Áp dụng cho hoá đơn POS và đơn hàng online.",
                                vatRateField = numberField(), false)},
                        {fieldGroup("Chênh lệch giá bán (VNĐ)",
                                "Giá bán = Giá nhập + số này.",
                                defaultMarginField = numberField(), false)}
                },
                true  // compact mode
        ));

        // Card 3: Chính sách đổi trả
        twoColRow.add(buildSettingsCard(
                FontAwesomeSolid.EXCHANGE_ALT,
                AppColor.WARNING,
                "Chính sách đổi trả",
                "Quy định thời gian và quy trình duyệt phiếu",
                new Component[][]{
                        {fieldGroup("Số ngày đổi/trả",
                                "Số ngày kể từ ngày mua.",
                                returnPolicyDaysField = numberField(), false)},
                        {fieldGroup("Ngưỡng cần duyệt (VNĐ)",
                                "Lớn hơn số này ở trạng thái Chờ duyệt.",
                                approvalThresholdField = numberField(), false)}
                },
                true  // compact mode
        ));

        wrapper.add(twoColRow);

        return wrapper;
    }

    /**
     * Tạo một card cài đặt với icon, tiêu đề, mô tả và các trường form.
     * fields: mảng 2 chiều [cột][dòng], mỗi phần tử là JPanel fieldGroup đã được tạo.
     * compact: true => giảm padding/khoảng trắng để tiết kiệm không gian.
     */
    private JPanel buildSettingsCard(FontAwesomeSolid iconType, Color iconColor,
                                      String title, String description, Component[][] fields,
                                      boolean compact) {
        JPanel card = new JPanel();
        card.setLayout(new BorderLayout());
        card.setBackground(AppColor.WHITE);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        // Không giới hạn chiều rộng tối đa: card luôn giãn hết chiều rộng
        // khả dụng của khung chứa (BoxLayout Y_AXIS sẽ tự co giãn theo cha).
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        int cardPadding = compact ? 12 : 20;
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(cardPadding, cardPadding, cardPadding, cardPadding)));

        // ====== Header card: Icon + Tiêu đề + Mô tả ======
        JPanel header = new JPanel(new BorderLayout(compact ? 10 : 12, 0));
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(0, 0, (compact ? 10 : 16), 0));

        // Icon box tròn màu
        FontIcon icon = FontIcon.of(iconType, 18);
        icon.setIconColor(Color.WHITE);
        JLabel iconBox = new JLabel(icon) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int size = compact ? 32 : 40;
                int x = (getWidth() - size) / 2;
                int y = (getHeight() - size) / 2;
                g2.setColor(iconColor);
                g2.fillOval(x, y, size, size);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        iconBox.setHorizontalAlignment(SwingConstants.CENTER);
        iconBox.setVerticalAlignment(SwingConstants.CENTER);
        int iconBoxSize = compact ? 36 : 44;
        Dimension iconDim = new Dimension(iconBoxSize, iconBoxSize);
        iconBox.setPreferredSize(iconDim);
        iconBox.setMinimumSize(iconDim);
        iconBox.setMaximumSize(iconDim);
        iconBox.setOpaque(false);

        JPanel titleCol = new JPanel();
        titleCol.setOpaque(false);
        titleCol.setLayout(new BoxLayout(titleCol, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(AppFont.HEADING_MD);
        titleLabel.setForeground(AppColor.TEXT_TITLE);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextArea descLabel = wrapText(description, AppFont.SMALL, AppColor.TEXT_MUTED);
        descLabel.setBorder(new EmptyBorder(2, 0, 0, 0));

        titleCol.add(titleLabel);
        titleCol.add(descLabel);

        header.add(iconBox, BorderLayout.WEST);
        header.add(titleCol, BorderLayout.CENTER);

        // ====== Nội dung form: Bố cục 2 cột ======
        JPanel contentPanel = new JPanel(new GridBagLayout());
        contentPanel.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        // Chia đều trọng số theo đúng số cột thực tế => các cột luôn bằng
        // nhau về chiều rộng dù card có 1 hay nhiều cột (căn đều nội dung).
        gbc.weightx = 1.0 / fields.length;
        gbc.insets = new Insets(0, 0, 16, 16);

        int maxRows = 0;
        for (Component[] col : fields) {
            maxRows = Math.max(maxRows, col.length);
        }

        for (int col = 0; col < fields.length; col++) {
            gbc.gridx = col;
            for (int row = 0; row < fields[col].length; row++) {
                gbc.gridy = row;
                int fieldGap = compact ? 10 : 16;
                int colGap = compact ? 10 : 16;
                gbc.insets = new Insets(0, (col > 0 ? colGap : 0), fieldGap, (col < fields.length - 1 ? colGap : 0));
                // Dòng cuối cùng không có khoảng cách dưới
                if (row == fields[col].length - 1) {
                    gbc.insets = new Insets(0, (col > 0 ? colGap : 0), 0, (col < fields.length - 1 ? colGap : 0));
                }
                contentPanel.add(fields[col][row], gbc);
            }
        }

        // ====== Gộp header + content ======
        card.add(header, BorderLayout.NORTH);
        card.add(contentPanel, BorderLayout.CENTER);

        return card;
    }

    /** Overload: fullWidth mặc định = false. */
    private JPanel fieldGroup(String label, String hint, JComponent field) {
        return fieldGroup(label, hint, field, false);
    }

    /** Overload: không có hint, fullWidth mặc định = false. */
    private JPanel fieldGroup(String label, JComponent field) {
        return fieldGroup(label, null, field, false);
    }

    /** Overload: buildSettingsCard không có compact (mặc định compact = false). */
    private JPanel buildSettingsCard(FontAwesomeSolid iconType, Color iconColor,
                                      String title, String description, Component[][] fields) {
        return buildSettingsCard(iconType, iconColor, title, description, fields, false);
    }

    /**
     * Tạo một nhóm trường form: Label + Input + Hint.
     * fullWidth: true => trường chiếm toàn bộ chiều rộng card (dành cho trường quan trọng/đầu tiên).
     */
    private JPanel fieldGroup(String label, String hint, JComponent field, boolean fullWidth) {
        JPanel group = new JPanel();
        group.setOpaque(false);
        group.setLayout(new BoxLayout(group, BoxLayout.Y_AXIS));
        group.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Label
        JLabel labelComp = new JLabel(label);
        labelComp.setFont(AppFont.BODY_BOLD);
        labelComp.setForeground(AppColor.TEXT_PRIMARY);
        labelComp.setAlignmentX(Component.LEFT_ALIGNMENT);
        labelComp.setBorder(new EmptyBorder(0, 0, 4, 0));
        group.add(labelComp);

        // Input field: không giới hạn chiều rộng tối đa => field luôn giãn
        // khớp với chiều rộng cột mà GridBagLayout (bên ngoài) cấp cho nó,
        // nên các field trên cùng 1 hàng luôn bằng nhau, căn đều.
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (field instanceof JTextField) {
            field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
            field.setPreferredSize(new Dimension(fullWidth ? 280 : 160, 32));
        }
        group.add(field);

        // Hint text
        if (hint != null) {
            JTextArea hintComp = wrapText(hint, AppFont.SMALL, AppColor.TEXT_MUTED);
            hintComp.setBorder(new EmptyBorder(4, 0, 0, 0));
            group.add(hintComp);
        }

        return group;
    }

    /**
     * Nhãn mô tả/hint tự xuống dòng theo đúng chiều rộng thực tế của cột
     * chứa nó (thay cho JLabel HTML với width cố định theo px, vốn gây
     * tràn ngang khi cửa sổ bị thu nhỏ).
     */
    private JTextArea wrapText(String text, Font font, Color color) {
        JTextArea area = new JTextArea(text);
        area.setFont(font);
        area.setForeground(color);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setFocusable(false);
        area.setOpaque(false);
        area.setBorder(null);
        area.setAlignmentX(Component.LEFT_ALIGNMENT);
        area.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return area;
    }

    /**
     * Panel dùng cho nội dung bên trong JScrollPane: luôn ép chiều rộng
     * bằng đúng viewport (không bao giờ rộng hơn hay hẹp hơn) => nội dung
     * tự động reflow theo kích thước cửa sổ và không bao giờ phát sinh
     * thanh cuộn ngang. Vẫn cho phép cuộn dọc bình thường.
     */
    private static class ScrollableFormPanel extends JPanel implements Scrollable {
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 100;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
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
        field.setForeground(AppColor.TEXT_PRIMARY);
        field.setCaretColor(AppColor.TEXT_PRIMARY);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(5, 10, 5, 10)));
        // Hiệu ứng khi focus: đổi màu viền
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                field.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(AppColor.ACCENT, 2, true),
                        new EmptyBorder(4, 9, 4, 9)));
            }
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                field.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(AppColor.BORDER, 1, true),
                        new EmptyBorder(5, 10, 5, 10)));
            }
        });
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