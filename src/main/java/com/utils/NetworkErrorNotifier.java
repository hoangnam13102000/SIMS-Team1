package com.utils;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.net.BindException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dung chung cho OrderNotifyServer / OrderQueueServer / ChatServer: hien 1
 * popup CANH BAO RO RANG khi WebSocketServer noi bo khong the bind duoc cong
 * (thay vi chi im lang in stack trace ra console nhu truoc).
 * <p>
 * Nguyen nhan pho bien nhat trong thuc te: mot tien trinh admin CU chua tat
 * han (vd: dung chay dot ngot trong IDE, hoac IDE chi kill tien trinh cha
 * ma khong kill duoc JVM con neu chay qua mvn exec) van dang giu cong cu -
 * dan den tien trinh MOI mo len bind that bai, nhung UI van hien binh thuong
 * nen rat kho phat hien neu khong xem console.
 * <p>
 * Thu vien org.java_websocket co the goi onError() NHIEU LAN cho CUNG 1 lan
 * bind that bai (moi selector thread noi bo bao loi rieng) - notifiedFlag
 * dung de dam bao chi 1 popup duy nhat duoc hien cho moi server.
 */
public final class NetworkErrorNotifier {

    private NetworkErrorNotifier() {}

    /** True neu exception (hoac 1 trong cac cause cua no) la loi bind cong that bai. */
    public static boolean isBindFailure(Throwable ex) {
        while (ex != null) {
            if (ex instanceof BindException) return true;
            ex = ex.getCause();
        }
        return false;
    }

    /**
     * Hien popup canh bao bind that bai, CHI 1 LAN moi server (dua vao
     * notifiedFlag do noi goi tu quan ly - moi server nen co 1 AtomicBoolean
     * rieng). Cac lan goi tiep theo sau lan dau se bi bo qua im lang.
     */
    public static void notifyBindFailureOnce(AtomicBoolean notifiedFlag, String serverLabel, int port) {
        if (!notifiedFlag.compareAndSet(false, true)) return;

        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
                "Không thể khởi động " + serverLabel + " (cổng " + port + " đã bị chiếm dụng).\n\n"
                        + "Nguyên nhân thường gặp nhất: một phiên Admin cũ chưa tắt hẳn (ví dụ do dừng chạy\n"
                        + "đột ngột trong IDE thay vì đóng cửa sổ ứng dụng) vẫn đang giữ cổng này.\n\n"
                        + "Cách khắc phục: mở Task Manager, kiểm tra và tắt hẳn các tiến trình java.exe cũ\n"
                        + "của ứng dụng này, rồi mở lại.",
                "Không khởi động được server nền", JOptionPane.WARNING_MESSAGE));
    }
}