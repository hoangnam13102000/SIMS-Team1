package com.dao;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.event.AppEventBus;
import com.event.DataChangedEvent;
import com.model.Invoice;
import com.model.InvoiceDetail;
import com.utils.DBConnection;
import com.utils.PaginationHelper;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class InvoiceDAO extends BaseDAO<Invoice> {

	private final StoreConfigDAO storeConfigDAO = new StoreConfigDAO();
	private final ShiftDAO shiftDAO = new ShiftDAO();

	private static final String BASE_TABLE = "Invoices inv " + "JOIN Users u ON inv.CreatedBy = u.UserID "
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
				+ "inv.SubTotal, inv.DiscountAmount, inv.PromotionID, inv.PromotionCode, "
				+ "inv.PointsUsed, inv.PointsDiscountAmount, " + "inv.VATRate, inv.VATAmount, inv.TotalAmount, "
				+ "inv.OriginalTotalAmount, " + "inv.PaymentMethod, inv.PayPalOrderID, inv.PayPalCaptureID, "
				+ "inv.Status, inv.CancelReason, inv.CancelledAt, "
				+ "(SELECT COUNT(*) FROM InvoiceDetails d WHERE d.InvoiceID = inv.InvoiceID) AS ItemCount";
	}

	@Override
	protected String getOrderBy() {
		return "inv.CreatedAt DESC, inv.InvoiceID DESC";
	}

	@Override
	protected String[] getSearchableColumns() {
		return new String[] { "inv.InvoiceCode", "u.FullName", "cu.FullName" };
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
		BigDecimal discount = rs.getBigDecimal("DiscountAmount");
		invoice.setDiscountAmount(discount != null ? discount : BigDecimal.ZERO);
		int promoId = rs.getInt("PromotionID");
		invoice.setPromotionId(rs.wasNull() ? null : promoId);
		invoice.setPromotionCode(rs.getString("PromotionCode"));
		try {
			invoice.setPointsUsed(rs.getInt("PointsUsed"));
			BigDecimal pd = rs.getBigDecimal("PointsDiscountAmount");
			invoice.setPointsDiscountAmount(pd != null ? pd : BigDecimal.ZERO);
		} catch (SQLException ignore) {
			// DB chua migration diem
		}
		invoice.setVatRate(rs.getBigDecimal("VATRate"));

		invoice.setVatAmount(rs.getBigDecimal("VATAmount"));

		invoice.setTotalAmount(rs.getBigDecimal("TotalAmount"));

		invoice.setOriginalTotalAmount(rs.getBigDecimal("OriginalTotalAmount"));

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
	 * Tim kiem + loc hoa don theo tu khoa (ma HD/nguoi tao/khach hang) va/hoac
	 * khoang ngay tao (ca 2 dau co the null neu khong loc).
	 */
	public PaginationHelper.PaginationResult<Invoice> getPagedFiltered(int page, int pageSize, String keyword,
			LocalDate fromDate, LocalDate toDate) {

		return getPagedFiltered(page, pageSize, keyword, fromDate, toDate, null);
	}

	public PaginationHelper.PaginationResult<Invoice> getPagedFiltered(int page, int pageSize, String keyword,
			LocalDate fromDate, LocalDate toDate, Integer createdByUserId) {

		List<String> conditions = new ArrayList<>();
		List<Object> params = new ArrayList<>();

		String trimmedKeyword = keyword == null ? "" : keyword.trim();

		if (!trimmedKeyword.isEmpty()) {

			String[] columns = getSearchableColumns();

			String likeParam = "%" + escapeLike(trimmedKeyword) + "%";

			StringBuilder keywordCondition = new StringBuilder("(");

			for (int i = 0; i < columns.length; i++) {

				if (i > 0) {
					keywordCondition.append(" OR ");
				}

				keywordCondition.append(columns[i]).append(" LIKE ? ESCAPE '!'");

				params.add(likeParam);
			}

			keywordCondition.append(")");

			conditions.add(keywordCondition.toString());
		}

		if (fromDate != null) {
			conditions.add("inv.CreatedAt >= ?");
			params.add(Timestamp.valueOf(fromDate.atStartOfDay()));
		}

		if (toDate != null) {
			conditions.add("inv.CreatedAt < ?");
			params.add(Timestamp.valueOf(toDate.plusDays(1).atStartOfDay()));
		}

		/*
		 * Đây là điều kiện ownership quan trọng.
		 *
		 * SALES_STAFF: inv.CreatedBy = UserID hiện tại
		 *
		 * Manager/Admin: createdByUserId = null => không thêm điều kiện này.
		 */
		if (createdByUserId != null) {
			conditions.add("inv.CreatedBy = ?");
			params.add(createdByUserId);
		}

		String whereClause = conditions.isEmpty() ? null : String.join(" AND ", conditions);

		PaginationHelper.PaginationResult<Invoice> result = getPaged(page, pageSize, whereClause, params.toArray());

		if (result != null && result.getData() != null) {
			attachReturnSummary(result.getData());
		}

		return result;
	}

	public List<Invoice> getAllFiltered(Integer createdByUserId) {

		if (createdByUserId == null) {

			List<Invoice> all = getAll();

			attachReturnSummary(all);

			return all;
		}

		List<Invoice> list = new ArrayList<>();

		String sql = "SELECT " + getColumns() + " FROM " + getTableName() + " WHERE inv.CreatedBy = ?" + " ORDER BY "
				+ getOrderBy();

		try (Connection con = DBConnection.getConnection();

				PreparedStatement ps = con.prepareStatement(sql)) {

			ps.setInt(1, createdByUserId);

			try (ResultSet rs = ps.executeQuery()) {

				while (rs.next()) {
					list.add(mapResultSet(rs));
				}
			}

			attachReturnSummary(list);

		} catch (SQLException e) {

			AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
					"InvoiceDAO.getAllFiltered - userId=" + createdByUserId, e);
		}

		return list;
	}

	private String escapeLike(String raw) {
		return raw.replace("!", "!!").replace("%", "!%").replace("_", "!_");
	}

	/**
	 * Lap 1 hoa don ban hang THAT SU (POS).
	 */
	/**
	 * true neu TAT CA productId truyen vao hien van Status = 'ACTIVE' VA category
	 * cha cua no cung Status = 'ACTIVE'. Dung ben trong transaction cua
	 * {@link #createInvoice} de chan ban hang cho san pham da bi khoa hoac thuoc
	 * danh muc da ngung ban - xem chi tiet o noi goi.
	 */
	private boolean allProductsSellable(Connection con, Set<Integer> productIds) throws SQLException {
		if (productIds.isEmpty())
			return true;
		StringBuilder sql = new StringBuilder(
				"SELECT COUNT(*) FROM Products p JOIN Categories c ON c.CategoryID = p.CategoryID "
						+ "WHERE p.Status = 'ACTIVE' AND c.Status = 'ACTIVE' AND p.ProductID IN (");
		for (int i = 0; i < productIds.size(); i++) {
			sql.append(i == 0 ? "?" : ", ?");
		}
		sql.append(")");
		try (PreparedStatement ps = con.prepareStatement(sql.toString())) {
			int idx = 1;
			for (Integer id : productIds) {
				ps.setInt(idx++, id);
			}
			try (ResultSet rs = ps.executeQuery()) {
				int sellableCount = rs.next() ? rs.getInt(1) : 0;
				return sellableCount == productIds.size();
			}
		}
	}

	public boolean createInvoice(Invoice invoice, List<InvoiceDetail> items) {
		if (invoice == null || items == null || items.isEmpty()) {
			return false;
		}

		if (invoice.getShiftId() <= 0 || invoice.getCreatedBy() <= 0) {
			return false;
		}

		String insertInvoiceSql = "INSERT INTO Invoices "
				+ "(InvoiceCode, ShiftID, CreatedBy, CustomerID, PaymentMethod, PayPalOrderID, PayPalCaptureID, "
				+ "VATRate, SubTotal, TotalAmount, DiscountAmount, PromotionID, PromotionCode, "
				+ "PointsUsed, PointsDiscountAmount) " + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, 0, 0, ?, ?, 0, 0)";
		String insertDetailSql = "INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice) VALUES (?, ?, ?, ?)";
		String sumLineTotalSql = "SELECT COALESCE(SUM(LineTotal), 0) FROM InvoiceDetails WHERE InvoiceID = ?";
		String updateTotalsSql = "UPDATE Invoices SET " + "InvoiceCode = ?, " + "SubTotal = ?, " + "TotalAmount = ?, "
				+ "OriginalTotalAmount = ?, " + "DiscountAmount = ?, " + "PointsUsed = ?, "
				+ "PointsDiscountAmount = ? " + "WHERE InvoiceID = ?";

		try (Connection con = DBConnection.getConnection()) {
			con.setAutoCommit(false);
			try {
				int invoiceId;

				shiftDAO.lockOwnedOpenShift(con, invoice.getShiftId(), invoice.getCreatedBy());

				BigDecimal requestedDiscount = invoice.getDiscountAmount() != null ? invoice.getDiscountAmount()
						: BigDecimal.ZERO;
				if (requestedDiscount.signum() < 0)
					requestedDiscount = BigDecimal.ZERO;

				// Kiem tra LAI ngay trong transaction (khong chi tin vao du lieu
				// POS da nap luc dau phien lam viec): tung san pham trong gio
				// hang van phai Status = ACTIVE VA category cha cung phai
				// Status = ACTIVE. Phong truong hop giua luc thu ngan dang phuc
				// vu khach (san pham da nam san trong gio hang tu truoc) thi
				// Admin vua khoa san pham hoac ngung ban ca danh muc o 1 tab
				// quan tri khac - neu khong chan o day, hoa don van duoc tao
				// binh thuong du san pham "khong con ban duoc" nua.
				Set<Integer> productIds = new LinkedHashSet<>();
				for (InvoiceDetail item : items)
					productIds.add(item.getProductId());
				if (!allProductsSellable(con, productIds)) {
					con.rollback();
					return false;
				}

				try (PreparedStatement ps = con.prepareStatement(insertInvoiceSql, Statement.RETURN_GENERATED_KEYS)) {
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
					if (invoice.getPromotionId() != null) {
						ps.setInt(9, invoice.getPromotionId());
					} else {
						ps.setNull(9, Types.INTEGER);
					}
					if (invoice.getPromotionCode() != null) {
						ps.setString(10, invoice.getPromotionCode());
					} else {
						ps.setNull(10, Types.VARCHAR);
					}
					ps.executeUpdate();

					try (ResultSet keys = ps.getGeneratedKeys()) {
						if (!keys.next())
							throw new SQLException("Khong lay duoc InvoiceID vua tao.");
						invoiceId = keys.getInt(1);
					}
				}

				try (PreparedStatement ps = con.prepareStatement(insertDetailSql)) {
					for (InvoiceDetail item : items) {
						ps.setInt(1, invoiceId);
						ps.setInt(2, item.getProductId());
						ps.setInt(3, item.getQuantity());
						ps.setBigDecimal(4, item.getUnitPrice());
						ps.executeUpdate();
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
					con.rollback();
					return false;
				}

				BigDecimal discount = requestedDiscount.min(subTotal);
				BigDecimal taxable = subTotal.subtract(discount);
				BigDecimal vatRate = invoice.getVatRate() != null ? invoice.getVatRate() : BigDecimal.ZERO;
				BigDecimal totalBeforePoints = taxable
						.add(taxable.multiply(vatRate).divide(new BigDecimal(100), 0, java.math.RoundingMode.HALF_UP));

				int pointsUsed = Math.max(0, invoice.getPointsUsed());
				BigDecimal pointsDiscount = BigDecimal.ZERO;
				if (invoice.getCustomerId() != null && pointsUsed > 0) {
					BigDecimal redeemRate = storeConfigDAO.getPointRedeemRate();
					int available = 0;
					try (PreparedStatement ps = con
							.prepareStatement("SELECT MemberPoint FROM Customers WHERE CustomerID = ? FOR UPDATE")) {
						ps.setInt(1, invoice.getCustomerId());
						try (ResultSet rs = ps.executeQuery()) {
							if (rs.next())
								available = Math.max(0, rs.getInt(1));
						}
					}
					pointsUsed = Math.min(pointsUsed, available);
					pointsDiscount = redeemRate.multiply(BigDecimal.valueOf(pointsUsed)).setScale(0,
							java.math.RoundingMode.DOWN);
					if (pointsDiscount.compareTo(totalBeforePoints) > 0) {
						pointsDiscount = totalBeforePoints;
						if (redeemRate.signum() > 0) {
							pointsUsed = pointsDiscount.divide(redeemRate, 0, java.math.RoundingMode.DOWN).intValue();
							pointsDiscount = redeemRate.multiply(BigDecimal.valueOf(pointsUsed)).setScale(0,
									java.math.RoundingMode.DOWN);
						} else {
							pointsUsed = 0;
							pointsDiscount = BigDecimal.ZERO;
						}
					}
					if (pointsUsed > 0) {
						try (PreparedStatement ps = con
								.prepareStatement("UPDATE Customers SET MemberPoint = MemberPoint - ? "
										+ "WHERE CustomerID = ? AND MemberPoint >= ?")) {
							ps.setInt(1, pointsUsed);
							ps.setInt(2, invoice.getCustomerId());
							ps.setInt(3, pointsUsed);
							int updated = ps.executeUpdate();
							if (updated != 1) {
								pointsUsed = 0;
								pointsDiscount = BigDecimal.ZERO;
							}
						}
					}
				} else {
					pointsUsed = 0;
					pointsDiscount = BigDecimal.ZERO;
				}

				BigDecimal totalAmount = totalBeforePoints.subtract(pointsDiscount);
				if (totalAmount.signum() < 0)
					totalAmount = BigDecimal.ZERO;

				String invoiceCode = "HD-"
						+ java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
						+ "-" + String.format("%04d", invoiceId);

				try (PreparedStatement ps = con.prepareStatement(updateTotalsSql)) {

					ps.setString(1, invoiceCode);

					ps.setBigDecimal(2, subTotal);

					/*
					 * Giá trị hiện tại của hóa đơn. Sau RETURN có thể bị trigger giảm.
					 */
					ps.setBigDecimal(3, totalAmount);

					/*
					 * Tổng tiền lúc bán ban đầu.
					 *
					 * Chỉ ghi khi tạo hóa đơn. Trigger RETURN/EXCHANGE không được thay đổi cột này.
					 */
					ps.setBigDecimal(4, totalAmount);

					ps.setBigDecimal(5, discount);

					ps.setInt(6, pointsUsed);

					ps.setBigDecimal(7, pointsDiscount);

					ps.setInt(8, invoiceId);

					ps.executeUpdate();
				}

				int pointsEarned = 0;
				if (invoice.getCustomerId() != null && totalAmount.signum() > 0) {
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

				if (invoice.getPromotionId() != null && discount.signum() > 0) {
					try (PreparedStatement ps = con.prepareStatement(
							"UPDATE Promotions SET UsedCount = UsedCount + 1 WHERE PromotionID = ?")) {
						ps.setInt(1, invoice.getPromotionId());
						ps.executeUpdate();
					}
				}

				con.commit();
				invoice.setInvoiceId(invoiceId);
				invoice.setInvoiceCode(invoiceCode);
				invoice.setSubTotal(subTotal);
				invoice.setDiscountAmount(discount);
				invoice.setTotalAmount(totalAmount);
				invoice.setOriginalTotalAmount(totalAmount);
				invoice.setPointsUsed(pointsUsed);
				invoice.setPointsDiscountAmount(pointsDiscount);
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

	/** Danh sach dong SP + so luong da tra (APPROVED). */
	public List<InvoiceDetail> getDetails(int invoiceId) {
		String sql = "SELECT d.InvoiceDetailID, d.InvoiceID, d.ProductID, p.ProductName, p.ProductCode, "
				+ "p.ImageUrl, d.Quantity, d.UnitPrice, d.LineTotal, " + "COALESCE(( "
				+ "  SELECT SUM(rd.Quantity) FROM ReturnExchangeDetails rd "
				+ "  JOIN ReturnExchanges r ON r.ReturnID = rd.ReturnID "
				+ "  WHERE r.InvoiceID = d.InvoiceID AND r.Status = 'APPROVED' "
				+ "    AND rd.Direction = 'IN' AND rd.ProductID = d.ProductID " + "), 0) AS ReturnedQty "
				+ "FROM InvoiceDetails d " + "JOIN Products p ON p.ProductID = d.ProductID " + "WHERE d.InvoiceID = ? "
				+ "ORDER BY d.InvoiceDetailID ASC";

		List<InvoiceDetail> list = new ArrayList<>();
		try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
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
					try {
						detail.setReturnedQuantity(rs.getInt("ReturnedQty"));
					} catch (SQLException ignore) {
						detail.setReturnedQuantity(0);
					}
					list.add(detail);
				}
			}
		} catch (Exception e) {
			AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "InvoiceDAO.getDetails - invoiceId=" + invoiceId, e);
		}
		return list;
	}

	/**
	 * Huy hoa don ACTIVE + hoan diem da dung, thu hoi diem da tich, giam UsedCount
	 * KM. Tra ve null neu OK, message loi neu that bai.
	 */
	public String cancelInvoice(int invoiceId, String reason, Integer createdByUserId) {

		if (reason == null || reason.isBlank()) {
			return "Vui lòng nhập lý do hủy hóa đơn.";
		}

		/*
		 * createdByUserId:
		 *
		 * != null: SALES_STAFF chỉ được thao tác hóa đơn của chính mình.
		 *
		 * == null: Manager/Admin được thao tác trong phạm vi rộng hơn.
		 */
		String lockSql = "SELECT " + "inv.InvoiceID, " + "inv.Status, " + "inv.CustomerID, " + "inv.PromotionID, "
				+ "inv.PointsUsed, " + "inv.TotalAmount, " + "inv.DiscountAmount, " + "inv.PaymentMethod, "
				+ "inv.CreatedBy, " + "EXISTS (" + "   SELECT 1 " + "   FROM Orders o "
				+ "   WHERE o.InvoiceID = inv.InvoiceID" + ") AS IsOnline " + "FROM Invoices inv "
				+ "WHERE inv.InvoiceID = ? " + (createdByUserId != null ? "AND inv.CreatedBy = ? " : "") + "FOR UPDATE";

		String activeReturnSql = "SELECT ReturnID, Status " + "FROM ReturnExchanges " + "WHERE InvoiceID = ? "
				+ "AND Status IN ('PENDING', 'APPROVED') " + "ORDER BY ReturnID DESC " + "LIMIT 1 " + "FOR UPDATE";

		String cancelSql = "UPDATE Invoices SET " + "Status = 'CANCELLED', " + "CancelReason = ?, "
				+ "CancelledAt = CURRENT_TIMESTAMP " + "WHERE InvoiceID = ? " + "AND Status = 'ACTIVE'";

		try (Connection con = DBConnection.getConnection()) {

			con.setAutoCommit(false);

			try {

				Integer customerId = null;
				Integer promotionId = null;

				int pointsUsed = 0;

				BigDecimal totalAmount = BigDecimal.ZERO;

				BigDecimal discountAmount = BigDecimal.ZERO;

				String paymentMethod;
				String status;

				boolean onlineInvoice;

				/*
				 * ========================================================= 1. Khóa hóa đơn +
				 * áp dụng ownership =========================================================
				 */
				try (PreparedStatement ps = con.prepareStatement(lockSql)) {

					int index = 1;

					ps.setInt(index++, invoiceId);

					if (createdByUserId != null) {

						ps.setInt(index, createdByUserId);
					}

					try (ResultSet rs = ps.executeQuery()) {

						if (!rs.next()) {

							con.rollback();

							return createdByUserId != null
									? "Không tìm thấy hóa đơn " + "hoặc bạn chỉ được hủy "
											+ "hóa đơn do chính mình lập."
									: "Không tìm thấy hóa đơn.";
						}

						status = rs.getString("Status");

						if (!"ACTIVE".equalsIgnoreCase(status)) {

							con.rollback();

							return "Hóa đơn đã được hủy " + "hoặc không còn hoạt động.";
						}

						int cid = rs.getInt("CustomerID");

						customerId = rs.wasNull() ? null : cid;

						int pid = rs.getInt("PromotionID");

						promotionId = rs.wasNull() ? null : pid;

						pointsUsed = Math.max(0, rs.getInt("PointsUsed"));

						totalAmount = rs.getBigDecimal("TotalAmount");

						if (totalAmount == null) {
							totalAmount = BigDecimal.ZERO;
						}

						discountAmount = rs.getBigDecimal("DiscountAmount");

						if (discountAmount == null) {
							discountAmount = BigDecimal.ZERO;
						}

						paymentMethod = rs.getString("PaymentMethod");

						onlineInvoice = rs.getBoolean("IsOnline");
					}
				}

				/*
				 * ========================================================= 2. Hóa đơn online
				 * phải đi qua OrderDAO
				 * =========================================================
				 */
				if (onlineInvoice) {

					con.rollback();

					return "Hóa đơn này thuộc đơn hàng online. " + "Vui lòng hủy từ trang " + "Quản lý đơn hàng.";
				}

				/*
				 * ========================================================= 3. Không hủy trực
				 * tiếp thanh toán điện tử
				 * =========================================================
				 *
				 * Vì đánh dấu CANCELLED trong DB không đồng nghĩa PayPal/Card/Bank đã hoàn tiền
				 * thật cho khách.
				 */
				if (!"CASH".equalsIgnoreCase(paymentMethod)) {

					con.rollback();

					return "Hóa đơn thanh toán bằng " + paymentMethod + " không thể hủy trực tiếp. "
							+ "Vui lòng dùng chức năng " + "Đổi / Trả hàng để ghi nhận "
							+ "hoàn tiền đúng phương thức.";
				}

				/*
				 * ========================================================= 4. Không hủy nếu đã
				 * có đổi/trả đang xử lý
				 * =========================================================
				 *
				 * Nếu đã RETURN rồi mà trigger CANCEL lại hoàn toàn bộ InvoiceDetailBatches thì
				 * có nguy cơ cộng tồn hai lần.
				 */
				try (PreparedStatement ps = con.prepareStatement(activeReturnSql)) {

					ps.setInt(1, invoiceId);

					try (ResultSet rs = ps.executeQuery()) {

						if (rs.next()) {

							String returnStatus = rs.getString("Status");

							con.rollback();

							return "Hóa đơn đã có phiếu đổi/trả " + returnStatus + ". Không thể hủy toàn bộ hóa đơn. "
									+ "Hãy tiếp tục xử lý bằng " + "chức năng Đổi / Trả hàng.";
						}
					}
				}

				/*
				 * ========================================================= 5. Hủy hóa đơn
				 * =========================================================
				 *
				 * Trigger MySQL tiếp tục chịu trách nhiệm: - cùng ngày; - ca đang OPEN; - hoàn
				 * InventoryBatch; - đồng bộ Products.Stock.
				 */
				try (PreparedStatement ps = con.prepareStatement(cancelSql)) {

					ps.setString(1, reason.trim());

					ps.setInt(2, invoiceId);

					int affected = ps.executeUpdate();

					if (affected != 1) {

						con.rollback();

						return "Hóa đơn đã được hủy " + "hoặc không còn hoạt động.";
					}
				}

				/*
				 * ========================================================= 6. Hoàn / thu hồi
				 * điểm =========================================================
				 */
				if (customerId != null) {

					int pointsEarned = 0;

					if (totalAmount.signum() > 0) {

						BigDecimal pointRate = storeConfigDAO.getPointRate();

						if (pointRate != null && pointRate.signum() > 0) {

							pointsEarned = totalAmount.divide(pointRate, 0, java.math.RoundingMode.DOWN).intValue();
						}
					}

					int delta = pointsUsed - pointsEarned;

					if (delta != 0) {

						String pointSql = "UPDATE Customers SET " + "MemberPoint = CASE " + "WHEN MemberPoint + ? < 0 "
								+ "THEN 0 " + "ELSE MemberPoint + ? END " + "WHERE CustomerID = ?";

						try (PreparedStatement ps = con.prepareStatement(pointSql)) {

							ps.setInt(1, delta);

							ps.setInt(2, delta);

							ps.setInt(3, customerId);

							ps.executeUpdate();
						}
					}
				}

				/*
				 * ========================================================= 7. Trả UsedCount
				 * promotion =========================================================
				 */
				if (promotionId != null && discountAmount.signum() > 0) {

					String promotionSql = "UPDATE Promotions SET " + "UsedCount = CASE " + "WHEN UsedCount > 0 "
							+ "THEN UsedCount - 1 " + "ELSE 0 END " + "WHERE PromotionID = ?";

					try (PreparedStatement ps = con.prepareStatement(promotionSql)) {

						ps.setInt(1, promotionId);

						ps.executeUpdate();
					}
				}

				con.commit();

				AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.INVOICE));

				return null;

			} catch (SQLException e) {

				con.rollback();

				throw e;

			} finally {

				con.setAutoCommit(true);
			}

		} catch (SQLException e) {

			AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL,
					"InvoiceDAO.cancelInvoice" + " - invoiceId=" + invoiceId, e);

			return e.getMessage() != null ? e.getMessage() : "Hủy hóa đơn thất bại.";
		}
	}

	@Override
	public PaginationHelper.PaginationResult<Invoice> search(String keyword, int pageNumber, int pageSize) {
		PaginationHelper.PaginationResult<Invoice> result = super.search(keyword, pageNumber, pageSize);
		if (result != null && result.getData() != null) {
			attachReturnSummary(result.getData());
		}
		return result;
	}

	@Override
	public PaginationHelper.PaginationResult<Invoice> getPaged(int pageNumber, int pageSize) {
		PaginationHelper.PaginationResult<Invoice> result = super.getPaged(pageNumber, pageSize);
		if (result != null && result.getData() != null) {
			attachReturnSummary(result.getData());
		}
		return result;
	}

	// ---------------------------------------------------------------
	// Tom tat doi/tra hang (khong luu DB - tinh tu ReturnExchanges)
	// ---------------------------------------------------------------

	public void attachReturnSummary(Invoice invoice) {
		if (invoice == null)
			return;
		List<Invoice> one = new ArrayList<>();
		one.add(invoice);
		attachReturnSummary(one);
	}

	public void attachReturnSummary(List<Invoice> invoices) {
		if (invoices == null || invoices.isEmpty())
			return;
		for (Invoice inv : invoices) {
			fillReturnSummary(inv);
		}
	}

	private void fillReturnSummary(Invoice inv) {
		String sqlRefund = "SELECT "

				+ "COALESCE(SUM(" + " CASE " + "   WHEN Status = 'APPROVED' " + "   THEN TotalValue " + "   ELSE 0 "
				+ " END" + "), 0) AS ApprovedRefund, "

				+ "COALESCE(SUM(" + " CASE " + "   WHEN Status = 'APPROVED' " + "    AND RefundStatus = 'COMPLETED' "
				+ "   THEN TotalValue " + "   ELSE 0 " + " END" + "), 0) AS CompletedRefund, "

				+ "SUM(" + " CASE " + "   WHEN Status = 'APPROVED' " + "   THEN 1 " + "   ELSE 0 " + " END"
				+ ") AS Cnt "

				+ "FROM ReturnExchanges " + "WHERE InvoiceID = ?";

		String sqlOriginal = "SELECT COALESCE(SUM(Quantity * UnitPrice), 0) AS OriginalSub "
				+ "FROM InvoiceDetails WHERE InvoiceID = ?";

		String sqlQty = "SELECT "
				+ "COALESCE((SELECT SUM(d.Quantity) FROM InvoiceDetails d WHERE d.InvoiceID = ?), 0) AS SoldQty, "
				+ "COALESCE((SELECT SUM(rd.Quantity) FROM ReturnExchangeDetails rd "
				+ "         JOIN ReturnExchanges r ON r.ReturnID = rd.ReturnID "
				+ "         WHERE r.InvoiceID = ? AND r.Status = 'APPROVED' AND rd.Direction = 'IN'), 0) AS ReturnedQty";

		try (Connection con = DBConnection.getConnection()) {
			try (PreparedStatement ps = con.prepareStatement(sqlRefund)) {
				ps.setInt(1, inv.getInvoiceId());
				try (ResultSet rs = ps.executeQuery()) {
					if (rs.next()) {

						BigDecimal approvedRefund = rs.getBigDecimal("ApprovedRefund");

						inv.setRefundedAmount(approvedRefund != null ? approvedRefund : BigDecimal.ZERO);

						BigDecimal completedRefund = rs.getBigDecimal("CompletedRefund");

						inv.setCompletedRefundAmount(completedRefund != null ? completedRefund : BigDecimal.ZERO);

						inv.setApprovedReturnCount(rs.getInt("Cnt"));
					}
				}
			}
			try (PreparedStatement ps = con.prepareStatement(sqlOriginal)) {
				ps.setInt(1, inv.getInvoiceId());
				try (ResultSet rs = ps.executeQuery()) {
					if (rs.next()) {
						BigDecimal orig = rs.getBigDecimal("OriginalSub");
						inv.setOriginalSubTotal(orig != null ? orig : BigDecimal.ZERO);
					}
				}
			}
			try (PreparedStatement ps = con.prepareStatement(sqlQty)) {
				ps.setInt(1, inv.getInvoiceId());
				ps.setInt(2, inv.getInvoiceId());
				try (ResultSet rs = ps.executeQuery()) {
					if (rs.next()) {
						int sold = rs.getInt("SoldQty");
						int returned = rs.getInt("ReturnedQty");
						if (returned <= 0) {
							inv.setReturnState("NONE");
						} else if (sold > 0 && returned >= sold) {
							inv.setReturnState("FULL");
						} else {
							inv.setReturnState("PARTIAL");
						}
					}
				}
			}
		} catch (Exception e) {
			AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
					"InvoiceDAO.fillReturnSummary - invoiceId=" + inv.getInvoiceId(), e);
		}
	}

	public Invoice findById(int invoiceId) {
		String sql = "SELECT " + getColumns() + " FROM " + getTableName() + " WHERE inv.InvoiceID = ?";
		try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, invoiceId);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					Invoice inv = mapResultSet(rs);
					attachReturnSummary(inv);
					return inv;
				}
			}
		} catch (Exception e) {
			AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "InvoiceDAO.findById - invoiceId=" + invoiceId, e);
		}
		return null;
	}

	public Invoice findByIdVisible(int invoiceId, Integer createdByUserId) {

		String sql = "SELECT " + getColumns() + " FROM " + getTableName() + " WHERE inv.InvoiceID = ?"
				+ (createdByUserId != null ? " AND inv.CreatedBy = ?" : "");

		try (Connection con = DBConnection.getConnection();

				PreparedStatement ps = con.prepareStatement(sql)) {

			ps.setInt(1, invoiceId);

			if (createdByUserId != null) {
				ps.setInt(2, createdByUserId);
			}

			try (ResultSet rs = ps.executeQuery()) {

				if (rs.next()) {

					Invoice invoice = mapResultSet(rs);

					attachReturnSummary(invoice);

					return invoice;
				}
			}

		} catch (SQLException e) {

			AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
					"InvoiceDAO.findByIdVisible - invoiceId=" + invoiceId + ", userId=" + createdByUserId, e);
		}

		return null;
	}

	public BigDecimal sumTodayRevenue() {
		String sql = "SELECT COALESCE(SUM(TotalAmount), 0) FROM Invoices "
				+ "WHERE Status = 'ACTIVE' AND CAST(CreatedAt AS DATE) = CAST(CURRENT_TIMESTAMP AS DATE)";
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