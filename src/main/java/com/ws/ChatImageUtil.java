package com.ws;

import com.utils.ImageUtil;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Base64;
import java.util.Iterator;

/**
 * Chuan hoa anh truoc khi gui qua WebSocket chat: resize, nen JPEG, Base64.
 * Gioi han kich thuoc de tranh lam treo ket noi / UI.
 */
public final class ChatImageUtil {

    /** Canh dai nhat cua anh sau khi scale (px). */
    public static final int MAX_EDGE = 800;
    /** Dung luong byte toi da cua anh da nen (truoc Base64). ~450KB. */
    public static final int MAX_BYTES = 450_000;
    /** Chat chi chap nhan cac dinh dang nay. */
    private static final String[] ALLOWED_EXT = {"jpg", "jpeg", "png", "gif", "bmp", "webp"};

    private ChatImageUtil() {
    }

    public static boolean isSupportedImage(File file) {
        if (file == null || !file.isFile()) return false;
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot < 0) return false;
        String ext = name.substring(dot + 1).toLowerCase();
        for (String allowed : ALLOWED_EXT) {
            if (allowed.equals(ext)) return true;
        }
        return false;
    }

    /**
     * Doc file, scale theo canh dai nhat MAX_EDGE, nen JPEG (chat luong giam
     * dan neu van vuot MAX_BYTES). Tra ve null neu khong doc duoc / qua lon.
     */
    public static EncodedImage encodeForChat(File file) {
        BufferedImage src = ImageUtil.readSafe(file);
        if (src == null) return null;
        return encodeForChat(src);
    }

    public static EncodedImage encodeForChat(BufferedImage src) {
        if (src == null) return null;
        BufferedImage scaled = ImageUtil.scaleKeepAspect(src, MAX_EDGE, MAX_EDGE);
        // JPEG khong ho tro alpha — ve len nen trang neu can.
        BufferedImage rgb = toRgb(scaled);

        float quality = 0.85f;
        byte[] bytes = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            bytes = writeJpeg(rgb, quality);
            if (bytes == null) return null;
            if (bytes.length <= MAX_BYTES) break;
            quality -= 0.15f;
            if (quality < 0.35f) break;
        }
        if (bytes == null || bytes.length > MAX_BYTES * 1.2) {
            // Van qua lon: scale them 1 lan nua.
            BufferedImage smaller = ImageUtil.scaleKeepAspect(rgb, MAX_EDGE / 2, MAX_EDGE / 2);
            bytes = writeJpeg(toRgb(smaller), 0.7f);
            if (bytes == null || bytes.length > MAX_BYTES * 1.5) return null;
        }
        return new EncodedImage(Base64.getEncoder().encodeToString(bytes), "image/jpeg", bytes.length);
    }

    public static BufferedImage decodeBase64(String base64) {
        if (base64 == null || base64.isBlank()) return null;
        try {
            // Bo prefix data:image/...;base64, neu co.
            String raw = base64;
            int comma = raw.indexOf(',');
            if (raw.startsWith("data:") && comma > 0) {
                raw = raw.substring(comma + 1);
            }
            byte[] bytes = Base64.getDecoder().decode(raw);
            return ImageIO.read(new java.io.ByteArrayInputStream(bytes));
        } catch (Exception e) {
            return null;
        }
    }

    private static BufferedImage toRgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_RGB) return src;
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = rgb.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return rgb;
    }

    private static byte[] writeJpeg(BufferedImage img, float quality) {
        try {
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
            if (!writers.hasNext()) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(img, "jpg", baos);
                return baos.toByteArray();
            }
            ImageWriter writer = writers.next();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                writer.setOutput(ios);
                ImageWriteParam param = writer.getDefaultWriteParam();
                if (param.canWriteCompressed()) {
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(Math.max(0.1f, Math.min(1f, quality)));
                }
                writer.write(null, new IIOImage(img, null, null), param);
            } finally {
                writer.dispose();
            }
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    public static final class EncodedImage {
        public final String base64;
        public final String mime;
        public final int byteSize;

        public EncodedImage(String base64, String mime, int byteSize) {
            this.base64 = base64;
            this.mime = mime;
            this.byteSize = byteSize;
        }
    }
}