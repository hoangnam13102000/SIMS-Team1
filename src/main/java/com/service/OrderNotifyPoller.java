package com.service;

import com.dao.OrderDAO;
import com.model.Order;
import com.settings.NotificationSettings;
import com.utils.NumberUtil;

import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Thông báo đơn hàng mới cho phía admin bằng cách POLLING định kỳ xuống DB
 * (thay vì WebSocket/push) - đơn giản và đáng tin cậy hơn nhiều cho 1 app
 * desktop nhiều tiến trình độc lập (client đặt hàng và admin xem đơn có thể
 * chạy trên 2 máy/2 tiến trình khác nhau, chỉ chia sẻ chung DB), không cần
 * mở thêm cổng/server nào. Độ trễ tối đa = {@link #POLL_INTERVAL_MS}, đủ
 * nhanh cho nghiệp vụ bán hàng thông thường.
 * <p>
 * Mỗi lần poll chạy trên background thread (SwingWorker) để KHÔNG chặn EDT,
 * kết quả (số đơn chưa xem + preview) được đưa lên callback trên chính EDT.
 */
public final class OrderNotifyPoller {

    private static final int POLL_INTERVAL_MS = 5000;
    private static final int PREVIEW_LIMIT = 5;

    private final OrderDAO orderDAO = new OrderDAO();
    private final Timer timer;

    private int lastKnownUnseenCount = -1;
    private BiConsumer<Integer, List<String>> onUnseenChanged;

    public OrderNotifyPoller() {
        timer = new Timer(POLL_INTERVAL_MS, e -> poll());
        timer.setRepeats(true);
    }

    /** Được gọi lại (trên EDT) mỗi khi số đơn chưa xem thay đổi: (soLuong, danhSachXemTruoc). */
    public void onUnseenChanged(BiConsumer<Integer, List<String>> listener) {
        this.onUnseenChanged = listener;
    }

    public void start() {
        poll();
        timer.start();
    }

    public void stop() {
        timer.stop();
    }

    private void poll() {
        SwingWorker<List<Order>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Order> doInBackground() {
                return orderDAO.getUnseenOrders();
            }

            @Override
            protected void done() {
                List<Order> unseen;
                try {
                    unseen = get();
                } catch (Exception ex) {
                    return; // Mat ket noi DB tam thoi - bo qua lan poll nay, thu lai lan sau.
                }

                int actualCount = unseen.size();
                boolean increased = lastKnownUnseenCount >= 0 && actualCount > lastKnownUnseenCount;
                lastKnownUnseenCount = actualCount;

                boolean muted = NotificationSettings.getInstance().isOrdersMuted();
                if (increased && !muted && NotificationSettings.getInstance().isSoundEnabled()) {
                    Toolkit.getDefaultToolkit().beep();
                }

                if (onUnseenChanged == null) return;
                if (muted) {
                    // "An thong bao don hang": khong tang so dem/khong hien preview,
                    // nhung du lieu (SeenByAdmin=0) van con nguyen, xem lai duoc sau.
                    onUnseenChanged.accept(0, List.of());
                    return;
                }

                List<String> preview = new ArrayList<>();
                for (int i = 0; i < Math.min(PREVIEW_LIMIT, unseen.size()); i++) {
                    Order o = unseen.get(i);
                    preview.add(o.getOrderCode() + " · " + o.getCustomerName() + " · "
                            + NumberUtil.formatThousands(o.getTotalAmount().longValue()) + " đ");
                }
                onUnseenChanged.accept(actualCount, preview);
            }
        };
        worker.execute();
    }
}