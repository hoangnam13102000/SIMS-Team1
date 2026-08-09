package com.service;

import com.dao.ReturnExchangeDAO;
import com.event.AppEventBus;
import com.event.DataChangedEvent;
import com.model.ReturnExchange;
import com.model.NotificationItem;
import com.settings.NotificationSettings;
import com.utils.NotificationSound;

import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import com.utils.DBConnection;

/**
 * Theo doi yeu cau tra hang ONLINE moi cua khach hang.
 * Chi thong bao yeu cau PENDING do User co ho so Customers tao ra;
 * yeu cau tao tu giao dien nhan vien khong tao chuong.
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
        // Lay moc hien tai ma khong phat chuong cho cac yeu cau da ton tai
        // truoc khi man hinh quan tri duoc mo.
        if (lastKnownReturnId < 0) {
            lastKnownReturnId = getMaxReturnId();
        }
        timer.start();
    }

    public void stop() {
        timer.stop();
    }

    private int getMaxReturnId() {
        String sql = "SELECT ISNULL(MAX(r.ReturnID), 0) FROM ReturnExchanges r";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private void poll() {
        SwingWorker<List<ReturnExchange>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<ReturnExchange> doInBackground() {
                List<ReturnExchange> result = new ArrayList<>();
                String sql =
                    "SELECT r.ReturnID, r.InvoiceID, inv.InvoiceCode, r.Type, r.Reason, "
                  + "r.TotalValue, r.RequiresApproval, r.Status, r.CreatedBy, u.FullName AS CreatedByName, r.CreatedAt "
                  + "FROM ReturnExchanges r "
                  + "JOIN Invoices inv ON inv.InvoiceID = r.InvoiceID "
                  + "JOIN Users u ON u.UserID = r.CreatedBy "
                  + "JOIN Customers c ON c.CustomerID = r.CreatedBy "
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
                            result.add(r);
                        }
                    }
                } catch (Exception ignored) {
                }
                return result;
            }

            @Override
            protected void done() {
                try {
                    List<ReturnExchange> rows = get();
                    if (rows.isEmpty()) return;

                    int maxId = lastKnownReturnId;
                    List<NotificationItem> items = new ArrayList<>();
                    for (ReturnExchange r : rows) {
                        maxId = Math.max(maxId, r.getReturnId());
                        String customer = r.getCreatedByName() == null ? "Khách hàng" : r.getCreatedByName();
                        String title = "Khách gửi yêu cầu trả hàng";
                        String message = customer + " - " + (r.getInvoiceCode() == null ? "Hóa đơn" : r.getInvoiceCode());
                        items.add(new NotificationItem(
                            "return-" + r.getReturnId(),
                            NotificationItem.Type.RETURN,   // <-- cần thêm Type.RETURN vào NotificationItem
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