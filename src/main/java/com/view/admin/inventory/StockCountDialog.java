package com.view.admin.inventory;

import com.components.BaseDialog;
import com.dao.StockReconciliationDAO;
import com.model.Product;
import com.model.StockReconciliation;
import com.theme.AppColor;
import com.theme.AppFont;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Dialog "Kiểm kê kho cuối ngày": liệt kê TOÀN BỘ sản phẩm đang bán, mỗi
 * dòng đã điền sẵn tồn hệ thống (Products.Stock) vào cột "Tồn thực tế" -
 * nhân viên kho chỉ cần sửa lại những sản phẩm có chênh lệch so với số đếm
 * thực tế. Khi lưu, TOÀN BỘ danh sách (không chỉ dòng bị sửa) được ghi
 * thành 1 phiên đối chiếu trong sql/Trigger_SIMS.sql
 * (trg_StockReconciliation_Apply) - vừa đúng tinh thần "kiểm kê", vừa để
 * lại đầy đủ chứng từ đối chiếu cho mọi sản phẩm trong ngày.
 */
public class StockCountDialog extends JDialog {

    private static final int COL_CODE = 0;
    private static final int COL_NAME = 1;
    private static final int COL_SYSTEM = 2;
    private static final int COL_ACTUAL = 3;
    private static final int COL_NOTE = 4;

    private final StockReconciliationDAO reconciliationDAO;
    private final int currentUserId;
    private final List<Product> products;

    private DefaultTableModel model;
    private JTable table;
    private JLabel summaryLabel;
    private JButton saveButton;
    private Runnable onSaved;

    public StockCountDialog(java.awt.Frame owner, List<Product> products,
                             StockReconciliationDAO reconciliationDAO, int currentUserId) {
        super(owner, "Kiểm kê kho cuối ngày", Dialog.ModalityType.APPLICATION_MODAL);
        this.products = products;
        this.reconciliationDAO = reconciliationDAO;
        this.currentUserId = currentUserId;

        setSize(760, 620);
        setMinimumSize(new Dimension(640, 460));
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(AppColor.WHITE);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        setLocationRelativeTo(owner);
        updateSummary();
    }

    public void onSaved(Runnable callback) {
        this.onSaved = callback;
    }

    // ---------------------------------------------------------------
    // Header
    // ---------------------------------------------------------------

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(14, 0));
        header.setBackground(AppColor.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AppColor.BORDER),
                new javax.swing.border.EmptyBorder(18, 24, 18, 24)));

        FontIcon icon = FontIcon.of(FontAwesomeSolid.BALANCE_SCALE, 18);
        icon.setIconColor(AppColor.ACCENT);
        JLabel iconBadge = new JLabel(icon, SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(AppColor.ACCENT_BG_SOFT);
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        iconBadge.setPreferredSize(new Dimension(44, 44));

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel("Kiểm kê kho cuối ngày");
        titleLabel.setFont(AppFont.DIALOG_TITLE);
        titleLabel.setForeground(AppColor.TEXT_PRIMARY);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitleLabel = new JLabel(
                "Nhập số lượng đếm thực tế - chỉ cần sửa những dòng có chênh lệch");
        subtitleLabel.setFont(AppFont.BODY);
        subtitleLabel.setForeground(AppColor.TEXT_MUTED);
        subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        titleBox.add(titleLabel);
        titleBox.add(Box.createVerticalStrut(2));
        titleBox.add(subtitleLabel);

        JTextField searchField = new JTextField();
        searchField.setFont(AppFont.BODY);
        searchField.putClientProperty("JTextField.placeholderText", "Tìm theo tên/mã sản phẩm...");
        searchField.setPreferredSize(new Dimension(220, 34));

        header.add(iconBadge, BorderLayout.WEST);
        header.add(titleBox, BorderLayout.CENTER);
        header.add(searchField, BorderLayout.EAST);

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { filter(); }
            @Override public void removeUpdate(DocumentEvent e) { filter(); }
            @Override public void changedUpdate(DocumentEvent e) { filter(); }

            private void filter() {
                String text = searchField.getText().trim();
                TableRowSorter<?> sorter = (TableRowSorter<?>) table.getRowSorter();
                if (sorter == null) return;
                if (text.isEmpty()) {
                    sorter.setRowFilter(null);
                } else {
                    sorter.setRowFilter(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(text),
                            COL_CODE, COL_NAME));
                }
            }
        });

        return header;
    }

    // ---------------------------------------------------------------
    // Body: bang kiem ke
    // ---------------------------------------------------------------

    private JPanel buildBody() {
        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(AppColor.WHITE);
        content.setBorder(new javax.swing.border.EmptyBorder(16, 24, 16, 24));

        String[] columns = {"Mã SP", "Sản phẩm", "Tồn hệ thống", "Tồn thực tế", "Ghi chú"};
        model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == COL_ACTUAL || column == COL_NOTE;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == COL_SYSTEM || columnIndex == COL_ACTUAL ? Integer.class : String.class;
            }
        };

        for (Product p : products) {
            model.addRow(new Object[]{p.getProductCode(), p.getProductName(), p.getStock(), p.getStock(), ""});
        }

        model.addTableModelListener(e -> updateSummary());

        table = new JTable(model);
        table.setFont(AppFont.BODY);
        table.setRowHeight(30);
        table.getTableHeader().setFont(AppFont.SMALL_BOLD);
        table.getTableHeader().setBackground(AppColor.BG_LIGHT);
        table.getTableHeader().setForeground(AppColor.TEXT_MUTED);
        table.setGridColor(AppColor.BORDER);
        table.setShowVerticalLines(false);
        table.setFillsViewportHeight(true);
        table.setRowSelectionAllowed(false);
        table.setAutoCreateRowSorter(false);
        table.setRowSorter(new TableRowSorter<>(model));
        table.getColumnModel().getColumn(COL_CODE).setPreferredWidth(80);
        table.getColumnModel().getColumn(COL_NAME).setPreferredWidth(230);
        table.getColumnModel().getColumn(COL_SYSTEM).setPreferredWidth(90);
        table.getColumnModel().getColumn(COL_ACTUAL).setPreferredWidth(90);
        table.getColumnModel().getColumn(COL_NOTE).setPreferredWidth(160);

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(BorderFactory.createLineBorder(AppColor.BORDER, 1, true));

        content.add(tableScroll, BorderLayout.CENTER);

        summaryLabel = new JLabel();
        summaryLabel.setFont(AppFont.SMALL_BOLD);
        summaryLabel.setForeground(AppColor.TEXT_MUTED);
        summaryLabel.setBorder(new javax.swing.border.EmptyBorder(10, 2, 0, 0));
        content.add(summaryLabel, BorderLayout.SOUTH);

        return content;
    }

    private void updateSummary() {
        if (model == null || summaryLabel == null) return;
        int total = model.getRowCount();
        int diffCount = 0;
        for (int i = 0; i < total; i++) {
            Object systemVal = model.getValueAt(i, COL_SYSTEM);
            Object actualVal = model.getValueAt(i, COL_ACTUAL);
            int sys = systemVal instanceof Integer ? (Integer) systemVal : 0;
            int act = actualVal instanceof Integer ? (Integer) actualVal : sys;
            if (act != sys) diffCount++;
        }
        summaryLabel.setForeground(diffCount > 0 ? AppColor.WARNING : AppColor.TEXT_MUTED);
        summaryLabel.setText(total + " sản phẩm - " + diffCount + " sản phẩm có chênh lệch tồn kho");
    }

    // ---------------------------------------------------------------
    // Footer
    // ---------------------------------------------------------------

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        footer.setBackground(AppColor.BG_LIGHT);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, AppColor.BORDER),
                new javax.swing.border.EmptyBorder(12, 24, 12, 24)));

        JButton cancelButton = new JButton("Hủy");
        cancelButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        cancelButton.setFocusPainted(false);
        cancelButton.setBackground(AppColor.BORDER);
        cancelButton.setForeground(AppColor.TEXT_PRIMARY);
        cancelButton.setBorder(new javax.swing.border.EmptyBorder(8, 18, 8, 18));
        cancelButton.addActionListener(e -> dispose());

        saveButton = new JButton("Lưu phiên kiểm kê");
        saveButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        saveButton.setFocusPainted(false);
        saveButton.setBackground(AppColor.ACCENT);
        saveButton.setForeground(AppColor.WHITE);
        saveButton.setBorder(new javax.swing.border.EmptyBorder(8, 18, 8, 18));
        saveButton.addActionListener(e -> handleSave());

        footer.add(cancelButton);
        footer.add(saveButton);
        getRootPane().setDefaultButton(saveButton);
        return footer;
    }

    // ---------------------------------------------------------------
    // Luu
    // ---------------------------------------------------------------

    private void handleSave() {
        // Dam bao moi edit dang go do (JTable cell editor) duoc chot lai vao model truoc khi doc.
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }

        List<StockReconciliation> rows = new ArrayList<>();
        int diffCount = 0;
        for (int i = 0; i < model.getRowCount(); i++) {
            Object actualVal = model.getValueAt(i, COL_ACTUAL);
            if (!(actualVal instanceof Integer) || (Integer) actualVal < 0) {
                BaseDialog.error(this, "Dữ liệu không hợp lệ",
                        "Tồn thực tế của \"" + model.getValueAt(i, COL_NAME) + "\" không hợp lệ. "
                                + "Vui lòng nhập số nguyên không âm.");
                return;
            }
            int system = (Integer) model.getValueAt(i, COL_SYSTEM);
            int actual = (Integer) actualVal;
            Object noteVal = model.getValueAt(i, COL_NOTE);

            StockReconciliation r = new StockReconciliation();
            r.setProductId(products.get(i).getProductId());
            r.setSystemStock(system);
            r.setActualStock(actual);
            r.setNote(noteVal == null ? null : noteVal.toString());
            rows.add(r);

            if (actual != system) diffCount++;
        }

        boolean confirmed = BaseDialog.confirm(this, "Xác nhận lưu phiên kiểm kê",
                "Lưu kết quả kiểm kê cho " + rows.size() + " sản phẩm, trong đó "
                        + diffCount + " sản phẩm có chênh lệch tồn kho?\n"
                        + "Tồn kho hệ thống sẽ được cập nhật lại theo số đếm thực tế.");
        if (!confirmed) return;

        saveButton.setEnabled(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            @Override
            protected Boolean doInBackground() {
                return reconciliationDAO.saveSession(rows, currentUserId);
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                saveButton.setEnabled(true);
                boolean success;
                try {
                    success = get();
                } catch (Exception ex) {
                    success = false;
                }
                if (success) {
                    BaseDialog.success(StockCountDialog.this, "Thành công",
                            "Đã lưu phiên kiểm kê kho cuối ngày.");
                    if (onSaved != null) onSaved.run();
                    dispose();
                } else {
                    BaseDialog.error(StockCountDialog.this, "Lỗi",
                            "Không thể lưu phiên kiểm kê. Vui lòng thử lại.");
                }
            }
        };
        worker.execute();
    }
}