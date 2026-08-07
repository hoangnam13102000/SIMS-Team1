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

    /**
     * 1 diem du lieu "Thu / Chi / Loi nhuan rong" theo ngay, dung cho bieu do
     * cot nhom trong tab Loi nhuan (xem {@link com.components.report.FinanceChartPanel}).
     * "Thu" = doanh thu ban hang (chua VAT). "Chi" = gia von hang ban + thiet
     * hai hang huy trong ngay do (disposalLoss). Tach rieng cost/disposalLoss
     * (khong gop san) de chart/tooltip van hien duoc tung phan cau thanh "Chi".
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

        /** Tong "Chi" = gia von + thiet hai. */
        public BigDecimal totalExpense() {
            return cost.add(disposalLoss);
        }

        /** Loi nhuan rong cua ngay = Thu - Chi. */
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
        /** Tong thiet hai hang huy (StockDisposals.TotalLossAmount) trong ky - xem StockDisposalDAO.sumLossBetween. */
        public final BigDecimal totalLoss;
        /** Loi nhuan GOP = Revenue - Cost (chua tru thiet hai) - giu de tuong thich cac noi dang dung. */
        public final BigDecimal totalProfit;
        /** Loi nhuan RONG = Revenue - Cost - Loss (da tru ca hang huy) - so "that" nen dung de bao cao. */
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

        /** Bien loi nhuan RONG (%) = Loi nhuan rong / Doanh thu * 100. Null neu doanh thu = 0. */
        public Double netMarginPercent() {
            if (totalRevenue.signum() == 0) return null;
            return netProfit.divide(totalRevenue, 4, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
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
    // DTO rieng cho "Xu huong ban hang theo thang & danh muc" (bieu do
    // duong nhieu chuoi so lieu - 1 duong / danh muc). Tach 2 lop: 1 diem
    // tho tu SQL (MonthlyCategoryPoint) va 1 goi da PIVOT san theo thang de
    // MonthlyCategoryTrendPanel chi viec ve, khong phai tu gop nhom lai.
    // ---------------------------------------------------------------

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

    /** 1 duong (danh muc) tren bieu do: so lieu da can chinh du cho MOI thang trong khoang loc. */
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

    /** Ket qua da PIVOT: truc thang (day du, khong thieu thang nao) + danh sach duong theo danh muc. */
    public static class MonthlyCategoryTrend {
        public final List<YearMonth> months;
        public final List<CategorySeries> series;

        public MonthlyCategoryTrend(List<YearMonth> months, List<CategorySeries> series) {
            this.months = months;
            this.series = series;
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
        // Thiet hai hang huy trong ky - tai su dung StockDisposalDAO (cung package
        // com.dao) thay vi lap lai query, de luon dong bo voi StockDisposalPanel.
        BigDecimal loss = new StockDisposalDAO().sumLossBetween(from, to);
        return new ProfitSummary(revenue, cost, loss);
    }

    /**
     * "Thu / Chi / Loi nhuan rong" tung ngay trong [from, to], tra du moi ngay
     * (ngay khong co du lieu thi = 0) de bieu do cot nhom ve truc lien tuc.
     * "Chi" gom 2 phan: gia von hang ban (tu InvoiceDetails, JOIN Products
     * lay gia nhap HIEN TAI - cung gioi han da ghi chu o getDailyProfit) va
     * thiet hai hang huy trong ngay (tu StockDisposals.TotalLossAmount, chi
     * tinh phieu Status='COMPLETED').
     */
    public List<DailyFinancePoint> getDailyFinance(LocalDate from, LocalDate to) {
        String salesSql = "SELECT CAST(inv.CreatedAt AS DATE) AS Day, "
                + "SUM(d.LineTotal) AS Revenue, SUM(d.Quantity * p.ImportPrice) AS Cost, "
                + "COUNT(DISTINCT inv.InvoiceID) AS Cnt "
                + "FROM InvoiceDetails d "
                + "JOIN Invoices inv ON d.InvoiceID = inv.InvoiceID "
                + "JOIN Products p ON p.ProductID = d.ProductID "
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

    // ---------------------------------------------------------------
    // Xu huong ban hang theo thang & danh muc (bieu do duong nhieu chuoi)
    // ---------------------------------------------------------------

    /**
     * So luong + doanh thu ban ra, gop nhom theo (nam, thang, danh muc) trong
     * [from, to]. Tra ve dang "tho" (1 dong / thang / danh muc co du lieu) -
     * dung {@link #getMonthlyCategoryTrend(LocalDate, LocalDate)} de co ban
     * da PIVOT san, dien du thang trong ("hut" thang khong co doanh so = 0)
     * cho bieu do duong ve truc lien tuc.
     */
    public List<MonthlyCategoryPoint> getMonthlySalesByCategory(LocalDate from, LocalDate to) {
        String sql = "SELECT YEAR(inv.CreatedAt) AS Yr, MONTH(inv.CreatedAt) AS Mo, "
                + "c.CategoryName AS CategoryName, SUM(d.Quantity) AS Qty, SUM(d.LineTotal) AS Revenue "
                + "FROM InvoiceDetails d "
                + "JOIN Invoices inv ON d.InvoiceID = inv.InvoiceID "
                + "JOIN Products p ON p.ProductID = d.ProductID "
                + "JOIN Categories c ON c.CategoryID = p.CategoryID "
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
     * Ban PIVOT san cua {@link #getMonthlySalesByCategory(LocalDate, LocalDate)}:
     * dien DU moi thang trong [from, to] (thang khong co don hang -> so luong = 0)
     * de {@link com.components.report.MonthlyCategoryTrendPanel} ve cac duong
     * lien tuc, khong bi "gay khuc" o thang thieu du lieu. Danh muc duoc sap
     * xep giam dan theo tong so luong ban ra (danh muc ban chay nhat len dau
     * danh sach chu thich).
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
            if (idx < 0) continue; // an toan: khong roi vao khoang loc thi bo qua

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