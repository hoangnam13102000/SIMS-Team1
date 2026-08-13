package com.dao;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.event.AppEventBus;
import com.event.DataChangedEvent;
import com.model.ReturnExchange;
import com.model.ReturnExchangeDetail;
import com.utils.DBConnection;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DAO đổi/trả hàng. TotalValue = số tiền hoàn thực tế sau khi phân bổ KM + điểm
 * theo tỷ lệ giá trị hàng trả / SubTotal hóa đơn. Khi duyệt: hoàn điểm đã dùng
 * và thu hồi điểm đã tích theo cùng tỷ lệ (không trùng với lần trả trước).
 */
public class ReturnExchangeDAO extends BaseDAO<ReturnExchange> {

    public static final BigDecimal APPROVAL_THRESHOLD = new BigDecimal("0");

    private final StoreConfigDAO storeConfigDAO = new StoreConfigDAO();

    private static final String BASE_TABLE =
            "ReturnExchanges r "
                    + "JOIN Invoices inv ON r.InvoiceID = inv.InvoiceID "
                    + "JOIN Users u ON r.CreatedBy = u.UserID "
                    + "LEFT JOIN Users au ON r.ApprovedBy = au.UserID";

    @Override
    protected Connection getConnection() throws SQLException {
        return DBConnection.getConnection();
    }

    @Override
    protected String getTableName() { return BASE_TABLE; }

    @Override
    protected String getJoinClause() { return null; }

    @Override
    protected String getColumns() {
        return "r.ReturnID, r.InvoiceID, inv.InvoiceCode, r.Type, r.Reason, r.RejectionReason, r.TotalValue, "
                + "r.DiscountShare, r.PointsShare, "
                + "r.RequiresApproval, r.Status, r.ApprovedBy, au.FullName AS ApprovedByName, r.ApprovedAt, "
                + "r.CreatedBy, u.FullName AS CreatedByName, r.CreatedAt";
    }

    @Override
    protected String getOrderBy() { return "r.CreatedAt DESC, r.ReturnID DESC"; }

    @Override
    protected String[] getSearchableColumns() {
        return new String[]{"inv.InvoiceCode", "u.FullName"};
    }

    @Override
    protected ReturnExchange mapResultSet(ResultSet rs) throws SQLException {
        ReturnExchange re = new ReturnExchange();
        re.setReturnId(rs.getInt("ReturnID"));
        re.setInvoiceId(rs.getInt("InvoiceID"));
        re.setInvoiceCode(rs.getString("InvoiceCode"));
        re.setType(rs.getString("Type"));
        re.setReason(rs.getString("Reason"));
        re.setRejectionReason(rs.getString("RejectionReason"));
        re.setTotalValue(rs.getBigDecimal("TotalValue"));
        re.setDiscountShare(nvl(rs.getBigDecimal("DiscountShare")));
        re.setPointsShare(nvl(rs.getBigDecimal("PointsShare")));
        re.setRequiresApproval(rs.getBoolean("RequiresApproval"));
        re.setStatus(rs.getString("Status"));
        int approvedBy = rs.getInt("ApprovedBy");
        re.setApprovedBy(rs.wasNull() ? null : approvedBy);
        re.setApprovedByName(rs.getString("ApprovedByName"));
        Timestamp approvedAt = rs.getTimestamp("ApprovedAt");
        re.setApprovedAt(approvedAt != null ? approvedAt.toLocalDateTime() : null);
        re.setCreatedBy(rs.getInt("CreatedBy"));
        re.setCreatedByName(rs.getString("CreatedByName"));
        Timestamp createdAt = rs.getTimestamp("CreatedAt");
        re.setCreatedAt(createdAt != null ? createdAt.toLocalDateTime() : null);
        return re;
    }

    public List<ReturnExchangeDetail> getDetails(int returnId) {
        String sql = "SELECT d.ReturnDetailID, d.ReturnID, d.ProductID, p.ProductName, p.ProductCode, "
                + "d.Quantity, d.Direction, d.UnitPrice "
                + "FROM ReturnExchangeDetails d "
                + "JOIN Products p ON p.ProductID = d.ProductID "
                + "WHERE d.ReturnID = ? ORDER BY d.ReturnDetailID";
        List<ReturnExchangeDetail> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, returnId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ReturnExchangeDetail d = new ReturnExchangeDetail();
                    d.setReturnDetailId(rs.getInt("ReturnDetailID"));
                    d.setReturnId(rs.getInt("ReturnID"));
                    d.setProductId(rs.getInt("ProductID"));
                    d.setProductName(rs.getString("ProductName"));
                    d.setProductCode(rs.getString("ProductCode"));
                    d.setQuantity(rs.getInt("Quantity"));
                    d.setDirection(rs.getString("Direction"));
                    d.setUnitPrice(rs.getBigDecimal("UnitPrice"));
                    list.add(d);
                }
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "ReturnExchangeDAO.getDetails - returnId=" + returnId, e);
        }
        return list;
    }

    /** SL đã bán còn có thể trả = sold - already returned (APPROVED, Direction=IN). */
    public Map<Integer, Integer> getReturnableQuantities(int invoiceId) {
        String soldSql = "SELECT ProductID, SUM(Quantity) AS Qty FROM InvoiceDetails WHERE InvoiceID = ? GROUP BY ProductID";
        String returnedSql = "SELECT d.ProductID, SUM(d.Quantity) AS Qty "
                + "FROM ReturnExchangeDetails d "
                + "JOIN ReturnExchanges r ON r.ReturnID = d.ReturnID "
                + "WHERE r.InvoiceID = ? AND r.Status = 'APPROVED' AND d.Direction = 'IN' "
                + "GROUP BY d.ProductID";
        Map<Integer, Integer> sold = new HashMap<>();
        try (Connection con = DBConnection.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement(soldSql)) {
                ps.setInt(1, invoiceId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) sold.put(rs.getInt(1), rs.getInt(2));
                }
            }
            try (PreparedStatement ps = con.prepareStatement(returnedSql)) {
                ps.setInt(1, invoiceId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int pid = rs.getInt(1);
                        int ret = rs.getInt(2);
                        sold.put(pid, Math.max(0, sold.getOrDefault(pid, 0) - ret));
                    }
                }
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "ReturnExchangeDAO.getReturnableQuantities - invoiceId=" + invoiceId, e);
        }
        return sold;
    }

    public String createReturnExchange(ReturnExchange header, List<ReturnExchangeDetail> details) {
        if (details == null || details.isEmpty()) {
            return "Chưa chọn sản phẩm nào để đổi/trả.";
        }
        if (header.getReason() == null || header.getReason().isBlank()) {
            return "Vui lòng nhập lý do đổi/trả (bắt buộc theo quy định).";
        }

        String insertHeaderSql = "INSERT INTO ReturnExchanges "
                + "(InvoiceID, Type, Reason, TotalValue, DiscountShare, PointsShare, RequiresApproval, Status, CreatedBy) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING', ?)";
        String insertDetailSql = "INSERT INTO ReturnExchangeDetails "
                + "(ReturnID, ProductID, Quantity, Direction, UnitPrice) VALUES (?, ?, ?, ?, ?)";
        String approveSql = "UPDATE ReturnExchanges SET Status = 'APPROVED', ApprovedBy = ?, ApprovedAt = GETDATE() "
                + "WHERE ReturnID = ?";

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                String invStatus;
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT Status FROM Invoices WITH (UPDLOCK, ROWLOCK) WHERE InvoiceID = ?")) {
                    ps.setInt(1, header.getInvoiceId());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            con.rollback();
                            return "Không tìm thấy hóa đơn.";
                        }
                        invStatus = rs.getString("Status");
                    }
                }
                if (!"ACTIVE".equalsIgnoreCase(invStatus)) {
                    con.rollback();
                    return "Hóa đơn đã bị hủy, không thể đổi/trả hàng.";
                }

                Map<Integer, Integer> returnable = getReturnableQuantities(header.getInvoiceId());
                Map<Integer, Integer> returnRequestedSoFar = new HashMap<>();

                for (ReturnExchangeDetail d : details) {
                    if (d.getQuantity() <= 0) {
                        con.rollback();
                        return "Số lượng phải lớn hơn 0.";
                    }
                    if (d.isIn()) {
                        int already = returnRequestedSoFar.getOrDefault(d.getProductId(), 0);
                        int limit = returnable.getOrDefault(d.getProductId(), 0);
                        if (already + d.getQuantity() > limit) {
                            con.rollback();
                            return "Sản phẩm \"" + d.getProductName() + "\" chỉ còn có thể trả tối đa "
                                    + (limit - already) + " (đã bán trừ đã đổi/trả trước đó).";
                        }
                        returnRequestedSoFar.put(d.getProductId(), already + d.getQuantity());
                    } else if (d.isOut()) {
                        try (PreparedStatement ps = con.prepareStatement(
                                "SELECT Stock, ProductName FROM Products WITH (UPDLOCK, ROWLOCK) WHERE ProductID = ?")) {
                            ps.setInt(1, d.getProductId());
                            try (ResultSet rs = ps.executeQuery()) {
                                if (!rs.next()) {
                                    con.rollback();
                                    return "Không tìm thấy sản phẩm đổi.";
                                }
                                int stock = rs.getInt("Stock");
                                if (d.getQuantity() > stock) {
                                    con.rollback();
                                    return "Sản phẩm \"" + rs.getString("ProductName")
                                            + "\" không đủ tồn kho để đổi (còn " + stock + ").";
                                }
                            }
                        }
                    }
                }

                // Tổng giá gốc hàng trả (IN)
                BigDecimal returnedGross = BigDecimal.ZERO;
                for (ReturnExchangeDetail d : details) {
                    if (d.isIn()) returnedGross = returnedGross.add(d.getLineTotal());
                }

                // Phân bổ KM + điểm → tiền hoàn thực tế
                RefundBreakdown breakdown = computeRefundAmount(con, header.getInvoiceId(), returnedGross);
                BigDecimal totalValue = breakdown.refund;
                boolean requiresApproval = totalValue.compareTo(APPROVAL_THRESHOLD) > 0;

                int returnId;
                try (PreparedStatement ps = con.prepareStatement(insertHeaderSql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, header.getInvoiceId());
                    ps.setString(2, header.getType());
                    ps.setString(3, header.getReason().trim());
                    ps.setBigDecimal(4, totalValue);
                    ps.setBigDecimal(5, breakdown.discountShare);
                    ps.setBigDecimal(6, breakdown.pointsShare);
                    ps.setBoolean(7, requiresApproval);
                    ps.setInt(8, header.getCreatedBy());
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Không lấy được ReturnID vừa tạo.");
                        returnId = keys.getInt(1);
                    }
                }

                try (PreparedStatement ps = con.prepareStatement(insertDetailSql)) {
                    for (ReturnExchangeDetail d : details) {
                        ps.setInt(1, returnId);
                        ps.setInt(2, d.getProductId());
                        ps.setInt(3, d.getQuantity());
                        ps.setString(4, d.getDirection());
                        ps.setBigDecimal(5, d.getUnitPrice());
                        ps.executeUpdate();
                    }
                }

                if (!requiresApproval) {
                    // Tính và hoàn/thu hồi điểm TRƯỚC khi trigger cập nhật hóa đơn.
                    // Làm vậy vẫn giữ được dữ liệu gốc ngay cả khi trả 100% làm SubTotal = 0.
                    adjustPointsForApprovedReturn(con, header.getInvoiceId(), returnId);
                    try (PreparedStatement ps = con.prepareStatement(approveSql)) {
                        ps.setInt(1, header.getCreatedBy());
                        ps.setInt(2, returnId);
                        ps.executeUpdate();
                    }
                }

                con.commit();
                header.setReturnId(returnId);
                header.setTotalValue(totalValue);
                header.setDiscountShare(breakdown.discountShare);
                header.setPointsShare(breakdown.pointsShare);
                header.setRequiresApproval(requiresApproval);
                header.setStatus(requiresApproval ? ReturnExchange.STATUS_PENDING : ReturnExchange.STATUS_APPROVED);

                AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.RETURN_EXCHANGE));
                AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.INVOICE));
                return null;
            } catch (SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.RETURN_CREATE_FAIL,
                    "ReturnExchangeDAO.createReturnExchange - invoiceId=" + header.getInvoiceId(), e);
            return e.getMessage() != null ? e.getMessage() : "Tạo yêu cầu đổi/trả thất bại.";
        }
    }

    public String approve(int returnId, int approverId) {
        String lockRequestSql = "SELECT Status, InvoiceID FROM ReturnExchanges WITH (UPDLOCK, ROWLOCK) WHERE ReturnID = ?";
        String outDetailSql = "SELECT d.Quantity, p.Stock, p.ProductName "
                + "FROM ReturnExchangeDetails d "
                + "JOIN Products p WITH (UPDLOCK, ROWLOCK) ON p.ProductID = d.ProductID "
                + "WHERE d.ReturnID = ? AND d.Direction = 'OUT'";
        String updateSql = "UPDATE ReturnExchanges SET Status = 'APPROVED', ApprovedBy = ?, ApprovedAt = GETDATE() "
                + "WHERE ReturnID = ? AND Status = 'PENDING'";

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                String status;
                int invoiceId;
                try (PreparedStatement ps = con.prepareStatement(lockRequestSql)) {
                    ps.setInt(1, returnId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            con.rollback();
                            return "Không tìm thấy yêu cầu đổi/trả.";
                        }
                        status = rs.getString("Status");
                        invoiceId = rs.getInt("InvoiceID");
                    }
                }
                if (!"PENDING".equals(status)) {
                    con.rollback();
                    return "Yêu cầu này không còn ở trạng thái chờ duyệt.";
                }

                try (PreparedStatement ps = con.prepareStatement(outDetailSql)) {
                    ps.setInt(1, returnId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            int qty = rs.getInt("Quantity");
                            int stock = rs.getInt("Stock");
                            if (qty > stock) {
                                con.rollback();
                                return "Sản phẩm \"" + rs.getString("ProductName")
                                        + "\" không đủ tồn kho để duyệt đổi (còn " + stock + ").";
                            }
                        }
                    }
                }

                // Tính và hoàn/thu hồi điểm TRƯỚC khi trigger cập nhật hóa đơn.
                // Sau UPDATE, trigger có thể đưa SubTotal/discount về 0 nên không
                // thể dùng các giá trị đó để khôi phục điểm gốc một cách an toàn.
                adjustPointsForApprovedReturn(con, invoiceId, returnId);

                try (PreparedStatement ps = con.prepareStatement(updateSql)) {
                    ps.setInt(1, approverId);
                    ps.setInt(2, returnId);
                    int affected = ps.executeUpdate();
                    if (affected == 0) {
                        con.rollback();
                        return "Yêu cầu này không còn ở trạng thái chờ duyệt.";
                    }
                }

                con.commit();
                AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.RETURN_EXCHANGE));
                AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.INVOICE));
                return null;
            } catch (SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.RETURN_STATUS_UPDATE_FAIL,
                    "ReturnExchangeDAO.approve - returnId=" + returnId, e);
            return e.getMessage() != null ? e.getMessage() : "Duyệt yêu cầu thất bại.";
        }
    }

    public String reject(int returnId, int approverId, String rejectionReason) {
        if (rejectionReason == null || rejectionReason.isBlank()) {
            return "Vui lòng nhập lý do từ chối.";
        }
        String sql = "UPDATE ReturnExchanges SET Status = 'REJECTED', RejectionReason = ?, ApprovedBy = ?, ApprovedAt = GETDATE() "
                + "WHERE ReturnID = ? AND Status = 'PENDING'";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, rejectionReason.trim());
            ps.setInt(2, approverId);
            ps.setInt(3, returnId);
            int affected = ps.executeUpdate();
            if (affected == 0) {
                return "Yêu cầu này không còn ở trạng thái chờ duyệt.";
            }
            AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.RETURN_EXCHANGE));
            return null;
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.RETURN_STATUS_UPDATE_FAIL,
                    "ReturnExchangeDAO.reject - returnId=" + returnId, e);
            return e.getMessage() != null ? e.getMessage() : "Từ chối yêu cầu thất bại.";
        }
    }

    // ------------------------------------------------------------------
    //  Phân bổ KM + điểm → tiền hoàn
    // ------------------------------------------------------------------

    /** Kết quả phân bổ tiền hoàn: tổng tiền hoàn + phần KM/điểm tương ứng (chỉ để hiển thị). */
    private static final class RefundBreakdown {
        final BigDecimal refund;
        final BigDecimal discountShare;
        final BigDecimal pointsShare;

        RefundBreakdown(BigDecimal refund, BigDecimal discountShare, BigDecimal pointsShare) {
            this.refund = refund;
            this.discountShare = discountShare;
            this.pointsShare = pointsShare;
        }
    }

    /**
     * Tiền hoàn thực tế cho phần hàng trả (returnedGross = Σ UnitPrice×Qty IN).
     * <p>
     * Phân bổ theo đúng tỷ lệ (gross hàng trả / SubTotal hóa đơn), áp trực
     * tiếp lên TotalAmount - tức số tiền KHÁCH THỰC SỰ ĐÃ TRẢ - thay vì dựng
     * lại công thức "taxable + VAT - điểm" từ đầu như trước.
     * <p>
     * BUG cũ: với đơn đặt online, Orders.TotalAmount = SubTotal -
     * DiscountAmount (KHÔNG cộng VAT - xem comment cột TotalAmount trong
     * bảng Orders), nhưng Invoices.VATRate vẫn được copy nguyên từ đơn hàng
     * khi lập hóa đơn (OrderDAO.createInvoiceForOrder) dù VAT đó chưa từng
     * được cộng vào số tiền khách trả. Công thức cũ cứ thấy VATRate > 0 là
     * cộng thêm VAT vào tiền hoàn, khiến "Giá trị hàng trả" hiển thị CAO
     * HƠN cả "Tổng tiền đơn hàng" đã thanh toán (ví dụ hoàn 176.256đ cho
     * đơn chỉ thu 163.200đ). Tính theo tỷ lệ trực tiếp trên TotalAmount đã
     * lưu đảm bảo tiền hoàn không bao giờ vượt quá số tiền đã thu, bất kể
     * hóa đơn có cộng VAT hay không.
     */
    private RefundBreakdown computeRefundAmount(Connection con, int invoiceId, BigDecimal returnedGross)
            throws SQLException {
        if (returnedGross == null || returnedGross.signum() <= 0) {
            return new RefundBreakdown(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        BigDecimal subTotal = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal pointsDisc = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;

        try (PreparedStatement ps = con.prepareStatement(
                "SELECT SubTotal, DiscountAmount, PointsDiscountAmount, TotalAmount "
                        + "FROM Invoices WHERE InvoiceID = ?")) {
            ps.setInt(1, invoiceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    subTotal = nvl(rs.getBigDecimal("SubTotal"));
                    discount = nvl(rs.getBigDecimal("DiscountAmount"));
                    pointsDisc = nvl(rs.getBigDecimal("PointsDiscountAmount"));
                    totalAmount = nvl(rs.getBigDecimal("TotalAmount"));
                }
            }
        }
        if (subTotal.signum() <= 0) {
            return new RefundBreakdown(returnedGross, BigDecimal.ZERO, BigDecimal.ZERO); // fallback
        }

        // SubTotal/DiscountAmount/TotalAmount da duoc trigger dieu chinh sau
        // cac lan doi/tra APPROVED truoc. Vi vay chi can gioi han theo phan
        // gia tri con lai hien tai; khong tru them alreadyReturned de tranh
        // tru 2 lan.
        BigDecimal gross = returnedGross.min(subTotal);
        BigDecimal ratio = gross.divide(subTotal, 8, RoundingMode.HALF_UP);

        BigDecimal discShare = discount.multiply(ratio).setScale(0, RoundingMode.HALF_UP);
        BigDecimal ptsShare = pointsDisc.multiply(ratio).setScale(0, RoundingMode.HALF_UP);

        BigDecimal refund = totalAmount.multiply(ratio).setScale(0, RoundingMode.HALF_UP);
        if (refund.signum() < 0) refund = BigDecimal.ZERO;
        // Chan cung: tien hoan khong bao gio duoc vuot qua so tien hoa don con lai.
        if (refund.compareTo(totalAmount) > 0) refund = totalAmount;

        return new RefundBreakdown(refund, discShare, ptsShare);
    }

    private BigDecimal sumApprovedReturnedGross(Connection con, int invoiceId) throws SQLException {
        String sql = "SELECT ISNULL(SUM(d.Quantity * d.UnitPrice), 0) "
                + "FROM ReturnExchangeDetails d "
                + "JOIN ReturnExchanges r ON r.ReturnID = d.ReturnID "
                + "WHERE r.InvoiceID = ? AND r.Status = 'APPROVED' AND d.Direction = 'IN'";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, invoiceId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? nvl(rs.getBigDecimal(1)) : BigDecimal.ZERO;
            }
        }
    }

    /**
     * Sau khi 1 phiếu đổi/trả chuyển APPROVED: chỉnh MemberPoint theo
     * chênh lệch tỷ lệ hàng đã trả (tránh hoàn trùng khi trả nhiều lần).
     * delta = (pointsUsed - pointsEarned) * (grossReturned / subTotal)  − alreadyApplied
     */
    private void adjustPointsForApprovedReturn(Connection con, int invoiceId, int returnId) throws SQLException {
        Integer customerId = null;
        int pointsUsed = 0;
        BigDecimal subTotal = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal pointsDiscount = BigDecimal.ZERO;
        BigDecimal vatRate = BigDecimal.ZERO;
        BigDecimal originalSubTotal = BigDecimal.ZERO;

        // Phuong thuc nay duoc goi TRUOC UPDATE Status='APPROVED', vi vay
        // cac gia tri invoice van la gia tri truoc phieu hien tai. Điều nay
        // tranh mat du lieu diem khi trigger da dua SubTotal ve 0 (tra 100%).
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT CustomerID, PointsUsed, SubTotal, DiscountAmount, PointsDiscountAmount, VATRate, "
                        + "ISNULL((SELECT SUM(id.Quantity * id.UnitPrice) FROM InvoiceDetails id WHERE id.InvoiceID = i.InvoiceID), 0) AS OriginalSubTotal "
                        + "FROM Invoices i WHERE i.InvoiceID = ?")) {
            ps.setInt(1, invoiceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return;
                int cid = rs.getInt("CustomerID");
                if (rs.wasNull()) return;
                customerId = cid;
                pointsUsed = Math.max(0, rs.getInt("PointsUsed"));
                subTotal = nvl(rs.getBigDecimal("SubTotal"));
                discount = nvl(rs.getBigDecimal("DiscountAmount"));
                pointsDiscount = nvl(rs.getBigDecimal("PointsDiscountAmount"));
                vatRate = nvl(rs.getBigDecimal("VATRate"));
                originalSubTotal = nvl(rs.getBigDecimal("OriginalSubTotal"));
            }
        }
        if (customerId == null || subTotal.signum() <= 0 || originalSubTotal.signum() <= 0) return;

        // Neu da co cac lan tra truoc, trigger da thu hep discount theo ty le
        // SubTotal con lai. Khoi phuc discount/points-discount goc de tinh
        // pointsEarned cua hoa don ban dau.
        BigDecimal restoreRatio = originalSubTotal.divide(subTotal, 8, RoundingMode.HALF_UP);
        if (restoreRatio.compareTo(BigDecimal.ONE) < 0) restoreRatio = BigDecimal.ONE;
        BigDecimal originalDiscount = discount.multiply(restoreRatio).setScale(0, RoundingMode.HALF_UP);
        BigDecimal originalPointsDiscount = pointsDiscount.multiply(restoreRatio).setScale(0, RoundingMode.HALF_UP);

        BigDecimal originalTaxable = originalSubTotal.subtract(originalDiscount);
        if (originalTaxable.signum() < 0) originalTaxable = BigDecimal.ZERO;
        BigDecimal originalVat = originalTaxable.multiply(vatRate)
                .divide(new BigDecimal("100"), 0, RoundingMode.HALF_UP);
        BigDecimal originalTotal = originalTaxable.add(originalVat).subtract(originalPointsDiscount);
        if (originalTotal.signum() < 0) originalTotal = BigDecimal.ZERO;

        int pointsEarned = 0;
        BigDecimal pointRate = storeConfigDAO.getPointRate();
        if (pointRate != null && pointRate.signum() > 0 && originalTotal.signum() > 0) {
            pointsEarned = originalTotal.divide(pointRate, 0, RoundingMode.DOWN).intValue();
        }

        int netFull = pointsUsed - pointsEarned;

        // Gross da tra truoc + gross cua phieu dang duyet.
        BigDecimal previousReturnedGross = sumApprovedReturnedGross(con, invoiceId);
        BigDecimal currentReturnedGross = sumReturnGross(con, returnId);
        BigDecimal returnedGross = previousReturnedGross.add(currentReturnedGross);

        int targetDelta = BigDecimal.valueOf(netFull)
                .multiply(returnedGross)
                .divide(originalSubTotal, 0, RoundingMode.DOWN)
                .intValue();
        int prevTarget = BigDecimal.valueOf(netFull)
                .multiply(previousReturnedGross)
                .divide(originalSubTotal, 0, RoundingMode.DOWN)
                .intValue();
        int delta = targetDelta - prevTarget;
        if (delta == 0) return;

        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE Customers SET MemberPoint = CASE "
                        + "WHEN MemberPoint + ? < 0 THEN 0 ELSE MemberPoint + ? END "
                        + "WHERE CustomerID = ?")) {
            ps.setInt(1, delta);
            ps.setInt(2, delta);
            ps.setInt(3, customerId);
            ps.executeUpdate();
        }
    }

    private BigDecimal sumReturnGross(Connection con, int returnId) throws SQLException {
        String sql = "SELECT ISNULL(SUM(d.Quantity * d.UnitPrice), 0) "
                + "FROM ReturnExchangeDetails d WHERE d.ReturnID = ? AND d.Direction = 'IN'";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, returnId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? nvl(rs.getBigDecimal(1)) : BigDecimal.ZERO;
            }
        }
    }


    private static BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}