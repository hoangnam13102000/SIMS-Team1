package com.components.table;

import com.utils.ImageUtil;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cot hien thi anh thu nho (bo goc) trong table - dung cho anh san pham, avatar
 * user... Nhan gia tri o (duong dan file hoac URL http/https, tai su dung
 * ImageUtil co san) va tu ve icon bo goc dung kich thuoc.
 *
 * De khong giat UI khi duong dan la URL mang, anh duoc tai bat dong bo (SwingWorker)
 * va cache lai theo duong dan; lan dau se hien placeholder xam, tai xong tu
 * repaint lai table.
 *
 * Cach dung:
 *   ImageColumn imageColumn = new ImageColumn(40);
 *   table.getColumnModel().getColumn(1).setCellRenderer(imageColumn.renderer(rowColorProvider));
 */
public class ImageColumn {

    private final int size;
    private final int radius;
    private final Map<String, ImageIcon> cache = new ConcurrentHashMap<>();
    private final Set<String> loading = ConcurrentHashMap.newKeySet();
    private ImageIcon placeholder;

    public ImageColumn(int size) {
        this(size, Math.max(4, size / 5));
    }

    public ImageColumn(int size, int radius) {
        this.size = size;
        this.radius = radius;
    }

    public TableCellRenderer renderer(RowColorProvider colorProvider) {
        JLabel label = new JLabel();
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setVerticalAlignment(SwingConstants.CENTER);

        return (t, value, isSelected, hasFocus, row, column) -> {
            label.setOpaque(true);
            label.setBackground(colorProvider.colorFor(row, isSelected));

            String path = value == null ? null : value.toString().trim();
            label.setIcon(resolveIcon(path, t));
            return label;
        };
    }

    private ImageIcon resolveIcon(String path, JTable table) {
        if (path == null || path.isEmpty()) return placeholderIcon();

        ImageIcon cached = cache.get(path);
        if (cached != null) return cached;

        if (loading.add(path)) {
        	SwingWorker<ImageIcon, Void> worker = new SwingWorker<ImageIcon, Void>()  {
                @Override
                protected ImageIcon doInBackground() {
                    BufferedImage img = ImageUtil.readSafe(path);
                    if (img == null) return placeholderIcon();
                    return new ImageIcon(roundCorners(ImageUtil.scale(img, size, size), radius));
                }

                @Override
                protected void done() {
                    try {
                        cache.put(path, get());
                    } catch (Exception ignored) {
                        cache.put(path, placeholderIcon());
                    } finally {
                        loading.remove(path);
                        table.repaint();
                    }
                }
            };
            worker.execute();
        }
        return placeholderIcon();
    }

    private BufferedImage roundCorners(BufferedImage src, int r) {
        BufferedImage result = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = result.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setClip(new RoundRectangle2D.Float(0, 0, src.getWidth(), src.getHeight(), r, r));
        g2.drawImage(src, 0, 0, null);
        g2.dispose();
        return result;
    }

    private ImageIcon placeholderIcon() {
        if (placeholder == null) {
            placeholder = new ImageIcon(roundCorners(ImageUtil.placeholder(size, size), radius));
        }
        return placeholder;
    }

    /** Xoa cache (vd sau khi sua anh san pham) de lan render sau tai lai anh moi. */
    public void invalidate(String path) {
        if (path != null) cache.remove(path);
    }

    public void clearCache() {
        cache.clear();
    }
}