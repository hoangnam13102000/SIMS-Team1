package com.components;

import com.theme.AppColor;

import javax.swing.*;
import java.awt.*;

/**
 * Overlay "đang xử lý" hiện trong lúc chạy 1 tác vụ đồng bộ nặng trên EDT
 * (vd rebuildContent() khi đổi Theme/Accent/Ngôn ngữ) - để người dùng thấy
 * phản hồi ngay thay vì cảm giác app đứng/lag trong lúc chờ (rebuildContent()
 * dựng lại toàn bộ sidebar/header/panel, có nơi còn query DB trong lúc dựng
 * (vd dashboard Tổng quan) nên có thể mất vài trăm ms tới cả giây).
 * <p>
 * QUAN TRỌNG VỀ CÁCH VẼ: KHÔNG dùng paintImmediately() để ép vẽ overlay
 * "ngay tại chỗ" rồi chạy heavyTask() liền sau trong CÙNG 1 sự kiện EDT.
 * Lý do: paintImmediately() chỉ đảm bảo Swing vẽ vào buffer nội bộ, còn việc
 * buffer đó có thực sự được blit (flush) ra màn hình ngay hay không còn phụ
 * thuộc vào toolkit/hệ điều hành - đặc biệt hay bị trễ/không hiện khi ngay
 * trước đó 1 JPopupMenu vừa đóng (popup.setVisible(false)), vì lúc đó hệ
 * thống cửa sổ (window manager) cũng đang tự vẽ lại vùng vừa lộ ra do popup
 * biến mất, tranh chấp với lệnh vẽ ép buộc của mình => kết quả thực tế quan
 * sát được là: nút Cài đặt đổi màu trước (do popup đóng kéo theo 1 lần vẽ
 * lại tự nhiên), sau đó đứng hình 1 lát (đây chính là lúc buildContent()
 * đang chạy nhưng overlay chưa kịp lên hình), rồi mới thấy overlay.
 * <p>
 * Cách làm ĐÚNG với mô hình 1 luồng của Swing: hiện overlay xong thì DỪNG
 * LẠI (return khỏi handler hiện tại) để nhường EDT 1 nhịp - lúc đó Swing sẽ
 * tự xử lý hàng đợi vẽ (kể cả phần do popup đóng để lại) một cách bình
 * thường, hiện overlay lên MÀN HÌNH THẬT SỰ. Rồi mới lên lịch chạy heavyTask
 * (buildContent...) ở 1 sự kiện EDT KẾ TIẾP bằng SwingUtilities.invokeLater().
 * Nhờ vậy overlay chắc chắn đã lên hình trước khi tác vụ nặng bắt đầu chặn
 * luồng UI, đúng thứ tự người dùng mong đợi: bấm -> overlay hiện -> (chờ
 * ngắn trong lúc build) -> overlay biến mất, hiện giao diện mới.
 * <p>
 * Tương thích với AdminAiAssistantWidget/AiAssistantWidget/ChatWidget (đều
 * đang chiếm glassPane của frame): nếu frame đã có glass pane khác đang
 * hiện (bong bóng AI, chat...), overlay sẽ ĐÈ LÊN TRÊN tạm thời (theo đúng
 * cách "layered wrap" mà AdminAiAssistantWidget đã dùng) rồi trả lại glass
 * pane cũ y nguyên sau khi tác vụ xong - không làm mất/hỏng các widget nổi.
 */
public final class RebuildOverlay {

    private RebuildOverlay() {
    }

    /**
     * Hien overlay ngay (nhuong 1 nhip EDT de Swing thuc su ve no ra man
     * hinh), roi MOI chay heavyTask o 1 su kien EDT ke tiep, cuoi cung an
     * overlay va tra lai glassPane cu.
     */
    public static void runWithOverlay(JFrame frame, Runnable heavyTask) {
        Component previousGlass = frame.getGlassPane();
        boolean previousGlassWasShowing = previousGlass != null && previousGlass.isVisible();

        JComponent overlay = buildOverlay();
        overlay.setBounds(0, 0, Math.max(frame.getWidth(), 1), Math.max(frame.getHeight(), 1));

        if (previousGlassWasShowing) {
            // Da co widget noi khac (AI bubble, chat...) dang chiem glassPane:
            // bao boc ca 2 trong 1 panel, overlay THEM VAO TRUOC de duoc ve tren cung.
            JPanel layered = new JPanel(null);
            layered.setOpaque(false);
            // Phai set bounds cho CHINH layered (khong chi cho con no) vi no
            // dung null layout - JRootPane chi tu canh lai kich thuoc glassPane
            // moi o lan validate() bat dong bo ke tiep, khong kip cho lan ve dau tien.
            layered.setBounds(0, 0, frame.getWidth(), frame.getHeight());
            previousGlass.setBounds(0, 0, frame.getWidth(), frame.getHeight());
            layered.add(overlay);
            layered.add(previousGlass);
            frame.setGlassPane(layered);
            layered.setVisible(true);
        } else {
            frame.setGlassPane(overlay);
            overlay.setVisible(true);
        }

        // KHONG goi heavyTask() ngay o day (xem giai thich o javadoc lop).
        // Nhuong EDT 1 nhip bang invokeLater de Swing thuc su ve overlay ra
        // man hinh TRUOC KHI tac vu nang chan luong UI.
        SwingUtilities.invokeLater(() -> {
            try {
                heavyTask.run();
            } finally {
                frame.setGlassPane(previousGlass);
                if (previousGlass != null) {
                    previousGlass.setVisible(previousGlassWasShowing);
                }
            }
        });
    }

    private static JComponent buildOverlay() {
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                g2.setColor(AppColor.OVERLAY_BACKDROP);
                g2.fillRect(0, 0, getWidth(), getHeight());

                int size = 44;
                int x = (getWidth() - size) / 2;
                int y = (getHeight() - size) / 2;

                g2.setStroke(new BasicStroke(4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(AppColor.BORDER);
                g2.drawOval(x, y, size, size);

                g2.setColor(AppColor.ACCENT);
                g2.drawArc(x, y, size, size, 90, 110);

                g2.dispose();
            }

            @Override
            public boolean isOpaque() {
                return false;
            }
        };
        panel.setOpaque(false);
        return panel;
    }
}