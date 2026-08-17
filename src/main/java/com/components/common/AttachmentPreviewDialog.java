package com.components.common;

import com.security.ScanResult;
import com.security.ScanStatus;
import com.theme.AppColor;
import com.theme.AppConstant;
import com.theme.AppFont;
import com.ws.ChatFileUtil;
import com.ws.ChatImageUtil;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

/**
 * Dialog xem trước nội dung file + kết quả quét virus trước khi đính kèm lên AI chat.
 * Nút bấm dùng cùng style rounded với {@link com.components.BaseDialog}.
 */
public final class AttachmentPreviewDialog extends JDialog {

    private static final int BUTTON_CORNER_RADIUS = AppConstant.RADIUS_LG;
    private static final int BUTTON_PADDING_TOP = 10;
    private static final int BUTTON_PADDING_BOTTOM = 10;
    private static final int BUTTON_PADDING_LEFT = 24;
    private static final int BUTTON_PADDING_RIGHT = 24;

    private boolean accepted = false;

    private AttachmentPreviewDialog(Window owner, File file, ScanResult scan, String previewText,
                                    ImageIcon imagePreview) {
        super(owner, "Xem trước & kiểm tra file", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(520, 420));
        setSize(640, 520);
        setLocationRelativeTo(owner);

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBorder(new EmptyBorder(16, 18, 8, 18));
        root.setBackground(AppColor.WHITE);

        // Header meta
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        String name = file.getName();
        String sizeLabel = formatSize(file.length());
        String ext = ChatFileUtil.extensionOf(name).toUpperCase(Locale.ROOT);

        JLabel title = new JLabel(name);
        title.setFont(AppFont.DIALOG_TITLE);
        title.setForeground(AppColor.TEXT_TITLE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel meta = new JLabel("Loại: " + (ext.isEmpty() ? "—" : ext)
                + "   ·   Dung lượng: " + sizeLabel
                + "   ·   " + file.getAbsolutePath());
        meta.setFont(AppFont.SMALL);
        meta.setForeground(AppColor.TEXT_MUTED);
        meta.setAlignmentX(Component.LEFT_ALIGNMENT);

        header.add(title);
        header.add(Box.createVerticalStrut(4));
        header.add(meta);
        header.add(Box.createVerticalStrut(10));
        header.add(buildScanBanner(scan));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);

        root.add(header, BorderLayout.NORTH);

        JComponent preview = buildPreviewBody(previewText, imagePreview);
        JScrollPane scroll = new JScrollPane(preview);
        scroll.setBorder(BorderFactory.createLineBorder(AppColor.BORDER, 1, true));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getViewport().setBackground(AppColor.WHITE);
        root.add(scroll, BorderLayout.CENTER);

        // Footer — cùng style BaseDialog (Hủy outline + primary)
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        footer.setBackground(AppColor.WHITE);
        footer.setBorder(new EmptyBorder(8, 0, 8, 0));

        JButton cancel = createModernButton(
                "Hủy",
                AppColor.CANCEL_BG,
                AppColor.CANCEL_HOVER,
                AppColor.TEXT_PRIMARY);
        cancel.addActionListener(e -> {
            accepted = false;
            dispose();
        });

        JButton ok = createModernButton(
                "Đính kèm file này",
                AppColor.ACCENT,
                AppColor.ACCENT_HOVER,
                Color.WHITE);
        boolean block = scan != null && scan.isBlocked();
        ok.setEnabled(!block);
        if (block) {
            ok.setToolTipText("File bị chặn do kết quả quét bảo mật");
            ok.setBackground(AppColor.CANCEL_BG);
            ok.setForeground(AppColor.TEXT_MUTED);
        }
        ok.addActionListener(e -> {
            if (!ok.isEnabled()) return;
            accepted = true;
            dispose();
        });

        footer.add(cancel);
        footer.add(ok);
        root.add(footer, BorderLayout.SOUTH);

        setContentPane(root);
        getRootPane().setDefaultButton(ok);
    }

    /** Giống BaseDialog.createModernButton — bo góc + hover. */
    private static JButton createModernButton(String text, Color bg, Color hover, Color fg) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), BUTTON_CORNER_RADIUS, BUTTON_CORNER_RADIUS);
                g2.setColor(getForeground());
                FontMetrics fm = g2.getFontMetrics();
                int textX = (getWidth() - fm.stringWidth(getText())) / 2;
                int textY = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(getText(), textX, textY);
                g2.dispose();
            }
        };
        button.setFont(AppFont.BODY_BOLD);
        button.setForeground(fg);
        button.setBackground(bg);
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(
                BUTTON_PADDING_TOP, BUTTON_PADDING_LEFT,
                BUTTON_PADDING_BOTTOM, BUTTON_PADDING_RIGHT));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                if (!button.isEnabled()) return;
                button.setBackground(hover);
                button.repaint();
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                if (!button.isEnabled()) return;
                button.setBackground(bg);
                button.repaint();
            }
        });
        return button;
    }

    private JPanel buildScanBanner(ScanResult scan) {
        JPanel banner = new JPanel(new BorderLayout(10, 0));
        banner.setAlignmentX(Component.LEFT_ALIGNMENT);
        banner.setBorder(new EmptyBorder(10, 12, 10, 12));

        Color bg;
        Color fg;
        FontAwesomeSolid iconType;
        String headline;

        if (scan != null && scan.isBlocked()) {
            bg = AppColor.ERROR_BG != null ? AppColor.ERROR_BG : new Color(254, 226, 226);
            fg = AppColor.ERROR;
            iconType = FontAwesomeSolid.SHIELD_ALT;
            headline = "ĐÃ CHẶN — không được đính kèm";
        } else if (scan != null && scan.isWarning()) {
            bg = AppColor.WARNING_BG != null ? AppColor.WARNING_BG : new Color(254, 243, 199);
            fg = AppColor.WARNING;
            iconType = FontAwesomeSolid.EXCLAMATION_TRIANGLE;
            headline = "Cảnh báo — quét AV hạn chế, vẫn có thể đính kèm";
        } else {
            bg = AppColor.SUCCESS_BG != null ? AppColor.SUCCESS_BG : new Color(220, 252, 231);
            fg = AppColor.SUCCESS;
            iconType = FontAwesomeSolid.CHECK_CIRCLE;
            headline = "An toàn — đã kiểm tra trước khi tải lên";
        }

        banner.setBackground(bg);
        FontIcon icon = FontIcon.of(iconType, 16);
        icon.setIconColor(fg);

        JLabel head = new JLabel(headline, icon, SwingConstants.LEFT);
        head.setIconTextGap(8);
        head.setFont(AppFont.BODY_BOLD);
        head.setForeground(fg);

        String detail = scan != null && scan.getMessage() != null ? scan.getMessage() : "";
        if (scan != null && scan.getThreatName() != null && !scan.getThreatName().isBlank()) {
            detail = "Mối đe dọa: " + scan.getThreatName() + "\n" + detail;
        }
        JTextArea detailArea = new JTextArea(detail);
        detailArea.setEditable(false);
        detailArea.setOpaque(false);
        detailArea.setLineWrap(true);
        detailArea.setWrapStyleWord(true);
        detailArea.setFont(AppFont.SMALL);
        detailArea.setForeground(AppColor.TEXT_PRIMARY);

        JPanel textCol = new JPanel(new BorderLayout(0, 4));
        textCol.setOpaque(false);
        textCol.add(head, BorderLayout.NORTH);
        textCol.add(detailArea, BorderLayout.CENTER);
        banner.add(textCol, BorderLayout.CENTER);
        return banner;
    }

    private JComponent buildPreviewBody(String previewText, ImageIcon imagePreview) {
        if (imagePreview != null) {
            JLabel img = new JLabel(imagePreview);
            img.setHorizontalAlignment(SwingConstants.CENTER);
            img.setBorder(new EmptyBorder(12, 12, 12, 12));
            img.setOpaque(true);
            img.setBackground(AppColor.WHITE);
            return img;
        }
        JTextArea area = new JTextArea(previewText != null ? previewText : "(Không có bản xem trước)");
        area.setEditable(false);
        area.setFont(new Font("Consolas", Font.PLAIN, 12));
        area.setForeground(AppColor.TEXT_PRIMARY);
        area.setBackground(AppColor.WHITE);
        area.setCaretColor(AppColor.TEXT_PRIMARY);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(new EmptyBorder(10, 12, 10, 12));
        return area;
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.ROOT, "%.2f MB", bytes / (1024.0 * 1024.0));
    }

    /**
     * Hiện dialog xem trước + kết quả scan. Trả về true nếu user xác nhận đính kèm.
     */
    public static boolean showPreview(Component parent, File file, ScanResult scan) {
        if (file == null || !file.isFile()) return false;

        String previewText = buildTextPreview(file);
        ImageIcon imageIcon = null;
        if (ChatFileUtil.isImageExtension(file.getName()) && ChatImageUtil.isSupportedImage(file)) {
            try {
                BufferedImage bi = javax.imageio.ImageIO.read(file);
                if (bi != null) {
                    int max = 360;
                    int w = bi.getWidth();
                    int h = bi.getHeight();
                    double scale = Math.min(1.0, Math.min(max / (double) w, max / (double) h));
                    int nw = Math.max(1, (int) (w * scale));
                    int nh = Math.max(1, (int) (h * scale));
                    Image scaled = bi.getScaledInstance(nw, nh, Image.SCALE_SMOOTH);
                    imageIcon = new ImageIcon(scaled);
                }
            } catch (Exception ignored) {
            }
        }

        Window owner = parent != null ? SwingUtilities.getWindowAncestor(parent) : null;
        if (owner == null && parent instanceof Window) owner = (Window) parent;
        AttachmentPreviewDialog dialog = new AttachmentPreviewDialog(owner, file, scan, previewText, imageIcon);
        dialog.setVisible(true);
        return dialog.accepted;
    }

    static String buildTextPreview(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".txt") || name.endsWith(".csv") || name.endsWith(".log")
                    || name.endsWith(".md") || name.endsWith(".json") || name.endsWith(".xml")) {
                byte[] raw = Files.readAllBytes(file.toPath());
                int max = Math.min(raw.length, 32_000);
                String text = new String(raw, 0, max, StandardCharsets.UTF_8);
                if (raw.length > max) {
                    text += "\n\n… (đã cắt, chỉ xem ~32KB đầu file)";
                }
                return text;
            }
            if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
                return "File bảng tính: " + file.getName() + "\n"
                        + "Dung lượng: " + formatSize(file.length()) + "\n\n"
                        + "Nội dung ô Excel sẽ được AI đọc khi bạn gửi tin "
                        + "(import/phân tích). Xác nhận đính kèm nếu đúng file cần dùng.";
            }
            if (name.endsWith(".docx") || name.endsWith(".doc")) {
                return "File Word: " + file.getName() + "\n"
                        + "Dung lượng: " + formatSize(file.length()) + "\n\n"
                        + "Nội dung văn bản sẽ được xử lý sau khi gửi. "
                        + "Hãy xác nhận đây đúng tài liệu cần đính kèm.";
            }
            if (name.endsWith(".pdf")) {
                return "File PDF: " + file.getName() + "\n"
                        + "Dung lượng: " + formatSize(file.length()) + "\n\n"
                        + "PDF không render trang trong hộp thoại này. "
                        + "Mở bằng trình đọc PDF nếu cần xem chi tiết trước khi đính kèm.";
            }
            if (name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".7z")) {
                return "File nén: " + file.getName() + "\n"
                        + "Dung lượng: " + formatSize(file.length()) + "\n\n"
                        + "Không giải nén tại đây. Chỉ đính kèm nếu bạn tin nguồn file.";
            }
            return "File: " + file.getName() + "\n"
                    + "Dung lượng: " + formatSize(file.length()) + "\n"
                    + "MIME ước lượng: " + ChatFileUtil.mimeOf(file.getName()) + "\n\n"
                    + "Không có xem trước dạng văn bản cho loại này.";
        } catch (Exception e) {
            return "Không đọc được nội dung xem trước: " + e.getMessage();
        }
    }
}
