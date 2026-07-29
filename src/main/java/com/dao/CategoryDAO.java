package com.dao;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.model.Category;
import com.utils.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DAO cho danh muc (bang Categories, xem SIMS.sql). Ke thua BaseDAO de co san
 * getPaged()/search()/getAll() dung cho man hinh quan tri "Quan ly danh muc"
 * (CategoryPanel), CONG VOI cac ham doc rieng co kem so san pham dang ban
 * (LEFT JOIN Products, chi dem Status = ACTIVE) phuc vu man hinh "Danh muc"
 * phia khach hang (CategoriesPanel) nhu truoc gio.
 * <p>
 * Ly do tach 2 nhom truy van: getPaged()/getAll() cua BaseDAO khong ho tro
 * GROUP BY (can thiet cho COUNT san pham active), nen phan quan tri (CRUD)
 * dung truy van don gian tren rieng bang Categories, con phan hien thi cho
 * khach dung truy van JOIN + GROUP BY rieng nhu ban goc.
 */
public class CategoryDAO extends BaseDAO<Category> {

    // ---------------------------------------------------------------
    // Hook bắt buộc của BaseDAO - dùng cho CategoryPanel (Admin)
    // ---------------------------------------------------------------

    @Override
    protected Connection getConnection() throws SQLException {
        return DBConnection.getConnection();
    }

    @Override
    protected String getTableName() {
        return "Categories";
    }

    @Override
    protected String getColumns() {
        return "CategoryID, CategoryName, Status";
    }

    @Override
    protected String getJoinClause() {
        return null;
    }

    @Override
    protected String getOrderBy() {
        return "CategoryName";
    }

    @Override
    protected Category mapResultSet(ResultSet rs) throws SQLException {
        Category category = new Category();
        category.setCategoryId(rs.getInt("CategoryID"));
        category.setCategoryName(rs.getString("CategoryName"));
        category.setStatus(rs.getString("Status"));
        return category;
    }

    @Override
    protected String[] getSearchableColumns() {
        return new String[]{"CategoryName"};
    }

    // ---------------------------------------------------------------
    // Quản lý danh mục (dành cho Admin)
    // ---------------------------------------------------------------

    /** Them 1 danh muc moi. Tra ve true neu them thanh cong. */
    public boolean insertCategory(Category category) {
        String sql = "INSERT INTO Categories (CategoryName, Status) VALUES (?, ?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, category.getCategoryName());
            ps.setString(2, category.getStatus());

            if (ps.executeUpdate() == 0) return false;

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    category.setCategoryId(keys.getInt(1));
                }
            }
            return true;
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_INSERT_FAIL,
                    "CategoryDAO.insertCategory - " + category.getCategoryName(), e);
            return false;
        }
    }

    /** Cap nhat ten/trang thai 1 danh muc. Tra ve true neu cap nhat thanh cong. */
    public boolean updateCategory(Category category) {
        String sql = "UPDATE Categories SET CategoryName = ?, Status = ? WHERE CategoryID = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, category.getCategoryName());
            ps.setString(2, category.getStatus());
            ps.setInt(3, category.getCategoryId());
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL,
                    "CategoryDAO.updateCategory - categoryId=" + category.getCategoryId(), e);
            return false;
        }
    }

    /**
     * Kiem tra ten danh muc da ton tai chua (khong phan biet hoa/thuong, vi
     * CategoryName la NVARCHAR UNIQUE tren SQL Server voi collation mac dinh
     * khong phan biet hoa thuong). excludeCategoryId = -1 khi dang THEM moi
     * (khong loai tru dong nao).
     */
    public boolean nameExistsExcluding(String categoryName, int excludeCategoryId) {
        String sql = "SELECT COUNT(*) FROM Categories WHERE CategoryName = ? AND CategoryID <> ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, categoryName);
            ps.setInt(2, excludeCategoryId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "CategoryDAO.nameExistsExcluding - " + categoryName, e);
            return false;
        }
    }

    /** Dem so san pham (moi trang thai) dang thuoc 1 danh muc - dung de canh bao Admin truoc khi vo hieu hoa/xoa. */
    public int countProducts(int categoryId) {
        String sql = "SELECT COUNT(*) FROM Products WHERE CategoryID = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, categoryId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "CategoryDAO.countProducts - categoryId=" + categoryId, e);
            return 0;
        }
    }

    /**
     * Ban gop (batch) cua {@link #countProducts(int)} - dem so san pham cho
     * NHIEU danh muc cung luc bang 1 truy van GROUP BY duy nhat, thay vi goi
     * countProducts() rieng cho tung dong (tranh N+1 query khi render 1
     * trang danh sach danh muc). Danh muc khong co san pham nao se KHONG co
     * mat trong Map tra ve (coi nhu 0).
     */
    public Map<Integer, Integer> countProductsGrouped(List<Integer> categoryIds) {
        Map<Integer, Integer> result = new HashMap<>();
        if (categoryIds == null || categoryIds.isEmpty()) return result;

        StringBuilder sql = new StringBuilder(
                "SELECT CategoryID, COUNT(*) AS Cnt FROM Products WHERE CategoryID IN (");
        for (int i = 0; i < categoryIds.size(); i++) {
            sql.append(i == 0 ? "?" : ", ?");
        }
        sql.append(") GROUP BY CategoryID");

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {

            for (int i = 0; i < categoryIds.size(); i++) {
                ps.setInt(i + 1, categoryIds.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getInt("CategoryID"), rs.getInt("Cnt"));
                }
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "CategoryDAO.countProductsGrouped", e);
        }
        return result;
    }

    /**
     * Xoa cung 1 danh muc. CHI goi khi da chac chan danh muc khong co san
     * pham nao tham chieu toi (xem {@link #countProducts(int)}/
     * {@link #countProductsGrouped(List)}) - Products.CategoryID la FOREIGN
     * KEY khong CASCADE nen neu con san pham, cau lenh DELETE se that bai
     * (bat boi catch ben duoi, tra ve false) thay vi nem loi ra ngoai.
     */
    public boolean deleteCategory(int categoryId) {
        String sql = "DELETE FROM Categories WHERE CategoryID = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, categoryId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_DELETE_FAIL,
                    "CategoryDAO.deleteCategory - categoryId=" + categoryId, e);
            return false;
        }
    }

    // ---------------------------------------------------------------
    // Truy vấn dành cho phía khách hàng (CategoriesPanel) - giữ nguyên như cũ
    // ---------------------------------------------------------------

    private static final String BASE_SELECT =
            "SELECT c.CategoryID, c.CategoryName, c.Status, "
                    + "COUNT(CASE WHEN p.Status = 'ACTIVE' THEN 1 END) AS ActiveProductCount "
                    + "FROM Categories c LEFT JOIN Products p ON p.CategoryID = c.CategoryID ";

    /** Danh sach danh muc dang hoat dong (Status = ACTIVE), kem so san pham dang ban, ten A-Z. */
    public List<Category> findAllActive() {
        String sql = BASE_SELECT
                + "WHERE c.Status = 'ACTIVE' "
                + "GROUP BY c.CategoryID, c.CategoryName, c.Status "
                + "ORDER BY c.CategoryName";
        List<Category> result = new ArrayList<>();

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                result.add(mapCategoryWithCount(rs));
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "CategoryDAO.findAllActive", e);
        }
        return result;
    }
    
    /** Toan bo danh muc (ca ACTIVE lan DISABLED), ten A-Z - dung de do combo box chon danh muc trong ProductFormDialog (Admin can gan san pham vao danh muc bat ky, khong chi danh muc dang hoat dong). */
    public List<Category> findAll() {
        String sql = "SELECT CategoryID, CategoryName, Status, 0 AS ActiveProductCount "
                + "FROM Categories ORDER BY CategoryName";
        List<Category> result = new ArrayList<>();

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                result.add(mapCategoryWithCount(rs));
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "CategoryDAO.findAll", e);
        }
        return result;
    }
    
    /** Lay 1 danh muc theo ID (dung khi can hien lai ten danh muc dang loc tren ProductsPanel). */
    public Category findById(int categoryId) {
        String sql = BASE_SELECT
                + "WHERE c.CategoryID = ? "
                + "GROUP BY c.CategoryID, c.CategoryName, c.Status";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, categoryId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapCategoryWithCount(rs);
                }
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "CategoryDAO.findById - " + categoryId, e);
        }
        return null;
    }

    private Category mapCategoryWithCount(ResultSet rs) throws SQLException {
        Category category = new Category();
        category.setCategoryId(rs.getInt("CategoryID"));
        category.setCategoryName(rs.getString("CategoryName"));
        category.setStatus(rs.getString("Status"));
        category.setActiveProductCount(rs.getInt("ActiveProductCount"));
        return category;
    }
}