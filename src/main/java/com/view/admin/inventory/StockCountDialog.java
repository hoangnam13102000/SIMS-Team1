package com.view.admin.inventory;

import com.components.BaseDialog;
import com.components.StatCard;
import com.components.ToggleSwitch;
import com.dao.StockReconciliationDAO;
import com.model.Product;
import com.model.StockReconciliation;
import com.theme.AppColor;
import com.theme.AppFont;
import com.theme.AppRadius;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.AbstractCellEditor;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
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
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.BasicStroke;
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
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
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
 * Bản thiết kế lại (v2) — hiện đại & thân thiện hơn:
 * - Dải KPI (StatCard) đầu trang: tổng sản phẩm / số dòng chênh lệch / chênh
 *   lệch ròng, cập nhật realtime khi gõ số.
 * - Ô tìm kiếm bo tròn có icon, ToggleSwitch mượt thay cho checkbox thô.
 * - Bảng đặt trong "card" bo góc nổi trên nền xám nhạt (giống các panel
 *   admin khác trong app) thay vì bảng phẳng chạm mép dialog.
 * - Cột "Chênh lệch" hiển thị dạng pill màu + icon mũi tên thay vì chữ số
 *   trần; cột "Tồn thực tế" hiển thị dạng chip trắng có icon bút chì gợi ý
 *   có thể bấm sửa, và khi sửa sẽ bật ra bộ đếm [-] [số] [+] thân thiện với
 *   thao tác chuột/cảm ứng thay vì chỉ gõ tay.
 * - Nút bấm bo góc, có hiệu ứng hover; thêm nút "Đặt lại tất cả" tiện lợi.
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

    private StatCard statTotalCard;
    private StatCard statDiffCard;
    private StatCard statNetCard;

    private JButton saveButton;
    private JTextField searchField;
    private ToggleSwitch onlyDiffToggle;
    private Runnable onSaved;

    // Flyweight renderer cho cột "Tồn thực tế" (chip trắng có icon bút chì)
    private JPanel actualChipWrapper;
    private RoundLabel actualChipLabel;

    // Flyweight renderer cho cột "Chênh lệch" (pill màu + icon mũi tên)
    private JPanel diffChipWrapper;
    private RoundLabel diffChipLabel;

    public StockCountDialog(java.awt.Frame owner, List<Product> products,
                            StockReconciliationDAO reconciliationDAO, int currentUserId) {
        super(owner, "Kiểm kê kho cuối ngày", Dialog.ModalityType.APPLICATION_MODAL);
        this.products = products;
        this.reconciliationDAO = reconciliationDAO;
        this.currentUserId = currentUserId;

        setSize(1000, 720);
        setMinimumSize(new Dimension(860, 560));
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
    // Header — icon bo tròn + tiêu đề + badge ngữ cảnh
    // ---------------------------------------------------------------

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
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        iconBox.setOpaque(false);
        iconBox.setPreferredSize(new Dimension(54, 54));
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

        RoundLabel tag = pillTag("Kiểm kê cuối ngày", FontAwesomeSolid.CALENDAR_CHECK,
                AppColor.INFO_BG != null ? AppColor.INFO_BG : new Color(238, 242, 255),
                AppColor.INFO != null ? AppColor.INFO : new Color(79, 70, 229));
        JPanel tagWrap = new JPanel(new GridBagLayout());
        tagWrap.setOpaque(false);
        tagWrap.add(tag);

        header.add(iconBox, BorderLayout.WEST);
        header.add(titles, BorderLayout.CENTER);
        header.add(tagWrap, BorderLayout.EAST);
        return header;
    }

    private RoundLabel pillTag(String text, FontAwesomeSolid icon, Color bg, Color fg) {
        RoundLabel lb = new RoundLabel();
        lb.setText(text);
        lb.setIcon(FontIcon.of(icon, 11, fg));
        lb.setIconTextGap(6);
        lb.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lb.setForeground(fg);
        lb.setBackground(bg);
        lb.setBorder(new EmptyBorder(7, 12, 7, 12));
        lb.setHorizontalAlignment(SwingConstants.CENTER);
        return lb;
    }

    // ---------------------------------------------------------------
    // Toolbar — KPI cards + tìm kiếm + toggle "chỉ hiện chênh lệch"
    // ---------------------------------------------------------------

    private JPanel buildToolbar() {
        JPanel toolbar = new JPanel(new BorderLayout(16, 0));
        toolbar.setOpaque(false);

        JPanel statsRow = new JPanel(new GridLayout(1, 3, 12, 0));
        statsRow.setOpaque(false);

        statTotalCard = new StatCard("Tổng sản phẩm", "0", FontAwesomeSolid.BOXES,
                AppColor.ACCENT != null ? AppColor.ACCENT : new Color(5, 150, 105), true);
        statDiffCard = new StatCard("Có chênh lệch", "0", FontAwesomeSolid.EXCLAMATION_TRIANGLE,
                AppColor.WARNING != null ? AppColor.WARNING : new Color(180, 83, 9), true);
        statNetCard = new StatCard("Chênh lệch ròng", "0", FontAwesomeSolid.BALANCE_SCALE,
                AppColor.INFO != null ? AppColor.INFO : new Color(79, 70, 229), true);

        statsRow.add(statTotalCard);
        statsRow.add(statDiffCard);
        statsRow.add(statNetCard);

        JPanel rightBox = new JPanel();
        rightBox.setOpaque(false);
        rightBox.setLayout(new BoxLayout(rightBox, BoxLayout.Y_AXIS));

        JPanel searchBox = buildSearchBox();
        searchBox.setAlignmentX(Component.RIGHT_ALIGNMENT);

        JPanel toggleRow = buildToggleBox();
        toggleRow.setAlignmentX(Component.RIGHT_ALIGNMENT);

        rightBox.add(Box.createVerticalGlue());
        rightBox.add(searchBox);
        rightBox.add(Box.createVerticalStrut(10));
        rightBox.add(toggleRow);
        rightBox.add(Box.createVerticalGlue());

        toolbar.add(statsRow, BorderLayout.WEST);
        toolbar.add(rightBox, BorderLayout.EAST);
        return toolbar;
    }

    private JPanel buildSearchBox() {
        JPanel box = new JPanel(new BorderLayout(8, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(AppColor.WHITE);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, AppRadius.MEDIUM, AppRadius.MEDIUM);
                g2.setColor(softBorder());
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, AppRadius.MEDIUM, AppRadius.MEDIUM);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        box.setOpaque(false);
        box.setPreferredSize(new Dimension(240, 38));
        box.setMaximumSize(new Dimension(240, 38));
        box.setBorder(new EmptyBorder(0, 12, 0, 10));

        JLabel searchIcon = new JLabel(FontIcon.of(FontAwesomeSolid.SEARCH, 13,
                AppColor.ICON_MUTED != null ? AppColor.ICON_MUTED : new Color(156, 163, 175)));

        searchField = new JTextField();
        searchField.setOpaque(false);
        searchField.setBorder(BorderFactory.createEmptyBorder());
        searchField.setFont(AppFont.BODY);
        searchField.putClientProperty("JTextField.placeholderText", "Tìm tên / mã sản phẩm...");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void removeUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });

        box.add(searchIcon, BorderLayout.WEST);
        box.add(searchField, BorderLayout.CENTER);
        return box;
    }

    private JPanel buildToggleBox() {
        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.X_AXIS));

        JLabel lbl = new JLabel("Chỉ hiện chênh lệch");
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lbl.setForeground(AppColor.TEXT_SECONDARY);

        onlyDiffToggle = new ToggleSwitch(false);
        onlyDiffToggle.onChange(selected -> applyFilter());

        box.add(lbl);
        box.add(Box.createHorizontalStrut(10));
        box.add(onlyDiffToggle);
        return box;
    }

    private void applyFilter() {
        if (sorter == null) return;
        String text = searchField.getText().trim();
        boolean onlyDiff = onlyDiffToggle != null && onlyDiffToggle.isSelected();

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
    // Body: KPI toolbar + bảng kiểm kê trong card bo góc
    // ---------------------------------------------------------------

    private JPanel buildBody() {
        JPanel content = new JPanel(new BorderLayout(0, 14));
        content.setBackground(AppColor.PAGE_BG != null ? AppColor.PAGE_BG : new Color(244, 246, 249));
        content.setBorder(new EmptyBorder(16, 24, 10, 24));

        content.add(buildToolbar(), BorderLayout.NORTH);

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
            if (onlyDiffToggle != null && onlyDiffToggle.isSelected()) {
                applyFilter();
            }
        });

        table = new JTable(model) {
            @Override
            public String getToolTipText(MouseEvent e) {
                int viewCol = columnAtPoint(e.getPoint());
                int viewRow = rowAtPoint(e.getPoint());
                if (viewCol == COL_DIFF && viewRow >= 0) {
                    int modelRow = convertRowIndexToModel(viewRow);
                    Object diffVal = model.getValueAt(modelRow, COL_DIFF);
                    int diff = diffVal instanceof Integer ? (Integer) diffVal : 0;
                    if (diff < 0) {
                        return "Thiếu " + (-diff) + " sản phẩm — bấm để lập phiếu hủy hàng (hỏng/mất) cho phần thiếu hụt này";
                    }
                }
                return super.getToolTipText(e);
            }
        };
        table.setFont(AppFont.BODY);
        table.setRowHeight(42);
        table.setGridColor(AppColor.TABLE_GRID != null ? AppColor.TABLE_GRID : softBorder());
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setFillsViewportHeight(true);
        table.setRowSelectionAllowed(false);
        table.setAutoCreateRowSorter(false);
        table.setIntercellSpacing(new Dimension(0, 0));

        sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);

        // Column widths
        table.getColumnModel().getColumn(COL_CODE).setPreferredWidth(95);
        table.getColumnModel().getColumn(COL_NAME).setPreferredWidth(250);
        table.getColumnModel().getColumn(COL_SYSTEM).setPreferredWidth(110);
        table.getColumnModel().getColumn(COL_ACTUAL).setPreferredWidth(130);
        table.getColumnModel().getColumn(COL_DIFF).setPreferredWidth(120);
        table.getColumnModel().getColumn(COL_NOTE).setPreferredWidth(200);

        // Header style
        JTableHeader header = table.getTableHeader();
        header.setFont(new Font("Segoe UI", Font.BOLD, 12));
        header.setPreferredSize(new Dimension(header.getWidth(), 40));
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
                        new EmptyBorder(0, 12, 0, 12)));
                lb.setHorizontalAlignment(column == COL_CODE || column == COL_NAME || column == COL_NOTE
                        ? SwingConstants.LEFT : SwingConstants.CENTER);
                return lb;
            }
        });

        // Cell renderers
        table.getColumnModel().getColumn(COL_CODE).setCellRenderer(textRenderer(SwingConstants.LEFT));
        table.getColumnModel().getColumn(COL_NAME).setCellRenderer(textRenderer(SwingConstants.LEFT));
        table.getColumnModel().getColumn(COL_SYSTEM).setCellRenderer(numberRenderer());
        table.getColumnModel().getColumn(COL_ACTUAL).setCellRenderer(actualRenderer());
        table.getColumnModel().getColumn(COL_DIFF).setCellRenderer(diffRenderer());
        table.getColumnModel().getColumn(COL_NOTE).setCellRenderer(noteRenderer());

        // Cell editors
        table.getColumnModel().getColumn(COL_ACTUAL).setCellEditor(new QuantityCellEditor());
        table.getColumnModel().getColumn(COL_NOTE).setCellEditor(new DefaultCellEditor(new JTextField()) {{
            ((JTextField) getComponent()).setBorder(new EmptyBorder(4, 10, 4, 10));
            ((JTextField) getComponent()).setFont(AppFont.BODY);
        }});
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        table.setSurrendersFocusOnKeystroke(true);

        // Ô "Chênh lệch" bị thiếu (âm) có thể bấm để lập phiếu hủy hàng ngay —
        // xử lý hao hụt có nguyên nhân (hỏng/mất) đúng chuẩn qua StockDisposal
        // thay vì chỉ ghi chú tay, xem chi tiết ở openDisposalForShortage().
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                handleDiffCellClick(e);
            }
        });
        table.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                updateDiffCellCursor(e);
            }
        });

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(null);
        tableScroll.getViewport().setBackground(AppColor.WHITE);
        tableScroll.setBackground(AppColor.WHITE);

        content.add(roundedCard(tableScroll, AppRadius.MEDIUM), BorderLayout.CENTER);
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

    // ---------------------------------------------------------------
    // Xử lý thiếu hụt (chênh lệch âm) — bấm pill "Chênh lệch" để lập phiếu
    // hủy hàng ngay, thay vì chỉ ghi chú tay rồi lưu kiểm kê như hao hụt
    // "không rõ nguyên nhân". Sau khi lưu phiếu hủy, StockDisposal đã tự trừ
    // đúng InventoryBatch.RemainingQty lẫn Products.Stock (xem StockDisposalDAO),
    // nên chỉ cần đồng bộ lại "Tồn hệ thống" của đúng dòng này trên bảng kiểm
    // kê để tính lại chênh lệch cho khớp thực tế trước khi lưu phiên.
    // ---------------------------------------------------------------

    private void handleDiffCellClick(MouseEvent e) {
        int viewCol = table.columnAtPoint(e.getPoint());
        int viewRow = table.rowAtPoint(e.getPoint());
        if (viewCol != COL_DIFF || viewRow < 0) return;

        int modelRow = table.convertRowIndexToModel(viewRow);
        Object diffVal = model.getValueAt(modelRow, COL_DIFF);
        int diff = diffVal instanceof Integer ? (Integer) diffVal : 0;
        if (diff >= 0) return; // chỉ xử lý khi THIẾU hàng — thừa hàng không phải hao hụt

        openDisposalForShortage(modelRow, -diff);
    }

    private void updateDiffCellCursor(MouseEvent e) {
        int viewCol = table.columnAtPoint(e.getPoint());
        int viewRow = table.rowAtPoint(e.getPoint());
        boolean clickable = false;
        if (viewCol == COL_DIFF && viewRow >= 0) {
            int modelRow = table.convertRowIndexToModel(viewRow);
            Object diffVal = model.getValueAt(modelRow, COL_DIFF);
            int diff = diffVal instanceof Integer ? (Integer) diffVal : 0;
            clickable = diff < 0;
        }
        table.setCursor(Cursor.getPredefinedCursor(clickable ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    /** Mở sẵn form "Lập phiếu tiêu hủy" theo đúng sản phẩm + số lượng thiếu hụt của dòng này. */
    private void openDisposalForShortage(int modelRow, int shortageQty) {
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
        Product product = products.get(modelRow);
        java.awt.Frame ownerFrame = (getOwner() instanceof java.awt.Frame) ? (java.awt.Frame) getOwner() : null;

        StockDisposalFormDialog dialog = new StockDisposalFormDialog(
                ownerFrame, product.getProductId(), shortageQty);
        dialog.onSaved((disposalId, lineCount) -> applyDisposalResult(modelRow, shortageQty, disposalId));
        dialog.setVisible(true);
    }

    /**
     * Sau khi phiếu hủy đã lưu thành công: StockDisposal đã tự trừ Products.Stock
     * ngay trong DB, nên chỉ cần trừ tương ứng trên "Tồn hệ thống" của dòng này
     * (không cần query lại DB — dialog kiểm kê đang application-modal nên không
     * có nơi nào khác đổi Stock song song lúc này), rồi tính lại chênh lệch.
     * Ghi chú của dòng cũng được gắn thêm mã phiếu để truy vết sau này.
     */
    private void applyDisposalResult(int modelRow, int disposedQty, int disposalId) {
        Object sysVal = model.getValueAt(modelRow, COL_SYSTEM);
        int currentSystem = sysVal instanceof Integer ? (Integer) sysVal : 0;
        model.setValueAt(Math.max(0, currentSystem - disposedQty), modelRow, COL_SYSTEM);

        String tag = "Đã lập phiếu hủy TH_" + String.format("%06d", disposalId) + " (" + disposedQty + " sp)";
        Object noteVal = model.getValueAt(modelRow, COL_NOTE);
        String existing = noteVal == null ? "" : noteVal.toString().trim();
        model.setValueAt(existing.isEmpty() ? tag : existing + " · " + tag, modelRow, COL_NOTE);

        recalcDiffs();
        updateSummary();
    }

    /** Card trắng bo góc bao quanh bảng — bảng "nổi" trên nền xám nhạt của dialog. */
    private JPanel roundedCard(JComponent inner, int radius) {
        JPanel card = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(AppColor.WHITE);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
                g2.setColor(softBorder());
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(1, 1, 1, 1));
        card.add(inner, BorderLayout.CENTER);
        return card;
    }

    private void updateSummary() {
        if (model == null || statTotalCard == null) return;
        int total = model.getRowCount();
        int diffCount = 0;
        int net = 0;
        for (int i = 0; i < total; i++) {
            Object diffVal = model.getValueAt(i, COL_DIFF);
            int diff = diffVal instanceof Integer ? (Integer) diffVal : 0;
            if (diff != 0) diffCount++;
            net += diff;
        }

        statTotalCard.setValue(String.valueOf(total));
        statTotalCard.setSubtitle("sản phẩm đang bán");

        statDiffCard.setValue(String.valueOf(diffCount));
        if (diffCount > 0) {
            statDiffCard.setTrend("cần kiểm tra lại", false);
        } else {
            statDiffCard.setTrend("đã khớp hoàn toàn", true);
        }

        statNetCard.setValue((net > 0 ? "+" : "") + net);
        if (net > 0) {
            statNetCard.setTrend("nhiều hơn hệ thống", true);
        } else if (net < 0) {
            statNetCard.setTrend("ít hơn hệ thống", false);
        } else {
            statNetCard.setSubtitle("không thay đổi");
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
                setBorder(new EmptyBorder(0, 12, 0, 12));
                return c;
            }
        };
    }

    private DefaultTableCellRenderer numberRenderer() {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setHorizontalAlignment(SwingConstants.CENTER);
                applyRowStyle(c, table, row, isSelected);
                setFont(AppFont.BODY);
                setBorder(new EmptyBorder(0, 8, 0, 8));
                return c;
            }
        };
    }

    /** Cột "Tồn thực tế": chip trắng bo góc + icon bút chì, gợi ý có thể bấm để sửa. */
    private TableCellRenderer actualRenderer() {
        if (actualChipWrapper == null) {
            actualChipLabel = new RoundLabel();
            actualChipLabel.setOpaque(false);
            actualChipLabel.setHorizontalAlignment(SwingConstants.CENTER);
            actualChipLabel.setIconTextGap(7);
            actualChipLabel.setRadius(8);

            actualChipWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            actualChipWrapper.setOpaque(true);
            actualChipWrapper.add(actualChipLabel);
        }
        return (tbl, value, isSelected, hasFocus, row, column) -> {
            int val = value instanceof Integer ? (Integer) value : 0;
            int modelRow = tbl.convertRowIndexToModel(row);
            Object diffVal = model.getValueAt(modelRow, COL_DIFF);
            int diff = diffVal instanceof Integer ? (Integer) diffVal : 0;

            actualChipLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
            actualChipLabel.setText(String.valueOf(val));
            actualChipLabel.setBorder(new EmptyBorder(5, 12, 5, 10));
            actualChipLabel.setIcon(FontIcon.of(FontAwesomeSolid.PEN, 9,
                    AppColor.TEXT_MUTED != null ? AppColor.TEXT_MUTED : new Color(100, 116, 139)));
            actualChipLabel.setForeground(AppColor.TEXT_PRIMARY);
            actualChipLabel.setBackground(AppColor.WHITE);
            actualChipLabel.setStrokeColor(diff != 0
                    ? (AppColor.ACCENT != null ? AppColor.ACCENT : new Color(5, 150, 105))
                    : softBorder());

            applyRowStyle(actualChipWrapper, tbl, row, isSelected);
            return actualChipWrapper;
        };
    }

    /** Cột "Chênh lệch": pill màu + icon mũi tên lên/xuống, hoặc dấu "—" trung tính khi khớp. */
    private TableCellRenderer diffRenderer() {
        if (diffChipWrapper == null) {
            diffChipLabel = new RoundLabel();
            diffChipLabel.setOpaque(false);
            diffChipLabel.setHorizontalAlignment(SwingConstants.CENTER);
            diffChipLabel.setIconTextGap(6);
            diffChipLabel.setRadius(999);

            diffChipWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            diffChipWrapper.setOpaque(true);
            diffChipWrapper.add(diffChipLabel);
        }
        return (tbl, value, isSelected, hasFocus, row, column) -> {
            int diff = value instanceof Integer ? (Integer) value : 0;
            diffChipLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
            diffChipLabel.setBorder(new EmptyBorder(4, 11, 4, 11));
            diffChipLabel.setStrokeColor(null);

            if (diff > 0) {
                Color c = AppColor.SUCCESS != null ? AppColor.SUCCESS : new Color(21, 128, 61);
                diffChipLabel.setIcon(FontIcon.of(FontAwesomeSolid.CHEVRON_UP, 9, c));
                diffChipLabel.setText("+" + diff);
                diffChipLabel.setForeground(c);
                diffChipLabel.setBackground(AppColor.SUCCESS_BG != null ? AppColor.SUCCESS_BG : new Color(236, 253, 245));
            } else if (diff < 0) {
                Color c = AppColor.ERROR != null ? AppColor.ERROR : new Color(220, 38, 38);
                diffChipLabel.setIcon(FontIcon.of(FontAwesomeSolid.CHEVRON_DOWN, 9, c));
                diffChipLabel.setText(String.valueOf(diff));
                diffChipLabel.setForeground(c);
                diffChipLabel.setBackground(AppColor.ERROR_BG != null ? AppColor.ERROR_BG : new Color(254, 242, 242));
            } else {
                diffChipLabel.setIcon(null);
                diffChipLabel.setText("— khớp");
                diffChipLabel.setForeground(AppColor.TEXT_MUTED);
                diffChipLabel.setBackground(AppColor.BG_LIGHTER != null ? AppColor.BG_LIGHTER : new Color(241, 245, 249));
            }

            applyRowStyle(diffChipWrapper, tbl, row, isSelected);
            return diffChipWrapper;
        };
    }

    /** Cột "Ghi chú": placeholder in nghiêng khi trống, chữ thường khi có nội dung. */
    private TableCellRenderer noteRenderer() {
        return (tbl, value, isSelected, hasFocus, row, column) -> {
            String text = value == null ? "" : value.toString();
            JLabel lb = new JLabel();
            lb.setOpaque(true);
            lb.setBorder(new EmptyBorder(0, 12, 0, 12));
            applyRowStyle(lb, tbl, row, isSelected);
            if (text.isBlank()) {
                lb.setText("Thêm ghi chú...");
                lb.setFont(new Font("Segoe UI", Font.ITALIC, 12));
                lb.setForeground(AppColor.TEXT_DISABLED != null ? AppColor.TEXT_DISABLED : AppColor.TEXT_MUTED);
            } else {
                lb.setText(text);
                lb.setFont(AppFont.BODY);
                lb.setForeground(AppColor.TEXT_PRIMARY);
            }
            return lb;
        };
    }

    private void applyRowStyle(Component c, JTable table, int viewRow, boolean isSelected) {
        if (isSelected) return;
        int modelRow = table.convertRowIndexToModel(viewRow);
        Object diffVal = model.getValueAt(modelRow, COL_DIFF);
        int diff = diffVal instanceof Integer ? (Integer) diffVal : 0;

        if (diff != 0) {
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

        JLabel hint = new JLabel("<html>Mẹo: bấm +/- để sửa \"Tồn thực tế\" · bấm vào pill <b>chênh lệch âm</b> (đỏ) để lập phiếu hủy hàng cho phần thiếu hụt.</html>");
        hint.setIcon(FontIcon.of(FontAwesomeSolid.INFO_CIRCLE, 12,
                AppColor.TEXT_MUTED != null ? AppColor.TEXT_MUTED : new Color(100, 116, 139)));
        hint.setIconTextGap(8);
        hint.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        hint.setForeground(AppColor.TEXT_MUTED);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);

        JButton resetButton = new ModernButton("Đặt lại tất cả", FontAwesomeSolid.UNDO, false);
        resetButton.addActionListener(e -> handleResetAll());

        JButton cancelButton = new ModernButton("Hủy", null, false);
        cancelButton.addActionListener(e -> dispose());

        saveButton = new ModernButton("Lưu phiên kiểm kê", FontAwesomeSolid.SAVE, true);
        saveButton.addActionListener(e -> handleSave());

        actions.add(resetButton);
        actions.add(cancelButton);
        actions.add(saveButton);

        footer.add(hint, BorderLayout.WEST);
        footer.add(actions, BorderLayout.EAST);
        getRootPane().setDefaultButton(saveButton);
        return footer;
    }

    /** Nút bo góc hiện đại, có hover; primary=true dùng nền accent, false dùng viền ghost. */
    private final class ModernButton extends JButton {
        private final boolean primary;
        private boolean hover = false;

        ModernButton(String text, FontAwesomeSolid icon, boolean primary) {
            super(text);
            this.primary = primary;
            if (icon != null) {
                setIcon(FontIcon.of(icon, 13, primary ? Color.WHITE : AppColor.TEXT_SECONDARY));
                setIconTextGap(8);
            }
            setFont(new Font("Segoe UI", Font.BOLD, 13));
            setForeground(primary ? Color.WHITE : AppColor.TEXT_PRIMARY);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setOpaque(false);
            setBorder(new EmptyBorder(10, 18, 10, 18));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (primary) {
                Color base = !isEnabled()
                        ? (AppColor.DISABLED_BTN != null ? AppColor.DISABLED_BTN : new Color(165, 165, 180))
                        : (hover ? AppColor.ACCENT_HOVER : AppColor.ACCENT);
                g2.setColor(base);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), AppRadius.MEDIUM, AppRadius.MEDIUM);
            } else {
                g2.setColor(hover ? (AppColor.BG_LIGHT != null ? AppColor.BG_LIGHT : new Color(248, 250, 252)) : AppColor.WHITE);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, AppRadius.MEDIUM, AppRadius.MEDIUM);
                g2.setColor(softBorder());
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, AppRadius.MEDIUM, AppRadius.MEDIUM);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ---------------------------------------------------------------
    // Đặt lại tất cả
    // ---------------------------------------------------------------

    private void handleResetAll() {
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
        boolean confirmed = BaseDialog.confirm(this, "Đặt lại toàn bộ",
                "Đặt \"Tồn thực tế\" của tất cả sản phẩm về đúng bằng tồn hệ thống?\n"
                        + "Mọi chỉnh sửa và ghi chú hiện tại sẽ bị xóa.");
        if (!confirmed) return;

        for (int i = 0; i < model.getRowCount(); i++) {
            Object sys = model.getValueAt(i, COL_SYSTEM);
            model.setValueAt(sys, i, COL_ACTUAL);
            model.setValueAt("", i, COL_NOTE);
        }
        recalcDiffs();
        updateSummary();
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
    // JLabel bo góc dùng chung cho pill/badge/chip (header tag, chip
    // "Tồn thực tế", pill "Chênh lệch")
    // ---------------------------------------------------------------

    private static class RoundLabel extends JLabel {
        private int radius = 999;
        private Color strokeColor;

        RoundLabel() {
            setOpaque(false);
        }

        void setRadius(int radius) {
            this.radius = radius;
        }

        void setStrokeColor(Color color) {
            this.strokeColor = color;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            if (strokeColor != null) {
                g2.setColor(strokeColor);
                g2.setStroke(new BasicStroke(1.4f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ---------------------------------------------------------------
    // Editor số lượng thân thiện: [-] [số] [+] — bấm nút hoặc gõ tay đều
    // được, double-click / F2 mở editor, Enter / mất focus để chốt giá trị.
    // ---------------------------------------------------------------

    private final class QuantityCellEditor extends AbstractCellEditor implements TableCellEditor {
        private final JPanel panel;
        private final JTextField field = new JTextField();
        private final JButton minusBtn;
        private final JButton plusBtn;
        private Integer currentValue = 0;

        QuantityCellEditor() {
            panel = new JPanel(new BorderLayout(2, 0)) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(AppColor.WHITE);
                    g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                    g2.setColor(AppColor.ACCENT != null ? AppColor.ACCENT : new Color(5, 150, 105));
                    g2.setStroke(new BasicStroke(1.6f));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            panel.setOpaque(false);
            panel.setBorder(new EmptyBorder(2, 2, 2, 2));

            minusBtn = stepButton(FontAwesomeSolid.MINUS);
            plusBtn = stepButton(FontAwesomeSolid.PLUS);

            field.setHorizontalAlignment(SwingConstants.CENTER);
            field.setFont(new Font("Segoe UI", Font.BOLD, 13));
            field.setBorder(BorderFactory.createEmptyBorder());
            field.setOpaque(false);
            field.addActionListener(e -> stopCellEditing());
            field.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    if (!e.isTemporary()) {
                        stopCellEditing();
                    }
                }
            });

            minusBtn.addActionListener(e -> adjust(-1));
            plusBtn.addActionListener(e -> adjust(1));

            panel.add(minusBtn, BorderLayout.WEST);
            panel.add(field, BorderLayout.CENTER);
            panel.add(plusBtn, BorderLayout.EAST);
        }

        private JButton stepButton(FontAwesomeSolid icon) {
            JButton btn = new JButton(FontIcon.of(icon, 10,
                    AppColor.ACCENT != null ? AppColor.ACCENT : new Color(5, 150, 105)));
            btn.setFocusable(false);
            btn.setContentAreaFilled(false);
            btn.setBorderPainted(false);
            btn.setOpaque(false);
            btn.setBorder(new EmptyBorder(4, 8, 4, 8));
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            return btn;
        }

        private void adjust(int delta) {
            int v = Math.max(0, parseCurrent() + delta);
            currentValue = v;
            field.setText(String.valueOf(v));
        }

        private int parseCurrent() {
            try {
                return Math.max(0, Integer.parseInt(field.getText().trim()));
            } catch (NumberFormatException ex) {
                return currentValue != null ? currentValue : 0;
            }
        }

        @Override
        public boolean isCellEditable(EventObject e) {
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
            SwingUtilities.invokeLater(() -> {
                field.requestFocusInWindow();
                field.selectAll();
            });
            return panel;
        }

        @Override
        public Object getCellEditorValue() {
            return parseCurrent();
        }

        @Override
        public boolean stopCellEditing() {
            currentValue = parseCurrent();
            return super.stopCellEditing();
        }
    }
}