package com.view.admin.inventory;

import com.components.BaseDialog;
import com.dao.StockDisposalDAO;
import com.model.InventoryBatch;
import com.model.StockDisposalDetail;
import com.service.AuthService;
import com.theme.AppColor;
import com.utils.NumberUtil;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
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
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Form lap phieu tieu huy nhieu dong theo lo.
 */
public class StockDisposalFormDialog extends JDialog {

    private static final String[] REASON_CODES = {"EXPIRED", "DAMAGED", "QUALITY", "OTHER"};
    private static final String[] REASON_LABELS = {"Hết hạn", "Hỏng / hư hỏng", "Chất lượng", "Khác"};

    private final StockDisposalDAO disposalDAO = new StockDisposalDAO();
    private final List<InventoryBatch> batches;

    private JComboBox<String> reasonCombo;
    private JTextArea noteArea;
    private JComboBox<InventoryBatch> batchCombo;
    private JTextField qtyField;
    private JLabel unitCostLabel;
    private JLabel remainLabel;

    private LineTableModel tableModel;
    private JTable lineTable;
    private JLabel totalLabel;
    private JLabel lineCountLabel;

    private BiConsumer<Integer, Integer> onSaved;

    public StockDisposalFormDialog(Frame owner) {
        super(owner, "Lập phiếu tiêu hủy", Dialog.ModalityType.APPLICATION_MODAL);
        this.batches = disposalDAO.listDisposableBatches();

        setSize(960, 680);
        setMinimumSize(new Dimension(860, 560));
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(AppColor.PAGE_BG != null ? AppColor.PAGE_BG : new Color(248, 250, 252));

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
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(AppColor.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, softBorder()),
                new EmptyBorder(18, 24, 16, 24)));
        JLabel title = new JLabel("Lập phiếu tiêu hủy hàng");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(AppColor.TEXT_PRIMARY);
        title.setIcon(FontIcon.of(FontAwesomeSolid.TRASH, 20, AppColor.ERROR));
        title.setIconTextGap(10);
        JLabel sub = new JLabel("Trừ lô theo FEFO / HSD, tính tổn thất = SL × giá nhập lô");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        sub.setForeground(AppColor.TEXT_MUTED);
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.add(title);
        titles.add(Box.createVerticalStrut(4));
        titles.add(sub);
        header.add(titles, BorderLayout.CENTER);
        return header;
    }

    private JComponent buildBody() {
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(14, 20, 8, 20));

        body.add(cardReason());
        body.add(Box.createVerticalStrut(10));
        body.add(cardAddLine());
        body.add(Box.createVerticalStrut(10));
        body.add(cardTable());

        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(getContentPane().getBackground());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private JPanel cardReason() {
        JPanel card = whiteCard();
        card.setLayout(new GridBagLayout());
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.insets = new Insets(0, 0, 0, 12);

        reasonCombo = new JComboBox<>(REASON_LABELS);
        reasonCombo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        reasonCombo.setPreferredSize(new Dimension(200, 34));

        noteArea = new JTextArea(2, 30);
        noteArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        noteArea.setLineWrap(true);
        noteArea.setWrapStyleWord(true);
        noteArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(softBorder(), 1),
                new EmptyBorder(6, 8, 6, 8)));
        JScrollPane noteScroll = new JScrollPane(noteArea);
        noteScroll.setPreferredSize(new Dimension(400, 52));

        gc.gridx = 0; gc.gridy = 0; gc.weightx = 0;
        card.add(label("Lý do *"), gc);
        gc.gridx = 1; gc.weightx = 0.3;
        card.add(reasonCombo, gc);
        gc.gridx = 2; gc.weightx = 0;
        card.add(label("Ghi chú"), gc);
        gc.gridx = 3; gc.weightx = 0.7;
        card.add(noteScroll, gc);
        return card;
    }

    private JPanel cardAddLine() {
        JPanel card = whiteCard();
        card.setLayout(new BorderLayout(0, 8));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));

        JLabel title = label("Thêm lô cần tiêu hủy");
        title.setFont(new Font("Segoe UI", Font.BOLD, 13));

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        row.setOpaque(false);

        batchCombo = new JComboBox<>(batches.toArray(new InventoryBatch[0]));
        batchCombo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        batchCombo.setPreferredSize(new Dimension(360, 34));
        batchCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
                                                          int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof InventoryBatch b) {
                    String exp = b.getExpiryDate() != null ? b.getExpiryDate().toString() : "—";
                    setText(b.getBatchCode() + " | " + b.getProductName()
                            + " | con " + b.getRemainingQty() + " | HSD " + exp);
                }
                return this;
            }
        });
        batchCombo.addActionListener(e -> syncBatchInfo());

        qtyField = new JTextField(6);
        qtyField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        qtyField.setPreferredSize(new Dimension(80, 34));

        unitCostLabel = new JLabel("Giá vốn: -");
        unitCostLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        unitCostLabel.setForeground(AppColor.TEXT_MUTED);
        remainLabel = new JLabel("Còn: -");
        remainLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        remainLabel.setForeground(AppColor.TEXT_MUTED);

        JButton addBtn = accentBtn("Thêm dòng", FontAwesomeSolid.PLUS);
        addBtn.addActionListener(e -> handleAddLine());

        row.add(batchCombo);
        row.add(label("SL"));
        row.add(qtyField);
        row.add(remainLabel);
        row.add(unitCostLabel);
        row.add(addBtn);

        card.add(title, BorderLayout.NORTH);
        card.add(row, BorderLayout.CENTER);
        syncBatchInfo();
        return card;
    }

    private JPanel cardTable() {
        JPanel card = whiteCard();
        card.setLayout(new BorderLayout(0, 8));
        card.setPreferredSize(new Dimension(0, 280));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JLabel title = label("Danh sách lô trên phiếu");
        title.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lineCountLabel = new JLabel("0 dòng");
        lineCountLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lineCountLabel.setForeground(AppColor.TEXT_MUTED);
        top.add(title, BorderLayout.WEST);
        top.add(lineCountLabel, BorderLayout.EAST);

        tableModel = new LineTableModel();
        lineTable = new JTable(tableModel);
        lineTable.setRowHeight(36);
        lineTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lineTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lineTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        lineTable.setFillsViewportHeight(true);

        int[] w = {110, 180, 90, 70, 100, 110, 50};
        for (int i = 0; i < w.length && i < lineTable.getColumnCount(); i++) {
            lineTable.getColumnModel().getColumn(i).setPreferredWidth(w[i]);
        }
        DefaultTableCellRenderer right = new DefaultTableCellRenderer();
        right.setHorizontalAlignment(SwingConstants.RIGHT);
        lineTable.getColumnModel().getColumn(3).setCellRenderer(right);
        lineTable.getColumnModel().getColumn(4).setCellRenderer(right);
        lineTable.getColumnModel().getColumn(5).setCellRenderer(right);
        lineTable.getColumnModel().getColumn(6).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                JLabel lb = new JLabel(FontIcon.of(FontAwesomeSolid.TRASH_ALT, 12, AppColor.ERROR));
                lb.setHorizontalAlignment(SwingConstants.CENTER);
                lb.setOpaque(true);
                lb.setBackground(isSelected ? table.getSelectionBackground() : AppColor.WHITE);
                return lb;
            }
        });
        lineTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (lineTable.columnAtPoint(e.getPoint()) == 6) {
                    int row = lineTable.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        tableModel.removeRow(row);
                        refreshSummary();
                    }
                }
            }
        });

        JScrollPane scroll = new JScrollPane(lineTable);
        scroll.setBorder(BorderFactory.createLineBorder(softBorder(), 1));
        card.add(top, BorderLayout.NORTH);
        card.add(scroll, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(AppColor.WHITE);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, softBorder()),
                new EmptyBorder(12, 24, 14, 24)));
        totalLabel = new JLabel("Tổng tổn thất: 0 đ");
        totalLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        totalLabel.setForeground(AppColor.ERROR);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        JButton cancel = ghostBtn("Hủy");
        cancel.addActionListener(e -> dispose());
        JButton save = accentBtn("Lưu phiếu tiêu hủy", FontAwesomeSolid.SAVE);
        save.addActionListener(e -> handleSave());
        actions.add(cancel);
        actions.add(save);

        footer.add(totalLabel, BorderLayout.WEST);
        footer.add(actions, BorderLayout.EAST);
        getRootPane().setDefaultButton(save);
        return footer;
    }

    private void syncBatchInfo() {
        InventoryBatch b = (InventoryBatch) batchCombo.getSelectedItem();
        if (b == null) {
            remainLabel.setText("Còn: -");
            unitCostLabel.setText("Giá vốn: -");
            return;
        }
        remainLabel.setText("Còn: " + b.getRemainingQty());
        unitCostLabel.setText("Giá vốn: " + NumberUtil.formatThousands(b.getImportPrice().longValue()) + " đ");
    }

    private void handleAddLine() {
        InventoryBatch b = (InventoryBatch) batchCombo.getSelectedItem();
        if (b == null) {
            BaseDialog.error(this, "Thiếu thông tin", "Vui lòng chọn lô hàng.");
            return;
        }
        // Khong trung batch tren phieu
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            if (tableModel.getRow(i).batch.getBatchId() == b.getBatchId()) {
                BaseDialog.error(this, "Trùng lô", "Lô này đã có trên phiếu. Xóa dòng cũ nếu muốn sửa SL.");
                return;
            }
        }
        int qty;
        try {
            qty = Integer.parseInt(qtyField.getText().trim().replaceAll("[^0-9]", ""));
            if (qty <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            BaseDialog.error(this, "SL không hợp lệ", "Nhập số lượng nguyên > 0.");
            return;
        }
        if (qty > b.getRemainingQty()) {
            BaseDialog.error(this, "Vượt tồn lô", "Lô chỉ còn " + b.getRemainingQty() + ".");
            return;
        }
        LineRow row = new LineRow();
        row.batch = b;
        row.quantity = qty;
        row.unitCost = b.getImportPrice();
        tableModel.addRow(row);
        qtyField.setText("");
        refreshSummary();
    }

    private void handleSave() {
        if (tableModel.getRowCount() == 0) {
            BaseDialog.error(this, "Chưa có dòng", "Thêm ít nhất 1 lô cần tiêu hủy.");
            return;
        }
        int reasonIdx = reasonCombo.getSelectedIndex();
        String reason = REASON_CODES[Math.max(0, reasonIdx)];
        String note = noteArea.getText() != null ? noteArea.getText().trim() : null;

        List<StockDisposalDetail> details = new ArrayList<>();
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            LineRow r = tableModel.getRow(i);
            StockDisposalDetail d = new StockDisposalDetail();
            d.setBatchId(r.batch.getBatchId());
            d.setProductId(r.batch.getProductId());
            d.setQuantity(r.quantity);
            d.setUnitCost(r.unitCost);
            details.add(d);
        }

        BigDecimal total = BigDecimal.ZERO;
        for (StockDisposalDetail d : details) {
            total = total.add(d.getUnitCost().multiply(BigDecimal.valueOf(d.getQuantity())));
        }

        boolean ok = BaseDialog.confirm(this, "Xác nhận tiêu hủy",
                "Tiêu hủy " + details.size() + " lô?\nTổng tổn thất ước tính: "
                        + NumberUtil.formatThousands(total.longValue()) + " đ\n"
                        + "Tồn kho sẽ bị trừ ngay sau khi lưu.",
                "Lưu phiếu", AppColor.ERROR, AppColor.ERROR, FontAwesomeSolid.TRASH);
        if (!ok) return;

        int userId = AuthService.getInstance().getCurrentUser().getUserId();
        int id = disposalDAO.createDisposal(reason, note, userId, details);
        if (id > 0) {
            BaseDialog.success(this, "Thành công",
                    "Đã lập phiếu TH_" + String.format("%06d", id)
                            + ". Tổn thất: " + NumberUtil.formatThousands(total.longValue()) + " đ");
            if (onSaved != null) onSaved.accept(id, details.size());
            dispose();
        } else {
            BaseDialog.error(this, "Thất bại", "Không thể lập phiếu. Kiểm tra tồn lô / kết nối DB.");
        }
    }

    private void refreshSummary() {
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            total = total.add(tableModel.getRow(i).lineLoss());
        }
        totalLabel.setText("Tổng tổn thất: " + NumberUtil.formatThousands(total.longValue()) + " đ");
        lineCountLabel.setText(tableModel.getRowCount() + " dòng");
    }

    private JPanel whiteCard() {
        JPanel p = new JPanel();
        p.setBackground(AppColor.WHITE);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(softBorder(), 1),
                new EmptyBorder(12, 14, 12, 14)));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    private JLabel label(String t) {
        JLabel lb = new JLabel(t);
        lb.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lb.setForeground(AppColor.TEXT_MUTED);
        return lb;
    }

    private JButton accentBtn(String text, FontAwesomeSolid icon) {
        JButton btn = new JButton(text, FontIcon.of(icon, 12, Color.WHITE));
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setBackground(AppColor.ACCENT);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorder(new EmptyBorder(8, 14, 8, 14));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JButton ghostBtn(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btn.setBackground(AppColor.WHITE);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(softBorder(), 1),
                new EmptyBorder(7, 14, 7, 14)));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private static Color softBorder() {
        return AppColor.BORDER != null ? AppColor.BORDER : new Color(226, 232, 240);
    }

    private static final class LineRow {
        InventoryBatch batch;
        int quantity;
        BigDecimal unitCost;
        BigDecimal lineLoss() {
            return unitCost.multiply(BigDecimal.valueOf(quantity));
        }
    }

    private static final class LineTableModel extends AbstractTableModel {
        private static final String[] COLS = {"Mã lô", "Sản phẩm", "HSD", "SL hủy", "Giá vốn", "Tổn thất", ""};
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

        LineRow getRow(int i) { return rows.get(i); }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return COLS.length; }
        @Override public String getColumnName(int c) { return COLS[c]; }
        @Override public boolean isCellEditable(int r, int c) { return false; }

        @Override
        public Object getValueAt(int r, int c) {
            LineRow row = rows.get(r);
            switch (c) {
                case 0: return row.batch.getBatchCode();
                case 1: return row.batch.getProductName();
                case 2: return row.batch.getExpiryDate() != null ? row.batch.getExpiryDate().toString() : "—";
                case 3: return row.quantity;
                case 4: return NumberUtil.formatThousands(row.unitCost.longValue());
                case 5: return NumberUtil.formatThousands(row.lineLoss().longValue());
                default: return "";
            }
        }
    }
}