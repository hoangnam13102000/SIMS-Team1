package com.components;

import com.components.table.ActionColumn;
import com.components.table.AutoRowNumber;
import com.components.table.ImageColumn;
import com.components.table.RowColorProvider;
import com.components.table.StatusColumn;
import com.components.table.TableFilter;
import com.components.table.TableSorter;
import com.theme.AppColor;
import com.theme.AppFont;
import com.theme.AppRadius;
import com.theme.AppShadow;
import com.theme.AppSpacing;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;


public class BaseTable extends JPanel {

    private static final Color HEADER_FG = Color.WHITE;

    private static final String ACTIONS_COLUMN_NAME = "Thao tác";

    private final JTable table;
    private final DefaultTableModel model;
    private final JScrollPane scrollPane;

    /** Mau nen dung chung cho moi renderer trong package com.components.table. */
    private final RowColorProvider rowColorProvider =
            (viewRow, isSelected) -> isSelected ? AppColor.ACCENT_SELECTION_BG : (viewRow % 2 == 0 ? AppColor.WHITE : AppColor.TABLE_ROW_ODD);

    private DefaultTableCellRenderer stripedRenderer;

    private boolean actionsEnabled = false;
    private ActionColumn actionColumn;
    private int actionColumnIndex = -1;
    private boolean actionClickHandlerInstalled = false;

    /** (viewRow, slotIndex trong cot Thao tac) dang duoc chuot tro toi - -1 nghia la khong hover nut nao. */
    private int hoveredActionRow = -1;
    private int hoveredActionSlot = -1;
    private final ActionColumn.HoverState actionHoverState =
            (viewRow, slotIndex) -> viewRow == hoveredActionRow && slotIndex == hoveredActionSlot;

    /** Cac cot khong nen cho sort (action, anh, STT...) - tu dong gom lai khi cau hinh. */
    private final Set<Integer> autoNonSortableColumns = new LinkedHashSet<>();
    /** Ghi nho renderer da gan cho tung cot de co the re-apply sau khi them cot moi vao model. */
    private final Map<Integer, TableCellRenderer> customRenderers = new LinkedHashMap<>();

    private TableSorter sorter;
    private TableFilter tableFilter;

    /** True khi enableHorizontalScroll() da duoc goi cho bang nay - xem javadoc cua ham do. */
    private boolean horizontalScrollEnabled = false;

    /** Cac chi so cot (model) duoc phep sua truc tiep tren bang. Mac dinh rong = khong sua. */
    private final Set<Integer> editableColumns = new LinkedHashSet<>();

    public BaseTable(String[] columns) {
        setLayout(new BorderLayout());
        setBackground(AppColor.WHITE);
        
        // ===== BORDER VỚI SHADOW =====
        setBorder(BorderFactory.createCompoundBorder(
            new DropShadowBorder(AppShadow.MEDIUM, AppShadow.MEDIUM_BLUR),
            new RoundedBorder(AppRadius.MEDIUM, AppColor.BORDER)
        ));
        
        // Tạo table
        model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return editableColumns.contains(column);
            }
        };

        table = new JTable(model);
        styleTable();
        installResizeWidthSync();

        // ===== SCROLLPANE KHÔNG CÓ BORDER VÀ BACKGROUND =====
        scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder()); // Tắt border mặc định
        scrollPane.getViewport().setBackground(AppColor.WHITE);
        scrollPane.setOpaque(false); // Làm trong suốt
        
        // ===== QUAN TRỌNG: TẮT VIỀN CỦA TABLE =====
        table.setBorder(BorderFactory.createEmptyBorder()); // Table không có border
        
        // ===== TẮT BACKGROUND XẤU KHI KHÔNG CÓ DỮ LIỆU =====
        table.setBackground(AppColor.WHITE);
        
        add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * Custom Border bo tròn
     */
    private static class RoundedBorder extends LineBorder {
        private final int radius;

        public RoundedBorder(int radius, Color color) {
            super(color, 1, true);
            this.radius = radius;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(lineColor);
            g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(2, 2, 2, 2);
        }
    }

    /**
     * DropShadow Border
     */
    private static class DropShadowBorder implements javax.swing.border.Border {
        private final Color shadowColor;
        private final int blur;

        public DropShadowBorder(Color shadowColor, int blur) {
            this.shadowColor = shadowColor;
            this.blur = blur;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            for (int i = 1; i <= blur; i++) {
                float alpha = 0.12f * (1.0f - (float)i / (blur + 2));
                g2.setColor(new Color(
                    shadowColor.getRed(),
                    shadowColor.getGreen(),
                    shadowColor.getBlue(),
                    (int)(shadowColor.getAlpha() * alpha)
                ));
                g2.drawRoundRect(x + i, y + i, width - i*2 - 1, height - i*2 - 1, 
                    AppRadius.MEDIUM, AppRadius.MEDIUM);
            }
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(blur + 1, blur + 1, blur + 1, blur + 1);
        }

        @Override
        public boolean isBorderOpaque() {
            return false;
        }
    }

    /**
     * Tach title header thanh 2 dong (HTML) de hien du chu khi cot hep.
     * Uu tien ngat o khoang trang gan giua chuoi; neu khong co space thi ngat
     * theo do rong pixel.
     */
    private static String wrapHeaderHtml(String text, FontMetrics fm, int maxWidth) {
        String safe = text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
        int breakAt = -1;
        int mid = text.length() / 2;
        // Tim khoang trang gan giua nhat ma dong 1 van vua maxWidth
        for (int i = 0; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                String left = text.substring(0, i).trim();
                if (fm.stringWidth(left) <= maxWidth) {
                    if (breakAt < 0 || Math.abs(i - mid) <= Math.abs(breakAt - mid)) {
                        breakAt = i;
                    }
                }
            }
        }
        if (breakAt > 0) {
            String line1 = safe.substring(0, breakAt).trim();
            String line2 = safe.substring(breakAt).trim();
            return "<html><body style='margin:0;padding:0;color:#ffffff'>"
                    + line1 + "<br>" + line2 + "</body></html>";
        }
        // Khong co space phu hop - ngat theo ky tu vua maxWidth
        int cut = text.length();
        for (int i = 1; i <= text.length(); i++) {
            if (fm.stringWidth(text.substring(0, i)) > maxWidth) {
                cut = Math.max(1, i - 1);
                break;
            }
        }
        return "<html><body style='margin:0;padding:0;color:#ffffff'>"
                + safe.substring(0, cut) + "<br>" + safe.substring(cut)
                + "</body></html>";
    }
    private static Icon sortIconFor(JTable table, int column) {
        RowSorter<?> rowSorter = table.getRowSorter();
        if (!(rowSorter instanceof TableRowSorter)) return null;

        int modelColumn = table.convertColumnIndexToModel(column);
        if (!((TableRowSorter<?>) rowSorter).isSortable(modelColumn)) return null;

        java.util.List<? extends RowSorter.SortKey> keys = rowSorter.getSortKeys();
        for (int i = 0; i < keys.size(); i++) {
            RowSorter.SortKey key = keys.get(i);
            if (key.getColumn() == modelColumn && key.getSortOrder() != SortOrder.UNSORTED) {
                boolean ascending = key.getSortOrder() == SortOrder.ASCENDING;
                int priority = keys.size() > 1 ? i + 1 : 0; // 0 = khong hien so thu tu
                return SortArrowIcon.active(ascending, priority, HEADER_FG);
            }
        }
        return SortArrowIcon.hint(new Color(HEADER_FG.getRed(), HEADER_FG.getGreen(), HEADER_FG.getBlue(), 130));
    }

    /** Icon mui ten sort ve bang Graphics2D, khong can anh (2 trang thai: hint / active). */
    private static class SortArrowIcon implements Icon {
        private static final int SIZE = 7;
        private static final int BOX_HEIGHT = SIZE * 2;

        private final boolean hint;
        private final boolean ascending;
        private final int priority; // 0 = an, >0 = hien so thu tu ben canh (chi o trang thai active)
        private final Color color;

        private SortArrowIcon(boolean hint, boolean ascending, int priority, Color color) {
            this.hint = hint;
            this.ascending = ascending;
            this.priority = priority;
            this.color = color;
        }

        /** Cot co the sort nhung chua duoc chon: hien ca 2 mui ten mo nhat. */
        static SortArrowIcon hint(Color color) {
            return new SortArrowIcon(true, true, 0, color);
        }

        /** Cot dang la sort key hien tai: hien 1 mui ten ro theo chieu dang sort. */
        static SortArrowIcon active(boolean ascending, int priority, Color color) {
            return new SortArrowIcon(false, ascending, priority, color);
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);

            if (hint) {
                drawTriangle(g2, x, y, true);              // mui ten len, nua tren
                drawTriangle(g2, x, y + SIZE, false);       // mui ten xuong, nua duoi
            } else {
                drawTriangle(g2, x, y + (BOX_HEIGHT - SIZE) / 2, ascending);
                if (priority > 0) {
                    g2.setFont(g2.getFont().deriveFont(Font.BOLD, 9f));
                    g2.drawString(String.valueOf(priority), x + SIZE + 3, y + BOX_HEIGHT - 2);
                }
            }
            g2.dispose();
        }

        private void drawTriangle(Graphics2D g2, int x, int y, boolean pointUp) {
            int[] xs = {x, x + SIZE, x + SIZE / 2};
            int[] ys = pointUp ? new int[]{y + SIZE - 1, y + SIZE - 1, y} : new int[]{y, y, y + SIZE - 1};
            g2.fillPolygon(xs, ys, 3);
        }

        @Override public int getIconWidth() { return !hint && priority > 0 ? SIZE + 12 : SIZE + 2; }
        @Override public int getIconHeight() { return BOX_HEIGHT; }
    }

    /**
     * SUA LOI: keo doi chieu rong cot xong, cot tu "nhay ve" kich thuoc cu.
     *
     * Nguyen nhan: JTable.doLayout() - moi khi bang duoc layout lai (vd sau
     * revalidate()/repaint() trong refresh(), sau khi resize cua so, sau khi
     * mo/dong sidebar...) - se tinh lai WIDTH thuc te cua TUNG COT dua theo ti
     * le PREFERRED WIDTH (khong phai width nguoi dung vua keo!), roi ep tong
     * width khop voi be rong vung nhin (vi autoResizeMode mac dinh KHONG phai
     * AUTO_RESIZE_OFF nen JTable luon tu keo gian de lap day vung nhin).
     *
     * Khi nguoi dung keo bien cot (drag border header), Swing chi doi
     * TableColumn#width, KHONG doi TableColumn#preferredWidth. Vi vay ngay sau
     * do, hanh dong layout lai tiep theo (thuong xay ra rat nhanh vd do
     * table.getTable().revalidate() trong refresh() sau khi load/search/CRUD,
     * hoac do window/panel resize) se doc lai preferredWidth cu va tinh lai
     * width - xoa mat thao tac keo dan cua nguoi dung, tao cam giac "khong keo
     * duoc". Bang Nha cung cap it bi refresh lien tuc nen it khi lo ra loi
     * nay, cac bang khac refresh thuong xuyen hon nen bi "nhay ve" gan nhu
     * ngay lap tuc.
     *
     * Cach sua: lang nghe su kien columnMarginChanged (ban vao lien tuc trong
     * luc keo) va dong bo preferredWidth = width hien tai cua dung cot dang
     * keo (table.getTableHeader().getResizingColumn()) - nho vay lan layout
     * lai tiep theo se giu nguyen kich thuoc nguoi dung vua chinh thay vi tra
     * ve gia tri cu.
     */
    private void installResizeWidthSync() {
        table.getColumnModel().addColumnModelListener(new javax.swing.event.TableColumnModelListener() {
            @Override
            public void columnMarginChanged(javax.swing.event.ChangeEvent e) {
                javax.swing.table.TableColumn resizingColumn = table.getTableHeader().getResizingColumn();
                if (resizingColumn != null) {
                    resizingColumn.setPreferredWidth(resizingColumn.getWidth());
                }
            }

            @Override
            public void columnAdded(javax.swing.event.TableColumnModelEvent e) {}
            @Override
            public void columnRemoved(javax.swing.event.TableColumnModelEvent e) {}
            @Override
            public void columnMoved(javax.swing.event.TableColumnModelEvent e) {}
            @Override
            public void columnSelectionChanged(javax.swing.event.ListSelectionEvent e) {}
        });
    }

    // ===== STYLE TABLE =====
    private void styleTable() {
        table.setRowHeight(48);
        table.setFont(AppFont.BODY);
        table.setShowGrid(true);
        table.setGridColor(AppColor.TABLE_GRID);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setSelectionBackground(AppColor.ACCENT_SELECTION_BG);
        table.setSelectionForeground(AppColor.TEXT_PRIMARY);
        table.setFillsViewportHeight(true);
        table.setFocusable(false);
        table.setRowSelectionAllowed(true);
        table.setColumnSelectionAllowed(false);
        
        // ===== THÊM: TẮT VIỀN TABLE =====
        table.setBorder(BorderFactory.createEmptyBorder());

        // === HEADER ===
        JTableHeader header = table.getTableHeader();
        header.setReorderingAllowed(false);
        header.setResizingAllowed(true);
        header.setPreferredSize(new Dimension(header.getPreferredSize().width, 56));
        header.setFont(AppFont.BODY_BOLD);
        header.setBackground(AppColor.TABLE_HEADER_BG);
        header.setForeground(HEADER_FG);
        header.setBorder(BorderFactory.createEmptyBorder());

        // Header renderer với spacing (gan o cap JTableHeader nen KHONG bi mat
        // khi model.addColumn(...) lam JTable tao lai cac TableColumn).
        //
        // LUU Y: day la renderer TU VIET, thay the hoan toan renderer mac dinh
        // cua Swing (javax.swing.plaf.basic.BasicTableHeaderUI) - renderer mac
        // dinh do la noi Swing tu ve mui ten sort (▲/▼) khi bam vao header. Vi
        // BaseTable ghi de bang renderer rieng nay ma khong tu ve lai mui ten,
        // nen truoc day click sort van hoat dong (dl duoc sap xep) nhung KHONG
        // co dau hieu gi tren header cho nguoi dung biet dang sort cot nao/chieu
        // nao - nhin nhu "sort khong xuat hien". Doan duoi day tu ve lai mui
        // ten do dua vao RowSorter#getSortKeys() cua table.
        DefaultTableCellRenderer headerRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel c = (JLabel) super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);
                
                c.setBackground(AppColor.TABLE_HEADER_BG);
                c.setForeground(HEADER_FG);
                c.setBorder(new EmptyBorder(AppSpacing.SM, AppSpacing.MD, AppSpacing.SM, AppSpacing.MD));
                c.setFont(AppFont.BODY_BOLD);
                c.setHorizontalAlignment(SwingConstants.CENTER);   // ← THÊM DÒNG NÀY
                c.setHorizontalTextPosition(SwingConstants.LEFT);
                c.setVerticalAlignment(SwingConstants.CENTER);
                
                Icon sortIcon = sortIconFor(table, column);
                c.setIcon(sortIcon);
                c.setIconTextGap(4);
                
                String text = value != null ? value.toString() : "";
                int colWidth = table.getColumnModel().getColumn(column).getWidth();
                int iconW = sortIcon != null ? sortIcon.getIconWidth() + 4 : 0;
                int available = Math.max(12, colWidth - AppSpacing.MD * 2 - iconW);
                FontMetrics fm = c.getFontMetrics(AppFont.BODY_BOLD);
                
                if (!text.isEmpty() && fm.stringWidth(text) > available) {
                    c.setText(wrapHeaderHtml(text, fm, available));
                } else {
                    c.setText(text);
                }
                
                return c;
            }
        };
        header.setDefaultRenderer(headerRenderer);

        // === ROW RENDERER (dung chung, cung la "default" cho cac cot text thuong) ===
        stripedRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, 
                    boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel c = (JLabel) super.getTableCellRendererComponent(
                    t, value, isSelected, hasFocus, row, column);
                
                c.setBorder(new EmptyBorder(AppSpacing.SM, AppSpacing.LG, AppSpacing.SM, AppSpacing.LG));
                
                if (column == 0) {
                    c.setHorizontalAlignment(SwingConstants.CENTER);
                } else {
                    c.setHorizontalAlignment(SwingConstants.LEFT);
                }
                
                c.setBackground(rowColorProvider.colorFor(row, isSelected));
                c.setForeground(isSelected ? AppColor.TEXT_PRIMARY : AppColor.TABLE_ROW_TEXT);

                // Khi text dai hon cot, Swing ve dau "..." (ellipsis). Gan tooltip
                // = full text de hover van doc duoc toan bo (ma NV, email, vai tro...).
                String full = value != null ? value.toString() : "";
                if (!full.isEmpty()) {
                    FontMetrics fm = c.getFontMetrics(c.getFont());
                    int colW = t.getColumnModel().getColumn(column).getWidth();
                    int available = Math.max(0, colW - AppSpacing.LG * 2);
                    c.setToolTipText(fm.stringWidth(full) > available ? full : null);
                } else {
                    c.setToolTipText(null);
                }
                
                return c;
            }
        };

        applyStripedRendererToAllColumns();
        
        // ===== FIX: ẨN VIỀN KHI TABLE RỖNG =====
        table.setBackground(AppColor.WHITE);
        table.setOpaque(true);
    }

    private void applyStripedRendererToAllColumns() {
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(stripedRenderer);
        }
    }

    /**
     * JTable.createDefaultColumnsFromModel() se chay moi khi model bao cau
     * truc cot thay doi (vd DefaultTableModel#addColumn), xoa sach renderer
     * cua TAT CA cac TableColumn hien co. Ham nay khoi phuc lai dung trang
     * thai: striped renderer mac dinh cho moi cot, sau do de len tren cung
     * (ghi de) cac renderer rieng da duoc nguoi dung cau hinh qua
     * setImageColumn/setBadgeColumn/setAutoRowNumberColumn..., cuoi cung la
     * renderer + do rong + click handler cua cot Thao tac neu co.
     */
    private void reapplyAllColumnRenderers() {
        applyStripedRendererToAllColumns();
        for (Map.Entry<Integer, TableCellRenderer> entry : customRenderers.entrySet()) {
            int col = entry.getKey();
            if (col < table.getColumnCount()) {
                table.getColumnModel().getColumn(col).setCellRenderer(entry.getValue());
            }
        }
        if (actionColumn != null && actionColumnIndex >= 0 && actionColumnIndex < table.getColumnCount()) {
            applyActionColumnRenderer();
        }
        if (sorter != null) {
            sorter.disableSortingFor(toIntArray(autoNonSortableColumns));
        }
        updateAutoResizeModeForScroll();
    }

    private static int[] toIntArray(Set<Integer> set) {
        int[] arr = new int[set.size()];
        int i = 0;
        for (int v : set) arr[i++] = v;
        return arr;
    }

    // ===== ACTION COLUMN (API cu, giu tuong thich nguoc) =====
    public void enableActions(RowActionListener listener) {
        enableActions(listener, true, true, true);
    }

    public void enableActions(RowActionListener listener, boolean showView, boolean showEdit, boolean showDelete) {
        ActionColumn ac = new ActionColumn().header(ACTIONS_COLUMN_NAME);
        if (showView) ac.add("view", FontAwesomeSolid.EYE, AppColor.TABLE_VIEW_ACTION, "Xem chi tiết", listener::onView);
        if (showEdit) ac.add("edit", FontAwesomeSolid.EDIT, AppColor.TABLE_EDIT_ACTION, "Chỉnh sửa", listener::onEdit);
        if (showDelete) ac.add("delete", FontAwesomeSolid.TRASH, AppColor.TABLE_DELETE_ACTION, "Xóa", listener::onDelete);
        setActionColumn(ac);
    }

    /**
     * API moi, linh hoat hon enableActions(): tu khai bao danh sach nut icon
     * tuy y (khong gioi han view/edit/delete), moi nut co the bat/tat rieng
     * theo tung dong (xem {@link ActionColumn#add}).
     *
     * Vi du (them nut "Duyet don" chi active khi don dang cho duyet):
     *   table.setActionColumn(new ActionColumn()
     *       .add("view", FontAwesomeSolid.EYE, AppColor.TEXT_SECONDARY, "Xem", this::view)
     *       .add("approve", FontAwesomeSolid.CHECK, AppColor.SUCCESS, "Duyệt", this::approve,
     *              row -> "PENDING".equals(statusAt(row))));
     */
    public BaseTable setActionColumn(ActionColumn actionColumn) {
        this.actionColumn = actionColumn;

        if (!actionsEnabled) {
            model.addColumn(actionColumn.getHeaderName());
            actionsEnabled = true;
            actionColumnIndex = model.getColumnCount() - 1;
            autoNonSortableColumns.add(actionColumnIndex);
            // them cot moi -> JTable da xoa renderer cua moi cot, khoi phuc lai ngay
            reapplyAllColumnRenderers();
            installActionClickHandlerOnce();
        } else {
            table.getColumnModel().getColumn(actionColumnIndex).setHeaderValue(actionColumn.getHeaderName());
            applyActionColumnRenderer();
        }
        return this;
    }

    /**
     * Gan DUY NHAT 1 lan MouseListener/MouseMotionListener cho cot Thao tac,
     * luon doc truong {@code this.actionColumn} tai thoi diem click (KHONG
     * "dinh cung" vao 1 instance ActionColumn cu the). Truoc day
     * ActionColumn.installClickHandler(table, idx) duoc goi lai moi lan
     * setActionColumn() - nhung chi lan dau (luc actionsEnabled con false) la
     * thuc su gan listener; cac lan setActionColumn() sau (vd doi bo nut khi
     * subclass tu cau hinh lai) chi cap nhat duoc RENDERER, con MouseListener
     * van la ban dau => bam bat ky icon nao cung chay dung 1 hanh dong cua bo
     * nut dau tien (bug thuc te da gap). Sua bang cach gan handler 1 lan duy
     * nhat va luon doc actionColumn hien tai moi khi co click.
     */
    private void installActionClickHandlerOnce() {
        if (actionClickHandlerInstalled) return;
        actionClickHandlerInstalled = true;

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (actionColumn == null || actionColumn.isEmpty()) return;
                int viewCol = table.columnAtPoint(e.getPoint());
                int viewRow = table.rowAtPoint(e.getPoint());
                if (viewRow < 0 || viewCol != actionColumnIndex) return;

                Rectangle cellRect = table.getCellRect(viewRow, viewCol, false);
                int relativeX = e.getX() - cellRect.x;
                int modelRow = table.convertRowIndexToModel(viewRow);
                actionColumn.handleClick(modelRow, relativeX, cellRect.width);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                clearActionHover();
            }
        });

        table.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int viewCol = table.columnAtPoint(e.getPoint());
                int viewRow = table.rowAtPoint(e.getPoint());
                boolean overActions = viewCol == actionColumnIndex && viewRow >= 0
                        && actionColumn != null && !actionColumn.isEmpty();
                table.setCursor(overActions ? new Cursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());

                if (!overActions) {
                    clearActionHover();
                    return;
                }

                Rectangle cellRect = table.getCellRect(viewRow, viewCol, false);
                int relativeX = e.getX() - cellRect.x;
                int itemCount = actionColumn.getItems().size();
                int slotWidth = Math.max(1, cellRect.width / itemCount);
                int slot = Math.min(itemCount - 1, Math.max(0, relativeX / slotWidth));
                setActionHover(viewRow, slot);
            }
        });
    }

    /** Cap nhat o (viewRow, slot) dang hover trong cot Thao tac va chi repaint vung anh huong. */
    private void setActionHover(int viewRow, int slot) {
        if (viewRow == hoveredActionRow && slot == hoveredActionSlot) return;
        int oldRow = hoveredActionRow;
        hoveredActionRow = viewRow;
        hoveredActionSlot = slot;
        if (oldRow >= 0 && oldRow < table.getRowCount()) {
            table.repaint(table.getCellRect(oldRow, actionColumnIndex, false));
        }
        table.repaint(table.getCellRect(viewRow, actionColumnIndex, false));
    }

    /** Bo trang thai hover (khi chuot roi khoi cot Thao tac hoac roi khoi bang). */
    private void clearActionHover() {
        if (hoveredActionRow < 0) return;
        int oldRow = hoveredActionRow;
        hoveredActionRow = -1;
        hoveredActionSlot = -1;
        if (oldRow >= 0 && oldRow < table.getRowCount()) {
            table.repaint(table.getCellRect(oldRow, actionColumnIndex, false));
        }
    }

    private void applyActionColumnRenderer() {
        int width = actionColumnWidth();
        var col = table.getColumnModel().getColumn(actionColumnIndex);
        col.setCellRenderer(actionColumn.renderer(rowColorProvider, actionHoverState));
        // Min = preferred de AUTO_RESIZE khong co cot Thao tac xuong duoi muc
        // can de hien du icon + header "Thao tác" (tranh bi cat "Tha...").
        col.setMinWidth(width);
        col.setPreferredWidth(width);
        col.setMaxWidth(width + 24);
        col.setResizable(false);
    }

    /**
     * Do rong thuc te se dat cho cot "Thao tac": lay max giua goi y cua
     * ActionColumn (tinh theo so nut icon) va do rong can thiet de chu tieu de
     * ("Thao tác"...) khong bi cat. ActionColumn#preferredWidth() chi biet so
     * nut, khong biet ten header dai bao nhieu - voi bang chi co 1-2 nut, ten
     * header mac dinh "Thao tác" thuong dai hon phan danh cho icon, dan den bi
     * cat con "Tha..." (vd bang Nhat ky hoat dong, chi co nut "Xem thay doi").
     */
    private int actionColumnWidth() {
        int iconBasedWidth = actionColumn.preferredWidth();
        FontMetrics headerFm = table.getTableHeader().getFontMetrics(AppFont.BODY_BOLD);
        int headerTextWidth = headerFm.stringWidth(actionColumn.getHeaderName());
        int headerNeededWidth = headerTextWidth + AppSpacing.LG * 2 + AppSpacing.SM;
        return Math.max(iconBasedWidth, headerNeededWidth);
    }

    // ===== BADGE / STATUS COLUMN (vd cot "Trạng thái" hien thi dang pill mau) =====
    /**
     * Bien 1 cot thanh dang "badge" (StatBadge): moi gia tri o cot do se duoc
     * ve nhu 1 pill bo tron thay vi text thuong. labelFn/colorFn nhan vao
     * gia tri goc cua o (vd: chuoi "PENDING") va tra ve nhan hien thi / mau
     * tuong ung - khong gioi han domain, co the dung cho trang thai don hang,
     * ton kho, vai tro nguoi dung...
     *
     * Vi du (trang thai don hang, tai su dung OrderStatusUtil co san):
     *   table.setBadgeColumn(4, v -> OrderStatusUtil.label((String) v),
     *                            v -> OrderStatusUtil.color((String) v));
     *
     * Co the goi truoc hoac sau enableActions()/setActionColumn() - renderer
     * se tu dong duoc khoi phuc dung ngay ca khi mot cot moi duoc them vao sau do.
     */
    public BaseTable setBadgeColumn(int columnIndex, Function<Object, String> labelFn, Function<Object, Color> colorFn) {
        return setColumnRenderer(columnIndex, StatusColumn.renderer(labelFn, colorFn, rowColorProvider));
    }

    /** Badge column variant with centered badge, keeping the default badge renderer unchanged. */
    public BaseTable setCenteredBadgeColumn(int columnIndex, Function<Object, String> labelFn, Function<Object, Color> colorFn) {
        return setColumnRenderer(columnIndex, StatusColumn.renderer(labelFn, colorFn, rowColorProvider, FlowLayout.CENTER));
    }

    // ===== CUSTOM RENDERER (cho cac cot dac thu tung man hinh, vd thanh mini-progress ton kho) =====
    /**
     * Loi ra {@link #setColumnRenderer} (dang private, chi dung noi bo cho
     * setBadgeColumn/setImageColumn/setAutoRowNumberColumn) de man hinh nao
     * can 1 renderer rieng (khong nam trong 3 loai co san o tren) van gan
     * duoc ma khong phai sua BaseTable - renderer se tu dong duoc khoi phuc
     * dung khi co cot moi them vao sau (vd setActionColumn), giong het co
     * che ap dung cho badge/image/STT.
     *
     * Vi du (cot "Ton kho" dang thanh progress bar, xem StockLevelColumn):
     *   table.setCustomColumn(6, StockLevelColumn.renderer(table.rowColorProvider()));
     */
    public BaseTable setCustomColumn(int columnIndex, TableCellRenderer renderer) {
        return setColumnRenderer(columnIndex, renderer);
    }

    /** RowColorProvider dung chung cua bang nay - truyen cho cac renderer ben ngoai package com.components.table. */
    public RowColorProvider rowColorProvider() {
        return rowColorProvider;
    }

    // ===== ĐỘ RỘNG CỘT / CUỘN NGANG =====
 
    public BaseTable setColumnWidths(int... widths) {
        for (int i = 0; i < widths.length && i < table.getColumnModel().getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        updateAutoResizeModeForScroll();
        return this;
    }

    public BaseTable setColumnMinWidths(int... widths) {
        for (int i = 0; i < widths.length && i < table.getColumnModel().getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setMinWidth(widths[i]);
        }
        return this;
    }
    public BaseTable enableHorizontalScroll() {
        horizontalScrollEnabled = true;
        scrollPane.getViewport().addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                updateAutoResizeModeForScroll();
            }
        });
        updateAutoResizeModeForScroll();
        return this;
    }

    /**
     * Tinh lai autoResizeMode dua tren tuong quan giua tong do rong "ua
     * thich" cua cac cot va be rong hien tai cua viewport - xem
     * enableHorizontalScroll() de biet ly do can lam dieu nay thay vi chi
     * dat co dinh AUTO_RESIZE_OFF. Goi lai moi khi viewport doi kich thuoc
     * (resize cua so) hoac do rong cot duoc thiet lap lai qua setColumnWidths().
     */
    private void updateAutoResizeModeForScroll() {
        if (!horizontalScrollEnabled) return;

        int viewportWidth = scrollPane.getViewport().getWidth();
        if (viewportWidth <= 0) return; // chua render lan dau - componentResized se goi lai sau

        int totalPreferredWidth = 0;
        for (int i = 0; i < table.getColumnModel().getColumnCount(); i++) {
            totalPreferredWidth += table.getColumnModel().getColumn(i).getPreferredWidth();
        }

        table.setAutoResizeMode(totalPreferredWidth <= viewportWidth
                ? JTable.AUTO_RESIZE_ALL_COLUMNS
                : JTable.AUTO_RESIZE_OFF);
    }

    // ===== IMAGE COLUMN (vd cot anh san pham/avatar) =====
  
    public ImageColumn setImageColumn(int columnIndex, int size) {
        ImageColumn imageColumn = new ImageColumn(size);
        setColumnRenderer(columnIndex, imageColumn.renderer(rowColorProvider));
        autoNonSortableColumns.add(columnIndex);
        if (sorter != null) sorter.disableSortingFor(toIntArray(autoNonSortableColumns));
        return imageColumn;
    }

    public ImageColumn setImageColumn(int columnIndex, int size, int radius) {
        ImageColumn imageColumn = new ImageColumn(size, radius);
        setColumnRenderer(columnIndex, imageColumn.renderer(rowColorProvider));
        autoNonSortableColumns.add(columnIndex);
        if (sorter != null) sorter.disableSortingFor(toIntArray(autoNonSortableColumns));
        return imageColumn;
    }

    // ===== AUTO ROW NUMBER (cot STT) =====
   
    public AutoRowNumber setAutoRowNumberColumn(int columnIndex) {
        AutoRowNumber autoRowNumber = new AutoRowNumber();
        setColumnRenderer(columnIndex, autoRowNumber.renderer(rowColorProvider));
        autoNonSortableColumns.add(columnIndex);
        if (sorter != null) sorter.disableSortingFor(toIntArray(autoNonSortableColumns));
        return autoRowNumber;
    }

    private BaseTable setColumnRenderer(int columnIndex, TableCellRenderer renderer) {
        customRenderers.put(columnIndex, renderer);
        if (columnIndex < table.getColumnCount()) {
            table.getColumnModel().getColumn(columnIndex).setCellRenderer(renderer);
        }
        return this;
    }

    // ===== SORT (client-side, trong pham vi du lieu dang co trong model) =====
    /**
     * Bat sort cho table (click header de sap xep). Tu dong tat sort cho cac
     * cot da duoc cau hinh la anh/action/STT (khong co y nghia de sap xep).
     * Truyen them extraNonSortableColumns neu muon tat sort cho vai cot khac.
     *
     * Luu y: du lieu trong BaseTable thuong phan trang o server (xem
     * PaginationHelper) - sort o day chi ap dung cho cac dong dang tai trong
     * trang hien tai, khong sap xep toan bo tap du lieu.
     */
    public TableSorter enableSorting(int... extraNonSortableColumns) {
        if (sorter == null) {
            sorter = new TableSorter(model);
            table.setRowSorter(sorter);
            // Dam bao header ve lai mui ten sort ngay khi sort key thay doi
            // (bam header), ke ca khi UI mac dinh khong tu repaint vi renderer
            // header da bi thay bang renderer rieng cua BaseTable.
            sorter.addRowSorterListener(e -> table.getTableHeader().repaint());
        }
        for (int c : extraNonSortableColumns) autoNonSortableColumns.add(c);
        sorter.disableSortingFor(toIntArray(autoNonSortableColumns));
        return sorter;
    }

    public TableSorter getSorter() {
        return sorter != null ? sorter : enableSorting();
    }

    // ===== FILTER (client-side, tren cac dong dang co trong model) =====
    /**
     * Bat loc theo tu khoa. Tu dong bat sorting truoc neu chua co (TableFilter
     * dung chung RowSorter voi TableSorter). Truyen filterColumns de gioi han
     * chi loc tren mot so cot cu the, bo trong de loc tren tat ca cot.
     *
     * Vi du (ket hop voi BaseSearch da co san trong du an):
     *   TableFilter filter = table.enableFilter(1, 2); // chi loc cot Ten, Danh muc
     *   searchBar.addSearchListener(filter::filter);
     */
    public TableFilter enableFilter(int... filterColumns) {
        if (tableFilter == null) {
            tableFilter = new TableFilter(getSorter());
            if (filterColumns.length > 0) tableFilter.columns(filterColumns);
        }
        return tableFilter;
    }

    public TableFilter getFilter() {
        return tableFilter != null ? tableFilter : enableFilter();
    }

    // ===== PUBLIC METHODS =====
    public JTable getTable() { return table; }
    public DefaultTableModel getModel() { return model; }

    /**
     * Cho phep sua truc tiep mot so cot tren bang (theo chi so model).
     * Cot "Thao tac" (neu co) van khong sua duoc.
     */
    public BaseTable setEditableColumns(int... columns) {
        editableColumns.clear();
        if (columns != null) {
            for (int c : columns) {
                if (c >= 0) editableColumns.add(c);
            }
        }
        return this;
    }

    public void clear() {
        hoveredActionRow = -1;
        hoveredActionSlot = -1;
        model.setRowCount(0);
    }

    public void addRow(Object[] row) {
        if (actionsEnabled) {
            Object[] extended = Arrays.copyOf(row, row.length + 1);
            extended[row.length] = "";
            model.addRow(extended);
        } else {
            model.addRow(row);
        }
    }

    public BaseTable setRowHeight(int height) {
        table.setRowHeight(height);
        return this;
    }
}