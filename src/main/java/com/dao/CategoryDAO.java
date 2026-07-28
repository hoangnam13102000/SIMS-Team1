package com.dao;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.model.Category;
import com.utils.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO doc danh muc tu bang Categories, kem so san pham dang ban (LEFT JOIN
 * Products, chi dem Status = ACTIVE) de phuc vu man hinh "Danh muc" phia
 * khach hang (CategoriesPanel). Chi co cac ham doc (SELECT), giong ProductDAO.
 */
public class CategoryDAO {

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
                result.add(mapCategory(rs));
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "CategoryDAO.findAllActive", e);
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
                    return mapCategory(rs);
                }
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "CategoryDAO.findById - " + categoryId, e);
        }
        return null;
    }

    private Category mapCategory(ResultSet rs) throws SQLException {
        Category category = new Category();
        category.setCategoryId(rs.getInt("CategoryID"));
        category.setCategoryName(rs.getString("CategoryName"));
        category.setStatus(rs.getString("Status"));
        category.setActiveProductCount(rs.getInt("ActiveProductCount"));
        return category;
    }
}