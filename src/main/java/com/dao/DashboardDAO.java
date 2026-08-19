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
        /** So hoa don bi huy TRONG NGAY hom nay (Status='CANCELLED', dem theo CancelledAt). */
        public final int cancelledInvoicesToday;
        /** Tong so luong san pham khach da tra lai TRONG NGAY hom nay (chi tinh doi/tra da
         *  duoc Quan ly ban hang DUYET - luc do kho moi thuc su duoc cong tra qua trigger). */
        public final int returnedProductsToday;

        public Overview(int totalProducts, int lowStockCount, int totalCustomers, int totalEmployees,
                         int cancelledInvoicesToday, int returnedProductsToday) {
            this.totalProducts = totalProducts;
            this.lowStockCount = lowStockCount;
            this.totalCustomers = totalCustomers;
            this.totalEmployees = totalEmployees;
            this.cancelledInvoicesToday = cancelledInvoicesToday;
            this.returnedProductsToday = returnedProductsToday;
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

    /** Tong san pham dang ban, so SP duoi muc ton toi thieu, tong khach hang/nhan vien con hoat dong (chua bi xoa mem),
     *  so hoa don bi huy va so SP tra lai trong ngay hom nay (de bai muc 3.3 - thong ke ban hang hang ngay). */
    public Overview getOverview() {
        String sql = "SELECT "
                + "(SELECT COUNT(*) FROM Products WHERE Status = 'ACTIVE') AS TotalProducts, "
                + "(SELECT COUNT(*) FROM Products WHERE Status = 'ACTIVE' AND Stock <= MinStock) AS LowStock, "
                + "(SELECT COUNT(*) FROM Customers c JOIN Users u ON c.CustomerID = u.UserID WHERE u.IsDeleted = 0) AS TotalCustomers, "
                + "(SELECT COUNT(*) FROM Employees e JOIN Users u ON e.UserID = u.UserID WHERE u.IsDeleted = 0) AS TotalEmployees, "
                + "(SELECT COUNT(*) FROM Invoices WHERE Status = 'CANCELLED' AND CAST(CancelledAt AS DATE) = CAST(CURRENT_TIMESTAMP AS DATE)) AS CancelledInvoicesToday, "
                + "(SELECT COALESCE(SUM(d.Quantity), 0) FROM ReturnExchangeDetails d "
                + "   JOIN ReturnExchanges r ON r.ReturnID = d.ReturnID "
                + "   WHERE d.Direction = 'IN' AND r.Status = 'APPROVED' AND CAST(r.ApprovedAt AS DATE) = CAST(CURRENT_TIMESTAMP AS DATE)) AS ReturnedProductsToday";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return new Overview(
                        rs.getInt("TotalProducts"),
                        rs.getInt("LowStock"),
                        rs.getInt("TotalCustomers"),
                        rs.getInt("TotalEmployees"),
                        rs.getInt("CancelledInvoicesToday"),
                        rs.getInt("ReturnedProductsToday"));
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "DashboardDAO.getOverview", e);
        }
        return new Overview(0, 0, 0, 0, 0, 0);
    }

    /** San pham dang ACTIVE va co Stock <= MinStock, het truoc/sap het sau, toi da {@code limit} dong. */
    public List<LowStockItem> getLowStockProducts(int limit) {
        String sql = "SELECT ProductCode, ProductName, Stock, MinStock "
                + "FROM Products WHERE Status = 'ACTIVE' AND Stock <= MinStock "
                + "ORDER BY Stock ASC, ProductName ASC LIMIT ?";
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

    // ------------------------------------------------------------------
    // Chỉ số dành riêng cho dashboard Quản lý bán hàng
    // ------------------------------------------------------------------

    /** 1 dòng yêu cầu đổi/trả đang chờ duyệt. */
    public static class PendingReturnItem {
        public final int returnId;
        public final String invoiceCode;
        public final String type;
        public final String createdByName;
        public final java.math.BigDecimal totalValue;
        public final java.time.LocalDateTime createdAt;

        public PendingReturnItem(int returnId, String invoiceCode, String type,
                                 String createdByName, java.math.BigDecimal totalValue,
                                 java.time.LocalDateTime createdAt) {
            this.returnId = returnId;
            this.invoiceCode = invoiceCode;
            this.type = type;
            this.createdByName = createdByName;
            this.totalValue = totalValue != null ? totalValue : java.math.BigDecimal.ZERO;
            this.createdAt = createdAt;
        }

        public boolean isExchange() {
            return "EXCHANGE".equalsIgnoreCase(type);
        }
    }

    /** 1 dòng báo cáo ngoại lệ đang PENDING. */
    public static class PendingExceptionItem {
        public final int reportId;
        public final String createdByName;
        public final String content;
        public final java.time.LocalDateTime createdAt;

        public PendingExceptionItem(int reportId, String createdByName, String content,
                                    java.time.LocalDateTime createdAt) {
            this.reportId = reportId;
            this.createdByName = createdByName;
            this.content = content;
            this.createdAt = createdAt;
        }
    }

    /** Số yêu cầu đổi/trả đang chờ Quản lý bán hàng duyệt. */
    public int countPendingReturnExchanges() {
        String sql = "SELECT COUNT(*) FROM ReturnExchanges WHERE Status = 'PENDING'";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "DashboardDAO.countPendingReturnExchanges", e);
        }
        return 0;
    }

    /** Danh sách yêu cầu đổi/trả PENDING mới nhất (tối đa {@code limit}). */
    public List<PendingReturnItem> getPendingReturnExchanges(int limit) {
        String sql = "SELECT r.ReturnID, i.InvoiceCode, r.Type, u.FullName AS CreatedByName, "
                + "r.TotalValue, r.CreatedAt "
                + "FROM ReturnExchanges r "
                + "JOIN Invoices i ON i.InvoiceID = r.InvoiceID "
                + "JOIN Users u ON u.UserID = r.CreatedBy "
                + "WHERE r.Status = 'PENDING' "
                + "ORDER BY r.CreatedAt DESC, r.ReturnID DESC LIMIT ?";
        List<PendingReturnItem> list = new ArrayList<>();
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.sql.Timestamp ts = rs.getTimestamp("CreatedAt");
                    list.add(new PendingReturnItem(
                            rs.getInt("ReturnID"),
                            rs.getString("InvoiceCode"),
                            rs.getString("Type"),
                            rs.getString("CreatedByName"),
                            rs.getBigDecimal("TotalValue"),
                            ts != null ? ts.toLocalDateTime() : null));
                }
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "DashboardDAO.getPendingReturnExchanges", e);
        }
        return list;
    }

    /** Số báo cáo ngoại lệ đang chờ xử lý. */
    public int countPendingExceptionReports() {
        String sql = "SELECT COUNT(*) FROM ExceptionReports WHERE Status = 'PENDING'";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "DashboardDAO.countPendingExceptionReports", e);
        }
        return 0;
    }

    /** Danh sách báo cáo ngoại lệ PENDING mới nhất. */
    public List<PendingExceptionItem> getPendingExceptionReports(int limit) {
        String sql = "SELECT er.ReportID, u.FullName AS CreatedByName, er.Content, er.CreatedAt "
                + "FROM ExceptionReports er "
                + "JOIN Users u ON u.UserID = er.CreatedBy "
                + "WHERE er.Status = 'PENDING' "
                + "ORDER BY er.CreatedAt DESC, er.ReportID DESC LIMIT ?";
        List<PendingExceptionItem> list = new ArrayList<>();
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.sql.Timestamp ts = rs.getTimestamp("CreatedAt");
                    list.add(new PendingExceptionItem(
                            rs.getInt("ReportID"),
                            rs.getString("CreatedByName"),
                            rs.getString("Content"),
                            ts != null ? ts.toLocalDateTime() : null));
                }
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "DashboardDAO.getPendingExceptionReports", e);
        }
        return list;
    }


    // ------------------------------------------------------------------
    // Chỉ số dashboard Nhân viên bán hàng (theo user đang đăng nhập)
    // ------------------------------------------------------------------

    /** Kết quả bán hàng cá nhân trong ngày. */
    public static class StaffDayStats {
        public final int invoiceCount;
        public final java.math.BigDecimal revenue;
        public final long itemsSold;
        public final int cancelledCount;

        public StaffDayStats(int invoiceCount, java.math.BigDecimal revenue,
                             long itemsSold, int cancelledCount) {
            this.invoiceCount = invoiceCount;
            this.revenue = revenue != null ? revenue : java.math.BigDecimal.ZERO;
            this.itemsSold = itemsSold;
            this.cancelledCount = cancelledCount;
        }
    }

    /** Hóa đơn gần đây của nhân viên. */
    public static class StaffInvoiceItem {
        public final int invoiceId;
        public final String invoiceCode;
        public final java.math.BigDecimal totalAmount;
        public final String status;
        public final String paymentMethod;
        public final java.time.LocalDateTime createdAt;

        public StaffInvoiceItem(int invoiceId, String invoiceCode,
                                java.math.BigDecimal totalAmount, String status,
                                String paymentMethod, java.time.LocalDateTime createdAt) {
            this.invoiceId = invoiceId;
            this.invoiceCode = invoiceCode;
            this.totalAmount = totalAmount != null ? totalAmount : java.math.BigDecimal.ZERO;
            this.status = status;
            this.paymentMethod = paymentMethod;
            this.createdAt = createdAt;
        }

        public boolean isCancelled() {
            return "CANCELLED".equalsIgnoreCase(status);
        }
    }

    /**
     * Thống kê bán hàng của 1 nhân viên trong ngày hôm nay
     * (hóa đơn ACTIVE theo CreatedBy + ngày tạo).
     */
    public StaffDayStats getStaffDayStats(int userId) {
        String sql = "SELECT "
                + "(SELECT COUNT(*) FROM Invoices WHERE CreatedBy = ? AND Status = 'ACTIVE' "
                + "   AND CAST(CreatedAt AS DATE) = CAST(CURRENT_TIMESTAMP AS DATE)) AS InvoiceCount, "
                + "(SELECT COALESCE(SUM(TotalAmount), 0) FROM Invoices WHERE CreatedBy = ? AND Status = 'ACTIVE' "
                + "   AND CAST(CreatedAt AS DATE) = CAST(CURRENT_TIMESTAMP AS DATE)) AS Revenue, "
                + "(SELECT COALESCE(SUM(d.Quantity), 0) FROM InvoiceDetails d "
                + "   JOIN Invoices i ON i.InvoiceID = d.InvoiceID "
                + "   WHERE i.CreatedBy = ? AND i.Status = 'ACTIVE' "
                + "   AND CAST(i.CreatedAt AS DATE) = CAST(CURRENT_TIMESTAMP AS DATE)) AS ItemsSold, "
                + "(SELECT COUNT(*) FROM Invoices WHERE CreatedBy = ? AND Status = 'CANCELLED' "
                + "   AND CAST(COALESCE(CancelledAt, CreatedAt) AS DATE) = CAST(CURRENT_TIMESTAMP AS DATE)) AS CancelledCount";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, userId);
            ps.setInt(3, userId);
            ps.setInt(4, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new StaffDayStats(
                            rs.getInt("InvoiceCount"),
                            rs.getBigDecimal("Revenue"),
                            rs.getLong("ItemsSold"),
                            rs.getInt("CancelledCount"));
                }
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "DashboardDAO.getStaffDayStats - userId=" + userId, e);
        }
        return new StaffDayStats(0, java.math.BigDecimal.ZERO, 0, 0);
    }

    /** Số yêu cầu đổi/trả do nhân viên tạo còn PENDING. */
    public int countMyPendingReturns(int userId) {
        String sql = "SELECT COUNT(*) FROM ReturnExchanges WHERE CreatedBy = ? AND Status = 'PENDING'";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "DashboardDAO.countMyPendingReturns - userId=" + userId, e);
        }
        return 0;
    }

    /** Hóa đơn gần đây do nhân viên tạo (mọi trạng thái), tối đa {@code limit}. */
    public List<StaffInvoiceItem> getStaffRecentInvoices(int userId, int limit) {
        String sql = "SELECT InvoiceID, InvoiceCode, TotalAmount, Status, PaymentMethod, CreatedAt "
                + "FROM Invoices WHERE CreatedBy = ? "
                + "ORDER BY CreatedAt DESC, InvoiceID DESC LIMIT ?";
        List<StaffInvoiceItem> list = new ArrayList<>();
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.sql.Timestamp ts = rs.getTimestamp("CreatedAt");
                    list.add(new StaffInvoiceItem(
                            rs.getInt("InvoiceID"),
                            rs.getString("InvoiceCode"),
                            rs.getBigDecimal("TotalAmount"),
                            rs.getString("Status"),
                            rs.getString("PaymentMethod"),
                            ts != null ? ts.toLocalDateTime() : null));
                }
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "DashboardDAO.getStaffRecentInvoices - userId=" + userId, e);
        }
        return list;
    }

    /** Đổi/trả do tôi tạo còn PENDING (tối đa limit). */
    public List<PendingReturnItem> getMyPendingReturns(int userId, int limit) {
        String sql = "SELECT r.ReturnID, i.InvoiceCode, r.Type, u.FullName AS CreatedByName, "
                + "r.TotalValue, r.CreatedAt "
                + "FROM ReturnExchanges r "
                + "JOIN Invoices i ON i.InvoiceID = r.InvoiceID "
                + "JOIN Users u ON u.UserID = r.CreatedBy "
                + "WHERE r.Status = 'PENDING' AND r.CreatedBy = ? "
                + "ORDER BY r.CreatedAt DESC, r.ReturnID DESC LIMIT ?";
        List<PendingReturnItem> list = new ArrayList<>();
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.sql.Timestamp ts = rs.getTimestamp("CreatedAt");
                    list.add(new PendingReturnItem(
                            rs.getInt("ReturnID"),
                            rs.getString("InvoiceCode"),
                            rs.getString("Type"),
                            rs.getString("CreatedByName"),
                            rs.getBigDecimal("TotalValue"),
                            ts != null ? ts.toLocalDateTime() : null));
                }
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "DashboardDAO.getMyPendingReturns - userId=" + userId, e);
        }
        return list;
    }

    /**
     * Thống kê KPI của nhân viên trong đúng một ca bán hàng.
     * Đây là nguồn dữ liệu cho dashboard SALES_STAFF khi đang có ca OPEN.
     */
    public StaffDayStats getStaffShiftStats(int userId, int shiftId) {
        String sql = "SELECT "
                + "(SELECT COUNT(*) FROM Invoices WHERE CreatedBy = ? AND ShiftID = ? AND Status = 'ACTIVE') AS InvoiceCount, "
                + "(SELECT COALESCE(SUM(TotalAmount), 0) FROM Invoices WHERE CreatedBy = ? AND ShiftID = ? AND Status = 'ACTIVE') AS Revenue, "
                + "(SELECT COALESCE(SUM(d.Quantity), 0) FROM InvoiceDetails d "
                + "   JOIN Invoices i ON i.InvoiceID = d.InvoiceID "
                + "   WHERE i.CreatedBy = ? AND i.ShiftID = ? AND i.Status = 'ACTIVE') AS ItemsSold, "
                + "(SELECT COUNT(*) FROM Invoices WHERE CreatedBy = ? AND ShiftID = ? AND Status = 'CANCELLED') AS CancelledCount";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, shiftId);
            ps.setInt(3, userId);
            ps.setInt(4, shiftId);
            ps.setInt(5, userId);
            ps.setInt(6, shiftId);
            ps.setInt(7, userId);
            ps.setInt(8, shiftId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new StaffDayStats(
                            rs.getInt("InvoiceCount"),
                            rs.getBigDecimal("Revenue"),
                            rs.getLong("ItemsSold"),
                            rs.getInt("CancelledCount"));
                }
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "DashboardDAO.getStaffShiftStats - userId=" + userId + ", shiftId=" + shiftId, e);
        }
        return new StaffDayStats(0, java.math.BigDecimal.ZERO, 0, 0);
    }

    /** Số đơn online chưa kết thúc đang được giao cho chính nhân viên. */
    public int countMyAssignedActiveOrders(int userId) {
        String sql = "SELECT COUNT(*) FROM Orders "
                + "WHERE AssignedTo = ? AND OrderStatus IN ('NEW','CONFIRMED','SHIPPING')";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "DashboardDAO.countMyAssignedActiveOrders - userId=" + userId, e);
        }
        return 0;
    }

    /**
     * Số yêu cầu hủy hóa đơn của chính nhân viên còn đang chờ/xử lý trong đúng ca.
     */
    public int countMyPendingCancelRequestsForShift(int userId, int shiftId) {
        String sql = "SELECT COUNT(*) "
                + "FROM InvoiceCancelRequests r "
                + "JOIN Invoices i ON i.InvoiceID = r.InvoiceID "
                + "WHERE r.RequestedBy = ? AND i.ShiftID = ? "
                + "AND r.Status IN ('PENDING','PROCESSING')";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, shiftId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "DashboardDAO.countMyPendingCancelRequestsForShift - userId=" + userId
                            + ", shiftId=" + shiftId, e);
        }
        return 0;
    }

    /** Số yêu cầu đổi/trả do nhân viên tạo còn PENDING trong đúng ca. */
    public int countMyPendingReturnsForShift(int userId, int shiftId) {
        String sql = "SELECT COUNT(*) FROM ReturnExchanges r "
                + "JOIN Invoices i ON i.InvoiceID = r.InvoiceID "
                + "WHERE r.CreatedBy = ? AND i.ShiftID = ? AND r.Status = 'PENDING'";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, shiftId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "DashboardDAO.countMyPendingReturnsForShift - userId=" + userId
                            + ", shiftId=" + shiftId, e);
        }
        return 0;
    }

    /** Hóa đơn gần đây của nhân viên trong đúng ca. */
    public List<StaffInvoiceItem> getStaffRecentInvoicesForShift(int userId, int shiftId, int limit) {
        String sql = "SELECT InvoiceID, InvoiceCode, TotalAmount, Status, PaymentMethod, CreatedAt "
                + "FROM Invoices WHERE CreatedBy = ? AND ShiftID = ? "
                + "ORDER BY CreatedAt DESC, InvoiceID DESC LIMIT ?";
        List<StaffInvoiceItem> list = new ArrayList<>();
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, shiftId);
            ps.setInt(3, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.sql.Timestamp ts = rs.getTimestamp("CreatedAt");
                    list.add(new StaffInvoiceItem(
                            rs.getInt("InvoiceID"),
                            rs.getString("InvoiceCode"),
                            rs.getBigDecimal("TotalAmount"),
                            rs.getString("Status"),
                            rs.getString("PaymentMethod"),
                            ts != null ? ts.toLocalDateTime() : null));
                }
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "DashboardDAO.getStaffRecentInvoicesForShift - userId=" + userId
                            + ", shiftId=" + shiftId, e);
        }
        return list;
    }

    /** Đổi/trả do tôi tạo còn PENDING trong đúng ca. */
    public List<PendingReturnItem> getMyPendingReturnsForShift(int userId, int shiftId, int limit) {
        String sql = "SELECT r.ReturnID, i.InvoiceCode, r.Type, u.FullName AS CreatedByName, "
                + "r.TotalValue, r.CreatedAt "
                + "FROM ReturnExchanges r "
                + "JOIN Invoices i ON i.InvoiceID = r.InvoiceID "
                + "JOIN Users u ON u.UserID = r.CreatedBy "
                + "WHERE r.Status = 'PENDING' AND r.CreatedBy = ? AND i.ShiftID = ? "
                + "ORDER BY r.CreatedAt DESC, r.ReturnID DESC LIMIT ?";
        List<PendingReturnItem> list = new ArrayList<>();
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, shiftId);
            ps.setInt(3, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.sql.Timestamp ts = rs.getTimestamp("CreatedAt");
                    list.add(new PendingReturnItem(
                            rs.getInt("ReturnID"),
                            rs.getString("InvoiceCode"),
                            rs.getString("Type"),
                            rs.getString("CreatedByName"),
                            rs.getBigDecimal("TotalValue"),
                            ts != null ? ts.toLocalDateTime() : null));
                }
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "DashboardDAO.getMyPendingReturnsForShift - userId=" + userId
                            + ", shiftId=" + shiftId, e);
        }
        return list;
    }

}
