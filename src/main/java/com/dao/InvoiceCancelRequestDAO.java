package com.dao;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.model.InvoiceCancelRequest;
import com.utils.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

/**
 * DAO cho workflow yeu cau huy hoa don.
 *
 * Luu y nghiep vu: - SALES_STAFF chi duoc tao request cho hoa don cua chinh
 * minh. - Chi CASH duoc di qua luong cancelInvoice hien tai; payment dien tu
 * phai refund. - Don online khong huy o InvoicePanel. - Khong tao request neu
 * hoa don da co doi/tra PENDING/APPROVED. - Mot hoa don chi co toi da mot
 * request PENDING/PROCESSING tai mot thoi diem.
 */
public class InvoiceCancelRequestDAO {

	private static final String SELECT_COLUMNS = "SELECT r.RequestID, r.InvoiceID, inv.InvoiceCode, r.RequestedBy, "
			+ "req.FullName AS RequestedByName, r.Reason, r.Status, r.RequestedAt, "
			+ "r.ReviewedBy, rev.FullName AS ReviewedByName, r.ReviewedAt, r.ReviewNote ";

	private static final String FROM_JOIN = "FROM InvoiceCancelRequests r "
			+ "JOIN Invoices inv ON inv.InvoiceID = r.InvoiceID " + "LEFT JOIN Users req ON req.UserID = r.RequestedBy "
			+ "LEFT JOIN Users rev ON rev.UserID = r.ReviewedBy ";

	public String createRequest(int invoiceId, int requestedBy, String reason) {
		if (invoiceId <= 0 || requestedBy <= 0) {
			return "Thông tin yêu cầu hủy không hợp lệ.";
		}
		if (reason == null || reason.isBlank()) {
			return "Vui lòng nhập lý do hủy hóa đơn.";
		}
		String cleanReason = reason.trim();
		if (cleanReason.length() > 500) {
			return "Lý do hủy tối đa 500 ký tự.";
		}

		String lockInvoiceSql = "SELECT inv.Status, inv.PaymentMethod, inv.CreatedBy, "
				+ "DATE(inv.CreatedAt) = CURRENT_DATE AS IsToday, " + "COALESCE(s.Status, 'CLOSED') AS ShiftStatus, "
				+ "EXISTS (SELECT 1 FROM Orders o WHERE o.InvoiceID = inv.InvoiceID) AS IsOnline, "
				+ "EXISTS (SELECT 1 FROM ReturnExchanges re WHERE re.InvoiceID = inv.InvoiceID "
				+ "        AND re.Status IN ('PENDING','APPROVED')) AS HasActiveReturn "
				+ "FROM Invoices inv LEFT JOIN Shifts s ON s.ShiftID = inv.ShiftID "
				+ "WHERE inv.InvoiceID = ? FOR UPDATE";

		String activeRequestSql = "SELECT RequestID FROM InvoiceCancelRequests "
				+ "WHERE InvoiceID = ? AND Status IN ('PENDING','PROCESSING') "
				+ "ORDER BY RequestID DESC LIMIT 1 FOR UPDATE";

		String insertSql = "INSERT INTO InvoiceCancelRequests "
				+ "(InvoiceID, RequestedBy, Reason, Status, RequestedAt) "
				+ "VALUES (?, ?, ?, 'PENDING', CURRENT_TIMESTAMP)";

		try (Connection con = DBConnection.getConnection()) {
			con.setAutoCommit(false);
			try {
				String status;
				String paymentMethod;
				int createdBy;
				boolean isToday;
				String shiftStatus;
				boolean online;
				boolean hasActiveReturn;

				try (PreparedStatement ps = con.prepareStatement(lockInvoiceSql)) {
					ps.setInt(1, invoiceId);
					try (ResultSet rs = ps.executeQuery()) {
						if (!rs.next()) {
							con.rollback();
							return "Không tìm thấy hóa đơn.";
						}
						status = rs.getString("Status");
						paymentMethod = rs.getString("PaymentMethod");
						createdBy = rs.getInt("CreatedBy");
						isToday = rs.getBoolean("IsToday");
						shiftStatus = rs.getString("ShiftStatus");
						online = rs.getBoolean("IsOnline");
						hasActiveReturn = rs.getBoolean("HasActiveReturn");
					}
				}

				if (createdBy != requestedBy) {
					con.rollback();
					return "Bạn chỉ được gửi yêu cầu hủy cho hóa đơn do chính mình tạo.";
				}
				if (!"ACTIVE".equalsIgnoreCase(status)) {
					con.rollback();
					return "Hóa đơn không còn ở trạng thái có thể yêu cầu hủy.";
				}
				if (!isToday || !"OPEN".equalsIgnoreCase(shiftStatus)) {
					con.rollback();
					return "Chỉ được gửi yêu cầu hủy cho hóa đơn trong ngày và ca bán hàng vẫn đang mở.";
				}
				if (online) {
					con.rollback();
					return "Hóa đơn này thuộc đơn hàng online. Vui lòng xử lý từ trang Quản lý đơn hàng.";
				}
				if (!"CASH".equalsIgnoreCase(paymentMethod)) {
					con.rollback();
					return "Hóa đơn thanh toán bằng " + paymentMethod
							+ " không thể hủy trực tiếp. Vui lòng dùng Đổi / Trả hàng để hoàn tiền đúng phương thức.";
				}
				if (hasActiveReturn) {
					con.rollback();
					return "Hóa đơn đã có yêu cầu đổi/trả đang xử lý nên không thể gửi yêu cầu hủy.";
				}

				try (PreparedStatement ps = con.prepareStatement(activeRequestSql)) {
					ps.setInt(1, invoiceId);
					try (ResultSet rs = ps.executeQuery()) {
						if (rs.next()) {
							con.rollback();
							return "Hóa đơn đã có yêu cầu hủy đang chờ xử lý.";
						}
					}
				}

				try (PreparedStatement ps = con.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
					ps.setInt(1, invoiceId);
					ps.setInt(2, requestedBy);
					ps.setString(3, cleanReason);
					ps.executeUpdate();
				}

				con.commit();
				return null;
			} catch (SQLException e) {
				con.rollback();
				// Unique ActiveInvoiceID trong migration la lop bao ve race-condition thu 2.
				if ("23000".equals(e.getSQLState())) {
					return "Hóa đơn đã có yêu cầu hủy đang chờ xử lý.";
				}
				throw e;
			} finally {
				con.setAutoCommit(true);
			}
		} catch (SQLException e) {
			AppLogger.getInstance().error(ErrorCode.DB_INSERT_FAIL,
					"InvoiceCancelRequestDAO.createRequest - invoiceId=" + invoiceId, e);
			return "Không tạo được yêu cầu hủy hóa đơn.";
		}
	}

	public InvoiceCancelRequest findLatestByInvoiceId(int invoiceId) {
		String sql = SELECT_COLUMNS + FROM_JOIN + "WHERE r.InvoiceID = ? ORDER BY r.RequestID DESC LIMIT 1";
		return findOne(sql, invoiceId);
	}

	public InvoiceCancelRequest findActiveByInvoiceId(int invoiceId) {
		reconcileForInvoice(invoiceId);
		String sql = SELECT_COLUMNS + FROM_JOIN + "WHERE r.InvoiceID = ? AND r.Status IN ('PENDING','PROCESSING') "
				+ "ORDER BY r.RequestID DESC LIMIT 1";
		return findOne(sql, invoiceId);
	}

	public InvoiceCancelRequest findById(int requestId) {
		String sql = SELECT_COLUMNS + FROM_JOIN + "WHERE r.RequestID = ? LIMIT 1";
		return findOne(sql, requestId);
	}

	/**
	 * Atomically claim 1 request PENDING cho 1 reviewer. Neu 2 manager bam cung
	 * luc, chi 1 UPDATE co the thanh cong.
	 */
	public InvoiceCancelRequest claimPendingForReview(int requestId, int reviewerId) {
		String updateSql = "UPDATE InvoiceCancelRequests SET Status='PROCESSING', ReviewedBy=?, "
				+ "ReviewedAt=CURRENT_TIMESTAMP, ReviewNote=NULL " + "WHERE RequestID=? AND Status='PENDING'";
		try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(updateSql)) {
			ps.setInt(1, reviewerId);
			ps.setInt(2, requestId);
			if (ps.executeUpdate() != 1) {
				return null;
			}
			return findById(requestId);
		} catch (SQLException e) {
			AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL,
					"InvoiceCancelRequestDAO.claimPendingForReview - requestId=" + requestId, e);
			return null;
		}
	}

	public boolean markApproved(int requestId, int reviewerId, String reviewNote) {
		String sql = "UPDATE InvoiceCancelRequests SET Status='APPROVED', ReviewedBy=?, "
				+ "ReviewedAt=CURRENT_TIMESTAMP, ReviewNote=? "
				+ "WHERE RequestID=? AND Status='PROCESSING' AND ReviewedBy=?";
		try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, reviewerId);
			ps.setString(2, normalizeNote(reviewNote));
			ps.setInt(3, requestId);
			ps.setInt(4, reviewerId);
			return ps.executeUpdate() == 1;
		} catch (SQLException e) {
			AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL,
					"InvoiceCancelRequestDAO.markApproved - requestId=" + requestId, e);
			return false;
		}
	}

	public boolean rejectPending(int requestId, int reviewerId, String reviewNote) {
		String sql = "UPDATE InvoiceCancelRequests SET Status='REJECTED', ReviewedBy=?, "
				+ "ReviewedAt=CURRENT_TIMESTAMP, ReviewNote=? " + "WHERE RequestID=? AND Status='PENDING'";
		try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, reviewerId);
			ps.setString(2, normalizeNote(reviewNote));
			ps.setInt(3, requestId);
			return ps.executeUpdate() == 1;
		} catch (SQLException e) {
			AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL,
					"InvoiceCancelRequestDAO.rejectPending - requestId=" + requestId, e);
			return false;
		}
	}

	/**
	 * Neu manager bam Duyet nhung cancelInvoice that bai, tra request ve PENDING de
	 * co the sua nguyen nhan va thu lai. Khong de request ket o PROCESSING.
	 */
	public boolean releaseProcessing(int requestId, int reviewerId, String errorNote) {
		String sql = "UPDATE InvoiceCancelRequests SET Status='PENDING', ReviewedBy=NULL, ReviewedAt=NULL, ReviewNote=? "
				+ "WHERE RequestID=? AND Status='PROCESSING' AND ReviewedBy=?";
		try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setString(1, normalizeNote(errorNote));
			ps.setInt(2, requestId);
			ps.setInt(3, reviewerId);
			return ps.executeUpdate() == 1;
		} catch (SQLException e) {
			AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL,
					"InvoiceCancelRequestDAO.releaseProcessing - requestId=" + requestId, e);
			return false;
		}
	}

	/**
	 * Recovery nhe cho truong hop app dung dung luc hoa don da CANCELLED nhung
	 * request chua kip chuyen tu PROCESSING -> APPROVED, hoac request PROCESSING bi
	 * treo qua 10 phut trong khi invoice van ACTIVE.
	 */
	public void reconcileForInvoice(int invoiceId) {
		String approveRecovered = "UPDATE InvoiceCancelRequests r " + "JOIN Invoices i ON i.InvoiceID=r.InvoiceID "
				+ "SET r.Status='APPROVED', r.ReviewedAt=COALESCE(r.ReviewedAt, CURRENT_TIMESTAMP), "
				+ "r.ReviewNote=COALESCE(NULLIF(r.ReviewNote,''), 'Tự đối soát: hóa đơn đã được hủy thành công.') "
				+ "WHERE r.InvoiceID=? AND r.Status='PROCESSING' AND i.Status='CANCELLED'";

		String releaseStale = "UPDATE InvoiceCancelRequests r " + "JOIN Invoices i ON i.InvoiceID=r.InvoiceID "
				+ "SET r.Status='PENDING', r.ReviewedBy=NULL, r.ReviewedAt=NULL, "
				+ "r.ReviewNote='Tự khôi phục yêu cầu bị treo khi xử lý.' "
				+ "WHERE r.InvoiceID=? AND r.Status='PROCESSING' AND i.Status='ACTIVE' "
				+ "AND r.ReviewedAt IS NOT NULL "
				+ "AND r.ReviewedAt < DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 10 MINUTE)";

		try (Connection con = DBConnection.getConnection()) {
			try (PreparedStatement ps = con.prepareStatement(approveRecovered)) {
				ps.setInt(1, invoiceId);
				ps.executeUpdate();
			}
			try (PreparedStatement ps = con.prepareStatement(releaseStale)) {
				ps.setInt(1, invoiceId);
				ps.executeUpdate();
			}
		} catch (SQLException e) {
			AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL,
					"InvoiceCancelRequestDAO.reconcileForInvoice - invoiceId=" + invoiceId, e);
		}
	}

	private InvoiceCancelRequest findOne(String sql, int id) {
		try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {

			ps.setInt(1, id);

			System.out.println(
					"[InvoiceCancelRequestDAO] DB=" + con.getCatalog() + ", URL=" + con.getMetaData().getURL());

			System.out.println("[InvoiceCancelRequestDAO] findOne id=" + id);

			try (ResultSet rs = ps.executeQuery()) {

				if (rs.next()) {
					InvoiceCancelRequest result = map(rs);

					System.out.println("[InvoiceCancelRequestDAO] FOUND" + " requestId=" + result.getRequestId()
							+ ", invoiceId=" + result.getInvoiceId() + ", status=" + result.getStatus());

					return result;
				}

				System.err.println("[InvoiceCancelRequestDAO] NO ROW" + " id=" + id);
			}

		} catch (SQLException e) {

			AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "InvoiceCancelRequestDAO.findOne - id=" + id, e);

			System.err.println("[InvoiceCancelRequestDAO] SQL ERROR" + " id=" + id);

			System.err.println("SQL = " + sql);

			e.printStackTrace();
		}

		return null;
	}

	private InvoiceCancelRequest map(ResultSet rs) throws SQLException {
		InvoiceCancelRequest r = new InvoiceCancelRequest();
		r.setRequestId(rs.getInt("RequestID"));
		r.setInvoiceId(rs.getInt("InvoiceID"));
		r.setInvoiceCode(rs.getString("InvoiceCode"));
		r.setRequestedBy(rs.getInt("RequestedBy"));
		r.setRequestedByName(rs.getString("RequestedByName"));
		r.setReason(rs.getString("Reason"));
		r.setStatus(rs.getString("Status"));

		Timestamp requestedAt = rs.getTimestamp("RequestedAt");
		r.setRequestedAt(requestedAt != null ? requestedAt.toLocalDateTime() : null);

		int reviewedBy = rs.getInt("ReviewedBy");
		r.setReviewedBy(rs.wasNull() ? null : reviewedBy);
		r.setReviewedByName(rs.getString("ReviewedByName"));

		Timestamp reviewedAt = rs.getTimestamp("ReviewedAt");
		r.setReviewedAt(reviewedAt != null ? reviewedAt.toLocalDateTime() : null);
		r.setReviewNote(rs.getString("ReviewNote"));
		return r;
	}

	private String normalizeNote(String note) {
		if (note == null) {
			return null;
		}
		String trimmed = note.trim();
		if (trimmed.isEmpty()) {
			return null;
		}
		return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500);
	}
}
