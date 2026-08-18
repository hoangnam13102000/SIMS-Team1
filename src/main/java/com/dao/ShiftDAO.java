package com.dao;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.model.Shift;
import com.model.ShiftCashSummary;
import com.model.ShiftCashTransaction;
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
import java.util.UUID;

/**
 * DAO truy cập dữ liệu ca bán hàng và sổ quỹ.
 *
 * DAO chỉ chịu trách nhiệm đọc/ghi database.
 *
 * DAO KHÔNG:
 * - kiểm tra quyền người dùng
 * - tự động mở ca
 * - quyết định nghiệp vụ
 *
 * Các nghiệp vụ mở ca, thu/chi, đóng ca, duyệt ca
 * phải được kiểm soát ở ShiftService.
 */
public class ShiftDAO {

    /**
     * SQL dùng chung để đọc thông tin Shift.
     */
    private static final String SHIFT_SELECT =
            "SELECT s.ShiftID, s.UserID, "
          + "u.FullName AS UserName, "
          + "s.StartTime, s.EndTime, s.Status, "
          + "s.OpeningCash, s.ExpectedCash, "
          + "s.CountedCash, s.CashDifference, "
          + "s.OpeningNote, s.ClosingNote, "
          + "s.ClosedBy, "
          + "closer.FullName AS ClosedByName, "
          + "s.ApprovedBy, "
          + "approver.FullName AS ApprovedByName, "
          + "s.ApprovedAt, "
          + "s.ApprovalNote, "

          // Invoice count
          + "(SELECT COUNT(*) "
          + " FROM Invoices inv "
          + " WHERE inv.ShiftID = s.ShiftID "
          + "   AND inv.Status = 'ACTIVE') "
          + " AS InvoiceCount, "

          // Cash sales
          + "COALESCE(("
          + " SELECT SUM(inv.TotalAmount) "
          + " FROM Invoices inv "
          + " WHERE inv.ShiftID = s.ShiftID "
          + "   AND inv.Status = 'ACTIVE' "
          + "   AND inv.PaymentMethod = 'CASH'"
          + "), 0) AS CashSales, "

          // Cash in
          + "COALESCE(("
          + " SELECT SUM(t.Amount) "
          + " FROM ShiftCashTransactions t "
          + " WHERE t.ShiftID = s.ShiftID "
          + "   AND t.Status = 'ACTIVE' "
          + "   AND t.TransactionType = 'CASH_IN'"
          + "), 0) AS CashIn, "

          // Cash out
          + "COALESCE(("
          + " SELECT SUM(t.Amount) "
          + " FROM ShiftCashTransactions t "
          + " WHERE t.ShiftID = s.ShiftID "
          + "   AND t.Status = 'ACTIVE' "
          + "   AND t.TransactionType = 'CASH_OUT'"
          + "), 0) AS CashOut, "

          // Cash refunds
          + "COALESCE(("
          + " SELECT SUM(r.TotalValue) "
          + " FROM ReturnExchanges r "
          + " JOIN Invoices originalInvoice "
          + "   ON originalInvoice.InvoiceID = r.InvoiceID "
          + " WHERE r.CreatedBy = s.UserID "
          + "   AND r.Status = 'APPROVED' "
          + "   AND r.Type = 'RETURN' "
          + "   AND originalInvoice.PaymentMethod = 'CASH' "
          + "   AND r.ApprovedAt >= s.StartTime "
          + "   AND r.ApprovedAt <= "
          + "       COALESCE(s.EndTime, CURRENT_TIMESTAMP)"
          + "), 0) AS CashRefunds "

          + "FROM Shifts s "
          + "JOIN Users u ON u.UserID = s.UserID "
          + "LEFT JOIN Users closer "
          + "  ON closer.UserID = s.ClosedBy "
          + "LEFT JOIN Users approver "
          + "  ON approver.UserID = s.ApprovedBy ";


    /**
     * ------------------------------------------------------------
     * LEGACY METHOD
     * ------------------------------------------------------------
     *
     * Giữ lại để PosPanel cũ vẫn compile.
     *
     * KHÔNG tự động mở ca.
     *
     * Nếu không có ca OPEN -> trả về -1.
     *
     * Sau khi PosPanel được sửa hoàn toàn,
     * có thể xóa method này.
     */
    @Deprecated
    public int getOrOpenShiftId(int userId) {

        Shift openShift = findOpenShiftByUserId(userId);

        if (openShift == null) {
            return -1;
        }

        return openShift.getShiftId();
    }


    /**
     * ------------------------------------------------------------
     * FIND OPEN SHIFT
     * ------------------------------------------------------------
     *
     * Tìm ca OPEN của một nhân viên.
     */
    public Shift findOpenShiftByUserId(int userId) {

        String sql =
                SHIFT_SELECT
              + "WHERE s.UserID = ? "
              + "AND s.Status = 'OPEN' "
              + "ORDER BY s.ShiftID DESC "
              + "LIMIT 1";

        try (
            Connection con = DBConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql)
        ) {

            ps.setInt(1, userId);

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {
                    return mapShift(rs);
                }

                return null;
            }

        } catch (SQLException e) {

            AppLogger.getInstance().error(
                    ErrorCode.DB_QUERY_FAIL,
                    "ShiftDAO.findOpenShiftByUserId - userId=" + userId,
                    e
            );

            return null;
        }
    }


    /**
     * ------------------------------------------------------------
     * FIND BY ID
     * ------------------------------------------------------------
     */
    public Shift findById(int shiftId) {

        try (Connection con = DBConnection.getConnection()) {

            return findById(con, shiftId);

        } catch (SQLException e) {

            AppLogger.getInstance().error(
                    ErrorCode.DB_QUERY_FAIL,
                    "ShiftDAO.findById - shiftId=" + shiftId,
                    e
            );

            return null;
        }
    }


    /**
     * ------------------------------------------------------------
     * FIND RECENT
     * ------------------------------------------------------------
     *
     * userId != null:
     *      chỉ xem ca của user đó.
     *
     * userId == null:
     *      xem ca của tất cả nhân viên.
     */
    public List<Shift> findRecent(
            Integer userId,
            int limit
    ) {

        int safeLimit = Math.max(
                1,
                Math.min(limit, 200)
        );

        String where =
                userId != null
                        ? "WHERE s.UserID = ? "
                        : "";

        String sql =
                SHIFT_SELECT
              + where
              + "ORDER BY s.StartTime DESC, "
              + "s.ShiftID DESC "
              + "LIMIT ?";

        List<Shift> result =
                new ArrayList<>();

        try (
            Connection con = DBConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql)
        ) {

            int parameterIndex = 1;

            if (userId != null) {

                ps.setInt(
                        parameterIndex,
                        userId
                );

                parameterIndex++;
            }

            ps.setInt(
                    parameterIndex,
                    safeLimit
            );

            try (ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {

                    result.add(
                            mapShift(rs)
                    );
                }
            }

        } catch (SQLException e) {

            AppLogger.getInstance().error(
                    ErrorCode.DB_QUERY_FAIL,
                    "ShiftDAO.findRecent - userId=" + userId,
                    e
            );
        }

        return result;
    }


    /**
     * ------------------------------------------------------------
     * FIND ALL OPEN SHIFTS
     * ------------------------------------------------------------
     */
    public List<Shift> findAllOpenShifts() {

        return findShiftsForMonitor(
                null,
                null,
                true
        );
    }


    /**
     * ------------------------------------------------------------
     * FIND SHIFTS FOR MONITOR
     * ------------------------------------------------------------
     *
     * @param from ngày bắt đầu
     * @param to ngày kết thúc
     * @param openOnly chỉ lấy OPEN
     */
    public List<Shift> findShiftsForMonitor(
            java.time.LocalDate from,
            java.time.LocalDate to,
            boolean openOnly
    ) {

        StringBuilder sql =
                new StringBuilder(
                        SHIFT_SELECT
                );

        sql.append(
                "WHERE 1=1 "
        );

        if (openOnly) {

            sql.append(
                    "AND s.Status = 'OPEN' "
            );
        }

        if (from != null) {

            sql.append(
                    "AND s.StartTime >= ? "
            );
        }

        if (to != null) {

            sql.append(
                    "AND s.StartTime < ? "
            );
        }

        sql.append(
                "ORDER BY s.StartTime DESC, "
              + "s.ShiftID DESC"
        );

        List<Shift> result =
                new ArrayList<>();

        try (
            Connection con = DBConnection.getConnection();
            PreparedStatement ps =
                    con.prepareStatement(
                            sql.toString()
                    )
        ) {

            int idx = 1;

            if (from != null) {

                ps.setTimestamp(
                        idx++,
                        Timestamp.valueOf(
                                from.atStartOfDay()
                        )
                );
            }

            if (to != null) {

                ps.setTimestamp(
                        idx,
                        Timestamp.valueOf(
                                to.plusDays(1)
                                  .atStartOfDay()
                        )
                );
            }

            try (ResultSet rs =
                         ps.executeQuery()) {

                while (rs.next()) {

                    result.add(
                            mapShift(rs)
                    );
                }
            }

        } catch (SQLException e) {

            AppLogger.getInstance().error(
                    ErrorCode.DB_QUERY_FAIL,
                    "ShiftDAO.findShiftsForMonitor "
                  + "from=" + from
                  + " to=" + to
                  + " openOnly=" + openOnly,
                    e
            );
        }

        return result;
    }


    /**
     * ------------------------------------------------------------
     * FIND CASH TRANSACTIONS
     * ------------------------------------------------------------
     */
    public List<ShiftCashTransaction> findTransactions(
            int shiftId
    ) {

        String sql =
                "SELECT "
              + "t.CashTransactionID, "
              + "t.TransactionCode, "
              + "t.ShiftID, "
              + "t.TransactionType, "
              + "t.Amount, "
              + "t.Reason, "
              + "t.CreatedBy, "
              + "u.FullName AS CreatedByName, "
              + "t.CreatedAt "
              + "FROM ShiftCashTransactions t "
              + "JOIN Users u "
              + "  ON u.UserID = t.CreatedBy "
              + "WHERE t.ShiftID = ? "
              + "  AND t.Status = 'ACTIVE' "
              + "ORDER BY t.CreatedAt DESC, "
              + "t.CashTransactionID DESC";

        List<ShiftCashTransaction> result =
                new ArrayList<>();

        try (
            Connection con = DBConnection.getConnection();
            PreparedStatement ps =
                    con.prepareStatement(sql)
        ) {

            ps.setInt(
                    1,
                    shiftId
            );

            try (ResultSet rs =
                         ps.executeQuery()) {

                while (rs.next()) {

                    result.add(
                            mapTransaction(rs)
                    );
                }
            }

        } catch (SQLException e) {

            AppLogger.getInstance().error(
                    ErrorCode.DB_QUERY_FAIL,
                    "ShiftDAO.findTransactions - shiftId="
                  + shiftId,
                    e
            );
        }

        return result;
    }


    /**
     * ------------------------------------------------------------
     * OPEN SHIFT
     * ------------------------------------------------------------
     */
    public Shift openShift(
            int userId,
            BigDecimal openingCash,
            String openingNote
    ) throws SQLException {

        String checkSql =
                "SELECT ShiftID "
              + "FROM Shifts "
              + "WHERE UserID = ? "
              + "  AND Status = 'OPEN' "
              + "LIMIT 1 "
              + "FOR UPDATE";

        String insertSql =
                "INSERT INTO Shifts ("
              + "UserID, "
              + "StartTime, "
              + "Status, "
              + "OpeningCash, "
              + "OpeningNote"
              + ") VALUES ("
              + "?, "
              + "CURRENT_TIMESTAMP, "
              + "'OPEN', "
              + "?, "
              + "?"
              + ")";

        try (
            Connection con =
                    DBConnection.getConnection()
        ) {

            con.setAutoCommit(false);

            try {

                /*
                 * Kiểm tra user đã có ca OPEN hay chưa.
                 */
                try (
                    PreparedStatement ps =
                            con.prepareStatement(
                                    checkSql
                            )
                ) {

                    ps.setInt(
                            1,
                            userId
                    );

                    try (
                        ResultSet rs =
                                ps.executeQuery()
                    ) {

                        if (rs.next()) {

                            throw new SQLException(
                                    "Nhân viên đã có ca đang mở",
                                    "45000"
                            );
                        }
                    }
                }

                int shiftId;

                /*
                 * Tạo ca mới.
                 */
                try (
                    PreparedStatement ps =
                            con.prepareStatement(
                                    insertSql,
                                    Statement.RETURN_GENERATED_KEYS
                            )
                ) {

                    ps.setInt(
                            1,
                            userId
                    );

                    ps.setBigDecimal(
                            2,
                            openingCash
                    );

                    setNullableString(
                            ps,
                            3,
                            openingNote
                    );

                    int insertedRows =
                            ps.executeUpdate();

                    if (insertedRows != 1) {

                        throw new SQLException(
                                "Không tạo được ca mới"
                        );
                    }

                    try (
                        ResultSet keys =
                                ps.getGeneratedKeys()
                    ) {

                        if (!keys.next()) {

                            throw new SQLException(
                                    "Không lấy được ShiftID vừa tạo"
                            );
                        }

                        shiftId =
                                keys.getInt(1);
                    }
                }

                /*
                 * Đọc lại ca vừa tạo trong cùng connection.
                 */
                Shift shift =
                        findById(
                                con,
                                shiftId
                        );

                if (shift == null) {

                    throw new SQLException(
                            "Tạo ca thành công nhưng không đọc lại được ca"
                    );
                }

                con.commit();

                return shift;

            } catch (Exception e) {

                con.rollback();

                if (e instanceof SQLException sqlException) {

                    throw sqlException;
                }

                throw new SQLException(
                        "Không thể mở ca",
                        e
                );

            } finally {

                con.setAutoCommit(true);
            }
        }
    }


    /**
     * ------------------------------------------------------------
     * ADD CASH TRANSACTION
     * ------------------------------------------------------------
     *
     * Ghi một khoản CASH_IN hoặc CASH_OUT.
     */
    public ShiftCashTransaction addCashTransaction(
            int shiftId,
            int actorUserId,
            String type,
            BigDecimal amount,
            String reason
    ) throws SQLException {

        String insertSql =
                "INSERT INTO ShiftCashTransactions ("
              + "TransactionCode, "
              + "ShiftID, "
              + "TransactionType, "
              + "Amount, "
              + "Reason, "
              + "CreatedBy, "
              + "Status"
              + ") VALUES ("
              + "?, ?, ?, ?, ?, ?, 'ACTIVE'"
              + ")";

        try (
            Connection con =
                    DBConnection.getConnection()
        ) {

            con.setAutoCommit(false);

            try {

                /*
                 * Khóa ca và kiểm tra người thao tác.
                 */
                lockOwnedOpenShift(
                        con,
                        shiftId,
                        actorUserId
                );

                String transactionCode =
                        "CT-"
                      + System.currentTimeMillis()
                      + "-"
                      + UUID.randomUUID()
                                .toString()
                                .substring(0, 8)
                                .toUpperCase();

                long cashTransactionId;

                try (
                    PreparedStatement ps =
                            con.prepareStatement(
                                    insertSql,
                                    Statement.RETURN_GENERATED_KEYS
                            )
                ) {

                    ps.setString(
                            1,
                            transactionCode
                    );

                    ps.setInt(
                            2,
                            shiftId
                    );

                    ps.setString(
                            3,
                            type
                    );

                    ps.setBigDecimal(
                            4,
                            amount
                    );

                    setNullableString(
                            ps,
                            5,
                            reason
                    );

                    ps.setInt(
                            6,
                            actorUserId
                    );

                    int insertedRows =
                            ps.executeUpdate();

                    if (insertedRows != 1) {

                        throw new SQLException(
                                "Không thêm được giao dịch quỹ"
                        );
                    }

                    try (
                        ResultSet keys =
                                ps.getGeneratedKeys()
                    ) {

                        if (!keys.next()) {

                            throw new SQLException(
                                    "Không lấy được mã giao dịch quỹ"
                            );
                        }

                        cashTransactionId =
                                keys.getLong(1);
                    }
                }

                ShiftCashTransaction transaction =
                        findTransactionById(
                                con,
                                cashTransactionId
                        );

                if (transaction == null) {

                    throw new SQLException(
                            "Tạo giao dịch thành công "
                          + "nhưng không đọc lại được"
                    );
                }

                con.commit();

                return transaction;

            } catch (Exception e) {

                con.rollback();

                if (e instanceof SQLException sqlException) {

                    throw sqlException;
                }

                throw new SQLException(
                        "Không thể ghi giao dịch quỹ",
                        e
                );

            } finally {

                con.setAutoCommit(true);
            }
        }
    }


    /**
     * ------------------------------------------------------------
     * CALCULATE CASH SUMMARY
     * ------------------------------------------------------------
     *
     * Public API.
     *
     * Code bên ngoài ShiftDAO nên gọi method này.
     *
     * KHÔNG truyền Connection từ bên ngoài.
     */
    public ShiftCashSummary calculateCashSummary(
            int shiftId
    ) throws SQLException {

        try (
            Connection con =
                    DBConnection.getConnection()
        ) {

            return calculateCashSummary(
                    con,
                    shiftId
            );
        }
    }


    /**
     * ------------------------------------------------------------
     * CALCULATE CASH SUMMARY - INTERNAL
     * ------------------------------------------------------------
     *
     * Package-private (khong phai private): cac DAO khac trong CUNG package
     * com.dao (vd ReturnExchangeDAO.completeCashRefund) can goi overload nay
     * de tinh tren DUNG Connection/transaction dang giu khoa FOR UPDATE cua
     * lockOwnedOpenShift - neu goi ban public calculateCashSummary(shiftId)
     * (tu mo Connection moi) se doc ngoai transaction, khong thay du lieu vua
     * khoa, sai logic. Van khong public de code NGOAI package com.dao khong
     * the tu tay truyen/quan ly Connection.
     */
    ShiftCashSummary calculateCashSummary(
            Connection con,
            int shiftId
    ) throws SQLException {

        String sql =
                "SELECT "
              + "s.OpeningCash, "

              // Cash sales
              + "COALESCE(("
              + " SELECT SUM(inv.TotalAmount) "
              + " FROM Invoices inv "
              + " WHERE inv.ShiftID = s.ShiftID "
              + "   AND inv.Status = 'ACTIVE' "
              + "   AND inv.PaymentMethod = 'CASH'"
              + "), 0) AS CashSales, "

              // Cash in
              + "COALESCE(("
              + " SELECT SUM(t.Amount) "
              + " FROM ShiftCashTransactions t "
              + " WHERE t.ShiftID = s.ShiftID "
              + "   AND t.Status = 'ACTIVE' "
              + "   AND t.TransactionType = 'CASH_IN'"
              + "), 0) AS CashIn, "

              // Cash out
              + "COALESCE(("
              + " SELECT SUM(t.Amount) "
              + " FROM ShiftCashTransactions t "
              + " WHERE t.ShiftID = s.ShiftID "
              + "   AND t.Status = 'ACTIVE' "
              + "   AND t.TransactionType = 'CASH_OUT'"
              + "), 0) AS CashOut, "

              // Cash refunds
              + "COALESCE(("
              + " SELECT SUM(r.TotalValue) "
              + " FROM ReturnExchanges r "
              + " JOIN Invoices originalInvoice "
              + "   ON originalInvoice.InvoiceID = r.InvoiceID "
              + " WHERE r.CreatedBy = s.UserID "
              + "   AND r.Status = 'APPROVED' "
              + "   AND r.Type = 'RETURN' "
              + "   AND originalInvoice.PaymentMethod = 'CASH' "
              + "   AND r.ApprovedAt >= s.StartTime "
              + "   AND r.ApprovedAt <= "
              + "       COALESCE(s.EndTime, CURRENT_TIMESTAMP)"
              + "), 0) AS CashRefunds, "

              // Invoice count
              + "(SELECT COUNT(*) "
              + " FROM Invoices inv "
              + " WHERE inv.ShiftID = s.ShiftID "
              + "   AND inv.Status = 'ACTIVE'"
              + ") AS InvoiceCount "

              + "FROM Shifts s "
              + "WHERE s.ShiftID = ?";

        try (
            PreparedStatement ps =
                    con.prepareStatement(sql)
        ) {

            ps.setInt(
                    1,
                    shiftId
            );

            try (
                ResultSet rs =
                        ps.executeQuery()
            ) {

                if (!rs.next()) {

                    throw new SQLException(
                            "Không tìm thấy ca #" + shiftId
                    );
                }

                return new ShiftCashSummary(
                        rs.getBigDecimal(
                                "OpeningCash"
                        ),
                        rs.getBigDecimal(
                                "CashSales"
                        ),
                        rs.getBigDecimal(
                                "CashIn"
                        ),
                        rs.getBigDecimal(
                                "CashOut"
                        ),
                        rs.getBigDecimal(
                                "CashRefunds"
                        ),
                        rs.getInt(
                                "InvoiceCount"
                        )
                );
            }
        }
    }


    /**
     * ------------------------------------------------------------
     * LOCK OWNED OPEN SHIFT
     * ------------------------------------------------------------
     *
     * Khóa ca và kiểm tra:
     *
     * 1. Ca tồn tại
     * 2. Đúng chủ sở hữu
     * 3. Ca đang OPEN
     */
    void lockOwnedOpenShift(
            Connection con,
            int shiftId,
            int actorUserId
    ) throws SQLException {

        String sql =
                "SELECT UserID, Status "
              + "FROM Shifts "
              + "WHERE ShiftID = ? "
              + "FOR UPDATE";

        try (
            PreparedStatement ps =
                    con.prepareStatement(sql)
        ) {

            ps.setInt(
                    1,
                    shiftId
            );

            try (
                ResultSet rs =
                        ps.executeQuery()
            ) {

                if (!rs.next()) {

                    throw new SQLException(
                            "Không tìm thấy ca #" + shiftId,
                            "45000"
                    );
                }

                int ownerUserId =
                        rs.getInt("UserID");

                String status =
                        rs.getString("Status");

                if (ownerUserId != actorUserId) {

                    throw new SQLException(
                            "Chỉ nhân viên sở hữu ca "
                          + "mới được thao tác",
                            "45000"
                    );
                }

                if (!"OPEN".equals(status)) {

                    throw new SQLException(
                            "Ca đã đóng, không thể thao tác",
                            "45000"
                    );
                }
            }
        }
    }


    /**
     * ------------------------------------------------------------
     * CLOSE SHIFT
     * ------------------------------------------------------------
     */
    public Shift closeShift(
            int shiftId,
            int actorUserId,
            BigDecimal countedCash,
            String closingNote
    ) throws SQLException {

        String updateSql =
                "UPDATE Shifts SET "
              + "EndTime = CURRENT_TIMESTAMP, "
              + "Status = 'PENDING_APPROVAL', "
              + "ExpectedCash = ?, "
              + "CountedCash = ?, "
              + "CashDifference = ?, "
              + "ClosingNote = ?, "
              + "ClosedBy = ? "
              + "WHERE ShiftID = ? "
              + "AND Status = 'OPEN'";

        try (
            Connection con =
                    DBConnection.getConnection()
        ) {

            con.setAutoCommit(false);

            try {

                /*
                 * Khóa ca.
                 */
                lockOwnedOpenShift(
                        con,
                        shiftId,
                        actorUserId
                );

                /*
                 * Tính tiền hệ thống ngay trong transaction.
                 *
                 * QUAN TRỌNG:
                 * Đây là lời gọi method private.
                 * Nó hợp lệ vì closeShift() nằm trong ShiftDAO.
                 */
                ShiftCashSummary summary =
                        calculateCashSummary(
                                con,
                                shiftId
                        );

                BigDecimal expectedCash =
                        summary.getExpectedCash();

                BigDecimal difference =
                        summary.differenceFrom(
                                countedCash
                        );

                /*
                 * Có chênh lệch -> bắt buộc nhập giải trình.
                 */
                if (
                    difference.signum() != 0
                    && (
                        closingNote == null
                        || closingNote.isBlank()
                    )
                ) {

                    throw new SQLException(
                            "Phải nhập giải trình khi tiền "
                          + "kiểm thực tế bị chênh lệch",
                            "45000"
                    );
                }

                try (
                    PreparedStatement ps =
                            con.prepareStatement(
                                    updateSql
                            )
                ) {

                    ps.setBigDecimal(
                            1,
                            expectedCash
                    );

                    ps.setBigDecimal(
                            2,
                            countedCash
                    );

                    ps.setBigDecimal(
                            3,
                            difference
                    );

                    setNullableString(
                            ps,
                            4,
                            closingNote
                    );

                    ps.setInt(
                            5,
                            actorUserId
                    );

                    ps.setInt(
                            6,
                            shiftId
                    );

                    int updatedRows =
                            ps.executeUpdate();

                    if (updatedRows != 1) {

                        throw new SQLException(
                                "Ca đã được đóng bởi tiến trình khác"
                        );
                    }
                }

                Shift closedShift =
                        findById(
                                con,
                                shiftId
                        );

                if (closedShift == null) {

                    throw new SQLException(
                            "Đóng ca thành công nhưng "
                          + "không đọc lại được ca"
                    );
                }

                con.commit();

                return closedShift;

            } catch (Exception e) {

                con.rollback();

                if (e instanceof SQLException sqlException) {

                    throw sqlException;
                }

                throw new SQLException(
                        "Không thể đóng ca",
                        e
                );

            } finally {

                con.setAutoCommit(true);
            }
        }
    }


    /**
     * ------------------------------------------------------------
     * APPROVE SHIFT
     * ------------------------------------------------------------
     *
     * PENDING_APPROVAL -> CLOSED
     */
    public Shift approveShift(
            int shiftId,
            int managerUserId,
            String approvalNote
    ) throws SQLException {

        String updateSql =
                "UPDATE Shifts SET "
              + "Status = 'CLOSED', "
              + "ApprovedBy = ?, "
              + "ApprovedAt = CURRENT_TIMESTAMP, "
              + "ApprovalNote = ? "
              + "WHERE ShiftID = ? "
              + "AND Status = 'PENDING_APPROVAL'";

        try (
            Connection con =
                    DBConnection.getConnection()
        ) {

            con.setAutoCommit(false);

            try {

                lockPendingShift(
                        con,
                        shiftId
                );

                try (
                    PreparedStatement ps =
                            con.prepareStatement(
                                    updateSql
                            )
                ) {

                    ps.setInt(
                            1,
                            managerUserId
                    );

                    setNullableString(
                            ps,
                            2,
                            approvalNote
                    );

                    ps.setInt(
                            3,
                            shiftId
                    );

                    int updated =
                            ps.executeUpdate();

                    if (updated != 1) {

                        throw new SQLException(
                                "Ca không ở trạng thái "
                              + "chờ duyệt hoặc đã được xử lý",
                                "45000"
                        );
                    }
                }

                Shift shift =
                        findById(
                                con,
                                shiftId
                        );

                if (shift == null) {

                    throw new SQLException(
                            "Duyệt ca thành công "
                          + "nhưng không đọc lại được ca"
                    );
                }

                con.commit();

                return shift;

            } catch (Exception e) {

                con.rollback();

                if (e instanceof SQLException sqlException) {

                    throw sqlException;
                }

                throw new SQLException(
                        "Không thể duyệt ca",
                        e
                );

            } finally {

                con.setAutoCommit(true);
            }
        }
    }


    /**
     * ------------------------------------------------------------
     * REJECT SHIFT
     * ------------------------------------------------------------
     *
     * PENDING_APPROVAL -> REJECTED
     */
    public Shift rejectShift(
            int shiftId,
            int managerUserId,
            String approvalNote
    ) throws SQLException {

        if (
            approvalNote == null
            || approvalNote.isBlank()
        ) {

            throw new SQLException(
                    "Phải nhập lý do từ chối đối soát",
                    "45000"
            );
        }

        String updateSql =
                "UPDATE Shifts SET "
              + "Status = 'REJECTED', "
              + "ApprovedBy = ?, "
              + "ApprovedAt = CURRENT_TIMESTAMP, "
              + "ApprovalNote = ? "
              + "WHERE ShiftID = ? "
              + "AND Status = 'PENDING_APPROVAL'";

        try (
            Connection con =
                    DBConnection.getConnection()
        ) {

            con.setAutoCommit(false);

            try {

                lockPendingShift(
                        con,
                        shiftId
                );

                try (
                    PreparedStatement ps =
                            con.prepareStatement(
                                    updateSql
                            )
                ) {

                    ps.setInt(
                            1,
                            managerUserId
                    );

                    setNullableString(
                            ps,
                            2,
                            approvalNote
                    );

                    ps.setInt(
                            3,
                            shiftId
                    );

                    int updated =
                            ps.executeUpdate();

                    if (updated != 1) {

                        throw new SQLException(
                                "Ca không ở trạng thái "
                              + "chờ duyệt hoặc đã được xử lý",
                                "45000"
                        );
                    }
                }

                Shift shift =
                        findById(
                                con,
                                shiftId
                        );

                if (shift == null) {

                    throw new SQLException(
                            "Từ chối ca thành công "
                          + "nhưng không đọc lại được ca"
                    );
                }

                con.commit();

                return shift;

            } catch (Exception e) {

                con.rollback();

                if (e instanceof SQLException sqlException) {

                    throw sqlException;
                }

                throw new SQLException(
                        "Không thể từ chối ca",
                        e
                );

            } finally {

                con.setAutoCommit(true);
            }
        }
    }


    /**
     * ------------------------------------------------------------
     * LOCK PENDING SHIFT
     * ------------------------------------------------------------
     */
    void lockPendingShift(
            Connection con,
            int shiftId
    ) throws SQLException {

        String sql =
                "SELECT Status "
              + "FROM Shifts "
              + "WHERE ShiftID = ? "
              + "FOR UPDATE";

        try (
            PreparedStatement ps =
                    con.prepareStatement(sql)
        ) {

            ps.setInt(
                    1,
                    shiftId
            );

            try (
                ResultSet rs =
                        ps.executeQuery()
            ) {

                if (!rs.next()) {

                    throw new SQLException(
                            "Không tìm thấy ca #" + shiftId,
                            "45000"
                    );
                }

                String status =
                        rs.getString("Status");

                if (
                    !"PENDING_APPROVAL".equals(status)
                ) {

                    throw new SQLException(
                            "Chỉ được đối soát ca đang "
                          + "chờ duyệt (PENDING_APPROVAL)",
                            "45000"
                    );
                }
            }
        }
    }


    /**
     * ------------------------------------------------------------
     * FIND PENDING APPROVAL SHIFTS
     * ------------------------------------------------------------
     */
    public List<Shift> findPendingApprovalShifts() {

        String sql =
                SHIFT_SELECT
              + "WHERE s.Status = 'PENDING_APPROVAL' "
              + "ORDER BY s.EndTime ASC";

        List<Shift> list =
                new ArrayList<>();

        try (
            Connection con =
                    DBConnection.getConnection();
            PreparedStatement ps =
                    con.prepareStatement(sql);
            ResultSet rs =
                    ps.executeQuery()
        ) {

            while (rs.next()) {

                list.add(
                        mapShift(rs)
                );
            }

        } catch (SQLException e) {

            AppLogger.getInstance().error(
                    ErrorCode.DB_QUERY_FAIL,
                    "ShiftDAO.findPendingApprovalShifts",
                    e
            );
        }

        return list;
    }


    /**
     * ------------------------------------------------------------
     * FIND BY ID - INTERNAL CONNECTION
     * ------------------------------------------------------------
     */
    private Shift findById(
            Connection con,
            int shiftId
    ) throws SQLException {

        String sql =
                SHIFT_SELECT
              + "WHERE s.ShiftID = ?";

        try (
            PreparedStatement ps =
                    con.prepareStatement(sql)
        ) {

            ps.setInt(
                    1,
                    shiftId
            );

            try (
                ResultSet rs =
                        ps.executeQuery()
            ) {

                if (rs.next()) {

                    return mapShift(rs);
                }

                return null;
            }
        }
    }


    /**
     * ------------------------------------------------------------
     * FIND TRANSACTION BY ID - INTERNAL
     * ------------------------------------------------------------
     */
    private ShiftCashTransaction findTransactionById(
            Connection con,
            long cashTransactionId
    ) throws SQLException {

        String sql =
                "SELECT "
              + "t.CashTransactionID, "
              + "t.TransactionCode, "
              + "t.ShiftID, "
              + "t.TransactionType, "
              + "t.Amount, "
              + "t.Reason, "
              + "t.CreatedBy, "
              + "u.FullName AS CreatedByName, "
              + "t.CreatedAt "
              + "FROM ShiftCashTransactions t "
              + "JOIN Users u "
              + "  ON u.UserID = t.CreatedBy "
              + "WHERE t.CashTransactionID = ?";

        try (
            PreparedStatement ps =
                    con.prepareStatement(sql)
        ) {

            ps.setLong(
                    1,
                    cashTransactionId
            );

            try (
                ResultSet rs =
                        ps.executeQuery()
            ) {

                if (rs.next()) {

                    return mapTransaction(rs);
                }

                return null;
            }
        }
    }


    /**
     * ------------------------------------------------------------
     * MAP SHIFT
     * ------------------------------------------------------------
     */
    private Shift mapShift(
            ResultSet rs
    ) throws SQLException {

        Shift shift =
                new Shift();

        shift.setShiftId(
                rs.getInt("ShiftID")
        );

        shift.setUserId(
                rs.getInt("UserID")
        );

        shift.setUserName(
                rs.getString("UserName")
        );

        shift.setStartTime(
                toLocalDateTime(
                        rs.getTimestamp(
                                "StartTime"
                        )
                )
        );

        shift.setEndTime(
                toLocalDateTime(
                        rs.getTimestamp(
                                "EndTime"
                        )
                )
        );

        shift.setStatus(
                rs.getString("Status")
        );

        shift.setOpeningCash(
                rs.getBigDecimal(
                        "OpeningCash"
                )
        );

        shift.setExpectedCash(
                rs.getBigDecimal(
                        "ExpectedCash"
                )
        );

        shift.setCountedCash(
                rs.getBigDecimal(
                        "CountedCash"
                )
        );

        shift.setCashDifference(
                rs.getBigDecimal(
                        "CashDifference"
                )
        );

        shift.setOpeningNote(
                rs.getString("OpeningNote")
        );

        shift.setClosingNote(
                rs.getString("ClosingNote")
        );

        int closedBy =
                rs.getInt("ClosedBy");

        if (rs.wasNull()) {

            shift.setClosedBy(null);

        } else {

            shift.setClosedBy(
                    closedBy
            );
        }

        shift.setClosedByName(
                rs.getString(
                        "ClosedByName"
                )
        );

        int approvedBy =
                rs.getInt("ApprovedBy");

        if (!rs.wasNull()) {

            shift.setApprovedBy(
                    approvedBy
            );
        }

        shift.setApprovedByName(
                rs.getString(
                        "ApprovedByName"
                )
        );

        shift.setApprovedAt(
                toLocalDateTime(
                        rs.getTimestamp(
                                "ApprovedAt"
                        )
                )
        );

        shift.setApprovalNote(
                rs.getString(
                        "ApprovalNote"
                )
        );

        shift.setInvoiceCount(
                rs.getInt(
                        "InvoiceCount"
                )
        );

        shift.setCashSales(
                rs.getBigDecimal(
                        "CashSales"
                )
        );

        shift.setCashIn(
                rs.getBigDecimal(
                        "CashIn"
                )
        );

        shift.setCashOut(
                rs.getBigDecimal(
                        "CashOut"
                )
        );

        shift.setCashRefunds(
                rs.getBigDecimal(
                        "CashRefunds"
                )
        );

        return shift;
    }


    /**
     * ------------------------------------------------------------
     * MAP CASH TRANSACTION
     * ------------------------------------------------------------
     */
    private ShiftCashTransaction mapTransaction(
            ResultSet rs
    ) throws SQLException {

        ShiftCashTransaction transaction =
                new ShiftCashTransaction();

        transaction.setCashTransactionId(
                rs.getLong(
                        "CashTransactionID"
                )
        );

        transaction.setTransactionCode(
                rs.getString(
                        "TransactionCode"
                )
        );

        transaction.setShiftId(
                rs.getInt(
                        "ShiftID"
                )
        );

        transaction.setTransactionType(
                rs.getString(
                        "TransactionType"
                )
        );

        transaction.setAmount(
                rs.getBigDecimal(
                        "Amount"
                )
        );

        transaction.setReason(
                rs.getString(
                        "Reason"
                )
        );

        transaction.setCreatedBy(
                rs.getInt(
                        "CreatedBy"
                )
        );

        transaction.setCreatedByName(
                rs.getString(
                        "CreatedByName"
                )
        );

        transaction.setCreatedAt(
                toLocalDateTime(
                        rs.getTimestamp(
                                "CreatedAt"
                        )
                )
        );

        return transaction;
    }


    /**
     * ------------------------------------------------------------
     * TIMESTAMP -> LOCALDATETIME
     * ------------------------------------------------------------
     */
    private java.time.LocalDateTime toLocalDateTime(
            Timestamp timestamp
    ) {

        if (timestamp == null) {
            return null;
        }

        return timestamp.toLocalDateTime();
    }


    /**
     * ------------------------------------------------------------
     * SET NULLABLE STRING
     * ------------------------------------------------------------
     */
    private void setNullableString(
            PreparedStatement ps,
            int parameterIndex,
            String value
    ) throws SQLException {

        if (
            value == null
            || value.isBlank()
        ) {

            ps.setNull(
                    parameterIndex,
                    Types.VARCHAR
            );

            return;
        }

        ps.setString(
                parameterIndex,
                value.trim()
        );
    }
}