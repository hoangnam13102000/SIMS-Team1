package com.model.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Một lượt trong hội thoại với trợ lý AI (Gemini).
 * Hỗ trợ nhiều ảnh (vision) và nhiều file đính kèm (Excel/Word/…) trong cùng 1 tin.
 */
public class AiChatMessage {

    public final String role;
    public final String text;
    public final long timestamp;

    public final List<ImagePart> images;
    public final List<FilePart> files;

    public final String imageBase64;
    public final String imageMime;
    public final String fileName;
    public final String fileBase64;
    public final String fileMime;
    public final String localFilePath;

    public static final class ImagePart {
        public final String base64;
        public final String mime;

        public ImagePart(String base64, String mime) {
            this.base64 = base64;
            this.mime = mime != null ? mime : "image/jpeg";
        }
    }

    public static final class FilePart {
        public final String fileName;
        public final String base64;
        public final String mime;
        public final String localFilePath;

        public FilePart(String fileName, String base64, String mime, String localFilePath) {
            this.fileName = fileName;
            this.base64 = base64;
            this.mime = mime;
            this.localFilePath = localFilePath;
        }

        public boolean hasLocalPath() {
            return localFilePath != null && !localFilePath.isBlank();
        }

        public boolean isSpreadsheet() {
            if (fileName == null) return false;
            String n = fileName.toLowerCase();
            return n.endsWith(".xlsx") || n.endsWith(".docx");
        }
    }

    public AiChatMessage(String role, String text) {
        this(role, text, null, null, null, null, null, null);
    }

    public AiChatMessage(String role, String text,
                         String imageBase64, String imageMime,
                         String fileName, String fileBase64, String fileMime) {
        this(role, text, imageBase64, imageMime, fileName, fileBase64, fileMime, null);
    }

    public AiChatMessage(String role, String text,
                         String imageBase64, String imageMime,
                         String fileName, String fileBase64, String fileMime,
                         String localFilePath) {
        this.role = role;
        this.text = text != null ? text : "";
        this.timestamp = System.currentTimeMillis();

        List<ImagePart> imgs = new ArrayList<>();
        if (imageBase64 != null && !imageBase64.isBlank()) {
            imgs.add(new ImagePart(imageBase64, imageMime));
        }
        this.images = Collections.unmodifiableList(imgs);

        List<FilePart> fs = new ArrayList<>();
        if (fileName != null && !fileName.isBlank() && fileBase64 != null && !fileBase64.isBlank()) {
            fs.add(new FilePart(fileName, fileBase64, fileMime, localFilePath));
        }
        this.files = Collections.unmodifiableList(fs);

        this.imageBase64 = imageBase64;
        this.imageMime = imageMime;
        this.fileName = fileName;
        this.fileBase64 = fileBase64;
        this.fileMime = fileMime;
        this.localFilePath = localFilePath;
    }

    public AiChatMessage(String role, String text,
                         List<ImagePart> images, List<FilePart> files) {
        this.role = role;
        this.text = text != null ? text : "";
        this.timestamp = System.currentTimeMillis();
        this.images = images == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(images));
        this.files = files == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(files));

        if (!this.images.isEmpty()) {
            this.imageBase64 = this.images.get(0).base64;
            this.imageMime = this.images.get(0).mime;
        } else {
            this.imageBase64 = null;
            this.imageMime = null;
        }
        if (!this.files.isEmpty()) {
            FilePart f = this.files.get(0);
            this.fileName = f.fileName;
            this.fileBase64 = f.base64;
            this.fileMime = f.mime;
            this.localFilePath = f.localFilePath;
        } else {
            this.fileName = null;
            this.fileBase64 = null;
            this.fileMime = null;
            this.localFilePath = null;
        }
    }

    public boolean isUser() {
        return "user".equals(role);
    }

    public boolean hasImage() {
        return images != null && !images.isEmpty();
    }

    public boolean hasFile() {
        return files != null && !files.isEmpty();
    }

    public boolean hasLocalFile() {
        if (files == null) return false;
        for (FilePart f : files) {
            if (f.hasLocalPath()) return true;
        }
        return false;
    }

    public int imageCount() {
        return images == null ? 0 : images.size();
    }

    public int fileCount() {
        return files == null ? 0 : files.size();
    }

    public String textForApi() {
        String t = text == null ? "" : text.trim();
        StringBuilder extra = new StringBuilder();

        if (hasFile()) {
            for (FilePart f : files) {
                extra.append("\n[Người dùng đính kèm file: ").append(f.fileName);
                if (f.mime != null) extra.append(" (").append(f.mime).append(")");
                extra.append("]");
                if (f.hasLocalPath()) {
                    extra.append("\n[FILE_PATH:").append(f.localFilePath).append("]");
                    if (f.isSpreadsheet()) {
                        extra.append("\n(File bảng tính/Word — nếu user muốn import, gọi import_excel với file_path=\"")
                                .append(f.localFilePath).append("\")");
                    }
                }
            }
        }
        if (hasImage() && t.isEmpty() && extra.length() == 0) {
            if (imageCount() == 1) {
                return "Hãy xem ảnh đính kèm và trả lời.";
            }
            return "Hãy xem " + imageCount() + " ảnh đính kèm và trả lời (gợi ý / so sánh sản phẩm nếu phù hợp).";
        }
        if (hasImage() && t.isEmpty()) {
            extra.insert(0, "Hãy xem " + imageCount() + " ảnh đính kèm.");
        }
        String e = extra.toString().trim();
        if (e.isEmpty()) return t;
        return t.isEmpty() ? e : t + "\n" + e;
    }
}
