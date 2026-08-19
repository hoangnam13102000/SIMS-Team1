package com.dao;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.model.InvoicePayment;
import com.utils.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/** Đọc các dòng thanh toán của hóa đơn. Ghi mới được thực hiện cùng transaction trong InvoiceDAO. */
public class InvoicePaymentDAO {

    public List<InvoicePayment> getByInvoiceId(int invoiceId) {
        String sql = "SELECT PaymentID, InvoiceID, PaymentMethod, Amount, TenderedAmount, ChangeAmount, "
                + "Provider, ProviderTransactionID, ProviderPaymentID, IdempotencyKey, PaymentStatus, CreatedBy, CreatedAt "
                + "FROM InvoicePayments WHERE InvoiceID = ? ORDER BY PaymentID";
        List<InvoicePayment> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, invoiceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    InvoicePayment p = new InvoicePayment();
                    p.setPaymentId(rs.getLong("PaymentID"));
                    p.setInvoiceId(rs.getInt("InvoiceID"));
                    p.setPaymentMethod(rs.getString("PaymentMethod"));
                    p.setAmount(rs.getBigDecimal("Amount"));
                    p.setTenderedAmount(rs.getBigDecimal("TenderedAmount"));
                    p.setChangeAmount(rs.getBigDecimal("ChangeAmount"));
                    p.setProvider(rs.getString("Provider"));
                    p.setProviderTransactionId(rs.getString("ProviderTransactionID"));
                    p.setProviderPaymentId(rs.getString("ProviderPaymentID"));
                    p.setIdempotencyKey(rs.getString("IdempotencyKey"));
                    p.setPaymentStatus(rs.getString("PaymentStatus"));
                    p.setCreatedBy(rs.getInt("CreatedBy"));
                    Timestamp ts = rs.getTimestamp("CreatedAt");
                    p.setCreatedAt(ts != null ? ts.toLocalDateTime() : null);
                    list.add(p);
                }
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "InvoicePaymentDAO.getByInvoiceId - invoiceId=" + invoiceId, e);
        }
        return list;
    }

    public boolean hasMultipleCompletedMethods(int invoiceId) {
        String sql = "SELECT COUNT(DISTINCT PaymentMethod) FROM InvoicePayments "
                + "WHERE InvoiceID=? AND PaymentStatus='COMPLETED'";
        try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, invoiceId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 1;
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "InvoicePaymentDAO.hasMultipleCompletedMethods - invoiceId=" + invoiceId, e);
            return false;
        }
    }
}
