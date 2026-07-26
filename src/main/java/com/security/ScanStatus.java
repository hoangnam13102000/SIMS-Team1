package com.security;

/**
 * Trang thai ket qua quet file truoc khi cho phep nhap (import) vao he thong.
 */
public enum ScanStatus {
    /** Khong phat hien gi bat thuong (qua heuristic va/hoac ClamAV). */
    CLEAN,
    /** Phat hien ma doc / virus / noi dung nguy hiem ro rang -> CHAN tuyet doi. */
    INFECTED,
    /** Khong ket luan duoc virus cu the nhung file co dau hieu bat thuong (macro, nen bat thuong...). */
    SUSPICIOUS,
    /** Khong quet duoc (vd khong ket noi duoc ClamAV) - chi dua tren heuristic noi bo. */
    SCAN_UNAVAILABLE,
    /** Loi trong qua trinh doc/quet file (file hong, khong doc duoc...). */
    ERROR
}