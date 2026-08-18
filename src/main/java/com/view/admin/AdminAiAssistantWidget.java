package com.view.admin;

import com.components.common.AiAssistantPanel;
import com.theme.AppColor;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Bong bóng nổi trợ lý AI cho màn hình nhân viên / admin (góc phải dưới),
 * cùng kiểu {@code AiAssistantWidget} phía client nhưng:
 * <ul>
 *   <li>{@code clientSide = false} → dùng quyền Role hiện tại (tạo danh mục, tra đơn…)</li>
 *   <li>Không lệch trái (admin thường chỉ có 1 bong bóng AI)</li>
 * </ul>
 */
public class AdminAiAssistantWidget extends JPanel {

    private static final int MARGIN = 24;
    /** Bang voi SettingsButton.SIZE de 2 nut noi (Cai dat / Tro ly AI) dong bo kich thuoc. */
    private static final int BUBBLE_SIZE = 52;
    private static final int WINDOW_WIDTH = 400;
    private static final int WINDOW_HEIGHT = 560;
    private static final int GAP = 12;

    private final BubbleButton bubbleButton = new BubbleButton();
    private final AiAssistantPanel aiPanel = new AiAssistantPanel(
            "Trợ lý AI nội bộ",
            "Xin chào! Mình là trợ lý AI nội bộ. Bạn có thể hỏi tra cứu đơn, tồn kho, "
                    + "doanh thu (nếu đủ quyền) hoặc yêu cầu tạo danh mục…",
            true,    // showCloseButton
            false);  // clientSide = false → staff/admin
    private boolean windowOpen = false;

    private AdminAiAssistantWidget() {
        setOpaque(false);
        setLayout(null);

        aiPanel.setVisible(false);
        aiPanel.onClose(this::closeWindow);

        bubbleButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                toggleWindow();
            }
        });

        add(aiPanel);
        add(bubbleButton);

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                layoutChildren();
            }
        });
    }

    /**
     * Gắn widget vào JFrame admin (glass pane).
     * Nếu đã có glass pane khác thì bọc layered để không đè mất.
     */
    public static AdminAiAssistantWidget install(JFrame frame) {
        AdminAiAssistantWidget widget = new AdminAiAssistantWidget();
        Component existingGlass = frame.getGlassPane();
        if (existingGlass instanceof JPanel
                && existingGlass.isVisible()
                && existingGlass != widget) {
            JPanel layered = new JPanel(null);
            layered.setOpaque(false);
            existingGlass.setBounds(0, 0, frame.getWidth(), frame.getHeight());
            widget.setBounds(0, 0, frame.getWidth(), frame.getHeight());
            layered.add(widget);
            layered.add(existingGlass);
            frame.setGlassPane(layered);
            layered.setVisible(true);
            layered.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    existingGlass.setBounds(0, 0, layered.getWidth(), layered.getHeight());
                    widget.setBounds(0, 0, layered.getWidth(), layered.getHeight());
                }
            });
        } else {
            frame.setGlassPane(widget);
            widget.setVisible(true);
        }
        SwingUtilities.invokeLater(widget::layoutChildren);
        return widget;
    }

    private void layoutChildren() {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        // Góc phải dưới
        bubbleButton.setBounds(
                w - MARGIN - BUBBLE_SIZE,
                h - MARGIN - BUBBLE_SIZE,
                BUBBLE_SIZE,
                BUBBLE_SIZE);

        int winX = Math.max(8, w - MARGIN - WINDOW_WIDTH);
        int winY = Math.max(8, h - MARGIN - BUBBLE_SIZE - GAP - WINDOW_HEIGHT);
        aiPanel.setBounds(winX, winY, WINDOW_WIDTH, WINDOW_HEIGHT);
    }

    private void toggleWindow() {
        windowOpen = !windowOpen;
        aiPanel.setVisible(windowOpen);
        bubbleButton.setOpenState(windowOpen);
    }

    private void closeWindow() {
        windowOpen = false;
        aiPanel.setVisible(false);
        bubbleButton.setOpenState(false);
    }

    /** Chỉ bắt sự kiện trên bubble / cửa sổ chat — vùng còn lại click xuyên xuống UI. */
    @Override
    public boolean contains(int x, int y) {
        if (bubbleButton.isVisible() && bubbleButton.getBounds().contains(x, y)) return true;
        if (aiPanel.isVisible() && aiPanel.getBounds().contains(x, y)) return true;
        return false;
    }

    private static class BubbleButton extends JPanel {
        private boolean open = false;
        private final FontIcon robotIcon;
        private final FontIcon closeIcon;

        BubbleButton() {
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            // Icon giam tu 22 -> 20 cho dong bo ty le voi SettingsButton (icon COG 20 trong bubble 52).
            robotIcon = FontIcon.of(FontAwesomeSolid.ROBOT, 20);
            robotIcon.setIconColor(Color.WHITE);
            closeIcon = FontIcon.of(FontAwesomeSolid.TIMES, 18);
            closeIcon.setIconColor(Color.WHITE);
        }

        void setOpenState(boolean open) {
            this.open = open;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(new Color(0, 0, 0, 40));
            g2.fillOval(2, 4, getWidth() - 4, getHeight() - 4);
            g2.setColor(AppColor.ACCENT);
            g2.fillOval(0, 0, getWidth() - 2, getHeight() - 2);

            FontIcon icon = open ? closeIcon : robotIcon;
            int iconX = (getWidth() - 2 - icon.getIconWidth()) / 2;
            int iconY = (getHeight() - 2 - icon.getIconHeight()) / 2;
            icon.paintIcon(this, g2, iconX, iconY);

            g2.dispose();
        }
    }
}