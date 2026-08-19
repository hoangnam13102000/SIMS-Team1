package com.service;

import com.dao.OrderDAO;
import com.event.AppEventBus;
import com.event.DataChangedEvent;
import com.model.Order;
import com.model.permission.AppPermission;
import com.permission.PermissionManager;
import com.settings.NotificationSettings;
import com.utils.NotificationSound;

import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.util.List;
import java.util.function.BiConsumer;

public final class OrderNotifyPoller {

    private static final int POLL_INTERVAL_MS = 5000;
    private static final int PREVIEW_LIMIT = 5;

    private final OrderDAO orderDAO = new OrderDAO();
    private final Timer timer;

    private int lastKnownUnseenCount = -1;
    private BiConsumer<Integer, List<Order>> onUnseenChanged;

    public OrderNotifyPoller() {
        timer = new Timer(POLL_INTERVAL_MS, e -> poll());
        timer.setRepeats(true);
    }

    /** Được gọi lại (trên EDT) mỗi khi số đơn chưa xem thay đổi: (soLuong, danhSachXemTruoc).
     *  danhSachXemTruoc la List<Order> (toi da PREVIEW_LIMIT don) de noi goi (Header) tu
     *  quyet dinh hien thi/dieu huong/danh dau da xem tung don - thay vi chuoi text dung san. */
    public void onUnseenChanged(BiConsumer<Integer, List<Order>> listener) {
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
                boolean broad = PermissionManager.getInstance().can(AppPermission.ORDER_VIEW)
                        || PermissionManager.getInstance().can(AppPermission.ORDER_MANAGE);
                boolean assigned = PermissionManager.getInstance().can(AppPermission.ORDER_VIEW_ASSIGNED)
                        || PermissionManager.getInstance().can(AppPermission.ORDER_PROCESS_ASSIGNED);
                Integer assignedToUserId = null;
                if (!broad && assigned && AuthService.getInstance().getCurrentUser() != null) {
                    assignedToUserId = AuthService.getInstance().getCurrentUser().getUserId();
                }
                return orderDAO.getUnseenOrders(assignedToUserId);
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

                if (increased) {
                    // Co don hang online moi: bao cho toan app (OrderPanel,
                    // InvoicePanel, Dashboard...) tu lam moi qua AutoRefresher -
                    // truoc day chi cap nhat chuong/badge tren Header (xem
                    // AdminMainFrame) ma khong publish DataChangedEvent nen
                    // cac trang dang mo khong tu dong hien don hang moi, phai
                    // F5/chuyen tab qua lai moi thay.
                    AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.ORDER));
                }

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

                List<Order> preview = unseen.subList(0, Math.min(PREVIEW_LIMIT, unseen.size()));
                onUnseenChanged.accept(actualCount, preview);
            }
        };
        worker.execute();
    }
}