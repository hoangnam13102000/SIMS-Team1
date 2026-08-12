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
 * <p>
 * <b>Doanh thu thuần (một nguồn sự thật)</b> =
 * {@code SUM(Invoices.TotalAmount)} của HĐ {@code ACTIVE} trong kỳ
 * − giá trị trả hàng đã duyệt (ReturnExchange APPROVED, Direction = IN)
 * gắn với các HĐ đó (theo ngày tạo HĐ).
 * <p>
 * Dùng chung cho StatCard, biểu đồ Thu/Chi, pie PTTT, summary lợi nhuận
 * để các số khớp nhau. Top SP / danh mục vẫn dùng LineTotal − trả
 * (phân tích theo sản phẩm; tổng có thể khác doanh thu thuần vì giảm giá/điểm/VAT cấp HĐ).
 * <p>
 * Chi / lãi ròng: giá vốn sau trả + thiệt hại hủy − hoàn trả NCC.
 */
public class RevenueReportDAO {

    private Connection getConnection() throws SQLException {
        return DBConnection.getConnection();
    }

    // ---------------------------------------------------------------
    // DTO
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
     * Thu / Chi / Lợi nhuận ròng theo ngày.
     * Chi = giá vốn + thiệt hại hủy - hoàn trả NCC.
     */
    public static class DailyFinancePoint {
        public final LocalDate date;
        public final BigDecimal revenue;
        public final BigDecimal cost;
        public final BigDecimal disposalLoss;
        /** Tiền hoàn từ phiếu trả NCC (COMPLETED) trong ngày. */
        public final BigDecimal supplierRefund;
        public final int invoiceCount;

        public DailyFinancePoint(LocalDate date, BigDecimal revenue, BigDecimal cost,
                                  BigDecimal disposalLoss, BigDecimal supplierRefund, int invoiceCount) {
            this.date = date;
            this.revenue = revenue != null ? revenue : BigDecimal.ZERO;
            this.cost = cost != null ? cost : BigDecimal.ZERO;
            this.disposalLoss = disposalLoss != null ? disposalLoss : BigDecimal.ZERO;
            this.supplierRefund = supplierRefund != null ? supplierRefund : BigDecimal.ZERO;
            this.invoiceCount = invoiceCount;
        }

        /** Chi = giá vốn + thiệt hại - hoàn trả NCC */
        public BigDecimal totalExpense() {
            return cost.add(disposalLoss).subtract(supplierRefund);
        }

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
        public final BigDecimal totalLoss;
        /** Tổng tiền hoàn trả NCC trong kỳ. */
        public final BigDecimal totalSupplierRefund;
        public final BigDecimal totalProfit;
        public final BigDecimal netProfit;

        public ProfitSummary(BigDecimal totalRevenue, BigDecimal totalCost) {
            this(totalRevenue, totalCost, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        public ProfitSummary(BigDecimal totalRevenue, BigDecimal totalCost, BigDecimal totalLoss) {
            this(totalRevenue, totalCost, totalLoss, BigDecimal.ZERO);
        }

        public ProfitSummary(BigDecimal totalRevenue, BigDecimal totalCost,
                             BigDecimal totalLoss, BigDecimal totalSupplierRefund) {
            this.totalRevenue = totalRevenue != null ? totalRevenue : BigDecimal.ZERO;
            this.totalCost = totalCost != null ? totalCost : BigDecimal.ZERO;
            this.totalLoss = totalLoss != null ? totalLoss : BigDecimal.ZERO;
            this.totalSupplierRefund = totalSupplierRefund != null ? totalSupplierRefund : BigDecimal.ZERO;
            this.totalProfit = this.totalRevenue.subtract(this.totalCost);
            // Lãi ròng = DT - giá vốn - hủy + hoàn trả NCC
            this.netProfit = this.totalProfit.subtract(this.totalLoss).add(this.totalSupplierRefund);
        }

        public Double netMarginPercent() {
            if (totalRevenue.signum() == 0) return null;
            return netProfit.divide(totalRevenue, 4, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
        }

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

    public static class MonthlyCategoryTrend {
        public final List<YearMonth> months;
        public final List<CategorySeries> series;

        public MonthlyCategoryTrend(List<YearMonth> months, List<CategorySeries> series) {
            this.months = months;
            this.series = series;
        }
    }

    // ---------------------------------------------------------------
    // JOIN trừ hàng khách trả (APPROVED, Direction = IN)
    // ---------------------------------------------------------------

    /** Theo dòng SP (dùng cho top SP / giá vốn / số lượng). Alias bảng chi tiết: d */
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

    /**
     * Theo hóa đơn (dùng cho doanh thu thuần = TotalAmount − trả).
     * Alias bảng hóa đơn: inv
     */
    private static final String RETURN_BY_INVOICE =
            "LEFT JOIN ( "
            + "    SELECT r.InvoiceID, "
            + "           SUM(rd.Quantity * rd.UnitPrice) AS ReturnedValue "
            + "    FROM ReturnExchangeDetails rd "
            + "    JOIN ReturnExchanges r ON r.ReturnID = rd.ReturnID "
            + "    WHERE r.Status = 'APPROVED' AND rd.Direction = 'IN' "
            + "    GROUP BY r.InvoiceID "
            + ") retInv ON retInv.InvoiceID = inv.InvoiceID ";

    /** Doanh thu thuần 1 HĐ: TotalAmount − giá trị trả (không âm). */
    private static final String NET_INVOICE_REVENUE =
            "CASE WHEN inv.TotalAmount - ISNULL(retInv.ReturnedValue, 0) < 0 "
            + "THEN 0 ELSE inv.TotalAmount - ISNULL(retInv.ReturnedValue, 0) END";

    // ---------------------------------------------------------------
    // Truy vấn
    // ---------------------------------------------------------------

    public Summary getSummary(LocalDate from, LocalDate to) {
        // Doanh thu thuần = TotalAmount HĐ ACTIVE − trả hàng đã duyệt (theo HĐ)
        String invoiceSql = "SELECT ISNULL(SUM(" + NET_INVOICE_REVENUE + "), 0) AS Revenue, "
                + "COUNT(*) AS Cnt "
                + "FROM Invoices inv "
                + RETURN_BY_INVOICE
                + "WHERE inv.Status = 'ACTIVE' AND CAST(inv.CreatedAt AS DATE) BETWEEN ? AND ?";

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

    public List<DailyPoint> getDailyRevenue(LocalDate from, LocalDate to) {
        String sql = "SELECT CAST(inv.CreatedAt AS DATE) AS Day, "
                + "ISNULL(SUM(" + NET_INVOICE_REVENUE + "), 0) AS Revenue, "
                + "COUNT(*) AS Cnt "
                + "FROM Invoices inv "
                + RETURN_BY_INVOICE
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

    public List<PaymentSlice> getRevenueByPaymentMethod(LocalDate from, LocalDate to) {
        String sql = "SELECT inv.PaymentMethod, "
                + "ISNULL(SUM(" + NET_INVOICE_REVENUE + "), 0) AS Revenue, "
                + "COUNT(*) AS Cnt "
                + "FROM Invoices inv "
                + RETURN_BY_INVOICE
                + "WHERE inv.Status = 'ACTIVE' AND CAST(inv.CreatedAt AS DATE) BETWEEN ? AND ? "
                + "GROUP BY inv.PaymentMethod ORDER BY Revenue DESC";

        List<PaymentSlice> list = new ArrayList<>();
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(from));
            ps.setDate(2, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new PaymentSlice(rs.getString("PaymentMethod"),
                            rs.getBigDecimal("Revenue"), rs.getInt("Cnt")));
                }
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "RevenueReportDAO.getRevenueByPaymentMethod - from=" + from + " to=" + to, e);
        }
        return list;
    }

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
                    list.add(new TopProduct(rs.getString("ProductName"),
                            rs.getLong("Qty"), rs.getBigDecimal("Revenue")));
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

    public ProfitSummary getProfitSummary(LocalDate from, LocalDate to) {
        // Doanh thu thuần: cùng công thức với getSummary (TotalAmount − trả)
        String revenueSql = "SELECT ISNULL(SUM(" + NET_INVOICE_REVENUE + "), 0) AS Revenue "
                + "FROM Invoices inv "
                + RETURN_BY_INVOICE
                + "WHERE inv.Status = 'ACTIVE' AND CAST(inv.CreatedAt AS DATE) BETWEEN ? AND ?";

        // Giá vốn: theo số lượng còn lại sau trả × ImportPrice
        String costSql = "SELECT ISNULL(SUM( (d.Quantity - ISNULL(ret.ReturnedQty, 0)) * p.ImportPrice ), 0) AS Cost "
                + "FROM InvoiceDetails d "
                + "JOIN Invoices inv ON d.InvoiceID = inv.InvoiceID "
                + "JOIN Products p ON p.ProductID = d.ProductID "
                + RETURN_JOIN
                + "WHERE inv.Status = 'ACTIVE' AND CAST(inv.CreatedAt AS DATE) BETWEEN ? AND ?";

        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        try (Connection con = getConnection()) {
            try (PreparedStatement ps = con.prepareStatement(revenueSql)) {
                ps.setDate(1, Date.valueOf(from));
                ps.setDate(2, Date.valueOf(to));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) revenue = rs.getBigDecimal("Revenue");
                }
            }
            try (PreparedStatement ps = con.prepareStatement(costSql)) {
                ps.setDate(1, Date.valueOf(from));
                ps.setDate(2, Date.valueOf(to));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) cost = rs.getBigDecimal("Cost");
                }
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "RevenueReportDAO.getProfitSummary - from=" + from + " to=" + to, e);
        }

        BigDecimal loss = new StockDisposalDAO().sumLossBetween(from, to);
        BigDecimal supplierRefund = sumSupplierRefundBetween(from, to);
        return new ProfitSummary(revenue, cost, loss, supplierRefund);
    }

    /** Tổng tiền hoàn trả NCC (phiếu COMPLETED) trong [from, to]. */
    public BigDecimal sumSupplierRefundBetween(LocalDate from, LocalDate to) {
        String sql = "SELECT ISNULL(SUM(TotalRefundAmount), 0) FROM SupplierReturns "
                + "WHERE Status = 'COMPLETED' AND CAST(CreatedAt AS DATE) BETWEEN ? AND ?";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(from));
            ps.setDate(2, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    BigDecimal v = rs.getBigDecimal(1);
                    return v != null ? v : BigDecimal.ZERO;
                }
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "RevenueReportDAO.sumSupplierRefundBetween - from=" + from + " to=" + to, e);
        }
        return BigDecimal.ZERO;
    }

    public List<DailyFinancePoint> getDailyFinance(LocalDate from, LocalDate to) {
        // Thu = doanh thu thuần (TotalAmount − trả), cùng công thức getSummary
        String revenueSql = "SELECT CAST(inv.CreatedAt AS DATE) AS Day, "
                + "ISNULL(SUM(" + NET_INVOICE_REVENUE + "), 0) AS Revenue, "
                + "COUNT(*) AS Cnt "
                + "FROM Invoices inv "
                + RETURN_BY_INVOICE
                + "WHERE inv.Status = 'ACTIVE' AND CAST(inv.CreatedAt AS DATE) BETWEEN ? AND ? "
                + "GROUP BY CAST(inv.CreatedAt AS DATE)";

        // Chi (giá vốn) vẫn theo số lượng còn lại sau trả
        String costSql = "SELECT CAST(inv.CreatedAt AS DATE) AS Day, "
                + "SUM( (d.Quantity - ISNULL(ret.ReturnedQty, 0)) * p.ImportPrice ) AS Cost "
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

        String refundSql = "SELECT CAST(CreatedAt AS DATE) AS Day, "
                + "ISNULL(SUM(TotalRefundAmount), 0) AS Refund "
                + "FROM SupplierReturns "
                + "WHERE Status = 'COMPLETED' "
                + "AND CAST(CreatedAt AS DATE) BETWEEN ? AND ? "
                + "GROUP BY CAST(CreatedAt AS DATE)";

        Map<LocalDate, BigDecimal> revenueByDay = new LinkedHashMap<>();
        Map<LocalDate, BigDecimal> costByDay = new LinkedHashMap<>();
        Map<LocalDate, Integer> cntByDay = new LinkedHashMap<>();
        Map<LocalDate, BigDecimal> lossByDay = new LinkedHashMap<>();
        Map<LocalDate, BigDecimal> refundByDay = new LinkedHashMap<>();

        try (Connection con = getConnection()) {
            try (PreparedStatement ps = con.prepareStatement(revenueSql)) {
                ps.setDate(1, Date.valueOf(from));
                ps.setDate(2, Date.valueOf(to));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        LocalDate day = rs.getDate("Day").toLocalDate();
                        revenueByDay.put(day, rs.getBigDecimal("Revenue"));
                        cntByDay.put(day, rs.getInt("Cnt"));
                    }
                }
            }
            try (PreparedStatement ps = con.prepareStatement(costSql)) {
                ps.setDate(1, Date.valueOf(from));
                ps.setDate(2, Date.valueOf(to));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        LocalDate day = rs.getDate("Day").toLocalDate();
                        costByDay.put(day, rs.getBigDecimal("Cost"));
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
            try (PreparedStatement ps = con.prepareStatement(refundSql)) {
                ps.setDate(1, Date.valueOf(from));
                ps.setDate(2, Date.valueOf(to));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        LocalDate day = rs.getDate("Day").toLocalDate();
                        refundByDay.put(day, rs.getBigDecimal("Refund"));
                    }
                }
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "RevenueReportDAO.getDailyFinance - from=" + from + " to=" + to, e);
        }

        List<DailyFinancePoint> result = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            result.add(new DailyFinancePoint(
                    d,
                    revenueByDay.get(d),
                    costByDay.get(d),
                    lossByDay.get(d),
                    refundByDay.get(d),
                    cntByDay.getOrDefault(d, 0)));
        }
        return result;
    }

    public List<DailyPoint> getDailyProfit(LocalDate from, LocalDate to) {
        // Tái sử dụng getDailyFinance để profit ngày = netProfit (Thu − Chi + hoàn NCC)
        // đồng bộ với biểu đồ Thu/Chi và ProfitSummary.
        List<DailyFinancePoint> finance = getDailyFinance(from, to);
        List<DailyPoint> result = new ArrayList<>();
        for (DailyFinancePoint p : finance) {
            result.add(new DailyPoint(p.date, p.netProfit(), p.invoiceCount));
        }
        return result;
    }

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
    // Xu hướng theo tháng & danh mục
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