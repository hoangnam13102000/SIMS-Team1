package com.security;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Bo quet noi bo, KHONG phu thuoc phan mem/AV ngoai, luon chay truoc (nhanh, dong bo)
 * cho moi file truoc khi (tuy chon) chuyen sang ClamAV. Muc dich: chan cac dang file
 * gia mao/nguy hiem pho bien nhat doi voi file Excel (.xlsx) va Word (.docx):
 * <p>
 * - Sai dinh dang thuc te so voi duoi file khai bao (gia mao phan mo rong).
 * - File .doc/.xls dang OLE2 nhi phan cu (de nhiem macro virus) - KHONG chap nhan,
 *   chi cho phep dinh dang OOXML (.xlsx/.docx) hien dai.
 * - Zip bomb: ty le nen bat thuong hoac dung luong giai nen qua lon.
 * - Zip slip: entry co duong dan ".." thoat ra ngoai thu muc giai nen.
 * - Macro nhung san (vbaProject.bin) - .xlsx/.docx chuan khong co macro, neu co
 *   tuc la thuc chat la .xlsm/.docm doi ten -> tu choi.
 * - File thuc thi/script nhung ben trong goi OOXML (.exe, .dll, .js, .vbs, .ps1, .jar, .bat...).
 */
public final class HeuristicFileScanner implements VirusScanner {

    private static final long MAX_FILE_SIZE_BYTES = 25L * 1024 * 1024; // 25MB, du cho file du lieu nhap
    private static final long MAX_UNCOMPRESSED_TOTAL = 300L * 1024 * 1024; // 300MB tong sau giai nen
    private static final double MAX_COMPRESSION_RATIO = 100.0; // 1 byte nen -> toi da 100 byte giai nen
    private static final long MAX_ENTRY_UNCOMPRESSED = 100L * 1024 * 1024; // 1 entry don le

    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04}; // "PK\3\4"
    private static final byte[] OLE2_MAGIC = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0}; // .doc/.xls cu

    private static final String[] DANGEROUS_EXTENSIONS = {
            ".exe", ".dll", ".scr", ".com", ".bat", ".cmd", ".ps1", ".vbs", ".vbe",
            ".js", ".jse", ".jar", ".msi", ".msp", ".sh", ".apk", ".cpl", ".hta", ".wsf"
    };

    @Override
    public String getName() { return "Heuristic"; }

    @Override
    public ScanResult scan(File file) {
        try {
            if (!file.exists() || !file.isFile()) {
                return ScanResult.error("File không tồn tại hoặc không hợp lệ.");
            }
            if (file.length() == 0) {
                return ScanResult.error("File rỗng (0 byte).");
            }
            if (file.length() > MAX_FILE_SIZE_BYTES) {
                return ScanResult.suspicious("File vượt quá dung lượng cho phép ("
                        + (MAX_FILE_SIZE_BYTES / (1024 * 1024)) + " MB).");
            }

            byte[] header = readHeader(file, 8);
            if (startsWith(header, OLE2_MAGIC)) {
                return ScanResult.suspicious(
                        "File ở định dạng Office nhị phân cũ (.doc/.xls) — dễ chứa macro độc hại. "
                                + "Vui lòng lưu lại dưới định dạng .xlsx/.docx rồi thử lại.");
            }
            if (!startsWith(header, ZIP_MAGIC)) {
                return ScanResult.suspicious(
                        "Nội dung file không đúng định dạng Excel/Word thật (không phải gói OOXML hợp lệ). "
                                + "Có thể file đã bị đổi đuôi hoặc hỏng.");
            }

            return scanZipStructure(file);
        } catch (IOException e) {
            return ScanResult.error("Không đọc được file để quét: " + e.getMessage());
        }
    }

    private ScanResult scanZipStructure(File file) {
        long totalUncompressed = 0;
        try (ZipFile zip = new ZipFile(file)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();

                // Zip slip: entry co the ghi ra ngoai thu muc dich khi giai nen.
                if (name.contains("..") || name.startsWith("/") || name.contains("\\..")) {
                    return ScanResult.infected("ZIP_SLIP", "Phát hiện đường dẫn bất thường trong file ("
                            + name + ") — dấu hiệu tấn công zip slip.");
                }

                String lower = name.toLowerCase(Locale.ROOT);

                // Macro nhung san trong file .xlsx/.docx "sach" la dau hieu doi duoi tu .xlsm/.docm.
                if (lower.endsWith("vbaproject.bin")) {
                    return ScanResult.infected("MACRO_EMBEDDED",
                            "File chứa macro VBA (vbaProject.bin) trong khi phần mở rộng khai báo là "
                                    + "định dạng không hỗ trợ macro. Vui lòng chỉ nhập file .xlsx/.docx không có macro.");
                }

                for (String ext : DANGEROUS_EXTENSIONS) {
                    if (lower.endsWith(ext)) {
                        return ScanResult.infected("EMBEDDED_EXECUTABLE",
                                "File chứa đối tượng nhúng đáng ngờ (" + name + ") có thể là mã thực thi/script độc hại.");
                    }
                }

                long size = entry.getSize();
                if (size > 0) {
                    if (size > MAX_ENTRY_UNCOMPRESSED) {
                        return ScanResult.suspicious("File chứa dữ liệu nén bất thường lớn, nghi ngờ zip bomb.");
                    }
                    long compressed = Math.max(entry.getCompressedSize(), 1);
                    double ratio = (double) size / (double) compressed;
                    if (ratio > MAX_COMPRESSION_RATIO) {
                        return ScanResult.suspicious("Tỉ lệ nén bất thường (" + String.format(Locale.ROOT, "%.0f", ratio)
                                + "x), nghi ngờ zip bomb.");
                    }
                    totalUncompressed += size;
                    if (totalUncompressed > MAX_UNCOMPRESSED_TOTAL) {
                        return ScanResult.suspicious("Tổng dung lượng giải nén vượt ngưỡng an toàn, nghi ngờ zip bomb.");
                    }
                }
            }
        } catch (IOException e) {
            return ScanResult.suspicious("File không phải gói OOXML hợp lệ hoặc đã hỏng: " + e.getMessage());
        }
        return ScanResult.clean("Không phát hiện dấu hiệu bất thường (kiểm tra heuristic nội bộ).");
    }

    private static byte[] readHeader(File file, int len) throws IOException {
        byte[] buf = new byte[len];
        try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
            int read = fis.read(buf);
            if (read < len) {
                byte[] trimmed = new byte[Math.max(read, 0)];
                System.arraycopy(buf, 0, trimmed, 0, trimmed.length);
                return trimmed;
            }
        }
        return buf;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
    }
}