package com.view.admin.supplier;

import com.components.crud.BaseCrudPanel;
import com.components.crud.CrudMode;
import com.components.crud.TrashConfig;
import com.components.table.AutoRowNumber;
import com.dao.SupplierDAO;
import com.model.Supplier;
import com.utils.PaginationHelper;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import java.awt.Frame;
import java.awt.Window;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;

public class SupplierPanel extends BaseCrudPanel<Supplier> {

    private final SupplierDAO supplierDAO = new SupplierDAO();
    private AutoRowNumber stt;

    public SupplierPanel() {
        super();

        stt = table.setAutoRowNumberColumn(0);
        // Không enableHorizontalScroll → cột co giãn theo khung, không scrollbar ngang.
        // Cột "Số SP đang cung cấp" (index 6) cần min ~130px để header hiện đủ 1 dòng.
        table.setColumnWidths(50, 150, 105, 145, 170, 150, 140);
        table.setColumnMinWidths(40, 100, 85, 110, 120, 110, 130);

        initialLoad();
    }

    @Override
    protected FontAwesomeSolid getIcon() { return FontAwesomeSolid.TRUCK; }

    @Override
    protected String getPageTitle() { return "Quản lý nhà cung cấp"; }

    @Override
    protected String getPageSubtitle() { return "Quản lý danh sách nhà cung cấp và mặt hàng họ cung cấp"; }

    @Override
    protected String getAddButtonLabel() { return "Thêm nhà cung cấp"; }

    @Override
    protected String[] getColumnNames() {
        return new String[]{"STT", "Tên nhà cung cấp", "Số điện thoại", "Email", "Địa chỉ", "Mặt hàng cung cấp", "Số SP đang cung cấp"};
    }

    @Override
    protected Object[] mapRowToColumns(Supplier item) {
        return new Object[]{
                "",
                item.getSupplierName(),
                emptyDash(item.getPhone()),
                emptyDash(item.getEmail()),
                emptyDash(item.getAddress()),
                emptyDash(item.getSuppliedItems()),
                item.getProductCount()
        };
    }

    @Override
    protected int[] numericColumns() { return new int[]{6}; }

    @Override
    protected String getEntityLabel() { return "nhà cung cấp"; }

    @Override
    protected void afterRender(PaginationHelper.PaginationResult<Supplier> result) {
        stt.setPageOffset((result.getCurrentPage() - 1) * result.getPageSize());
        table.getTable().repaint();
    }

    @Override
    protected String getItemDisplayName(Supplier item) {
        return item.getSupplierName();
    }

    @Override
    protected PaginationHelper.PaginationResult<Supplier> fetchPage(int page, int pageSize) {
        return withProductCounts(supplierDAO.getPaged(page, pageSize));
    }

    @Override
    protected PaginationHelper.PaginationResult<Supplier> searchPage(String keyword, int page, int pageSize) {
        return withProductCounts(supplierDAO.search(keyword, page, pageSize));
    }

    @Override
    protected List<Supplier> fetchAllForExport() {
        List<Supplier> all = supplierDAO.getAll();
        enrichProductCounts(all);
        return all;
    }

    private PaginationHelper.PaginationResult<Supplier> withProductCounts(PaginationHelper.PaginationResult<Supplier> result) {
        enrichProductCounts(result.getData());
        return result;
    }

    private void enrichProductCounts(List<Supplier> suppliers) {
        if (suppliers == null || suppliers.isEmpty()) return;
        List<Integer> ids = new ArrayList<>();
        for (Supplier s : suppliers) ids.add(s.getSupplierId());

        Map<Integer, Integer> counts = supplierDAO.countProductsGrouped(ids);
        for (Supplier s : suppliers) {
            s.setProductCount(counts.getOrDefault(s.getSupplierId(), 0));
        }
    }

    @Override
    protected void openForm(Supplier item) {
        CrudMode mode = item == null ? CrudMode.ADD : CrudMode.EDIT;
        Window owner = SwingUtilities.getWindowAncestor(this);
        SupplierFormDialog dialog = new SupplierFormDialog(
                owner instanceof Frame ? (Frame) owner : null, mode, item, supplierDAO);
        dialog.onSaved(this::handleFormSaved);
        dialog.setVisible(true);
    }

    /**
     * Xoa MEM (IsDeleted = 1) — van cho phep khi nha cung cap dang lien ket san pham,
     * vi ban ghi chi bi an khoi danh sach, khong mat du lieu / khoa ngoai.
     */
    @Override
    protected boolean deleteItem(Supplier item) {
        return supplierDAO.softDelete(item.getSupplierId());
    }

    /**
     * Bat Thung rac: xem ban ghi da xoa mem, khoi phuc, hoac xoa vinh vien
     * (hard delete se go lien ket SupplierProducts truoc).
     * Nut "Thùng rác" tu dong xuat hien tren header (BaseCrudPanel.maybeAddTrashButton).
     */
    @Override
    protected TrashConfig<Supplier> getTrashConfig() {
        return new TrashConfig<>(
                supplierDAO::getDeletedItems,
                item -> supplierDAO.restore(item.getSupplierId()),
                item -> supplierDAO.hardDeleteSupplier(item.getSupplierId())
        );
    }

    @Override
    protected String getSearchPlaceholder() { return "Tìm theo tên, số điện thoại, email, mặt hàng..."; }

    @Override
    protected List<String> fetchAutocompleteSuggestions() {
        List<String> names = new ArrayList<>();
        for (Supplier s : supplierDAO.getAll()) {
            if (s.getSupplierName() != null && !s.getSupplierName().isBlank()) {
                names.add(s.getSupplierName());
            }
        }
        return new ArrayList<>(new LinkedHashSet<>(names));
    }

    @Override
    protected void onDataChanged() {
        reload();
    }

    private static String emptyDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}