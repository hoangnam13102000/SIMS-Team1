package com.event;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.event.HierarchyEvent;
import java.util.function.Consumer;

/**
 * Component dung chung cho moi panel can "tu lam moi khi co su kien, nhung
 * khong query DB lien tuc". Gom 2 ky thuat:
 *
 * 1. DEBOUNCE - nhieu su kien don dap lien tiep (vd: doi trang thai 5 don
 *    lien tuc) chi bi query DB 1 lan duy nhat, sau khi "yen" du mot khoang
 *    thoi gian (debounceMs).
 * 2. BO QUA KHI DANG AN - neu component dang khong hien thi (o tab khac qua
 *    CardLayout), su kien toi chi danh dau "dirty", KHONG query DB ngay; chi
 *    thuc su query dung 1 lan khi component duoc hien thi tro lai.
 *
 * Cach dung don gian nhat - tu dang ky thang voi AppEventBus qua bind():
 *
 *   AutoRefresher.bind(this, DataChangedEvent.class, 400, this::loadData);
 *
 * Neu can nhieu loai su kien cung goi 1 ham refresh (vd: ca DataChangedEvent
 * lan NewOrderEvent), tao AutoRefresher 1 lan roi bind() them nhieu class:
 *
 *   AutoRefresher refresher = new AutoRefresher(this, 400, this::loadData);
 *   refresher.listenTo(DataChangedEvent.class);
 *   refresher.listenTo(NewOrderEvent.class);
 *
 * Panel dung component nay KHONG can tu viet Timer/HierarchyListener/dirty
 * flag nua, va cung khong dinh bug tung gap voi AncestorListener.ancestorRemoved
 * (fire nham khi CardLayout an component) vi o day dung isShowing() +
 * HierarchyListener.SHOWING_CHANGED, phan anh dung trang thai hien thi thuc te.
 */
public final class AutoRefresher {

    private final JComponent target;
    private final Timer debounceTimer;
    private volatile boolean dirty = false;

    public AutoRefresher(JComponent target, int debounceMs, Runnable refreshAction) {
        this.target = target;
        this.debounceTimer = new Timer(debounceMs, e -> performRefresh(refreshAction));
        this.debounceTimer.setRepeats(false);

        target.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && target.isShowing() && dirty) {
                performRefresh(refreshAction);
            }
        });
    }

    /** Goi khi co 1 su kien lam du lieu thay doi - tu quyet dinh debounce hay chi danh dau dirty. */
    public void requestRefresh() {
        if (target.isShowing()) {
            debounceTimer.restart();
        } else {
            dirty = true;
        }
    }

    private void performRefresh(Runnable refreshAction) {
        dirty = false;
        refreshAction.run();
    }

    /** Dang ky voi AppEventBus: moi khi co event kieu eventType, tu goi requestRefresh(). */
    public <T> void listenTo(Class<T> eventType) {
        Consumer<T> listener = e -> requestRefresh();
        AppEventBus.getInstance().subscribe(eventType, listener);
    }

    /**
     * Tien ich goi nhanh cho truong hop chi can nghe 1 loai su kien: tao
     * AutoRefresher va dang ky voi AppEventBus trong 1 buoc.
     */
    public static <T> AutoRefresher bind(JComponent target, Class<T> eventType, int debounceMs, Runnable refreshAction) {
        AutoRefresher refresher = new AutoRefresher(target, debounceMs, refreshAction);
        refresher.listenTo(eventType);
        return refresher;
    }
}