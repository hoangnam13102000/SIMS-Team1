package com.view.admin.inventory;

import com.components.BaseDialog;
import com.dao.StockReconciliationDAO;
import com.model.Product;
import com.model.StockReconciliation;
import com.theme.AppColor;
import com.theme.AppFont;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.AbstractCellEditor;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
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
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;

/**
 * Dialog "Kiểm kê kho cuối ngày": liệt kê TOÀN BỘ sản phẩm đang bán, mỗi
 * dòng đã điền sẵn tồn hệ thống (Products.Stock) vào cột "Tồn thực tế" —
 * nhân viên kho chỉ cần sửa lại những sản phẩm có chênh lệch so với số đếm
 * thực tế. Khi lưu, TOÀN BỘ danh sách (không chỉ dòng bị sửa) được ghi
 * thành 1 phiên đối chiếu trong sql/Trigger_SIMS.sql
 * (trg_StockReconciliation_Apply).
 *
 * UI đồng bộ với các form inventory khác (StockDisposal / PurchaseReceipt):
 * header icon bo tròn, bảng zebra + highlight chênh lệch, summary badge,
 * nút ghost / accent.
 */
public class StockCountDialog extends JDialog {

    private static final int COL_CODE = 0;
    private static final int COL_NAME = 1;
    private static final int COL_SYSTEM = 2;
    private static final int COL_ACTUAL = 3;
    private static final int COL_DIFF = 4;
    private static final int COL_NOTE = 5;

    private final StockReconciliationDAO reconciliationDAO;
    private final int currentUserId;
    private final List<Product> products;

    private DefaultTableModel model;
    private JTable table;
    private TableRowSorter<DefaultTableModel> sorter;
    private JLabel summaryTotalLabel;
    private JLabel summaryDiffLabel;
    private JButton saveButton;
    private JTextField searchField;
    private JCheckBox onlyDiffCheck;
    private Runnable onSaved;

    public StockCountDialog(java.awt.Frame owner, List<Product> products,
                            StockReconciliationDAO reconciliationDAO, int currentUserId) {
        super(owner, "Kiểm kê kho cuối ngày", Dialog.ModalityType.APPLICATION_MODAL);
        this.products = products;
        this.reconciliationDAO = reconciliationDAO;
        this.currentUserId = currentUserId;

        setSize(880, 680);
        setMinimumSize(new Dimension(720, 520));
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
        JPanel header = new JPanel(new BorderLayout(16, 0));
        header.setBackground(AppColor.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, softBorder()),
                new EmptyBorder(20, 28, 18, 28)));

        // Icon badge bo tròn – đồng bộ StockDisposal / PurchaseReceipt
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
        JLabel icon = new JLabel(FontIcon.of(FontAwesomeSolid.BALANCE_SCALE, 22, AppColor.ACCENT));
        icon.setHorizontalAlignment(SwingConstants.CENTER);
        iconBox.add(icon, BorderLayout.CENTER);

        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Kiểm kê kho cuối ngày");
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setForeground(AppColor.TEXT_TITLE != null ? AppColor.TEXT_TITLE : AppColor.TEXT_PRIMARY);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel sub = new JLabel("Nhập số lượng đếm thực tế — chỉ cần sửa những dòng có chênh lệch");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        sub.setForeground(AppColor.TEXT_MUTED);
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);

        titles.add(title);
        titles.add(Box.createVerticalStrut(4));
        titles.add(sub);

        // Search + filter
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        tools.setOpaque(false);

        searchField = new JTextField();
        searchField.setFont(AppFont.BODY);
        searchField.putClientProperty("JTextField.placeholderText", "Tìm tên / mã sản phẩm...");
        searchField.setPreferredSize(new Dimension(210, 36));
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(softBorder(), 1),
                new EmptyBorder(6, 12, 6, 12)));

        onlyDiffCheck = new JCheckBox("Chỉ hiện chênh lệch");
        onlyDiffCheck.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        onlyDiffCheck.setForeground(AppColor.TEXT_SECONDARY);
        onlyDiffCheck.setOpaque(false);
        onlyDiffCheck.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        onlyDiffCheck.setFocusPainted(false);

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void removeUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });
        onlyDiffCheck.addActionListener(e -> applyFilter());

        tools.add(onlyDiffCheck);
        tools.add(searchField);

        header.add(iconBox, BorderLayout.WEST);
        header.add(titles, BorderLayout.CENTER);
        header.add(tools, BorderLayout.EAST);
        return header;
    }

    private void applyFilter() {
        if (sorter == null) return;
        String text = searchField.getText().trim();
        boolean onlyDiff = onlyDiffCheck.isSelected();

        sorter.setRowFilter(new RowFilter<DefaultTableModel, Integer>() {
            @Override
            public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                if (onlyDiff) {
                    Object diffVal = entry.getValue(COL_DIFF);
                    int diff = diffVal instanceof Integer ? (Integer) diffVal : 0;
                    if (diff == 0) return false;
                }
                if (text.isEmpty()) return true;
                String code = String.valueOf(entry.getValue(COL_CODE)).toLowerCase();
                String name = String.valueOf(entry.getValue(COL_NAME)).toLowerCase();
                String q = text.toLowerCase();
                return code.contains(q) || name.contains(q);
            }
        });
    }

    // ---------------------------------------------------------------
    // Body: bảng kiểm kê
    // ---------------------------------------------------------------

    private JPanel buildBody() {
        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBackground(AppColor.WHITE);
        content.setBorder(new EmptyBorder(16, 28, 8, 28));

        String[] columns = {"Mã SP", "Sản phẩm", "Tồn hệ thống", "Tồn thực tế", "Chênh lệch", "Ghi chú"};
        model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == COL_ACTUAL || column == COL_NOTE;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == COL_SYSTEM || columnIndex == COL_ACTUAL || columnIndex == COL_DIFF) {
                    return Integer.class;
                }
                return String.class;
            }
        };

        for (Product p : products) {
            int stock = p.getStock();
            model.addRow(new Object[]{
                    p.getProductCode(),
                    p.getProductName(),
                    stock,
                    stock,
                    0,
                    ""
            });
        }

        model.addTableModelListener(e -> {
            if (e.getColumn() == COL_ACTUAL || e.getColumn() == -1) {
                recalcDiffs();
            }
            updateSummary();
            // Re-apply filter nếu đang bật "chỉ hiện chênh lệch"
            if (onlyDiffCheck != null && onlyDiffCheck.isSelected()) {
                applyFilter();
            }
        });

        table = new JTable(model);
        table.setFont(AppFont.BODY);
        table.setRowHeight(34);
        table.setGridColor(softBorder());
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setFillsViewportHeight(true);
        table.setRowSelectionAllowed(false);
        table.setAutoCreateRowSorter(false);
        table.setIntercellSpacing(new Dimension(0, 0));

        sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);

        // Column widths
        table.getColumnModel().getColumn(COL_CODE).setPreferredWidth(88);
        table.getColumnModel().getColumn(COL_NAME).setPreferredWidth(220);
        table.getColumnModel().getColumn(COL_SYSTEM).setPreferredWidth(100);
        table.getColumnModel().getColumn(COL_ACTUAL).setPreferredWidth(100);
        table.getColumnModel().getColumn(COL_DIFF).setPreferredWidth(90);
        table.getColumnModel().getColumn(COL_NOTE).setPreferredWidth(180);

        // Header style
        JTableHeader header = table.getTableHeader();
        header.setFont(new Font("Segoe UI", Font.BOLD, 12));
        header.setPreferredSize(new Dimension(header.getWidth(), 38));
        header.setReorderingAllowed(false);
        header.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                JLabel lb = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                lb.setFont(new Font("Segoe UI", Font.BOLD, 12));
                lb.setBackground(AppColor.BG_LIGHT != null ? AppColor.BG_LIGHT : new Color(248, 250, 252));
                lb.setForeground(AppColor.TEXT_MUTED);
                lb.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 0, softBorder()),
                        new EmptyBorder(0, 10, 0, 10)));
                lb.setHorizontalAlignment(column == COL_CODE || column == COL_NAME || column == COL_NOTE
                        ? SwingConstants.LEFT : SwingConstants.CENTER);
                return lb;
            }
        });

        // Cell renderers
        table.getColumnModel().getColumn(COL_CODE).setCellRenderer(textRenderer(SwingConstants.LEFT));
        table.getColumnModel().getColumn(COL_NAME).setCellRenderer(textRenderer(SwingConstants.LEFT));
        table.getColumnModel().getColumn(COL_SYSTEM).setCellRenderer(numberRenderer(false));
        table.getColumnModel().getColumn(COL_ACTUAL).setCellRenderer(numberRenderer(true));
        table.getColumnModel().getColumn(COL_DIFF).setCellRenderer(diffRenderer());
        table.getColumnModel().getColumn(COL_NOTE).setCellRenderer(textRenderer(SwingConstants.LEFT));

        // Cell editors — bắt buộc để sửa được mượt (Integer mặc định của Swing rất khó tính)
        table.getColumnModel().getColumn(COL_ACTUAL).setCellEditor(new IntegerCellEditor());
        table.getColumnModel().getColumn(COL_NOTE).setCellEditor(new DefaultCellEditor(new JTextField()) {{
            ((JTextField) getComponent()).setBorder(new EmptyBorder(4, 8, 4, 8));
            ((JTextField) getComponent()).setFont(AppFont.BODY);
        }});
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        table.setSurrendersFocusOnKeystroke(true);

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(BorderFactory.createLineBorder(softBorder(), 1));
        tableScroll.getViewport().setBackground(AppColor.WHITE);
        tableScroll.setBackground(AppColor.WHITE);

        content.add(tableScroll, BorderLayout.CENTER);
        content.add(buildSummaryBar(), BorderLayout.SOUTH);
        return content;
    }

    private void recalcDiffs() {
        for (int i = 0; i < model.getRowCount(); i++) {
            Object sysVal = model.getValueAt(i, COL_SYSTEM);
            Object actVal = model.getValueAt(i, COL_ACTUAL);
            int sys = sysVal instanceof Integer ? (Integer) sysVal : 0;
            int act = actVal instanceof Integer ? (Integer) actVal : sys;
            model.setValueAt(act - sys, i, COL_DIFF);
        }
    }

    private JPanel buildSummaryBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        bar.setOpaque(false);
        bar.setBorder(new EmptyBorder(4, 0, 0, 0));

        summaryTotalLabel = chipLabel("0 sản phẩm", AppColor.BG_LIGHT, AppColor.TEXT_SECONDARY);
        summaryDiffLabel = chipLabel("0 chênh lệch", AppColor.SUCCESS_BG, AppColor.SUCCESS);

        bar.add(summaryTotalLabel);
        bar.add(summaryDiffLabel);
        return bar;
    }

    private JLabel chipLabel(String text, Color bg, Color fg) {
        JLabel lb = new JLabel(text);
        lb.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lb.setForeground(fg != null ? fg : AppColor.TEXT_MUTED);
        lb.setOpaque(true);
        lb.setBackground(bg != null ? bg : softStripe());
        lb.setBorder(new EmptyBorder(6, 12, 6, 12));
        return lb;
    }

    private void updateSummary() {
        if (model == null || summaryTotalLabel == null) return;
        int total = model.getRowCount();
        int diffCount = 0;
        for (int i = 0; i < total; i++) {
            Object diffVal = model.getValueAt(i, COL_DIFF);
            int diff = diffVal instanceof Integer ? (Integer) diffVal : 0;
            if (diff != 0) diffCount++;
        }

        summaryTotalLabel.setText(total + " sản phẩm");
        summaryTotalLabel.setBackground(AppColor.BG_LIGHT != null ? AppColor.BG_LIGHT : softStripe());
        summaryTotalLabel.setForeground(AppColor.TEXT_SECONDARY);

        if (diffCount > 0) {
            summaryDiffLabel.setText(diffCount + " sản phẩm có chênh lệch");
            summaryDiffLabel.setBackground(AppColor.WARNING_BG != null ? AppColor.WARNING_BG : new Color(255, 251, 235));
            summaryDiffLabel.setForeground(AppColor.WARNING != null ? AppColor.WARNING : new Color(180, 83, 9));
        } else {
            summaryDiffLabel.setText("Không có chênh lệch");
            summaryDiffLabel.setBackground(AppColor.SUCCESS_BG != null ? AppColor.SUCCESS_BG : new Color(236, 253, 245));
            summaryDiffLabel.setForeground(AppColor.SUCCESS != null ? AppColor.SUCCESS : new Color(21, 128, 61));
        }
    }

    // ---------------------------------------------------------------
    // Cell renderers
    // ---------------------------------------------------------------

    private DefaultTableCellRenderer textRenderer(int align) {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setHorizontalAlignment(align);
                applyRowStyle(c, table, row, isSelected);
                setBorder(new EmptyBorder(0, 10, 0, 10));
                return c;
            }
        };
    }

    private DefaultTableCellRenderer numberRenderer(boolean editableHint) {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setHorizontalAlignment(SwingConstants.CENTER);
                applyRowStyle(c, table, row, isSelected);
                if (editableHint && !isSelected) {
                    // Gợi ý ô có thể sửa
                    setForeground(AppColor.TEXT_PRIMARY);
                    setFont(new Font("Segoe UI", Font.BOLD, 13));
                } else {
                    setFont(AppFont.BODY);
                }
                setBorder(new EmptyBorder(0, 8, 0, 8));
                return c;
            }
        };
    }

    private DefaultTableCellRenderer diffRenderer() {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                JLabel lb = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setHorizontalAlignment(SwingConstants.CENTER);
                applyRowStyle(lb, table, row, isSelected);

                int diff = value instanceof Integer ? (Integer) value : 0;
                if (diff > 0) {
                    lb.setText("+" + diff);
                    lb.setForeground(AppColor.SUCCESS != null ? AppColor.SUCCESS : new Color(21, 128, 61));
                    lb.setFont(new Font("Segoe UI", Font.BOLD, 13));
                } else if (diff < 0) {
                    lb.setText(String.valueOf(diff));
                    lb.setForeground(AppColor.ERROR != null ? AppColor.ERROR : new Color(220, 38, 38));
                    lb.setFont(new Font("Segoe UI", Font.BOLD, 13));
                } else {
                    lb.setText("—");
                    lb.setForeground(AppColor.TEXT_MUTED);
                    lb.setFont(AppFont.BODY);
                }
                setBorder(new EmptyBorder(0, 8, 0, 8));
                return lb;
            }
        };
    }

    private void applyRowStyle(Component c, JTable table, int viewRow, boolean isSelected) {
        if (isSelected) return;
        int modelRow = table.convertRowIndexToModel(viewRow);
        Object diffVal = model.getValueAt(modelRow, COL_DIFF);
        int diff = diffVal instanceof Integer ? (Integer) diffVal : 0;

        if (diff != 0) {
            // Hàng có chênh lệch – nền warning nhẹ
            c.setBackground(AppColor.WARNING_BG != null ? AppColor.WARNING_BG : new Color(255, 251, 235));
            c.setForeground(AppColor.TEXT_PRIMARY);
        } else {
            c.setBackground(viewRow % 2 == 0 ? AppColor.WHITE : softStripe());
            c.setForeground(AppColor.TEXT_PRIMARY);
        }
    }

    // ---------------------------------------------------------------
    // Footer
    // ---------------------------------------------------------------

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(AppColor.BG_LIGHT != null ? AppColor.BG_LIGHT : new Color(248, 250, 252));
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, softBorder()),
                new EmptyBorder(14, 28, 16, 28)));

        JLabel hint = new JLabel("Tip: chỉ sửa cột «Tồn thực tế» khi số đếm khác tồn hệ thống");
        hint.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        hint.setForeground(AppColor.TEXT_MUTED);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);

        JButton cancelButton = ghostButton("Hủy");
        cancelButton.addActionListener(e -> dispose());

        saveButton = accentButton("Lưu phiên kiểm kê", FontAwesomeSolid.SAVE);
        saveButton.addActionListener(e -> handleSave());

        actions.add(cancelButton);
        actions.add(saveButton);

        footer.add(hint, BorderLayout.WEST);
        footer.add(actions, BorderLayout.EAST);
        getRootPane().setDefaultButton(saveButton);
        return footer;
    }

    private JButton accentButton(String text, FontAwesomeSolid icon) {
        JButton btn = new JButton(text, FontIcon.of(icon, 13, Color.WHITE));
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setBackground(AppColor.ACCENT);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorder(new EmptyBorder(10, 18, 10, 18));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JButton ghostButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        btn.setBackground(AppColor.WHITE);
        btn.setForeground(AppColor.TEXT_PRIMARY);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(softBorder(), 1),
                new EmptyBorder(9, 16, 9, 16)));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    // ---------------------------------------------------------------
    // Lưu
    // ---------------------------------------------------------------

    private void handleSave() {
        // Đảm bảo mọi edit đang gõ dở (JTable cell editor) được chốt lại vào model trước khi đọc.
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
            r.setNote(noteVal == null ? null : noteVal.toString().trim().isEmpty() ? null : noteVal.toString().trim());
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

    // ---------------------------------------------------------------
    // Helpers màu
    // ---------------------------------------------------------------

    private static Color softBorder() {
        return AppColor.BORDER != null ? AppColor.BORDER : new Color(226, 232, 240);
    }

    private static Color softStripe() {
        return AppColor.BG_LIGHTER != null ? AppColor.BG_LIGHTER : new Color(248, 250, 252);
    }

    // ---------------------------------------------------------------
    // Editor số nguyên thân thiện — double-click / F2 / gõ phím đều sửa được
    // ---------------------------------------------------------------

    private static final class IntegerCellEditor extends AbstractCellEditor implements TableCellEditor {
        private final JTextField field = new JTextField();
        private Integer currentValue = 0;

        IntegerCellEditor() {
            field.setHorizontalAlignment(SwingConstants.CENTER);
            field.setFont(new Font("Segoe UI", Font.BOLD, 13));
            field.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(AppColor.ACCENT != null ? AppColor.ACCENT : new Color(5, 150, 105), 2),
                    new EmptyBorder(2, 6, 2, 6)));
            field.addActionListener(e -> stopCellEditing());
            field.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    // Không stop ở đây nếu đang chuyển focus trong cùng table
                    if (!e.isTemporary()) {
                        stopCellEditing();
                    }
                }
            });
        }

        @Override
        public boolean isCellEditable(EventObject e) {
            // Cho phép sửa ngay khi double-click hoặc gõ phím
            if (e instanceof MouseEvent) {
                return ((MouseEvent) e).getClickCount() >= 1;
            }
            return true;
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value,
                                                     boolean isSelected, int row, int column) {
            currentValue = value instanceof Integer ? (Integer) value : 0;
            field.setText(String.valueOf(currentValue));
            field.selectAll();
            return field;
        }

        @Override
        public Object getCellEditorValue() {
            String text = field.getText().trim();
            if (text.isEmpty()) {
                return currentValue; // giữ giá trị cũ nếu xóa trắng
            }
            try {
                int v = Integer.parseInt(text);
                return Math.max(0, v); // không cho số âm
            } catch (NumberFormatException ex) {
                return currentValue; // gõ sai → giữ giá trị cũ
            }
        }

        @Override
        public boolean stopCellEditing() {
            // Ép parse trước khi commit
            getCellEditorValue();
            return super.stopCellEditing();
        }
    }
}
