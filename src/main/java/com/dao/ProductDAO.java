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


public class ProductDAO extends BaseDAO<Product> {

    private static final String BASE_SELECT =
            "SELECT p.ProductID, p.ProductName, p.CategoryID, c.CategoryName, "
                    + "p.ImportPrice, p.SellPrice, p.ImageUrl, p.Stock, p.MinStock, p.Status "
                    + "FROM Products p JOIN Categories c ON p.CategoryID = c.CategoryID ";

    // ---------------------------------------------------------------
    // BaseDAO - cho phep dung getPaged()/search()/getAll() cho trang
    // Quan ly san pham (Admin), ben canh cac ham doc client rieng ben duoi.
    // ---------------------------------------------------------------

    @Override
    protected Connection getConnection() throws SQLException {
        return DBConnection.getConnection();
    }

    @Override
    protected String getTableName() {
        return "Products p JOIN Categories c ON p.CategoryID = c.CategoryID";
    }

    @Override
    protected String getJoinClause() {
        return null;
    }

    @Override
    protected String getColumns() {
        return "p.ProductID, p.ProductName, p.CategoryID, c.CategoryName, "
                + "p.ImportPrice, p.SellPrice, p.ImageUrl, p.Stock, p.MinStock, p.Status";
    }

    @Override
    protected String getOrderBy() {
        return "p.ProductID DESC";
    }

    @Override
    protected String[] getSearchableColumns() {
        return new String[]{"p.ProductName", "c.CategoryName"};
    }

    @Override
    protected Product mapResultSet(ResultSet rs) throws SQLException {
        return mapProduct(rs);
    }

    // ---------------------------------------------------------------
    // Quan ly san pham (danh cho Admin) - them/sua, dung chung voi
    // ProductPanel/ProductFormDialog o view/admin/product.
    // ---------------------------------------------------------------

    /** Them 1 san pham moi. Tra ve true neu insert thanh cong. */
    public boolean insert(Product product) {
        String sql = "INSERT INTO Products (ProductName, CategoryID, ImportPrice, SellPrice, ImageUrl, Stock, MinStock, Status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            bindProduct(ps, product);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_INSERT_FAIL,
                    "ProductDAO.insert - " + product.getProductName(), e);
            return false;
        }
    }

    /** Cap nhat 1 san pham (gom ca Stock/MinStock - hien chua co man hinh nhap/xuat kho rieng nen ProductFormDialog la noi duy nhat chinh ton kho). Tra ve true neu co it nhat 1 dong bi anh huong. */
    public boolean update(Product product) {
        String sql = "UPDATE Products SET ProductName = ?, CategoryID = ?, ImportPrice = ?, SellPrice = ?, "
                + "ImageUrl = ?, Stock = ?, MinStock = ?, Status = ? WHERE ProductID = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            int nextIndex = bindProduct(ps, product);
            ps.setInt(nextIndex, product.getProductId());
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL,
                    "ProductDAO.update - productId=" + product.getProductId(), e);
            return false;
        }
    }

    /** Gan cac tham so chung cho insert/update, tra ve index tiep theo con trong (dung cho update noi them WHERE ProductID = ?). */
    private int bindProduct(PreparedStatement ps, Product product) throws SQLException {
        ps.setString(1, product.getProductName());
        ps.setInt(2, product.getCategoryId());
        ps.setBigDecimal(3, product.getImportPrice());
        ps.setBigDecimal(4, product.getSellPrice());
        ps.setString(5, product.getImageUrl());
        ps.setInt(6, product.getStock());
        ps.setInt(7, product.getMinStock());
        ps.setString(8, product.getStatus());
        return 9;
    }

    /** Danh sach san pham dang ban (Status = ACTIVE), moi nhat/ten A-Z. */
    public List<Product> findAllActive() {
        return findActive(null, null);
    }

    /** Tim san pham dang ban theo tu khoa (ten san pham hoac ten danh muc), dung cho o tim kiem tren header. */
    public List<Product> searchActive(String keyword) {
        return findActive(keyword, null);
    }

    /** Danh sach san pham dang ban thuoc 1 danh muc, dung cho trang "Sản phẩm" phia client khi loc theo danh muc. */
    public List<Product> findActiveByCategory(int categoryId) {
        return findActive(null, categoryId);
    }

    /**
     * Truy van hop nhat: san pham dang ban (Status = ACTIVE), loc theo tu
     * khoa (ten san pham/ten danh muc) va/hoac theo danh muc - ca 2 tham so
     * deu co the null/rong de bo qua dieu kien tuong ung.
     */
    public List<Product> findActive(String keyword, Integer categoryId) {
        StringBuilder sql = new StringBuilder(BASE_SELECT).append("WHERE p.Status = 'ACTIVE' ");
        List<Object> params = new ArrayList<>();

        String trimmedKeyword = keyword == null ? "" : keyword.trim();
        if (!trimmedKeyword.isEmpty()) {
            sql.append("AND (p.ProductName LIKE ? ESCAPE '\\' OR c.CategoryName LIKE ? ESCAPE '\\') ");
            String likeParam = "%" + escapeLike(trimmedKeyword) + "%";
            params.add(likeParam);
            params.add(likeParam);
        }
        if (categoryId != null) {
            sql.append("AND p.CategoryID = ? ");
            params.add(categoryId);
        }
        sql.append("ORDER BY p.ProductName");

        List<Product> result = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapProduct(rs));
                }
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "ProductDAO.findActive - keyword=" + keyword + ", categoryId=" + categoryId, e);
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
        product.setImageUrl(rs.getString("ImageUrl"));
        product.setStock(rs.getInt("Stock"));
        product.setMinStock(rs.getInt("MinStock"));
        product.setStatus(rs.getString("Status"));
        return product;
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}