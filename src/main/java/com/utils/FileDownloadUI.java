package com.utils;

import com.components.AppAlert;
import com.theme.AppColor;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Base64;
import java.util.List;

/**
 * Tải / lưu file đính kèm chat: hiện dialog progress giống web,
 * ghi file nền, báo thành công / lỗi khi xong.
 */
public final class FileDownloadUI {

    private FileDownloadUI() {
    }

    /**
     * Decode Base64 → chọn nơi lưu → progress bar → thông báo kết quả.
     *
     * @param parent   component cha (dialog)
     * @param fileName tên file gợi ý
     * @param base64   nội dung Base64
     */
    public static void saveBase64WithProgress(Component parent, String fileName, String base64) {
        if (parent == null || fileName == null || fileName.isBlank()
                || base64 == null || base64.isBlank()) {
            return;
        }

        final byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64);
        } catch (Exception e) {
            AppAlert.error(parent, "Lỗi", "Không đọc được nội dung file.");
            return;
        }
        if (bytes == null || bytes.length == 0) {
            AppAlert.error(parent, "Lỗi", "File trống hoặc không hợp lệ.");
            return;
        }

        File dest = FileUtil.chooseSaveLocation(parent, fileName);
        if (dest == null) return;

        Window owner = parent instanceof Window
                ? (Window) parent
                : SwingUtilities.getWindowAncestor(parent);

        JDialog progressDlg = new JDialog(owner, "Đang tải xuống...", Dialog.ModalityType.APPLICATION_MODAL);
        progressDlg.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        progressDlg.setResizable(false);

        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setBorder(new EmptyBorder(20, 24, 20, 24));
        panel.setBackground(AppColor.WHITE);
        panel.setPreferredSize(new Dimension(360, 120));

        JLabel title = new JLabel("Đang tải: " + dest.getName());
        title.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        title.setForeground(AppColor.TEXT_PRIMARY);

        JProgressBar bar = new JProgressBar(0, 100);
        bar.setStringPainted(true);
        bar.setValue(0);
        bar.setPreferredSize(new Dimension(320, 22));

        JLabel status = new JLabel("0%");
        status.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        status.setForeground(AppColor.TEXT_MUTED);

        panel.add(title, BorderLayout.NORTH);
        panel.add(bar, BorderLayout.CENTER);
        panel.add(status, BorderLayout.SOUTH);
        progressDlg.setContentPane(panel);
        progressDlg.pack();
        progressDlg.setLocationRelativeTo(parent);

        SwingWorker<Void, Integer> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                final int chunk = Math.max(8 * 1024, bytes.length / 50);
                try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(dest))) {
                    int written = 0;
                    while (written < bytes.length) {
                        int len = Math.min(chunk, bytes.length - written);
                        out.write(bytes, written, len);
                        written += len;
                        int pct = (int) ((written * 100L) / bytes.length);
                        publish(pct);
                        // File nhỏ: cho UI kịp vẽ progress
                        if (bytes.length < 200_000) {
                            Thread.sleep(8);
                        }
                    }
                    out.flush();
                }
                publish(100);
                return null;
            }

            @Override
            protected void process(List<Integer> chunks) {
                int pct = chunks.get(chunks.size() - 1);
                bar.setValue(pct);
                status.setText(pct + "% — " + formatSize(bytes.length));
            }

            @Override
            protected void done() {
                progressDlg.dispose();
                try {
                    get();
                    AppAlert.success(parent, "Thành công",
                            "Tải xuống thành công!\n" + dest.getAbsolutePath());
                } catch (Exception ex) {
                    AppAlert.error(parent, "Lỗi",
                            "Tải xuống thất bại: " + (ex.getCause() != null
                                    ? ex.getCause().getMessage()
                                    : ex.getMessage()));
                }
            }
        };

        worker.execute();
        progressDlg.setVisible(true); // block until dispose in done()
    }

    private static String formatSize(int bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }
}