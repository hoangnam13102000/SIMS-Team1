package com.security;

/**
 * Ket qua quet 1 file: trang thai + ten moi de doa (neu co) + thong diep chi tiet
 * (hien thi cho nguoi dung khi bi chan hoac khi ClamAV khong kha dung).
 */
public final class ScanResult {

    private final ScanStatus status;
    private final String threatName;
    private final String message;

    private ScanResult(ScanStatus status, String threatName, String message) {
        this.status = status;
        this.threatName = threatName;
        this.message = message;
    }

    public static ScanResult clean(String message) {
        return new ScanResult(ScanStatus.CLEAN, null, message);
    }

    public static ScanResult infected(String threatName, String message) {
        return new ScanResult(ScanStatus.INFECTED, threatName, message);
    }

    public static ScanResult suspicious(String message) {
        return new ScanResult(ScanStatus.SUSPICIOUS, null, message);
    }

    public static ScanResult unavailable(String message) {
        return new ScanResult(ScanStatus.SCAN_UNAVAILABLE, null, message);
    }

    public static ScanResult error(String message) {
        return new ScanResult(ScanStatus.ERROR, null, message);
    }

    public ScanStatus getStatus() { return status; }
    public String getThreatName() { return threatName; }
    public String getMessage() { return message; }

    /** true => KHONG duoc phep tiep tuc import file nay. */
    public boolean isBlocked() {
        return status == ScanStatus.INFECTED || status == ScanStatus.SUSPICIOUS || status == ScanStatus.ERROR;
    }

    /** true => cho phep tiep tuc nhung nen canh bao cho nguoi dung (khong co AV that quet). */
    public boolean isWarning() {
        return status == ScanStatus.SCAN_UNAVAILABLE;
    }
}