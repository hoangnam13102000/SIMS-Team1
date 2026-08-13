package com.view.admin;

import com.backup.BackupResult;
import com.backup.BackupSchemaGuard;
import com.components.AppAlert;
import com.components.BaseSearch;
import com.components.BaseTable;
import com.components.DatePickerField;
import com.components.LoadingOverlay;
import com.components.SectionHeader;
import com.disaster.DisasterRecoveryBootstrap;
import com.security.AppConfig;
import com.theme.AppColor;
import com.theme.AppFont;
import com.utils.DBConnection;
import com.components.BaseDialog;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.sql.Connection;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

public class BackupRecoveryPanel extends JPanel {

    private final BaseTable backupTable;
    private final JLabel statusLabel;
    private final LoadingOverlay loadingOverlay = new LoadingOverlay("Dang xu ly...");
    private final JButton restoreButton;
    private final JButton backupNowButton;

    /** Lọc danh sách file backup theo ngày (lastModified). */
    private DatePickerField fromDateFilter;
    private DatePickerField toDateFilter;
    private JLabel clearDateFilterLink;
    private boolean adjustingDateFilter;
    /** Lọc danh sách file backup theo tên file. */
    private BaseSearch fileNameSearchBar;
    private String fileNameFilterText = "";

    public BackupRecoveryPanel() {
        setLayout(new BorderLayout());
        setBackground(AppColor.PAGE_BG);
        setBorder(new EmptyBorder(20, 24, 20, 24));

        SectionHeader header = new SectionHeader(FontAwesomeSolid.SHIELD_ALT, AppColor.ACCENT,
                "Sao lưu & Khôi phục", "Bảo vệ dữ liệu khi hệ thống gặp sự cố hoặc bị tấn công");

        restoreButton = header.addButton("Khôi phục từ file...", FontAwesomeSolid.UPLOAD,
                SectionHeader.ButtonStyle.OUTLINE, AppColor.WARNING, this::onRestoreClicked);
        backupNowButton = header.addButton("Sao lưu ngay", FontAwesomeSolid.DOWNLOAD,
                SectionHeader.ButtonStyle.PRIMARY, this::onBackupNowClicked);

        backupTable = new BaseTable(new String[]{"Tên file", "Chiến lược", "Thời gian", "Dung lượng"});

        statusLabel = new JLabel(" ");
        statusLabel.setFont(AppFont.SMALL);
        statusLabel.setForeground(AppColor.TEXT_MUTED);

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.add(Box.createVerticalStrut(16));
        center.add(backupSectionCard());

        JScrollPane scroll = new JScrollPane(center);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);

        JPanel headerWrap = new JPanel(new BorderLayout());
        headerWrap.setOpaque(false);
        headerWrap.add(header, BorderLayout.NORTH);
        headerWrap.add(statusLabel, BorderLayout.SOUTH);
        headerWrap.setBorder(new EmptyBorder(0, 0, 4, 0));

        add(headerWrap, BorderLayout.NORTH);
        add(LoadingOverlay.attach(scroll, loadingOverlay), BorderLayout.CENTER);

        if (DisasterRecoveryBootstrap.isInitialized()) {
            refreshAll();
        } else {
            showSubsystemUnavailable();
        }
    }

    private void showSubsystemUnavailable() {
        String reason = DisasterRecoveryBootstrap.getLastInitFailureMessage();
        statusLabel.setForeground(AppColor.ERROR);
        String message = reason != null ? reason : "Chưa rõ lý do, xem log console.";
        statusLabel.setText("<html><body style='width: 720px'>"
                + "<b>Hệ thống sao lưu chưa sẵn sàng:</b><br>"
                + escapeHtml(message).replace("\n", "<br>")
                + "</body></html>");
        statusLabel.setToolTipText("<html><body style='width: 500px'>" + escapeHtml(message).replace("\n", "<br>") + "</body></html>");
        restoreButton.setEnabled(false);
        backupNowButton.setEnabled(false);
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private JPanel backupSectionCard() {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(AppColor.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(16, 16, 16, 16)));

        JLabel titleLabel = new JLabel("Các bản sao lưu hiện có");
        titleLabel.setFont(AppFont.HEADING_MD);
        titleLabel.setForeground(AppColor.TEXT_TITLE);

        JPanel north = new JPanel();
        north.setOpaque(false);
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        north.add(titleLabel);
        north.add(Box.createVerticalStrut(8));

        JPanel filterRow = buildBackupDateFilterBar();
        filterRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        north.add(filterRow);
        north.add(Box.createVerticalStrut(10));

        card.add(north, BorderLayout.NORTH);
        card.add(backupTable, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildBackupDateFilterBar() {
        fromDateFilter = new DatePickerField(null, true);
        toDateFilter = new DatePickerField(null, true);

        JLabel fromLabel = new JLabel("Từ ngày");
        fromLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        fromLabel.setForeground(AppColor.TEXT_MUTED);

        JLabel toLabel = new JLabel("đến");
        toLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        toLabel.setForeground(AppColor.TEXT_MUTED);

        // ====== Cùng một hàng: tìm kiếm tên file + lọc ngày ======
        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        filterRow.setOpaque(false);

        // ====== Thanh tìm kiếm tên file dùng BaseSearch (chung) - đặt trước ======
        fileNameSearchBar = new BaseSearch("Tìm theo tên file backup...");
        fileNameSearchBar.setPreferredSize(new Dimension(240, fileNameSearchBar.getPreferredSize().height));
        fileNameSearchBar.onSearch(keyword -> {
            fileNameFilterText = keyword != null ? keyword.trim().toLowerCase() : "";
            refreshBackupTable();
        });

        // Cập nhật gợi ý autocomplete từ danh sách file backup
        refreshSearchAutocomplete();

        filterRow.add(fileNameSearchBar);

        // ====== Bộ lọc theo ngày - đặt sau ======
        filterRow.add(fromLabel);
        filterRow.add(fromDateFilter);
        filterRow.add(toLabel);
        filterRow.add(toDateFilter);

        fromDateFilter.onChange(d -> onBackupDateFilterChanged());
        toDateFilter.onChange(d -> onBackupDateFilterChanged());

        FontIcon clearIcon = FontIcon.of(FontAwesomeSolid.TIMES, 14);
        clearIcon.setIconColor(AppColor.TEXT_MUTED);
        clearDateFilterLink = new JLabel(clearIcon);
        clearDateFilterLink.setToolTipText("Xóa lọc ngày");
        clearDateFilterLink.setCursor(new Cursor(Cursor.HAND_CURSOR));
        clearDateFilterLink.setVisible(false);
        clearDateFilterLink.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                fromDateFilter.setValue(null);
                toDateFilter.setValue(null);
                onBackupDateFilterChanged();
            }
            @Override
            public void mouseEntered(MouseEvent e) {
                clearIcon.setIconColor(AppColor.ERROR);
                clearDateFilterLink.repaint();
            }
            @Override
            public void mouseExited(MouseEvent e) {
                clearIcon.setIconColor(AppColor.TEXT_MUTED);
                clearDateFilterLink.repaint();
            }
        });
        filterRow.add(clearDateFilterLink);

        return filterRow;
    }

    /** Cập nhật danh sách gợi ý autocomplete cho thanh tìm kiếm từ tên các file backup. */
    private void refreshSearchAutocomplete() {
        if (fileNameSearchBar == null || !DisasterRecoveryBootstrap.isInitialized()) return;
        SwingWorker<List<String>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<String> doInBackground() {
                List<File> backups = DisasterRecoveryBootstrap.getBackupManager().getStorage().listBackups();
                List<String> suggestions = new java.util.ArrayList<>();
                for (File f : backups) {
                    String name = f.getName();
                    suggestions.add(name);
                    // Thêm cả tên strategy để người dùng có thể tìm theo strategy
                    String strategy = extractStrategyName(name);
                    if (!"?".equals(strategy) && !suggestions.contains(strategy)) {
                        suggestions.add(strategy);
                    }
                }
                return suggestions;
            }
            @Override
            protected void done() {
                try {
                    List<String> suggestions = get();
                    if (suggestions != null && !suggestions.isEmpty()) {
                        fileNameSearchBar.setSuggestions(suggestions);
                    }
                } catch (Exception ignored) {}
            }
        };
        worker.execute();
    }

    private void onBackupDateFilterChanged() {
        if (adjustingDateFilter) return;
        LocalDate from = fromDateFilter != null ? fromDateFilter.getValue() : null;
        LocalDate to = toDateFilter != null ? toDateFilter.getValue() : null;

        if (from != null && to != null && to.isBefore(from)) {
            AppAlert.warning(this, "Khoảng ngày không hợp lệ",
                    "Ngày \"đến\" phải lớn hơn hoặc bằng ngày \"từ\".");
            adjustingDateFilter = true;
            try {
                toDateFilter.setValue(null);
            } finally {
                adjustingDateFilter = false;
            }
        }

        if (clearDateFilterLink != null) {
            clearDateFilterLink.setVisible(
                    (fromDateFilter != null && fromDateFilter.getValue() != null)
                            || (toDateFilter != null && toDateFilter.getValue() != null));
        }

        refreshBackupTable();
    }

    private void onFileNameSearchChanged() {
        // BaseSearch xử lý trực tiếp qua onSearch lambda, không cần method này nữa
        // Giữ lại để tương thích nếu có nơi khác gọi
        if (fileNameSearchBar != null) {
            String text = fileNameSearchBar.getText();
            fileNameFilterText = text != null ? text.trim().toLowerCase() : "";
        }
        refreshBackupTable();
    }

    private boolean matchesFileNameFilter(File f) {
        if (fileNameFilterText == null || fileNameFilterText.isEmpty()) return true;
        String fileName = f.getName().toLowerCase();
        // Tìm kiếm theo nhiều từ khóa (phân cách bằng khoảng trắng)
        String[] keywords = fileNameFilterText.split("\s+");
        for (String kw : keywords) {
            if (!kw.isEmpty() && !fileName.contains(kw)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesDateFilter(File f) {
        LocalDate from = fromDateFilter != null ? fromDateFilter.getValue() : null;
        LocalDate to = toDateFilter != null ? toDateFilter.getValue() : null;
        if (from == null && to == null) return true;

        LocalDate fileDate = Instant.ofEpochMilli(f.lastModified())
                .atZone(ZoneId.systemDefault())
                .toLocalDate();

        if (from != null && fileDate.isBefore(from)) return false;
        if (to != null && fileDate.isAfter(to)) return false;
        return true;
    }

    private void onBackupNowClicked() {
        loadingOverlay.start("Đang sao lưu...");
        new SwingWorker<BackupResult, Void>() {
            String error;
            @Override protected BackupResult doInBackground() {
                try { return DisasterRecoveryBootstrap.getBackupManager().backupNow(); }
                catch (Exception e) { error = e.getMessage(); return null; }
            }
            @Override protected void done() {
                loadingOverlay.stop();
                if (error != null) {
                    statusLabel.setForeground(AppColor.ERROR);
                    statusLabel.setText("Sao lưu thất bại: " + error);
                    BaseDialog.error(BackupRecoveryPanel.this, "Sao lưu thất bại", error);
                } else {
                    statusLabel.setForeground(AppColor.SUCCESS);
                    statusLabel.setText("Sao lưu thành công lúc "
                            + new SimpleDateFormat("HH:mm:ss dd/MM/yyyy").format(new java.util.Date()));
                    BaseDialog.success(BackupRecoveryPanel.this, "Sao lưu thành công",
                            "Đã tạo bản sao lưu mới. Danh sách bên dưới đã được cập nhật.");
                }
                refreshAll();
            }
        }.execute();
    }

    private void onRestoreClicked() {
        File backupDir = DisasterRecoveryBootstrap.getBackupManager().getStorage().getDirectory();
        JFileChooser chooser = new JFileChooser(backupDir);
        chooser.setDialogTitle("Chọn file backup để khôi phục");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File selected = chooser.getSelectedFile();
        loadingOverlay.start("Đang kiểm tra bản backup...");
        new SwingWorker<List<BackupSchemaGuard.TableWarning>, Void>() {
            @Override protected List<BackupSchemaGuard.TableWarning> doInBackground() {
                try (Connection conn = DBConnection.getConnection()) {
                    if (conn == null) return List.of();
                    return BackupSchemaGuard.checkMissingColumns(selected,
                            () -> AppConfig.getInstance().get("BACKUP_ENCRYPTION_PASSPHRASE", null), conn);
                } catch (Exception e) {
                    return List.of();
                }
            }
            @Override protected void done() {
                loadingOverlay.stop();
                List<BackupSchemaGuard.TableWarning> schemaWarnings;
                try { schemaWarnings = get(); } catch (Exception e) { schemaWarnings = List.of(); }
                confirmAndRestore(selected, schemaWarnings);
            }
        }.execute();
    }

    private void confirmAndRestore(File selected, List<BackupSchemaGuard.TableWarning> schemaWarnings) {
        StringBuilder message = new StringBuilder(
                "Khôi phục sẽ GHI ĐÈ toàn bộ dữ liệu hiện tại bằng nội dung trong file:\n"
                        + selected.getName() + "\n");

        if (!schemaWarnings.isEmpty()) {
            message.append("\n⚠ CẢNH BÁO: bản backup này CŨ HƠN lần thay đổi cấu trúc bảng gần nhất.\n")
                    .append("Các cột sau sẽ BỊ MẤT vĩnh viễn nếu khôi phục:\n");
            for (BackupSchemaGuard.TableWarning w : schemaWarnings) {
                message.append("  • ").append(w.table).append(": ")
                        .append(String.join(", ", w.missingColumns)).append("\n");
            }
            message.append("\nChỉ tiếp tục nếu bạn CHẮC CHẮN chấp nhận mất các cột trên.\n");
        }

        message.append("\nBạn có chắc chắn muốn tiếp tục?");

        boolean ok;
        if (schemaWarnings.isEmpty()) {
            ok = BaseDialog.confirm(
                    this,
                    "Xác nhận khôi phục",
                    message.toString(),
                    "Khôi phục",
                    AppColor.WARNING,
                    AppColor.WARNING,
                    FontAwesomeSolid.EXCLAMATION_TRIANGLE
            );
        } else {
            ok = BaseDialog.confirm(
                    this,
                    "Xác nhận khôi phục — mất cột",
                    message.toString(),
                    "Vẫn khôi phục",
                    AppColor.ERROR,
                    AppColor.ERROR_HOVER,
                    FontAwesomeSolid.EXCLAMATION_TRIANGLE
            );
        }

        if (!ok) return;

        loadingOverlay.start("Đang khôi phục dữ liệu...");
        new SwingWorker<Void, Void>() {
            String error;
            @Override protected Void doInBackground() {
                try { DisasterRecoveryBootstrap.getBackupManager().restore(selected); }
                catch (Exception e) { error = e.getMessage(); }
                return null;
            }
            @Override protected void done() {
                loadingOverlay.stop();
                if (error != null) {
                    statusLabel.setForeground(AppColor.ERROR);
                    statusLabel.setText("Khôi phục thất bại: " + error);
                    BaseDialog.error(BackupRecoveryPanel.this, "Khôi phục thất bại", error);
                } else {
                    statusLabel.setForeground(AppColor.SUCCESS);
                    statusLabel.setText("Khôi phục thành công từ " + selected.getName());
                    BaseDialog.success(BackupRecoveryPanel.this, "Hoàn tất",
                            "Đã khôi phục dữ liệu thành công.\n"
                                    + "Các màn hình đang mở sẽ tự tải lại dữ liệu mới.\n"
                                    + "Nếu vẫn thấy dữ liệu cũ, hãy chuyển tab hoặc mở lại màn hình đó.");
                }
                refreshAll();
            }
        }.execute();
    }

    private void refreshAll() { refreshBackupTable(); }

    private void refreshBackupTable() {
        backupTable.clear();
        if (!DisasterRecoveryBootstrap.isInitialized()) return;

        List<File> backups = DisasterRecoveryBootstrap.getBackupManager().getStorage().listBackups();
        SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss dd/MM/yyyy");

        for (File f : backups) {
            if (!matchesDateFilter(f)) continue;
            if (!matchesFileNameFilter(f)) continue;
            backupTable.addRow(new Object[]{
                    f.getName(), extractStrategyName(f.getName()),
                    fmt.format(new java.util.Date(f.lastModified())), formatSize(f.length())
            });
        }

        // Cập nhật lại gợi ý autocomplete (có thể có file backup mới)
        refreshSearchAutocomplete();
    }

    private static String extractStrategyName(String fileName) {
        int lastUnderscore = fileName.lastIndexOf('_');
        int firstDotAfterUnderscore = lastUnderscore >= 0 ? fileName.indexOf('.', lastUnderscore) : -1;
        return (lastUnderscore >= 0 && firstDotAfterUnderscore > lastUnderscore)
                ? fileName.substring(lastUnderscore + 1, firstDotAfterUnderscore) : "?";
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
