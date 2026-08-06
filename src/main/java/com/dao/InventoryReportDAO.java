package com.dao;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.dao.RevenueReportDAO.CategorySeries;
import com.dao.RevenueReportDAO.MonthlyCategoryTrend;
import com.utils.DBConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DAO rieng cho trang "Bao cao hang ton kho" (Nhan vien kho / Quan ly kho -
 * quyen STOCK_VIEW). Khong extends BaseDAO vi day la cac truy van THONG
 * KE/GOP NHOM tren nhieu bang, khong phai CRUD 1 entity, cung tinh than voi
 * {@link RevenueReportDAO}.
 *
 * Ton kho HIEN TAI luon lay truc tiep tu Products.Stock/MinStock/SellPrice
 * (khong phan biet Status ACTIVE/DISABLED - nhan vien kho can biet TOAN BO
 * so luong dang nam trong kho vat ly, bat ke san pham co dang mo ban hay
 * khong). "Gia ban" dung de tinh GIA TRI ton kho la SellPrice HIEN TAI cua
 * Products (he thong khong luu lai lich su thay doi gia ban theo thoi gian).
 */
public class InventoryReportDAO {

    private Connection getConnection() throws SQLException {
        return DBConnection.getConnection();
    }

    // ---------------------------------------------------------------
    // DTO ket qua
    // ---------------------------------------------------------------

    public static class OverallSummary {
        public final int productCount;
        public final long totalQuantity;
        public final BigDecimal valueAtSellPrice;
        public final BigDecimal valueAtImportPrice;
        public final int lowStockCount;
        public final int outOfStockCount;

        public OverallSummary(int productCount, long totalQuantity, BigDecimal valueAtSellPrice,
                               BigDecimal valueAtImportPrice, int lowStockCount, int outOfStockCount) {
            this.productCount = productCount;
            this.totalQuantity = totalQuantity;
            this.valueAtSellPrice = valueAtSellPrice != null ? valueAtSellPrice : BigDecimal.ZERO;
            this.valueAtImportPrice = valueAtImportPrice != null ? valueAtImportPrice : BigDecimal.ZERO;
            this.lowStockCount = lowStockCount;
            this.outOfStockCount = outOfStockCount;
        }
    }

    public static class CategoryStock {
        public final String categoryName;
        public final int productCount;
        public final long quantity;
        public final BigDecimal valueAtSellPrice;

        public CategoryStock(String categoryName, int productCount, long quantity, BigDecimal valueAtSellPrice) {
            this.categoryName = categoryName;
            this.productCount = productCount;
            this.quantity = quantity;
            this.valueAtSellPrice = valueAtSellPrice != null ? valueAtSellPrice : BigDecimal.ZERO;
        }
    }

    /** 1 khoang gia ban ("duoi 50k", "50k - 100k"...) - xem {@link #getStockByPriceRange()}. */
    public static class PriceRangeStock {
        public final String label;
        public final int productCount;
        public final long quantity;
        public final BigDecimal valueAtSellPrice;

        public PriceRangeStock(String label, int productCount, long quantity, BigDecimal valueAtSellPrice) {
            this.label = label;
            this.productCount = productCount;
            this.quantity = quantity;
            this.valueAtSellPrice = valueAtSellPrice != null ? valueAtSellPrice : BigDecimal.ZERO;
        }
    }

    /** 1 dong du lieu tho: ton kho cua 1 san pham TAI 1 thoi diem (theo InventoryTransactions.StockAfter). */
    private static class StockEvent {
        final LocalDateTime at;
        final int stockAfter;

        StockEvent(LocalDateTime at, int stockAfter) {
            this.at = at;
            this.stockAfter = stockAfter;
        }
    }

    // ---------------------------------------------------------------
    // Tong quan
    // ---------------------------------------------------------------

    /** Tong quan ton kho HIEN TAI: tong so SP, tong so luong, gia tri theo gia ban/gia nhap, so SP sap/het hang. */
    public OverallSummary getOverallSummary() {
        String sql = "SELECT COUNT(*) AS ProductCount, ISNULL(SUM(Stock), 0) AS TotalQty, "
                + "ISNULL(SUM(CAST(Stock AS DECIMAL(18,2)) * SellPrice), 0) AS ValueSell, "
                + "ISNULL(SUM(CAST(Stock AS DECIMAL(18,2)) * ImportPrice), 0) AS ValueImport, "
                + "SUM(CASE WHEN Stock > 0 AND Stock <= MinStock THEN 1 ELSE 0 END) AS LowStockCnt, "
                + "SUM(CASE WHEN Stock = 0 THEN 1 ELSE 0 END) AS OutOfStockCnt "
                + "FROM Products";

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return new OverallSummary(rs.getInt("ProductCount"), rs.getLong("TotalQty"),
                        rs.getBigDecimal("ValueSell"), rs.getBigDecimal("ValueImport"),
                        rs.getInt("LowStockCnt"), rs.getInt("OutOfStockCnt"));
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "InventoryReportDAO.getOverallSummary", e);
        }
        return new OverallSummary(0, 0, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);
    }

    /** Ton kho gop nhom theo danh muc san pham, sap xep giam dan theo gia tri (tinh theo gia ban). */
    public List<CategoryStock> getStockByCategory() {
        String sql = "SELECT c.CategoryName, COUNT(*) AS ProductCount, ISNULL(SUM(p.Stock), 0) AS TotalQty, "
                + "ISNULL(SUM(CAST(p.Stock AS DECIMAL(18,2)) * p.SellPrice), 0) AS ValueSell "
                + "FROM Products p JOIN Categories c ON c.CategoryID = p.CategoryID "
                + "GROUP BY c.CategoryID, c.CategoryName "
                + "ORDER BY ValueSell DESC";

        List<CategoryStock> list = new ArrayList<>();
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new CategoryStock(rs.getString("CategoryName"), rs.getInt("ProductCount"),
                        rs.getLong("TotalQty"), rs.getBigDecimal("ValueSell")));
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "InventoryReportDAO.getStockByCategory", e);
        }
        return list;
    }

    /**
     * Ton kho gop nhom theo khoang GIA BAN (duoi 50k / 50k-100k / 100k-200k /
     * 200k-500k / tren 500k) - giup nhan vien kho thay von ton kho dang tap
     * trung o phan khuc gia nao. Cac moc gia co dinh, phu hop voi mat bang
     * gia hang tieu dung/sieu thi cua du an nay.
     */
    public List<PriceRangeStock> getStockByPriceRange() {
        String sql = "SELECT "
                + "CASE "
                + "  WHEN SellPrice < 50000 THEN 1 "
                + "  WHEN SellPrice < 100000 THEN 2 "
                + "  WHEN SellPrice < 200000 THEN 3 "
                + "  WHEN SellPrice < 500000 THEN 4 "
                + "  ELSE 5 END AS Bucket, "
                + "COUNT(*) AS ProductCount, ISNULL(SUM(Stock), 0) AS TotalQty, "
                + "ISNULL(SUM(CAST(Stock AS DECIMAL(18,2)) * SellPrice), 0) AS ValueSell "
                + "FROM Products "
                + "GROUP BY CASE "
                + "  WHEN SellPrice < 50000 THEN 1 "
                + "  WHEN SellPrice < 100000 THEN 2 "
                + "  WHEN SellPrice < 200000 THEN 3 "
                + "  WHEN SellPrice < 500000 THEN 4 "
                + "  ELSE 5 END "
                + "ORDER BY Bucket ASC";

        Map<Integer, PriceRangeStock> byBucket = new LinkedHashMap<>();
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int bucket = rs.getInt("Bucket");
                byBucket.put(bucket, new PriceRangeStock(bucketLabel(bucket), rs.getInt("ProductCount"),
                        rs.getLong("TotalQty"), rs.getBigDecimal("ValueSell")));
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "InventoryReportDAO.getStockByPriceRange", e);
        }

        // Luon tra ve DU 5 khoang gia (khoang khong co SP nao -> 0) de bieu do/bang khong bi "thieu cot".
        List<PriceRangeStock> result = new ArrayList<>();
        for (int bucket = 1; bucket <= 5; bucket++) {
            result.add(byBucket.getOrDefault(bucket, new PriceRangeStock(bucketLabel(bucket), 0, 0, BigDecimal.ZERO)));
        }
        return result;
    }

    private static String bucketLabel(int bucket) {
        switch (bucket) {
            case 1: return "Dưới 50.000 đ";
            case 2: return "50.000 - dưới 100.000 đ";
            case 3: return "100.000 - dưới 200.000 đ";
            case 4: return "200.000 - dưới 500.000 đ";
            default: return "Từ 500.000 đ trở lên";
        }
    }

    // ---------------------------------------------------------------
    // Xu huong ton kho theo thang & danh muc (bieu do duong nhieu chuoi,
    // tai su dung kieu du lieu cua RevenueReportDAO de dung chung
    // MonthlyCategoryTrendPanel voi tab "Xu huong ban hang").
    // ---------------------------------------------------------------

    /**
     * Ton kho CUOI MOI THANG trong [from, to], gop nhom theo danh muc - dung
     * de ve bieu do duong "lượng SP thay đổi theo tháng" (vd ca phe bot tang/
     * giam qua cac thang). Tai lai truong {@code quantityByMonth} cua
     * {@link CategorySeries} de mang y nghia TON KHO CUOI THANG (snapshot),
     * KHONG PHAI so luong ban ra nhu ben bao cao doanh thu.
     *
     * Nguon du lieu: InventoryTransactions.StockAfter (ghi lai ton kho sau
     * MOI giao dich nhap/ban/huy/doi-tra/doi-chieu cua tung san pham) - lay
     * gia tri StockAfter GAN NHAT tinh den cuoi moi thang cho tung san pham,
     * roi cong don theo danh muc. San pham chua tung co giao dich nao (du
     * lieu khoi tao san co ton kho) duoc coi la giu NGUYEN ton kho hien tai
     * xuyen suot khoang thoi gian loc (khong co lich su de tinh lui), day la
     * gioi han da biet, chap nhan duoc voi quy mo du lieu hien tai cua SIMS.
     */
    public MonthlyCategoryTrend getMonthlyCategoryStockTrend(LocalDate from, LocalDate to) {
        List<YearMonth> months = new ArrayList<>();
        YearMonth start = YearMonth.from(from);
        YearMonth end = YearMonth.from(to);
        for (YearMonth ym = start; !ym.isAfter(end); ym = ym.plusMonths(1)) {
            months.add(ym);
        }
        int n = months.size();
        LocalDateTime rangeEnd = end.atEndOfMonth().atTime(23, 59, 59);

        // 1) San pham hien tai (danh muc + ton kho hien tai) - dung lam fallback
        //    cho SP chua tung co giao dich, va de biet danh muc cua tung ProductID.
        Map<Integer, String> categoryByProduct = new LinkedHashMap<>();
        Map<Integer, Integer> currentStockByProduct = new LinkedHashMap<>();
        String productSql = "SELECT p.ProductID, c.CategoryName, p.Stock "
                + "FROM Products p JOIN Categories c ON c.CategoryID = p.CategoryID";

        // 2) Lich su giao dich (ProductID, CreatedAt, StockAfter) tinh den cuoi khoang loc, sap theo thoi gian tang dan.
        Map<Integer, List<StockEvent>> eventsByProduct = new LinkedHashMap<>();
        String eventSql = "SELECT t.ProductID, t.CreatedAt, t.StockAfter "
                + "FROM InventoryTransactions t "
                + "WHERE t.CreatedAt <= ? "
                + "ORDER BY t.ProductID ASC, t.CreatedAt ASC, t.TransactionID ASC";

        try (Connection con = getConnection()) {
            try (PreparedStatement ps = con.prepareStatement(productSql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int productId = rs.getInt("ProductID");
                    categoryByProduct.put(productId, rs.getString("CategoryName"));
                    currentStockByProduct.put(productId, rs.getInt("Stock"));
                }
            }
            try (PreparedStatement ps = con.prepareStatement(eventSql)) {
                ps.setTimestamp(1, Timestamp.valueOf(rangeEnd));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int productId = rs.getInt("ProductID");
                        LocalDateTime at = rs.getTimestamp("CreatedAt").toLocalDateTime();
                        int stockAfter = rs.getInt("StockAfter");
                        eventsByProduct.computeIfAbsent(productId, k -> new ArrayList<>())
                                .add(new StockEvent(at, stockAfter));
                    }
                }
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "InventoryReportDAO.getMonthlyCategoryStockTrend - from=" + from + " to=" + to, e);
        }

        // 3) Voi tung san pham, tinh ton kho CUOI MOI THANG (snapshot) roi cong don theo danh muc.
        Map<String, long[]> qtyByCategory = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> entry : categoryByProduct.entrySet()) {
            int productId = entry.getKey();
            String categoryName = entry.getValue();
            List<StockEvent> events = eventsByProduct.get(productId);

            long[] monthlyStock = new long[n];
            if (events == null || events.isEmpty()) {
                // Chua tung co giao dich -> gia dinh giu nguyen ton kho hien tai (xem javadoc).
                int fallback = currentStockByProduct.getOrDefault(productId, 0);
                java.util.Arrays.fill(monthlyStock, fallback);
            } else {
                int pointer = 0;
                int lastKnown = 0;
                boolean anySeen = false;
                for (int i = 0; i < n; i++) {
                    LocalDateTime monthEnd = months.get(i).atEndOfMonth().atTime(23, 59, 59);
                    while (pointer < events.size() && !events.get(pointer).at.isAfter(monthEnd)) {
                        lastKnown = events.get(pointer).stockAfter;
                        anySeen = true;
                        pointer++;
                    }
                    monthlyStock[i] = anySeen ? lastKnown : 0;
                }
            }

            long[] agg = qtyByCategory.computeIfAbsent(categoryName, k -> new long[n]);
            for (int i = 0; i < n; i++) agg[i] += monthlyStock[i];
        }

        List<CategorySeries> series = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : qtyByCategory.entrySet()) {
            long[] qtyArr = entry.getValue();
            List<Long> qtyList = new ArrayList<>(n);
            List<BigDecimal> valueList = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                qtyList.add(qtyArr[i]);
                valueList.add(BigDecimal.ZERO); // gia ban theo thoi gian khong duoc luu lai - khong tinh o day.
            }
            long lastMonthQty = n > 0 ? qtyArr[n - 1] : 0;
            series.add(new CategorySeries(entry.getKey(), qtyList, valueList, lastMonthQty));
        }
        // Sap xep giam dan theo ton kho CUOI KHOANG loc (danh muc ton nhieu nhat HIEN TAI len dau chu thich).
        series.sort((a, b) -> Long.compare(b.totalQuantity, a.totalQuantity));

        return new MonthlyCategoryTrend(months, series);
    }
}