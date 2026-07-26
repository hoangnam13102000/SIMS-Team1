package com.components.crud;

import com.components.AppAlert;
import com.components.LoadingOverlay;
import com.importer.ImportRowResult;
import com.importer.RowImportHandler;
import com.importer.SpreadsheetImportReader;
import com.security.FileSecurityScanner;
import com.security.ScanResult;
import com.theme.AppColor;
import com.theme.AppFont;
import com.utils.FileUtil;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Dialog "Nhập dữ liệu" dùng chung cho mọi panel CRUD có {@code supportsImport() == true}.
 * <p>
 * Quy trình: chọn file .xlsx/.docx -> quét virus (heuristic + ClamAV nếu có, xem
 * {@link FileSecurityScanner}) -> đọc thành các dòng qua {@link SpreadsheetImportReader}
 * (bỏ dòng đầu = header) -> gọi {@link RowImportHandler#importRow} cho từng dòng -> hiển thị
 * tổng kết kết quả (số dòng thành công / thất bại + lý do).
 */
public final class ImportFileDialog extends JDialog {

    private final String[] importColumns;
    private final String instructions;
    private final RowImportHandler handler;

    private final JLabel fileLabel;
    private final JLabel scanStatusLabel;
    private final JTextArea resultArea;
    private final JScrollPane resultScrollPane;
    private final JButton chooseButton;
    private final JButton startButton;
    private final JButton closeButton;
    private final LoadingOverlay loadingOverlay;

    private File selectedFile;
    private BiConsumer<Integer, Integer> onFinished;

    public ImportFileDialog(Window owner, String pageTitle, String[] importColumns,
                             String instructions, RowImportHandler handler) {
        super(owner, "Nhập dữ liệu - " + pageTitle, ModalityType.APPLICATION_MODAL);
        this.importColumns = importColumns;
        this.handler = handler;
        this.instructions = instructions;

        setLayout(new BorderLayout());
        getContentPane().setBackground(AppColor.WHITE);
        setResizable(false);

        JPanel body = new JPanel();
        body.setBackground(AppColor.WHITE);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(24, 24, 8, 24));

        JPanel headerRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        headerRow.setOpaque(false);
        headerRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        FontIcon icon = FontIcon.of(FontAwesomeSolid.FILE_UPLOAD, 26);
        icon.setIconColor(AppColor.ACCENT);
        headerRow.add(new JLabel(icon));
        JLabel titleLabel = new JLabel("Nhập dữ liệu từ file");
        titleLabel.setFont(AppFont.DIALOG_TITLE);
        titleLabel.setForeground(AppColor.TEXT_PRIMARY);
        headerRow.add(titleLabel);
        body.add(headerRow);
        body.add(Box.createVerticalStrut(14));

        body.add(wrapLabel("Chỉ chấp nhận file .xlsx hoặc .docx, dòng đầu tiên là tiêu đề cột."));
        body.add(Box.createVerticalStrut(6));
        body.add(wrapLabel("Cột cần có: " + String.join(", ", importColumns)));
        if (instructions != null && !instructions.isBlank()) {
            body.add(Box.createVerticalStrut(6));
            body.add(wrapLabel(instructions));
        }
        body.add(Box.createVerticalStrut(14));

        JPanel filePanel = new JPanel(new BorderLayout(10, 0));
        filePanel.setOpaque(false);
        filePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        filePanel.setMaximumSize(new Dimension(420, 36));
        chooseButton = createButton("Chọn file...", AppColor.CANCEL_BG, AppColor.CANCEL_HOVER, AppColor.TEXT_PRIMARY);
        chooseButton.addActionListener(e -> chooseFile());
        fileLabel = new JLabel("Chưa chọn file nào");
        fileLabel.setFont(AppFont.BODY);
        fileLabel.setForeground(AppColor.TEXT_MUTED);
        filePanel.add(chooseButton, BorderLayout.WEST);
        filePanel.add(fileLabel, BorderLayout.CENTER);
        body.add(filePanel);
        body.add(Box.createVerticalStrut(12));

        // Dong trang thai quet virus - 1 JLabel duy nhat co ca icon (Ikonli, thay
        // emoji vi emoji render ra o vuong "tofu" tren nhieu may Windows) lan text,
        // dung tinh nang icon+text co san cua JLabel thay vi ghep 2 component rieng
        // qua FlowLayout (de bi cat text khi height bi gioi han).
        scanStatusLabel = new JLabel();
        scanStatusLabel.setFont(AppFont.SMALL);
        scanStatusLabel.setIconTextGap(8);
        scanStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        scanStatusLabel.setBorder(new EmptyBorder(4, 0, 8, 0));
        scanStatusLabel.setVisible(false);
        body.add(scanStatusLabel);

        resultArea = new JTextArea(8, 40);
        resultArea.setEditable(false);
        resultArea.setFont(AppFont.SMALL);
        resultArea.setForeground(AppColor.TEXT_SECONDARY);
        resultArea.setBackground(AppColor.BG_LIGHT);
        resultArea.setLineWrap(true);
        resultArea.setWrapStyleWord(true);
        resultArea.setBorder(new EmptyBorder(8, 10, 8, 10));
        JScrollPane scrollPane = new JScrollPane(resultArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(AppColor.BORDER, 1, true));
        scrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        scrollPane.setMaximumSize(new Dimension(420, 220));
        scrollPane.setVisible(false);
        body.add(scrollPane);
        resultScrollPane = scrollPane;

        JPanel centerWrapper = new JPanel(new BorderLayout());
        centerWrapper.setBackground(AppColor.WHITE);
        centerWrapper.add(body, BorderLayout.CENTER);

        loadingOverlay = new LoadingOverlay("Đang xử lý...");
        add(LoadingOverlay.attach(centerWrapper, loadingOverlay), BorderLayout.CENTER);

        closeButton = createButton("Đóng", AppColor.CANCEL_BG, AppColor.CANCEL_HOVER, AppColor.TEXT_PRIMARY);
        closeButton.addActionListener(e -> dispose());

        startButton = createButton("Bắt đầu nhập", AppColor.ACCENT, AppColor.ACCENT_HOVER, Color.WHITE);
        startButton.setEnabled(false);
        startButton.addActionListener(e -> startImport());

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        footer.setBackground(AppColor.WHITE);
        footer.setBorder(new EmptyBorder(8, 16, 16, 16));
        footer.add(closeButton);
        footer.add(startButton);
        add(footer, BorderLayout.SOUTH);

        pack();
        if (getWidth() < 480) setSize(480, getHeight());
        setLocationRelativeTo(owner);
    }

    public void setOnFinished(BiConsumer<Integer, Integer> onFinished) {
        this.onFinished = onFinished;
    }

    private void chooseFile() {
        File file = FileUtil.chooseFile(this, "Excel / Word (*.xlsx, *.docx)", "xlsx", "docx");
        if (file == null) return;
        if (!SpreadsheetImportReader.isSupported(file)) {
            AppAlert.error(this, "File không hợp lệ", "Chỉ chấp nhận file .xlsx hoặc .docx.");
            return;
        }
        selectedFile = file;
        fileLabel.setText(file.getName() + " (" + FileUtil.formatSize(file.length()) + ")");
        fileLabel.setForeground(AppColor.TEXT_PRIMARY);
        startButton.setEnabled(true);
        resultScrollPane.setVisible(false);
        scanStatusLabel.setVisible(false);
        resultArea.setText("");
        pack();
        if (getWidth() < 480) setSize(480, getHeight());
    }

    private void startImport() {
        if (selectedFile == null) return;
        chooseButton.setEnabled(false);
        startButton.setEnabled(false);
        closeButton.setEnabled(false);
        loadingOverlay.start("Đang quét virus...");

        final File file = selectedFile;
        SwingWorker<ImportOutcome, Void> worker = new SwingWorker<ImportOutcome, Void>() {
            @Override
            protected ImportOutcome doInBackground() {
                ScanResult scan = FileSecurityScanner.getInstance().scan(file);
                if (scan.isBlocked()) {
                    return ImportOutcome.blocked(scan.getMessage());
                }

                List<String[]> rows;
                try {
                    SwingUtilities.invokeLater(() -> loadingOverlay.setMessage("Đang đọc file..."));
                    rows = SpreadsheetImportReader.read(file);
                } catch (Exception e) {
                    return ImportOutcome.blocked("Không đọc được file: " + e.getMessage());
                }

                if (rows.size() <= 1) {
                    return ImportOutcome.blocked("File không có dòng dữ liệu nào (chỉ có dòng tiêu đề hoặc rỗng).");
                }

                // Doi chieu dong tieu de thuc te trong file voi danh sach cot ky vong (importColumns) de biet
                // moi cot ky vong nam o vi tri nao trong file - tranh truong hop file co them cot (vd "ID" khi
                // nguoi dung xuat ra roi nhap lai), thieu cot tuy chon, hoac cac cot bi dao thu tu.
                int[] columnMapping = buildColumnMapping(rows.get(0));

                SwingUtilities.invokeLater(() -> loadingOverlay.setMessage("Đang nhập dữ liệu..."));
                int success = 0;
                List<String> errors = new ArrayList<>();
                for (int i = 1; i < rows.size(); i++) {
                    int rowNumber = i + 1; // tinh ca dong header, hien thi cho nguoi dung
                    String[] rawCells = rows.get(i);
                    if (isBlankRow(rawCells)) continue;
                    String[] cells = columnMapping != null ? applyColumnMapping(columnMapping, rawCells) : rawCells;
                    ImportRowResult result = handler.importRow(cells, rowNumber);
                    if (result.isSuccess()) {
                        success++;
                    } else {
                        errors.add("Dòng " + rowNumber + ": " + result.getErrorMessage());
                    }
                }
                return ImportOutcome.done(success, errors, scan.getMessage(), scan.isWarning());
            }

            @Override
            protected void done() {
                loadingOverlay.stop();
                chooseButton.setEnabled(true);
                closeButton.setEnabled(true);
                startButton.setEnabled(true);

                ImportOutcome outcome;
                try {
                    outcome = get();
                } catch (Exception e) {
                    outcome = ImportOutcome.blocked("Lỗi không xác định: " + e.getMessage());
                }

                if (outcome.blockedReason != null) {
                    AppAlert.error(ImportFileDialog.this, "Không thể nhập file", outcome.blockedReason);
                    startButton.setEnabled(selectedFile != null);
                    return;
                }

                if (outcome.scanIsWarning && outcome.scanMessage != null) {
                    AppAlert.warning(ImportFileDialog.this, "Chưa quét virus chuyên sâu", outcome.scanMessage);
                }

                showResult(outcome.successCount, outcome.errors, outcome.scanMessage, outcome.scanIsWarning);
                startButton.setEnabled(false);
                chooseButton.setEnabled(true);

                if (onFinished != null) {
                    onFinished.accept(outcome.successCount, outcome.errors.size());
                }
            }
        };
        worker.execute();
    }

    private void showResult(int successCount, List<String> errors, String scanMessage, boolean scanIsWarning) {
        // Icon Ikonli thay cho emoji (emoji render ra o vuong "tofu" tren nhieu may
        // Windows do thieu font emoji trong Swing).
        if (scanMessage != null && !scanMessage.isBlank()) {
            FontIcon icon = FontIcon.of(
                scanIsWarning ? FontAwesomeSolid.EXCLAMATION_TRIANGLE : FontAwesomeSolid.SHIELD_ALT, 16);
            Color color = scanIsWarning ? AppColor.WARNING : AppColor.SUCCESS;
            icon.setIconColor(color);
            scanStatusLabel.setIcon(icon);
            scanStatusLabel.setForeground(color);
            scanStatusLabel.setText("<html><div style='width:360px'>" + scanMessage + "</div></html>");
            scanStatusLabel.setVisible(true);
        } else {
            scanStatusLabel.setVisible(false);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Đã nhập thành công ").append(successCount).append(" dòng.");
        if (!errors.isEmpty()) {
            sb.append("\nCó ").append(errors.size()).append(" dòng lỗi:\n");
            int shown = Math.min(errors.size(), 20);
            for (int i = 0; i < shown; i++) {
                sb.append("• ").append(errors.get(i)).append("\n");
            }
            if (errors.size() > shown) {
                sb.append("... và ").append(errors.size() - shown).append(" lỗi khác.");
            }
        }
        resultArea.setText(sb.toString());
        resultArea.setCaretPosition(0);
        resultScrollPane.setVisible(true);
        pack();
        if (getWidth() < 480) setSize(480, getHeight());

        if (errors.isEmpty()) {
            AppAlert.success(this, "Thành công", "Đã nhập " + successCount + " dòng dữ liệu.");
        } else if (successCount > 0) {
            AppAlert.warning(this, "Hoàn tất một phần",
                    successCount + " dòng thành công, " + errors.size() + " dòng lỗi (xem chi tiết trong dialog).");
        } else {
            AppAlert.error(this, "Nhập thất bại", "Không có dòng nào được nhập thành công.");
        }
    }

    /**
     * Doi chieu dong tieu de {@code fileHeader} (doc tu file nguoi dung chon) voi {@link #importColumns}
     * (danh sach cot ma {@link #handler} thuc su can, theo dung thu tu). Voi moi cot ky vong, tim vi tri
     * cot tuong ung trong file (so sanh ten cot, bo qua phan chu thich trong ngoac vd "Giá (VND)" ~ "Giá").
     * <p>
     * Neu khong doi chieu duoc cot nao (vd file cu khong co dong tieu de dung nghia) thi tra ve {@code null}
     * de goi noi tiep tuc dung du lieu tho theo vi tri cu (giu tuong thich nguoc).
     */
    private int[] buildColumnMapping(String[] fileHeader) {
        if (importColumns == null || importColumns.length == 0 || fileHeader == null) return null;

        String[] normalizedFileHeader = new String[fileHeader.length];
        for (int i = 0; i < fileHeader.length; i++) normalizedFileHeader[i] = normalizeColumnName(fileHeader[i]);

        int[] mapping = new int[importColumns.length];
        int matchedCount = 0;
        for (int i = 0; i < importColumns.length; i++) {
            String target = normalizeColumnName(importColumns[i]);
            int foundAt = -1;
            for (int h = 0; h < normalizedFileHeader.length; h++) {
                if (normalizedFileHeader[h].equalsIgnoreCase(target)) {
                    foundAt = h;
                    break;
                }
            }
            mapping[i] = foundAt;
            if (foundAt >= 0) matchedCount++;
        }

        // Khong khop duoc cot nao -> co the file khong co tieu de dung dinh dang mong doi, fallback ve vi tri cu.
        return matchedCount == 0 ? null : mapping;
    }

    /** Sap xep lai 1 dong du lieu tho theo dung thu tu {@link #importColumns}, dua tren {@code mapping}. */
    private static String[] applyColumnMapping(int[] mapping, String[] rawCells) {
        String[] mapped = new String[mapping.length];
        for (int i = 0; i < mapping.length; i++) {
            int src = mapping[i];
            mapped[i] = (src >= 0 && src < rawCells.length && rawCells[src] != null) ? rawCells[src] : "";
        }
        return mapped;
    }

    /** "Giá (VND)" -> "giá", "Ảnh (URL, có thể để trống)" -> "ảnh" - bo phan chu thich trong ngoac de so sanh. */
    private static String normalizeColumnName(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        int paren = s.indexOf('(');
        if (paren >= 0) s = s.substring(0, paren).trim();
        return s.toLowerCase(java.util.Locale.forLanguageTag("vi"));
    }

    private static boolean isBlankRow(String[] cells) {
        if (cells == null) return true;
        for (String cell : cells) {
            if (cell != null && !cell.trim().isEmpty()) return false;
        }
        return true;
    }

    private static JLabel wrapLabel(String text) {
        JLabel label = new JLabel("<html><div style='width:400px'>" + text + "</div></html>");
        label.setFont(AppFont.BODY);
        label.setForeground(AppColor.TEXT_SECONDARY);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private static JButton createButton(String text, Color bg, Color hover, Color fg) {
        JButton button = new JButton(text);
        button.setFont(AppFont.BODY_BOLD);
        button.setForeground(fg);
        button.setBackground(bg);
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(9, 18, 9, 18));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent e) { button.setBackground(hover); }
            @Override public void mouseExited(java.awt.event.MouseEvent e) { button.setBackground(bg); }
        });
        return button;
    }

    /** Ket qua noi bo cua 1 lan chay import trong background thread. */
    private static final class ImportOutcome {
        final String blockedReason;
        final String scanMessage;
        final boolean scanIsWarning;
        final int successCount;
        final List<String> errors;

        private ImportOutcome(String blockedReason, String scanMessage, boolean scanIsWarning,
                               int successCount, List<String> errors) {
            this.blockedReason = blockedReason;
            this.scanMessage = scanMessage;
            this.scanIsWarning = scanIsWarning;
            this.successCount = successCount;
            this.errors = errors;
        }

        static ImportOutcome blocked(String reason) {
            return new ImportOutcome(reason, null, false, 0, new ArrayList<>());
        }

        static ImportOutcome done(int successCount, List<String> errors, String scanMessage, boolean scanIsWarning) {
            return new ImportOutcome(null, scanMessage, scanIsWarning, successCount, errors);
        }
    }
}