package com.security;

import java.io.File;

/**
 * Scanner bảo mật file - Đã được cải thiện để không làm người dùng bực mình
 */
public final class FileSecurityScanner {

    private static final FileSecurityScanner INSTANCE = new FileSecurityScanner();

    private final VirusScanner heuristicScanner = new HeuristicFileScanner();
    // Windows Defender - co san tren Windows, khong can cai/chay them phan mem
    // thu 3 nao (khong con ClamAV fallback).
    private final VirusScanner primaryScanner = new WindowsDefenderVirusScanner();

    private FileSecurityScanner() {}

    public static FileSecurityScanner getInstance() {
        return INSTANCE;
    }

    public ScanResult scan(File file) {
        // Buoc 1: Luon kiem tra heuristic truoc
        ScanResult heuristicResult = heuristicScanner.scan(file);
        if (heuristicResult.isBlocked()) {
            return heuristicResult; // Ngan ngay file nguy hiem
        }

        // Buoc 2: Windows Defender (co the unavailable neu khong phai Windows
        // hoac khong tim thay MpCmdRun.exe)
        ScanResult avResult = primaryScanner.scan(file);

        switch (avResult.getStatus()) {
            case INFECTED:
            case ERROR:
                return avResult; // Ngăn file có virus thật

            case SCAN_UNAVAILABLE:
                // Chi canh bao, KHONG chan import. Giu nguyen ly do that.
                return ScanResult.unavailable(
                    "Đã kiểm tra cấu trúc file — không phát hiện bất thường.\n" +
                    avResult.getMessage() + "\n" +
                    "File vẫn được chấp nhận (chỉ kiểm tra heuristic).");

            default:
                return ScanResult.clean("File an toàn (heuristic + " + lastScannerName(avResult) + ").");
        }
    }

    private String lastScannerName(ScanResult avResult) {
        return "Windows Defender";
    }
}