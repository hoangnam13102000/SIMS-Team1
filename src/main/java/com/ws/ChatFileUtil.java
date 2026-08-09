package com.ws;

import com.utils.FileUtil;

import java.io.File;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Locale;

/**
 * Ma hoa / kiem tra file dinh kem chat (pdf, office, zip...).
 * Anh nen uu tien {@link ChatImageUtil} de nen JPEG.
 */
public final class ChatFileUtil {

    /** Gioi han dung luong file goc (bytes) truoc Base64. ~3MB. */
    public static final int MAX_BYTES = 3_000_000;

    private static final String[] ALLOWED_EXT = {
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "txt", "csv", "zip", "rar", "7z",
            "jpg", "jpeg", "png", "gif", "bmp", "webp"
    };

    private ChatFileUtil() {
    }

    public static boolean isSupportedFile(File file) {
        if (file == null || !file.isFile()) return false;
        String ext = extensionOf(file.getName());
        if (ext.isEmpty()) return false;
        for (String a : ALLOWED_EXT) {
            if (a.equals(ext)) return true;
        }
        return false;
    }

    public static boolean isImageExtension(String fileName) {
        String ext = extensionOf(fileName);
        return ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png")
                || ext.equals("gif") || ext.equals("bmp") || ext.equals("webp");
    }

    public static EncodedFile encodeForChat(File file) {
        if (!isSupportedFile(file)) return null;
        try {
            long size = file.length();
            if (size <= 0 || size > MAX_BYTES) return null;
            byte[] bytes = Files.readAllBytes(file.toPath());
            if (bytes.length > MAX_BYTES) return null;
            String mime = mimeOf(file.getName());
            return new EncodedFile(
                    Base64.getEncoder().encodeToString(bytes),
                    file.getName(),
                    mime,
                    bytes.length
            );
        } catch (Exception e) {
            return null;
        }
    }

    public static byte[] decodeBase64(String base64) {
        if (base64 == null || base64.isBlank()) return null;
        try {
            return Base64.getDecoder().decode(base64);
        } catch (Exception e) {
            return null;
        }
    }

    public static String mimeOf(String fileName) {
        String ext = extensionOf(fileName);
        return switch (ext) {
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt" -> "application/vnd.ms-powerpoint";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "txt" -> "text/plain";
            case "csv" -> "text/csv";
            case "zip" -> "application/zip";
            case "rar" -> "application/vnd.rar";
            case "7z" -> "application/x-7z-compressed";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "bmp" -> "image/bmp";
            case "jpg", "jpeg" -> "image/jpeg";
            default -> "application/octet-stream";
        };
    }

    public static String extensionOf(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "";
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Mo hop thoai chon anh + file van ban/van phong. */
    public static File chooseAttachment(java.awt.Component parent) {
        return FileUtil.chooseFile(
                parent,
                "Ảnh & tài liệu (jpg, png, pdf, doc, xls, zip...)",
                ALLOWED_EXT
        );
    }

    public static final class EncodedFile {
        public final String base64;
        public final String fileName;
        public final String mime;
        public final int byteSize;

        public EncodedFile(String base64, String fileName, String mime, int byteSize) {
            this.base64 = base64;
            this.fileName = fileName;
            this.mime = mime;
            this.byteSize = byteSize;
        }
    }
}
