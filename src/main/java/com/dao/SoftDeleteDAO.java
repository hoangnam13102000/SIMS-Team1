package com.dao;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.utils.PaginationHelper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

public abstract class SoftDeleteDAO<T> extends BaseDAO<T> {

   
    protected abstract String getBaseTableName();

    /** Ten cot khoa chinh, dung cho UPDATE/DELETE truc tiep, vi du "PhoneID". */
    protected abstract String getIdColumn();

   
    protected String getIsDeletedColumn() {
        return "IsDeleted";
    }

    /**
     * Ten cot luu thoi diem xoa. Tra ve {@code null} neu khong muon luu/cap
     * nhat thoi diem xoa (vi du bang khong co cot nay).
     */
    protected String getDeletedAtColumn() {
        return "DeletedAt";
    }

    // =====================================================================
    // Tu dong loai bo ban ghi da xoa mem khoi MOI truy van danh sach
    // =====================================================================

    @Override
    public PaginationHelper.PaginationResult<T> getPaged(int pageNumber, int pageSize, String whereClause, Object... params) {
        return super.getPaged(pageNumber, pageSize, mergeNotDeleted(whereClause), params);
    }

    @Override
    public List<T> getAll() {
        return getByCondition(null);
    }

    @Override
    public List<T> getByCondition(String whereClause) {
        return super.getByCondition(mergeNotDeleted(whereClause));
    }

    private String mergeNotDeleted(String whereClause) {
        String notDeleted = getIsDeletedColumn() + " = 0";
        if (whereClause == null || whereClause.trim().isEmpty()) return notDeleted;
        return "(" + whereClause + ") AND " + notDeleted;
    }

    // =====================================================================
    // Xoa mem / Khoi phuc / Thung rac / Xoa vinh vien
    // =====================================================================

    /** Xoa mem 1 ban ghi: KHONG DELETE that, chi danh dau IsDeleted=1 (+ luu thoi diem xoa neu co). */
    public boolean softDelete(Object id) {
        String sql = "UPDATE " + getBaseTableName() + " SET " + getIsDeletedColumn() + " = 1"
                + (getDeletedAtColumn() != null ? ", " + getDeletedAtColumn() + " = SYSDATETIME()" : "")
                + " WHERE " + getIdColumn() + " = ?";
        return executeUpdateById(sql, id, "softDelete");
    }

    /** Khoi phuc 1 ban ghi da xoa mem truoc do. */
    public boolean restore(Object id) {
        String sql = "UPDATE " + getBaseTableName() + " SET " + getIsDeletedColumn() + " = 0"
                + (getDeletedAtColumn() != null ? ", " + getDeletedAtColumn() + " = NULL" : "")
                + " WHERE " + getIdColumn() + " = ?";
        return executeUpdateById(sql, id, "restore");
    }

    /** Xoa VINH VIEN (DELETE that su). Chi nen goi tu man hinh Thung rac, sau khi nguoi dung da xac nhan chac chan. */
    public boolean hardDelete(Object id) {
        String sql = "DELETE FROM " + getBaseTableName() + " WHERE " + getIdColumn() + " = ?";
        return executeUpdateById(sql, id, "hardDelete");
    }

    /** Danh sach cac ban ghi ĐANG nam trong "thung rac" (da xoa mem), dung cho man hinh Thung rac. */
    public List<T> getDeletedItems() {
        // Goi super.getByCondition() (KHONG phai this.getByCondition() da bi
        // override o tren) de tranh bi cong don nham dieu kien "IsDeleted = 0"
        // (loai tru ban ghi da xoa) vao chinh truy van dang muon LAY ban ghi
        // da xoa.
        return super.getByCondition(getIsDeletedColumn() + " = 1");
    }

    private boolean executeUpdateById(String sql, Object id, String opName) {
        try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, getClass().getSimpleName() + "." + opName, e);
            return false;
        }
    }
}