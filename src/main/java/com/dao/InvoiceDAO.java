package com.dao;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.event.AppEventBus;
import com.event.DataChangedEvent;
import com.model.Invoice;
import com.model.InvoiceDetail;
import com.utils.DBConnection;
import com.utils.PaginationHelper;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;


public class InvoiceDAO extends BaseDAO<Invoice> {

    private final StoreConfigDAO storeConfigDAO = new StoreConfigDAO();

    private static final String BASE_TABLE =
            "Invoices inv "
                    + "JOIN Users u ON inv.CreatedBy = u.UserID "
                    + "LEFT JOIN Customers c ON inv.CustomerID = c.CustomerID "
                    + "LEFT JOIN Users cu ON c.CustomerID = cu.UserID";

    @Override
    protected Connection getConnection() throws SQLException {
        return DBConnection.getConnection();
    }

    @Override
    protected String getTableName() {
        return BASE_TABLE;
    }

    @Override
    protected String getJoinClause() {
        return null;
    }

    @Override
    protected String getColumns() {
        return "inv.InvoiceID, inv.InvoiceCode, inv.CreatedBy, u.FullName AS CreatedByName, "
                + "inv.CustomerID, cu.FullName AS CustomerName, inv.CreatedAt, "
                + "inv.SubTotal, inv.DiscountAmount, inv.PromotionID, inv.PromotionCode, "
                + "inv.PointsUsed, inv.PointsDiscountAmount, "
                + "inv.VATRate, inv.VATAmount, inv.TotalAmount, "
                + "inv.PaymentMethod, inv.PayPalOrderID, inv.PayPalCaptureID, inv.Status, inv.CancelReason, inv.CancelledAt, "
                + "(SELECT COUNT(*) FROM InvoiceDetails d WHERE d.InvoiceID = inv.InvoiceID) AS ItemCount";
    }

    @Override
    protected String getOrderBy() {
        return "inv.CreatedAt DESC, inv.InvoiceID DESC";
    }

    @Override
    protected String[] getSearchableColumns() {
        return new String[]{"inv.InvoiceCode", "u.FullName", "cu.FullName"};
    }

    @Override
    protected Invoice mapResultSet(ResultSet rs) throws SQLException {
        Invoice invoice = new Invoice();
        invoice.setInvoiceId(rs.getInt("InvoiceID"));
        invoice.setInvoiceCode(rs.getString("InvoiceCode"));
        invoice.setCreatedBy(rs.getInt("CreatedBy"));
        invoice.setCreatedByName(rs.getString("CreatedByName"));

        int customerId = rs.getInt("CustomerID");
        invoice.setCustomerId(rs.wasNull() ? null : customerId);
        invoice.setCustomerName(rs.getString("CustomerName"));

        Timestamp createdAt = rs.getTimestamp("CreatedAt");
        invoice.setCreatedAt(createdAt != null ? createdAt.toLocalDateTime() : null);

        invoice.setSubTotal(rs.getBigDecimal("SubTotal"));
        BigDecimal discount = rs.getBigDecimal("DiscountAmount");
        invoice.setDiscountAmount(discount != null ? discount : BigDecimal.ZERO);
        int promoId = rs.getInt("PromotionID");
        invoice.setPromotionId(rs.wasNull() ? null : promoId);
        invoice.setPromotionCode(rs.getString("PromotionCode"));
        try {
            invoice.setPointsUsed(rs.getInt("PointsUsed"));
            BigDecimal pd = rs.getBigDecimal("PointsDiscountAmount");
            invoice.setPointsDiscountAmount(pd != null ? pd : BigDecimal.ZERO);
        } catch (SQLException ignore) {
            // DB chua migration diem
        }
        invoice.setVatRate(rs.getBigDecimal("VATRate"));
        invoice.setVatAmount(rs.getBigDecimal("VATAmount"));
        invoice.setTotalAmount(rs.getBigDecimal("TotalAmount"));
        invoice.setPaymentMethod(rs.getString("PaymentMethod"));
        invoice.setPayPalOrderId(rs.getString("PayPalOrderID"));
        invoice.setPayPalCaptureId(rs.getString("PayPalCaptureID"));
        invoice.setStatus(rs.getString("Status"));
        invoice.setCancelReason(rs.getString("CancelReason"));

        Timestamp cancelledAt = rs.getTimestamp("CancelledAt");
        invoice.setCancelledAt(cancelledAt != null ? cancelledAt.toLocalDateTime() : null);

        invoice.setItemCount(rs.getInt("ItemCount"));
        return invoice;
    }

    /**
     * Tim kiem + loc hoa don theo tu khoa (ma HD/nguoi tao/khach hang) va/hoac
     * khoang ngay tao (ca 2 dau co the null neu khong loc).
     */
    public PaginationHelper.PaginationResult<Invoice> getPagedFiltered(
            int page, int pageSize, String keyword, LocalDate fromDate, LocalDate toDate) {

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
        if (fromDate != null) {
            conditions.add("inv.CreatedAt >= ?");
            params.add(Timestamp.valueOf(fromDate.atStartOfDay()));
        }
        if (toDate != null) {
            conditions.add("inv.CreatedAt < ?");
            params.add(Timestamp.valueOf(toDate.plusDays(1).atStartOfDay()));
        }

        String whereClause = conditions.isEmpty() ? null : String.join(" AND ", conditions);
        return getPaged(page, pageSize, whereClause, params.toArray());
    }

    private String escapeLike(String raw) {
        return raw.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
                .replace("[", "\\[");
    }

    /**
     * Lap hoa don POS: KM + doi diem + tich diem + UsedCount KM trong 1 transaction.
     */
    public boolean createInvoice(Invoice invoice, List<InvoiceDetail> items) {
        if (items == null || items.isEmpty()) return false;

        String insertInvoiceSql = "INSERT INTO Invoices "
                + "(InvoiceCode, ShiftID, CreatedBy, CustomerID, PaymentMethod, PayPalOrderID, PayPalCaptureID, "
                + "VATRate, SubTotal, TotalAmount, DiscountAmount, PromotionID, PromotionCode, "
                + "PointsUsed, PointsDiscountAmount) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, 0, ?, ?, ?, ?, ?)";
        String insertDetailSql = "INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice) VALUES (?, ?, ?, ?)";
        String sumLineTotalSql = "SELECT ISNULL(SUM(LineTotal), 0) FROM InvoiceDetails WHERE InvoiceID = ?";
        String updateTotalsSql = "UPDATE Invoices SET InvoiceCode = ?, SubTotal = ?, "
                + "TotalAmount = ?, DiscountAmount = ?, PointsUsed = ?, PointsDiscountAmount = ? WHERE InvoiceID = ?";

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                int invoiceId;
                BigDecimal requestedDiscount = invoice.getDiscountAmount() != null
                        ? invoice.getDiscountAmount() : BigDecimal.ZERO;
                if (requestedDiscount.signum() < 0) requestedDiscount = BigDecimal.ZERO;

                try (PreparedStatement ps = con.prepareStatement(insertInvoiceSql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, "TMP-" + System.nanoTime());
                    ps.setInt(2, invoice.getShiftId());
                    ps.setInt(3, invoice.getCreatedBy());
                    if (invoice.getCustomerId() != null) {
                        ps.setInt(4, invoice.getCustomerId());
                    } else {
                        ps.setNull(4, Types.INTEGER);
                    }
                    ps.setString(5, invoice.getPaymentMethod());
                    if (invoice.getPayPalOrderId() != null) {
                        ps.setString(6, invoice.getPayPalOrderId());
                    } else {
                        ps.setNull(6, Types.VARCHAR);
                    }
                    if (invoice.getPayPalCaptureId() != null) {
                        ps.setString(7, invoice.getPayPalCaptureId());
                    } else {
                        ps.setNull(7, Types.VARCHAR);
                    }
                    ps.setBigDecimal(8, invoice.getVatRate());
                    ps.setBigDecimal(9, requestedDiscount);
                    if (invoice.getPromotionId() != null) {
                        ps.setInt(10, invoice.getPromotionId());
                    } else {
                        ps.setNull(10, Types.INTEGER);
                    }
                    if (invoice.getPromotionCode() != null) {
                        ps.setString(11, invoice.getPromotionCode());
                    } else {
                        ps.setNull(11, Types.VARCHAR);
                    }
                    ps.setInt(12, Math.max(0, invoice.getPointsUsed()));
                    BigDecimal ptsDisc = invoice.getPointsDiscountAmount() != null
                            ? invoice.getPointsDiscountAmount() : BigDecimal.ZERO;
                    if (ptsDisc.signum() < 0) ptsDisc = BigDecimal.ZERO;
                    ps.setBigDecimal(13, ptsDisc);
                    ps.executeUpdate();

                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Khong lay duoc InvoiceID vua tao.");
                        invoiceId = keys.getInt(1);
                    }
                }

                try (PreparedStatement ps = con.prepareStatement(insertDetailSql)) {
                    for (InvoiceDetail item : items) {
                        ps.setInt(1, invoiceId);
                        ps.setInt(2, item.getProductId());
                        ps.setInt(3, item.getQuantity());
                        ps.setBigDecimal(4, item.getUnitPrice());
                        ps.executeUpdate();
                    }
                }

                BigDecimal subTotal;
                try (PreparedStatement ps = con.prepareStatement(sumLineTotalSql)) {
                    ps.setInt(1, invoiceId);
                    try (ResultSet rs = ps.executeQuery()) {
                        subTotal = rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
                    }
                }
                if (subTotal == null || subTotal.signum() == 0) {
                    con.rollback();
                    return false;
                }

                BigDecimal discount = requestedDiscount.min(subTotal);
                BigDecimal taxable = subTotal.subtract(discount);
                BigDecimal vatRate = invoice.getVatRate() != null ? invoice.getVatRate() : BigDecimal.ZERO;
                BigDecimal totalBeforePoints = taxable.add(taxable.multiply(vatRate)
                        .divide(new BigDecimal(100), 0, java.math.RoundingMode.HALF_UP));

                // Doi diem thanh vien
                int pointsUsed = Math.max(0, invoice.getPointsUsed());
                BigDecimal pointsDiscount = BigDecimal.ZERO;
                if (invoice.getCustomerId() != null && pointsUsed > 0) {
                    BigDecimal redeemRate = storeConfigDAO.getPointRedeemRate();
                    int available = 0;
                    try (PreparedStatement ps = con.prepareStatement(
                            "SELECT MemberPoint FROM Customers WITH (UPDLOCK, ROWLOCK) WHERE CustomerID = ?")) {
                        ps.setInt(1, invoice.getCustomerId());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) available = Math.max(0, rs.getInt(1));
                        }
                    }
                    pointsUsed = Math.min(pointsUsed, available);
                    pointsDiscount = redeemRate.multiply(BigDecimal.valueOf(pointsUsed))
                            .setScale(0, java.math.RoundingMode.DOWN);
                    if (pointsDiscount.compareTo(totalBeforePoints) > 0) {
                        pointsDiscount = totalBeforePoints;
                        if (redeemRate.signum() > 0) {
                            pointsUsed = pointsDiscount.divide(redeemRate, 0, java.math.RoundingMode.DOWN).intValue();
                            pointsDiscount = redeemRate.multiply(BigDecimal.valueOf(pointsUsed))
                                    .setScale(0, java.math.RoundingMode.DOWN);
                        } else {
                            pointsUsed = 0;
                            pointsDiscount = BigDecimal.ZERO;
                        }
                    }
                    if (pointsUsed > 0) {
                        try (PreparedStatement ps = con.prepareStatement(
                                "UPDATE Customers SET MemberPoint = MemberPoint - ? "
                                        + "WHERE CustomerID = ? AND MemberPoint >= ?")) {
                            ps.setInt(1, pointsUsed);
                            ps.setInt(2, invoice.getCustomerId());
                            ps.setInt(3, pointsUsed);
                            int updated = ps.executeUpdate();
                            if (updated != 1) {
                                pointsUsed = 0;
                                pointsDiscount = BigDecimal.ZERO;
                            }
                        }
                    }
                } else {
                    pointsUsed = 0;
                    pointsDiscount = BigDecimal.ZERO;
                }

                BigDecimal totalAmount = totalBeforePoints.subtract(pointsDiscount);
                if (totalAmount.signum() < 0) totalAmount = BigDecimal.ZERO;

                String invoiceCode = "HD-" + java.time.LocalDate.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
                        + "-" + String.format("%04d", invoiceId);

                try (PreparedStatement ps = con.prepareStatement(updateTotalsSql)) {
                    ps.setString(1, invoiceCode);
                    ps.setBigDecimal(2, subTotal);
                    ps.setBigDecimal(3, totalAmount);
                    ps.setBigDecimal(4, discount);
                    ps.setInt(5, pointsUsed);
                    ps.setBigDecimal(6, pointsDiscount);
                    ps.setInt(7, invoiceId);
                    ps.executeUpdate();
                }

                // Tich diem tren so tien thuc tra
                int pointsEarned = 0;
                if (invoice.getCustomerId() != null && totalAmount.signum() > 0) {
                    BigDecimal pointRate = storeConfigDAO.getPointRate();
                    pointsEarned = totalAmount.divide(pointRate, 0, java.math.RoundingMode.DOWN).intValueExact();
                    if (pointsEarned > 0) {
                        String addPointSql = "UPDATE Customers SET MemberPoint = MemberPoint + ? WHERE CustomerID = ?";
                        try (PreparedStatement ps = con.prepareStatement(addPointSql)) {
                            ps.setInt(1, pointsEarned);
                            ps.setInt(2, invoice.getCustomerId());
                            ps.executeUpdate();
                        }
                    }
                }

                if (invoice.getPromotionId() != null && discount.signum() > 0) {
                    try (PreparedStatement ps = con.prepareStatement(
                            "UPDATE Promotions SET UsedCount = UsedCount + 1 WHERE PromotionID = ?")) {
                        ps.setInt(1, invoice.getPromotionId());
                        ps.executeUpdate();
                    }
                }

                con.commit();
                invoice.setInvoiceId(invoiceId);
                invoice.setInvoiceCode(invoiceCode);
                invoice.setSubTotal(subTotal);
                invoice.setDiscountAmount(discount);
                invoice.setTotalAmount(totalAmount);
                invoice.setPointsUsed(pointsUsed);
                invoice.setPointsDiscountAmount(pointsDiscount);
                invoice.setPointsEarned(pointsEarned);
                AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.INVOICE));
                return true;
            } catch (SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.INVOICE_CREATE_FAIL,
                    "InvoiceDAO.createInvoice - createdBy=" + invoice.getCreatedBy(), e);
            return false;
        }
    }

    public List<InvoiceDetail> getDetails(int invoiceId) {
        String sql = "SELECT d.InvoiceDetailID, d.InvoiceID, d.ProductID, p.ProductName, p.ProductCode, "
                + "p.ImageUrl, d.Quantity, d.UnitPrice, d.LineTotal "
                + "FROM InvoiceDetails d "
                + "JOIN Products p ON p.ProductID = d.ProductID "
                + "WHERE d.InvoiceID = ? "
                + "ORDER BY d.InvoiceDetailID ASC";

        List<InvoiceDetail> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, invoiceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    InvoiceDetail detail = new InvoiceDetail();
                    detail.setInvoiceDetailId(rs.getInt("InvoiceDetailID"));
                    detail.setInvoiceId(rs.getInt("InvoiceID"));
                    detail.setProductId(rs.getInt("ProductID"));
                    detail.setProductName(rs.getString("ProductName"));
                    detail.setProductCode(rs.getString("ProductCode"));
                    detail.setProductImageUrl(rs.getString("ImageUrl"));
                    detail.setQuantity(rs.getInt("Quantity"));
                    detail.setUnitPrice(rs.getBigDecimal("UnitPrice"));
                    detail.setLineTotal(rs.getBigDecimal("LineTotal"));
                    list.add(detail);
                }
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "InvoiceDAO.getDetails - invoiceId=" + invoiceId, e);
        }
        return list;
    }

    public String cancelInvoice(int invoiceId, String reason) {
        String sql = "UPDATE Invoices SET Status = 'CANCELLED', CancelReason = ?, CancelledAt = GETDATE() "
                + "WHERE InvoiceID = ? AND Status = 'ACTIVE'";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            if (reason == null || reason.isBlank()) {
                ps.setNull(1, Types.NVARCHAR);
            } else {
                ps.setString(1, reason.trim());
            }
            ps.setInt(2, invoiceId);

            int affected = ps.executeUpdate();
            if (affected == 0) {
                return "Hóa đơn đã được hủy trước đó hoặc không còn tồn tại.";
            }

            AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.INVOICE));
            return null;
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL,
                    "InvoiceDAO.cancelInvoice - invoiceId=" + invoiceId, e);
            return e.getMessage();
        }
    }

    public BigDecimal sumTodayRevenue() {
        String sql = "SELECT ISNULL(SUM(TotalAmount), 0) FROM Invoices "
                + "WHERE Status = 'ACTIVE' AND CAST(CreatedAt AS DATE) = CAST(GETDATE() AS DATE)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "InvoiceDAO.sumTodayRevenue", e);
            return BigDecimal.ZERO;
        }
    }
}