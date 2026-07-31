package com.service;

import com.dao.OrderDAO;
import com.model.Order;
import com.settings.NotificationSettings;
import com.utils.NotificationSound;
import com.utils.NumberUtil;

import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

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
                if (increased && !muted) {
                    // NotificationSound.playDing() tu kiem tra isSoundEnabled() ben trong,
                    // dung tieng chuong tong hop (ChimePlayer) giong myShop thay vi
                    // Toolkit.beep() (tieng "beep" mac dinh cua he dieu hanh).
                    NotificationSound.playDing();
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