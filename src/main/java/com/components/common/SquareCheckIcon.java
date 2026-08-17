package com.components.common;

import com.theme.AppColor;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Line2D;

/**
 * Icon checkbox vuông (tự vẽ, không phụ thuộc Look and Feel).
 * Mặc định dùng màu ACCENT (tím/xanh dương) cho trạng thái đã chọn.
 * Dùng constructor {@code SquareCheckIcon(true)} khi ý nghĩa của việc tick là
 * "thành công / đã xác nhận / khớp" (ví dụ: đã kiểm kê) để dùng màu SUCCESS (xanh lá)
 * cho đúng ngôn ngữ màu sắc (xanh lá = tốt/hoàn tất, tím = hành động trung tính).
 * Tự động làm mờ (giảm alpha) khi component bị disable để phản ánh trạng thái khóa/không tương tác được.
 */
public class SquareCheckIcon implements Icon {

    private static final int SIZE = 18;
    private final boolean useSuccessColor;

    public SquareCheckIcon() {
        this(false);
    }

    public SquareCheckIcon(boolean useSuccessColor) {
        this.useSuccessColor = useSuccessColor;
    }

    @Override
    public int getIconWidth() {
        return SIZE;
    }

    @Override
    public int getIconHeight() {
        return SIZE;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.translate(x, y);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        boolean selected = (c instanceof AbstractButton) && ((AbstractButton) c).isSelected();
        boolean enabled = c == null || c.isEnabled();
        Color accent = useSuccessColor ? AppColor.SUCCESS : AppColor.ACCENT;

        if (selected) {
            // Đã chọn - nền màu accent (xanh lá nếu useSuccessColor) với tick trắng
            g2.setColor(enabled ? accent : withAlpha(accent, 110));
            g2.fillRoundRect(0, 0, SIZE - 1, SIZE - 1, 5, 5);
            g2.setColor(enabled ? Color.WHITE : withAlpha(Color.WHITE, 200));
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(new Line2D.Double(4, 9, 7.5, 12.5));
            g2.draw(new Line2D.Double(7.5, 12.5, 14, 5));
        } else {
            // Chưa chọn - nền trắng nhẹ với viền
            g2.setColor(enabled ? AppColor.WHITE : withAlpha(AppColor.WHITE, 140));
            g2.fillRoundRect(0, 0, SIZE - 1, SIZE - 1, 5, 5);
            g2.setColor(enabled ? AppColor.BORDER : withAlpha(AppColor.BORDER, 140));
            g2.setStroke(new BasicStroke(1.4f));
            g2.drawRoundRect(0, 0, SIZE - 1, SIZE - 1, 5, 5);
        }
        g2.dispose();
    }

    private static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }
}