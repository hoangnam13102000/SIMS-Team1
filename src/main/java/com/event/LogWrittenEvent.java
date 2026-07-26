package com.event;

/**
 * Bao "vua co 1 dong nhat ky moi duoc ghi" - khong mang du lieu, chi la tin
 * hieu "hay tai lai". Duoc phat ra tu 2 nguon:
 *  - AppLogger.log() khi thao tac xay ra NGAY trong tien trinh nay.
 *  - StockSyncClient khi nhan broadcast LOG_ADDED tu 1 may/tien trinh admin
 *    KHAC qua WebSocket (xem OrderQueueServer.broadcastLogAdded).
 * Man hinh nao can tu cap nhat khi co nhat ky moi (vd: ActivityLogPanel) chi
 * can bind AutoRefresher voi class nay, khong quan tam nguon goc su kien.
 */
public final class LogWrittenEvent {
    public LogWrittenEvent() {}
}