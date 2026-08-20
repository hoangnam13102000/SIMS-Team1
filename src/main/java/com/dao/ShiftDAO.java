package com.dao;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.model.Shift;
import com.model.ShiftCashSummary;
import com.model.ShiftCashTransaction;
import com.model.ShiftReconciliation;
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
 * DAO không tự động mở ca khi nhân viên bán hàng. Việc mở ca, thu/chi và đóng
 * ca phải đi qua ShiftService để kiểm tra quyền và các quy tắc nghiệp vụ.
 *
 * Mọi hóa đơn tại quầy phải thuộc về một ca đang OPEN và ca đó phải thuộc đúng
 * nhân viên tạo hóa đơn.
 */
public class ShiftDAO {

	/**
	 * Truy cap du lieu ca ban hang va so quy tien mat.
	 *
	 * DAO chi chiu trach nhiem doc/ghi database. DAO khong tu kiem tra quyen nguoi
	 * dung. Quyen se duoc kiem tra tai ShiftService.
	 *
	 * Khac phien ban cu, DAO khong tu dong mo ca khi ban hang. Nhan vien phai mo
	 * ca, nhap tien dau ca va dong ca ro rang.
	 */

	/*
	 * Invoices.TotalAmount là giá trị hiện tại sau đổi/trả.
	 *
	 * Đối soát quỹ cần số tiền CASH đã thực sự thu tại thời điểm bán, vì vậy dùng
	 * Invoices.OriginalTotalAmount.
	 *
	 * CashRefunds chỉ tính các khoản: - RETURN - RefundMethod = CASH - RefundStatus
	 * = COMPLETED - thuộc đúng RefundShiftID của ca hiện tại.
	 */

	private static final String SHIFT_SELECT = "SELECT " + "s.ShiftID, " + "s.UserID, " + "u.FullName AS UserName, "
            + "s.StartTime, " + "s.EndTime, " + "s.Status, " + "s.OpeningCash, "
            + "COALESCE(rec.ExpectedCash, s.ExpectedCash) AS ExpectedCash, "
            + "COALESCE(rec.CountedCash, s.CountedCash) AS CountedCash, "
            + "COALESCE(rec.DifferenceAmount, s.CashDifference) AS CashDifference, "
            + "s.OpeningNote, " + "COALESCE(rec.ClosingNote, s.ClosingNote) AS ClosingNote, "
            + "s.ClosedBy, " + "closer.FullName AS ClosedByName, "
            + "rec.ReconciliationID, " + "rec.RevisionNo AS ReconciliationRevisionNo, "
            + "rec.Status AS ReconciliationStatus, " + "rec.SubmittedAt AS ReconciliationSubmittedAt, "
            + "rec.ReviewedBy AS ApprovedBy, " + "reviewer.FullName AS ApprovedByName, "
            + "rec.ReviewedAt AS ApprovedAt, " + "rec.ReviewNote AS ApprovalNote, "
            + "(SELECT COUNT(*) FROM Invoices inv WHERE inv.ShiftID=s.ShiftID AND inv.Status='ACTIVE') AS InvoiceCount, "
            + "COALESCE(( SELECT SUM(CASE "
            + " WHEN EXISTS (SELECT 1 FROM InvoicePayments px WHERE px.InvoiceID=inv.InvoiceID) "
            + " THEN COALESCE((SELECT SUM(p.Amount) FROM InvoicePayments p WHERE p.InvoiceID=inv.InvoiceID "
            + "   AND p.PaymentStatus='COMPLETED' AND p.PaymentMethod='CASH'),0) "
            + " WHEN inv.PaymentMethod='CASH' THEN inv.OriginalTotalAmount ELSE 0 END) "
            + " FROM Invoices inv WHERE inv.ShiftID=s.ShiftID AND inv.Status='ACTIVE'),0) AS CashSales, "
            + "COALESCE((SELECT SUM(t.Amount) FROM ShiftCashTransactions t WHERE t.ShiftID=s.ShiftID "
            + " AND t.Status='ACTIVE' AND t.TransactionType='CASH_IN'),0) AS CashIn, "
            + "COALESCE((SELECT SUM(t.Amount) FROM ShiftCashTransactions t WHERE t.ShiftID=s.ShiftID "
            + " AND t.Status='ACTIVE' AND t.TransactionType='CASH_OUT'),0) AS CashOut, "
            + "COALESCE((SELECT SUM(r.TotalValue) FROM ReturnExchanges r WHERE r.RefundShiftID=s.ShiftID "
            + " AND r.Type='RETURN' AND r.Status='APPROVED' AND r.RefundMethod='CASH' "
            + " AND r.RefundStatus='COMPLETED'),0) AS CashRefunds "
            + "FROM Shifts s "
            + "JOIN Users u ON u.UserID=s.UserID "
            + "LEFT JOIN Users closer ON closer.UserID=s.ClosedBy "
            + "LEFT JOIN ShiftReconciliations rec ON rec.ReconciliationID=("
            + " SELECT r2.ReconciliationID FROM ShiftReconciliations r2 "
            + " WHERE r2.ShiftID=s.ShiftID ORDER BY r2.RevisionNo DESC,r2.ReconciliationID DESC LIMIT 1) "
            + "LEFT JOIN Users reviewer ON reviewer.UserID=rec.ReviewedBy ";

	/**
	 * Ham tam thoi de PosPanel cu van bien dich.
	 *
	 * Luu y: ham nay KHONG con tu dong mo ca. Neu nhan vien chua co ca OPEN thi tra
	 * ve -1.
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
	 * @return doi tuong Shift neu dang co ca mo; null neu chua co ca hoac co loi
	 *         database
	 */
	public Shift findOpenShiftByUserId(int userId) {
		String sql = SHIFT_SELECT + "WHERE s.UserID = ? " + "AND s.Status = 'OPEN' " + "ORDER BY s.ShiftID DESC "
				+ "LIMIT 1";

		try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, userId);

			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return mapShift(rs);
				}

				return null;
			}
		} catch (SQLException e) {
			AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "ShiftDAO.findOpenShiftByUserId - userId=" + userId,
					e);

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
			AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "ShiftDAO.findById - shiftId=" + shiftId, e);

			return null;
		}
	}


	/**
	 * Lay danh sach ca trong khoang ngay (theo StartTime) cho man hinh giam sat QL.
	 *
	 * @param openOnly true: chi ca OPEN; false: moi trang thai
	 */
	public List<Shift> findForMonitor(java.time.LocalDate from, java.time.LocalDate to, boolean openOnly) {
		List<Shift> result = new ArrayList<>();

		StringBuilder sql = new StringBuilder(SHIFT_SELECT);
		sql.append("WHERE 1=1 ");
		if (from != null) {
			sql.append("AND s.StartTime >= ? ");
		}
		if (to != null) {
			sql.append("AND s.StartTime < ? ");
		}
		if (openOnly) {
			sql.append("AND s.Status = 'OPEN' ");
		}
		sql.append("ORDER BY s.StartTime DESC, s.ShiftID DESC");

		try (Connection con = DBConnection.getConnection();
				PreparedStatement ps = con.prepareStatement(sql.toString())) {
			int idx = 1;
			if (from != null) {
				ps.setTimestamp(idx++, Timestamp.valueOf(from.atStartOfDay()));
			}
			if (to != null) {
				ps.setTimestamp(idx++, Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
			}
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					result.add(mapShift(rs));
				}
			}
		} catch (SQLException e) {
			AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
					"ShiftDAO.findForMonitor - from=" + from + ", to=" + to, e);
		}

		return result;
	}

	/**
	 * Lay danh sach ca moi nhat.
	 *
	 * userId != null: chi xem ca cua user do. userId == null: xem ca cua tat ca
	 * nhan vien.
	 */
	public List<Shift> findRecent(Integer userId, int limit) {
		int safeLimit = Math.max(1, Math.min(limit, 200));

		String where = userId != null ? "WHERE s.UserID = ? " : "";

		String sql = SHIFT_SELECT + where + "ORDER BY s.StartTime DESC, " + "s.ShiftID DESC " + "LIMIT ?";

		List<Shift> result = new ArrayList<>();

		try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
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
			AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "ShiftDAO.findRecent - userId=" + userId, e);
		}

		return result;
	}

	/**
	 * Lay cac giao dich thu/chi dang ACTIVE cua mot ca.
	 */
	public List<ShiftCashTransaction> findTransactions(int shiftId) {
		String sql = "SELECT " + "t.CashTransactionID, " + "t.TransactionCode, " + "t.ShiftID, " + "t.TransactionType, "
				+ "t.Amount, " + "t.Reason, " + "t.CreatedBy, " + "u.FullName AS CreatedByName, " + "t.CreatedAt "
				+ "FROM ShiftCashTransactions t " + "JOIN Users u " + "  ON u.UserID = t.CreatedBy "
				+ "WHERE t.ShiftID = ? " + "  AND t.Status = 'ACTIVE' " + "ORDER BY t.CreatedAt DESC, "
				+ "t.CashTransactionID DESC";

		List<ShiftCashTransaction> result = new ArrayList<>();

		try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, shiftId);

			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					result.add(mapTransaction(rs));
				}
			}
		} catch (SQLException e) {
			AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "ShiftDAO.findTransactions - shiftId=" + shiftId, e);
		}

		return result;
	}

	/**
	 * Mo mot ca ban hang moi.
	 *
	 * @param userId      nhan vien mo ca
	 * @param openingCash tien mat nhan dau ca
	 * @param openingNote ghi chu dau ca
	 * @return ca vua duoc tao
	 */
	public Shift openShift(int userId, BigDecimal openingCash, String openingNote) throws SQLException {

		String checkSql = "SELECT ShiftID " + "FROM Shifts " + "WHERE UserID = ? " + "  AND Status = 'OPEN' "
				+ "LIMIT 1 " + "FOR UPDATE";

		String insertSql = "INSERT INTO Shifts (" + "UserID, " + "StartTime, " + "Status, " + "OpeningCash, "
				+ "OpeningNote" + ") VALUES (" + "?, " + "CURRENT_TIMESTAMP, " + "'OPEN', " + "?, " + "?" + ")";

		try (Connection con = DBConnection.getConnection()) {

			con.setAutoCommit(false);

			try {
				/*
				 * Kiem tra user da co ca OPEN hay chua.
				 */
				try (PreparedStatement ps = con.prepareStatement(checkSql)) {

					ps.setInt(1, userId);

					try (ResultSet rs = ps.executeQuery()) {
						if (rs.next()) {
							throw new SQLException("Nhan vien da co ca dang mo", "45000");
						}
					}
				}

				int shiftId;

				/*
				 * Tao ca moi.
				 */
				try (PreparedStatement ps = con.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {

					ps.setInt(1, userId);
					ps.setBigDecimal(2, openingCash);

					setNullableString(ps, 3, openingNote);

					int insertedRows = ps.executeUpdate();

					if (insertedRows != 1) {
						throw new SQLException("Khong tao duoc ca moi");
					}

					try (ResultSet keys = ps.getGeneratedKeys()) {

						if (!keys.next()) {
							throw new SQLException("Khong lay duoc ShiftID vua tao");
						}

						shiftId = keys.getInt(1);
					}
				}

				/*
				 * Doc lai ca vua tao trong cung connection.
				 */
				Shift shift = findById(con, shiftId);

				if (shift == null) {
					throw new SQLException("Tao ca thanh cong nhung khong doc lai duoc ca");
				}

				con.commit();

				return shift;

			} catch (Exception e) {
				con.rollback();

				if (e instanceof SQLException sqlException) {
					throw sqlException;
				}

				throw new SQLException("Khong the mo ca", e);

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
	public ShiftCashTransaction addCashTransaction(int shiftId, int actorUserId, String type, BigDecimal amount,
			String reason) throws SQLException {

		String insertSql = "INSERT INTO ShiftCashTransactions (" + "TransactionCode, " + "ShiftID, "
				+ "TransactionType, " + "Amount, " + "Reason, " + "CreatedBy, " + "Status" + ") VALUES ("
				+ "?, ?, ?, ?, ?, ?, 'ACTIVE'" + ")";

		try (Connection con = DBConnection.getConnection()) {

			con.setAutoCommit(false);

			try {
				/*
				 * Khoa ca va kiem tra nguoi thao tac.
				 */
				lockOwnedOpenShift(con, shiftId, actorUserId);

				/*
				 * Tao ma giao dich kho co kha nang trung.
				 *
				 * Vi du: CT-1786782345678-A1B2C3D4
				 */
				String transactionCode = "CT-" + System.currentTimeMillis() + "-"
						+ UUID.randomUUID().toString().substring(0, 8).toUpperCase();

				long cashTransactionId;

				try (PreparedStatement ps = con.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {

					ps.setString(1, transactionCode);
					ps.setInt(2, shiftId);
					ps.setString(3, type);
					ps.setBigDecimal(4, amount);
					ps.setString(5, reason);
					ps.setInt(6, actorUserId);

					int insertedRows = ps.executeUpdate();

					if (insertedRows != 1) {
						throw new SQLException("Khong them duoc giao dich quy");
					}

					try (ResultSet keys = ps.getGeneratedKeys()) {

						if (!keys.next()) {
							throw new SQLException("Khong lay duoc ma giao dich quy");
						}

						cashTransactionId = keys.getLong(1);
					}
				}

				ShiftCashTransaction transaction = findTransactionById(con, cashTransactionId);

				if (transaction == null) {
					throw new SQLException("Tao giao dich thanh cong nhung khong doc lai duoc");
				}

				con.commit();

				return transaction;

			} catch (Exception e) {
				con.rollback();

				if (e instanceof SQLException sqlException) {
					throw sqlException;
				}

				throw new SQLException("Khong the ghi giao dich quy", e);

			} finally {
				con.setAutoCommit(true);
			}
		}
	}

	/**
	 * Tinh so tien mat cua ca tai thoi diem hien tai.
	 */
	public ShiftCashSummary calculateCashSummary(int shiftId) throws SQLException {

		try (Connection con = DBConnection.getConnection()) {

			return calculateCashSummary(con, shiftId);
		}
	}

	void lockOwnedOpenShift(Connection con, int shiftId, int actorUserId) throws SQLException {

		String sql = "SELECT UserID, Status " + "FROM Shifts " + "WHERE ShiftID = ? " + "FOR UPDATE";

		try (PreparedStatement ps = con.prepareStatement(sql)) {

			ps.setInt(1, shiftId);

			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) {
					throw new SQLException("Khong tim thay ca #" + shiftId, "45000");
				}

				int ownerUserId = rs.getInt("UserID");

				String status = rs.getString("Status");

				if (ownerUserId != actorUserId) {
					throw new SQLException("Chi nhan vien so huu ca " + "moi duoc thao tac", "45000");
				}

				if (!"OPEN".equals(status)) {
					throw new SQLException("Ca da dong, khong the thao tac", "45000");
				}
			}
		}
	}

	/**
	 * Dong ca va luu ket qua doi soat.
	 */
	public Shift closeShift(int shiftId, int actorUserId, BigDecimal countedCash, String closingNote)
            throws SQLException {

        String updateSql = "UPDATE Shifts SET EndTime=CURRENT_TIMESTAMP, Status='CLOSED', "
                + "ExpectedCash=?, CountedCash=?, CashDifference=?, ClosingNote=?, ClosedBy=? "
                + "WHERE ShiftID=? AND Status='OPEN'";

        String insertReconciliation = "INSERT INTO ShiftReconciliations ("
                + "ShiftID,RevisionNo,ExpectedCash,CountedCash,DifferenceAmount,ClosingNote,Status,SubmittedBy,SubmittedAt) "
                + "VALUES (?,1,?,?,?,?, 'PENDING', ?, CURRENT_TIMESTAMP)";

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                lockOwnedOpenShift(con, shiftId, actorUserId);
                ShiftCashSummary summary = calculateCashSummary(con, shiftId);
                BigDecimal expectedCash = summary.getExpectedCash();
                BigDecimal difference = summary.differenceFrom(countedCash);

                if (difference.signum() != 0 && (closingNote == null || closingNote.isBlank())) {
                    throw new SQLException("Phai nhap giai trinh khi tien kiem thuc te bi chenh lech", "45000");
                }

                try (PreparedStatement ps = con.prepareStatement(updateSql)) {
                    ps.setBigDecimal(1, expectedCash);
                    ps.setBigDecimal(2, countedCash);
                    ps.setBigDecimal(3, difference);
                    setNullableString(ps, 4, closingNote);
                    ps.setInt(5, actorUserId);
                    ps.setInt(6, shiftId);
                    if (ps.executeUpdate() != 1) {
                        throw new SQLException("Ca da duoc dong boi tien trinh khac", "45000");
                    }
                }

                try (PreparedStatement ps = con.prepareStatement(insertReconciliation)) {
                    ps.setInt(1, shiftId);
                    ps.setBigDecimal(2, expectedCash);
                    ps.setBigDecimal(3, countedCash);
                    ps.setBigDecimal(4, difference);
                    setNullableString(ps, 5, closingNote);
                    ps.setInt(6, actorUserId);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE HeldCarts SET Status='EXPIRED', ExpiredAt=CURRENT_TIMESTAMP "
                        + "WHERE ShiftID=? AND Status='HELD'")) {
                    ps.setInt(1, shiftId);
                    ps.executeUpdate();
                }

                Shift closedShift = findById(con, shiftId);
                if (closedShift == null) {
                    throw new SQLException("Dong ca thanh cong nhung khong doc lai duoc ca");
                }
                con.commit();
                return closedShift;
            } catch (Exception e) {
                con.rollback();
                if (e instanceof SQLException sqlException) throw sqlException;
                throw new SQLException("Khong the dong ca", e);
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

	ShiftCashSummary calculateCashSummary(Connection con, int shiftId) throws SQLException {

		String sql = "SELECT " + "s.OpeningCash, "

		// Tổng tiền CASH gốc đã thu khi bán; ưu tiên InvoicePayments để split payment không làm lệch két.
				+ "COALESCE(( SELECT SUM(CASE "
				+ " WHEN EXISTS (SELECT 1 FROM InvoicePayments px WHERE px.InvoiceID=inv.InvoiceID) "
				+ " THEN COALESCE((SELECT SUM(p.Amount) FROM InvoicePayments p WHERE p.InvoiceID=inv.InvoiceID "
				+ "   AND p.PaymentStatus='COMPLETED' AND p.PaymentMethod='CASH'),0) "
				+ " WHEN inv.PaymentMethod='CASH' THEN inv.OriginalTotalAmount ELSE 0 END) "
				+ " FROM Invoices inv WHERE inv.ShiftID=s.ShiftID AND inv.Status='ACTIVE'), 0) AS CashSales, "

				// Các khoản thu tiền mặt thủ công
				+ "COALESCE((" + " SELECT SUM(t.Amount) " + " FROM ShiftCashTransactions t "
				+ " WHERE t.ShiftID = s.ShiftID " + "   AND t.Status = 'ACTIVE' "
				+ "   AND t.TransactionType = 'CASH_IN'" + "), 0) AS CashIn, "

				// Các khoản chi tiền mặt thủ công
				+ "COALESCE((" + " SELECT SUM(t.Amount) " + " FROM ShiftCashTransactions t "
				+ " WHERE t.ShiftID = s.ShiftID " + "   AND t.Status = 'ACTIVE' "
				+ "   AND t.TransactionType = 'CASH_OUT'" + "), 0) AS CashOut, "

				// Refund CASH thực sự đã hoàn
				+ "COALESCE((" + " SELECT SUM(r.TotalValue) " + " FROM ReturnExchanges r "
				+ " WHERE r.RefundShiftID = s.ShiftID " + "   AND r.Type = 'RETURN' " + "   AND r.Status = 'APPROVED' "
				+ "   AND r.RefundMethod = 'CASH' " + "   AND r.RefundStatus = 'COMPLETED'" + "), 0) AS CashRefunds, "

				// Tổng hóa đơn ACTIVE trong ca
				+ "(SELECT COUNT(*) " + " FROM Invoices inv " + " WHERE inv.ShiftID = s.ShiftID "
				+ "   AND inv.Status = 'ACTIVE'" + ") AS InvoiceCount "

				+ "FROM Shifts s " + "WHERE s.ShiftID = ?";

		try (PreparedStatement ps = con.prepareStatement(sql)) {

			ps.setInt(1, shiftId);

			try (ResultSet rs = ps.executeQuery()) {

				if (!rs.next()) {
					throw new SQLException("Khong tim thay ca #" + shiftId);
				}

				return new ShiftCashSummary(rs.getBigDecimal("OpeningCash"), rs.getBigDecimal("CashSales"),
						rs.getBigDecimal("CashIn"), rs.getBigDecimal("CashOut"), rs.getBigDecimal("CashRefunds"),
						rs.getInt("InvoiceCount"));
			}
		}
	}

	private Shift findById(Connection con, int shiftId) throws SQLException {
		String sql = SHIFT_SELECT + "WHERE s.ShiftID = ?";

		try (PreparedStatement ps = con.prepareStatement(sql)) {

			ps.setInt(1, shiftId);

			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return mapShift(rs);
				}

				return null;
			}
		}
	}

	private ShiftCashTransaction findTransactionById(Connection con, long cashTransactionId) throws SQLException {

		String sql = "SELECT " + "t.CashTransactionID, " + "t.TransactionCode, " + "t.ShiftID, " + "t.TransactionType, "
				+ "t.Amount, " + "t.Reason, " + "t.CreatedBy, " + "u.FullName AS CreatedByName, " + "t.CreatedAt "
				+ "FROM ShiftCashTransactions t " + "JOIN Users u " + "  ON u.UserID = t.CreatedBy "
				+ "WHERE t.CashTransactionID = ?";

		try (PreparedStatement ps = con.prepareStatement(sql)) {

			ps.setLong(1, cashTransactionId);

			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return mapTransaction(rs);
				}

				return null;
			}
		}
	}


	/** Duyet lan doi soat PENDING moi nhat; Shift van CLOSED. */
    public Shift approveShift(int shiftId, int actorUserId, String approvalNote) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                long reconciliationId = lockLatestPendingReconciliation(con, shiftId);
                String sql = "UPDATE ShiftReconciliations SET Status='APPROVED', ReviewedBy=?, "
                        + "ReviewedAt=CURRENT_TIMESTAMP, ReviewNote=? "
                        + "WHERE ReconciliationID=? AND Status='PENDING'";
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setInt(1, actorUserId);
                    setNullableString(ps, 2, approvalNote);
                    ps.setLong(3, reconciliationId);
                    if (ps.executeUpdate() != 1) {
                        throw new SQLException("Doi soat da duoc xu ly boi nguoi khac", "45000");
                    }
                }
                updateLegacyReviewSnapshot(con, shiftId, actorUserId, approvalNote);
                Shift shift = findById(con, shiftId);
                if (shift == null) throw new SQLException("Duyet thanh cong nhung khong doc lai duoc ca");
                con.commit();
                return shift;
            } catch (Exception e) {
                con.rollback();
                if (e instanceof SQLException sqlException) throw sqlException;
                throw new SQLException("Khong the duyet ca", e);
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    /** Tu choi lan doi soat PENDING moi nhat; ca KHONG duoc mo lai. */
    public Shift rejectShift(int shiftId, int actorUserId, String rejectionNote) throws SQLException {
        if (rejectionNote == null || rejectionNote.isBlank()) {
            throw new SQLException("Phai nhap ly do tu choi", "45000");
        }
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                long reconciliationId = lockLatestPendingReconciliation(con, shiftId);
                String sql = "UPDATE ShiftReconciliations SET Status='REJECTED', ReviewedBy=?, "
                        + "ReviewedAt=CURRENT_TIMESTAMP, ReviewNote=? "
                        + "WHERE ReconciliationID=? AND Status='PENDING'";
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setInt(1, actorUserId);
                    ps.setString(2, rejectionNote.trim());
                    ps.setLong(3, reconciliationId);
                    if (ps.executeUpdate() != 1) {
                        throw new SQLException("Doi soat da duoc xu ly boi nguoi khac", "45000");
                    }
                }
                updateLegacyReviewSnapshot(con, shiftId, actorUserId, rejectionNote.trim());
                Shift shift = findById(con, shiftId);
                if (shift == null) throw new SQLException("Tu choi thanh cong nhung khong doc lai duoc ca");
                con.commit();
                return shift;
            } catch (Exception e) {
                con.rollback();
                if (e instanceof SQLException sqlException) throw sqlException;
                throw new SQLException("Khong the tu choi ca", e);
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    /** Tao revision moi sau khi quan ly yeu cau kiem lai. */
    public Shift resubmitReconciliation(int shiftId, int actorUserId, BigDecimal countedCash, String closingNote)
            throws SQLException {
        String lockSql = "SELECT s.UserID,s.Status,r.RevisionNo,r.ExpectedCash,r.Status AS RecStatus "
                + "FROM Shifts s JOIN ShiftReconciliations r ON r.ReconciliationID=("
                + " SELECT r2.ReconciliationID FROM ShiftReconciliations r2 WHERE r2.ShiftID=s.ShiftID "
                + " ORDER BY r2.RevisionNo DESC,r2.ReconciliationID DESC LIMIT 1) "
                + "WHERE s.ShiftID=? FOR UPDATE";
        String insertSql = "INSERT INTO ShiftReconciliations (ShiftID,RevisionNo,ExpectedCash,CountedCash,"
                + "DifferenceAmount,ClosingNote,Status,SubmittedBy,SubmittedAt) "
                + "VALUES (?,?,?,?,?,?, 'PENDING', ?, CURRENT_TIMESTAMP)";

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                int nextRevision;
                BigDecimal expectedCash;
                try (PreparedStatement ps = con.prepareStatement(lockSql)) {
                    ps.setInt(1, shiftId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Khong tim thay ca hoac doi soat", "45000");
                        if (rs.getInt("UserID") != actorUserId) {
                            throw new SQLException("Chi nhan vien so huu ca moi duoc gui lai doi soat", "45000");
                        }
                        if (!"CLOSED".equalsIgnoreCase(rs.getString("Status"))) {
                            throw new SQLException("Ca phai da dong truoc khi gui lai doi soat", "45000");
                        }
                        if (!ShiftReconciliation.STATUS_REJECTED.equalsIgnoreCase(rs.getString("RecStatus"))) {
                            throw new SQLException("Chi gui lai duoc doi soat bi yeu cau kiem lai", "45000");
                        }
                        nextRevision = rs.getInt("RevisionNo") + 1;
                        expectedCash = rs.getBigDecimal("ExpectedCash");
                    }
                }

                BigDecimal difference = countedCash.subtract(expectedCash);
                if (difference.signum() != 0 && (closingNote == null || closingNote.isBlank())) {
                    throw new SQLException("Phai nhap giai trinh khi tien kiem thuc te bi chenh lech", "45000");
                }

                try (PreparedStatement ps = con.prepareStatement(insertSql)) {
                    ps.setInt(1, shiftId);
                    ps.setInt(2, nextRevision);
                    ps.setBigDecimal(3, expectedCash);
                    ps.setBigDecimal(4, countedCash);
                    ps.setBigDecimal(5, difference);
                    setNullableString(ps, 6, closingNote);
                    ps.setInt(7, actorUserId);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE Shifts SET CountedCash=?,CashDifference=?,ClosingNote=?,"
                        + "ApprovedBy=NULL,ApprovedAt=NULL,ApprovalNote=NULL WHERE ShiftID=?")) {
                    ps.setBigDecimal(1, countedCash);
                    ps.setBigDecimal(2, difference);
                    setNullableString(ps, 3, closingNote);
                    ps.setInt(4, shiftId);
                    ps.executeUpdate();
                }

                Shift shift = findById(con, shiftId);
                if (shift == null) throw new SQLException("Gui lai thanh cong nhung khong doc lai duoc ca");
                con.commit();
                return shift;
            } catch (Exception e) {
                con.rollback();
                if (e instanceof SQLException sqlException) throw sqlException;
                throw new SQLException("Khong the gui lai doi soat", e);
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    public List<ShiftReconciliation> findReconciliations(int shiftId) {
        List<ShiftReconciliation> result = new ArrayList<>();
        String sql = "SELECT r.ReconciliationID,r.ShiftID,r.RevisionNo,r.ExpectedCash,r.CountedCash,"
                + "r.DifferenceAmount,r.ClosingNote,r.Status,r.SubmittedBy,su.FullName AS SubmittedByName,"
                + "r.SubmittedAt,r.ReviewedBy,ru.FullName AS ReviewedByName,r.ReviewedAt,r.ReviewNote "
                + "FROM ShiftReconciliations r JOIN Users su ON su.UserID=r.SubmittedBy "
                + "LEFT JOIN Users ru ON ru.UserID=r.ReviewedBy WHERE r.ShiftID=? "
                + "ORDER BY r.RevisionNo DESC,r.ReconciliationID DESC";
        try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, shiftId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapReconciliation(rs));
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "ShiftDAO.findReconciliations - shiftId=" + shiftId, e);
        }
        return result;
    }

    private long lockLatestPendingReconciliation(Connection con, int shiftId) throws SQLException {
        String sql = "SELECT s.Status AS ShiftStatus,r.ReconciliationID,r.Status AS RecStatus "
                + "FROM Shifts s JOIN ShiftReconciliations r ON r.ReconciliationID=("
                + " SELECT r2.ReconciliationID FROM ShiftReconciliations r2 WHERE r2.ShiftID=s.ShiftID "
                + " ORDER BY r2.RevisionNo DESC,r2.ReconciliationID DESC LIMIT 1) "
                + "WHERE s.ShiftID=? FOR UPDATE";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, shiftId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Khong tim thay ca/doi soat #" + shiftId, "45000");
                if (!"CLOSED".equalsIgnoreCase(rs.getString("ShiftStatus"))) {
                    throw new SQLException("Chi duyet doi soat cua ca da dong", "45000");
                }
                if (!ShiftReconciliation.STATUS_PENDING.equalsIgnoreCase(rs.getString("RecStatus"))) {
                    throw new SQLException("Chi duyet/tu choi duoc doi soat dang cho xu ly", "45000");
                }
                return rs.getLong("ReconciliationID");
            }
        }
    }

    private void updateLegacyReviewSnapshot(Connection con, int shiftId, int actorUserId, String note)
            throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE Shifts SET ApprovedBy=?,ApprovedAt=CURRENT_TIMESTAMP,ApprovalNote=? WHERE ShiftID=?")) {
            ps.setInt(1, actorUserId);
            setNullableString(ps, 2, note);
            ps.setInt(3, shiftId);
            ps.executeUpdate();
        }
    }

    private ShiftReconciliation mapReconciliation(ResultSet rs) throws SQLException {
        ShiftReconciliation r = new ShiftReconciliation();
        r.setReconciliationId(rs.getLong("ReconciliationID"));
        r.setShiftId(rs.getInt("ShiftID"));
        r.setRevisionNo(rs.getInt("RevisionNo"));
        r.setExpectedCash(rs.getBigDecimal("ExpectedCash"));
        r.setCountedCash(rs.getBigDecimal("CountedCash"));
        r.setDifference(rs.getBigDecimal("DifferenceAmount"));
        r.setClosingNote(rs.getString("ClosingNote"));
        r.setStatus(rs.getString("Status"));
        r.setSubmittedBy(rs.getInt("SubmittedBy"));
        r.setSubmittedByName(rs.getString("SubmittedByName"));
        r.setSubmittedAt(toLocalDateTime(rs.getTimestamp("SubmittedAt")));
        int reviewedBy = rs.getInt("ReviewedBy");
        r.setReviewedBy(rs.wasNull() ? null : reviewedBy);
        r.setReviewedByName(rs.getString("ReviewedByName"));
        r.setReviewedAt(toLocalDateTime(rs.getTimestamp("ReviewedAt")));
        r.setReviewNote(rs.getString("ReviewNote"));
        return r;
    }

	private Shift mapShift(ResultSet rs) throws SQLException {
		Shift shift = new Shift();

		shift.setShiftId(rs.getInt("ShiftID"));

		shift.setUserId(rs.getInt("UserID"));

		shift.setUserName(rs.getString("UserName"));

		shift.setStartTime(toLocalDateTime(rs.getTimestamp("StartTime")));

		shift.setEndTime(toLocalDateTime(rs.getTimestamp("EndTime")));

		shift.setStatus(rs.getString("Status"));

		shift.setOpeningCash(rs.getBigDecimal("OpeningCash"));

		shift.setExpectedCash(rs.getBigDecimal("ExpectedCash"));

		shift.setCountedCash(rs.getBigDecimal("CountedCash"));

		shift.setCashDifference(rs.getBigDecimal("CashDifference"));

		shift.setOpeningNote(rs.getString("OpeningNote"));

		shift.setClosingNote(rs.getString("ClosingNote"));

		int closedBy = rs.getInt("ClosedBy");

		if (rs.wasNull()) {
			shift.setClosedBy(null);
		} else {
			shift.setClosedBy(closedBy);
		}

		shift.setClosedByName(rs.getString("ClosedByName"));

        long reconciliationId = rs.getLong("ReconciliationID");
        shift.setReconciliationId(rs.wasNull() ? null : reconciliationId);
        int revisionNo = rs.getInt("ReconciliationRevisionNo");
        shift.setReconciliationRevisionNo(rs.wasNull() ? null : revisionNo);
        shift.setReconciliationStatus(rs.getString("ReconciliationStatus"));
        shift.setReconciliationSubmittedAt(toLocalDateTime(rs.getTimestamp("ReconciliationSubmittedAt")));

        int approvedBy = rs.getInt("ApprovedBy");
        shift.setApprovedBy(rs.wasNull() ? null : approvedBy);
        shift.setApprovedByName(rs.getString("ApprovedByName"));
        shift.setApprovedAt(toLocalDateTime(rs.getTimestamp("ApprovedAt")));
        shift.setApprovalNote(rs.getString("ApprovalNote"));

		shift.setInvoiceCount(rs.getInt("InvoiceCount"));

		shift.setCashSales(rs.getBigDecimal("CashSales"));

		shift.setCashIn(rs.getBigDecimal("CashIn"));

		shift.setCashOut(rs.getBigDecimal("CashOut"));

		shift.setCashRefunds(rs.getBigDecimal("CashRefunds"));

		return shift;
	}

	private ShiftCashTransaction mapTransaction(ResultSet rs) throws SQLException {
		ShiftCashTransaction transaction = new ShiftCashTransaction();

		transaction.setCashTransactionId(rs.getLong("CashTransactionID"));

		transaction.setTransactionCode(rs.getString("TransactionCode"));

		transaction.setShiftId(rs.getInt("ShiftID"));

		transaction.setTransactionType(rs.getString("TransactionType"));

		transaction.setAmount(rs.getBigDecimal("Amount"));

		transaction.setReason(rs.getString("Reason"));

		transaction.setCreatedBy(rs.getInt("CreatedBy"));

		transaction.setCreatedByName(rs.getString("CreatedByName"));

		transaction.setCreatedAt(toLocalDateTime(rs.getTimestamp("CreatedAt")));

		return transaction;
	}

	private java.time.LocalDateTime toLocalDateTime(Timestamp timestamp) {
		if (timestamp == null) {
			return null;
		}

		return timestamp.toLocalDateTime();
	}

	private void setNullableString(PreparedStatement ps, int parameterIndex, String value) throws SQLException {

		if (value == null || value.isBlank()) {
			ps.setNull(parameterIndex, Types.VARCHAR);

			return;
		}

		ps.setString(parameterIndex, value.trim());
	}

	private Integer findOpenShiftId(int userId) {
		String sql = "SELECT ShiftID FROM Shifts WHERE UserID = ? AND Status = 'OPEN' ORDER BY ShiftID DESC LIMIT 1";
		try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, userId);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? rs.getInt(1) : null;
			}
		} catch (SQLException e) {
			AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "ShiftDAO.findOpenShiftId - userId=" + userId, e);
			return null;
		}
	}

	private int openNewShift(int userId) {
		String sql = "INSERT INTO Shifts (UserID, StartTime, Status) VALUES (?, CURRENT_TIMESTAMP, 'OPEN')";
		try (Connection con = DBConnection.getConnection();
				PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
			ps.setInt(1, userId);
			ps.executeUpdate();
			try (ResultSet keys = ps.getGeneratedKeys()) {
				if (keys.next())
					return keys.getInt(1);
			}
		} catch (SQLException e) {
			AppLogger.getInstance().error(ErrorCode.DB_INSERT_FAIL, "ShiftDAO.openNewShift - userId=" + userId, e);
		}
		return -1;
	}
}