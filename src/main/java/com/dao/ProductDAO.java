package com.dao;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.model.Product;
import com.utils.DBConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO doc san pham tu bang Products (JOIN Categories de lay ten danh muc).
 * Hien chi phuc vu man hinh hien thi phia khach hang (HomePanel) nen chi
 * co cac ham doc (SELECT) - chua can INSERT/UPDATE/DELETE o day.
 */
public class ProductDAO {

    private static final String BASE_SELECT =
            "SELECT p.ProductID, p.ProductName, p.CategoryID, c.CategoryName, "
                    + "p.ImportPrice, p.SellPrice, p.Stock, p.MinStock, p.Status "
                    + "FROM Products p JOIN Categories c ON p.CategoryID = c.CategoryID ";

    /** Danh sach san pham dang ban (Status = ACTIVE), moi nhat/ten A-Z. */
    public List<Product> findAllActive() {
        String sql = BASE_SELECT + "WHERE p.Status = 'ACTIVE' ORDER BY p.ProductName";
        List<Product> result = new ArrayList<>();

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                result.add(mapProduct(rs));
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "ProductDAO.findAllActive", e);
        }
        return result;
    }

    /** Tim san pham dang ban theo tu khoa (ten san pham hoac ten danh muc), dung cho o tim kiem tren header. */
    public List<Product> searchActive(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return findAllActive();
        }

        String sql = BASE_SELECT
                + "WHERE p.Status = 'ACTIVE' AND (p.ProductName LIKE ? ESCAPE '\\' OR c.CategoryName LIKE ? ESCAPE '\\') "
                + "ORDER BY p.ProductName";
        List<Product> result = new ArrayList<>();
        String likeParam = "%" + escapeLike(keyword.trim()) + "%";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, likeParam);
            ps.setString(2, likeParam);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapProduct(rs));
                }
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "ProductDAO.searchActive - " + keyword, e);
        }
        return result;
    }

    /** Escape cac ky tu dac biet cua LIKE (%, _, [) truoc khi noi vao tham so tim kiem. */
    private String escapeLike(String raw) {
        return raw.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
                .replace("[", "\\[");
    }

    private Product mapProduct(ResultSet rs) throws SQLException {
        Product product = new Product();
        product.setProductId(rs.getInt("ProductID"));
        product.setProductName(rs.getString("ProductName"));
        product.setCategoryId(rs.getInt("CategoryID"));
        product.setCategoryName(rs.getString("CategoryName"));
        product.setImportPrice(nullSafe(rs.getBigDecimal("ImportPrice")));
        product.setSellPrice(nullSafe(rs.getBigDecimal("SellPrice")));
        product.setStock(rs.getInt("Stock"));
        product.setMinStock(rs.getInt("MinStock"));
        product.setStatus(rs.getString("Status"));
        return product;
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}