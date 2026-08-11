package com.dao;
// ============================================================
// THAY THE toan bo method createInvoice(...) trong InvoiceDAO
// (giu nguyen class, chi thay method nay)
// ============================================================

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.event.AppEventBus;
import com.event.DataChangedEvent;
import com.model.Invoice;
import com.model.InvoiceDetail;
import com.service.PromotionService;
import com.utils.DBConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;

public class InvoiceDAO_createInvoice_DOCS {
    /*
     * Sau khi chay migration SQL (them cot DiscountAmount, PromotionID, PromotionCode),
     * thay method createInvoice bang ban duoi.
     */

    public boolean createInvoice(Invoice invoice, List<InvoiceDetail> items) {
        if (items == null || items.isEmpty()) return false;

        String insertInvoiceSql = "INSERT INTO Invoices "
                + "(InvoiceCode, ShiftID, CreatedBy, CustomerID, PaymentMethod, PayPalOrderID, PayPalCaptureID, "
                + "VATRate, SubTotal, TotalAmount, DiscountAmount, PromotionID, PromotionCode) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, 0, ?, ?, ?)";
        String insertDetailSql = "INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice) VALUES (?, ?, ?, ?)";
        String sumLineTotalSql = "SELECT ISNULL(SUM(LineTotal), 0) FROM InvoiceDetails WHERE InvoiceID = ?";
        String updateTotalsSql = "UPDATE Invoices SET InvoiceCode = ?, SubTotal = ?, "
                + "TotalAmount = ?, DiscountAmount = ? WHERE InvoiceID = ?";

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                int invoiceId;
                BigDecimal requestedDiscount = invoice.getDiscountAmount() != null
                        ? invoice.getDiscountAmount() : BigDecimal.ZERO;
                if (requestedDiscount.signum() < 0) requestedDiscount = BigDecimal.ZERO;

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
                    ps.setBigDecimal(9, requestedDiscount);
                    if (invoice.getPromotionId() != null) {
                        ps.setInt(10, invoice.getPromotionId());
                    } else {
                        ps.setNull(10, Types.INTEGER);
                    }
                    if (invoice.getPromotionCode() != null) {
                        ps.setString(11, invoice.getPromotionCode());
                    } else {
                        ps.setNull(11, Types.VARCHAR);
                    }
                    ps.executeUpdate();

                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Khong lay duoc InvoiceID vua tao.");
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

                // Giam gia khong vuot subTotal that (sau khi trigger co the cat bot SL)
                BigDecimal discount = requestedDiscount.min(subTotal);
                BigDecimal taxable = subTotal.subtract(discount);
                BigDecimal vatRate = invoice.getVatRate() != null ? invoice.getVatRate() : BigDecimal.ZERO;
                BigDecimal totalAmount = taxable.add(taxable.multiply(vatRate)
                        .divide(new BigDecimal(100), 0, java.math.RoundingMode.HALF_UP));

                String invoiceCode = "HD-" + java.time.LocalDate.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
                        + "-" + String.format("%04d", invoiceId);

                try (PreparedStatement ps = con.prepareStatement(updateTotalsSql)) {
                    ps.setString(1, invoiceCode);
                    ps.setBigDecimal(2, subTotal);
                    ps.setBigDecimal(3, totalAmount);
                    ps.setBigDecimal(4, discount);
                    ps.setInt(5, invoiceId);
                    ps.executeUpdate();
                }

                // Cong diem tren so tien KHACH THUC TRA (totalAmount sau giam gia)
                int pointsEarned = 0;
                if (invoice.getCustomerId() != null) {
                    BigDecimal pointRate = new StoreConfigDAO().getPointRate();
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

                // Tang UsedCount ma KM (cung transaction)
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
}