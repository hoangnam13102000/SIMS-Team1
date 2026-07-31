package com.components.barcode;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamPanel;
import com.github.sarxos.webcam.WebcamResolution;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.theme.AppColor;
import com.theme.AppFont;
import com.theme.AppSpacing;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

public class BarcodeScannerDialog extends JDialog {

    private Webcam webcam;
    private WebcamPanel webcamPanel;
    private volatile boolean scanning = false;
    private Thread scanThread;

    private final JLabel statusLabel = new JLabel();
    private final JPanel videoHolder = new JPanel(new BorderLayout());

    private Consumer<String> onScanned;

    public BarcodeScannerDialog(Window owner) {
        super(owner, "Quét mã vạch sản phẩm", ModalityType.APPLICATION_MODAL);
        setSize(520, 460);
        setLocationRelativeTo(owner);
        setResizable(false);
        setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(0, AppSpacing.MD));
        root.setBackground(AppColor.WHITE);
        root.setBorder(new EmptyBorder(AppSpacing.LG, AppSpacing.LG, AppSpacing.LG, AppSpacing.LG));

        JLabel title = new JLabel("Đưa mã vạch sản phẩm vào giữa khung hình");
        title.setFont(AppFont.BODY_BOLD);
        title.setForeground(AppColor.TEXT_TITLE);
        root.add(title, BorderLayout.NORTH);

        videoHolder.setOpaque(false);
        videoHolder.setPreferredSize(new Dimension(480, 330));
        root.add(videoHolder, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.setBorder(new EmptyBorder(AppSpacing.SM, 0, 0, 0));

        statusLabel.setFont(AppFont.SMALL);
        statusLabel.setForeground(AppColor.TEXT_MUTED);
        bottom.add(statusLabel, BorderLayout.CENTER);

        JButton cancelBtn = new JButton("Hủy");
        cancelBtn.setFont(AppFont.BODY_BOLD);
        cancelBtn.setFocusPainted(false);
        cancelBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        cancelBtn.addActionListener(e -> closeDialog());
        bottom.add(cancelBtn, BorderLayout.EAST);

        root.add(bottom, BorderLayout.SOUTH);
        setContentPane(root);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeDialog();
            }
        });

        initWebcam();
    }

    /** Callback goi DUY NHAT 1 lan khi doc duoc 1 ma (dialog da tu dong luc goi). */
    public BarcodeScannerDialog onScanned(Consumer<String> listener) {
        this.onScanned = listener;
        return this;
    }

    private void initWebcam() {
        try {
            webcam = Webcam.getDefault();
            if (webcam == null) {
                showError("Không tìm thấy webcam nào trên máy này. Vui lòng kiểm tra kết nối camera.");
                return;
            }
            webcam.setViewSize(WebcamResolution.VGA.getSize());

            webcamPanel = new WebcamPanel(webcam);
            webcamPanel.setFPSDisplayed(false);
            webcamPanel.setPreferredSize(new Dimension(480, 330));
            videoHolder.add(webcamPanel, BorderLayout.CENTER);

            statusLabel.setText("Đang quét...");
            scanning = true;
            startScanLoop();
        } catch (Exception e) {
            showError("Không thể mở webcam: " + e.getMessage());
        }
    }

    private void showError(String message) {
        videoHolder.removeAll();
        JLabel errorLabel = new JLabel("<html><center>" + escapeHtml(message) + "</center></html>");
        errorLabel.setFont(AppFont.BODY);
        errorLabel.setForeground(AppColor.ERROR);
        errorLabel.setHorizontalAlignment(SwingConstants.CENTER);
        videoHolder.add(errorLabel, BorderLayout.CENTER);
        videoHolder.revalidate();
        videoHolder.repaint();
        statusLabel.setText("");
    }

    /**
     * Vong lap doc + giai ma chay tren thread nen rieng (daemon) - dung
     * webcam.getImage() de lay khung hinh moi nhat (doc lap voi vong ve cua
     * WebcamPanel), thu giai ma bang ZXing; neu chua thay ma vach nao trong
     * khung hinh (truong hop BINH THUONG, xay ra hau het cac lan) thi bo qua
     * va thu lai khung ke tiep sau 1 khoang nghi ngan.
     */
    private void startScanLoop() {
        scanThread = new Thread(() -> {
            MultiFormatReader reader = new MultiFormatReader();
            while (scanning) {
                try {
                    BufferedImage image = webcam.getImage();
                    if (image != null) {
                        String code = tryDecode(reader, image);
                        if (code != null && !code.isBlank()) {
                            scanning = false;
                            String finalCode = code;
                            SwingUtilities.invokeLater(() -> handleScanned(finalCode));
                            break;
                        }
                    }
                } catch (Exception ignored) {
                    // Loi doc/giai ma 1 khung hinh don le - bo qua, KHONG dung
                    // ca vong quet vi day chi la 1 khung hinh xau tam thoi.
                }
                try {
                    Thread.sleep(250);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "barcode-scan-loop");
        scanThread.setDaemon(true);
        scanThread.start();
    }

    private String tryDecode(MultiFormatReader reader, BufferedImage image) {
        try {
            LuminanceSource source = new BufferedImageLuminanceSource(image);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            Result result = reader.decode(bitmap);
            return result.getText();
        } catch (NotFoundException notFound) {
            // Binh thuong: khung hinh nay chua doc duoc ma vach nao.
            return null;
        } finally {
            reader.reset();
        }
    }

    private void handleScanned(String code) {
        Consumer<String> listener = onScanned;
        closeDialog();
        if (listener != null) listener.accept(code);
    }

    private void closeDialog() {
        scanning = false;
        if (scanThread != null) {
            scanThread.interrupt();
        }
        if (webcamPanel != null) {
            webcamPanel.stop();
        }
        if (webcam != null && webcam.isOpen()) {
            webcam.close();
        }
        dispose();
    }

    private String escapeHtml(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}