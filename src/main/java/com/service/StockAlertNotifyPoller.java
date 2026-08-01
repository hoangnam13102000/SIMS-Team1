package com.service;

import com.dao.StockAlertDAO;
import com.model.StockAlert;
import com.utils.NotificationSound;

import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Poll dinh ky xuong DB de phat hien bao cao het/sap het hang moi tu NV ban
 * hang (StockAlerts.SeenByInventoryManager = 0) - dung cho badge o muc
 * "Canh bao ton kho" tren sidebar cua Quan ly kho. Cung mo hinh voi
 * {@link OrderNotifyPoller} (Orders.SeenByAdmin) de nhat quan trong toan he
 * thong; khong dung chung 1 chuong Header voi don hang online vi 2 nghiep
 * vu/2 doi tuong xem khac nhau (Admin/Sales xem don hang, Quan ly kho xem
 * canh bao ton kho) - hien o badge rieng cua trang "stockAlerts".
 */
public final class StockAlertNotifyPoller {

    private static final int POLL_INTERVAL_MS = 5000;
    private static final int PREVIEW_LIMIT = 5;

    private final StockAlertDAO stockAlertDAO = new StockAlertDAO();
    private final Timer timer;

    private int lastKnownUnseenCount = -1;
    private BiConsumer<Integer, List<String>> onUnseenChanged;

    public StockAlertNotifyPoller() {
        timer = new Timer(POLL_INTERVAL_MS, e -> poll());
        timer.setRepeats(true);
    }

    /** Được gọi lại (trên EDT) mỗi khi số báo cáo chưa xem thay đổi: (soLuong, danhSachXemTruoc). */
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
        SwingWorker<List<StockAlert>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<StockAlert> doInBackground() {
                return stockAlertDAO.getUnseenForInventoryManager();
            }

            @Override
            protected void done() {
                List<StockAlert> unseen;
                try {
                    unseen = get();
                } catch (Exception ex) {
                    return; // Mat ket noi DB tam thoi - bo qua lan poll nay, thu lai lan sau.
                }

                int actualCount = unseen.size();
                boolean increased = lastKnownUnseenCount >= 0 && actualCount > lastKnownUnseenCount;
                lastKnownUnseenCount = actualCount;

                if (increased) {
                    NotificationSound.playDing();
                }

                if (onUnseenChanged == null) return;

                List<String> preview = new ArrayList<>();
                for (int i = 0; i < Math.min(PREVIEW_LIMIT, unseen.size()); i++) {
                    StockAlert a = unseen.get(i);
                    String kind = a.isOutOfStock() ? "Hết hàng" : "Sắp hết hàng";
                    preview.add(kind + " · " + a.getProductName() + " · còn " + a.getStockAtReport());
                }
                onUnseenChanged.accept(actualCount, preview);
            }
        };
        worker.execute();
    }
}