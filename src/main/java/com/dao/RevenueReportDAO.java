package com.dao;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.utils.DBConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DAO rieng cho trang "Bao cao doanh thu" (khong extends BaseDAO vi day la
 * cac truy van THONG KE/GOP NHOM, khong phai CRUD 1 entity/1 bang nhu cac
 * DAO khac). Chi tinh tren hoa don Status = 'ACTIVE' (hoa don da HUY khong
 * duoc tinh vao doanh thu).
 */
public class RevenueReportDAO {

    private Connection getConnection() throws SQLException {
        return DBConnection.getConnection();
    }

    // ---------------------------------------------------------------
    // DTO ket qua (chi doc, khong phai entity luu DB nen khong dat rieng
    // trong package com.model - dung noi bo cho man hinh bao cao nay).
    // ---------------------------------------------------------------

    public static class Summary {
        public final BigDecimal totalRevenue;
        public final int invoiceCount;
        public final long itemsSold;

        public Summary(BigDecimal totalRevenue, int invoiceCount, long itemsSold) {
            this.totalRevenue = totalRevenue != null ? totalRevenue : BigDecimal.ZERO;
            this.invoiceCount = invoiceCount;
            this.itemsSold = itemsSold;
        }

        public BigDecimal avgOrderValue() {
            if (invoiceCount == 0) return BigDecimal.ZERO;
            return totalRevenue.divide(BigDecimal.valueOf(invoiceCount), 0, java.math.RoundingMode.HALF_UP);
        }

        /** % tang/giam so voi 1 Summary khac (thuong la ky truoc). Null neu ky truoc = 0 (khong co gi de so sanh). */
        public Double growthPercent(Summary previous) {
            if (previous == null || previous.totalRevenue.signum() == 0) return null;
            return totalRevenue.subtract(previous.totalRevenue)
                    .divide(previous.totalRevenue, 4, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
        }
    }

    public static class DailyPoint {
        public final LocalDate date;
        public final BigDecimal revenue;
        public final int invoiceCount;

        public DailyPoint(LocalDate date, BigDecimal revenue, int invoiceCount) {
            this.date = date;
            this.revenue = revenue != null ? revenue : BigDecimal.ZERO;
            this.invoiceCount = invoiceCount;
        }
    }

    public static class PaymentSlice {
        public final String method;
        public final BigDecimal revenue;
        public final int invoiceCount;

        public PaymentSlice(String method, BigDecimal revenue, int invoiceCount) {
            this.method = method;
            this.revenue = revenue != null ? revenue : BigDecimal.ZERO;
            this.invoiceCount = invoiceCount;
        }
    }

    public static class TopProduct {
        public final String productName;
        public final long quantity;
        public final BigDecimal revenue;

        public TopProduct(String productName, long quantity, BigDecimal revenue) {
            this.productName = productName;
            this.quantity = quantity;
            this.revenue = revenue != null ? revenue : BigDecimal.ZERO;
        }
    }

    // ---------------------------------------------------------------
    // DTO rieng cho BAO CAO LOI NHUAN (de bai muc 3.3 - "Bao cao loi nhuan,
    // so sanh giua gia nhap va gia ban"). Gia von lay tu Products.ImportPrice
    // HIEN TAI (he thong khong luu lai gia nhap tai thoi diem ban trong
    // InvoiceDetails) - neu gia nhap 1 SP thay doi theo thoi gian, loi nhuan
    // cua cac hoa don CU se duoc tinh lai theo gia nhap MOI NHAT. Day la gioi
    // han da biet, chap nhan duoc voi quy mo du lieu hien tai cua SIMS.
    // ---------------------------------------------------------------

    public static class ProfitSummary {
        public final BigDecimal totalRevenue;
        public final BigDecimal totalCost;
        public final BigDecimal totalProfit;

        public ProfitSummary(BigDecimal totalRevenue, BigDecimal totalCost) {
            this.totalRevenue = totalRevenue != null ? totalRevenue : BigDecimal.ZERO;
            this.totalCost = totalCost != null ? totalCost : BigDecimal.ZERO;
            this.totalProfit = this.totalRevenue.subtract(this.totalCost);
        }

        /** Bien loi nhuan (%) = Loi nhuan / Doanh thu * 100. Null neu doanh thu = 0 (khong co gi de tinh %). */
        public Double marginPercent() {
            if (totalRevenue.signum() == 0) return null;
            return totalProfit.divide(totalRevenue, 4, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
        }
    }

    public static class ProductProfit {
        public final String productName;
        public final long quantity;
        public final BigDecimal revenue;
        public final BigDecimal cost;
        public final BigDecimal profit;

        public ProductProfit(String productName, long quantity, BigDecimal revenue, BigDecimal cost) {
            this.productName = productName;
            this.quantity = quantity;
            this.revenue = revenue != null ? revenue : BigDecimal.ZERO;
            this.cost = cost != null ? cost : BigDecimal.ZERO;
            this.profit = this.revenue.subtract(this.cost);
        }
    }

    public static class CategoryProfit {
        public final String categoryName;
        public final BigDecimal revenue;
        public final BigDecimal cost;
        public final BigDecimal profit;

        public CategoryProfit(String categoryName, BigDecimal revenue, BigDecimal cost) {
            this.categoryName = categoryName;
            this.revenue = revenue != null ? revenue : BigDecimal.ZERO;
            this.cost = cost != null ? cost : BigDecimal.ZERO;
            this.profit = this.revenue.subtract(this.cost);
        }
    }

    // ---------------------------------------------------------------
    // Truy van
    // ---------------------------------------------------------------

    /** Tong quan (doanh thu, so hoa don, so mat hang da ban) trong [from, to] (bao gom ca 2 dau). */
    public Summary getSummary(LocalDate from, LocalDate to) {
        String invoiceSql = "SELECT ISNULL(SUM(TotalAmount), 0) AS Revenue, COUNT(*) AS Cnt "
                + "FROM Invoices WHERE Status = 'ACTIVE' AND CAST(CreatedAt AS DATE) BETWEEN ? AND ?";
        String itemsSql = "SELECT ISNULL(SUM(d.Quantity), 0) FROM InvoiceDetails d "
                + "JOIN Invoices inv ON d.InvoiceID = inv.InvoiceID "
                + "WHERE inv.Status = 'ACTIVE' AND CAST(inv.CreatedAt AS DATE) BETWEEN ? AND ?";

        BigDecimal revenue = BigDecimal.ZERO;
        int invoiceCount = 0;
        long itemsSold = 0;

        try (Connection con = getConnection()) {
            try (PreparedStatement ps = con.prepareStatement(invoiceSql)) {
                ps.setDate(1, Date.valueOf(from));
                ps.setDate(2, Date.valueOf(to));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        revenue = rs.getBigDecimal("Revenue");
                        invoiceCount = rs.getInt("Cnt");
                    }
                }
            }
            try (PreparedStatement ps = con.prepareStatement(itemsSql)) {
                ps.setDate(1, Date.valueOf(from));
                ps.setDate(2, Date.valueOf(to));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) itemsSold = rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "RevenueReportDAO.getSummary - from=" + from + " to=" + to, e);
        }
        return new Summary(revenue, invoiceCount, itemsSold);
    }

    /**
     * Doanh thu tung ngay trong [from, to]. Luon tra ve DU moi ngay trong
     * khoang (kha nang khong co hoa don nao trong ngay do -> revenue = 0),
     * de RevenueChartPanel ve truc lien tuc, khong bi "nhay coc" ngay thieu du lieu.
     */
    public List<DailyPoint> getDailyRevenue(LocalDate from, LocalDate to) {
        String sql = "SELECT CAST(CreatedAt AS DATE) AS Day, SUM(TotalAmount) AS Revenue, COUNT(*) AS Cnt "
                + "FROM Invoices WHERE Status = 'ACTIVE' AND CAST(CreatedAt AS DATE) BETWEEN ? AND ? "
                + "GROUP BY CAST(CreatedAt AS DATE) ORDER BY Day ASC";

        Map<LocalDate, DailyPoint> byDay = new LinkedHashMap<>();
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(from));
            ps.setDate(2, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LocalDate day = rs.getDate("Day").toLocalDate();
                    byDay.put(day, new DailyPoint(day, rs.getBigDecimal("Revenue"), rs.getInt("Cnt")));
                }
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "RevenueReportDAO.getDailyRevenue - from=" + from + " to=" + to, e);
        }

        List<DailyPoint> result = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            result.add(byDay.getOrDefault(d, new DailyPoint(d, BigDecimal.ZERO, 0)));
        }
        return result;
    }

    /** Doanh thu gop nhom theo phuong thuc thanh toan (CASH/BANK_TRANSFER/PAYPAL/CARD), sap xep giam dan. */
    public List<PaymentSlice> getRevenueByPaymentMethod(LocalDate from, LocalDate to) {
        String sql = "SELECT PaymentMethod, SUM(TotalAmount) AS Revenue, COUNT(*) AS Cnt "
                + "FROM Invoices WHERE Status = 'ACTIVE' AND CAST(CreatedAt AS DATE) BETWEEN ? AND ? "
                + "GROUP BY PaymentMethod ORDER BY Revenue DESC";

        List<PaymentSlice> list = new ArrayList<>();
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(from));
            ps.setDate(2, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new PaymentSlice(rs.getString("PaymentMethod"), rs.getBigDecimal("Revenue"), rs.getInt("Cnt")));
                }
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "RevenueReportDAO.getRevenueByPaymentMethod - from=" + from + " to=" + to, e);
        }
        return list;
    }

    /** Top san pham ban chay nhat (theo doanh thu) trong [from, to], toi da {@code limit} dong. */
    public List<TopProduct> getTopProducts(LocalDate from, LocalDate to, int limit) {
        String sql = "SELECT TOP (?) p.ProductName, SUM(d.Quantity) AS Qty, SUM(d.LineTotal) AS Revenue "
                + "FROM InvoiceDetails d "
                + "JOIN Invoices inv ON d.InvoiceID = inv.InvoiceID "
                + "JOIN Products p ON p.ProductID = d.ProductID "
                + "WHERE inv.Status = 'ACTIVE' AND CAST(inv.CreatedAt AS DATE) BETWEEN ? AND ? "
                + "GROUP BY p.ProductID, p.ProductName "
                + "ORDER BY Revenue DESC";

        List<TopProduct> list = new ArrayList<>();
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ps.setDate(2, Date.valueOf(from));
            ps.setDate(3, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new TopProduct(rs.getString("ProductName"), rs.getLong("Qty"), rs.getBigDecimal("Revenue")));
                }
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "RevenueReportDAO.getTopProducts - from=" + from + " to=" + to, e);
        }
        return list;
    }

    // ---------------------------------------------------------------
    // Bao cao loi nhuan (doanh thu - gia von theo Products.ImportPrice hien tai)
    // ---------------------------------------------------------------

    /** Tong doanh thu, tong gia von va loi nhuan trong [from, to]. */
    public ProfitSummary getProfitSummary(LocalDate from, LocalDate to) {
        String sql = "SELECT ISNULL(SUM(d.LineTotal), 0) AS Revenue, ISNULL(SUM(d.Quantity * p.ImportPrice), 0) AS Cost "
                + "FROM InvoiceDetails d "
                + "JOIN Invoices inv ON d.InvoiceID = inv.InvoiceID "
                + "JOIN Products p ON p.ProductID = d.ProductID "
                + "WHERE inv.Status = 'ACTIVE' AND CAST(inv.CreatedAt AS DATE) BETWEEN ? AND ?";

        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(from));
            ps.setDate(2, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    revenue = rs.getBigDecimal("Revenue");
                    cost = rs.getBigDecimal("Cost");
                }
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "RevenueReportDAO.getProfitSummary - from=" + from + " to=" + to, e);
        }
        return new ProfitSummary(revenue, cost);
    }

    /**
     * Loi nhuan tung ngay trong [from, to], tra ve du moi ngay (ngay khong co
     * hoa don thi loi nhuan = 0) de {@link com.components.report.RevenueChartPanel}
     * ve truc lien tuc - tai su dung lai DailyPoint (truong {@code revenue} o
     * day mang y nghia la LOI NHUAN cua ngay do, khong phai doanh thu).
     */
    public List<DailyPoint> getDailyProfit(LocalDate from, LocalDate to) {
        String sql = "SELECT CAST(inv.CreatedAt AS DATE) AS Day, "
                + "SUM(d.LineTotal) AS Revenue, SUM(d.Quantity * p.ImportPrice) AS Cost, "
                + "COUNT(DISTINCT inv.InvoiceID) AS Cnt "
                + "FROM InvoiceDetails d "
                + "JOIN Invoices inv ON d.InvoiceID = inv.InvoiceID "
                + "JOIN Products p ON p.ProductID = d.ProductID "
                + "WHERE inv.Status = 'ACTIVE' AND CAST(inv.CreatedAt AS DATE) BETWEEN ? AND ? "
                + "GROUP BY CAST(inv.CreatedAt AS DATE) ORDER BY Day ASC";

        Map<LocalDate, DailyPoint> byDay = new LinkedHashMap<>();
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(from));
            ps.setDate(2, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LocalDate day = rs.getDate("Day").toLocalDate();
                    BigDecimal revenue = rs.getBigDecimal("Revenue");
                    BigDecimal cost = rs.getBigDecimal("Cost");
                    BigDecimal profit = (revenue != null ? revenue : BigDecimal.ZERO)
                            .subtract(cost != null ? cost : BigDecimal.ZERO);
                    byDay.put(day, new DailyPoint(day, profit, rs.getInt("Cnt")));
                }
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "RevenueReportDAO.getDailyProfit - from=" + from + " to=" + to, e);
        }

        List<DailyPoint> result = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            result.add(byDay.getOrDefault(d, new DailyPoint(d, BigDecimal.ZERO, 0)));
        }
        return result;
    }

    /** Top san pham theo LOI NHUAN (khong phai doanh thu) trong [from, to], toi da {@code limit} dong. */
    public List<ProductProfit> getTopProductsByProfit(LocalDate from, LocalDate to, int limit) {
        String sql = "SELECT TOP (?) p.ProductName, SUM(d.Quantity) AS Qty, "
                + "SUM(d.LineTotal) AS Revenue, SUM(d.Quantity * p.ImportPrice) AS Cost "
                + "FROM InvoiceDetails d "
                + "JOIN Invoices inv ON d.InvoiceID = inv.InvoiceID "
                + "JOIN Products p ON p.ProductID = d.ProductID "
                + "WHERE inv.Status = 'ACTIVE' AND CAST(inv.CreatedAt AS DATE) BETWEEN ? AND ? "
                + "GROUP BY p.ProductID, p.ProductName "
                + "ORDER BY (SUM(d.LineTotal) - SUM(d.Quantity * p.ImportPrice)) DESC";

        List<ProductProfit> list = new ArrayList<>();
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ps.setDate(2, Date.valueOf(from));
            ps.setDate(3, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new ProductProfit(rs.getString("ProductName"), rs.getLong("Qty"),
                            rs.getBigDecimal("Revenue"), rs.getBigDecimal("Cost")));
                }
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "RevenueReportDAO.getTopProductsByProfit - from=" + from + " to=" + to, e);
        }
        return list;
    }

    /** Loi nhuan gop nhom theo danh muc san pham trong [from, to], sap xep giam dan theo loi nhuan. */
    public List<CategoryProfit> getProfitByCategory(LocalDate from, LocalDate to) {
        String sql = "SELECT c.CategoryName, SUM(d.LineTotal) AS Revenue, SUM(d.Quantity * p.ImportPrice) AS Cost "
                + "FROM InvoiceDetails d "
                + "JOIN Invoices inv ON d.InvoiceID = inv.InvoiceID "
                + "JOIN Products p ON p.ProductID = d.ProductID "
                + "JOIN Categories c ON c.CategoryID = p.CategoryID "
                + "WHERE inv.Status = 'ACTIVE' AND CAST(inv.CreatedAt AS DATE) BETWEEN ? AND ? "
                + "GROUP BY c.CategoryID, c.CategoryName "
                + "ORDER BY (SUM(d.LineTotal) - SUM(d.Quantity * p.ImportPrice)) DESC";

        List<CategoryProfit> list = new ArrayList<>();
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(from));
            ps.setDate(2, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new CategoryProfit(rs.getString("CategoryName"),
                            rs.getBigDecimal("Revenue"), rs.getBigDecimal("Cost")));
                }
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "RevenueReportDAO.getProfitByCategory - from=" + from + " to=" + to, e);
        }
        return list;
    }
}