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
import java.sql.Types;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DAO truy cập dữ liệu ca bán hàng và sổ quỹ.
 *
 * DAO không tự động mở ca khi nhân viên bán hàng.
 * Việc mở ca, thu/chi và đóng ca phải đi qua ShiftService
 * để kiểm tra quyền và các quy tắc nghiệp vụ.
 *
 * Mọi hóa đơn tại quầy phải thuộc về một ca đang OPEN
 * và ca đó phải thuộc đúng nhân viên tạo hóa đơn.
 */
public class ShiftDAO {

	/**
	 * Truy cap du lieu ca ban hang va so quy tien mat.
	 *
	 * DAO chi chiu trach nhiem doc/ghi database.
	 * DAO khong tu kiem tra quyen nguoi dung.
	 * Quyen se duoc kiem tra tai ShiftService.
	 *
	 * Khac phien ban cu, DAO khong tu dong mo ca khi ban hang.
	 * Nhan vien phai mo ca, nhap tien dau ca va dong ca ro rang.
	 */
	
	private static final String SHIFT_SELECT = ""
	        + "SELECT s.ShiftID, s.UserID, "
	        + "u.FullName AS UserName, "
	        + "s.StartTime, s.EndTime, s.Status, "
	        + "s.OpeningCash, s.ExpectedCash, "
	        + "s.CountedCash, s.CashDifference, "
	        + "s.OpeningNote, s.ClosingNote, "
	        + "s.ClosedBy, "
	        + "closer.FullName AS ClosedByName, "

	        + "(SELECT COUNT(*) "
	        + " FROM Invoices inv "
	        + " WHERE inv.ShiftID = s.ShiftID "
	        + "   AND inv.Status = 'ACTIVE') "
	        + " AS InvoiceCount, "

	        + "COALESCE(("
	        + " SELECT SUM(inv.TotalAmount) "
	        + " FROM Invoices inv "
	        + " WHERE inv.ShiftID = s.ShiftID "
	        + "   AND inv.Status = 'ACTIVE' "
	        + "   AND inv.PaymentMethod = 'CASH'"
	        + "), 0) AS CashSales, "

	        + "COALESCE(("
	        + " SELECT SUM(t.Amount) "
	        + " FROM ShiftCashTransactions t "
	        + " WHERE t.ShiftID = s.ShiftID "
	        + "   AND t.Status = 'ACTIVE' "
	        + "   AND t.TransactionType = 'CASH_IN'"
	        + "), 0) AS CashIn, "

	        + "COALESCE(("
	        + " SELECT SUM(t.Amount) "
	        + " FROM ShiftCashTransactions t "
	        + " WHERE t.ShiftID = s.ShiftID "
	        + "   AND t.Status = 'ACTIVE' "
	        + "   AND t.TransactionType = 'CASH_OUT'"
	        + "), 0) AS CashOut, "

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
	        + "  ON closer.UserID = s.ClosedBy ";
	
	/**
	 * Ham tam thoi de PosPanel cu van bien dich.
	 *
	 * Luu y: ham nay KHONG con tu dong mo ca.
	 * Neu nhan vien chua co ca OPEN thi tra ve -1.
	 *
	 * Sau khi PosPanel duoc sua xong, ham nay se duoc xoa.
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
	 * Tim ca dang OPEN cua mot nhan vien.
	 *
	 * @param userId ma nhan vien dang dang nhap
	 * @return doi tuong Shift neu dang co ca mo;
	 *         null neu chua co ca hoac co loi database
	 */
	public Shift findOpenShiftByUserId(int userId) {
	    String sql = SHIFT_SELECT
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
	 * Tim mot ca theo ShiftID.
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
	 * Lay danh sach ca moi nhat.
	 *
	 * userId != null: chi xem ca cua user do.
	 * userId == null: xem ca cua tat ca nhan vien.
	 */
	public List<Shift> findRecent(Integer userId, int limit) {
	    int safeLimit = Math.max(
	            1,
	            Math.min(limit, 200)
	    );

	    String where = userId != null
	            ? "WHERE s.UserID = ? "
	            : "";

	    String sql = SHIFT_SELECT
	            + where
	            + "ORDER BY s.StartTime DESC, "
	            + "s.ShiftID DESC "
	            + "LIMIT ?";

	    List<Shift> result = new ArrayList<>();

	    try (
	        Connection con = DBConnection.getConnection();
	        PreparedStatement ps = con.prepareStatement(sql)
	    ) {
	        int parameterIndex = 1;

	        if (userId != null) {
	            ps.setInt(parameterIndex, userId);
	            parameterIndex++;
	        }

	        ps.setInt(parameterIndex, safeLimit);

	        try (ResultSet rs = ps.executeQuery()) {
	            while (rs.next()) {
	                result.add(mapShift(rs));
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
	 * Lay cac giao dich thu/chi dang ACTIVE cua mot ca.
	 */
	public List<ShiftCashTransaction> findTransactions(int shiftId) {
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
	        PreparedStatement ps = con.prepareStatement(sql)
	    ) {
	        ps.setInt(1, shiftId);

	        try (ResultSet rs = ps.executeQuery()) {
	            while (rs.next()) {
	                result.add(mapTransaction(rs));
	            }
	        }
	    } catch (SQLException e) {
	        AppLogger.getInstance().error(
	                ErrorCode.DB_QUERY_FAIL,
	                "ShiftDAO.findTransactions - shiftId=" + shiftId,
	                e
	        );
	    }

	    return result;
	}
	
	/**
	 * Mo mot ca ban hang moi.
	 *
	 * @param userId       nhan vien mo ca
	 * @param openingCash  tien mat nhan dau ca
	 * @param openingNote  ghi chu dau ca
	 * @return ca vua duoc tao
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

	    try (Connection con =
	             DBConnection.getConnection()) {

	        con.setAutoCommit(false);

	        try {
	            /*
	             * Kiem tra user da co ca OPEN hay chua.
	             */
	            try (PreparedStatement ps =
	                     con.prepareStatement(checkSql)) {

	                ps.setInt(1, userId);

	                try (ResultSet rs = ps.executeQuery()) {
	                    if (rs.next()) {
	                        throw new SQLException(
	                                "Nhan vien da co ca dang mo",
	                                "45000"
	                        );
	                    }
	                }
	            }

	            int shiftId;

	            /*
	             * Tao ca moi.
	             */
	            try (PreparedStatement ps =
	                     con.prepareStatement(
	                             insertSql,
	                             Statement.RETURN_GENERATED_KEYS
	                     )) {

	                ps.setInt(1, userId);
	                ps.setBigDecimal(2, openingCash);

	                setNullableString(
	                        ps,
	                        3,
	                        openingNote
	                );

	                int insertedRows = ps.executeUpdate();

	                if (insertedRows != 1) {
	                    throw new SQLException(
	                            "Khong tao duoc ca moi"
	                    );
	                }

	                try (ResultSet keys =
	                         ps.getGeneratedKeys()) {

	                    if (!keys.next()) {
	                        throw new SQLException(
	                                "Khong lay duoc ShiftID vua tao"
	                        );
	                    }

	                    shiftId = keys.getInt(1);
	                }
	            }

	            /*
	             * Doc lai ca vua tao trong cung connection.
	             */
	            Shift shift = findById(
	                    con,
	                    shiftId
	            );

	            if (shift == null) {
	                throw new SQLException(
	                        "Tao ca thanh cong nhung khong doc lai duoc ca"
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
	                    "Khong the mo ca",
	                    e
	            );

	        } finally {
	            con.setAutoCommit(true);
	        }
	    }
	}
	
	/**
	 * Ghi mot khoan thu hoac chi tien mat trong ca.
	 *
	 * Chi nhan vien so huu ca dang OPEN moi duoc thao tac.
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

	    try (Connection con =
	             DBConnection.getConnection()) {

	        con.setAutoCommit(false);

	        try {
	            /*
	             * Khoa ca va kiem tra nguoi thao tac.
	             */
	            lockOwnedOpenShift(
	                    con,
	                    shiftId,
	                    actorUserId
	            );

	            /*
	             * Tao ma giao dich kho co kha nang trung.
	             *
	             * Vi du:
	             * CT-1786782345678-A1B2C3D4
	             */
	            String transactionCode =
	                    "CT-"
	                  + System.currentTimeMillis()
	                  + "-"
	                  + UUID.randomUUID()
	                        .toString()
	                        .substring(0, 8)
	                        .toUpperCase();

	            long cashTransactionId;

	            try (PreparedStatement ps =
	                     con.prepareStatement(
	                             insertSql,
	                             Statement.RETURN_GENERATED_KEYS
	                     )) {

	                ps.setString(1, transactionCode);
	                ps.setInt(2, shiftId);
	                ps.setString(3, type);
	                ps.setBigDecimal(4, amount);
	                ps.setString(5, reason);
	                ps.setInt(6, actorUserId);

	                int insertedRows = ps.executeUpdate();

	                if (insertedRows != 1) {
	                    throw new SQLException(
	                            "Khong them duoc giao dich quy"
	                    );
	                }

	                try (ResultSet keys =
	                         ps.getGeneratedKeys()) {

	                    if (!keys.next()) {
	                        throw new SQLException(
	                                "Khong lay duoc ma giao dich quy"
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
	                        "Tao giao dich thanh cong nhung khong doc lai duoc"
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
	                    "Khong the ghi giao dich quy",
	                    e
	            );

	        } finally {
	            con.setAutoCommit(true);
	        }
	    }
	}
	
	/**
	 * Tinh so tien mat cua ca tai thoi diem hien tai.
	 */
	public ShiftCashSummary calculateCashSummary(
	        int shiftId
	) throws SQLException {

	    try (Connection con =
	             DBConnection.getConnection()) {

	        return calculateCashSummary(
	                con,
	                shiftId
	        );
	    }
	}
	
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

	    try (PreparedStatement ps =
	             con.prepareStatement(sql)) {

	        ps.setInt(1, shiftId);

	        try (ResultSet rs = ps.executeQuery()) {
	            if (!rs.next()) {
	                throw new SQLException(
	                        "Khong tim thay ca #" + shiftId,
	                        "45000"
	                );
	            }

	            int ownerUserId =
	                    rs.getInt("UserID");

	            String status =
	                    rs.getString("Status");

	            if (ownerUserId != actorUserId) {
	                throw new SQLException(
	                        "Chi nhan vien so huu ca "
	                      + "moi duoc thao tac",
	                        "45000"
	                );
	            }

	            if (!"OPEN".equals(status)) {
	                throw new SQLException(
	                        "Ca da dong, khong the thao tac",
	                        "45000"
	                );
	            }
	        }
	    }
	}
	
	/**
	 * Dong ca va luu ket qua doi soat.
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
	          + "Status = 'CLOSED', "
	          + "ExpectedCash = ?, "
	          + "CountedCash = ?, "
	          + "CashDifference = ?, "
	          + "ClosingNote = ?, "
	          + "ClosedBy = ? "
	          + "WHERE ShiftID = ? "
	          + "  AND Status = 'OPEN'";

	    try (Connection con =
	             DBConnection.getConnection()) {

	        con.setAutoCommit(false);

	        try {
	            /*
	             * Khoa ca de khong co giao dich quy khac
	             * chen vao trong luc dang dong ca.
	             */
	            lockOwnedOpenShift(
	                    con,
	                    shiftId,
	                    actorUserId
	            );

	            /*
	             * Tinh lai tien he thong ngay trong transaction.
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
	             * Neu co chenh lech thi bat buoc giai trinh.
	             */
	            if (
	                difference.signum() != 0
	                && (
	                    closingNote == null
	                    || closingNote.isBlank()
	                )
	            ) {
	                throw new SQLException(
	                        "Phai nhap giai trinh khi tien "
	                      + "kiem thuc te bi chenh lech",
	                        "45000"
	                );
	            }

	            try (PreparedStatement ps =
	                     con.prepareStatement(updateSql)) {

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
	                            "Ca da duoc dong boi tien trinh khac"
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
	                        "Dong ca thanh cong nhung "
	                      + "khong doc lai duoc ca"
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
	                    "Khong the dong ca",
	                    e
	            );

	        } finally {
	            con.setAutoCommit(true);
	        }
	    }
	}
	
	private ShiftCashSummary calculateCashSummary(
	        Connection con,
	        int shiftId
	) throws SQLException {

	    String sql =
	            "SELECT "
	          + "s.OpeningCash, "

	          + "COALESCE(("
	          + " SELECT SUM(inv.TotalAmount) "
	          + " FROM Invoices inv "
	          + " WHERE inv.ShiftID = s.ShiftID "
	          + "   AND inv.Status = 'ACTIVE' "
	          + "   AND inv.PaymentMethod = 'CASH'"
	          + "), 0) AS CashSales, "

	          + "COALESCE(("
	          + " SELECT SUM(t.Amount) "
	          + " FROM ShiftCashTransactions t "
	          + " WHERE t.ShiftID = s.ShiftID "
	          + "   AND t.Status = 'ACTIVE' "
	          + "   AND t.TransactionType = 'CASH_IN'"
	          + "), 0) AS CashIn, "

	          + "COALESCE(("
	          + " SELECT SUM(t.Amount) "
	          + " FROM ShiftCashTransactions t "
	          + " WHERE t.ShiftID = s.ShiftID "
	          + "   AND t.Status = 'ACTIVE' "
	          + "   AND t.TransactionType = 'CASH_OUT'"
	          + "), 0) AS CashOut, "

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

	          + "(SELECT COUNT(*) "
	          + " FROM Invoices inv "
	          + " WHERE inv.ShiftID = s.ShiftID "
	          + "   AND inv.Status = 'ACTIVE'"
	          + ") AS InvoiceCount "

	          + "FROM Shifts s "
	          + "WHERE s.ShiftID = ?";

	    try (PreparedStatement ps =
	             con.prepareStatement(sql)) {

	        ps.setInt(1, shiftId);

	        try (ResultSet rs = ps.executeQuery()) {
	            if (!rs.next()) {
	                throw new SQLException(
	                        "Khong tim thay ca #" + shiftId
	                );
	            }

	            return new ShiftCashSummary(
	                    rs.getBigDecimal("OpeningCash"),
	                    rs.getBigDecimal("CashSales"),
	                    rs.getBigDecimal("CashIn"),
	                    rs.getBigDecimal("CashOut"),
	                    rs.getBigDecimal("CashRefunds"),
	                    rs.getInt("InvoiceCount")
	            );
	        }
	    }
	}
	
	private Shift findById(
	        Connection con,
	        int shiftId
	) throws SQLException {
	    String sql = SHIFT_SELECT
	            + "WHERE s.ShiftID = ?";

	    try (PreparedStatement ps =
	             con.prepareStatement(sql)) {

	        ps.setInt(1, shiftId);

	        try (ResultSet rs = ps.executeQuery()) {
	            if (rs.next()) {
	                return mapShift(rs);
	            }

	            return null;
	        }
	    }
	}
	
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

	    try (PreparedStatement ps =
	             con.prepareStatement(sql)) {

	        ps.setLong(
	                1,
	                cashTransactionId
	        );

	        try (ResultSet rs = ps.executeQuery()) {
	            if (rs.next()) {
	                return mapTransaction(rs);
	            }

	            return null;
	        }
	    }
	}
	
	private Shift mapShift(
	        ResultSet rs
	) throws SQLException {
	    Shift shift = new Shift();

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
	                    rs.getTimestamp("StartTime")
	            )
	    );

	    shift.setEndTime(
	            toLocalDateTime(
	                    rs.getTimestamp("EndTime")
	            )
	    );

	    shift.setStatus(
	            rs.getString("Status")
	    );

	    shift.setOpeningCash(
	            rs.getBigDecimal("OpeningCash")
	    );

	    shift.setExpectedCash(
	            rs.getBigDecimal("ExpectedCash")
	    );

	    shift.setCountedCash(
	            rs.getBigDecimal("CountedCash")
	    );

	    shift.setCashDifference(
	            rs.getBigDecimal("CashDifference")
	    );

	    shift.setOpeningNote(
	            rs.getString("OpeningNote")
	    );

	    shift.setClosingNote(
	            rs.getString("ClosingNote")
	    );

	    int closedBy = rs.getInt("ClosedBy");

	    if (rs.wasNull()) {
	        shift.setClosedBy(null);
	    } else {
	        shift.setClosedBy(closedBy);
	    }

	    shift.setClosedByName(
	            rs.getString("ClosedByName")
	    );

	    shift.setInvoiceCount(
	            rs.getInt("InvoiceCount")
	    );

	    shift.setCashSales(
	            rs.getBigDecimal("CashSales")
	    );

	    shift.setCashIn(
	            rs.getBigDecimal("CashIn")
	    );

	    shift.setCashOut(
	            rs.getBigDecimal("CashOut")
	    );

	    shift.setCashRefunds(
	            rs.getBigDecimal("CashRefunds")
	    );

	    return shift;
	}
	
	private ShiftCashTransaction mapTransaction(
	        ResultSet rs
	) throws SQLException {
	    ShiftCashTransaction transaction =
	            new ShiftCashTransaction();

	    transaction.setCashTransactionId(
	            rs.getLong("CashTransactionID")
	    );

	    transaction.setTransactionCode(
	            rs.getString("TransactionCode")
	    );

	    transaction.setShiftId(
	            rs.getInt("ShiftID")
	    );

	    transaction.setTransactionType(
	            rs.getString("TransactionType")
	    );

	    transaction.setAmount(
	            rs.getBigDecimal("Amount")
	    );

	    transaction.setReason(
	            rs.getString("Reason")
	    );

	    transaction.setCreatedBy(
	            rs.getInt("CreatedBy")
	    );

	    transaction.setCreatedByName(
	            rs.getString("CreatedByName")
	    );

	    transaction.setCreatedAt(
	            toLocalDateTime(
	                    rs.getTimestamp("CreatedAt")
	            )
	    );

	    return transaction;
	}
	
	private java.time.LocalDateTime toLocalDateTime(
	        Timestamp timestamp
	) {
	    if (timestamp == null) {
	        return null;
	    }

	    return timestamp.toLocalDateTime();
	}
	
	private void setNullableString(
	        PreparedStatement ps,
	        int parameterIndex,
	        String value
	) throws SQLException {

	    if (value == null || value.isBlank()) {
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
