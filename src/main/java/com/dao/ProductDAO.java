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
        // KHONG dua ProductCode vao day: no la cot COMPUTED PERSISTED (xem
        // SIMS.sql), SQL Server tu tinh tu ProductID ngay sau khi insert -
        // khong duoc phep (va khong can) ghi gia tri tay cho cot nay.
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

    /**
     * "SP_" + ProductID dem 4 so (vd ProductID=7 -> "SP_0007") - PHAI khop
     * chinh xac cong thuc cot COMPUTED trong SIMS.sql
     * (ProductCode AS ('SP_' + RIGHT('0000' + CAST(ProductID AS VARCHAR(10)), 4))),
     * dung de gan ngay vao object sau insert() ma khong can truy van lai.
     */
    private String generateProductCode(int productId) {
        return "SP_" + String.format("%04d", productId);
    }

    /** Cap nhat 1 san pham (gom ca Stock/MinStock - hien chua co man hinh nhap/xuat kho rieng nen ProductFormDialog la noi duy nhat chinh ton kho). Tra ve true neu co it nhat 1 dong bi anh huong. */
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

    /** Gan cac tham so chung cho insert/update, tra ve index tiep theo con trong (dung cho update noi them WHERE ProductID = ?). */
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