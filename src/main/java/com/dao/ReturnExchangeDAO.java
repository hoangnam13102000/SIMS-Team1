package com.dao;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.event.AppEventBus;
import com.event.DataChangedEvent;
import com.model.ReturnExchange;
import com.model.ReturnExchangeDetail;
import com.model.ShiftCashSummary;
import com.utils.DBConnection;
import com.utils.PaginationHelper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
	private final StoreConfigDAO storeConfigDAO = new StoreConfigDAO();
	private final ShiftDAO shiftDAO = new ShiftDAO();
	private static final String BASE_TABLE = "ReturnExchanges r " + "JOIN Invoices inv ON r.InvoiceID = inv.InvoiceID "
			+ "JOIN Users u ON r.CreatedBy = u.UserID " + "LEFT JOIN Users au ON r.ApprovedBy = au.UserID";

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
		return "r.ReturnID, " + "r.InvoiceID, " + "inv.InvoiceCode, " + "r.Type, " + "r.Reason, "
				+ "r.RejectionReason, " + "r.TotalValue, " + "r.DiscountShare, " + "r.PointsShare, "

				+ "r.RefundMethod, " + "r.RefundShiftID, " + "r.RefundTransactionID, " + "r.RefundStatus, "
				+ "r.RefundedBy, " + "r.RefundedAt, "

				+ "r.RequiresApproval, " + "r.Status, " + "r.ApprovedBy, " + "au.FullName AS ApprovedByName, "
				+ "r.ApprovedAt, "

				+ "r.CreatedBy, " + "u.FullName AS CreatedByName, " + "r.CreatedAt";
	}

	@Override
	protected String getOrderBy() {
		return "r.CreatedAt DESC, r.ReturnID DESC";
	}

	@Override
	protected String[] getSearchableColumns() {
		return new String[] { "inv.InvoiceCode", "u.FullName" };
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

		re.setRefundMethod(rs.getString("RefundMethod"));

		int refundShiftId = rs.getInt("RefundShiftID");

		re.setRefundShiftId(rs.wasNull() ? null : refundShiftId);

		re.setRefundTransactionId(rs.getString("RefundTransactionID"));

		re.setRefundStatus(rs.getString("RefundStatus"));

		int refundedBy = rs.getInt("RefundedBy");

		re.setRefundedBy(rs.wasNull() ? null : refundedBy);

		Timestamp refundedAt = rs.getTimestamp("RefundedAt");

		re.setRefundedAt(refundedAt != null ? refundedAt.toLocalDateTime() : null);

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

	// ==================================================================
	// LỌC THEO THỜI GIAN — xây dựng WHERE + params cho khoảng CreatedAt
	// ==================================================================
	/**
	 * Xây dựng mệnh đề WHERE cho khoảng ngày (dựa trên cột r.CreatedAt). fromDate:
	 * bao gồm cả ngày đó (>= 00:00:00) toDate: bao gồm cả ngày đó (<= 23:59:59.999)
	 * Trả về null nếu cả hai đều null (không có điều kiện).
	 */
	private static String buildDateWhere(LocalDate fromDate, LocalDate toDate) {
		if (fromDate == null && toDate == null)
			return null;
		StringBuilder sb = new StringBuilder();
		if (fromDate != null)
			sb.append("r.CreatedAt >= ?");
		if (fromDate != null && toDate != null)
			sb.append(" AND ");
		if (toDate != null)
			sb.append("r.CreatedAt <= ?");
		return sb.toString();
	}

	/**
	 * Gộp điều kiện ngày vào một WHERE chung (vd từ search) và mảng params tương
	 * ứng.
	 *
	 * @param baseWhere  điều kiện gốc (có thể null), KHÔNG chứa từ khóa "WHERE"
	 * @param baseParams params của điều kiện gốc (có thể null)
	 * @param fromDate   ngày bắt đầu (có thể null)
	 * @param toDate     ngày kết thúc (có thể null)
	 * @return Mảng 2 phần tử: [0] = String where hoàn chỉnh, [1] = Object[] params
	 */
	private static Object[] mergeDateCondition(String baseWhere, Object[] baseParams, LocalDate fromDate,
			LocalDate toDate) {
		String dateWhere = buildDateWhere(fromDate, toDate);
		if (dateWhere == null)
			return new Object[] { baseWhere, baseParams };

		List<Object> dateParams = new ArrayList<>(2);
		if (fromDate != null) {
			dateParams.add(Timestamp.valueOf(LocalDateTime.of(fromDate, LocalTime.MIN)));
		}
		if (toDate != null) {
			dateParams.add(Timestamp.valueOf(LocalDateTime.of(toDate, LocalTime.MAX)));
		}

		String finalWhere;
		Object[] finalParams;
		if (baseWhere == null || baseWhere.isEmpty()) {
			finalWhere = dateWhere;
			finalParams = dateParams.toArray();
		} else {
			finalWhere = "(" + baseWhere + ") AND (" + dateWhere + ")";
			List<Object> merged = new ArrayList<>();
			if (baseParams != null)
				for (Object p : baseParams)
					merged.add(p);
			merged.addAll(dateParams);
			finalParams = merged.toArray();
		}
		return new Object[] { finalWhere, finalParams };
	}

	// ==================================================================
	// Phân trang + search CÓ hỗ trợ lọc theo khoảng ngày
	//
	// QUAN TRỌNG: ĐẶT TÊN RIÊNG (không dùng getPaged/search) để tránh
	// ambiguous method do BaseDAO có getPaged(..., String, Object...)
	// — null vừa cast được String vừa cast được LocalDate.
	// ==================================================================

	/**
	 * Phân trang CÓ lọc theo khoảng ngày. Đặt tên là getPagedFiltered thay vì
	 * overload getPaged để tránh đụng với BaseDAO.getPaged(int, int, String,
	 * Object...) (varargs).
	 */
	public PaginationHelper.PaginationResult<ReturnExchange> getPagedFiltered(int pageNumber, int pageSize,
			LocalDate fromDate, LocalDate toDate) {
		Object[] merged = mergeDateCondition(null, null, fromDate, toDate);
		String where = (String) merged[0];
		Object[] params = (Object[]) merged[1];
		return super.getPaged(pageNumber, pageSize, where, params);
	}

	/**
	 * Tìm kiếm kết hợp thêm lọc theo khoảng ngày. Đặt tên là searchFiltered thay vì
	 * overload search.
	 */
	public PaginationHelper.PaginationResult<ReturnExchange> searchFiltered(String keyword, int pageNumber,
			int pageSize, LocalDate fromDate, LocalDate toDate) {
		String[] columns = getSearchableColumns();
		if (keyword == null || keyword.trim().isEmpty() || columns.length == 0) {
			return getPagedFiltered(pageNumber, pageSize, fromDate, toDate);
		}
		String escaped = keyword.trim().replace("!", "!!").replace("%", "!%").replace("_", "!_");
		String likeValue = "%" + escaped + "%";
		StringBuilder where = new StringBuilder("(");
		Object[] searchParams = new Object[columns.length];
		for (int i = 0; i < columns.length; i++) {
			if (i > 0)
				where.append(" OR ");
			where.append(columns[i]).append(" LIKE ? ESCAPE '!'");
			searchParams[i] = likeValue;
		}
		where.append(")");

		Object[] merged = mergeDateCondition(where.toString(), searchParams, fromDate, toDate);
		String finalWhere = (String) merged[0];
		Object[] finalParams = (Object[]) merged[1];
		return super.getPaged(pageNumber, pageSize, finalWhere, finalParams);
	}

	/** Lấy tất cả (không phân trang) — dùng cho export, có thể lọc theo ngày. */
	@Override
	public List<ReturnExchange> getAll() {
		return getAllFiltered(null, null);
	}

	/**
	 * Lấy tất cả có lọc theo khoảng ngày (dùng cho export khi có bộ lọc đang bật).
	 */
	public List<ReturnExchange> getAllFiltered(LocalDate fromDate, LocalDate toDate) {
		Object[] merged = mergeDateCondition(null, null, fromDate, toDate);
		String where = (String) merged[0];
		Object[] params = (Object[]) merged[1];

		StringBuilder sql = new StringBuilder();
		sql.append("SELECT ").append(getColumns()).append(" FROM ").append(getTableName());
		String joinClause = getJoinClause();
		if (joinClause != null && !joinClause.isEmpty())
			sql.append(" ").append(joinClause);
		if (where != null && !where.isEmpty())
			sql.append(" WHERE ").append(where);
		sql.append(" ORDER BY ").append(getOrderBy());

		List<ReturnExchange> list = new ArrayList<>();
		try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
			if (params != null) {
				for (int i = 0; i < params.length; i++)
					stmt.setObject(i + 1, params[i]);
			}
			try (ResultSet rs = stmt.executeQuery()) {
				while (rs.next())
					list.add(mapResultSet(rs));
			}
		} catch (SQLException e) {
			AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, getClass().getSimpleName() + ".getAllFiltered", e);
		}
		return list;
	}

	// ==================================================================
	// Các phương thức khác (giữ nguyên từ bản gốc)
	// ==================================================================

	public List<ReturnExchangeDetail> getDetails(int returnId) {
		String sql = "SELECT d.ReturnDetailID, d.ReturnID, d.ProductID, p.ProductName, p.ProductCode, "
				+ "d.Quantity, d.Direction, d.UnitPrice " + "FROM ReturnExchangeDetails d "
				+ "JOIN Products p ON p.ProductID = d.ProductID " + "WHERE d.ReturnID = ? ORDER BY d.ReturnDetailID";
		List<ReturnExchangeDetail> list = new ArrayList<>();
		try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
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

	/**
	 * SL đã bán còn có thể trả = sold - already returned (APPROVED, Direction=IN).
	 */
	public Map<Integer, Integer> getReturnableQuantities(int invoiceId) {
		String soldSql = "SELECT ProductID, SUM(Quantity) AS Qty FROM InvoiceDetails WHERE InvoiceID = ? GROUP BY ProductID";
		String returnedSql = "SELECT d.ProductID, SUM(d.Quantity) AS Qty " + "FROM ReturnExchangeDetails d "
				+ "JOIN ReturnExchanges r ON r.ReturnID = d.ReturnID "
				+ "WHERE r.InvoiceID = ? AND r.Status = 'APPROVED' AND d.Direction = 'IN' " + "GROUP BY d.ProductID";
		Map<Integer, Integer> sold = new HashMap<>();
		try (Connection con = DBConnection.getConnection()) {
			try (PreparedStatement ps = con.prepareStatement(soldSql)) {
				ps.setInt(1, invoiceId);
				try (ResultSet rs = ps.executeQuery()) {
					while (rs.next())
						sold.put(rs.getInt(1), rs.getInt(2));
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

	/**
	 * Tạo yêu cầu RETURN do CUSTOMER gửi từ đơn hàng online.
	 *
	 * Khác với RETURN tại quầy: - luôn PENDING; - chưa chọn RefundMethod; - chưa
	 * gắn RefundShiftID; - chưa thay đổi tồn kho/hóa đơn; - chưa hoàn tiền; - nhân
	 * viên/quản lý phải tiếp nhận trước.
	 */
	public String createCustomerReturnRequest(ReturnExchange header, List<ReturnExchangeDetail> details) {

		if (details == null || details.isEmpty()) {
			return "Đơn hàng không có sản phẩm để trả.";
		}

		if (header.getReason() == null || header.getReason().isBlank()) {
			return "Vui lòng nhập lý do trả hàng.";
		}

		String insertHeaderSql = "INSERT INTO ReturnExchanges (" + "InvoiceID, " + "Type, " + "Reason, "
				+ "TotalValue, " + "DiscountShare, " + "PointsShare, " + "RefundMethod, " + "RefundShiftID, "
				+ "RefundStatus, " + "RequiresApproval, " + "Status, " + "CreatedBy" + ") " + "VALUES ("
				+ "?, 'RETURN', ?, ?, ?, ?, " + "NULL, NULL, 'PENDING', " + "1, 'PENDING', ?" + ")";

		String insertDetailSql = "INSERT INTO ReturnExchangeDetails (" + "ReturnID, " + "ProductID, " + "Quantity, "
				+ "Direction, " + "UnitPrice" + ") " + "VALUES (?, ?, ?, 'IN', ?)";

		try (Connection con = DBConnection.getConnection()) {

			con.setAutoCommit(false);

			try {

				/*
				 * --------------------------------------------------------- 1. Khóa hóa đơn.
				 * ---------------------------------------------------------
				 */
				String invoiceStatus;

				try (PreparedStatement ps = con.prepareStatement(
						"SELECT Status " + "FROM Invoices " + "WHERE InvoiceID = ? " + "FOR UPDATE")) {

					ps.setInt(1, header.getInvoiceId());

					try (ResultSet rs = ps.executeQuery()) {

						if (!rs.next()) {
							con.rollback();
							return "Không tìm thấy hóa đơn của đơn hàng.";
						}

						invoiceStatus = rs.getString("Status");
					}
				}

				if (!"ACTIVE".equalsIgnoreCase(invoiceStatus)) {
					con.rollback();

					return "Hóa đơn đã bị hủy, " + "không thể gửi yêu cầu trả hàng.";
				}

				/*
				 * --------------------------------------------------------- 2. Không cho tạo
				 * hai yêu cầu RETURN đang xử lý cho cùng một hóa đơn.
				 * ---------------------------------------------------------
				 */
				String duplicateSql = "SELECT ReturnID " + "FROM ReturnExchanges " + "WHERE InvoiceID = ? "
						+ "AND Type = 'RETURN' " + "AND Status IN (" + "'PENDING', 'APPROVED'" + ") "
						+ "ORDER BY ReturnID DESC " + "LIMIT 1 " + "FOR UPDATE";

				try (PreparedStatement ps = con.prepareStatement(duplicateSql)) {

					ps.setInt(1, header.getInvoiceId());

					try (ResultSet rs = ps.executeQuery()) {

						if (rs.next()) {
							con.rollback();

							return "Đơn hàng đã có yêu cầu " + "trả hàng đang được xử lý.";
						}
					}
				}

				/*
				 * --------------------------------------------------------- 3. Customer chỉ
				 * được RETURN.
				 *
				 * Không cho đưa Direction=OUT từ client.
				 * ---------------------------------------------------------
				 */
				Map<Integer, Integer> returnable = getReturnableQuantities(header.getInvoiceId());

				BigDecimal returnedGross = BigDecimal.ZERO;

				for (ReturnExchangeDetail d : details) {

					if (d.getQuantity() <= 0) {
						con.rollback();
						return "Số lượng trả phải lớn hơn 0.";
					}

					if (!d.isIn()) {
						con.rollback();

						return "Khách hàng online " + "chỉ được gửi yêu cầu trả hàng.";
					}

					int maxQty = returnable.getOrDefault(d.getProductId(), 0);

					if (d.getQuantity() > maxQty) {
						con.rollback();

						return "Sản phẩm \"" + d.getProductName() + "\" chỉ còn có thể trả tối đa " + maxQty + ".";
					}

					returnedGross = returnedGross.add(d.getLineTotal());
				}

				/*
				 * --------------------------------------------------------- 4. Tính số tiền dự
				 * kiến hoàn. ---------------------------------------------------------
				 */
				RefundBreakdown breakdown = computeRefundAmount(con, header.getInvoiceId(), returnedGross);

				BigDecimal totalValue = breakdown.refund;

				/*
				 * --------------------------------------------------------- 5. Tạo phiếu
				 * PENDING.
				 *
				 * RefundMethod chưa có. Nhân viên phải tiếp nhận sau.
				 * ---------------------------------------------------------
				 */
				int returnId;

				try (PreparedStatement ps = con.prepareStatement(insertHeaderSql, Statement.RETURN_GENERATED_KEYS)) {

					ps.setInt(1, header.getInvoiceId());

					ps.setString(2, header.getReason().trim());

					ps.setBigDecimal(3, totalValue);

					ps.setBigDecimal(4, breakdown.discountShare);

					ps.setBigDecimal(5, breakdown.pointsShare);

					ps.setInt(6, header.getCreatedBy());

					int inserted = ps.executeUpdate();

					if (inserted != 1) {
						throw new SQLException("Không tạo được yêu cầu trả hàng.");
					}

					try (ResultSet keys = ps.getGeneratedKeys()) {

						if (!keys.next()) {
							throw new SQLException("Không lấy được ReturnID vừa tạo.");
						}

						returnId = keys.getInt(1);
					}
				}

				/*
				 * --------------------------------------------------------- 6. Lưu chi tiết.
				 * ---------------------------------------------------------
				 */
				try (PreparedStatement ps = con.prepareStatement(insertDetailSql)) {

					for (ReturnExchangeDetail d : details) {

						ps.setInt(1, returnId);

						ps.setInt(2, d.getProductId());

						ps.setInt(3, d.getQuantity());

						ps.setBigDecimal(4, d.getUnitPrice());

						ps.addBatch();
					}

					ps.executeBatch();
				}

				con.commit();

				/*
				 * Cập nhật object Java.
				 */
				header.setReturnId(returnId);

				header.setType(ReturnExchange.TYPE_RETURN);

				header.setTotalValue(totalValue);

				header.setDiscountShare(breakdown.discountShare);

				header.setPointsShare(breakdown.pointsShare);

				header.setRefundMethod(null);
				header.setRefundShiftId(null);

				header.setRefundStatus(ReturnExchange.REFUND_STATUS_PENDING);

				header.setRequiresApproval(true);

				header.setStatus(ReturnExchange.STATUS_PENDING);

				AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.RETURN_EXCHANGE));

				return null;

			} catch (SQLException e) {

				con.rollback();
				throw e;

			} finally {

				con.setAutoCommit(true);
			}

		} catch (SQLException e) {

			AppLogger.getInstance().error(ErrorCode.RETURN_CREATE_FAIL,
					"ReturnExchangeDAO." + "createCustomerReturnRequest" + " - invoiceId=" + header.getInvoiceId(), e);

			return e.getMessage() != null ? e.getMessage() : "Không thể gửi yêu cầu trả hàng.";
		}
	}

	public String createReturnExchange(ReturnExchange header, List<ReturnExchangeDetail> details) {

		if (details == null || details.isEmpty()) {
			return "Chưa chọn sản phẩm nào để đổi/trả.";
		}

		if (header.getReason() == null || header.getReason().isBlank()) {
			return "Vui lòng nhập lý do đổi/trả " + "(bắt buộc theo quy định).";
		}

		String insertHeaderSql = "INSERT INTO ReturnExchanges (" + "InvoiceID, " + "Type, " + "Reason, "
				+ "TotalValue, " + "DiscountShare, " + "PointsShare, " + "RefundMethod, " + "RefundShiftID, "
				+ "RefundStatus, " + "RequiresApproval, " + "Status, " + "CreatedBy" + ") " + "VALUES ("
				+ "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, " + "'PENDING', ?" + ")";

		String insertDetailSql = "INSERT INTO ReturnExchangeDetails " + "(" + "ReturnID, " + "ProductID, "
				+ "Quantity, " + "Direction, " + "UnitPrice" + ") " + "VALUES (?, ?, ?, ?, ?)";

		String approveSql = "UPDATE ReturnExchanges SET " + "Status = 'APPROVED', " + "ApprovedBy = ?, "
				+ "ApprovedAt = CURRENT_TIMESTAMP " + "WHERE ReturnID = ?";

		try (Connection con = DBConnection.getConnection()) {

			con.setAutoCommit(false);

			try {

				/*
				 * ========================================================= 1. Khóa và kiểm tra
				 * hóa đơn =========================================================
				 */

				String invStatus;

				try (PreparedStatement ps = con.prepareStatement(
						"SELECT Status " + "FROM Invoices " + "WHERE InvoiceID = ? " + "FOR UPDATE")) {

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

					return "Hóa đơn đã bị hủy, " + "không thể đổi/trả hàng.";
				}

				/*
				 * ========================================================= 2. Kiểm tra số
				 * lượng trả / tồn kho hàng đổi
				 * =========================================================
				 */

				Map<Integer, Integer> returnable = getReturnableQuantities(header.getInvoiceId());

				Map<Integer, Integer> returnRequestedSoFar = new HashMap<>();

				for (ReturnExchangeDetail d : details) {

					if (d.getQuantity() <= 0) {
						con.rollback();

						return "Số lượng phải lớn hơn 0.";
					}

					/*
					 * IN = hàng khách trả lại cửa hàng.
					 */
					if (d.isIn()) {

						int already = returnRequestedSoFar.getOrDefault(d.getProductId(), 0);

						int limit = returnable.getOrDefault(d.getProductId(), 0);

						if (already + d.getQuantity() > limit) {

							con.rollback();

							return "Sản phẩm \"" + d.getProductName() + "\" chỉ còn có thể trả tối đa "
									+ (limit - already) + " (đã bán trừ đã đổi/trả " + "trước đó).";
						}

						returnRequestedSoFar.put(d.getProductId(), already + d.getQuantity());

						/*
						 * OUT = hàng cửa hàng đưa cho khách khi đổi.
						 */
					} else if (d.isOut()) {

						String stockSql = "SELECT Stock, ProductName " + "FROM Products " + "WHERE ProductID = ? "
								+ "FOR UPDATE";

						try (PreparedStatement ps = con.prepareStatement(stockSql)) {

							ps.setInt(1, d.getProductId());

							try (ResultSet rs = ps.executeQuery()) {

								if (!rs.next()) {
									con.rollback();

									return "Không tìm thấy " + "sản phẩm đổi.";
								}

								int stock = rs.getInt("Stock");

								if (d.getQuantity() > stock) {

									con.rollback();

									return "Sản phẩm \"" + rs.getString("ProductName") + "\" không đủ tồn kho "
											+ "để đổi (còn " + stock + ").";
								}
							}
						}
					}
				}

				/*
				 * ========================================================= 3. Tính giá trị
				 * hàng RETURN =========================================================
				 */

				BigDecimal returnedGross = BigDecimal.ZERO;

				for (ReturnExchangeDetail d : details) {

					if (d.isIn()) {
						returnedGross = returnedGross.add(d.getLineTotal());
					}
				}

				RefundBreakdown breakdown = computeRefundAmount(con, header.getInvoiceId(), returnedGross);

				BigDecimal totalValue = breakdown.refund;

				/*
				 * ========================================================= 4. Xử lý
				 * RefundMethod =========================================================
				 */

				String refundMethod = null;

				Integer refundShiftId = null;

				String refundStatus = ReturnExchange.REFUND_STATUS_NONE;

				/*
				 * Chỉ RETURN mới có refund.
				 *
				 * EXCHANGE hiện chưa xử lý dòng tiền chênh lệch.
				 */
				if (ReturnExchange.TYPE_RETURN.equalsIgnoreCase(header.getType())) {

					refundMethod = header.getRefundMethod();

					if (refundMethod == null || refundMethod.isBlank()) {
						con.rollback();

						return "Vui lòng chọn " + "phương thức hoàn tiền.";
					}

					if (!isSupportedRefundMethod(refundMethod)) {
						con.rollback();

						return "Phương thức hoàn tiền " + "không hợp lệ.";
					}

					refundStatus = ReturnExchange.REFUND_STATUS_PENDING;

					/*
					 * CASH bắt buộc phải gắn vào ca OPEN của cashier.
					 */
					if (ReturnExchange.REFUND_CASH.equalsIgnoreCase(refundMethod)) {

						refundShiftId = findOpenRefundShift(con, header.getCreatedBy());

						if (refundShiftId == null) {

							con.rollback();

							return "Hoàn tiền mặt yêu cầu " + "nhân viên phải có " + "ca đang mở.";
						}
					}
				}

				header.setRefundMethod(refundMethod);

				header.setRefundShiftId(refundShiftId);

				header.setRefundStatus(refundStatus);

				/*
				 * ========================================================= 5. Xác định có cần
				 * Manager duyệt không =========================================================
				 */

				BigDecimal approvalThreshold = storeConfigDAO.getApprovalThreshold();

				boolean requiresApproval = totalValue.compareTo(approvalThreshold) > 0;

				/*
				 * ========================================================= 6. Insert
				 * ReturnExchanges =========================================================
				 */

				int returnId;

				try (PreparedStatement ps = con.prepareStatement(insertHeaderSql, Statement.RETURN_GENERATED_KEYS)) {

					ps.setInt(1, header.getInvoiceId());

					ps.setString(2, header.getType());

					ps.setString(3, header.getReason().trim());

					ps.setBigDecimal(4, totalValue);

					ps.setBigDecimal(5, breakdown.discountShare);

					ps.setBigDecimal(6, breakdown.pointsShare);

					if (refundMethod != null) {

						ps.setString(7, refundMethod);

					} else {

						ps.setNull(7, Types.VARCHAR);
					}

					if (refundShiftId != null) {

						ps.setInt(8, refundShiftId);

					} else {

						ps.setNull(8, Types.INTEGER);
					}

					ps.setString(9, refundStatus);

					ps.setBoolean(10, requiresApproval);

					ps.setInt(11, header.getCreatedBy());

					int inserted = ps.executeUpdate();

					if (inserted != 1) {
						throw new SQLException("Không tạo được " + "yêu cầu đổi/trả.");
					}

					try (ResultSet keys = ps.getGeneratedKeys()) {

						if (!keys.next()) {

							throw new SQLException("Không lấy được ReturnID " + "vừa tạo.");
						}

						returnId = keys.getInt(1);
					}
				}

				/*
				 * ========================================================= 7. Insert chi tiết
				 * =========================================================
				 */

				try (PreparedStatement ps = con.prepareStatement(insertDetailSql)) {

					for (ReturnExchangeDetail d : details) {

						ps.setInt(1, returnId);

						ps.setInt(2, d.getProductId());

						ps.setInt(3, d.getQuantity());

						ps.setString(4, d.getDirection());

						ps.setBigDecimal(5, d.getUnitPrice());

						ps.addBatch();
					}

					ps.executeBatch();
				}

				/*
				 * ========================================================= 8. Nếu không cần
				 * Manager duyệt → auto approve
				 * =========================================================
				 */

				if (!requiresApproval) {

					/*
					 * Điều chỉnh điểm khách hàng.
					 */
					adjustPointsForApprovedReturn(con, header.getInvoiceId(), returnId);

					/*
					 * APPROVED.
					 *
					 * Trigger DB sẽ xử lý: - tồn kho; - TotalAmount; - SubTotal...
					 */
					try (PreparedStatement ps = con.prepareStatement(approveSql)) {

						ps.setInt(1, header.getCreatedBy());

						ps.setInt(2, returnId);

						int affected = ps.executeUpdate();

						if (affected != 1) {

							con.rollback();

							return "Không thể tự động " + "duyệt yêu cầu đổi/trả.";
						}
					}

					/*
					 * RETURN + CASH: hoàn tiền ngay từ két.
					 */
					if (ReturnExchange.TYPE_RETURN.equalsIgnoreCase(header.getType())

							&& ReturnExchange.REFUND_CASH.equalsIgnoreCase(refundMethod)) {

						if (refundShiftId == null) {

							con.rollback();

							return "Không xác định được " + "ca hoàn tiền mặt.";
						}

						Integer cashierUserId = findRefundShiftOwner(con, refundShiftId);

						if (cashierUserId == null) {

							con.rollback();

							return "Ca hoàn tiền mặt đã đóng " + "hoặc không còn hợp lệ.";
						}

						String refundError = completeCashRefund(con, returnId, cashierUserId, refundShiftId,
								totalValue);

						if (refundError != null) {

							con.rollback();

							return refundError;
						}

						header.setRefundStatus(ReturnExchange.REFUND_STATUS_COMPLETED);
					}
				}

				/*
				 * ========================================================= 9. Commit
				 * =========================================================
				 */

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
					"ReturnExchangeDAO." + "createReturnExchange" + " - invoiceId=" + header.getInvoiceId(), e);

			return e.getMessage() != null ? e.getMessage() : "Tạo yêu cầu đổi/trả thất bại.";
		}
	}

	public String assignRefundMethod(int returnId, int actorUserId, String refundMethod) {

		if (refundMethod == null || refundMethod.isBlank()) {
			return "Vui lòng chọn phương thức hoàn tiền.";
		}

		if (!isSupportedRefundMethod(refundMethod)) {
			return "Phương thức hoàn tiền không hợp lệ.";
		}

		String lockSql = "SELECT " + "Type, " + "Status, " + "RefundStatus " + "FROM ReturnExchanges "
				+ "WHERE ReturnID = ? " + "FOR UPDATE";

		String updateSql = "UPDATE ReturnExchanges SET " + "RefundMethod = ?, " + "RefundShiftID = ? "
				+ "WHERE ReturnID = ? " + "AND Type = 'RETURN' " + "AND Status = 'PENDING'";

		try (Connection con = DBConnection.getConnection()) {

			con.setAutoCommit(false);

			try {

				String type;
				String status;
				String refundStatus;

				try (PreparedStatement ps = con.prepareStatement(lockSql)) {

					ps.setInt(1, returnId);

					try (ResultSet rs = ps.executeQuery()) {

						if (!rs.next()) {
							con.rollback();

							return "Không tìm thấy " + "yêu cầu trả hàng.";
						}

						type = rs.getString("Type");

						status = rs.getString("Status");

						refundStatus = rs.getString("RefundStatus");
					}
				}

				if (!ReturnExchange.TYPE_RETURN.equalsIgnoreCase(type)) {
					con.rollback();

					return "Phiếu này không phải " + "yêu cầu trả hàng.";
				}

				if (!ReturnExchange.STATUS_PENDING.equalsIgnoreCase(status)) {
					con.rollback();

					return "Chỉ có thể chọn phương thức " + "hoàn tiền khi phiếu đang chờ duyệt.";
				}

				if (!ReturnExchange.REFUND_STATUS_PENDING.equalsIgnoreCase(refundStatus)) {
					con.rollback();

					return "Khoản hoàn tiền không ở " + "trạng thái chờ xử lý.";
				}

				Integer refundShiftId = null;

				/*
				 * Nếu chọn CASH: người tiếp nhận phải có ca OPEN.
				 */
				if (ReturnExchange.REFUND_CASH.equalsIgnoreCase(refundMethod)) {

					refundShiftId = findOpenRefundShift(con, actorUserId);

					if (refundShiftId == null) {

						con.rollback();

						return "Hoàn tiền mặt phải được " + "tiếp nhận bởi nhân viên " + "đang có ca bán hàng mở.";
					}
				}

				try (PreparedStatement ps = con.prepareStatement(updateSql)) {

					ps.setString(1, refundMethod);

					if (refundShiftId != null) {

						ps.setInt(2, refundShiftId);

					} else {

						ps.setNull(2, Types.INTEGER);
					}

					ps.setInt(3, returnId);

					int affected = ps.executeUpdate();

					if (affected != 1) {

						con.rollback();

						return "Không thể cập nhật " + "phương thức hoàn tiền.";
					}
				}

				con.commit();

				AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.RETURN_EXCHANGE));

				return null;

			} catch (SQLException e) {

				con.rollback();
				throw e;

			} finally {

				con.setAutoCommit(true);
			}

		} catch (SQLException e) {

			AppLogger.getInstance().error(ErrorCode.RETURN_STATUS_UPDATE_FAIL,
					"ReturnExchangeDAO." + "assignRefundMethod" + " - returnId=" + returnId, e);

			return e.getMessage() != null ? e.getMessage() : "Cập nhật phương thức hoàn tiền thất bại.";
		}
	}

	public String approve(int returnId, int approverId) {

		String lockRequestSql = "SELECT " + "Status, " + "InvoiceID, " + "Type, " + "TotalValue, " + "RefundMethod, "
				+ "RefundShiftID " + "FROM ReturnExchanges " + "WHERE ReturnID = ? " + "FOR UPDATE";

		String outDetailSql = "SELECT d.Quantity, " + "p.Stock, " + "p.ProductName " + "FROM ReturnExchangeDetails d "
				+ "JOIN Products p " + "ON p.ProductID = d.ProductID " + "WHERE d.ReturnID = ? "
				+ "AND d.Direction = 'OUT' " + "FOR UPDATE";

		String updateSql = "UPDATE ReturnExchanges SET " + "Status = 'APPROVED', " + "ApprovedBy = ?, "
				+ "ApprovedAt = CURRENT_TIMESTAMP " + "WHERE ReturnID = ? " + "AND Status = 'PENDING'";

		try (Connection con = DBConnection.getConnection()) {

			con.setAutoCommit(false);

			try {

				String status;
				int invoiceId;

				String type;
				BigDecimal totalValue;
				String refundMethod;
				Integer refundShiftId;

				/*
				 * Khóa request để tránh hai manager duyệt cùng một phiếu.
				 */
				try (PreparedStatement ps = con.prepareStatement(lockRequestSql)) {

					ps.setInt(1, returnId);

					try (ResultSet rs = ps.executeQuery()) {

						if (!rs.next()) {
							con.rollback();

							return "Không tìm thấy yêu cầu đổi/trả.";
						}

						status = rs.getString("Status");

						invoiceId = rs.getInt("InvoiceID");

						type = rs.getString("Type");

						totalValue = nvl(rs.getBigDecimal("TotalValue"));

						refundMethod = rs.getString("RefundMethod");

						int rawRefundShiftId = rs.getInt("RefundShiftID");

						refundShiftId = rs.wasNull() ? null : rawRefundShiftId;
					}
				}

				if (!ReturnExchange.STATUS_PENDING.equalsIgnoreCase(status)) {

					con.rollback();

					return "Yêu cầu này không còn " + "ở trạng thái chờ duyệt.";
				}

				if (ReturnExchange.TYPE_RETURN.equalsIgnoreCase(type)

						&& (refundMethod == null || refundMethod.isBlank())) {

					con.rollback();

					return "Yêu cầu trả hàng chưa có " + "phương thức hoàn tiền. " + "Vui lòng tiếp nhận và chọn "
							+ "phương thức hoàn tiền trước.";
				}

				/*
				 * Nếu đây là EXCHANGE có hàng OUT, khóa và kiểm tra tồn kho trước khi duyệt.
				 */
				try (PreparedStatement ps = con.prepareStatement(outDetailSql)) {

					ps.setInt(1, returnId);

					try (ResultSet rs = ps.executeQuery()) {

						while (rs.next()) {

							int qty = rs.getInt("Quantity");

							int stock = rs.getInt("Stock");

							if (qty > stock) {

								con.rollback();

								return "Sản phẩm \"" + rs.getString("ProductName") + "\" không đủ tồn kho "
										+ "để duyệt đổi " + "(còn " + stock + ").";
							}
						}
					}
				}

				/*
				 * Điều chỉnh điểm khách hàng trong cùng transaction.
				 */
				adjustPointsForApprovedReturn(con, invoiceId, returnId);

				/*
				 * Chuyển request sang APPROVED.
				 *
				 * Trigger MySQL xử lý tồn kho/hóa đơn tại bước này.
				 */
				try (PreparedStatement ps = con.prepareStatement(updateSql)) {

					ps.setInt(1, approverId);

					ps.setInt(2, returnId);

					int affected = ps.executeUpdate();

					if (affected != 1) {

						con.rollback();

						return "Yêu cầu này không còn " + "ở trạng thái chờ duyệt.";
					}
				}

				/*
				 * RETURN + CASH:
				 *
				 * Sau khi manager approve, thực hiện refund từ đúng két.
				 *
				 * Nếu két không đủ tiền: rollback toàn transaction.
				 *
				 * Khi rollback: - approval rollback - trigger tồn kho rollback - thay đổi hóa
				 * đơn rollback - điểm khách hàng rollback
				 */
				if (ReturnExchange.TYPE_RETURN.equalsIgnoreCase(type)

						&& ReturnExchange.REFUND_CASH.equalsIgnoreCase(refundMethod)) {

					if (refundShiftId == null) {

						con.rollback();

						return "Yêu cầu hoàn tiền mặt " + "không có ca hoàn tiền.";
					}

					/*
					 * Không sử dụng CreatedBy ở đây.
					 *
					 * Với return tại quầy: CreatedBy thường là cashier.
					 *
					 * Nhưng với online return: CreatedBy là CUSTOMER.
					 *
					 * Người thực sự chi tiền mặt phải là chủ của RefundShiftID.
					 */
					Integer cashierUserId = findRefundShiftOwner(con, refundShiftId);

					if (cashierUserId == null) {

						con.rollback();

						return "Ca hoàn tiền mặt đã đóng " + "hoặc không còn hợp lệ.";
					}

					String refundError = completeCashRefund(con, returnId, cashierUserId, refundShiftId, totalValue);

					if (refundError != null) {

						con.rollback();

						return refundError;
					}
				}

				/*
				 * BANK_TRANSFER / CARD / PAYPAL vẫn giữ RefundStatus=PENDING.
				 *
				 * Sau này transaction thực tế mới đổi sang COMPLETED.
				 */

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
					"ReturnExchangeDAO.approve" + " - returnId=" + returnId, e);

			return e.getMessage() != null ? e.getMessage() : "Duyệt yêu cầu thất bại.";
		}
	}

	public String reject(int returnId, int approverId, String rejectionReason) {

		if (rejectionReason == null || rejectionReason.isBlank()) {
			return "Vui lòng nhập lý do từ chối.";
		}

		String sql = "UPDATE ReturnExchanges SET " + "Status = 'REJECTED', " + "RefundStatus = 'NONE', "
				+ "RejectionReason = ?, " + "ApprovedBy = ?, " + "ApprovedAt = CURRENT_TIMESTAMP "
				+ "WHERE ReturnID = ? " + "AND Status = 'PENDING'";

		try (Connection con = DBConnection.getConnection();

				PreparedStatement ps = con.prepareStatement(sql)) {

			ps.setString(1, rejectionReason.trim());

			ps.setInt(2, approverId);

			ps.setInt(3, returnId);

			int affected = ps.executeUpdate();

			if (affected != 1) {
				return "Yêu cầu này không còn " + "ở trạng thái chờ duyệt.";
			}

			AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.RETURN_EXCHANGE));

			return null;

		} catch (SQLException e) {

			AppLogger.getInstance().error(ErrorCode.RETURN_STATUS_UPDATE_FAIL,
					"ReturnExchangeDAO.reject" + " - returnId=" + returnId, e);

			return e.getMessage() != null ? e.getMessage() : "Từ chối yêu cầu thất bại.";
		}
	}

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

	private RefundBreakdown computeRefundAmount(Connection con, int invoiceId, BigDecimal returnedGross)
			throws SQLException {
		if (returnedGross == null || returnedGross.signum() <= 0) {
			return new RefundBreakdown(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
		}
		BigDecimal subTotal = BigDecimal.ZERO;
		BigDecimal discount = BigDecimal.ZERO;
		BigDecimal pointsDisc = BigDecimal.ZERO;
		BigDecimal totalAmount = BigDecimal.ZERO;
		try (PreparedStatement ps = con
				.prepareStatement("SELECT SubTotal, DiscountAmount, PointsDiscountAmount, TotalAmount "
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
			return new RefundBreakdown(returnedGross, BigDecimal.ZERO, BigDecimal.ZERO);
		}
		BigDecimal gross = returnedGross.min(subTotal);
		BigDecimal ratio = gross.divide(subTotal, 8, RoundingMode.HALF_UP);
		BigDecimal discShare = discount.multiply(ratio).setScale(0, RoundingMode.HALF_UP);
		BigDecimal ptsShare = pointsDisc.multiply(ratio).setScale(0, RoundingMode.HALF_UP);
		BigDecimal refund = totalAmount.multiply(ratio).setScale(0, RoundingMode.HALF_UP);
		if (refund.signum() < 0)
			refund = BigDecimal.ZERO;
		if (refund.compareTo(totalAmount) > 0)
			refund = totalAmount;
		return new RefundBreakdown(refund, discShare, ptsShare);
	}

	private BigDecimal sumApprovedReturnedGross(Connection con, int invoiceId) throws SQLException {
		String sql = "SELECT COALESCE(SUM(d.Quantity * d.UnitPrice), 0) " + "FROM ReturnExchangeDetails d "
				+ "JOIN ReturnExchanges r ON r.ReturnID = d.ReturnID "
				+ "WHERE r.InvoiceID = ? AND r.Status = 'APPROVED' AND d.Direction = 'IN'";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, invoiceId);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? nvl(rs.getBigDecimal(1)) : BigDecimal.ZERO;
			}
		}
	}

	private void adjustPointsForApprovedReturn(Connection con, int invoiceId, int returnId) throws SQLException {
		Integer customerId = null;
		int pointsUsed = 0;
		BigDecimal subTotal = BigDecimal.ZERO;
		BigDecimal discount = BigDecimal.ZERO;
		BigDecimal pointsDiscount = BigDecimal.ZERO;
		BigDecimal vatRate = BigDecimal.ZERO;
		BigDecimal originalSubTotal = BigDecimal.ZERO;
		try (PreparedStatement ps = con.prepareStatement(
				"SELECT CustomerID, PointsUsed, SubTotal, DiscountAmount, PointsDiscountAmount, VATRate, "
						+ "COALESCE((SELECT SUM(id.Quantity * id.UnitPrice) FROM InvoiceDetails id WHERE id.InvoiceID = i.InvoiceID), 0) AS OriginalSubTotal "
						+ "FROM Invoices i WHERE i.InvoiceID = ?")) {
			ps.setInt(1, invoiceId);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next())
					return;
				int cid = rs.getInt("CustomerID");
				if (rs.wasNull())
					return;
				customerId = cid;
				pointsUsed = Math.max(0, rs.getInt("PointsUsed"));
				subTotal = nvl(rs.getBigDecimal("SubTotal"));
				discount = nvl(rs.getBigDecimal("DiscountAmount"));
				pointsDiscount = nvl(rs.getBigDecimal("PointsDiscountAmount"));
				vatRate = nvl(rs.getBigDecimal("VATRate"));
				originalSubTotal = nvl(rs.getBigDecimal("OriginalSubTotal"));
			}
		}
		if (customerId == null || subTotal.signum() <= 0 || originalSubTotal.signum() <= 0)
			return;
		BigDecimal restoreRatio = originalSubTotal.divide(subTotal, 8, RoundingMode.HALF_UP);
		if (restoreRatio.compareTo(BigDecimal.ONE) < 0)
			restoreRatio = BigDecimal.ONE;
		BigDecimal originalDiscount = discount.multiply(restoreRatio).setScale(0, RoundingMode.HALF_UP);
		BigDecimal originalPointsDiscount = pointsDiscount.multiply(restoreRatio).setScale(0, RoundingMode.HALF_UP);
		BigDecimal originalTaxable = originalSubTotal.subtract(originalDiscount);
		if (originalTaxable.signum() < 0)
			originalTaxable = BigDecimal.ZERO;
		BigDecimal originalVat = originalTaxable.multiply(vatRate).divide(new BigDecimal("100"), 0,
				RoundingMode.HALF_UP);
		BigDecimal originalTotal = originalTaxable.add(originalVat).subtract(originalPointsDiscount);
		if (originalTotal.signum() < 0)
			originalTotal = BigDecimal.ZERO;
		int pointsEarned = 0;
		BigDecimal pointRate = storeConfigDAO.getPointRate();
		if (pointRate != null && pointRate.signum() > 0 && originalTotal.signum() > 0) {
			pointsEarned = originalTotal.divide(pointRate, 0, RoundingMode.DOWN).intValue();
		}
		int netFull = pointsUsed - pointsEarned;
		BigDecimal previousReturnedGross = sumApprovedReturnedGross(con, invoiceId);
		BigDecimal currentReturnedGross = sumReturnGross(con, returnId);
		BigDecimal returnedGross = previousReturnedGross.add(currentReturnedGross);
		int targetDelta = BigDecimal.valueOf(netFull).multiply(returnedGross)
				.divide(originalSubTotal, 0, RoundingMode.DOWN).intValue();
		int prevTarget = BigDecimal.valueOf(netFull).multiply(previousReturnedGross)
				.divide(originalSubTotal, 0, RoundingMode.DOWN).intValue();
		int delta = targetDelta - prevTarget;
		if (delta == 0)
			return;
		try (PreparedStatement ps = con.prepareStatement("UPDATE Customers SET MemberPoint = CASE "
				+ "WHEN MemberPoint + ? < 0 THEN 0 ELSE MemberPoint + ? END " + "WHERE CustomerID = ?")) {
			ps.setInt(1, delta);
			ps.setInt(2, delta);
			ps.setInt(3, customerId);
			ps.executeUpdate();
		}
	}

	private BigDecimal sumReturnGross(Connection con, int returnId) throws SQLException {
		String sql = "SELECT COALESCE(SUM(d.Quantity * d.UnitPrice), 0) "
				+ "FROM ReturnExchangeDetails d WHERE d.ReturnID = ? AND d.Direction = 'IN'";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, returnId);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? nvl(rs.getBigDecimal(1)) : BigDecimal.ZERO;
			}
		}
	}

	private boolean isSupportedRefundMethod(String method) {
		return ReturnExchange.REFUND_CASH.equalsIgnoreCase(method)

				|| ReturnExchange.REFUND_BANK_TRANSFER.equalsIgnoreCase(method)

				|| ReturnExchange.REFUND_CARD.equalsIgnoreCase(method)

				|| ReturnExchange.REFUND_PAYPAL.equalsIgnoreCase(method);
	}

	private Integer findOpenRefundShift(Connection con, int userId) throws SQLException {

		String sql = "SELECT ShiftID " + "FROM Shifts " + "WHERE UserID = ? " + "  AND Status = 'OPEN' "
				+ "ORDER BY ShiftID DESC " + "LIMIT 1 " + "FOR UPDATE";

		try (PreparedStatement ps = con.prepareStatement(sql)) {

			ps.setInt(1, userId);

			try (ResultSet rs = ps.executeQuery()) {

				if (!rs.next()) {
					return null;
				}

				return rs.getInt("ShiftID");
			}
		}
	}

	private Integer findRefundShiftOwner(Connection con, int shiftId) throws SQLException {

		String sql = "SELECT UserID " + "FROM Shifts " + "WHERE ShiftID = ? " + "AND Status = 'OPEN' " + "FOR UPDATE";

		try (PreparedStatement ps = con.prepareStatement(sql)) {

			ps.setInt(1, shiftId);

			try (ResultSet rs = ps.executeQuery()) {

				if (!rs.next()) {
					return null;
				}

				return rs.getInt("UserID");
			}
		}
	}

	private String completeCashRefund(Connection con, int returnId, int cashierUserId, int shiftId, BigDecimal amount)
			throws SQLException {

		/*
		 * Khoa ca và chắc chắn: - đúng cashier; - ca vẫn OPEN.
		 */
		shiftDAO.lockOwnedOpenShift(con, shiftId, cashierUserId);

		ShiftCashSummary summary = shiftDAO.calculateCashSummary(con, shiftId);

		BigDecimal expectedCash = summary.getExpectedCash();

		/*
		 * Không cho lấy ra khỏi két nhiều hơn số tiền hệ thống đang có.
		 */
		if (expectedCash.compareTo(amount) < 0) {
			return "Quỹ tiền mặt hiện chỉ có " + expectedCash.toPlainString() + " đ, không đủ để hoàn "
					+ amount.toPlainString() + " đ.";
		}

		String sql = "UPDATE ReturnExchanges SET " + "RefundStatus = 'COMPLETED', " + "RefundTransactionID = ?, "
				+ "RefundedBy = ?, " + "RefundedAt = CURRENT_TIMESTAMP " + "WHERE ReturnID = ? "
				+ "  AND RefundMethod = 'CASH' " + "  AND RefundStatus = 'PENDING'";

		try (PreparedStatement ps = con.prepareStatement(sql)) {

			ps.setString(1, "CASH-" + shiftId + "-RET-" + returnId);

			ps.setInt(2, cashierUserId);

			ps.setInt(3, returnId);

			int affected = ps.executeUpdate();

			if (affected != 1) {
				return "Không thể ghi nhận " + "giao dịch hoàn tiền mặt.";
			}
		}

		return null;
	}

	public String completeElectronicRefund(int returnId, int actorUserId, String transactionId) {

		if (transactionId == null || transactionId.isBlank()) {
			return "Vui lòng nhập mã giao dịch hoàn tiền.";
		}

		String cleanTransactionId = transactionId.trim();

		if (cleanTransactionId.length() > 100) {
			return "Mã giao dịch không được vượt quá 100 ký tự.";
		}

		String lockSql = "SELECT " + "Type, " + "Status, " + "RefundMethod, " + "RefundStatus "
				+ "FROM ReturnExchanges " + "WHERE ReturnID = ? " + "FOR UPDATE";

		String updateSql = "UPDATE ReturnExchanges SET " + "RefundTransactionID = ?, " + "RefundStatus = 'COMPLETED', "
				+ "RefundedBy = ?, " + "RefundedAt = CURRENT_TIMESTAMP " + "WHERE ReturnID = ? "
				+ "AND Type = 'RETURN' " + "AND Status = 'APPROVED' " + "AND RefundStatus = 'PENDING' "
				+ "AND RefundMethod IN (" + "'BANK_TRANSFER', " + "'CARD', " + "'PAYPAL'" + ")";

		try (Connection con = DBConnection.getConnection()) {

			con.setAutoCommit(false);

			try {

				String type;
				String status;
				String refundMethod;
				String refundStatus;

				/*
				 * Khóa phiếu để tránh hai người xác nhận cùng lúc.
				 */
				try (PreparedStatement ps = con.prepareStatement(lockSql)) {

					ps.setInt(1, returnId);

					try (ResultSet rs = ps.executeQuery()) {

						if (!rs.next()) {
							con.rollback();

							return "Không tìm thấy " + "yêu cầu đổi/trả.";
						}

						type = rs.getString("Type");

						status = rs.getString("Status");

						refundMethod = rs.getString("RefundMethod");

						refundStatus = rs.getString("RefundStatus");
					}
				}

				if (!ReturnExchange.TYPE_RETURN.equalsIgnoreCase(type)) {
					con.rollback();

					return "Phiếu này không phải " + "yêu cầu trả hàng.";
				}

				if (!ReturnExchange.STATUS_APPROVED.equalsIgnoreCase(status)) {
					con.rollback();

					return "Yêu cầu phải được duyệt " + "trước khi xác nhận hoàn tiền.";
				}

				if (ReturnExchange.REFUND_CASH.equalsIgnoreCase(refundMethod)) {
					con.rollback();

					return "Hoàn tiền mặt phải được " + "xử lý qua ca bán hàng.";
				}

				boolean supportedElectronicMethod = ReturnExchange.REFUND_BANK_TRANSFER.equalsIgnoreCase(refundMethod)
						|| ReturnExchange.REFUND_CARD.equalsIgnoreCase(refundMethod)
						|| ReturnExchange.REFUND_PAYPAL.equalsIgnoreCase(refundMethod);

				if (!supportedElectronicMethod) {
					con.rollback();

					return "Phương thức hoàn tiền " + "không hỗ trợ xác nhận " + "giao dịch điện tử.";
				}

				if (ReturnExchange.REFUND_STATUS_COMPLETED.equalsIgnoreCase(refundStatus)) {
					con.rollback();

					return "Khoản hoàn tiền này " + "đã được hoàn thành.";
				}

				if (!ReturnExchange.REFUND_STATUS_PENDING.equalsIgnoreCase(refundStatus)) {
					con.rollback();

					return "Khoản hoàn tiền không ở " + "trạng thái chờ xử lý.";
				}

				try (PreparedStatement ps = con.prepareStatement(updateSql)) {

					ps.setString(1, cleanTransactionId);

					ps.setInt(2, actorUserId);

					ps.setInt(3, returnId);

					int affected = ps.executeUpdate();

					if (affected != 1) {
						con.rollback();

						return "Không thể xác nhận " + "hoàn tiền.";
					}
				}

				con.commit();

			} catch (SQLException e) {

				con.rollback();

				/*
				 * MySQL duplicate key.
				 *
				 * RefundTransactionID có UNIQUE index, không cho dùng cùng mã giao dịch cho hai
				 * refund khác nhau.
				 */
				if (e.getErrorCode() == 1062) {

					return "Mã giao dịch \"" + cleanTransactionId + "\" đã được sử dụng.";
				}

				throw e;

			} finally {

				con.setAutoCommit(true);
			}

			AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.RETURN_EXCHANGE));

			AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.INVOICE));

			return null;

		} catch (SQLException e) {

			AppLogger.getInstance().error(ErrorCode.RETURN_STATUS_UPDATE_FAIL,
					"ReturnExchangeDAO." + "completeElectronicRefund" + " - returnId=" + returnId, e);

			return e.getMessage() != null ? e.getMessage() : "Xác nhận hoàn tiền thất bại.";
		}
	}

	public boolean hasPendingCashRefundForShift(int shiftId) {

		String sql = "SELECT 1 " + "FROM ReturnExchanges " + "WHERE RefundShiftID = ? " + "AND Type = 'RETURN' "
				+ "AND RefundMethod = 'CASH' " + "AND RefundStatus = 'PENDING' " + "AND Status IN ("
				+ "'PENDING', 'APPROVED'" + ") " + "LIMIT 1";

		try (Connection con = DBConnection.getConnection();

				PreparedStatement ps = con.prepareStatement(sql)) {

			ps.setInt(1, shiftId);

			try (ResultSet rs = ps.executeQuery()) {

				return rs.next();
			}

		} catch (SQLException e) {

			AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
					"ReturnExchangeDAO." + "hasPendingCashRefundForShift" + " - shiftId=" + shiftId, e);

			/*
			 * Fail-safe: không kiểm tra được DB thì xem như còn refund đang chờ.
			 *
			 * Mục tiêu là tránh đóng ca trong trạng thái không xác định.
			 */
			return true;
		}
	}

	private static BigDecimal nvl(BigDecimal v) {
		return v != null ? v : BigDecimal.ZERO;
	}
}
