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

    // ===== MÀU SẮC =====
    // HEADER_FG luon trang vi TABLE_HEADER_BG (Light lan Dark) deu la nen toi -
    // khong can doi theo theme. ROW_EVEN/BORDER_COLOR thi CO doi theo theme nen
    // khong the khai bao "static final" (se bi dong cung 1 gia tri mai mai tu
    // lan load class dau tien) - doc truc tiep AppColor.XXX o tung noi dung.
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

    /** Cac cot khong nen cho sort (action, anh, STT...) - tu dong gom lai khi cau hinh. */
    private final Set<Integer> autoNonSortableColumns = new LinkedHashSet<>();
    /** Ghi nho renderer da gan cho tung cot de co the re-apply sau khi them cot moi vao model. */
    private final Map<Integer, TableCellRenderer> customRenderers = new LinkedHashMap<>();

    private TableSorter sorter;
    private TableFilter tableFilter;

    /** True khi enableHorizontalScroll() da duoc goi cho bang nay - xem javadoc cua ham do. */
    private boolean horizontalScrollEnabled = false;

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
                return false;
            }
        };

        table = new JTable(model);
        styleTable();

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
     * Icon mui ten sort cho 1 cot header (view index {@code column}):
     *  - null                      neu cot khong sortable (action/anh/STT...)
     *  - mui ten len+xuong MO NHAT neu cot sortable nhung chua duoc chon de
     *    sort - day la "goi y" cho nguoi dung biet co the bam vao de sort,
     *    luon hien thi san thay vi chi xuat hien sau khi da click 1 lan.
     *  - mui ten RO, 1 chieu       neu cot dang la sort key hien tai (ho tro
     *    ca multi-column sort qua Shift+click, hien them so thu tu uu tien).
     */
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
        header.setPreferredSize(new Dimension(header.getPreferredSize().width, 50));
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
                c.setBorder(new EmptyBorder(AppSpacing.SM, AppSpacing.LG, AppSpacing.SM, AppSpacing.LG));
                c.setFont(AppFont.BODY_BOLD);
                c.setHorizontalTextPosition(SwingConstants.LEFT);
                c.setIcon(sortIconFor(table, column));
                c.setIconTextGap(6);
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
        });

        table.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int viewCol = table.columnAtPoint(e.getPoint());
                boolean overActions = viewCol == actionColumnIndex && table.rowAtPoint(e.getPoint()) >= 0;
                table.setCursor(overActions ? new Cursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
            }
        });
    }

    private void applyActionColumnRenderer() {
        int width = actionColumnWidth();
        table.getColumnModel().getColumn(actionColumnIndex).setCellRenderer(actionColumn.renderer(rowColorProvider));
        table.getColumnModel().getColumn(actionColumnIndex).setPreferredWidth(width);
        table.getColumnModel().getColumn(actionColumnIndex).setMaxWidth(width + 16);
        table.getColumnModel().getColumn(actionColumnIndex).setResizable(false);
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

    // ===== ĐỘ RỘNG CỘT / CUỘN NGANG =====
    /**
     * Dat do rong "ua thich" (preferred width, px) cho tung cot theo dung thu
     * tu khai bao trong constructor - mang co the ngan hon so cot (cac cot
     * con lai giu nguyen do rong mac dinh cua JTable).
     *
     * CHI co tac dung ro rang khi ket hop voi {@link #enableHorizontalScroll()}:
     * mac dinh JTable luon co bop/gian moi cot vua khit voi be rong scrollpane
     * (khong bao gio vuot qua, cung khong bao gio hien thanh cuon ngang), nen
     * neu khong goi enableHorizontalScroll() thi cac gia tri o day chi la
     * "ty le uu tien" ban dau roi van bi ep lai vua khung.
     *
     * Vi du (cot "Chi tiết" can nhieu cho hon cac cot con lai):
     *   table.setColumnWidths(140, 140, 160, 140, 500).enableHorizontalScroll();
     */
    public BaseTable setColumnWidths(int... widths) {
        for (int i = 0; i < widths.length && i < table.getColumnModel().getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        updateAutoResizeModeForScroll();
        return this;
    }

    /**
     * Cho phep bang cuon ngang khi tong do rong cac cot (xem setColumnWidths)
     * vuot qua be rong scrollpane, thay vi hanh vi mac dinh cua JTable la luon
     * co bop/gian cot vua khit voi vung nhin (AUTO_RESIZE_SUBSEQUENT_COLUMNS)
     * - lam noi dung cot dai (vd mo ta/chi tiet) bi cat mat vinh vien ma
     * khong co cach nao xem het duoc.
     *
     * KHONG chi ep cung AUTO_RESIZE_OFF: neu lam vay, nhung luc cua so du
     * rong hon tong do rong cac cot, JTable se phinh to bang cho khop
     * scrollpane nhung KHONG chia deu phan du cho cac cot (do la co che rieng
     * cua AUTO_RESIZE_*, khong tu dong bat len chi vi bang duoc phinh to) -
     * de lai khoang trang ben phai, nhin nhu toan bo noi dung bi don het ve
     * ben trai. Thay vao do, ta tu theo doi kich thuoc viewport (qua
     * ComponentListener) va CHUYEN DOI qua lai giua AUTO_RESIZE_ALL_COLUMNS
     * (khi noi dung vua/nho hon khung - cot tu gian lap day, nhin gon nhu cac
     * bang khac) va AUTO_RESIZE_OFF (khi noi dung vuot khung - giu nguyen do
     * rong da dat, cho phep cuon ngang) - dung y muon "gian khi vua, cuon khi
     * tran" thay vi chi mot trong hai trang thai co dinh.
     *
     * Chi nen bat cho cac bang co cot noi dung dai/khong co do dai co dinh
     * (vd cot "Chi tiết" trong Nhat ky hoat dong) - cac bang thong thuong khac
     * van nen giu hanh vi mac dinh (tu gian cot vua khung, nhin gon hon) nen
     * day la tuy chon rieng tung bang, khong bat mac dinh cho tat ca BaseTable.
     */
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
    /**
     * Bien 1 cot thanh anh thu nho bo goc (kich thuoc vuong {@code size}px),
     * tai bat dong bo va cache theo duong dan de khong giat UI. Tra ve
     * {@link ImageColumn} de co the goi invalidate(path)/clearCache() sau khi
     * sua/xoa anh.
     *
     * Vi du:
     *   ImageColumn avatarCol = table.setImageColumn(1, 40);
     *   ...
     *   avatarCol.invalidate(phone.getImagePath()); // sau khi sua anh
     */
    public ImageColumn setImageColumn(int columnIndex, int size) {
        ImageColumn imageColumn = new ImageColumn(size);
        setColumnRenderer(columnIndex, imageColumn.renderer(rowColorProvider));
        autoNonSortableColumns.add(columnIndex);
        return imageColumn;
    }

    public ImageColumn setImageColumn(int columnIndex, int size, int radius) {
        ImageColumn imageColumn = new ImageColumn(size, radius);
        setColumnRenderer(columnIndex, imageColumn.renderer(rowColorProvider));
        autoNonSortableColumns.add(columnIndex);
        return imageColumn;
    }

    // ===== AUTO ROW NUMBER (cot STT) =====
    /**
     * Bien 1 cot thanh STT tu dong danh so theo vi tri dong dang hien thi
     * (sau sort/filter) + pageOffset. Tra ve {@link AutoRowNumber} de goi
     * setPageOffset(...) moi khi doi trang.
     *
     * Vi du:
     *   AutoRowNumber stt = table.setAutoRowNumberColumn(0);
     *   ...
     *   stt.setPageOffset((currentPage - 1) * pageSize);
     *   table.getTable().repaint();
     */
    public AutoRowNumber setAutoRowNumberColumn(int columnIndex) {
        AutoRowNumber autoRowNumber = new AutoRowNumber();
        setColumnRenderer(columnIndex, autoRowNumber.renderer(rowColorProvider));
        autoNonSortableColumns.add(columnIndex);
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

    public void clear() {
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