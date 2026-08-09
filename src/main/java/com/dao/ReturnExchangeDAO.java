package com.dao;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.event.AppEventBus;
import com.event.DataChangedEvent;
import com.model.ReturnExchange;
import com.model.ReturnExchangeDetail;
import com.utils.DBConnection;

import java.math.BigDecimal;
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

public class ReturnExchangeDAO extends BaseDAO<ReturnExchange> {

    public static final BigDecimal APPROVAL_THRESHOLD = new BigDecimal("0");

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
                + "WHERE d.ReturnID = ? "
                + "ORDER BY d.Direction DESC, d.ReturnDetailID ASC";

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

    public List<ReturnExchange> getByInvoice(int invoiceId) {
        String sql = "SELECT " + getColumns() + " FROM " + BASE_TABLE
                + " WHERE r.InvoiceID = ? ORDER BY r.CreatedAt DESC, r.ReturnID DESC";
        List<ReturnExchange> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, invoiceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapResultSet(rs));
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "ReturnExchangeDAO.getByInvoice - invoiceId=" + invoiceId, e);
        }
        return list;
    }

    public Map<Integer, Integer> getReturnableQuantities(int invoiceId) {
        Map<Integer, Integer> sold = new HashMap<>();
        String soldSql = "SELECT ProductID, SUM(Quantity) AS Qty FROM InvoiceDetails "
                + "WHERE InvoiceID = ? GROUP BY ProductID";
        String returnedSql = "SELECT rd.ProductID, SUM(rd.Quantity) AS Qty "
                + "FROM ReturnExchangeDetails rd "
                + "JOIN ReturnExchanges r ON r.ReturnID = rd.ReturnID "
                + "WHERE r.InvoiceID = ? AND rd.Direction = 'IN' AND r.Status IN ('PENDING','APPROVED') "
                + "GROUP BY rd.ProductID";

        try (Connection con = DBConnection.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement(soldSql)) {
                ps.setInt(1, invoiceId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) sold.put(rs.getInt("ProductID"), rs.getInt("Qty"));
                }
            }
            try (PreparedStatement ps = con.prepareStatement(returnedSql)) {
                ps.setInt(1, invoiceId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int pid = rs.getInt("ProductID");
                        int returned = rs.getInt("Qty");
                        sold.merge(pid, -returned, Integer::sum);
                    }
                }
            }
        } catch (SQLException e) {
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
                        int already = returnRequestedSoFar.merge(d.getProductId(), d.getQuantity(), Integer::sum);
                        int limit = returnable.getOrDefault(d.getProductId(), 0);
                        if (already > limit) {
                            con.rollback();
                            return "Sản phẩm \"" + d.getProductName() + "\" chỉ còn có thể trả tối đa "
                                    + limit + " (đã bán trừ đã đổi/trả trước đó).";
                        }
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

                BigDecimal totalValue = BigDecimal.ZERO;
                for (ReturnExchangeDetail d : details) {
                    if (d.isIn()) totalValue = totalValue.add(d.getLineTotal());
                }
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
        String lockRequestSql = "SELECT Status FROM ReturnExchanges WITH (UPDLOCK, ROWLOCK) WHERE ReturnID = ?";
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
                try (PreparedStatement ps = con.prepareStatement(lockRequestSql)) {
                    ps.setInt(1, returnId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            con.rollback();
                            return "Không tìm thấy yêu cầu đổi/trả.";
                        }
                        status = rs.getString("Status");
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
                            int quantity = rs.getInt("Quantity");
                            int stock = rs.getInt("Stock");
                            if (quantity > stock) {
                                con.rollback();
                                return "Sản phẩm \"" + rs.getString("ProductName")
                                        + "\" không đủ tồn kho để duyệt đổi (còn " + stock
                                        + ", cần " + quantity + ").";
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
}