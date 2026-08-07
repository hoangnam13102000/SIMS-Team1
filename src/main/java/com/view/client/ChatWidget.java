package com.view.client;

import com.theme.AppColor;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** Bong bong chat noi (floating) o goc phai duoi man hinh cua ClientMainFrame. */
public class ChatWidget extends JPanel {

    private static final int MARGIN = 24;
    private static final int BUBBLE_SIZE = 60;
    private static final int WINDOW_WIDTH = 380;
    private static final int WINDOW_HEIGHT = 520;
    private static final int GAP = 12;

    private final BubbleButton bubbleButton = new BubbleButton();
    private final ChatPanel chatPanel = new ChatPanel();
    private int unreadCount = 0;
    private boolean windowOpen = false;

    private ChatWidget() {
        setOpaque(false);
        setLayout(null);

        chatPanel.setVisible(false);
        chatPanel.onClose(this::closeWindow);
        chatPanel.onIncomingMessage(this::onIncomingMessage);

        bubbleButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                toggleWindow();
            }
        });

        add(chatPanel);
        add(bubbleButton);

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                layoutChildren();
            }
        });
    }

    public static void install(JFrame frame) {
        ChatWidget widget = new ChatWidget();
        frame.setGlassPane(widget);
        widget.setVisible(true);
        SwingUtilities.invokeLater(widget::layoutChildren);
    }

    private void layoutChildren() {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        bubbleButton.setBounds(w - MARGIN - BUBBLE_SIZE, h - MARGIN - BUBBLE_SIZE, BUBBLE_SIZE, BUBBLE_SIZE);

        int winX = Math.max(8, w - MARGIN - WINDOW_WIDTH);
        int winY = Math.max(8, h - MARGIN - BUBBLE_SIZE - GAP - WINDOW_HEIGHT);
        chatPanel.setBounds(winX, winY, WINDOW_WIDTH, WINDOW_HEIGHT);
    }

    private void toggleWindow() {
        windowOpen = !windowOpen;
        chatPanel.setVisible(windowOpen);
        bubbleButton.setOpenState(windowOpen);
        if (windowOpen) {
            unreadCount = 0;
            bubbleButton.setBadge(0);
        }
    }

    private void closeWindow() {
        windowOpen = false;
        chatPanel.setVisible(false);
        bubbleButton.setOpenState(false);
    }

    private void onIncomingMessage() {
        if (!windowOpen) {
            unreadCount++;
            bubbleButton.setBadge(unreadCount);
        }
    }

    @Override
    public boolean contains(int x, int y) {
        if (bubbleButton.isVisible() && bubbleButton.getBounds().contains(x, y)) return true;
        if (chatPanel.isVisible() && chatPanel.getBounds().contains(x, y)) return true;
        return false;
    }

    private static class BubbleButton extends JPanel {
        private int badgeCount = 0;
        private boolean open = false;
        private final FontIcon chatIcon;
        private final FontIcon closeIcon;

        BubbleButton() {
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            chatIcon = FontIcon.of(FontAwesomeSolid.COMMENT_DOTS, 22);
            chatIcon.setIconColor(Color.WHITE);
            closeIcon = FontIcon.of(FontAwesomeSolid.TIMES, 20);
            closeIcon.setIconColor(Color.WHITE);
        }

        void setBadge(int count) {
            this.badgeCount = count;
            repaint();
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
            g2.setColor(AppColor.ACCENT_HOVER);
            g2.fillOval(0, 0, getWidth() - 2, getHeight() - 2);

            FontIcon icon = open ? closeIcon : chatIcon;
            int iconX = (getWidth() - 2 - icon.getIconWidth()) / 2;
            int iconY = (getHeight() - 2 - icon.getIconHeight()) / 2;
            icon.paintIcon(this, g2, iconX, iconY);

            if (!open && badgeCount > 0) {
                String text = badgeCount > 9 ? "9+" : String.valueOf(badgeCount);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
                FontMetrics fm = g2.getFontMetrics();
                int textWidth = fm.stringWidth(text);
                int badgeSize = Math.max(18, textWidth + 10);
                int badgeX = getWidth() - badgeSize - 2;
                int badgeY = 0;

                g2.setColor(AppColor.RED_ALT);
                g2.fillOval(badgeX, badgeY, badgeSize, badgeSize);
                g2.setColor(Color.WHITE);
                g2.drawString(text, badgeX + (badgeSize - textWidth) / 2, badgeY + badgeSize - 6);
            }
            g2.dispose();
        }
    }
}