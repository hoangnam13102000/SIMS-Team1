package com.view.admin.inventory;

import com.components.BaseDialog;
import com.dao.SupplierDAO;
import com.dao.SupplierReturnDAO;
import com.model.Supplier;
import com.model.SupplierReturnDetail;
import com.service.AuthService;
import com.theme.AppColor;
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
import javax.swing.JTextArea;
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
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Form lap phieu tra hang lo ve NCC — UI dong bo voi StockDisposalFormDialog.
 * Chon NCC truoc -> danh sach lo con hang cua dung NCC do (kem ma phieu nhap
 * goc de tra cuu theo phieu nhap neu can) -> them dong -> tru kho NGAY khi
 * luu, cong don cong no NCC (Suppliers.DebtBalance).
 */
public class SupplierReturnFormDialog extends JDialog {

    private static final String[] REASON_CODES = {"DAMAGED", "EXPIRED", "QUALITY", "WRONG_SPEC", "OTHER"};
    private static final String[] REASON_LABELS = {"Hỏng / hư hỏng", "Hết hạn sớm", "Chất lượng", "Sai quy cách", "Khác"};

    private final SupplierReturnDAO returnDAO = new SupplierReturnDAO();
    private final SupplierDAO supplierDAO = new SupplierDAO();
    private final List<Supplier> suppliers;

    private JComboBox<Supplier> supplierCombo;
    private JComboBox<String> reasonCombo;
    private JTextArea noteArea;
    private JComboBox<SupplierReturnDAO.ReturnableBatch> batchCombo;
    private JTextField qtyField;
    private JTextField priceField;
    private JLabel supplierDebtHint;

    private LineTableModel tableModel;
    private JTable lineTable;
    private JLabel totalLabel;
    private JLabel lineCountLabel;
    private JLabel emptyHint;

    private BiConsumer<Integer, Integer> onSaved;

    public SupplierReturnFormDialog(Frame owner) {
        super(owner, "Lập phiếu trả hàng NCC", Dialog.ModalityType.APPLICATION_MODAL);
        this.suppliers = supplierDAO.getAll();

        setSize(1040, 720);
        setMinimumSize(new Dimension(920, 620));
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

        reloadBatchCombo();
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

        Color accent = AppColor.ACCENT != null ? AppColor.ACCENT : new Color(37, 99, 235);
        Color accentBg = AppColor.ACCENT_BG_SOFT != null ? AppColor.ACCENT_BG_SOFT : new Color(219, 234, 254);
        JPanel iconBox = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(accentBg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        iconBox.setOpaque(false);
        iconBox.setPreferredSize(new Dimension(52, 52));
        iconBox.setLayout(new BorderLayout());
        JLabel icon = new JLabel(FontIcon.of(FontAwesomeSolid.UNDO, 22, accent));
        icon.setHorizontalAlignment(SwingConstants.CENTER);
        iconBox.add(icon, BorderLayout.CENTER);

        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Lập phiếu trả hàng nhà cung cấp");
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setForeground(AppColor.TEXT_TITLE != null ? AppColor.TEXT_TITLE : AppColor.TEXT_PRIMARY);
        JLabel sub = new JLabel("Một phiếu · một NCC · nhiều lô · trừ tồn ngay · cộng dồn công nợ NCC");
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

        body.add(cardSupplierReason());
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

    private JPanel cardSupplierReason() {
        JPanel card = roundedCard();
        card.setLayout(new GridBagLayout());
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));

        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.insets = new Insets(0, 0, 0, 12);

        supplierCombo = styledCombo(suppliers.toArray(new Supplier[0]));
        supplierCombo.setPreferredSize(new Dimension(220, 38));
        supplierCombo.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
            String text = value != null ? value.getSupplierName() : "— Chọn NCC —";
            return comboLabel(text, isSelected);
        });
        supplierCombo.addActionListener(e -> {
            if (!tableModel.rows.isEmpty()) {
                tableModel.clear();
                refreshSummary();
            }
            reloadBatchCombo();
        });

        reasonCombo = styledCombo(REASON_LABELS);
        reasonCombo.setPreferredSize(new Dimension(180, 38));

        noteArea = new JTextArea(2, 24);
        noteArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        noteArea.setLineWrap(true);
        noteArea.setWrapStyleWord(true);
        noteArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(softBorder(), 1),
                new EmptyBorder(8, 10, 8, 10)));
        JScrollPane noteScroll = new JScrollPane(noteArea);
        noteScroll.setPreferredSize(new Dimension(360, 52));
        noteScroll.setBorder(BorderFactory.createLineBorder(softBorder(), 1));

        gc.gridx = 0; gc.gridy = 0; gc.weightx = 0.3;
        card.add(fieldBlock("Nhà cung cấp *", supplierCombo), gc);
        gc.gridx = 1; gc.weightx = 0.25;
        card.add(fieldBlock("Lý do *", reasonCombo), gc);
        gc.gridx = 2; gc.weightx = 0.45;
        card.add(fieldBlock("Ghi chú", noteScroll), gc);
        return card;
    }

    private JPanel cardAddLine() {
        JPanel card = roundedCard();
        card.setLayout(new BorderLayout(0, 12));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        JLabel title = sectionLabel("Thêm lô cần trả");
        title.setIcon(FontIcon.of(FontAwesomeSolid.PLUS_CIRCLE, 14, AppColor.ACCENT));
        title.setIconTextGap(8);
        titleRow.add(title, BorderLayout.WEST);
        supplierDebtHint = new JLabel(" ");
        supplierDebtHint.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        supplierDebtHint.setForeground(AppColor.TEXT_MUTED);
        titleRow.add(supplierDebtHint, BorderLayout.EAST);

        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.insets = new Insets(0, 0, 0, 10);
        gc.weightx = 1;

        batchCombo = styledCombo(new SupplierReturnDAO.ReturnableBatch[0]);
        batchCombo.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
            String text;
            if (value != null) {
                String exp = value.expiryDate != null ? value.expiryDate.toString() : "—";
                String receipt = value.receiptCode != null ? value.receiptCode : "—";
                text = value.batchCode + "  ·  " + value.productName
                        + "  ·  PN " + receipt + "  ·  còn " + value.remainingQty + "  ·  HSD " + exp;
            } else {
                text = "— Chọn lô hàng —";
            }
            return comboLabel(text, isSelected);
        });

        qtyField = styledField();
        qtyField.setPreferredSize(new Dimension(90, 38));
        priceField = styledField();
        priceField.setPreferredSize(new Dimension(120, 38));

        JLabel remainHint = new JLabel(" ");
        remainHint.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        remainHint.setForeground(AppColor.TEXT_MUTED);
        batchCombo.addActionListener(e -> {
            SupplierReturnDAO.ReturnableBatch b = (SupplierReturnDAO.ReturnableBatch) batchCombo.getSelectedItem();
            if (b == null) {
                remainHint.setText(" ");
                priceField.setText("");
            } else {
                remainHint.setText("Còn " + b.remainingQty
                        + "  ·  Giá nhập " + NumberUtil.formatThousands(b.importPrice.longValue()) + " đ");
                priceField.setText(String.valueOf(b.importPrice.longValue()));
            }
        });

        gc.gridx = 0; gc.gridy = 0; gc.weightx = 2.4;
        grid.add(fieldBlock("Lô hàng *", batchCombo), gc);
        gc.gridx = 1; gc.weightx = 0.5;
        grid.add(fieldBlock("Số lượng *", qtyField), gc);
        gc.gridx = 2; gc.weightx = 0.6;
        grid.add(fieldBlock("Đơn giá hoàn *", priceField), gc);
        gc.gridx = 3; gc.weightx = 0.7;
        JPanel btnWrap = new JPanel(new BorderLayout());
        btnWrap.setOpaque(false);
        btnWrap.add(fieldLabel(" "), BorderLayout.NORTH);
        JButton addBtn = accentButton("Thêm dòng", FontAwesomeSolid.PLUS);
        addBtn.setPreferredSize(new Dimension(130, 36));
        addBtn.addActionListener(e -> handleAddLine());
        btnWrap.add(addBtn, BorderLayout.CENTER);
        grid.add(btnWrap, gc);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        south.setOpaque(false);
        south.add(remainHint);

        JPanel center = new JPanel(new BorderLayout(0, 4));
        center.setOpaque(false);
        center.add(grid, BorderLayout.CENTER);
        center.add(south, BorderLayout.SOUTH);

        card.add(titleRow, BorderLayout.NORTH);
        card.add(center, BorderLayout.CENTER);
        return card;
    }

    private JPanel cardLinesTable() {
        JPanel card = roundedCard();
        card.setLayout(new BorderLayout(0, 10));
        card.setPreferredSize(new Dimension(0, 280));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 360));

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        JLabel title = sectionLabel("Danh sách lô trên phiếu");
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
        Color headerBg = new Color(30, 41, 59);
        header.setBackground(headerBg);
        header.setForeground(Color.WHITE);
        header.setPreferredSize(new Dimension(0, 36));
        header.setReorderingAllowed(false);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, softBorder()));
        header.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                JLabel lb = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                lb.setBackground(headerBg);
                lb.setForeground(Color.WHITE);
                lb.setFont(new Font("Segoe UI", Font.BOLD, 12));
                lb.setHorizontalAlignment(SwingConstants.CENTER);
                lb.setBorder(new EmptyBorder(0, 8, 0, 8));
                lb.setOpaque(true);
                return lb;
            }
        });

        int[] widths = {100, 90, 170, 90, 70, 100, 110, 50};
        for (int i = 0; i < widths.length && i < lineTable.getColumnCount(); i++) {
            lineTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        lineTable.getColumnModel().getColumn(7).setMaxWidth(56);

        DefaultTableCellRenderer left = cellRenderer(SwingConstants.LEFT);
        DefaultTableCellRenderer right = cellRenderer(SwingConstants.RIGHT);
        DefaultTableCellRenderer center = cellRenderer(SwingConstants.CENTER);
        lineTable.getColumnModel().getColumn(0).setCellRenderer(left);
        lineTable.getColumnModel().getColumn(1).setCellRenderer(center);
        lineTable.getColumnModel().getColumn(2).setCellRenderer(left);
        lineTable.getColumnModel().getColumn(3).setCellRenderer(center);
        lineTable.getColumnModel().getColumn(4).setCellRenderer(right);
        lineTable.getColumnModel().getColumn(5).setCellRenderer(right);
        lineTable.getColumnModel().getColumn(6).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setHorizontalAlignment(SwingConstants.RIGHT);
                setForeground(AppColor.SUCCESS);
                setFont(getFont().deriveFont(Font.BOLD));
                if (!isSelected) {
                    setBackground(row % 2 == 0 ? AppColor.WHITE : softStripe());
                }
                return c;
            }
        });
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

        emptyHint = new JLabel("Chưa có lô — chọn NCC rồi dùng form phía trên để thêm dòng", SwingConstants.CENTER);
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
        JLabel totalCap = new JLabel("TỔNG TIỀN HOÀN (NCC NỢ LẠI)");
        totalCap.setFont(new Font("Segoe UI", Font.BOLD, 11));
        totalCap.setForeground(AppColor.TEXT_MUTED);
        totalLabel = new JLabel("0 đ");
        totalLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        totalLabel.setForeground(AppColor.SUCCESS);
        totalBox.add(totalCap);
        totalBox.add(Box.createVerticalStrut(2));
        totalBox.add(totalLabel);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);
        JButton cancel = ghostButton("Hủy", FontAwesomeSolid.TIMES);
        cancel.addActionListener(e -> dispose());
        JButton save = accentButton("Lưu phiếu trả NCC", FontAwesomeSolid.SAVE);
        save.setPreferredSize(new Dimension(190, 40));
        save.addActionListener(e -> handleSave());
        actions.add(cancel);
        actions.add(save);

        footer.add(totalBox, BorderLayout.WEST);
        footer.add(actions, BorderLayout.EAST);
        getRootPane().setDefaultButton(save);
        return footer;
    }

    private void reloadBatchCombo() {
        Supplier s = (Supplier) supplierCombo.getSelectedItem();
        List<SupplierReturnDAO.ReturnableBatch> list = s != null
                ? returnDAO.listReturnableBatches(s.getSupplierId())
                : new ArrayList<>();
        batchCombo.setModel(new javax.swing.DefaultComboBoxModel<>(list.toArray(new SupplierReturnDAO.ReturnableBatch[0])));
        if (batchCombo.getItemCount() > 0) batchCombo.setSelectedIndex(0);
        supplierDebtHint.setText(s != null
                ? "NCC đang nợ: " + NumberUtil.formatThousands(returnDAO.getSupplierDebt(s.getSupplierId()).longValue()) + " đ"
                : " ");
    }

    private void handleAddLine() {
        Supplier supplier = (Supplier) supplierCombo.getSelectedItem();
        SupplierReturnDAO.ReturnableBatch b = (SupplierReturnDAO.ReturnableBatch) batchCombo.getSelectedItem();
        if (supplier == null || b == null) {
            BaseDialog.error(this, "Thiếu thông tin", "Vui lòng chọn nhà cung cấp và lô hàng.");
            return;
        }
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            if (tableModel.getRow(i).batch.batchId == b.batchId) {
                BaseDialog.error(this, "Trùng lô", "Lô này đã có trên phiếu. Xóa dòng cũ nếu muốn sửa SL.");
                return;
            }
        }
        int qty;
        try {
            qty = Integer.parseInt(qtyField.getText().trim().replaceAll("[^0-9]", ""));
            if (qty <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            BaseDialog.error(this, "Số lượng không hợp lệ", "Số lượng phải là số nguyên lớn hơn 0.");
            qtyField.requestFocus();
            return;
        }
        if (qty > b.remainingQty) {
            BaseDialog.error(this, "Vượt tồn lô", "Lô chỉ còn " + b.remainingQty + ".");
            qtyField.requestFocus();
            return;
        }
        BigDecimal price;
        try {
            price = new BigDecimal(priceField.getText().trim().replaceAll("[^0-9]", ""));
            if (price.signum() < 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            BaseDialog.error(this, "Đơn giá không hợp lệ", "Đơn giá hoàn phải là số ≥ 0.");
            priceField.requestFocus();
            return;
        }
        LineRow row = new LineRow();
        row.batch = b;
        row.quantity = qty;
        row.unitRefundPrice = price;
        tableModel.addRow(row);
        qtyField.setText("");
        refreshSummary();
        batchCombo.requestFocus();
    }

    private void handleSave() {
        Supplier supplier = (Supplier) supplierCombo.getSelectedItem();
        if (supplier == null) {
            BaseDialog.error(this, "Thiếu thông tin", "Vui lòng chọn nhà cung cấp.");
            return;
        }
        if (tableModel.getRowCount() == 0) {
            BaseDialog.error(this, "Chưa có dòng", "Thêm ít nhất 1 lô cần trả trước khi lưu phiếu.");
            return;
        }
        int reasonIdx = reasonCombo.getSelectedIndex();
        String reason = REASON_CODES[Math.max(0, reasonIdx)];
        String note = noteArea.getText() != null ? noteArea.getText().trim() : null;

        List<SupplierReturnDetail> details = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            LineRow r = tableModel.getRow(i);
            SupplierReturnDetail d = new SupplierReturnDetail();
            d.setBatchId(r.batch.batchId);
            d.setProductId(r.batch.productId);
            d.setQuantity(r.quantity);
            d.setUnitRefundPrice(r.unitRefundPrice);
            details.add(d);
            total = total.add(r.lineRefund());
        }

        boolean ok = BaseDialog.confirm(this, "Xác nhận trả hàng NCC",
                "Trả " + details.size() + " lô về " + supplier.getSupplierName() + "?\n"
                        + "Tổng tiền hoàn: " + NumberUtil.formatThousands(total.longValue()) + " đ\n"
                        + "Tồn kho sẽ bị trừ ngay, công nợ NCC sẽ được cộng dồn sau khi lưu.",
                "Lưu phiếu", AppColor.ACCENT,
                AppColor.ACCENT_HOVER != null ? AppColor.ACCENT_HOVER : AppColor.ACCENT,
                FontAwesomeSolid.UNDO);
        if (!ok) return;

        int userId = AuthService.getInstance().getCurrentUser().getUserId();
        int id = returnDAO.createSupplierReturn(supplier.getSupplierId(), reason, note, userId, details);
        if (id > 0) {
            BaseDialog.success(this, "Thành công",
                    "Đã lập phiếu TRNC_" + String.format("%06d", id)
                            + " (" + details.size() + " dòng). Tiền hoàn: "
                            + NumberUtil.formatThousands(total.longValue()) + " đ");
            if (onSaved != null) onSaved.accept(id, details.size());
            dispose();
        } else {
            BaseDialog.error(this, "Thất bại", "Không thể lập phiếu. Kiểm tra tồn lô / kết nối DB.");
        }
    }

    private void refreshSummary() {
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            total = total.add(tableModel.getRow(i).lineRefund());
        }
        totalLabel.setText(NumberUtil.formatThousands(total.longValue()) + " đ");
        int n = tableModel.getRowCount();
        lineCountLabel.setText(n + " dòng");
        emptyHint.setVisible(n == 0);
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
                BorderFactory.createLineBorder(softBorder(), 1),
                new EmptyBorder(4, 8, 4, 8)));
        return combo;
    }

    private JTextField styledField() {
        JTextField f = new JTextField();
        f.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(softBorder(), 1),
                new EmptyBorder(8, 10, 8, 10)));
        return f;
    }

    private JLabel comboLabel(String text, boolean selected) {
        JLabel lb = new JLabel(text);
        lb.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lb.setOpaque(true);
        lb.setBorder(new EmptyBorder(6, 10, 6, 10));
        if (selected) {
            lb.setBackground(AppColor.ACCENT_SELECTION_BG != null
                    ? AppColor.ACCENT_SELECTION_BG : new Color(209, 250, 229));
            lb.setForeground(AppColor.TEXT_PRIMARY);
        } else {
            lb.setBackground(AppColor.WHITE);
            lb.setForeground(AppColor.TEXT_PRIMARY);
        }
        return lb;
    }

    private JButton accentButton(String text, FontAwesomeSolid icon) {
        JButton btn = new JButton(text, FontIcon.of(icon, 13, Color.WHITE));
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setBackground(AppColor.ACCENT);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorder(new EmptyBorder(10, 16, 10, 16));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JButton ghostButton(String text, FontAwesomeSolid icon) {
        JButton btn = new JButton(text, FontIcon.of(icon, 12, AppColor.TEXT_MUTED));
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        btn.setBackground(AppColor.WHITE);
        btn.setForeground(AppColor.TEXT_PRIMARY);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(softBorder(), 1),
                new EmptyBorder(9, 14, 9, 14)));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private DefaultTableCellRenderer cellRenderer(int align) {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setHorizontalAlignment(align);
                if (!isSelected) {
                    setBackground(row % 2 == 0 ? AppColor.WHITE : softStripe());
                    setForeground(AppColor.TEXT_PRIMARY);
                }
                setBorder(new EmptyBorder(0, 10, 0, 10));
                return c;
            }
        };
    }

    private static Color softBorder() {
        return AppColor.BORDER != null ? AppColor.BORDER : new Color(226, 232, 240);
    }

    private static Color softStripe() {
        return AppColor.BG_LIGHTER != null ? AppColor.BG_LIGHTER : new Color(248, 250, 252);
    }

    private static final class LineRow {
        SupplierReturnDAO.ReturnableBatch batch;
        int quantity;
        BigDecimal unitRefundPrice;
        BigDecimal lineRefund() {
            return unitRefundPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }

    private static final class LineTableModel extends AbstractTableModel {
        private static final String[] COLS = {
                "Mã lô", "Phiếu nhập", "Sản phẩm", "HSD", "SL trả", "Đơn giá", "Hoàn tiền", ""
        };
        private final List<LineRow> rows = new ArrayList<>();

        void addRow(LineRow r) {
            rows.add(r);
            fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
        }

        void removeRow(int i) {
            if (i < 0 || i >= rows.size()) return;
            rows.remove(i);
            fireTableRowsDeleted(i, i);
        }

        void clear() {
            int n = rows.size();
            if (n == 0) return;
            rows.clear();
            fireTableRowsDeleted(0, n - 1);
        }

        LineRow getRow(int i) { return rows.get(i); }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return COLS.length; }
        @Override public String getColumnName(int c) { return COLS[c]; }
        @Override public boolean isCellEditable(int r, int c) { return false; }

        @Override
        public Object getValueAt(int r, int c) {
            LineRow row = rows.get(r);
            switch (c) {
                case 0: return row.batch.batchCode;
                case 1: return row.batch.receiptCode != null ? row.batch.receiptCode : "—";
                case 2: return row.batch.productName;
                case 3: return row.batch.expiryDate != null ? row.batch.expiryDate.toString() : "—";
                case 4: return row.quantity;
                case 5: return NumberUtil.formatThousands(row.unitRefundPrice.longValue());
                case 6: return NumberUtil.formatThousands(row.lineRefund().longValue());
                default: return "";
            }
        }
    }
}