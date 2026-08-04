package com.dao;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.event.AppEventBus;
import com.event.DataChangedEvent;
import com.model.Invoice;
import com.model.InvoiceDetail;
import com.utils.DBConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
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
                + "inv.SubTotal, inv.VATRate, inv.VATAmount, inv.TotalAmount, "
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
     * Lap 1 hoa don ban hang THAT SU (dung cho trang POS - ban hang tai
     * quay). Day la nguoc lai voi javadoc cu o dau class: DAO nay tu do
     * khong con "chi doc + huy" nua.
     * <p>
     * Trinh tu bat buoc (dung 1 transaction, dung y trigger duoi DB):
     * <ol>
     *   <li>INSERT Invoices voi InvoiceCode tam (se cap nhat lai ngay sau
     *   khi co InvoiceID, vi cot nay KHONG phai computed column nhu
     *   ProductCode/OrderCode).</li>
     *   <li>INSERT tung dong InvoiceDetails - trigger INSTEAD OF INSERT
     *   trg_InvoiceDetails_CheckStock se tu tru kho theo FEFO, co the CAT
     *   BOT so luong neu vuot ton kho con lai (xem javadoc trigger), va tu
     *   chan hoan toan neu san pham da het hang.</li>
     *   <li>Doc lai LineTotal thuc te (sau khi trigger co the da cat bot so
     *   luong) de tinh dung SubTotal/TotalAmount, roi UPDATE lai Invoices -
     *   2 cot nay KHONG tu tinh, "duy tri qua trigger/app" (xem SIMS.sql).</li>
     *   <li>Neu hoa don co gan khach hang (co tai khoan): cong diem thanh
     *   vien theo StoreConfig.POINT_RATE, TRONG CUNG transaction nay.</li>
     * </ol>
     * Tra ve true + gan lai invoiceId/invoiceCode/subTotal/totalAmount/pointsEarned
     * vao {@code invoice} neu thanh cong; false neu that bai (het hang, loi
     * DB...) - chi tiet loi da duoc log qua AppLogger.
     */
    public boolean createInvoice(Invoice invoice, List<InvoiceDetail> items) {
        if (items == null || items.isEmpty()) return false;

        String insertInvoiceSql = "INSERT INTO Invoices "
                + "(InvoiceCode, ShiftID, CreatedBy, CustomerID, PaymentMethod, PayPalOrderID, PayPalCaptureID, VATRate, SubTotal, TotalAmount) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, 0)";
        String insertDetailSql = "INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice) VALUES (?, ?, ?, ?)";
        String sumLineTotalSql = "SELECT ISNULL(SUM(LineTotal), 0) FROM InvoiceDetails WHERE InvoiceID = ?";
        String updateTotalsSql = "UPDATE Invoices SET InvoiceCode = ?, SubTotal = ?, "
                + "TotalAmount = ? WHERE InvoiceID = ?";

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                int invoiceId;
                try (PreparedStatement ps = con.prepareStatement(insertInvoiceSql, Statement.RETURN_GENERATED_KEYS)) {
                    // Ma tam thoi duy nhat (chi ton tai trong pham vi transaction nay,
                    // se bi ghi de ngay ben duoi) - tranh vi pham UNIQUE(InvoiceCode).
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
                        ps.executeUpdate(); // tung dong 1 - de trigger (INSTEAD OF) xu ly dung tung san pham
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
                    // Khong co dong nao duoc tao that su (vd tat ca san pham da het
                    // hang khi trigger chay) - huy toan bo, khong lap hoa don rong.
                    con.rollback();
                    return false;
                }

                BigDecimal vatRate = invoice.getVatRate() != null ? invoice.getVatRate() : BigDecimal.ZERO;
                BigDecimal totalAmount = subTotal.add(subTotal.multiply(vatRate)
                        .divide(new BigDecimal(100)));
                String invoiceCode = "HD-" + java.time.LocalDate.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
                        + "-" + String.format("%04d", invoiceId);

                try (PreparedStatement ps = con.prepareStatement(updateTotalsSql)) {
                    ps.setString(1, invoiceCode);
                    ps.setBigDecimal(2, subTotal);
                    ps.setBigDecimal(3, totalAmount);
                    ps.setInt(4, invoiceId);
                    ps.executeUpdate();
                }

                // Cong diem thanh vien - CHI ap dung cho hoa don co khach hang (co
                // tai khoan), khach le (CustomerID null) khong tich diem. Tinh tren
                // totalAmount THAT SU (sau khi trigger co the da cat bot so luong o
                // tren, xem sumLineTotalSql) - dam bao khong bao gio cong "khong" so
                // voi so tien khach thuc tra. Lam trong CUNG transaction voi hoa don
                // de khong bao gio lech du lieu (hoa don thanh cong nhung diem thi
                // khong, hoac nguoc lai).
                int pointsEarned = 0;
                if (invoice.getCustomerId() != null) {
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

                con.commit();
                invoice.setInvoiceId(invoiceId);
                invoice.setInvoiceCode(invoiceCode);
                invoice.setSubTotal(subTotal);
                invoice.setTotalAmount(totalAmount);
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

    /** Danh sach dong san pham (InvoiceDetails) cua 1 hoa don, dung khi xem chi tiet. */
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

    /**
     * Huy 1 hoa don dang ACTIVE. Tra ve null neu huy thanh cong, hoac thong
     * diep loi (da la tieng Viet, hien thang len UI duoc) neu that bai -
     * hoac do trigger tu choi (khac ngay/ca da dong) hoac do hoa don khong
     * con o trang thai ACTIVE nua (da bi huy truoc do / khong ton tai).
     */
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
            // RAISERROR tu trigger trg_Invoices_CancelSameDayOnly (vd "Chi duoc
            // huy hoa don trong cung ca ban hang dang mo va trong ngay tao.")
            // roi len toi day dung nguyen van trong e.getMessage().
            AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL,
                    "InvoiceDAO.cancelInvoice - invoiceId=" + invoiceId, e);
            return e.getMessage();
        }
    }

    /** Tong doanh thu hoa don ACTIVE trong ngay hom nay (dung cho Dashboard neu can). */
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