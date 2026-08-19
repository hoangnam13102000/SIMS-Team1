package com.components.common;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;

/**
 * {@link FlowLayout} chuẩn của Swing luôn tính preferred size như thể mọi thành
 * phần con nằm trên 1 hàng duy nhất — nó chỉ thực sự "xuống dòng" lúc vẽ, không
 * phản ánh vào preferred size. Khi đặt trong 1 panel dùng {@code BoxLayout.Y_AXIS}
 * (ví dụ hàng chip câu hỏi gợi ý bên trong khung chat AI), điều này khiến panel
 * cha không cấp đủ chiều cao, làm nội dung xuống dòng bị cắt xén ở mép phải thay
 * vì xuống dòng đúng vị trí.
 * <p>
 * WrapLayout tính lại preferred/minimum size dựa trên số dòng thực sự cần dùng
 * ở bề rộng khả dụng hiện tại, giúp component cha luôn cấp đủ chỗ.
 */
public class WrapLayout extends FlowLayout {

    public WrapLayout() {
        super();
    }

    public WrapLayout(int align) {
        super(align);
    }

    public WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return layoutSize(target, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension minimum = layoutSize(target, false);
        minimum.width -= (getHgap() + 1);
        return minimum;
    }

    private Dimension layoutSize(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            // Bề rộng của "target" thường chưa được set ở lần layout đầu tiên (bằng 0),
            // nên đi ngược lên container cha gần nhất đã có bề rộng thật (vd viewport
            // của JScrollPane, hoặc glass pane của JFrame) để lấy bề rộng khả dụng.
            Container container = target;
            while (container.getSize().width == 0 && container.getParent() != null) {
                container = container.getParent();
            }
            int targetWidth = container.getWidth();
            if (targetWidth <= 0) {
                targetWidth = Integer.MAX_VALUE;
            }

            int hgap = getHgap();
            int vgap = getVgap();
            Insets insets = target.getInsets();
            int horizontalInsetsAndGap = insets.left + insets.right + (hgap * 2);
            int maxWidth = targetWidth - horizontalInsetsAndGap;
            if (maxWidth <= 0) {
                maxWidth = Integer.MAX_VALUE;
            }

            Dimension dim = new Dimension(0, 0);
            int rowWidth = 0;
            int rowHeight = 0;

            int nmembers = target.getComponentCount();
            for (int i = 0; i < nmembers; i++) {
                Component m = target.getComponent(i);
                if (!m.isVisible()) continue;

                Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();

                if (rowWidth + d.width > maxWidth && rowWidth > 0) {
                    dim.width = Math.max(dim.width, rowWidth);
                    dim.height += rowHeight + vgap;
                    rowWidth = 0;
                    rowHeight = 0;
                }

                if (rowWidth != 0) {
                    rowWidth += hgap;
                }
                rowWidth += d.width;
                rowHeight = Math.max(rowHeight, d.height);
            }
            dim.width = Math.max(dim.width, rowWidth);
            dim.height += rowHeight;

            dim.width += horizontalInsetsAndGap;
            dim.height += insets.top + insets.bottom + vgap * 2;
            return dim;
        }
    }
}