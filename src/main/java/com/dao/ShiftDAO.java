package com.dao;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.utils.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * DAO rất mỏng cho bảng Shifts (ca bán hàng) - chỉ phục vụ đúng 1 nhu cầu:
 * mọi hóa đơn (Invoices.ShiftID) đều BẮT BUỘC gắn với 1 ca đang mở, và trigger
 * R4 (trg_Invoices_CancelSameDayOnly) chỉ cho phép hủy hóa đơn khi ca đó vẫn
 * đang OPEN + trong cùng ngày tạo. Trang POS không cần màn hình "mở ca/đóng
 * ca" riêng - {@link #getOrOpenShiftId(int)} tự động mở ca mới cho nhân viên
 * ngay lần bán hàng đầu tiên trong ngày nếu chưa có ca nào đang mở.
 */
public class ShiftDAO {

    /**
     * Lấy ShiftID đang OPEN gần nhất của nhân viên; nếu chưa có ca nào đang
     * mở (hoặc ca gần nhất đã CLOSED) thì tự mở 1 ca mới và trả về ShiftID đó.
     * Trả về -1 nếu có lỗi (không nên xảy ra trong điều kiện DB bình thường).
     */
    public int getOrOpenShiftId(int userId) {
        Integer openShiftId = findOpenShiftId(userId);
        if (openShiftId != null) return openShiftId;
        return openNewShift(userId);
    }

    private Integer findOpenShiftId(int userId) {
        String sql = "SELECT ShiftID FROM Shifts WHERE UserID = ? AND Status = 'OPEN' ORDER BY ShiftID DESC LIMIT 1";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "ShiftDAO.findOpenShiftId - userId=" + userId, e);
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
                if (keys.next()) return keys.getInt(1);
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_INSERT_FAIL,
                    "ShiftDAO.openNewShift - userId=" + userId, e);
        }
        return -1;
    }
}
