package com.view.admin.inventory;

import com.components.BaseDialog;
import com.components.DatePickerField;
import com.dao.ProductDAO;
import com.dao.PurchaseReceiptDAO;
import com.dao.SupplierDAO;
import com.model.Product;
import com.model.PurchaseReceiptDetail;
import com.model.Supplier;
import com.service.AuthService;
import com.theme.AppColor;
import com.utils.CurrencyDocumentFilter;
import com.utils.NumberUtil;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.BiConsumer;

/**
 * Form lập phiếu nhập kho nhiều dòng — giao diện card hiện đại.
 * Form thêm từng dòng + bảng danh sách (chỉ đọc + xóa).
 */
public class PurchaseReceiptFormDialog extends JDialog {

    private static final Random RANDOM = new Random();
    /** So dong demo se duoc tu dong sinh moi lan bam nut - chi de demo, khong lien quan logic nghiep vu. */
    private static final int DEMO_MIN_LINES = 2;
    private static final int DEMO_MAX_LINES = 4;
    private static final String[] DEMO_LOT_PREFIXES = {"LNCC", "PO", "BATCH"};

    private final PurchaseReceiptDAO receiptDAO = new PurchaseReceiptDAO();
    private final List<Product> products;
    private final List<Supplier> suppliers;

    private JComboBox<Supplier> supplierCombo;
    private JComboBox<Product> productCombo;
    private JTextField quantityField;
    private JTextField priceField;
    private JTextField lotField;
    private DatePickerField mfgPicker;
    private DatePickerField expPicker;

    private LineTableModel tableModel;
    private JTable lineTable;
    private JLabel totalLabel;
    private JLabel lineCountLabel;
    private JLabel emptyHint;
    private JButton demoButton;

    private BiConsumer<Integer, Integer> onSaved;

    public PurchaseReceiptFormDialog(Frame owner) {
        super(owner, "Lập phiếu nhập kho", Dialog.ModalityType.APPLICATION_MODAL);
        this.products = new ProductDAO().findAllActive();
        this.suppliers = new SupplierDAO().findAllOrderByName();

        setSize(1020, 700);
        setMinimumSize(new Dimension(900, 600));
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        Color pageBg = AppColor.PAGE_BG != null ? AppColor.PAGE_BG : new Color(248, 250, 252);
        getContentPane().setBackground(pageBg);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        refreshSummary();
        setLocationRelativeTo(owner);
    }

    public void onSaved(BiConsumer<Integer, Integer> callback) {
        this.onSaved = callback;
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(16, 0));
        header.setBackground(AppColor.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, softBorder()),
                new EmptyBorder(20, 28, 18, 28)));

        JPanel iconBox = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = AppColor.ACCENT_BG_SOFT != null ? AppColor.ACCENT_BG_SOFT : new Color(236, 253, 245);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        iconBox.setOpaque(false);
        iconBox.setPreferredSize(new Dimension(52, 52));
        iconBox.setLayout(new BorderLayout());
        JLabel icon = new JLabel(FontIcon.of(FontAwesomeSolid.FILE_INVOICE, 22, AppColor.ACCENT));
        icon.setHorizontalAlignment(SwingConstants.CENTER);
        iconBox.add(icon, BorderLayout.CENTER);

        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Lập phiếu nhập kho");
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setForeground(AppColor.TEXT_TITLE != null ? AppColor.TEXT_TITLE : AppColor.TEXT_PRIMARY);
        JLabel sub = new JLabel("Một phiếu · nhiều sản phẩm · mỗi dòng tự sinh 1 lô hàng (FEFO)");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        sub.setForeground(AppColor.TEXT_MUTED);
        titles.add(title);
        titles.add(Box.createVerticalStrut(4));
        titles.add(sub);

        header.add(iconBox, BorderLayout.WEST);
        header.add(titles, BorderLayout.CENTER);
        return header;
    }

    private JComponent buildBody() {
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(16, 24, 8, 24));

        body.add(buildDemoBar());
        body.add(Box.createVerticalStrut(10));
        body.add(cardSupplier());
        body.add(Box.createVerticalStrut(12));
        body.add(cardAddLine());
        body.add(Box.createVerticalStrut(12));
        body.add(cardLinesTable());

        JScrollPane outer = new JScrollPane(body);
        outer.setBorder(null);
        Color pageBg = AppColor.PAGE_BG != null ? AppColor.PAGE_BG : new Color(248, 250, 252);
        outer.getViewport().setBackground(pageBg);
        outer.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        outer.getVerticalScrollBar().setUnitIncrement(16);
        return outer;
    }

    // ---------------------------------------------------------------
    // Nút Demo — tự động điền nhà cung cấp + sinh vài dòng sản phẩm mẫu
    // ---------------------------------------------------------------

    private JPanel buildDemoBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        bar.setOpaque(false);
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        demoButton = new JButton("Điền dữ liệu Demo", FontIcon.of(FontAwesomeSolid.BOLT, 13, Color.WHITE));
        demoButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        demoButton.setFocusPainted(false);
        demoButton.setBackground(AppColor.ACCENT);
        demoButton.setForeground(Color.WHITE);
        demoButton.setBorder(new EmptyBorder(7, 14, 7, 14));
        demoButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        demoButton.setToolTipText("Tự động chọn NCC và sinh vài dòng sản phẩm mẫu để giảm thời gian demo");
        demoButton.addActionListener(e -> fillDemoData());
        bar.add(demoButton);
        return bar;
    }

    /**
     * Dien nhanh nha cung cap + sinh vai dong san pham ngau nhien vao bang
     * de phuc vu demo, khong dung cho du lieu thuc te.
     */
    private void fillDemoData() {
        if (suppliers.isEmpty()) {
            BaseDialog.error(this, "Chưa có nhà cung cấp", "Vui lòng tạo nhà cung cấp trước khi dùng demo.");
            return;
        }
        if (products.isEmpty()) {
            BaseDialog.error(this, "Chưa có sản phẩm", "Vui lòng tạo sản phẩm trước khi dùng demo.");
            return;
        }

        if (supplierCombo.getSelectedItem() == null) {
            supplierCombo.setSelectedIndex(RANDOM.nextInt(suppliers.size()));
        }

        List<Product> shuffled = new ArrayList<>(products);
        java.util.Collections.shuffle(shuffled, RANDOM);
        int lineCount = Math.min(shuffled.size(), DEMO_MIN_LINES + RANDOM.nextInt(DEMO_MAX_LINES - DEMO_MIN_LINES + 1));

        for (int i = 0; i < lineCount; i++) {
            Product product = shuffled.get(i);
            LineRow row = new LineRow();
            row.product = product;
            row.quantity = 10 + RANDOM.nextInt(191); // 10 - 200
            row.importPrice = randomDemoPrice(product);
            row.lotNumber = DEMO_LOT_PREFIXES[RANDOM.nextInt(DEMO_LOT_PREFIXES.length)] + "-" + (100000 + RANDOM.nextInt(900000));
            row.manufactureDate = LocalDate.now().minusDays(1 + RANDOM.nextInt(60));
            row.expiryDate = row.manufactureDate.plusDays(90 + RANDOM.nextInt(275)); // ~3 - 12 tháng sau NSX
            tableModel.addRow(row);
        }
        refreshSummary();
        supplierCombo.requestFocusInWindow();
    }

    /** Gia nhap demo: lay theo gia nhap hien tai cua san pham (+-15%), hoac ngau nhien neu san pham chua co gia nhap. */
    private BigDecimal randomDemoPrice(Product product) {
        BigDecimal base = product.getImportPrice();
        if (base == null || base.signum() <= 0) {
            long fallback = 5000 + RANDOM.nextInt(20) * 5000L; // 5,000 - 100,000
            return BigDecimal.valueOf(fallback);
        }
        double variance = 0.85 + RANDOM.nextDouble() * 0.3; // 85% - 115%
        long rounded = Math.round(base.doubleValue() * variance / 500.0) * 500;
        return BigDecimal.valueOf(Math.max(rounded, 500));
    }

    private JPanel cardSupplier() {
        JPanel card = roundedCard();
        card.setLayout(new BorderLayout(16, 0));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));

        JLabel label = sectionLabel("Nhà cung cấp");
        supplierCombo = styledCombo(suppliers.toArray(new Supplier[0]));
        supplierCombo.setRenderer((list, value, index, isSelected, cellHasFocus) ->
                comboLabel(value == null ? "— Chọn nhà cung cấp —" : value.getSupplierName(), isSelected));
        supplierCombo.setPreferredSize(new Dimension(420, 38));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(label);
        left.add(Box.createHorizontalStrut(14));
        left.add(supplierCombo);

        JLabel required = new JLabel("* bắt buộc");
        required.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        required.setForeground(AppColor.TEXT_MUTED);

        card.add(left, BorderLayout.WEST);
        card.add(required, BorderLayout.EAST);
        return card;
    }

    private JPanel cardAddLine() {
        JPanel card = roundedCard();
        card.setLayout(new BorderLayout(0, 12));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        JLabel title = sectionLabel("Thêm sản phẩm vào phiếu");
        title.setIcon(FontIcon.of(FontAwesomeSolid.PLUS_CIRCLE, 14, AppColor.ACCENT));
        title.setIconTextGap(8);
        titleRow.add(title, BorderLayout.WEST);

        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.insets = new Insets(0, 0, 8, 10);
        gc.weightx = 1;

        productCombo = styledCombo(products.toArray(new Product[0]));
        productCombo.setRenderer((list, value, index, isSelected, cellHasFocus) ->
                comboLabel(value == null ? "— Chọn sản phẩm —" : value.getProductName(), isSelected));
        quantityField = styledField();
        priceField = styledField();
        CurrencyDocumentFilter.install(priceField);
        lotField = styledField();
        mfgPicker = new DatePickerField();
        expPicker = new DatePickerField();

        gc.gridy = 0;
        gc.gridx = 0; gc.gridwidth = 2; gc.weightx = 2;
        grid.add(fieldBlock("Sản phẩm *", productCombo), gc);
        gc.gridx = 2; gc.gridwidth = 1; gc.weightx = 0.6;
        grid.add(fieldBlock("Số lượng *", quantityField), gc);
        gc.gridx = 3; gc.weightx = 0.8;
        grid.add(fieldBlock("Giá nhập *", priceField), gc);

        gc.gridy = 1;
        gc.gridx = 0; gc.gridwidth = 1; gc.weightx = 1;
        gc.insets = new Insets(0, 0, 0, 10);
        grid.add(fieldBlock("Số lô NCC *", lotField), gc);
        gc.gridx = 1;
        grid.add(fieldBlock("NSX", mfgPicker), gc);
        gc.gridx = 2;
        grid.add(fieldBlock("HSD", expPicker), gc);
        gc.gridx = 3; gc.weightx = 0.8;
        JPanel btnWrap = new JPanel(new BorderLayout());
        btnWrap.setOpaque(false);
        btnWrap.add(fieldLabel(" "), BorderLayout.NORTH);
        JButton addBtn = accentButton("Thêm dòng", FontAwesomeSolid.PLUS);
        addBtn.setPreferredSize(new Dimension(130, 36));
        addBtn.addActionListener(e -> handleAddLine());
        btnWrap.add(addBtn, BorderLayout.CENTER);
        grid.add(btnWrap, gc);

        card.add(titleRow, BorderLayout.NORTH);
        card.add(grid, BorderLayout.CENTER);
        return card;
    }

    private JPanel cardLinesTable() {
        JPanel card = roundedCard();
        card.setLayout(new BorderLayout(0, 10));
        card.setPreferredSize(new Dimension(0, 280));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 360));

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        JLabel title = sectionLabel("Danh sách sản phẩm trên phiếu");
        title.setIcon(FontIcon.of(FontAwesomeSolid.LIST, 14, AppColor.ACCENT));
        title.setIconTextGap(8);
        lineCountLabel = new JLabel("0 dòng");
        lineCountLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lineCountLabel.setForeground(AppColor.TEXT_MUTED);
        titleRow.add(title, BorderLayout.WEST);
        titleRow.add(lineCountLabel, BorderLayout.EAST);

        tableModel = new LineTableModel();
        lineTable = new JTable(tableModel);
        lineTable.setRowHeight(40);
        lineTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lineTable.setShowHorizontalLines(true);
        lineTable.setShowVerticalLines(false);
        lineTable.setGridColor(softBorder());
        lineTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        Color selBg = AppColor.ACCENT_SELECTION_BG != null ? AppColor.ACCENT_SELECTION_BG : new Color(209, 250, 229);
        lineTable.setSelectionBackground(selBg);
        lineTable.setSelectionForeground(AppColor.TEXT_PRIMARY);
        lineTable.setBackground(AppColor.WHITE);
        lineTable.setFillsViewportHeight(true);
        lineTable.setIntercellSpacing(new Dimension(0, 0));

        JTableHeader header = lineTable.getTableHeader();
        header.setFont(new Font("Segoe UI", Font.BOLD, 12));
        Color headerBg = AppColor.TABLE_HEADER_BG != null ? AppColor.TABLE_HEADER_BG : new Color(241, 245, 249);
        header.setBackground(headerBg);
        header.setForeground(AppColor.TEXT_SECONDARY != null ? AppColor.TEXT_SECONDARY : AppColor.TEXT_MUTED);
        header.setPreferredSize(new Dimension(0, 36));
        header.setReorderingAllowed(false);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, softBorder()));

        int[] widths = {200, 70, 100, 100, 100, 100, 110, 56};
        for (int i = 0; i < widths.length && i < lineTable.getColumnCount(); i++) {
            lineTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        lineTable.getColumnModel().getColumn(7).setMaxWidth(64);

        DefaultTableCellRenderer left = cellRenderer(SwingConstants.LEFT);
        DefaultTableCellRenderer right = cellRenderer(SwingConstants.RIGHT);
        DefaultTableCellRenderer center = cellRenderer(SwingConstants.CENTER);
        for (int i = 0; i < 7; i++) {
            lineTable.getColumnModel().getColumn(i).setCellRenderer(
                    i == 1 || i == 2 || i == 6 ? right : (i == 4 || i == 5 ? center : left));
        }
        lineTable.getColumnModel().getColumn(7).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                JLabel lb = new JLabel(FontIcon.of(FontAwesomeSolid.TRASH_ALT, 13,
                        isSelected ? AppColor.ERROR : new Color(148, 163, 184)));
                lb.setHorizontalAlignment(SwingConstants.CENTER);
                lb.setOpaque(true);
                Color bg = isSelected ? selBg : (row % 2 == 0 ? AppColor.WHITE : softStripe());
                lb.setBackground(bg);
                lb.setToolTipText("Xóa dòng này");
                return lb;
            }
        });

        lineTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int col = lineTable.columnAtPoint(e.getPoint());
                int row = lineTable.rowAtPoint(e.getPoint());
                if (col == 7 && row >= 0) {
                    tableModel.removeRow(row);
                    refreshSummary();
                }
            }
        });

        JScrollPane scroll = new JScrollPane(lineTable);
        scroll.setBorder(BorderFactory.createLineBorder(softBorder(), 1));
        scroll.getViewport().setBackground(AppColor.WHITE);
        scroll.setPreferredSize(new Dimension(0, 200));

        emptyHint = new JLabel("Chưa có sản phẩm — dùng form phía trên để thêm dòng", SwingConstants.CENTER);
        emptyHint.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        emptyHint.setForeground(AppColor.TEXT_MUTED);
        emptyHint.setBorder(new EmptyBorder(24, 0, 24, 0));

        JPanel tableWrap = new JPanel(new BorderLayout());
        tableWrap.setOpaque(false);
        tableWrap.add(scroll, BorderLayout.CENTER);
        tableWrap.add(emptyHint, BorderLayout.SOUTH);

        card.add(titleRow, BorderLayout.NORTH);
        card.add(tableWrap, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(AppColor.WHITE);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, softBorder()),
                new EmptyBorder(14, 28, 16, 28)));

        JPanel totalBox = new JPanel();
        totalBox.setOpaque(false);
        totalBox.setLayout(new BoxLayout(totalBox, BoxLayout.Y_AXIS));
        JLabel totalCap = new JLabel("TỔNG TIỀN");
        totalCap.setFont(new Font("Segoe UI", Font.BOLD, 11));
        totalCap.setForeground(AppColor.TEXT_MUTED);
        totalLabel = new JLabel("0 đ");
        totalLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        totalLabel.setForeground(AppColor.ACCENT);
        totalBox.add(totalCap);
        totalBox.add(Box.createVerticalStrut(2));
        totalBox.add(totalLabel);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);
        JButton cancel = ghostButton("Hủy", FontAwesomeSolid.TIMES);
        cancel.addActionListener(e -> dispose());
        JButton save = accentButton("Lưu phiếu nhập", FontAwesomeSolid.SAVE);
        save.setPreferredSize(new Dimension(170, 40));
        save.addActionListener(e -> handleSave());
        actions.add(cancel);
        actions.add(save);

        footer.add(totalBox, BorderLayout.WEST);
        footer.add(actions, BorderLayout.EAST);
        getRootPane().setDefaultButton(save);
        return footer;
    }

    private void handleAddLine() {
        Product product = (Product) productCombo.getSelectedItem();
        if (product == null) {
            BaseDialog.error(this, "Thiếu thông tin", "Vui lòng chọn sản phẩm.");
            return;
        }
        int qty;
        try {
            qty = Integer.parseInt(quantityField.getText().trim().replaceAll("[^0-9]", ""));
            if (qty <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            BaseDialog.error(this, "Số lượng không hợp lệ", "Số lượng phải là số nguyên lớn hơn 0.");
            quantityField.requestFocus();
            return;
        }
        BigDecimal price = parseAmount(priceField.getText());
        if (price == null || price.signum() < 0) {
            BaseDialog.error(this, "Giá nhập không hợp lệ", "Nhập giá ≥ 0 (có thể dùng dấu phẩy phân cách hàng nghìn).");
            priceField.requestFocus();
            return;
        }
        // Bat buoc nhap so lo NCC: he thong se tu sinh ma lo noi bo (LOT_xxxxxx)
        // cho moi dong, nhung neu khong ghi lai so lo tren bao bi NCC ngay luc
        // nhap thi ve sau khong the doi chieu duoc lo nao la lo nao ngoai thuc te
        // (vd khi kho hoi "lo nay nam o dau" / doi tra hang / thu hoi lo loi).
        String lot = blankToNull(lotField.getText());
        if (lot == null) {
            BaseDialog.error(this, "Thiếu số lô NCC",
                    "Vui lòng ghi lại số lô in trên bao bì/phiếu giao của nhà cung cấp trước khi thêm dòng.\n"
                            + "Hệ thống sẽ tự sinh mã lô nội bộ, nhưng cần số lô NCC để đối chiếu thực tế.");
            lotField.requestFocus();
            return;
        }

        LocalDate mfg = mfgPicker.getValue();
        LocalDate exp = expPicker.getValue();
        if (mfg != null && exp != null && !exp.isAfter(mfg)) {
            BaseDialog.error(this, "Ngày không hợp lệ", "Hạn sử dụng phải sau ngày sản xuất.");
            return;
        }

        LineRow row = new LineRow();
        row.product = product;
        row.quantity = qty;
        row.importPrice = price;
        row.lotNumber = lot;
        row.manufactureDate = mfg;
        row.expiryDate = exp;
        tableModel.addRow(row);
        refreshSummary();

        quantityField.setText("");
        priceField.setText("");
        lotField.setText("");
        mfgPicker.setValue(null);
        expPicker.setValue(null);
        productCombo.requestFocus();
    }

    private void handleSave() {
        Supplier supplier = (Supplier) supplierCombo.getSelectedItem();
        if (supplier == null) {
            BaseDialog.error(this, "Thiếu thông tin", "Vui lòng chọn nhà cung cấp.");
            return;
        }
        if (tableModel.getRowCount() == 0) {
            BaseDialog.error(this, "Chưa có sản phẩm", "Thêm ít nhất 1 dòng sản phẩm trước khi lưu phiếu.");
            return;
        }

        List<PurchaseReceiptDetail> details = new ArrayList<>();
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            LineRow row = tableModel.getRow(i);
            PurchaseReceiptDetail d = new PurchaseReceiptDetail();
            d.setProductId(row.product.getProductId());
            d.setProductName(row.product.getProductName());
            d.setQuantity(row.quantity);
            d.setImportPrice(row.importPrice);
            d.setLotNumber(row.lotNumber);
            d.setManufactureDate(row.manufactureDate);
            d.setExpiryDate(row.expiryDate);
            details.add(d);
        }

        Color hover = AppColor.ACCENT_HOVER != null ? AppColor.ACCENT_HOVER : AppColor.ACCENT;
        boolean ok = BaseDialog.confirm(this, "Xác nhận lập phiếu",
                "Lập phiếu nhập " + details.size() + " dòng từ \"" + supplier.getSupplierName()
                        + "\"?\nTồn kho và lô hàng sẽ được cập nhật ngay sau khi lưu.",
                "Lưu phiếu", AppColor.ACCENT, hover, FontAwesomeSolid.SAVE);
        if (!ok) return;

        int userId = AuthService.getInstance().getCurrentUser().getUserId();
        int receiptId = receiptDAO.createReceipt(supplier.getSupplierId(), userId, details);
        if (receiptId > 0) {
            BaseDialog.success(this, "Thành công",
                    "Đã lập phiếu PN_" + String.format("%06d", receiptId)
                            + " (" + details.size() + " dòng). Lô hàng đã được sinh tự động.");
            if (onSaved != null) onSaved.accept(receiptId, details.size());
            dispose();
        } else {
            BaseDialog.error(this, "Thất bại", "Không thể lập phiếu nhập. Vui lòng thử lại.");
        }
    }

    private void refreshSummary() {
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            total = total.add(tableModel.getRow(i).lineTotal());
        }
        totalLabel.setText(NumberUtil.formatThousands(total.longValue()) + " đ");
        int n = tableModel.getRowCount();
        lineCountLabel.setText(n + " dòng");
        emptyHint.setVisible(n == 0);
        lineTable.setVisible(true);
    }

    private JPanel roundedCard() {
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(AppColor.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.setColor(softBorder());
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(16, 18, 16, 18));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        return card;
    }

    private JLabel sectionLabel(String text) {
        JLabel lb = new JLabel(text);
        lb.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lb.setForeground(AppColor.TEXT_PRIMARY);
        return lb;
    }

    private JLabel fieldLabel(String text) {
        JLabel lb = new JLabel(text);
        lb.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lb.setForeground(AppColor.TEXT_MUTED);
        lb.setBorder(new EmptyBorder(0, 0, 4, 0));
        return lb;
    }

    private JPanel fieldBlock(String label, JComponent field) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(fieldLabel(label), BorderLayout.NORTH);
        p.add(field, BorderLayout.CENTER);
        return p;
    }

    private <E> JComboBox<E> styledCombo(E[] items) {
        JComboBox<E> combo = new JComboBox<>(items);
        combo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        combo.setBackground(AppColor.WHITE);
        combo.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(softBorder(), 1, true),
                new EmptyBorder(4, 8, 4, 8)));
        combo.setPreferredSize(new Dimension(0, 36));
        return combo;
    }

    private JLabel comboLabel(String text, boolean selected) {
        JLabel lb = new JLabel(text);
        lb.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lb.setOpaque(true);
        lb.setBackground(selected ? AppColor.ACCENT : AppColor.WHITE);
        lb.setForeground(selected ? Color.WHITE : AppColor.TEXT_PRIMARY);
        lb.setBorder(new EmptyBorder(6, 10, 6, 10));
        return lb;
    }

    private JTextField styledField() {
        JTextField f = new JTextField();
        f.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        f.setBackground(AppColor.WHITE);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(softBorder(), 1, true),
                new EmptyBorder(6, 10, 6, 10)));
        f.setPreferredSize(new Dimension(0, 36));
        return f;
    }

    private JButton accentButton(String text, FontAwesomeSolid icon) {
        JButton btn = new JButton(text, FontIcon.of(icon, 13, Color.WHITE));
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setFocusPainted(false);
        btn.setBackground(AppColor.ACCENT);
        btn.setForeground(Color.WHITE);
        btn.setBorder(new EmptyBorder(8, 16, 8, 16));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btn.setBackground(AppColor.ACCENT_HOVER != null ? AppColor.ACCENT_HOVER : AppColor.ACCENT.darker());
            }
            @Override
            public void mouseExited(MouseEvent e) {
                btn.setBackground(AppColor.ACCENT);
            }
        });
        return btn;
    }

    private JButton ghostButton(String text, FontAwesomeSolid icon) {
        Color muted = AppColor.TEXT_SECONDARY != null ? AppColor.TEXT_SECONDARY : AppColor.TEXT_MUTED;
        JButton btn = new JButton(text, FontIcon.of(icon, 12, muted));
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        btn.setFocusPainted(false);
        btn.setBackground(AppColor.WHITE);
        btn.setForeground(AppColor.TEXT_PRIMARY);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(softBorder(), 1, true),
                new EmptyBorder(7, 14, 7, 14)));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private DefaultTableCellRenderer cellRenderer(int align) {
        Color selBg = AppColor.ACCENT_SELECTION_BG != null ? AppColor.ACCENT_SELECTION_BG : new Color(209, 250, 229);
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setHorizontalAlignment(align);
                setBorder(new EmptyBorder(0, 12, 0, 12));
                setFont(new Font("Segoe UI", Font.PLAIN, 13));
                if (isSelected) {
                    setBackground(selBg);
                    setForeground(AppColor.TEXT_PRIMARY);
                } else {
                    setBackground(row % 2 == 0 ? AppColor.WHITE : softStripe());
                    setForeground(AppColor.TEXT_PRIMARY);
                }
                return this;
            }
        };
    }

    private static Color softBorder() {
        return AppColor.BORDER != null ? AppColor.BORDER : new Color(226, 232, 240);
    }

    private static Color softStripe() {
        return AppColor.TABLE_ROW_ODD != null ? AppColor.TABLE_ROW_ODD : new Color(248, 250, 252);
    }

    private static String blankToNull(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    private static BigDecimal parseAmount(String value) {
        return CurrencyDocumentFilter.parse(value);
    }

    private static final class LineRow {
        Product product;
        int quantity;
        BigDecimal importPrice = BigDecimal.ZERO;
        String lotNumber;
        LocalDate manufactureDate;
        LocalDate expiryDate;

        BigDecimal lineTotal() {
            if (importPrice == null || quantity <= 0) return BigDecimal.ZERO;
            return importPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }

    private static final class LineTableModel extends AbstractTableModel {
        private static final String[] COLS = {
                "Sản phẩm", "SL", "Giá nhập", "Số lô NCC", "NSX", "HSD", "Thành tiền", ""
        };
        private final List<LineRow> rows = new ArrayList<>();

        void addRow(LineRow row) {
            rows.add(row);
            fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
        }

        void removeRow(int index) {
            if (index < 0 || index >= rows.size()) return;
            rows.remove(index);
            fireTableRowsDeleted(index, index);
        }

        LineRow getRow(int index) {
            return rows.get(index);
        }

        @Override
        public int getRowCount() { return rows.size(); }

        @Override
        public int getColumnCount() { return COLS.length; }

        @Override
        public String getColumnName(int column) { return COLS[column]; }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) { return false; }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            LineRow r = rows.get(rowIndex);
            switch (columnIndex) {
                case 0: return r.product != null ? r.product.getProductName() : "";
                case 1: return r.quantity;
                case 2: return NumberUtil.formatThousands(r.importPrice.longValue());
                case 3: return r.lotNumber != null ? r.lotNumber : "—";
                case 4: return r.manufactureDate != null ? r.manufactureDate.toString() : "—";
                case 5: return r.expiryDate != null ? r.expiryDate.toString() : "—";
                case 6: return NumberUtil.formatThousands(r.lineTotal().longValue());
                case 7: return "";
                default: return null;
            }
        }
    }
}