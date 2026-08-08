package com.view.client;

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
 * Bong bóng nổi cho trợ lý AI (Gemini) ở góc trái dưới màn hình client,
 * tương tự {@link ChatWidget} (hỗ trợ trực tuyến người thật) nhưng đặt lệch
 * sang trái để 2 bong bóng không đè lên nhau.
 */
public class AiAssistantWidget extends JPanel {

    private static final int MARGIN = 24;
    private static final int BUBBLE_SIZE = 60;
    private static final int WINDOW_WIDTH = 380;
    private static final int WINDOW_HEIGHT = 520;
    private static final int GAP = 12;
    /** Khoảng lệch trái so với bong bóng chat hỗ trợ (BUBBLE_SIZE + margin giữa 2 bong bóng). */
    private static final int LEFT_OFFSET = BUBBLE_SIZE + 16;

    private final BubbleButton bubbleButton = new BubbleButton();
    private final AiAssistantPanel aiPanel = new AiAssistantPanel(
            "Trợ lý AI",
            "Xin chào! Mình là trợ lý AI của Connect Mart, mình có thể giúp gì cho bạn?",
            true,   // showCloseButton
            true);  // clientSide = khách hàng
    private boolean windowOpen = false;

    private AiAssistantWidget() {
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

    /** Gắn widget vào 1 JFrame (dùng glass pane riêng, không dùng chung với ChatWidget). */
    public static AiAssistantWidget install(JFrame frame) {
        AiAssistantWidget widget = new AiAssistantWidget();
        // Neu frame da co glassPane khac (vd ChatWidget), bao no vao 1 layer container
        // de ca 2 widget cung hien thi duoc.
        Component existingGlass = frame.getGlassPane();
        if (existingGlass instanceof JPanel && existingGlass.isVisible() && existingGlass != widget) {
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

        bubbleButton.setBounds(w - MARGIN - LEFT_OFFSET - BUBBLE_SIZE, h - MARGIN - BUBBLE_SIZE, BUBBLE_SIZE, BUBBLE_SIZE);

        int winX = Math.max(8, w - MARGIN - LEFT_OFFSET - WINDOW_WIDTH);
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
            robotIcon = FontIcon.of(FontAwesomeSolid.ROBOT, 22);
            robotIcon.setIconColor(Color.WHITE);
            closeIcon = FontIcon.of(FontAwesomeSolid.TIMES, 20);
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