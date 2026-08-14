package com.service;

import com.event.AppEventBus;
import com.event.DataChangedEvent;
import com.model.ReturnExchange;
import com.model.NotificationItem;
import com.settings.NotificationSettings;
import com.utils.NotificationSound;
import com.utils.DBConnection;

import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Theo dõi yêu cầu đổi/trả hàng mới cần duyệt (Status = PENDING).
 * <p>
 * Báo chuông cho <b>mọi</b> phiếu PENDING mới — cả khách online và nhân viên
 * tạo tại quầy/admin — để quản trị không bỏ sót yêu cầu chờ duyệt.
 * <p>
 * Không phát chuông cho các phiếu đã tồn tại trước khi màn hình admin mở
 * (chỉ lấy mốc {@code MAX(ReturnID)} lúc {@code start()}).
 */
public final class ReturnExchangeNotifyPoller {
    private static final int POLL_INTERVAL_MS = 5000;
    private final Timer timer;
    private final Consumer<List<NotificationItem>> onNewNotifications;
    private int lastKnownReturnId = -1;

    public ReturnExchangeNotifyPoller(Consumer<List<NotificationItem>> listener) {
        this.onNewNotifications = listener;
        this.timer = new Timer(POLL_INTERVAL_MS, e -> poll());
        this.timer.setRepeats(true);
    }

    public void start() {
        // Lấy mốc hiện tại, không phát chuông cho yêu cầu đã có trước khi mở màn admin.
        if (lastKnownReturnId < 0) {
            lastKnownReturnId = getMaxReturnId();
        }
        timer.start();
    }

    public void stop() {
        timer.stop();
    }

    private int getMaxReturnId() {
        String sql = "SELECT COALESCE(MAX(r.ReturnID), 0) FROM ReturnExchanges r";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /** Kết quả 1 dòng poll kèm cờ khách online. */
    private static final class PendingRow {
        final ReturnExchange exchange;
        final boolean isCustomer;

        PendingRow(ReturnExchange exchange, boolean isCustomer) {
            this.exchange = exchange;
            this.isCustomer = isCustomer;
        }
    }

    private void poll() {
        SwingWorker<List<PendingRow>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<PendingRow> doInBackground() {
                List<PendingRow> result = new ArrayList<>();
                // LEFT JOIN Customers: vẫn nhận diện khách online để đổi title,
                // nhưng không loại phiếu do nhân viên tạo.
                String sql =
                    "SELECT r.ReturnID, r.InvoiceID, inv.InvoiceCode, r.Type, r.Reason, "
                  + "r.TotalValue, r.RequiresApproval, r.Status, r.CreatedBy, "
                  + "u.FullName AS CreatedByName, r.CreatedAt, "
                  + "CASE WHEN c.CustomerID IS NOT NULL THEN 1 ELSE 0 END AS IsCustomer "
                  + "FROM ReturnExchanges r "
                  + "JOIN Invoices inv ON inv.InvoiceID = r.InvoiceID "
                  + "JOIN Users u ON u.UserID = r.CreatedBy "
                  + "LEFT JOIN Customers c ON c.CustomerID = r.CreatedBy "
                  + "WHERE r.ReturnID > ? AND r.Status = 'PENDING' "
                  + "ORDER BY r.ReturnID ASC";
                try (Connection con = DBConnection.getConnection();
                     PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setInt(1, lastKnownReturnId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            ReturnExchange r = new ReturnExchange();
                            r.setReturnId(rs.getInt("ReturnID"));
                            r.setInvoiceId(rs.getInt("InvoiceID"));
                            r.setInvoiceCode(rs.getString("InvoiceCode"));
                            r.setType(rs.getString("Type"));
                            r.setReason(rs.getString("Reason"));
                            r.setTotalValue(rs.getBigDecimal("TotalValue"));
                            r.setRequiresApproval(rs.getBoolean("RequiresApproval"));
                            r.setStatus(rs.getString("Status"));
                            r.setCreatedBy(rs.getInt("CreatedBy"));
                            r.setCreatedByName(rs.getString("CreatedByName"));
                            var ts = rs.getTimestamp("CreatedAt");
                            r.setCreatedAt(ts == null ? null : ts.toLocalDateTime());
                            result.add(new PendingRow(r, rs.getInt("IsCustomer") == 1));
                        }
                    }
                } catch (Exception ignored) {
                }
                return result;
            }

            @Override
            protected void done() {
                try {
                    List<PendingRow> rows = get();
                    if (rows.isEmpty()) return;

                    int maxId = lastKnownReturnId;
                    List<NotificationItem> items = new ArrayList<>();
                    for (PendingRow row : rows) {
                        ReturnExchange r = row.exchange;
                        maxId = Math.max(maxId, r.getReturnId());
                        String who = r.getCreatedByName() == null
                                ? (row.isCustomer ? "Khách hàng" : "Nhân viên")
                                : r.getCreatedByName();
                        String title = row.isCustomer
                                ? "Khách gửi yêu cầu trả hàng"
                                : "Yêu cầu đổi/trả chờ duyệt";
                        String message = who + " - "
                                + (r.getInvoiceCode() == null ? "Hóa đơn" : r.getInvoiceCode());
                        items.add(new NotificationItem(
                            "return-" + r.getReturnId(),
                            NotificationItem.Type.RETURN,
                            title,
                            message,
                            r.getCreatedAt() == null ? LocalDateTime.now() : r.getCreatedAt(),
                            r.getReturnId()
                        ));
                    }
                    lastKnownReturnId = maxId;

                    if (!NotificationSettings.getInstance().isOrdersMuted()) {
                        NotificationSound.playDing();
                    }
                    AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.RETURN_EXCHANGE));
                    if (onNewNotifications != null) onNewNotifications.accept(items);
                } catch (Exception ignored) {
                }
            }
        };
        worker.execute();
    }
}