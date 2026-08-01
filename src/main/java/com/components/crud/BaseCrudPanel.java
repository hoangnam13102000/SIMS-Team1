package com.components.crud;

import com.components.BaseDialog;
import com.components.BaseSearch;
import com.components.BaseTable;
import com.components.EmptyState;
import com.components.LoadingOverlay;
import com.components.Pagination;
import com.components.RowActionListener;
import com.components.SectionHeader;
import com.components.table.TableSorter;
import com.event.AutoRefresher;
import com.event.DataChangedEvent;
import com.importer.ImportRowResult;
import com.theme.AppColor;
import com.utils.FileUtil;
import com.utils.PaginationHelper;
import com.utils.TableExportUtil;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Panel quản lý CRUD dùng chung: header + (search) + table + (phân trang) + empty state + loading overlay.
 * <p>
 * Subclass chỉ cần khai báo cấu hình (cột, nhãn, icon...) và cách lấy/lưu/xóa dữ liệu qua các hook
 * bên dưới; toàn bộ phần layout/loading/search/phân trang/empty-state được BaseCrudPanel lo.
 * <p>
 * Subclass PHẢI gọi {@link #initialLoad()} ở cuối constructor, sau khi DAO/field riêng đã sẵn sàng.
 */
public abstract class BaseCrudPanel<T> extends JPanel {

    private static final String CARD_TABLE = "TABLE";
    private static final String CARD_EMPTY = "EMPTY";

    protected final BaseTable table;
    protected final Pagination pagination;
    protected final LoadingOverlay loadingOverlay;
    protected BaseSearch searchBar;
    protected JLabel countLabel;
    protected List<T> currentPageData;

    private JPanel dataContainer;
    private JPanel toolbarLeft;
    private CardLayout dataCardLayout;
    private EmptyState emptyState;

    protected BaseCrudPanel() {
        setLayout(new BorderLayout());
        setBackground(AppColor.PAGE_BG);
        setBorder(new EmptyBorder(20, 24, 20, 24));

        table = new BaseTable(getColumnNames());
        table.enableActions(new RowActionListener() {
            @Override public void onView(int modelRow) { if (supportsView()) viewRow(modelRow); }
            @Override public void onEdit(int modelRow) { if (supportsEdit()) editRow(modelRow); }
            @Override public void onDelete(int modelRow) { if (supportsDelete()) deleteRow(modelRow); }
        }, supportsView(), supportsEdit(), supportsDelete());

        // Bat sort (click header) tren toan bo cot - chi ap dung trong pham vi
        // trang dang tai (xem javadoc BaseTable#enableSorting). Cot nao la
        // chuoi da format so (vd "12.000.000") thi dung numericStringComparator
        // qua hook numericColumns() de sort dung theo gia tri thay vi alphabet.
        table.enableSorting();
        for (int col : numericColumns()) {
            table.getSorter().comparator(col, TableSorter.numericStringComparator());
        }

        pagination = new Pagination();
        pagination.setVisiblePages(5);
        pagination.addPropertyChangeListener("pageChanged", e -> loadData((int) e.getNewValue(), pagination.getPageSize()));
        pagination.addPropertyChangeListener("pageSizeChanged", e -> loadData(1, (int) e.getNewValue()));

        loadingOverlay = new LoadingOverlay("Đang tải dữ liệu...");

        add(buildPageHeader(), BorderLayout.NORTH);
        add(buildTableCard(buildToolbar()), BorderLayout.CENTER);
        AutoRefresher.bind(this, DataChangedEvent.class, 400, this::reload);
    }

    /** Gọi ở cuối constructor của subclass để tải dữ liệu lần đầu. */
    protected void initialLoad() {
        maybeAddTrashButton();
        loadData(1, defaultPageSize());
        loadAutocompleteSuggestionsAsync();
    }

    protected int defaultPageSize() { return 10; }

    // ---------------------------------------------------------------
    // Hook bắt buộc subclass triển khai
    // ---------------------------------------------------------------

    protected abstract FontAwesomeSolid getIcon();
    protected abstract String getPageTitle();
    protected abstract String getPageSubtitle();
    /** Nhãn nút "Thêm..." trên header; trả về null để ẩn nút thêm. */
    protected abstract String getAddButtonLabel();
    protected abstract String[] getColumnNames();
    protected abstract Object[] mapRowToColumns(T item);
    /** Tên entity viết thường, dùng trong thông báo/đếm số lượng (vd "điện thoại", "danh mục"). */
    protected abstract String getEntityLabel();
    protected abstract String getItemDisplayName(T item);
    protected abstract PaginationHelper.PaginationResult<T> fetchPage(int page, int pageSize);
    /** Mở form thêm mới (item == null) hoặc sửa (item != null). */
    protected abstract void openForm(T item);
    protected abstract boolean deleteItem(T item);

    // ---------------------------------------------------------------
    // Hook tùy chọn, có default
    // ---------------------------------------------------------------

    protected boolean supportsView() { return false; }
    protected boolean supportsEdit() { return true; }
    protected boolean supportsDelete() { return true; }
    protected boolean showPagination() { return true; }
    /** false => ẩn 2 nút "Xuất CSV"/"Xuất Excel" trên header. */
    protected boolean supportsExport() { return true; }

    /**
     * null (mặc định) => KHÔNG bật tính năng "Thùng rác" (xóa mềm/khôi phục)
     * cho panel này. Subclass override và trả về 1 {@link TrashConfig} (thường
     * bọc quanh 1 {@link com.dao.SoftDeleteDAO}) để tự động có nút "Thùng rác"
     * trên header, mở {@link com.components.TrashDialog} cho phép khôi phục
     * (và tùy chọn xóa vĩnh viễn) — không cần tự viết lại UI.
     */
    protected TrashConfig<T> getTrashConfig() { return null; }

    /** true => hiện nút "Nhập dữ liệu" (Excel/Word) trên header. Subclass phải override {@link #importRow} khi bật. */
    protected boolean supportsImport() { return false; }

    /**
     * Tên các cột theo đúng thứ tự file Excel/Word cần có (không tính cột ID tự sinh),
     * hiển thị làm hướng dẫn cho người dùng trong dialog import. Mặc định dùng lại
     * {@link #getColumnNames()} — subclass nên override nếu cột import khác cột hiển thị bảng.
     */
    protected String[] getImportColumns() { return getColumnNames(); }

    /** Ghi chú thêm hiển thị trong dialog import (vd yêu cầu định dạng giá trị). Mặc định không có. */
    protected String getImportInstructions() { return null; }

    /**
     * Xử lý 1 dòng dữ liệu từ file import (đã bỏ qua dòng tiêu đề) — parse, validate rồi lưu
     * vào CSDL qua DAO. Subclass BẮT BUỘC override khi {@link #supportsImport()} trả về true.
     */
    protected ImportRowResult importRow(String[] cells, int rowNumber) {
        throw new UnsupportedOperationException(
                getClass().getSimpleName() + " khai báo supportsImport()=true nhưng chưa override importRow(...)");
    }

    /** null => không hiện thanh tìm kiếm. */
    protected String getSearchPlaceholder() { return null; }

    /**
     * Chi so cac cot da format thanh chuoi so (vd "12.000.000") can sort
     * theo gia tri that thay vi thu tu chu cai. Mac dinh khong co cot nao.
     */
    protected int[] numericColumns() { return new int[0]; }

    /**
     * true => thanh tim kiem loc ngay tren du lieu client (BaseTable.enableFilter),
     * dung cho cac trang khong phan trang server (showPagination() == false,
     * toan bo du lieu da nam san trong bang). Mac dinh false: tim kiem se goi
     * searchPage(...) len server nhu truoc gio (phu hop du lieu lon/phan trang).
     */
    protected boolean useClientSideFilter() { return false; }

    /**
     * Toàn bộ dữ liệu (không phân trang) dùng để export CSV/Excel.
     * Mặc định gom qua fetchPage với 1 trang lớn; subclass nên override bằng
     * một query "lấy tất cả" thật sự (vd DAO.getAll()) nếu có, để tránh phụ
     * thuộc giới hạn OFFSET/FETCH của trang.
     */
    protected List<T> fetchAllForExport() {
        return fetchPage(1, 100_000).getData();
    }

    protected PaginationHelper.PaginationResult<T> searchPage(String keyword, int page, int pageSize) {
        return fetchPage(page, pageSize);
    }

    /**
     * Danh sach goi y autocomplete cho thanh tim kiem (vd: toan bo ten san
     * pham). Mac dinh tra ve null - KHONG bat autocomplete. Subclass override
     * va tra ve 1 danh sach "ten hien thi" (thuong lay tu 1 truy van DAO nhe,
     * vd getAll() roi map sang ten) de bat tinh nang goi y khi go tim kiem.
     * <p>
     * Duoc goi tren background thread (SwingWorker) trong
     * {@link #loadAutocompleteSuggestionsAsync()} - an toan de thuc hien
     * truy van DAO (blocking) tai day, KHONG duoc dong bat cu thao tac Swing
     * nao truc tiep trong ham nay.
     */
    protected List<String> fetchAutocompleteSuggestions() {
        return null;
    }

    /**
     * Tai lai danh sach goi y autocomplete tren 1 background thread rieng
     * (khong lam treo UI), roi cap nhat vao searchBar khi xong. Duoc goi tu
     * {@link #initialLoad()} (lan dau) va {@link #reload()} (moi khi du lieu
     * thay doi) de goi y luon "moi" theo du lieu hien co - vd them 1 dien
     * thoai moi thi ngay sau do da co the go tim kiem ra ten do.
     */
    private void loadAutocompleteSuggestionsAsync() {
        if (searchBar == null) return;

        SwingWorker<List<String>, Void> worker = new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() {
                try {
                    return fetchAutocompleteSuggestions();
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            }

            @Override
            protected void done() {
                try {
                    List<String> names = get();
                    if (names != null) searchBar.setSuggestions(names);
                } catch (Exception ignored) {
                    // Loi tai goi y khong nghiem trong - khong hien loi cho nguoi
                    // dung, ho van tim kiem binh thuong duoc, chi la thieu goi y.
                }
            }
        };
        worker.execute();
    }

    protected void viewRow(int modelRow) { /* subclass override nếu supportsView() == true */ }

    /** Gọi sau khi thêm/sửa/xóa thành công để bắn event bus, ... Mặc định không làm gì. */
    protected void onDataChanged() { }

    protected String getDeleteFailureMessage(T item) {
        return "Xóa thất bại. " + getEntityLabel() + " này có thể đang được sử dụng ở nơi khác.";
    }

    // ---------------------------------------------------------------
    // Layout
    // ---------------------------------------------------------------

    /**
     * Header duoc luu lai (thay vi chi la bien local) de co the them nut
     * "Thùng rác" SAU nay, tu initialLoad() - xem giai thich o
     * maybeAddTrashButton() ben duoi.
     */
    private SectionHeader pageHeader;

    private JPanel buildPageHeader() {
        SectionHeader header = new SectionHeader(getIcon(), AppColor.ACCENT, getPageTitle(), getPageSubtitle());
        this.pageHeader = header;

        if (supportsExport()) {
            header.addOverflowAction("Xuất CSV", FontAwesomeSolid.FILE_CSV, () -> exportData("csv"));
            header.addOverflowAction("Xuất Excel", FontAwesomeSolid.FILE_EXCEL, () -> exportData("xlsx"));
        }

        if (supportsImport()) {
            header.addOverflowAction("Nhập dữ liệu", FontAwesomeSolid.UPLOAD, this::openImportDialog);
        }

        // Thùng rác (nếu bật) được thêm SAU, từ maybeAddTrashButton() gọi trong
        // initialLoad() - xem giải thích ở đó (subclass field chưa sẵn sàng
        // tại thời điểm buildPageHeader() này chạy).

        if (getAddButtonLabel() != null) {
            header.addButton(getAddButtonLabel(), FontAwesomeSolid.PLUS,
                    SectionHeader.ButtonStyle.PRIMARY, () -> openForm(null));
        }

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setBorder(new EmptyBorder(0, 0, 16, 0));
        wrapper.add(header, BorderLayout.CENTER);
        return wrapper;
    }

    /**
     * Them nut "Thùng rác" NEU {@link #getTrashConfig()} != null.
     * <p>
     * QUAN TRONG: hàm này PHẢI được gọi từ {@link #initialLoad()} (được
     * subclass gọi ở CUỐI constructor của chính nó, SAU KHI field riêng như
     * DAO đã được gán) - KHÔNG được gọi trực tiếp từ buildPageHeader()/
     * constructor của BaseCrudPanel. Lý do: Java chạy hết constructor lớp
     * cha (BaseCrudPanel) TRƯỚC, rồi mới chạy phần khởi tạo field của lớp
     * con (ví dụ {@code phoneDAO = new PhoneDAO();}) - nếu gọi
     * getTrashConfig() (thường dùng field DAO của subclass) ngay trong
     * buildPageHeader(), field đó CHƯA được gán, gây
     * NullPointerException ngay lúc khởi tạo màn hình.
     */
    private void maybeAddTrashButton() {
        if (pageHeader == null || getTrashConfig() == null) return;
        pageHeader.addOverflowAction("Thùng rác", FontAwesomeSolid.TRASH, this::openTrash);
    }

    private JPanel buildToolbar() {
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setOpaque(false);
        toolbar.setBorder(new EmptyBorder(14, 16, 14, 16));

        toolbarLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        toolbarLeft.setOpaque(false);

        if (getSearchPlaceholder() != null) {
            searchBar = new BaseSearch(getSearchPlaceholder());
            if (useClientSideFilter()) {
                searchBar.onSearch(table.getFilter()::filter);
            } else {
                searchBar.onSearch(this::searchItem);
            }
            toolbarLeft.add(searchBar);
        }
        toolbar.add(toolbarLeft, BorderLayout.WEST);

        countLabel = new JLabel();
        countLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        countLabel.setForeground(AppColor.TEXT_MUTED);
        toolbar.add(countLabel, BorderLayout.EAST);
        return toolbar;
    }

    /**
     * Thêm control lọc bên cạnh ô tìm kiếm. Gọi từ constructor của subclass
     * sau super() và trước initialLoad().
     */
    protected void addToolbarFilter(Component component) {
        if (toolbarLeft != null && component != null) {
            toolbarLeft.add(component);
            toolbarLeft.revalidate();
            toolbarLeft.repaint();
        }
    }

    /** Áp dụng lại search + các bộ lọc của subclass và quay về trang đầu. */
    protected void applyFilters() {
        String keyword = searchBar != null && searchBar.getText() != null ? searchBar.getText().trim() : "";
        if (!keyword.isEmpty()) {
            searchItem(keyword);
        } else {
            loadData(1, showPagination() ? pagination.getPageSize() : Integer.MAX_VALUE);
        }
    }

    private JPanel buildTableCard(JPanel toolbar) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(AppColor.WHITE);
        card.setBorder(BorderFactory.createLineBorder(AppColor.BORDER, 1, true));

        JPanel toolbarWrapper = new JPanel(new BorderLayout());
        toolbarWrapper.setBackground(AppColor.WHITE);
        toolbarWrapper.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, AppColor.BORDER));
        toolbarWrapper.add(toolbar, BorderLayout.CENTER);

        emptyState = EmptyState.noData(getEntityLabel());
        emptyState.setAction(getAddButtonLabel(), () -> openForm(null));

        dataCardLayout = new CardLayout();
        dataContainer = new JPanel(dataCardLayout);
        dataContainer.setOpaque(false);
        dataContainer.add(table, CARD_TABLE);
        dataContainer.add(emptyState, CARD_EMPTY);

        JPanel wrappedDataContainer = LoadingOverlay.attach(dataContainer, loadingOverlay);

        card.add(toolbarWrapper, BorderLayout.NORTH);
        card.add(wrappedDataContainer, BorderLayout.CENTER);

        if (showPagination()) {
            JPanel paginationWrapper = new JPanel(new BorderLayout());
            paginationWrapper.setBackground(AppColor.WHITE);
            paginationWrapper.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, AppColor.BORDER));
            paginationWrapper.add(pagination, BorderLayout.CENTER);
            card.add(paginationWrapper, BorderLayout.SOUTH);
        }

        return card;
    }

    // ---------------------------------------------------------------
    // Data loading
    // ---------------------------------------------------------------

    private void loadData(int page, int pageSize) {
        loadingOverlay.start("Đang tải dữ liệu...");

        SwingWorker<PaginationHelper.PaginationResult<T>, Void> worker = new SwingWorker<PaginationHelper.PaginationResult<T>, Void>() {
            @Override
            protected PaginationHelper.PaginationResult<T> doInBackground() {
                return fetchPage(page, pageSize);
            }

            @Override
            protected void done() {
                try {
                    renderTable(get());
                } catch (Exception e) {
                    e.printStackTrace();
                    BaseDialog.error(BaseCrudPanel.this, "Lỗi", "Không thể tải dữ liệu: " + e.getMessage());
                } finally {
                    loadingOverlay.stop();
                }
            }
        };
        worker.execute();
    }

    private void searchItem(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            loadData(1, showPagination() ? pagination.getPageSize() : Integer.MAX_VALUE);
            return;
        }

        loadingOverlay.start("Đang tìm kiếm...");

        SwingWorker<PaginationHelper.PaginationResult<T>, Void> worker = new SwingWorker<PaginationHelper.PaginationResult<T>, Void>() {
            @Override
            protected PaginationHelper.PaginationResult<T> doInBackground() {
                return searchPage(keyword, 1, showPagination() ? pagination.getPageSize() : Integer.MAX_VALUE);
            }

            @Override
            protected void done() {
                try {
                    renderTable(get());
                } catch (Exception e) {
                    e.printStackTrace();
                    BaseDialog.error(BaseCrudPanel.this, "Lỗi", "Không thể tìm kiếm: " + e.getMessage());
                } finally {
                    loadingOverlay.stop();
                }
            }
        };
        worker.execute();
    }

    private void renderTable(PaginationHelper.PaginationResult<T> result) {
        currentPageData = result.getData();

        table.clear();
        for (T item : currentPageData) {
            table.addRow(mapRowToColumns(item));
        }
        table.getTable().revalidate();
        table.getTable().repaint();

        if (showPagination()) {
            pagination.setTotalItems(result.getTotalRecords());
            pagination.setPageSize(result.getPageSize());
            pagination.setCurrentPage(result.getCurrentPage());
        }

        countLabel.setText("Tổng cộng: " + result.getTotalRecords() + " " + getEntityLabel());

        if (currentPageData == null || currentPageData.isEmpty()) {
            String keyword = searchBar != null && searchBar.getText() != null ? searchBar.getText().trim() : "";
            if (!keyword.isEmpty()) {
                emptyState.setTitle("Không tìm thấy kết quả")
                        .setSubtitle("Không có " + getEntityLabel() + " nào khớp với \"" + keyword + "\"")
                        .setAction(null, null);
            } else {
                emptyState.setTitle("Chưa có " + getEntityLabel() + " nào")
                        .setSubtitle(getAddButtonLabel() != null
                                ? "Bấm \"" + getAddButtonLabel() + "\" để tạo mới"
                                : "Chưa có dữ liệu")
                        .setAction(getAddButtonLabel(), () -> openForm(null));
            }
            dataCardLayout.show(dataContainer, CARD_EMPTY);
        } else {
            dataCardLayout.show(dataContainer, CARD_TABLE);
        }
        afterRender(result);
    }
    
    protected void afterRender(PaginationHelper.PaginationResult<T> result) {
    }

    /** Tải lại đúng trang/từ khóa đang hiển thị. Public để subclass gọi khi có sự kiện bên ngoài (vd websocket). */
    public void reload() {
        String keyword = searchBar != null && searchBar.getText() != null ? searchBar.getText().trim() : "";
        if (!keyword.isEmpty()) {
            searchItem(keyword);
        } else if (showPagination()) {
            loadData(pagination.getCurrentPage(), pagination.getPageSize());
        } else {
            loadData(1, Integer.MAX_VALUE);
        }
        // Du lieu vua thay doi (them/sua/xoa/websocket...) - lam moi lai danh
        // sach goi y autocomplete de phan anh dung du lieu hien tai.
        loadAutocompleteSuggestionsAsync();
    }

    // ---------------------------------------------------------------
    // Row actions
    // ---------------------------------------------------------------

    protected T rowToItem(int modelRow) {
        if (currentPageData == null || modelRow < 0 || modelRow >= currentPageData.size()) return null;
        return currentPageData.get(modelRow);
    }

    private void editRow(int modelRow) {
        T item = rowToItem(modelRow);
        if (item != null) openForm(item);
    }

    private void deleteRow(int modelRow) {
        T item = rowToItem(modelRow);
        if (item == null) return;

        boolean confirmed = BaseDialog.confirmDelete(this, getEntityLabel(), getItemDisplayName(item));
        if (!confirmed) return;

        boolean ok = deleteItem(item);
        if (ok) {
            BaseDialog.success(this, "Thành công", "Đã xóa " + getEntityLabel() + " \"" + getItemDisplayName(item) + "\"");
            // Chi can onDataChanged() - AutoRefresher da bind(DataChangedEvent -> reload())
            // o constructor, tu goi reload() (debounce 400ms). Goi reload() them o day
            // se lam loadingOverlay hien 2 lan lien tiep (1 lan ngay, 1 lan ~400ms sau).
            onDataChanged();
        } else {
            BaseDialog.error(this, "Không thể xóa", getDeleteFailureMessage(item));
        }
    }

    /**
     * Mở dialog "Thùng rác" (chỉ khi {@link #getTrashConfig()} != null). Sau
     * khi khôi phục/xóa vĩnh viễn thành công 1 mục, {@link #onDataChanged()}
     * được gọi để làm mới lại bảng chính (qua AutoRefresher đã bind sẵn).
     */
    private void openTrash() {
        TrashConfig<T> trash = getTrashConfig();
        if (trash == null) return;

        com.components.TrashDialog.show(
                SwingUtilities.getWindowAncestor(this),
                "Thùng rác - " + getEntityLabel(),
                trash.fetchDeleted(),
                this::getItemDisplayName,
                trash.restore(),
                trash.hardDelete(),
                this::onDataChanged
        );
    }

    /**
     * Helper để subclass gắn làm {@link CrudCallback} cho BaseFormDialog:
     * {@code dialog.onSaved(this::handleFormSaved);}
     */
    protected void handleFormSaved(T item, CrudMode mode) {
        BaseDialog.success(this, "Thành công",
                mode == CrudMode.ADD ? "Đã thêm " + getEntityLabel() + " mới" : "Đã cập nhật " + getEntityLabel());
        // Chi can onDataChanged() - AutoRefresher tu goi reload(), tranh loadingOverlay
        // hien 2 lan lien tiep (xem giai thich chi tiet o deleteRow()).
        onDataChanged();
    }

    // ---------------------------------------------------------------
    // Import Excel / Word (co quet virus truoc khi doc du lieu)
    // ---------------------------------------------------------------

    private void openImportDialog() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        ImportFileDialog dialog = new ImportFileDialog(owner, getPageTitle(), getImportColumns(),
                getImportInstructions(), this::importRow);
        dialog.setOnFinished((success, failed) -> {
            if (success > 0) {
                // Chi can onDataChanged() - AutoRefresher tu goi reload(), tranh loadingOverlay
                // hien 2 lan lien tiep (xem giai thich chi tiet o deleteRow()).
                onDataChanged();
            }
        });
        dialog.setVisible(true);
    }

    // ---------------------------------------------------------------
    // Export CSV / Excel
    // ---------------------------------------------------------------

    /** format: "csv" hoặc "xlsx". Mở hộp thoại Save As rồi xuất toàn bộ dữ liệu (không chỉ trang hiện tại). */
    private void exportData(String format) {
        String defaultName = sanitizeFileName(getEntityLabel()) + "_" + timestamp() + "." + format;
        File chosen = FileUtil.chooseSaveLocation(this, defaultName);
        if (chosen == null) return;

        File file = ensureExtension(chosen, format);

        loadingOverlay.start("Đang xuất dữ liệu...");
        SwingWorker<Integer, Void> worker = new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() throws Exception {
                List<T> data = fetchAllForExport();
                System.out.println("[Export] " + getEntityLabel() + ": fetchAllForExport() tra ve "
                        + (data == null ? "null" : data.size()) + " dong.");

                List<Object[]> rows = new ArrayList<>();
                if (data != null) {
                    for (T item : data) {
                        rows.add(mapRowToColumns(item));
                    }
                }
                if ("csv".equals(format)) {
                    TableExportUtil.exportCsv(file, getColumnNames(), rows);
                } else {
                    TableExportUtil.exportExcel(file, sheetTitle(), getColumnNames(), rows);
                }
                return rows.size();
            }

            @Override
            protected void done() {
                loadingOverlay.stop();
                try {
                    int rowCount = get();
                    if (rowCount == 0) {
                        BaseDialog.info(BaseCrudPanel.this, "Không có dữ liệu",
                                "Đã tạo file \"" + file.getName() + "\" nhưng không có dòng dữ liệu nào để xuất.\n"
                                        + "Kiểm tra lại bộ lọc/tìm kiếm, hoặc bảng " + getEntityLabel() + " hiện đang trống.");
                    } else {
                        BaseDialog.success(BaseCrudPanel.this, "Thành công",
                                "Đã xuất " + rowCount + " dòng vào file \"" + file.getName() + "\"");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    BaseDialog.error(BaseCrudPanel.this, "Lỗi", "Xuất file thất bại: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private static String sanitizeFileName(String name) {
        String cleaned = name.replaceAll("[^\\p{L}\\p{N}]+", "_");
        return cleaned.isEmpty() ? "du_lieu" : cleaned;
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
    }

    private static File ensureExtension(File file, String ext) {
        String name = file.getName();
        if (name.toLowerCase().endsWith("." + ext)) return file;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return new File(file.getParentFile(), base + "." + ext);
    }

    /** Tên sheet Excel: bỏ ký tự không hợp lệ, giới hạn 31 ký tự theo chuẩn OOXML. */
    private String sheetTitle() {
        String cleaned = getEntityLabel().replaceAll("[\\\\/*?:\\[\\]]", "").trim();
        if (cleaned.isEmpty()) cleaned = "Sheet1";
        return cleaned.length() > 31 ? cleaned.substring(0, 31) : cleaned;
    }
}