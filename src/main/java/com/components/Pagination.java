package com.components;

import com.theme.AppColor;
import com.theme.AppConstant;
import com.theme.AppFont;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public class Pagination extends JPanel {
    
    private JButton btnPrevious;
    private JButton btnNext;
    private List<JButton> pageButtons;
    private JLabel lblPageInfo;
    private JComboBox<Integer> cbPageSize;
    private JPanel pagePanel;
    
    private int currentPage = 1;
    private int totalPages = 10;
    private int pageSize = 10;
    private int totalItems = 0;
    private int visiblePages = 5; // Số trang hiển thị cùng lúc
    
    // Dong bo mau voi theme dung chung toan app (AppColor)
    private Color primaryColor = AppColor.ACCENT;
    private Color primaryHover = AppColor.ACCENT_HOVER;
    private Color borderColor = AppColor.BORDER;
    private Color textColor = AppColor.TEXT_PRIMARY;
    private Color bgHover = AppColor.BG_LIGHTER;
    private Color activeTextColor = Color.WHITE;
    
    // Button radius constant
    private static final int BUTTON_RADIUS = 20;
    
    public Pagination() {
        pageButtons = new ArrayList<>();
        initComponents();
        setupLayout();
        addEventListeners();
        refreshUI();
    }
    
    private void initComponents() {
        setLayout(new FlowLayout(FlowLayout.CENTER, 5, 6));
        setBackground(AppColor.WHITE);
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(borderColor, 1),
            new EmptyBorder(8, 15, 8, 15)
        ));
        
        btnPrevious = createNavButton(FontAwesomeSolid.ANGLE_LEFT, "Trước");
        btnNext = createNavButton(FontAwesomeSolid.ANGLE_RIGHT, "Sau");
        
        lblPageInfo = new JLabel("Trang 1 / 10");
        lblPageInfo.setFont(AppFont.BODY);
        lblPageInfo.setForeground(AppColor.TEXT_SECONDARY);
        
        Integer[] sizes = {10, 20, 50, 100};
        cbPageSize = new JComboBox<>(sizes);
        cbPageSize.setSelectedItem(10);
        cbPageSize.setFont(AppFont.BODY);
        cbPageSize.setPreferredSize(new Dimension(65, 30));
        cbPageSize.setBackground(AppColor.WHITE);
        cbPageSize.setBorder(BorderFactory.createLineBorder(borderColor, 1));
        
        UIManager.put("ComboBox.background", AppColor.WHITE);
        UIManager.put("ComboBox.selectionBackground", primaryColor);
        UIManager.put("ComboBox.selectionForeground", Color.WHITE);
    }
    
    private JButton createNavButton(FontAwesomeSolid iconType, String tooltip) {
        FontIcon icon = FontIcon.of(iconType, 14);
        JButton button = new JButton(icon) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // Draw rounded background
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), BUTTON_RADIUS, BUTTON_RADIUS);
                
                // Draw icon
                if (getIcon() != null) {
                    Icon icon = getIcon();
                    int iconX = (getWidth() - icon.getIconWidth()) / 2;
                    int iconY = (getHeight() - icon.getIconHeight()) / 2;
                    icon.paintIcon(this, g2, iconX, iconY);
                }
                
                g2.dispose();
            }
        };
        
        button.setFont(AppFont.BODY);
        button.setForeground(textColor);
        button.setBackground(AppColor.WHITE);
        button.setBorder(new EmptyBorder(6, 10, 6, 10));
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setToolTipText(tooltip);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (button.isEnabled()) {
                    button.setBackground(bgHover);
                    button.repaint();
                }
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                if (button.isEnabled()) {
                    button.setBackground(AppColor.WHITE);
                    button.repaint();
                }
            }
        });
        
        return button;
    }
    
    private JButton createPageButton(int page) {
        JButton button = new JButton(String.valueOf(page)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // Draw rounded background
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), BUTTON_RADIUS, BUTTON_RADIUS);
                
                // Draw text
                if (getText() != null && !getText().isEmpty()) {
                    g2.setColor(getForeground());
                    FontMetrics fm = g2.getFontMetrics();
                    int textX = (getWidth() - fm.stringWidth(getText())) / 2;
                    int textY = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                    g2.drawString(getText(), textX, textY);
                }
                
                g2.dispose();
            }
        };
        
        button.setFont(AppFont.BODY);
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorder(new EmptyBorder(6, 14, 6, 14));
        
        if (page == currentPage) {
            // Active page
            button.setBackground(primaryColor);
            button.setForeground(activeTextColor);
        } else {
            button.setBackground(AppColor.WHITE);
            button.setForeground(textColor);
        }
        
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (page != currentPage && button.isEnabled()) {
                    button.setBackground(bgHover);
                    button.repaint();
                }
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                if (page != currentPage && button.isEnabled()) {
                    button.setBackground(AppColor.WHITE);
                    button.repaint();
                }
            }
        });
        
        button.addActionListener(e -> {
            if (page != currentPage) {
                setCurrentPage(page);
                notifyPageChange();
            }
        });
        
        return button;
    }
    
    private void setupLayout() {
        JPanel navPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        navPanel.setOpaque(false);
        navPanel.add(btnPrevious);
        
        pagePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 0));
        pagePanel.setOpaque(false);
        pagePanel.setName("pagePanel");
        
        updatePageButtons(pagePanel);
        
        navPanel.add(pagePanel);
        navPanel.add(btnNext);
        
        JSeparator sep1 = new JSeparator(SwingConstants.VERTICAL);
        sep1.setPreferredSize(new Dimension(1, 28));
        sep1.setForeground(borderColor);
        
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        infoPanel.setOpaque(false);
        infoPanel.add(lblPageInfo);
        
        JSeparator sep2 = new JSeparator(SwingConstants.VERTICAL);
        sep2.setPreferredSize(new Dimension(1, 28));
        sep2.setForeground(borderColor);
        
        JPanel sizePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        sizePanel.setOpaque(false);
        
        JLabel lblShow = new JLabel("Hiển thị:");
        lblShow.setFont(AppFont.BODY);
        lblShow.setForeground(AppColor.TEXT_SECONDARY);
        
        JLabel lblItems = new JLabel("dòng");
        lblItems.setFont(AppFont.BODY);
        lblItems.setForeground(AppColor.TEXT_SECONDARY);
        
        sizePanel.add(lblShow);
        sizePanel.add(cbPageSize);
        sizePanel.add(lblItems);
        
        add(navPanel);
        add(sep1);
        add(infoPanel);
        add(sep2);
        add(sizePanel);
    }
    
    private void updatePageButtons(JPanel container) {
        container.removeAll();
        pageButtons.clear();
        
        // Nếu tổng số trang <= visiblePages, hiển thị tất cả
        if (totalPages <= visiblePages) {
            for (int i = 1; i <= totalPages; i++) {
                JButton btn = createPageButton(i);
                container.add(btn);
                pageButtons.add(btn);
            }
            container.revalidate();
            container.repaint();
            return;
        }
        
        // Tính toán trang bắt đầu và kết thúc
        int start = Math.max(1, currentPage - visiblePages / 2);
        int end = Math.min(totalPages, start + visiblePages - 1);
        
        // Điều chỉnh nếu end - start < visiblePages
        if (end - start < visiblePages - 1) {
            start = Math.max(1, end - visiblePages + 1);
        }
        
        // Thêm nút trang đầu
        if (start > 1) {
            JButton firstBtn = createPageButton(1);
            container.add(firstBtn);
            pageButtons.add(firstBtn);
            
            if (start > 2) {
                JLabel dots = createDotsLabel();
                container.add(dots);
            }
        }
        
        // Thêm các trang trong khoảng
        for (int i = start; i <= end; i++) {
            JButton btn = createPageButton(i);
            container.add(btn);
            pageButtons.add(btn);
        }
        
        // Thêm nút trang cuối
        if (end < totalPages) {
            if (end < totalPages - 1) {
                JLabel dots = createDotsLabel();
                container.add(dots);
            }
            
            JButton lastBtn = createPageButton(totalPages);
            container.add(lastBtn);
            pageButtons.add(lastBtn);
        }
        
        container.revalidate();
        container.repaint();
    }
    
    private JLabel createDotsLabel() {
        JLabel dots = new JLabel("...");
        dots.setFont(AppFont.TOAST_TITLE);
        dots.setForeground(AppColor.TEXT_DISABLED);
        return dots;
    }
    
    private void addEventListeners() {
        btnPrevious.addActionListener(e -> {
            if (currentPage > 1) {
                setCurrentPage(currentPage - 1);
                notifyPageChange();
            }
        });
        
        btnNext.addActionListener(e -> {
            if (currentPage < totalPages) {
                setCurrentPage(currentPage + 1);
                notifyPageChange();
            }
        });
        
        cbPageSize.addActionListener(e -> {
            pageSize = (Integer) cbPageSize.getSelectedItem();
            if (totalItems > 0) {
                totalPages = (int) Math.ceil((double) totalItems / pageSize);
            } else {
                totalPages = 10;
            }
            if (currentPage > totalPages) {
                currentPage = totalPages;
            }
            refreshUI();
            notifyPageSizeChange();
        });
    }
    
    private void refreshUI() {
        lblPageInfo.setText(String.format("Trang %d / %d", currentPage, totalPages));
        
        btnPrevious.setEnabled(currentPage > 1);
        btnNext.setEnabled(currentPage < totalPages);
        
        updateNavButtonState(btnPrevious, currentPage > 1);
        updateNavButtonState(btnNext, currentPage < totalPages);
        
        // Cập nhật lại các nút số trang
        if (pagePanel != null) {
            updatePageButtons(pagePanel);
        }
    }
    
    private void updateNavButtonState(JButton button, boolean enabled) {
        if (!enabled) {
            button.setForeground(AppColor.TEXT_DISABLED);
            button.setBackground(AppColor.WHITE);
            button.setBorder(new EmptyBorder(6, 10, 6, 10));
        } else {
            button.setForeground(textColor);
            button.setBackground(AppColor.WHITE);
            button.setBorder(new EmptyBorder(6, 10, 6, 10));
        }
        button.repaint();
    }
    
    private void notifyPageChange() {
        firePropertyChange("pageChanged", -1, currentPage);
    }
    
    private void notifyPageSizeChange() {
        firePropertyChange("pageSizeChanged", -1, pageSize);
    }
    
    // ============ PUBLIC METHODS ============
    
    public void setCurrentPage(int page) {
        int oldPage = this.currentPage;
        this.currentPage = Math.max(1, Math.min(page, totalPages));
        if (oldPage != this.currentPage) {
            refreshUI();
        }
    }
    
    public int getCurrentPage() {
        return currentPage;
    }
    
    public void setTotalPages(int totalPages) {
        this.totalPages = Math.max(1, totalPages);
        if (currentPage > totalPages) {
            currentPage = totalPages;
        }
        refreshUI();
    }
    
    public int getTotalPages() {
        return totalPages;
    }
    
    public void setPageSize(int pageSize) {
        if (this.pageSize != pageSize) {
            this.pageSize = pageSize;
            cbPageSize.setSelectedItem(pageSize);
        }
        refreshUI();
    }
    
    public int getPageSize() {
        return pageSize;
    }
    
    public void setTotalItems(int totalItems) {
        this.totalItems = totalItems;
        if (totalItems == 0) {
            this.totalPages = 1;
        } else {
            this.totalPages = (int) Math.ceil((double) totalItems / pageSize);
        }
        if (currentPage > totalPages) {
            currentPage = totalPages;
        }
        refreshUI();
    }
    
    public int getTotalItems() {
        return totalItems;
    }
    
    public void setVisiblePages(int visiblePages) {
        this.visiblePages = Math.max(3, visiblePages);
        refreshUI();
    }
    
    public int getVisiblePages() {
        return visiblePages;
    }
    
    public JButton getBtnPrevious() { return btnPrevious; }
    public JButton getBtnNext() { return btnNext; }
    public JComboBox<Integer> getCbPageSize() { return cbPageSize; }
}