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
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DAO riêng cho trang "Báo cáo doanh thu".
 * Chỉ tính trên hóa đơn Status = 'ACTIVE'.
 * Đã trừ hàng trả (ReturnExchange APPROVED, Direction = 'IN') để
 * doanh thu / giá vốn / lợi nhuận phản ánh đúng sau khi trả hàng.
 */
public class RevenueReportDAO {

    private Connection getConnection() throws SQLException {
        return DBConnection.getConnection();
    }

    // ---------------------------------------------------------------
    // DTO kết quả
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

        /** % tăng/giảm so với 1 Summary khác (thường là kỳ trước). Null nếu kỳ trước = 0. */
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

    /**
     * 1 điểm dữ liệu "Thu / Chi / Lợi nhuận ròng" theo ngày.
     * "Thu" = doanh thu bán hàng (đã trừ trả hàng).
     * "Chi" = giá vốn hàng bán (đã trừ trả hàng) + thiệt hại hàng hủy.
     */
    public static class DailyFinancePoint {
        public final LocalDate date;
        public final BigDecimal revenue;
        public final BigDecimal cost;
        public final BigDecimal disposalLoss;
        public final int invoiceCount;

        public DailyFinancePoint(LocalDate date, BigDecimal revenue, BigDecimal cost,
                                  BigDecimal disposalLoss, int invoiceCount) {
            this.date = date;
            this.revenue = revenue != null ? revenue : BigDecimal.ZERO;
            this.cost = cost != null ? cost : BigDecimal.ZERO;
            this.disposalLoss = disposalLoss != null ? disposalLoss : BigDecimal.ZERO;
            this.invoiceCount = invoiceCount;
        }

        /** Tổng "Chi" = giá vốn + thiệt hại. */
        public BigDecimal totalExpense() {
            return cost.add(disposalLoss);
        }

        /** Lợi nhuận ròng của ngày = Thu - Chi. */
        public BigDecimal netProfit() {
            return revenue.subtract(totalExpense());
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

    public static class ProfitSummary {
        public final BigDecimal totalRevenue;
        public final BigDecimal totalCost;
        /** Tổng thiệt hại hàng hủy (StockDisposals.TotalLossAmount) trong kỳ. */
        public final BigDecimal totalLoss;
        /** Lợi nhuận gộp = Revenue - Cost (chưa trừ thiệt hại). */
        public final BigDecimal totalProfit;
        /** Lợi nhuận ròng = Revenue - Cost - Loss. */
        public final BigDecimal netProfit;

        public ProfitSummary(BigDecimal totalRevenue, BigDecimal totalCost) {
            this(totalRevenue, totalCost, BigDecimal.ZERO);
        }

        public ProfitSummary(BigDecimal totalRevenue, BigDecimal totalCost, BigDecimal totalLoss) {
            this.totalRevenue = totalRevenue != null ? totalRevenue : BigDecimal.ZERO;
            this.totalCost = totalCost != null ? totalCost : BigDecimal.ZERO;
            this.totalLoss = totalLoss != null ? totalLoss : BigDecimal.ZERO;
            this.totalProfit = this.totalRevenue.subtract(this.totalCost);
            this.netProfit = this.totalProfit.subtract(this.totalLoss);
        }

        /** Biên lợi nhuận ròng (%) = Lợi nhuận ròng / Doanh thu * 100. Null nếu doanh thu = 0. */
        public Double netMarginPercent() {
            if (totalRevenue.signum() == 0) return null;
            return netProfit.divide(totalRevenue, 4, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
        }

        /** Biên lợi nhuận (%) = Lợi nhuận / Doanh thu * 100. Null nếu doanh thu = 0. */
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

    public static class MonthlyCategoryPoint {
        public final YearMonth month;
        public final String categoryName;
        public final long quantity;
        public final BigDecimal revenue;

        public MonthlyCategoryPoint(YearMonth month, String categoryName, long quantity, BigDecimal revenue) {
            this.month = month;
            this.categoryName = categoryName;
            this.quantity = quantity;
            this.revenue = revenue != null ? revenue : BigDecimal.ZERO;
        }
    }

    /** 1 đường (danh mục) trên biểu đồ: số liệu đã can chỉnh đủ cho MỌI tháng trong khoảng lọc. */
    public static class CategorySeries {
        public final String categoryName;
        public final List<Long> quantityByMonth;
        public final List<BigDecimal> revenueByMonth;
        public final long totalQuantity;

        public CategorySeries(String categoryName, List<Long> quantityByMonth,
                               List<BigDecimal> revenueByMonth, long totalQuantity) {
            this.categoryName = categoryName;
            this.quantityByMonth = quantityByMonth;
            this.revenueByMonth = revenueByMonth;
            this.totalQuantity = totalQuantity;
        }
    }

    /** Kết quả đã PIVOT: trục tháng (đầy đủ) + danh sách đường theo danh mục. */
    public static class MonthlyCategoryTrend {
        public final List<YearMonth> months;
        public final List<CategorySeries> series;

        public MonthlyCategoryTrend(List<YearMonth> months, List<CategorySeries> series) {
            this.months = months;
            this.series = series;
        }
    }

    // ---------------------------------------------------------------
    // Subquery dùng chung để trừ hàng trả đã duyệt
    // ---------------------------------------------------------------
    private static final String RETURN_JOIN =
            "LEFT JOIN ( "
            + "    SELECT r.InvoiceID, rd.ProductID, "
            + "           SUM(rd.Quantity) AS ReturnedQty, "
            + "           SUM(rd.Quantity * rd.UnitPrice) AS ReturnedValue "
            + "    FROM ReturnExchangeDetails rd "
            + "    JOIN ReturnExchanges r ON r.ReturnID = rd.ReturnID "
            + "    WHERE r.Status = 'APPROVED' AND rd.Direction = 'IN' "
            + "    GROUP BY r.InvoiceID, rd.ProductID "
            + ") ret ON ret.InvoiceID = d.InvoiceID AND ret.ProductID = d.ProductID ";

    // ---------------------------------------------------------------
    // Truy vấn
    // ---------------------------------------------------------------

    /** Tổng quan (doanh thu, số hóa đơn, số mặt hàng đã bán net) trong [from, to]. */
    public Summary getSummary(LocalDate from, LocalDate to) {
        // TotalAmount đã được trigger giảm khi trả hàng → dùng luôn
        String invoiceSql = "SELECT ISNULL(SUM(TotalAmount), 0) AS Revenue, COUNT(*) AS Cnt "
                + "FROM Invoices WHERE Status = 'ACTIVE' AND CAST(CreatedAt AS DATE) BETWEEN ? AND ?";

        // Số lượng bán net = Quantity - ReturnedQty
        String itemsSql = "SELECT ISNULL(SUM(d.Quantity - ISNULL(ret.ReturnedQty, 0)), 0) "
                + "FROM InvoiceDetails d "
                + "JOIN Invoices inv ON d.InvoiceID = inv.InvoiceID "
                + RETURN_JOIN
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
     * Doanh thu từng ngày trong [from, to].
     * Dùng TotalAmount (đã được trigger điều chỉnh khi trả hàng).
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

    /** Doanh thu nhóm theo phương thức thanh toán. */
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

    /** Top sản phẩm bán chạy (đã trừ số lượng trả). */
    public List<TopProduct> getTopProducts(LocalDate from, LocalDate to, int limit) {
        String sql = "SELECT TOP (?) p.ProductName, "
                + "SUM(d.Quantity - ISNULL(ret.ReturnedQty, 0)) AS Qty, "
                + "SUM(d.LineTotal - ISNULL(ret.ReturnedValue, 0)) AS Revenue "
                + "FROM InvoiceDetails d "
                + "JOIN Invoices inv ON d.InvoiceID = inv.InvoiceID "
                + "JOIN Products p ON p.ProductID = d.ProductID "
                + RETURN_JOIN
                + "WHERE inv.Status = 'ACTIVE' AND CAST(inv.CreatedAt AS DATE) BETWEEN ? AND ? "
                + "GROUP BY p.ProductID, p.ProductName "
                + "HAVING SUM(d.Quantity - ISNULL(ret.ReturnedQty, 0)) > 0 "
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
    // Báo cáo lợi nhuận
    // ---------------------------------------------------------------

    /** Tổng doanh thu, giá vốn, lợi nhuận (đã trừ hàng trả). */
    public ProfitSummary getProfitSummary(LocalDate from, LocalDate to) {
        String sql = "SELECT "
                + "ISNULL(SUM(d.LineTotal - ISNULL(ret.ReturnedValue, 0)), 0) AS Revenue, "
                + "ISNULL(SUM( (d.Quantity - ISNULL(ret.ReturnedQty, 0)) * p.ImportPrice ), 0) AS Cost "
                + "FROM InvoiceDetails d "
                + "JOIN Invoices inv ON d.InvoiceID = inv.InvoiceID "
                + "JOIN Products p ON p.ProductID = d.ProductID "
                + RETURN_JOIN
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
        BigDecimal loss = new StockDisposalDAO().sumLossBetween(from, to);
        return new ProfitSummary(revenue, cost, loss);
    }

    /**
     * "Thu / Chi / Lợi nhuận ròng" từng ngày – dùng cho biểu đồ cột.
     * ĐÃ TRỪ hàng trả nên cột Doanh thu và Chi sẽ thay đổi khi có trả hàng.
     */
    public List<DailyFinancePoint> getDailyFinance(LocalDate from, LocalDate to) {
        String salesSql = "SELECT CAST(inv.CreatedAt AS DATE) AS Day, "
                + "SUM(d.LineTotal - ISNULL(ret.ReturnedValue, 0)) AS Revenue, "
                + "SUM( (d.Quantity - ISNULL(ret.ReturnedQty, 0)) * p.ImportPrice ) AS Cost, "
                + "COUNT(DISTINCT inv.InvoiceID) AS Cnt "
                + "FROM InvoiceDetails d "
                + "JOIN Invoices inv ON d.InvoiceID = inv.InvoiceID "
                + "JOIN Products p ON p.ProductID = d.ProductID "
                + RETURN_JOIN
                + "WHERE inv.Status = 'ACTIVE' AND CAST(inv.CreatedAt AS DATE) BETWEEN ? AND ? "
                + "GROUP BY CAST(inv.CreatedAt AS DATE)";

        String lossSql = "SELECT CAST(CreatedAt AS DATE) AS Day, SUM(TotalLossAmount) AS Loss "
                + "FROM StockDisposals WHERE Status = 'COMPLETED' "
                + "AND CAST(CreatedAt AS DATE) BETWEEN ? AND ? "
                + "GROUP BY CAST(CreatedAt AS DATE)";

        Map<LocalDate, BigDecimal> revenueByDay = new LinkedHashMap<>();
        Map<LocalDate, BigDecimal> costByDay = new LinkedHashMap<>();
        Map<LocalDate, Integer> cntByDay = new LinkedHashMap<>();
        Map<LocalDate, BigDecimal> lossByDay = new LinkedHashMap<>();

        try (Connection con = getConnection()) {
            try (PreparedStatement ps = con.prepareStatement(salesSql)) {
                ps.setDate(1, Date.valueOf(from));
                ps.setDate(2, Date.valueOf(to));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        LocalDate day = rs.getDate("Day").toLocalDate();
                        revenueByDay.put(day, rs.getBigDecimal("Revenue"));
                        costByDay.put(day, rs.getBigDecimal("Cost"));
                        cntByDay.put(day, rs.getInt("Cnt"));
                    }
                }
            }
            try (PreparedStatement ps = con.prepareStatement(lossSql)) {
                ps.setDate(1, Date.valueOf(from));
                ps.setDate(2, Date.valueOf(to));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        LocalDate day = rs.getDate("Day").toLocalDate();
                        lossByDay.put(day, rs.getBigDecimal("Loss"));
                    }
                }
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "RevenueReportDAO.getDailyFinance - from=" + from + " to=" + to, e);
        }

        List<DailyFinancePoint> result = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            result.add(new DailyFinancePoint(d,
                    revenueByDay.get(d), costByDay.get(d), lossByDay.get(d),
                    cntByDay.getOrDefault(d, 0)));
        }
        return result;
    }

    /** Lợi nhuận từng ngày (đã trừ hàng trả). */
    public List<DailyPoint> getDailyProfit(LocalDate from, LocalDate to) {
        String sql = "SELECT CAST(inv.CreatedAt AS DATE) AS Day, "
                + "SUM(d.LineTotal - ISNULL(ret.ReturnedValue, 0)) AS Revenue, "
                + "SUM( (d.Quantity - ISNULL(ret.ReturnedQty, 0)) * p.ImportPrice ) AS Cost, "
                + "COUNT(DISTINCT inv.InvoiceID) AS Cnt "
                + "FROM InvoiceDetails d "
                + "JOIN Invoices inv ON d.InvoiceID = inv.InvoiceID "
                + "JOIN Products p ON p.ProductID = d.ProductID "
                + RETURN_JOIN
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

    /** Top sản phẩm theo lợi nhuận (đã trừ hàng trả). */
    public List<ProductProfit> getTopProductsByProfit(LocalDate from, LocalDate to, int limit) {
        String sql = "SELECT TOP (?) p.ProductName, "
                + "SUM(d.Quantity - ISNULL(ret.ReturnedQty, 0)) AS Qty, "
                + "SUM(d.LineTotal - ISNULL(ret.ReturnedValue, 0)) AS Revenue, "
                + "SUM( (d.Quantity - ISNULL(ret.ReturnedQty, 0)) * p.ImportPrice ) AS Cost "
                + "FROM InvoiceDetails d "
                + "JOIN Invoices inv ON d.InvoiceID = inv.InvoiceID "
                + "JOIN Products p ON p.ProductID = d.ProductID "
                + RETURN_JOIN
                + "WHERE inv.Status = 'ACTIVE' AND CAST(inv.CreatedAt AS DATE) BETWEEN ? AND ? "
                + "GROUP BY p.ProductID, p.ProductName "
                + "HAVING SUM(d.Quantity - ISNULL(ret.ReturnedQty, 0)) > 0 "
                + "ORDER BY (SUM(d.LineTotal - ISNULL(ret.ReturnedValue, 0)) "
                + "        - SUM( (d.Quantity - ISNULL(ret.ReturnedQty, 0)) * p.ImportPrice )) DESC";

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

    /** Lợi nhuận theo danh mục (đã trừ hàng trả). */
    public List<CategoryProfit> getProfitByCategory(LocalDate from, LocalDate to) {
        String sql = "SELECT c.CategoryName, "
                + "SUM(d.LineTotal - ISNULL(ret.ReturnedValue, 0)) AS Revenue, "
                + "SUM( (d.Quantity - ISNULL(ret.ReturnedQty, 0)) * p.ImportPrice ) AS Cost "
                + "FROM InvoiceDetails d "
                + "JOIN Invoices inv ON d.InvoiceID = inv.InvoiceID "
                + "JOIN Products p ON p.ProductID = d.ProductID "
                + "JOIN Categories c ON c.CategoryID = p.CategoryID "
                + RETURN_JOIN
                + "WHERE inv.Status = 'ACTIVE' AND CAST(inv.CreatedAt AS DATE) BETWEEN ? AND ? "
                + "GROUP BY c.CategoryID, c.CategoryName "
                + "ORDER BY (SUM(d.LineTotal - ISNULL(ret.ReturnedValue, 0)) "
                + "        - SUM( (d.Quantity - ISNULL(ret.ReturnedQty, 0)) * p.ImportPrice )) DESC";

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

    // ---------------------------------------------------------------
    // Xu hướng bán hàng theo tháng & danh mục
    // ---------------------------------------------------------------

    public List<MonthlyCategoryPoint> getMonthlySalesByCategory(LocalDate from, LocalDate to) {
        String sql = "SELECT YEAR(inv.CreatedAt) AS Yr, MONTH(inv.CreatedAt) AS Mo, "
                + "c.CategoryName AS CategoryName, "
                + "SUM(d.Quantity - ISNULL(ret.ReturnedQty, 0)) AS Qty, "
                + "SUM(d.LineTotal - ISNULL(ret.ReturnedValue, 0)) AS Revenue "
                + "FROM InvoiceDetails d "
                + "JOIN Invoices inv ON d.InvoiceID = inv.InvoiceID "
                + "JOIN Products p ON p.ProductID = d.ProductID "
                + "JOIN Categories c ON c.CategoryID = p.CategoryID "
                + RETURN_JOIN
                + "WHERE inv.Status = 'ACTIVE' AND CAST(inv.CreatedAt AS DATE) BETWEEN ? AND ? "
                + "GROUP BY YEAR(inv.CreatedAt), MONTH(inv.CreatedAt), c.CategoryID, c.CategoryName "
                + "ORDER BY Yr ASC, Mo ASC, CategoryName ASC";

        List<MonthlyCategoryPoint> list = new ArrayList<>();
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(from));
            ps.setDate(2, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    YearMonth ym = YearMonth.of(rs.getInt("Yr"), rs.getInt("Mo"));
                    list.add(new MonthlyCategoryPoint(ym, rs.getString("CategoryName"),
                            rs.getLong("Qty"), rs.getBigDecimal("Revenue")));
                }
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "RevenueReportDAO.getMonthlySalesByCategory - from=" + from + " to=" + to, e);
        }
        return list;
    }

    /**
     * Bản PIVOT sẵn của getMonthlySalesByCategory:
     * điền ĐỦ mọi tháng trong [from, to] để biểu đồ đường vẽ trục liên tục.
     */
    public MonthlyCategoryTrend getMonthlyCategoryTrend(LocalDate from, LocalDate to) {
        List<MonthlyCategoryPoint> raw = getMonthlySalesByCategory(from, to);

        List<YearMonth> months = new ArrayList<>();
        YearMonth start = YearMonth.from(from);
        YearMonth end = YearMonth.from(to);
        for (YearMonth ym = start; !ym.isAfter(end); ym = ym.plusMonths(1)) {
            months.add(ym);
        }
        int n = months.size();

        Map<String, long[]> qtyByCategory = new LinkedHashMap<>();
        Map<String, BigDecimal[]> revenueByCategory = new LinkedHashMap<>();

        for (MonthlyCategoryPoint p : raw) {
            int idx = months.indexOf(p.month);
            if (idx < 0) continue;

            long[] qtyArr = qtyByCategory.computeIfAbsent(p.categoryName, k -> new long[n]);
            BigDecimal[] revArr = revenueByCategory.computeIfAbsent(p.categoryName, k -> {
                BigDecimal[] arr = new BigDecimal[n];
                Arrays.fill(arr, BigDecimal.ZERO);
                return arr;
            });
            qtyArr[idx] += p.quantity;
            revArr[idx] = revArr[idx].add(p.revenue);
        }

        List<CategorySeries> series = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : qtyByCategory.entrySet()) {
            String categoryName = entry.getKey();
            long[] qtyArr = entry.getValue();
            BigDecimal[] revArr = revenueByCategory.get(categoryName);

            List<Long> qtyList = new ArrayList<>(n);
            List<BigDecimal> revList = new ArrayList<>(n);
            long total = 0;
            for (int i = 0; i < n; i++) {
                qtyList.add(qtyArr[i]);
                revList.add(revArr[i]);
                total += qtyArr[i];
            }
            series.add(new CategorySeries(categoryName, qtyList, revList, total));
        }
        series.sort((a, b) -> Long.compare(b.totalQuantity, a.totalQuantity));

        return new MonthlyCategoryTrend(months, series);
    }
}