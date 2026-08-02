package com.dao;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.utils.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO rieng cho trang Dashboard (Tong quan) - giong RevenueReportDAO, KHONG
 * extends BaseDAO vi day la cac truy van DEM/GOP NHOM tren nhieu bang, khong
 * phai CRUD 1 entity. Cac chi so "hom nay" (doanh thu, so hoa don) da co san
 * o RevenueReportDAO/InvoiceDAO nen khong lap lai o day - DashboardPanel tu
 * ghep tu nhieu DAO (giong cach RevenueReportPanel dung rieng RevenueReportDAO).
 */
public class DashboardDAO {

    private Connection getConnection() throws SQLException {
        return DBConnection.getConnection();
    }

    /** Cac chi so dem nhanh (KHONG tai ca danh sach) cho hang StatCard tren Dashboard. */
    public static class Overview {
        public final int totalProducts;
        public final int lowStockCount;
        public final int totalCustomers;
        public final int totalEmployees;

        public Overview(int totalProducts, int lowStockCount, int totalCustomers, int totalEmployees) {
            this.totalProducts = totalProducts;
            this.lowStockCount = lowStockCount;
            this.totalCustomers = totalCustomers;
            this.totalEmployees = totalEmployees;
        }
    }

    /** 1 dong san pham dang o muc canh bao ton kho (Stock <= MinStock). */
    public static class LowStockItem {
        public final String productCode;
        public final String productName;
        public final int stock;
        public final int minStock;

        public LowStockItem(String productCode, String productName, int stock, int minStock) {
            this.productCode = productCode;
            this.productName = productName;
            this.stock = stock;
            this.minStock = minStock;
        }

        public boolean isOutOfStock() {
            return stock <= 0;
        }
    }

    /** Tong san pham dang ban, so SP duoi muc ton toi thieu, tong khach hang/nhan vien con hoat dong (chua bi xoa mem). */
    public Overview getOverview() {
        String sql = "SELECT "
                + "(SELECT COUNT(*) FROM Products WHERE Status = 'ACTIVE') AS TotalProducts, "
                + "(SELECT COUNT(*) FROM Products WHERE Status = 'ACTIVE' AND Stock <= MinStock) AS LowStock, "
                + "(SELECT COUNT(*) FROM Customers c JOIN Users u ON c.CustomerID = u.UserID WHERE u.IsDeleted = 0) AS TotalCustomers, "
                + "(SELECT COUNT(*) FROM Employees e JOIN Users u ON e.UserID = u.UserID WHERE u.IsDeleted = 0) AS TotalEmployees";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return new Overview(
                        rs.getInt("TotalProducts"),
                        rs.getInt("LowStock"),
                        rs.getInt("TotalCustomers"),
                        rs.getInt("TotalEmployees"));
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "DashboardDAO.getOverview", e);
        }
        return new Overview(0, 0, 0, 0);
    }

    /** San pham dang ACTIVE va co Stock <= MinStock, het truoc/sap het sau, toi da {@code limit} dong. */
    public List<LowStockItem> getLowStockProducts(int limit) {
        String sql = "SELECT TOP (?) ProductCode, ProductName, Stock, MinStock "
                + "FROM Products WHERE Status = 'ACTIVE' AND Stock <= MinStock "
                + "ORDER BY Stock ASC, ProductName ASC";
        List<LowStockItem> list = new ArrayList<>();
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new LowStockItem(
                            rs.getString("ProductCode"),
                            rs.getString("ProductName"),
                            rs.getInt("Stock"),
                            rs.getInt("MinStock")));
                }
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "DashboardDAO.getLowStockProducts", e);
        }
        return list;
    }
}