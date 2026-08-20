package com.service;

import com.dao.ShiftDAO;
import com.event.AppEventBus;
import com.event.DataChangedEvent;
import com.model.Shift;
import com.model.permission.AppPermission;
import com.permission.PermissionManager;
import com.utils.NotificationSound;

import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.util.List;
import java.util.function.BiConsumer;

public final class ShiftNotifyPoller {

    private static final int POLL_INTERVAL_MS = 5000;
    private static final int PREVIEW_LIMIT = 8;

    private final ShiftDAO shiftDAO = new ShiftDAO();
    private final Timer timer;
    private int lastKnownPendingCount = -1;
    private BiConsumer<Integer, List<Shift>> onPendingChanged;

    public ShiftNotifyPoller() {
        timer = new Timer(POLL_INTERVAL_MS, e -> poll());
        timer.setRepeats(true);
    }

    public void onPendingChanged(BiConsumer<Integer, List<Shift>> listener) {
        this.onPendingChanged = listener;
    }

    public void start() {
        poll();
        timer.start();
    }

    public void stop() {
        timer.stop();
    }

    private boolean canReceiveShiftNotifications() {
        PermissionManager pm = PermissionManager.getInstance();
        return pm.can(AppPermission.SHIFT_APPROVE) || pm.can(AppPermission.SHIFT_VIEW_ALL);
    }

    private void poll() {
        if (!canReceiveShiftNotifications()) {
            if (onPendingChanged != null && lastKnownPendingCount != 0) {
                lastKnownPendingCount = 0;
                onPendingChanged.accept(0, List.of());
            }
            return;
        }

        SwingWorker<List<Shift>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Shift> doInBackground() {
                return shiftDAO.findPendingApproval(PREVIEW_LIMIT);
            }

            @Override
            protected void done() {
                List<Shift> pending;
                try {
                    pending = get();
                } catch (Exception ex) {
                    return;
                }

                int actualCount = shiftDAO.countPendingApproval();
                if (actualCount == 0 && !pending.isEmpty()) {
                    actualCount = pending.size();
                }

                boolean increased = lastKnownPendingCount >= 0 && actualCount > lastKnownPendingCount;
                lastKnownPendingCount = actualCount;

                if (increased) {
                    AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.SHIFT));
                    NotificationSound.playDing();
                }

                if (onPendingChanged != null) {
                    onPendingChanged.accept(actualCount, pending);
                }
            }
        };
        worker.execute();
    }
}