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
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO CHI DOC + HUY (khong tao moi) cho hoa don ban hang (Invoices). Viec
 * tao hoa don that su dien ra o luong ban hang (gio hang/thanh toan) - hien
 * tai luong do van la mock (xem PaymentDialog), chua ghi xuong DB, nen DAO
 * nay chi phuc vu trang "Quan ly hoa don": tra cuu + huy hoa don trong ngay.
 * <p>
 * Huy hoa don CHI can UPDATE Status='CANCELLED' - toan bo nghiep vu (chi cho
 * huy trong cung ngay + ca dang mo, hoan lai dung tung lo da tru) da duoc
 * trigger trg_Invoices_CancelSameDayOnly xu ly duoi DB. Neu vi pham dieu
 * kien, trigger RAISERROR + ROLLBACK va thong diep loi (tieng Viet, da than
 * thien) duoc nem len qua SQLException.getMessage().
 */
public class InvoiceDAO extends BaseDAO<Invoice> {

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
                + "inv.PaymentMethod, inv.Status, inv.CancelReason, inv.CancelledAt, "
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
        invoice.setStatus(rs.getString("Status"));
        invoice.setCancelReason(rs.getString("CancelReason"));

        Timestamp cancelledAt = rs.getTimestamp("CancelledAt");
        invoice.setCancelledAt(cancelledAt != null ? cancelledAt.toLocalDateTime() : null);

        invoice.setItemCount(rs.getInt("ItemCount"));
        return invoice;
    }

    /** Danh sach dong san pham (InvoiceDetails) cua 1 hoa don, dung khi xem chi tiet. */
    public List<InvoiceDetail> getDetails(int invoiceId) {
        String sql = "SELECT d.InvoiceDetailID, d.InvoiceID, d.ProductID, p.ProductName, p.ProductCode, "
                + "d.Quantity, d.UnitPrice, d.LineTotal "
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