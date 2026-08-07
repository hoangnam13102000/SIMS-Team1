package com.view.admin;

import com.backup.BackupResult;
import com.backup.BackupSchemaGuard;
import com.components.BaseTable;
import com.components.LoadingOverlay;
import com.components.SectionHeader;
import com.disaster.DisasterRecoveryBootstrap;
import com.security.AppConfig;
import com.theme.AppColor;
import com.theme.AppFont;
import com.utils.DBConnection;
import com.components.BaseDialog;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.sql.Connection;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.List;

public class BackupRecoveryPanel extends JPanel {

    private final BaseTable backupTable;
    private final BaseTable incidentTable;
    private final JLabel statusLabel;
    private final LoadingOverlay loadingOverlay = new LoadingOverlay("Dang xu ly...");
    private final JButton restoreButton;
    private final JButton backupNowButton;

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
        incidentTable = new BaseTable(new String[]{"Thời gian", "Mức độ", "Loại", "Nguồn", "Mô tả"});
        incidentTable.setBadgeColumn(1, o -> String.valueOf(o), this::severityColor);

        statusLabel = new JLabel(" ");
        statusLabel.setFont(AppFont.SMALL);
        statusLabel.setForeground(AppColor.TEXT_MUTED);

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.add(Box.createVerticalStrut(16));
        center.add(sectionCard("Các bản sao lưu hiện có", backupTable));
        center.add(Box.createVerticalStrut(16));
        center.add(sectionCard("Nhật ký sự cố gần đây", incidentTable));

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

    /**
     * DisasterRecoveryBootstrap.init() co the that bai luc app khoi dong (vd
     * thieu BACKUP_ENCRYPTION_PASSPHRASE, mat ket noi DB...) - Main.java chi
     * log loi ra console va van cho app chay tiep (khong ep dong toan bo app
     * chi vi subsystem backup loi). Panel nay vi vay PHAI tu kiem tra truoc
     * khi goi getBackupManager(), thay vi de exception lam sap ca man hinh
     * Admin (truoc day AdminMainFrame se crash ngay luc mo).
     */
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

    private JPanel sectionCard(String title, BaseTable table) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(AppColor.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(16, 16, 16, 16)));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(AppFont.HEADING_MD);
        titleLabel.setForeground(AppColor.TEXT_TITLE);
        titleLabel.setBorder(new EmptyBorder(0, 0, 10, 0));

        card.add(titleLabel, BorderLayout.NORTH);
        card.add(table, BorderLayout.CENTER);
        return card;
    }

    private Color severityColor(Object severity) {
        switch (String.valueOf(severity)) {
            case "CRITICAL": return AppColor.ERROR;
            case "HIGH": return AppColor.WARNING;
            case "MEDIUM": return AppColor.INFO;
            default: return AppColor.TEXT_MUTED;
        }
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

        // BaseDialog.confirm — có schema lệch dùng màu ERROR (nguy hiểm), còn lại WARNING
        boolean ok;
        if (schemaWarnings.isEmpty()) {
            ok = BaseDialog.confirm(
                    this,
                    "Xác nhận khôi phục",
                    message.toString(),
                    "Khôi phục",
                    AppColor.WARNING,
                    AppColor.WARNING, // hover — nếu theme có WARNING_HOVER thì dùng cái đó
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
                    // Đồng bộ UI: DisasterRecoveryBootstrap.onRestoreSucceeded đã
                    // clear CartService + DataChangedEvent.publishFullRefresh()
                    BaseDialog.success(BackupRecoveryPanel.this, "Hoàn tất",
                            "Đã khôi phục dữ liệu thành công.\n"
                                    + "Các màn hình đang mở sẽ tự tải lại dữ liệu mới.\n"
                                    + "Nếu vẫn thấy dữ liệu cũ, hãy chuyển tab hoặc mở lại màn hình đó.");
                }
                refreshAll();
            }
        }.execute();
    }

    private void refreshAll() { refreshBackupTable(); refreshIncidentTable(); }

    private void refreshBackupTable() {
        backupTable.clear();
        List<File> backups = DisasterRecoveryBootstrap.getBackupManager().getStorage().listBackups();
        SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss dd/MM/yyyy");
        for (File f : backups) {
            backupTable.addRow(new Object[]{
                    f.getName(), extractStrategyName(f.getName()),
                    fmt.format(new java.util.Date(f.lastModified())), formatSize(f.length())
            });
        }
    }

    private void refreshIncidentTable() {
        incidentTable.clear();
        List<String> lines = DisasterRecoveryBootstrap.getIncidentSink().readRawLines(LocalDate.now());
        for (String line : lines) {
            incidentTable.addRow(new Object[]{
                    extractJsonField(line, "timestamp"), extractJsonField(line, "severity"),
                    extractJsonField(line, "type"), extractJsonField(line, "source"), extractJsonField(line, "message")
            });
        }
    }

    private static String extractStrategyName(String fileName) {
        int lastUnderscore = fileName.lastIndexOf('_');
        // Dung dau '.' DAU TIEN sau underscore, khong phai dau '.' cuoi cung:
        // file da ma hoa co duoi kep (vd "jdbc-sql-dump.sql.enc"), lastIndexOf('.')
        // se cat nham vao giua ten strategy va phan mo rong goc.
        int firstDotAfterUnderscore = lastUnderscore >= 0 ? fileName.indexOf('.', lastUnderscore) : -1;
        return (lastUnderscore >= 0 && firstDotAfterUnderscore > lastUnderscore)
                ? fileName.substring(lastUnderscore + 1, firstDotAfterUnderscore) : "?";
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private static String extractJsonField(String jsonLine, String field) {
        String key = "\"" + field + "\":\"";
        int start = jsonLine.indexOf(key);
        if (start < 0) return "";
        start += key.length();
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < jsonLine.length(); i++) {
            char c = jsonLine.charAt(i);
            if (c == '\\' && i + 1 < jsonLine.length()) { sb.append(jsonLine.charAt(i + 1)); i++; }
            else if (c == '"') break;
            else sb.append(c);
        }
        return sb.toString();
    }
}