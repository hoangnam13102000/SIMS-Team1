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
                + "(InvoiceID, Type, Reason, TotalValue, RequiresApproval, Status, CreatedBy) "
                + "VALUES (?, ?, ?, ?, ?, 'PENDING', ?)";
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
                BigDecimal totalValue = computeRefundAmount(con, header.getInvoiceId(), returnedGross);
                boolean requiresApproval = totalValue.compareTo(APPROVAL_THRESHOLD) > 0;

                int returnId;
                try (PreparedStatement ps = con.prepareStatement(insertHeaderSql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, header.getInvoiceId());
                    ps.setString(2, header.getType());
                    ps.setString(3, header.getReason().trim());
                    ps.setBigDecimal(4, totalValue);
                    ps.setBoolean(5, requiresApproval);
                    ps.setInt(6, header.getCreatedBy());
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
                    try (PreparedStatement ps = con.prepareStatement(approveSql)) {
                        ps.setInt(1, header.getCreatedBy());
                        ps.setInt(2, returnId);
                        ps.executeUpdate();
                    }
                    // Hoàn/thu hồi điểm theo tỷ lệ (sau khi đã APPROVED)
                    adjustPointsForApprovedReturn(con, header.getInvoiceId());
                }

                con.commit();
                header.setReturnId(returnId);
                header.setTotalValue(totalValue);
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

                try (PreparedStatement ps = con.prepareStatement(updateSql)) {
                    ps.setInt(1, approverId);
                    ps.setInt(2, returnId);
                    int affected = ps.executeUpdate();
                    if (affected == 0) {
                        con.rollback();
                        return "Yêu cầu này không còn ở trạng thái chờ duyệt.";
                    }
                }

                adjustPointsForApprovedReturn(con, invoiceId);

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

    /**
     * Tiền hoàn thực tế cho phần hàng trả (returnedGross = Σ UnitPrice×Qty IN).
     * Công thức khớp thứ tự tính lúc lập HĐ:
     *   discountShare → taxable → +VAT → −pointsShare
     */
    private BigDecimal computeRefundAmount(Connection con, int invoiceId, BigDecimal returnedGross)
            throws SQLException {
        if (returnedGross == null || returnedGross.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal subTotal = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal pointsDisc = BigDecimal.ZERO;
        BigDecimal vatRate = BigDecimal.ZERO;

        try (PreparedStatement ps = con.prepareStatement(
                "SELECT SubTotal, DiscountAmount, PointsDiscountAmount, VATRate "
                        + "FROM Invoices WHERE InvoiceID = ?")) {
            ps.setInt(1, invoiceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    subTotal = nvl(rs.getBigDecimal("SubTotal"));
                    discount = nvl(rs.getBigDecimal("DiscountAmount"));
                    try {
                        pointsDisc = nvl(rs.getBigDecimal("PointsDiscountAmount"));
                    } catch (SQLException ignore) {
                        pointsDisc = BigDecimal.ZERO;
                    }
                    vatRate = nvl(rs.getBigDecimal("VATRate"));
                }
            }
        }
        if (subTotal.signum() <= 0) {
            return returnedGross; // fallback
        }
        // Không vượt phần còn có thể trả theo gross
        BigDecimal alreadyReturned = sumApprovedReturnedGross(con, invoiceId);
        BigDecimal maxGrossLeft = subTotal.subtract(alreadyReturned);
        if (maxGrossLeft.signum() < 0) maxGrossLeft = BigDecimal.ZERO;
        BigDecimal gross = returnedGross.min(maxGrossLeft);

        BigDecimal ratio = gross.divide(subTotal, 8, RoundingMode.HALF_UP);
        BigDecimal discShare = discount.multiply(ratio).setScale(0, RoundingMode.HALF_UP);
        BigDecimal taxable = gross.subtract(discShare);
        if (taxable.signum() < 0) taxable = BigDecimal.ZERO;
        BigDecimal vatShare = taxable.multiply(vatRate)
                .divide(new BigDecimal("100"), 0, RoundingMode.HALF_UP);
        BigDecimal beforePts = taxable.add(vatShare);
        BigDecimal ptsShare = pointsDisc.multiply(ratio).setScale(0, RoundingMode.HALF_UP);
        BigDecimal refund = beforePts.subtract(ptsShare);
        return refund.signum() < 0 ? BigDecimal.ZERO : refund;
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
    private void adjustPointsForApprovedReturn(Connection con, int invoiceId) throws SQLException {
        Integer customerId = null;
        int pointsUsed = 0;
        BigDecimal subTotal = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;

        try (PreparedStatement ps = con.prepareStatement(
                "SELECT CustomerID, SubTotal, TotalAmount, PointsUsed FROM Invoices WHERE InvoiceID = ?")) {
            ps.setInt(1, invoiceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return;
                int cid = rs.getInt("CustomerID");
                if (rs.wasNull()) return;
                customerId = cid;
                subTotal = nvl(rs.getBigDecimal("SubTotal"));
                totalAmount = nvl(rs.getBigDecimal("TotalAmount"));
                try {
                    pointsUsed = Math.max(0, rs.getInt("PointsUsed"));
                } catch (SQLException ignore) {
                    pointsUsed = 0;
                }
            }
        }
        if (customerId == null || subTotal.signum() <= 0) return;

        int pointsEarned = 0;
        if (totalAmount.signum() > 0) {
            BigDecimal pointRate = storeConfigDAO.getPointRate();
            if (pointRate != null && pointRate.signum() > 0) {
                pointsEarned = totalAmount.divide(pointRate, 0, RoundingMode.DOWN).intValue();
            }
        }

        // net điểm cần hoàn trên toàn HĐ nếu trả 100%: +pointsUsed −pointsEarned
        int netFull = pointsUsed - pointsEarned;

        BigDecimal returnedGross = sumApprovedReturnedGross(con, invoiceId);
        // target = netFull * (returnedGross / subTotal), làm tròn xuống
        int targetDelta = BigDecimal.valueOf(netFull)
                .multiply(returnedGross)
                .divide(subTotal, 0, RoundingMode.DOWN)
                .intValue();

        // Đã áp dụng trước đó? Ước lượng từ MemberPoint không được → dùng công thức
        // dựa trên gross trước phiếu vừa duyệt: already = netFull * prevGross/sub
        // prevGross = returnedGross - gross của phiếu vừa duyệt cuối cùng — đơn giản hơn:
        // vì mỗi lần approve chỉ cộng phần chênh so với “target theo toàn bộ approved hiện tại”,
        // ta lưu không được → tính already bằng cách: không có cột → giả định
        // chỉ điều chỉnh đúng target − 0 nếu đây là lần đầu, hoặc…
        //
        // Cách an toàn không cần cột mới: set điểm về mốc tuyệt đối dựa trên
        // “điểm sau mua” + targetDelta, nhưng không biết điểm trước mua.
        //
        // Thực tế: chỉ cộng delta = targetDelta - previousTargetDelta
        // previousTarget = netFull * (returnedGross - lastReturnGross) / sub
        // lastReturnGross = gross of this return only.

        // Lấy gross của các return APPROVED TRỪ return có ApprovedAt mới nhất (vừa duyệt)
        BigDecimal prevGross = sumApprovedReturnedGrossExceptLatest(con, invoiceId);
        int prevTarget = BigDecimal.valueOf(netFull)
                .multiply(prevGross)
                .divide(subTotal, 0, RoundingMode.DOWN)
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

    /** Tổng gross IN đã APPROVED, trừ phiếu có ApprovedAt mới nhất (phiếu vừa duyệt). */
    private BigDecimal sumApprovedReturnedGrossExceptLatest(Connection con, int invoiceId)
            throws SQLException {
        String sql = "SELECT ISNULL(SUM(d.Quantity * d.UnitPrice), 0) "
                + "FROM ReturnExchangeDetails d "
                + "JOIN ReturnExchanges r ON r.ReturnID = d.ReturnID "
                + "WHERE r.InvoiceID = ? AND r.Status = 'APPROVED' AND d.Direction = 'IN' "
                + "AND r.ReturnID <> ("
                + "  SELECT TOP 1 ReturnID FROM ReturnExchanges "
                + "  WHERE InvoiceID = ? AND Status = 'APPROVED' "
                + "  ORDER BY ApprovedAt DESC, ReturnID DESC"
                + ")";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, invoiceId);
            ps.setInt(2, invoiceId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? nvl(rs.getBigDecimal(1)) : BigDecimal.ZERO;
            }
        }
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
