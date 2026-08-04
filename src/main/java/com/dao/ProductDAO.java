package com.dao;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.model.Product;
import com.utils.DBConnection;
import com.utils.PaginationHelper;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;


public class ProductDAO extends BaseDAO<Product> {

    private static final String BASE_SELECT =
            "SELECT p.ProductID, p.ProductCode, p.ProductName, p.CategoryID, c.CategoryName, "
                    + "p.Brand, p.Unit, p.WeightVolume, p.Description, "
                    + "p.ImportPrice, p.SellPrice, p.ImageUrl, p.Stock, p.MinStock, p.Status, "
                    + "p.CreatedAt, p.UpdatedAt "
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
        return "p.ProductID, p.ProductCode, p.ProductName, p.CategoryID, c.CategoryName, "
                + "p.Brand, p.Unit, p.WeightVolume, p.Description, "
                + "p.ImportPrice, p.SellPrice, p.ImageUrl, p.Stock, p.MinStock, p.Status, "
                + "p.CreatedAt, p.UpdatedAt";
    }

    @Override
    protected String getOrderBy() {
        return "p.ProductID DESC";
    }

    @Override
    protected String[] getSearchableColumns() {
        return new String[]{"p.ProductName", "c.CategoryName", "p.ProductCode", "p.Brand"};
    }

    @Override
    protected Product mapResultSet(ResultSet rs) throws SQLException {
        return mapProduct(rs);
    }

    // ---------------------------------------------------------------
    // Quan ly san pham (danh cho Admin) - them/sua, dung chung voi
    // ProductPanel/ProductFormDialog o view/admin/product.
    // ---------------------------------------------------------------

    /** Them 1 san pham moi. Tra ve true neu insert thanh cong; product.productId/productCode duoc set lai tu key sinh ra. */
    public boolean insert(Product product) {
        String sql = "INSERT INTO Products (ProductName, CategoryID, Brand, Unit, WeightVolume, Description, "
                + "ImportPrice, SellPrice, ImageUrl, Stock, MinStock, Status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            bindProduct(ps, product);
            if (ps.executeUpdate() == 0) return false;

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int productId = keys.getInt(1);
                    product.setProductId(productId);
                    product.setProductCode(generateProductCode(productId));
                }
            }
            return true;
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_INSERT_FAIL,
                    "ProductDAO.insert - " + product.getProductName(), e);
            return false;
        }
    }

    private String generateProductCode(int productId) {
        return "SP_" + String.format("%04d", productId);
    }

    public boolean update(Product product) {
        String sql = "UPDATE Products SET ProductName = ?, CategoryID = ?, Brand = ?, Unit = ?, WeightVolume = ?, Description = ?, "
                + "ImportPrice = ?, SellPrice = ?, ImageUrl = ?, Stock = ?, MinStock = ?, Status = ?, UpdatedAt = GETDATE() "
                + "WHERE ProductID = ?";
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

    private int bindProduct(PreparedStatement ps, Product product) throws SQLException {
        ps.setString(1, product.getProductName());
        ps.setInt(2, product.getCategoryId());
        ps.setString(3, product.getBrand());
        ps.setString(4, product.getUnit());
        ps.setString(5, product.getWeightVolume());
        ps.setString(6, product.getDescription());
        ps.setBigDecimal(7, product.getImportPrice());
        ps.setBigDecimal(8, product.getSellPrice());
        ps.setString(9, product.getImageUrl());
        ps.setInt(10, product.getStock());
        ps.setInt(11, product.getMinStock());
        ps.setString(12, product.getStatus());
        return 13;
    }

    /**
     * Ban mo rong cua {@link #getPaged(int, int)}/{@link #search(String, int, int)}
     * danh cho trang Quan ly san pham (Admin, ProductPanel): ket hop CUNG LUC
     * tu khoa tim kiem (tren cac cot khai bao o getSearchableColumns()), loc
     * theo danh muc (CategoryID) va loc theo khoang gia ban (SellPrice) trong
     * 1 truy van phan trang duy nhat.
     * <p>
     * Moi tham so loc deu co the null/rong de bo qua dieu kien tuong ung.
     * minPrice la can duoi bao gom (>=), maxPrice la can tren KHONG bao gom (<).
     */
    public PaginationHelper.PaginationResult<Product> getPagedFiltered(
            int page, int pageSize, String keyword, Integer categoryId,
            BigDecimal minPrice, BigDecimal maxPrice) {

        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        String trimmedKeyword = keyword == null ? "" : keyword.trim();
        if (!trimmedKeyword.isEmpty()) {
            String[] columns = getSearchableColumns();
            String likeParam = "%" + escapeLike(trimmedKeyword) + "%";
            StringBuilder keywordCondition = new StringBuilder("(");
            for (int i = 0; i < columns.length; i++) {
                if (i > 0) keywordCondition.append(" OR ");
                keywordCondition.append(columns[i]).append(" LIKE ? ESCAPE '\\'");
                params.add(likeParam);
            }
            keywordCondition.append(")");
            conditions.add(keywordCondition.toString());
        }
        if (categoryId != null) {
            conditions.add("p.CategoryID = ?");
            params.add(categoryId);
        }
        if (minPrice != null) {
            conditions.add("p.SellPrice >= ?");
            params.add(minPrice);
        }
        if (maxPrice != null) {
            conditions.add("p.SellPrice < ?");
            params.add(maxPrice);
        }

        String whereClause = conditions.isEmpty() ? null : String.join(" AND ", conditions);
        return getPaged(page, pageSize, whereClause, params.toArray());
    }

    public List<Product> findAllActive() {
        return findActive(null, null);
    }

    public List<Product> searchActive(String keyword) {
        return findActive(keyword, null);
    }

    public List<Product> findActiveByCategory(int categoryId) {
        return findActive(null, categoryId);
    }

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

    public Product findActiveByCode(String code) {
        if (code == null || code.isBlank()) return null;
        String sql = BASE_SELECT + "WHERE p.Status = 'ACTIVE' AND UPPER(p.ProductCode) = UPPER(?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, code.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapProduct(rs) : null;
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "ProductDAO.findActiveByCode - code=" + code, e);
            return null;
        }
    }

    private String escapeLike(String raw) {
        return raw.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
                .replace("[", "\\[");
    }

    private Product mapProduct(ResultSet rs) throws SQLException {
        Product product = new Product();
        product.setProductId(rs.getInt("ProductID"));
        product.setProductCode(rs.getString("ProductCode"));
        product.setProductName(rs.getString("ProductName"));
        product.setCategoryId(rs.getInt("CategoryID"));
        product.setCategoryName(rs.getString("CategoryName"));
        product.setBrand(rs.getString("Brand"));
        product.setUnit(rs.getString("Unit"));
        product.setWeightVolume(rs.getString("WeightVolume"));
        product.setDescription(rs.getString("Description"));
        product.setImportPrice(nullSafe(rs.getBigDecimal("ImportPrice")));
        product.setSellPrice(nullSafe(rs.getBigDecimal("SellPrice")));
        product.setImageUrl(rs.getString("ImageUrl"));
        product.setStock(rs.getInt("Stock"));
        product.setMinStock(rs.getInt("MinStock"));
        product.setStatus(rs.getString("Status"));

        java.sql.Timestamp createdAt = rs.getTimestamp("CreatedAt");
        if (createdAt != null) product.setCreatedAt(createdAt.toLocalDateTime());
        java.sql.Timestamp updatedAt = rs.getTimestamp("UpdatedAt");
        if (updatedAt != null) product.setUpdatedAt(updatedAt.toLocalDateTime());

        return product;
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}