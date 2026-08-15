package com.dao;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.utils.PaginationHelper;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public abstract class BaseDAO<T> {
    
    protected abstract Connection getConnection() throws SQLException;
    protected abstract String getTableName();
    protected abstract String getColumns();
    protected abstract String getJoinClause();
    protected abstract String getOrderBy();
    protected abstract T mapResultSet(ResultSet rs) throws SQLException;
    
    /**
     * Phương thức generic cho phân trang
     */
    public PaginationHelper.PaginationResult<T> getPaged(int pageNumber, int pageSize) {
        return getPaged(pageNumber, pageSize, null);
    }
    
    /**
     * Phương thức generic cho phân trang với điều kiện.
     * Giu nguyen chu ky cu (whereClause dang String) de KHONG lam vo cac
     * cho da goi getPaged(page, size, "p.CategoryID = " + id) hien co.
     */
    public PaginationHelper.PaginationResult<T> getPaged(
        int pageNumber, 
        int pageSize, 
        String whereClause
    ) {
        return getPaged(pageNumber, pageSize, whereClause, (Object[]) null);
    }

    /**
     * Ban co params: whereClause dung dau {@code ?} nhu PreparedStatement
     * binh thuong, gia tri thuc truyen qua {@code params} thay vi noi
     * chuoi truc tiep vao SQL. Day la duong duoc khuyen dung cho MOI dieu
     * kien lay tu input nguoi dung (vd tu khoa tim kiem) de tranh SQL
     * injection VA tranh phai tu escape ky tu dac biet cua LIKE (%, _, [).
     */
    public PaginationHelper.PaginationResult<T> getPaged(
        int pageNumber,
        int pageSize,
        String whereClause,
        Object... params
    ) {
        PaginationHelper.PaginationResult<T> result = new PaginationHelper.PaginationResult<>();
        List<T> data = new ArrayList<>();
        
        // Xây dựng câu lệnh SQL
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ");
        sql.append(getColumns());
        sql.append(" FROM ");
        sql.append(getTableName());
        
        String joinClause = getJoinClause();
        if (joinClause != null && !joinClause.isEmpty()) {
            sql.append(" ");
            sql.append(joinClause);
        }
        
        if (whereClause != null && !whereClause.isEmpty()) {
            sql.append(" WHERE ");
            sql.append(whereClause);
        }
        
        sql.append(" ORDER BY ");
        sql.append(getOrderBy());
        // MySQL: LIMIT offset, row_count. Thu tu tham so van la offset, pageSize.
        sql.append(" LIMIT ?, ?");
        
        // Câu lệnh đếm tổng số records
        StringBuilder countSql = new StringBuilder();
        countSql.append("SELECT COUNT(*) FROM ");
        countSql.append(getTableName());
        
        if (joinClause != null && !joinClause.isEmpty()) {
            countSql.append(" ");
            countSql.append(joinClause);
        }
        
        if (whereClause != null && !whereClause.isEmpty()) {
            countSql.append(" WHERE ");
            countSql.append(whereClause);
        }
        
        try (Connection conn = getConnection()) {
            
            // Lấy tổng số records
            int totalRecords = 0;
            try (PreparedStatement countStmt = conn.prepareStatement(countSql.toString())) {
                bindParams(countStmt, params);
                try (ResultSet countRs = countStmt.executeQuery()) {
                    if (countRs.next()) {
                        totalRecords = countRs.getInt(1);
                    }
                }
            }
            
            // Lấy dữ liệu phân trang
            int offset = (pageNumber - 1) * pageSize;
            try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
                int nextIndex = bindParams(stmt, params);
                stmt.setInt(nextIndex, offset);
                stmt.setInt(nextIndex + 1, pageSize);
                
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    data.add(mapResultSet(rs));
                }
            }
            
            result.setData(data);
            result.setTotalRecords(totalRecords);
            result.setPageSize(pageSize);
            result.setCurrentPage(pageNumber);
            result.setTotalPages((int) Math.ceil((double) totalRecords / pageSize));
            
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_PAGINATION_FAIL, getClass().getSimpleName() + ".getPaged", e);
        }
        
        return result;
    }

    /** Gan lan luot cac params (neu co) vao PreparedStatement, tra ve index tiep theo con trong. */
    private int bindParams(PreparedStatement ps, Object... params) throws SQLException {
        if (params == null) return 1;
        int i = 1;
        for (Object p : params) {
            ps.setObject(i++, p);
        }
        return i;
    }

    /**
     * Danh sach cot (co alias bang neu co JOIN, vd "p.PhoneName") duoc phep
     * tim kiem bang tu khoa. Entity nao muon co search() generic ben duoi
     * thi CHI CAN override ham nay, khong phai tu viet lai SQL. Mac dinh
     * rong -> khong ho tro search chung, DAO cu the van co the tu viet
     * ham search rieng nhu truoc gio (vd searchByName cu).
     */
    protected String[] getSearchableColumns() {
        return new String[0];
    }

    /**
     * Tim kiem chung cho MOI entity ke thua BaseDAO: chi can khai bao
     * getSearchableColumns(), khong can dong cham gi den SQL/PreparedStatement.
     * Tu khoa duoc OR tren tat ca cot khai bao, escape dung dau %, _, [ cua
     * LIKE (khac voi cach noi chuoi thu cong truoc day chi escape dau nhay
     * don), va truyen qua PreparedStatement param - an toan SQL injection.
     * Neu keyword rong hoac getSearchableColumns() rong -> tra ve getPaged
     * binh thuong (khong loc).
     */
    public PaginationHelper.PaginationResult<T> search(String keyword, int pageNumber, int pageSize) {
        String[] columns = getSearchableColumns();
        if (keyword == null || keyword.trim().isEmpty() || columns.length == 0) {
            return getPaged(pageNumber, pageSize);
        }

        String escaped = keyword.trim()
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        String likeValue = "%" + escaped + "%";

        StringBuilder where = new StringBuilder("(");
        Object[] params = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if (i > 0) where.append(" OR ");
            where.append(columns[i]).append(" LIKE ? ESCAPE '!'");
            params[i] = likeValue;
        }
        where.append(")");

        return getPaged(pageNumber, pageSize, where.toString(), params);
    }
    
    /**
     * Lấy tất cả dữ liệu (không phân trang)
     */
    public List<T> getAll() {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ");
        sql.append(getColumns());
        sql.append(" FROM ");
        sql.append(getTableName());
        
        String joinClause = getJoinClause();
        if (joinClause != null && !joinClause.isEmpty()) {
            sql.append(" ");
            sql.append(joinClause);
        }
        
        sql.append(" ORDER BY ");
        sql.append(getOrderBy());
        
        List<T> list = new ArrayList<>();
        
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql.toString())) {
            
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
            
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, getClass().getSimpleName() + ".getAll", e);
        }
        
        return list;
    }
    
    /**
     * Lấy dữ liệu với điều kiện (không phân trang)
     */
    public List<T> getByCondition(String whereClause) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ");
        sql.append(getColumns());
        sql.append(" FROM ");
        sql.append(getTableName());
        
        String joinClause = getJoinClause();
        if (joinClause != null && !joinClause.isEmpty()) {
            sql.append(" ");
            sql.append(joinClause);
        }
        
        if (whereClause != null && !whereClause.isEmpty()) {
            sql.append(" WHERE ");
            sql.append(whereClause);
        }
        
        sql.append(" ORDER BY ");
        sql.append(getOrderBy());
        
        List<T> list = new ArrayList<>();
        
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql.toString())) {
            
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
            
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, getClass().getSimpleName() + ".getByCondition", e);
        }
        
        return list;
    }
}
