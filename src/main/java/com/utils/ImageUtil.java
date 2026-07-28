package com.utils;


import com.theme.AppColor;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;

/**
 * Tien ich xu ly anh dung chung cho Swing: doc anh an toan (khong crash UI
 * khi thieu/loi anh), resize, bo tron lam avatar, tao avatar placeholder co
 * chu cai dau. Khong phu thuoc model nao cua du an (Phone, User...) nen dung
 * lai duoc cho bat ky ung dung Swing khac.
 */
public final class ImageUtil {

    private ImageUtil() {}

    private static final String[] SUPPORTED_EXTENSIONS = {"jpg", "jpeg", "png", "gif", "bmp", "webp"};

    // ---------- Doc anh an toan ----------

    /** Doc anh tu file, tra ve null neu loi (khong nem exception ra UI). */
    public static BufferedImage readSafe(File file) {
        if (file == null || !file.exists()) return null;
        try {
            return ImageIO.read(file);
        } catch (IOException e) {
            return null;
        }
    }

    /** Doc anh tu duong dan - co the la file local hoac URL http/https (vd Phone.imageUrl). */
    public static BufferedImage readSafe(String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.isBlank()) return null;
        try {
            if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
                return ImageIO.read(new URL(pathOrUrl));
            }
            return ImageIO.read(new File(pathOrUrl));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Tao ImageIcon da resize tu duong dan; neu doc loi (anh hong/khong ton
     * tai) se tra ve placeholder xam thay vi crash - dung cho o luoi san pham,
     * anh dai dien...
     */
    public static ImageIcon loadIcon(String pathOrUrl, int width, int height) {
        BufferedImage img = readSafe(pathOrUrl);
        if (img == null) return new ImageIcon(placeholder(width, height));
        return new ImageIcon(scale(img, width, height));
    }

    // ---------- Resize ----------

    public static BufferedImage scale(BufferedImage src, int width, int height) {
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = result.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g2.drawImage(src, 0, 0, width, height, null);
        g2.dispose();
        return result;
    }

    /** Resize giu nguyen ty le, anh vua khit trong khung width x height. */
    public static BufferedImage scaleKeepAspect(BufferedImage src, int maxWidth, int maxHeight) {
        double ratio = Math.min((double) maxWidth / src.getWidth(), (double) maxHeight / src.getHeight());
        int w = Math.max(1, (int) (src.getWidth() * ratio));
        int h = Math.max(1, (int) (src.getHeight() * ratio));
        return scale(src, w, h);
    }

    // ---------- Avatar / bo tron ----------

    /** Cat anh thanh hinh tron, dung cho avatar nguoi dung (vd ProfilePanel). */
    public static BufferedImage toCircular(BufferedImage src) {
        int size = Math.min(src.getWidth(), src.getHeight());
        BufferedImage result = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = result.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setClip(new Ellipse2D.Float(0, 0, size, size));
        int offsetX = (src.getWidth() - size) / 2;
        int offsetY = (src.getHeight() - size) / 2;
        g2.drawImage(src, -offsetX, -offsetY, null);
        g2.dispose();
        return result;
    }

    /** Icon avatar hinh tron kich thuoc dat truoc, tu duong dan anh (tu dong placeholder neu loi). */
    public static ImageIcon circularIcon(String pathOrUrl, int diameter) {
        return circularIcon(pathOrUrl, diameter, "?");
    }

    /**
     * Icon avatar hinh tron - neu doc anh loi/khong co thi tra ve placeholder
     * voi chu cai dau lay tu {@code initials} (vd ten "Nam" -> "N"), dung cho
     * AdminHeader/ClientHeader khi user chua upload anh dai dien.
     */
    public static ImageIcon circularIcon(String pathOrUrl, int diameter, String initials) {
        BufferedImage img = readSafe(pathOrUrl);
        if (img == null) return new ImageIcon(placeholderAvatar(diameter, initials, AppColor.ACCENT));
        return new ImageIcon(toCircular(scale(img, diameter, diameter)));
    }

    /**
     * Avatar placeholder hinh tron voi 1 chu cai dau (vd "Nam" -> "N"), dung
     * khi nguoi dung chua upload anh dai dien - giong pattern o cac dashboard mau.
     */
    public static BufferedImage placeholderAvatar(int diameter, String initials, Color background) {
        BufferedImage img = new BufferedImage(diameter, diameter, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(background);
        g2.fill(new Ellipse2D.Float(0, 0, diameter, diameter));

        String text = (initials == null || initials.isBlank()) ? "?" : initials.trim().substring(0, 1).toUpperCase();
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Segoe UI", Font.BOLD, (int) (diameter * 0.45)));
        FontMetrics fm = g2.getFontMetrics();
        int x = (diameter - fm.stringWidth(text)) / 2;
        int y = (diameter - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(text, x, y);
        g2.dispose();
        return img;
    }

    /** Anh xam trung tinh dung khi khong doc duoc anh that (loi/khong ton tai). */
    public static BufferedImage placeholder(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setColor(AppColor.BORDER);
        g2.fillRect(0, 0, width, height);
        g2.dispose();
        return img;
    }

    // ---------- Kiem tra ----------

    /** Kiem tra file co phai dinh dang anh duoc ho tro khong (jpg, jpeg, png, gif, bmp, webp). */
    public static boolean isSupportedImage(File file) {
        if (file == null) return false;
        String ext = FileUtil.getExtension(file.getName()).toLowerCase();
        for (String s : SUPPORTED_EXTENSIONS) {
            if (s.equals(ext)) return true;
        }
        return false;
    }
}