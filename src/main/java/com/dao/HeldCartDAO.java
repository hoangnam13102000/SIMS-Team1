package com.dao;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.model.CartItem;
import com.model.HeldCart;
import com.model.HeldCartItem;
import com.service.PosCartService;
import com.utils.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** DAO luu/lay gio hang tam giu cua POS. */
public class HeldCartDAO {

    public HeldCart create(int shiftId, int heldBy, PosCartService cart, String paymentMethod, String note) throws SQLException {
        String insertCart = "INSERT INTO HeldCarts (ShiftID, HeldBy, CustomerID, CustomerLabelSnapshot, "
                + "PromotionID, PromotionCode, PaymentMethodSnapshot, PointsToUse, SubTotalSnapshot, DiscountSnapshot, "
                + "PointsDiscountSnapshot, Status, Note, HeldAt) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'HELD', ?, CURRENT_TIMESTAMP)";
        String insertItem = "INSERT INTO HeldCartItems (HoldID, ProductID, ProductCodeSnapshot, "
                + "ProductNameSnapshot, Quantity, UnitPriceSnapshot) VALUES (?, ?, ?, ?, ?, ?)";
        String updateCode = "UPDATE HeldCarts SET HoldCode=? WHERE HoldID=?";

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                long holdId;
                try (PreparedStatement ps = con.prepareStatement(insertCart, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, shiftId);
                    ps.setInt(2, heldBy);
                    setNullableInt(ps, 3, cart.getCustomerId());
                    ps.setString(4, cart.getCustomerLabel());
                    Integer promotionId = cart.getAppliedPromotion() != null
                            ? cart.getAppliedPromotion().getPromotionId() : null;
                    setNullableInt(ps, 5, promotionId);
                    ps.setString(6, cart.getAppliedPromotion() != null ? cart.getAppliedPromotion().getCode() : null);
                    ps.setString(7, normalizePaymentMethod(paymentMethod));
                    ps.setInt(8, cart.getPointsToUse());
                    ps.setBigDecimal(9, cart.getSubTotalDecimal());
                    ps.setBigDecimal(10, cart.getDiscountAmount());
                    ps.setBigDecimal(11, cart.getPointsDiscountAmount());
                    ps.setString(12, normalizeNote(note));
                    if (ps.executeUpdate() != 1) throw new SQLException("Khong tao duoc phieu tam giu");
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Khong lay duoc HoldID");
                        holdId = keys.getLong(1);
                    }
                }

                String holdCode = "HG-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                        + "-" + String.format("%06d", holdId);
                try (PreparedStatement ps = con.prepareStatement(updateCode)) {
                    ps.setString(1, holdCode);
                    ps.setLong(2, holdId);
                    if (ps.executeUpdate() != 1) throw new SQLException("Khong tao duoc ma phieu tam giu");
                }

                try (PreparedStatement ps = con.prepareStatement(insertItem)) {
                    for (CartItem item : new ArrayList<>(cart.getItems())) {
                        ps.setLong(1, holdId);
                        ps.setInt(2, item.getProduct().getProductId());
                        ps.setString(3, item.getProduct().getProductCode());
                        ps.setString(4, item.getProduct().getProductName());
                        ps.setInt(5, item.getQuantity());
                        ps.setBigDecimal(6, item.getProduct().getSellPrice());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                con.commit();
                HeldCart created = findById(holdId, heldBy, shiftId);
                if (created == null) {
                    created = new HeldCart();
                    created.setHoldId(holdId);
                    created.setHoldCode(holdCode);
                    created.setShiftId(shiftId);
                    created.setHeldBy(heldBy);
                }
                return created;
            } catch (Exception e) {
                con.rollback();
                if (e instanceof SQLException se) throw se;
                throw new SQLException("Khong the tam giu gio hang", e);
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    public List<HeldCart> findHeldForShift(int heldBy, int shiftId, String keyword) {
        List<HeldCart> result = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT h.*, u.FullName AS HeldByName, "
                        + "(SELECT COUNT(*) FROM HeldCartItems i WHERE i.HoldID=h.HoldID) AS ItemCount "
                        + "FROM HeldCarts h JOIN Users u ON u.UserID=h.HeldBy "
                        + "WHERE h.HeldBy=? AND h.ShiftID=? AND h.Status='HELD' ");
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        if (hasKeyword) {
            sql.append("AND (h.HoldCode LIKE ? ESCAPE '!' OR COALESCE(h.CustomerLabelSnapshot,'') LIKE ? ESCAPE '!' "
                    + "OR COALESCE(h.Note,'') LIKE ? ESCAPE '!') ");
        }
        sql.append("ORDER BY h.HeldAt DESC, h.HoldID DESC");

        try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql.toString())) {
            ps.setInt(1, heldBy);
            ps.setInt(2, shiftId);
            if (hasKeyword) {
                String like = "%" + escapeLike(keyword.trim()) + "%";
                ps.setString(3, like);
                ps.setString(4, like);
                ps.setString(5, like);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapCart(rs));
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "HeldCartDAO.findHeldForShift - shiftId=" + shiftId, e);
        }
        return result;
    }

    public HeldCart findById(long holdId, int heldBy, int shiftId) {
        String sql = "SELECT h.*, u.FullName AS HeldByName, "
                + "(SELECT COUNT(*) FROM HeldCartItems i WHERE i.HoldID=h.HoldID) AS ItemCount "
                + "FROM HeldCarts h JOIN Users u ON u.UserID=h.HeldBy "
                + "WHERE h.HoldID=? AND h.HeldBy=? AND h.ShiftID=? LIMIT 1";
        try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, holdId);
            ps.setInt(2, heldBy);
            ps.setInt(3, shiftId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                HeldCart cart = mapCart(rs);
                cart.setItems(findItems(con, holdId));
                return cart;
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "HeldCartDAO.findById - holdId=" + holdId, e);
            return null;
        }
    }

    public boolean markRestored(long holdId, int heldBy, int shiftId) {
        String sql = "UPDATE HeldCarts SET Status='RESTORED', RestoredAt=CURRENT_TIMESTAMP "
                + "WHERE HoldID=? AND HeldBy=? AND ShiftID=? AND Status='HELD'";
        return updateStatus(sql, holdId, heldBy, shiftId, "markRestored");
    }

    public boolean cancel(long holdId, int heldBy, int shiftId) {
        String sql = "UPDATE HeldCarts SET Status='CANCELLED', CancelledAt=CURRENT_TIMESTAMP "
                + "WHERE HoldID=? AND HeldBy=? AND ShiftID=? AND Status='HELD'";
        return updateStatus(sql, holdId, heldBy, shiftId, "cancel");
    }

    private boolean updateStatus(String sql, long holdId, int heldBy, int shiftId, String action) {
        try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, holdId);
            ps.setInt(2, heldBy);
            ps.setInt(3, shiftId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL,
                    "HeldCartDAO." + action + " - holdId=" + holdId, e);
            return false;
        }
    }

    private List<HeldCartItem> findItems(Connection con, long holdId) throws SQLException {
        String sql = "SELECT HoldItemID, HoldID, ProductID, ProductCodeSnapshot, ProductNameSnapshot, "
                + "Quantity, UnitPriceSnapshot FROM HeldCartItems WHERE HoldID=? ORDER BY HoldItemID";
        List<HeldCartItem> items = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, holdId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    HeldCartItem item = new HeldCartItem();
                    item.setHoldItemId(rs.getLong("HoldItemID"));
                    item.setHoldId(rs.getLong("HoldID"));
                    item.setProductId(rs.getInt("ProductID"));
                    item.setProductCodeSnapshot(rs.getString("ProductCodeSnapshot"));
                    item.setProductNameSnapshot(rs.getString("ProductNameSnapshot"));
                    item.setQuantity(rs.getInt("Quantity"));
                    item.setUnitPriceSnapshot(rs.getBigDecimal("UnitPriceSnapshot"));
                    items.add(item);
                }
            }
        }
        return items;
    }

    private HeldCart mapCart(ResultSet rs) throws SQLException {
        HeldCart h = new HeldCart();
        h.setHoldId(rs.getLong("HoldID"));
        h.setHoldCode(rs.getString("HoldCode"));
        h.setShiftId(rs.getInt("ShiftID"));
        h.setHeldBy(rs.getInt("HeldBy"));
        h.setHeldByName(rs.getString("HeldByName"));
        int customerId = rs.getInt("CustomerID");
        h.setCustomerId(rs.wasNull() ? null : customerId);
        h.setCustomerLabelSnapshot(rs.getString("CustomerLabelSnapshot"));
        int promotionId = rs.getInt("PromotionID");
        h.setPromotionId(rs.wasNull() ? null : promotionId);
        h.setPromotionCode(rs.getString("PromotionCode"));
        h.setPaymentMethodSnapshot(rs.getString("PaymentMethodSnapshot"));
        h.setPointsToUse(rs.getInt("PointsToUse"));
        h.setSubTotalSnapshot(rs.getBigDecimal("SubTotalSnapshot"));
        h.setDiscountSnapshot(rs.getBigDecimal("DiscountSnapshot"));
        h.setPointsDiscountSnapshot(rs.getBigDecimal("PointsDiscountSnapshot"));
        h.setStatus(rs.getString("Status"));
        h.setNote(rs.getString("Note"));
        h.setHeldAt(toLocal(rs.getTimestamp("HeldAt")));
        h.setRestoredAt(toLocal(rs.getTimestamp("RestoredAt")));
        h.setCancelledAt(toLocal(rs.getTimestamp("CancelledAt")));
        h.setExpiredAt(toLocal(rs.getTimestamp("ExpiredAt")));
        h.setItemCount(rs.getInt("ItemCount"));
        return h;
    }

    private static java.time.LocalDateTime toLocal(Timestamp ts) {
        return ts != null ? ts.toLocalDateTime() : null;
    }

    private static void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) ps.setNull(index, java.sql.Types.INTEGER);
        else ps.setInt(index, value);
    }

    private static String normalizePaymentMethod(String method) {
        if (method == null) return "CASH";
        String v = method.trim().toUpperCase();
        return switch (v) {
            case "CASH", "BANK_TRANSFER", "PAYPAL", "CARD" -> v;
            default -> "CASH";
        };
    }

    private static String normalizeNote(String note) {
        if (note == null) return null;
        String v = note.trim();
        if (v.isEmpty()) return null;
        return v.length() <= 500 ? v : v.substring(0, 500);
    }

    private static String escapeLike(String raw) {
        return raw.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
