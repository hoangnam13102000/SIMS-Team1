package com.view.client;


import com.dao.ProductDAO;
import com.i18n.Lang;
import com.model.Product;
import com.theme.AppColor;
import com.theme.AppFont;
import com.service.AuthService;
import com.service.CartService;
import com.model.CartItem;
import com.utils.NumberUtil;
import javax.swing.event.PopupMenuListener;
import javax.swing.event.PopupMenuEvent;
import com.utils.ImageUtil;
import com.model.User;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class ClientHeader extends JPanel {


    private final Map<String, NavItem> navItems = new LinkedHashMap<>();
    private JPanel navPanel;
    private JPanel accountBadge;
    private JTextField searchField;
    private JComponent searchBarAnchor;

    private Consumer<String> navigateListener;
    private Consumer<String> searchListener;
    private Runnable profileListener;
    private Runnable logoutListener;
    private Runnable cartListener;
    private CartIconButton cartButton;

    // ---------- Autocomplete tim san pham ----------
    private final ProductDAO productDAO = new ProductDAO();
    private static final int SUGGEST_LIMIT = 8;
    private static final int SUGGEST_DELAY_MS = 250;

    private JPopupMenu suggestPopup;
    private JList<Object> suggestList;
    private DefaultListModel<Object> suggestModel;
    private Timer suggestDebounce;
    private SwingWorker<List<Product>, Void> suggestWorker;
    /** Marker cuoi danh sach goi y: "Xem tat ca ket qua cho '...'" - bam vao se tim day du. */
    private static final Object VIEW_ALL_MARKER = new Object();

    public ClientHeader() {
        setLayout(new BorderLayout());
        setBackground(AppColor.WHITE);

        add(buildTopBar(), BorderLayout.NORTH);
        add(buildNavBar(), BorderLayout.SOUTH);
        
        // Badge so luong tren icon gio hang chi cap nhat neu header dang ky lang nghe CartService.
        CartService.getInstance().addListener(this::syncCartBadge);
        syncCartBadge();
    }
    
    /** Dong bo badge so luong tren icon gio hang voi tong so luong hien co trong CartService. */
    private void syncCartBadge() {
        updateCartCount(CartService.getInstance().getTotalQuantity());
    }

    // ---------- Top bar: logo + search + cart + account ----------

    private JPanel buildTopBar() {
        JPanel topBar = new JPanel(new BorderLayout(20, 0));
        topBar.setOpaque(true);
        topBar.setBackground(AppColor.WHITE);
        topBar.setBorder(new EmptyBorder(14, 24, 14, 20));

        topBar.add(buildLogoSection(), BorderLayout.WEST);
        topBar.add(buildSearchWrapper(), BorderLayout.CENTER);
        topBar.add(buildActionsSection(), BorderLayout.EAST);
        return topBar;
    }

    private JPanel buildLogoSection() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        panel.setOpaque(false);

        // Logo tu classpath: src/main/resources/logo/logo.png
        JLabel logoLabel = new JLabel(loadLogoIcon(38));
        logoLabel.setPreferredSize(new Dimension(38, 38));

        JLabel brand = new JLabel("SIMS");
        brand.setForeground(AppColor.TEXT_PRIMARY);
        brand.setFont(new Font("Segoe UI", Font.BOLD, 18));

        panel.add(logoLabel);
        panel.add(brand);
        return panel;
    }

    /** Load logo.png tu resources, scale ve size x size. Fallback icon neu thieu file. */
    private static ImageIcon loadLogoIcon(int size) {
        java.net.URL url = ClientHeader.class.getResource("/logo/logo.png");
        if (url != null) {
            ImageIcon raw = new ImageIcon(url);
            Image scaled = raw.getImage().getScaledInstance(size, size, Image.SCALE_SMOOTH);
            return new ImageIcon(scaled);
        }
        java.awt.image.BufferedImage fallback =
                new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = fallback.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(AppColor.ACCENT_HOVER);
        g2.fillRoundRect(0, 0, size, size, 12, 12);
        FontIcon icon = FontIcon.of(FontAwesomeSolid.MOBILE_ALT, Math.max(14, size / 2));
        icon.setIconColor(Color.WHITE);
        icon.paintIcon(null, g2, (size - icon.getIconWidth()) / 2, (size - icon.getIconHeight()) / 2);
        g2.dispose();
        return new ImageIcon(fallback);
    }

    // ---------- Search bar (giua header, giong website ban hang) ----------

    private JPanel buildSearchWrapper() {
        JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        wrapper.setOpaque(false);

        JPanel searchBar = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(AppColor.BG_LIGHTER);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        searchBar.setOpaque(false);
        searchBar.setPreferredSize(new Dimension(460, 40));
        searchBar.setBorder(new EmptyBorder(0, 16, 0, 6));
        searchBarAnchor = searchBar;

        FontIcon searchIconLeft = FontIcon.of(FontAwesomeSolid.SEARCH, 13);
        searchIconLeft.setIconColor(AppColor.TEXT_MUTED);
        JLabel searchIconLabel = new JLabel(searchIconLeft);
        searchIconLabel.setBorder(new EmptyBorder(0, 0, 0, 8));

        searchField = new JTextField();
        searchField.putClientProperty("JTextField.placeholderText", Lang.get("client.header.search.placeholder"));
        searchField.setOpaque(false);
        searchField.setBorder(null);
        searchField.setForeground(AppColor.TEXT_PRIMARY);
        searchField.setCaretColor(AppColor.TEXT_PRIMARY);
        searchField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        // Enter luon thuc hien tim kiem day du (voi noi dung hien tai cua o nhap).
        searchField.addActionListener(e -> triggerSearch());
        // Ban phim chi dung de dieu huong popup goi y (mui ten len/xuong, Esc dong lai).
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_DOWN:
                        if (suggestPopup.isVisible()) {
                            moveSuggestSelection(1);
                            e.consume();
                        }
                        break;
                    case KeyEvent.VK_UP:
                        if (suggestPopup.isVisible()) {
                            moveSuggestSelection(-1);
                            e.consume();
                        }
                        break;
                    case KeyEvent.VK_ENTER:
                        if (suggestPopup.isVisible() && suggestList.getSelectedIndex() >= 0) {
                            applySuggestion(suggestList.getSelectedValue());
                            e.consume();
                        }
                        break;
                    case KeyEvent.VK_ESCAPE:
                        hideSuggestions();
                        break;
                    default:
                        break;
                }
            }
        });
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { scheduleSuggest(); }
            @Override public void removeUpdate(DocumentEvent e) { scheduleSuggest(); }
            @Override public void changedUpdate(DocumentEvent e) { scheduleSuggest(); }
        });
        searchField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                if (suggestModel.size() > 0 && !searchField.getText().trim().isEmpty()) {
                    showSuggestPopup();
                }
            }
        });

        JPanel fieldWrapper = new JPanel(new BorderLayout());
        fieldWrapper.setOpaque(false);
        fieldWrapper.add(searchIconLabel, BorderLayout.WEST);
        fieldWrapper.add(searchField, BorderLayout.CENTER);

        initSuggestPopup();

        JButton searchButton = new JButton(Lang.get("client.header.searchButton"));
        searchButton.setFocusPainted(false);
        searchButton.setBorderPainted(false);
        searchButton.setBackground(AppColor.ACCENT_HOVER);
        searchButton.setForeground(Color.WHITE);
        searchButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        searchButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        searchButton.setBorder(new EmptyBorder(6, 16, 6, 16));
        searchButton.addActionListener(e -> triggerSearch());

        JPanel searchButtonWrapper = new JPanel(new GridBagLayout());
        searchButtonWrapper.setOpaque(false);
        searchButtonWrapper.add(roundedButton(searchButton));

        searchBar.add(fieldWrapper, BorderLayout.CENTER);
        searchBar.add(searchButtonWrapper, BorderLayout.EAST);

        wrapper.add(searchBar);
        return wrapper;
    }

    /** Boc button trong panel tu ve nen bo tron, vi JButton mac dinh kho bo tron dep tren moi L&F. */
    private JPanel roundedButton(JButton button) {
        JPanel rounded = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(AppColor.ACCENT_HOVER);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        rounded.setOpaque(false);
        button.setContentAreaFilled(false);
        rounded.add(button, BorderLayout.CENTER);
        return rounded;
    }

    private void triggerSearch() {
        String keyword = searchField.getText() == null ? "" : searchField.getText().trim();
        hideSuggestions();
        if (searchListener != null) searchListener.accept(keyword);
    }

    public void onSearch(Consumer<String> listener) {
        this.searchListener = listener;
    }

    // ---------- Autocomplete: popup goi y san pham khi go tim kiem ----------

    private void initSuggestPopup() {
        suggestModel = new DefaultListModel<>();
        suggestList = new JList<>(suggestModel);
        suggestList.setFocusable(false);
        suggestList.setOpaque(false);
        suggestList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        suggestList.setCellRenderer(new SuggestionRenderer());
        suggestList.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int idx = suggestList.locationToIndex(e.getPoint());
                if (idx >= 0) suggestList.setSelectedIndex(idx);
            }
        });
        suggestList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int idx = suggestList.locationToIndex(e.getPoint());
                if (idx >= 0) applySuggestion(suggestModel.getElementAt(idx));
            }
        });

        JScrollPane scroll = new JScrollPane(suggestList);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);

        suggestPopup = new JPopupMenu();
        suggestPopup.setLayout(new BorderLayout());
        suggestPopup.setBorder(BorderFactory.createLineBorder(AppColor.BORDER, 1));
        suggestPopup.setBackground(AppColor.WHITE);
        suggestPopup.add(scroll, BorderLayout.CENTER);
        suggestPopup.setFocusable(false);

        suggestDebounce = new Timer(SUGGEST_DELAY_MS, e -> fetchSuggestions(searchField.getText()));
        suggestDebounce.setRepeats(false);
    }

    /** Goi moi lan noi dung o tim kiem thay doi - debounce de khong query DB tren tung phim go. */
    private void scheduleSuggest() {
        suggestDebounce.restart();
    }

    private void fetchSuggestions(String rawKeyword) {
        String keyword = rawKeyword == null ? "" : rawKeyword.trim();
        if (keyword.isEmpty()) {
            hideSuggestions();
            return;
        }

        if (suggestWorker != null && !suggestWorker.isDone()) {
            suggestWorker.cancel(true);
        }

        suggestWorker = new SwingWorker<>() {
            @Override
            protected List<Product> doInBackground() {
                try {
                    return productDAO.searchActive(keyword);
                } catch (Exception ex) {
                    return new ArrayList<>();
                }
            }

            @Override
            protected void done() {
                if (isCancelled()) return;
                // Neu nguoi dung da go tiep sau khi worker nay bat dau, bo qua ket qua cu.
                if (!keyword.equals(searchField.getText().trim())) return;
                try {
                    populateSuggestions(get(), keyword);
                } catch (Exception ignored) {
                }
            }
        };
        suggestWorker.execute();
    }

    private void populateSuggestions(List<Product> products, String keyword) {
        suggestModel.clear();
        if (products == null || products.isEmpty()) {
            hideSuggestions();
            return;
        }
        int limit = Math.min(SUGGEST_LIMIT, products.size());
        for (int i = 0; i < limit; i++) {
            suggestModel.addElement(products.get(i));
        }
        if (products.size() > limit) {
            suggestModel.addElement(VIEW_ALL_MARKER);
        }
        suggestList.setSelectedIndex(-1);
        showSuggestPopup();
    }

    private void showSuggestPopup() {
        int width = searchBarAnchor.getWidth() > 0 ? searchBarAnchor.getWidth() : 460;
        int rowHeight = 52;
        int visibleRows = Math.min(suggestModel.size(), SUGGEST_LIMIT + 1);
        suggestList.setVisibleRowCount(visibleRows);
        suggestPopup.setPreferredSize(new Dimension(width, visibleRows * rowHeight));
        if (!suggestPopup.isVisible()) {
            suggestPopup.show(searchBarAnchor, 0, searchBarAnchor.getHeight() + 6);
        } else {
            suggestPopup.setSize(suggestPopup.getPreferredSize());
        }
    }

    private void hideSuggestions() {
        if (suggestPopup != null && suggestPopup.isVisible()) {
            suggestPopup.setVisible(false);
        }
    }

    private void moveSuggestSelection(int delta) {
        int size = suggestModel.size();
        if (size == 0) return;
        int next = suggestList.getSelectedIndex() + delta;
        if (next < 0) next = size - 1;
        if (next >= size) next = 0;
        suggestList.setSelectedIndex(next);
        suggestList.ensureIndexIsVisible(next);
    }

    /** Ap dung 1 goi y (san pham cu the hoac dong "xem tat ca") - dien vao o tim va chay tim kiem day du. */
    private void applySuggestion(Object selected) {
        if (selected == null) return;
        if (selected instanceof Product) {
            searchField.setText(((Product) selected).getProductName());
        }
        // VIEW_ALL_MARKER: giu nguyen tu khoa nguoi dung da go, chi can chay tim day du.
        triggerSearch();
    }

    /** Ve tung dong trong popup goi y: ten san pham + danh muc (ben trai), gia ban (ben phai). */
    private class SuggestionRenderer implements ListCellRenderer<Object> {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                        boolean isSelected, boolean cellHasFocus) {
            if (value == VIEW_ALL_MARKER) {
                JPanel row = new JPanel(new BorderLayout());
                row.setBorder(new EmptyBorder(10, 16, 10, 16));
                row.setBackground(isSelected ? AppColor.ACCENT_BG_SOFT : AppColor.WHITE);

                String keyword = searchField.getText() == null ? "" : searchField.getText().trim();
                JLabel label = new JLabel(Lang.get("client.header.search.viewAll") + " \"" + keyword + "\"");
                label.setFont(AppFont.SMALL_BOLD);
                label.setForeground(AppColor.ACCENT_HOVER);
                row.add(label, BorderLayout.WEST);
                return row;
            }

            Product product = (Product) value;

            JPanel row = new JPanel(new BorderLayout(10, 0));
            row.setBorder(new EmptyBorder(8, 16, 8, 14));
            row.setBackground(isSelected ? AppColor.ACCENT_BG_SOFT : AppColor.WHITE);

            FontIcon boxIcon = FontIcon.of(FontAwesomeSolid.BOX, 13);
            boxIcon.setIconColor(isSelected ? AppColor.ACCENT_HOVER : AppColor.TEXT_MUTED);
            JLabel iconLabel = new JLabel(boxIcon);
            iconLabel.setBorder(new EmptyBorder(0, 0, 0, 4));

            JPanel textPanel = new JPanel();
            textPanel.setOpaque(false);
            textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

            JLabel nameLabel = new JLabel(product.getProductName());
            nameLabel.setFont(AppFont.BODY_BOLD);
            nameLabel.setForeground(AppColor.TEXT_PRIMARY);
            nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel categoryLabel = new JLabel(product.getCategoryName());
            categoryLabel.setFont(AppFont.SMALL);
            categoryLabel.setForeground(AppColor.TEXT_MUTED);
            categoryLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

            textPanel.add(nameLabel);
            textPanel.add(categoryLabel);

            long price = product.getSellPrice() == null ? 0 : product.getSellPrice().longValue();
            JLabel priceLabel = new JLabel(NumberUtil.formatThousands(price) + " đ");
            priceLabel.setFont(AppFont.SMALL_BOLD);
            priceLabel.setForeground(AppColor.ACCENT_HOVER);

            row.add(iconLabel, BorderLayout.WEST);
            row.add(textPanel, BorderLayout.CENTER);
            row.add(priceLabel, BorderLayout.EAST);
            return row;
        }
    }

    // ---------- Cart icon + account (ben phai) ----------

    private JPanel buildActionsSection() {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        actions.setOpaque(false);

        cartButton = new CartIconButton();
        cartButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showCartDropdown();
            }
        });
        actions.add(cartButton);
        actions.add(buildAccountBadge());
        return actions;
    }

    public void onCartClick(Runnable listener) {
        this.cartListener = listener;
    }

    /** Cap nhat so luong hien thi tren badge do cua icon gio hang. */
    public void updateCartCount(int count) {
        if (cartButton != null) cartButton.setCount(count);
    }

    private class CartIconButton extends JPanel {
        private int count = 0;
        private boolean hover = false;
        private final FontIcon icon;

        CartIconButton() {
            setOpaque(false);
            setPreferredSize(new Dimension(44, 40));
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            icon = FontIcon.of(FontAwesomeSolid.SHOPPING_CART, 18);
            icon.setIconColor(AppColor.TEXT_SECONDARY);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }
            });
        }

        void setCount(int count) {
            this.count = count;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (hover) {
                g2.setColor(AppColor.BG_LIGHTER);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            }

            int iconX = (getWidth() - icon.getIconWidth()) / 2;
            int iconY = (getHeight() - icon.getIconHeight()) / 2;
            icon.paintIcon(this, g2, iconX, iconY);

            if (count > 0) {
                String text = count > 99 ? "99+" : String.valueOf(count);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
                FontMetrics fm = g2.getFontMetrics();
                int textWidth = fm.stringWidth(text);
                int badgeWidth = Math.max(16, textWidth + 8);
                int badgeHeight = 16;
                int badgeX = iconX + icon.getIconWidth() - badgeWidth + 6;
                int badgeY = iconY - 6;
                g2.setColor(AppColor.ERROR);
                g2.fillRoundRect(badgeX, badgeY, badgeWidth, badgeHeight, badgeHeight, badgeHeight);
                g2.setColor(Color.WHITE);
                g2.drawString(text, badgeX + (badgeWidth - textWidth) / 2, badgeY + badgeHeight - 4);
            }
            g2.dispose();
        }
    }

    private static final int CART_DROPDOWN_WIDTH = 340;
    private static final int CART_THUMB_SIZE = 48;

    /** Dropdown xem nhanh gio hang - the hien dai: anh san pham, ten + danh muc, bo dieu chinh so luong (-/+), gia. */
    private void showCartDropdown() {
        JPopupMenu popup = new JPopupMenu();
        popup.setBorder(new EmptyBorder(0, 0, 0, 0));
        popup.setOpaque(false);

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(AppColor.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(AppColor.BORDER, 1, true),
            new EmptyBorder(14, 14, 14, 14)
        ));

        JLabel title = new JLabel("Giỏ hàng");
        title.setFont(new Font("Segoe UI", Font.BOLD, 15));
        title.setForeground(AppColor.TEXT_PRIMARY);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        title.setBorder(new EmptyBorder(0, 2, 12, 2));
        card.add(title);

        java.util.List<CartItem> items = CartService.getInstance().getItems();
        if (items.isEmpty()) {
            JLabel empty = new JLabel("Chưa có sản phẩm nào");
            empty.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            empty.setForeground(AppColor.TEXT_MUTED);
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            empty.setBorder(new EmptyBorder(8, 2, 8, 2));
            card.add(empty);
        } else {
            int shown = 0;
            for (CartItem item : items) {
                if (shown >= 4) break; // chi xem nhanh 4 dong
                card.add(buildCartDropdownRow(item, popup));
                card.add(Box.createVerticalStrut(10));
                shown++;
            }
            if (items.size() > 4) {
                JLabel more = new JLabel("... và " + (items.size() - 4) + " sản phẩm khác");
                more.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                more.setForeground(AppColor.TEXT_MUTED);
                more.setAlignmentX(Component.LEFT_ALIGNMENT);
                more.setBorder(new EmptyBorder(0, 2, 8, 2));
                card.add(more);
            }

            JSeparator sep = new JSeparator();
            sep.setForeground(AppColor.BORDER);
            sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
            sep.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(sep);
            card.add(Box.createVerticalStrut(10));

            JPanel totalRow = new JPanel(new BorderLayout());
            totalRow.setOpaque(false);
            totalRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            totalRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

            JLabel totalCaption = new JLabel("Tổng cộng");
            totalCaption.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            totalCaption.setForeground(AppColor.TEXT_MUTED);

            JLabel totalValue = new JLabel(NumberUtil.formatThousands(CartService.getInstance().getTotal()) + " đ");
            totalValue.setFont(new Font("Segoe UI", Font.BOLD, 17));
            totalValue.setForeground(AppColor.ACCENT_HOVER);

            totalRow.add(totalCaption, BorderLayout.WEST);
            totalRow.add(totalValue, BorderLayout.EAST);
            card.add(totalRow);
            card.add(Box.createVerticalStrut(12));
        }

        JButton viewBtn = new JButton("Xem giỏ hàng") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(AppColor.ACCENT_HOVER);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        viewBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        viewBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        viewBtn.setPreferredSize(new Dimension(CART_DROPDOWN_WIDTH, 42));
        viewBtn.setFocusPainted(false);
        viewBtn.setContentAreaFilled(false);
        viewBtn.setBorderPainted(false);
        viewBtn.setOpaque(false);
        viewBtn.setForeground(Color.WHITE);
        viewBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        viewBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        viewBtn.addActionListener(e -> {
            popup.setVisible(false);
            if (cartListener != null) cartListener.run();
        });
        card.add(viewBtn);

        if (!items.isEmpty()) {
            JLabel hint = new JLabel("Bạn có thể thanh toán tại trang giỏ hàng", SwingConstants.CENTER);
            hint.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            hint.setForeground(AppColor.TEXT_MUTED);
            hint.setAlignmentX(Component.CENTER_ALIGNMENT);
            hint.setBorder(new EmptyBorder(8, 0, 0, 0));
            hint.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
            card.add(hint);
        }

        popup.add(card);
        popup.pack();
        popup.show(cartButton, cartButton.getWidth() - popup.getPreferredSize().width, cartButton.getHeight() + 8);
    }

    /** 1 dong san pham trong dropdown: anh (bo goc), ten + danh muc, bo dieu chinh so luong (-/+), gia.
     *  Khi doi so luong, cap nhat CartService roi dong + mo lai dropdown de phan anh du lieu moi nhat. */
    private JPanel buildCartDropdownRow(CartItem item, JPopupMenu popup) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(CART_DROPDOWN_WIDTH, CART_THUMB_SIZE));
        row.setPreferredSize(new Dimension(CART_DROPDOWN_WIDTH, CART_THUMB_SIZE));

        JLabel thumb = new JLabel(loadRoundedProductThumb(item.getProduct().getImageUrl(), CART_THUMB_SIZE));
        row.add(thumb, BorderLayout.WEST);

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

        JLabel name = new JLabel(item.getProduct().getProductName());
        name.setFont(new Font("Segoe UI", Font.BOLD, 13));
        name.setForeground(AppColor.TEXT_PRIMARY);
        name.setAlignmentX(Component.LEFT_ALIGNMENT);
        textPanel.add(name);

        String categoryName = item.getProduct().getCategoryName();
        if (categoryName != null && !categoryName.isBlank()) {
            JLabel category = new JLabel(categoryName);
            category.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            category.setForeground(AppColor.TEXT_MUTED);
            category.setAlignmentX(Component.LEFT_ALIGNMENT);
            textPanel.add(Box.createVerticalStrut(2));
            textPanel.add(category);
        }
        row.add(textPanel, BorderLayout.CENTER);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);
        right.add(buildQtyStepper(item, popup));

        JLabel price = new JLabel(NumberUtil.formatThousands(item.getSubtotal()) + " đ");
        price.setFont(new Font("Segoe UI", Font.BOLD, 13));
        price.setForeground(AppColor.TEXT_PRIMARY);
        right.add(price);

        row.add(right, BorderLayout.EAST);
        return row;
    }

    /** Bo dieu chinh so luong nho gon (-/so luong/+) dung trong dropdown gio hang. */
    private JPanel buildQtyStepper(CartItem item, JPopupMenu popup) {
        JPanel stepper = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
        stepper.setOpaque(false);

        JButton minus = qtyStepButton(FontAwesomeSolid.MINUS);
        JLabel qtyLabel = new JLabel(String.valueOf(item.getQuantity()), SwingConstants.CENTER);
        qtyLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        qtyLabel.setForeground(AppColor.TEXT_PRIMARY);
        qtyLabel.setPreferredSize(new Dimension(22, 22));
        JButton plus = qtyStepButton(FontAwesomeSolid.PLUS);

        minus.addActionListener(e -> {
            CartService.getInstance().updateQuantity(item.getProduct().getProductId(), item.getQuantity() - 1);
            popup.setVisible(false);
            showCartDropdown();
        });
        plus.addActionListener(e -> {
            CartService.getInstance().updateQuantity(item.getProduct().getProductId(), item.getQuantity() + 1);
            popup.setVisible(false);
            showCartDropdown();
        });

        stepper.add(minus);
        stepper.add(qtyLabel);
        stepper.add(plus);
        return stepper;
    }

    private JButton qtyStepButton(FontAwesomeSolid iconType) {
        JButton button = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(AppColor.BG_LIGHTER);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.dispose();

                FontIcon icon = FontIcon.of(iconType, 9);
                icon.setIconColor(AppColor.TEXT_SECONDARY);
                int ix = (getWidth() - icon.getIconWidth()) / 2;
                int iy = (getHeight() - icon.getIconHeight()) / 2;
                icon.paintIcon(this, g, ix, iy);
            }
        };
        button.setPreferredSize(new Dimension(22, 22));
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return button;
    }

    /** Anh dai dien san pham bo goc, dung trong dropdown gio hang - fallback icon hop neu chua co/loi anh. */
    private ImageIcon loadRoundedProductThumb(String imageUrl, int size) {
        BufferedImage raw = (imageUrl == null || imageUrl.isBlank()) ? null : ImageUtil.readSafe(imageUrl);
        BufferedImage square = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = square.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setClip(new RoundRectangle2D.Float(0, 0, size, size, 12, 12));

        if (raw != null) {
            int side = Math.min(raw.getWidth(), raw.getHeight());
            BufferedImage cropped = raw.getSubimage((raw.getWidth() - side) / 2, (raw.getHeight() - side) / 2, side, side);
            g2.drawImage(cropped, 0, 0, size, size, null);
        } else {
            g2.setColor(AppColor.ACCENT_BG_SOFT);
            g2.fillRect(0, 0, size, size);
            FontIcon icon = FontIcon.of(FontAwesomeSolid.BOX, (int) (size * 0.45));
            icon.setIconColor(AppColor.ACCENT_HOVER);
            int ix = (size - icon.getIconWidth()) / 2;
            int iy = (size - icon.getIconHeight()) / 2;
            icon.paintIcon(null, g2, ix, iy);
        }
        g2.dispose();
        return new ImageIcon(square);
    }

    // ---------- Nav bar (Cua hang, Don hang cua toi, ...) dang tab gach chan ----------

    private JPanel buildNavBar() {
        JPanel navBarWrapper = new JPanel(new BorderLayout());
        navBarWrapper.setOpaque(true);
        navBarWrapper.setBackground(AppColor.BG_LIGHTER);
        navBarWrapper.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, AppColor.BORDER));

        navPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        navPanel.setOpaque(false);
        navPanel.setBorder(new EmptyBorder(0, 20, 0, 0));

        navBarWrapper.add(navPanel, BorderLayout.WEST);
        return navBarWrapper;
    }

    public void addPage(String key, String label, FontAwesomeSolid iconType) {
        NavItem item = new NavItem(key, label, iconType);
        navItems.put(key, item);
        navPanel.add(item);
    }

    public void setActive(String key) {
        for (Map.Entry<String, NavItem> entry : navItems.entrySet()) {
            entry.getValue().setActive(entry.getKey().equals(key));
        }
    }

    public void onNavigate(Consumer<String> listener) {
        this.navigateListener = listener;
    }

    private class NavItem extends JPanel {
        private final String key;
        private boolean active = false;
        private boolean hover = false;
        private final FontIcon icon;
        private final JLabel label;

        NavItem(String key, String text, FontAwesomeSolid iconType) {
            this.key = key;
            setOpaque(false);
            setLayout(new FlowLayout(FlowLayout.CENTER, 8, 0));
            setBorder(new EmptyBorder(11, 14, 8, 14));
            setCursor(new Cursor(Cursor.HAND_CURSOR));

            icon = FontIcon.of(iconType, 13);
            icon.setIconColor(AppColor.TEXT_MUTED);
            label = new JLabel(text);
            label.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            label.setForeground(AppColor.TEXT_MUTED);

            add(new JLabel(icon));
            add(label);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    ClientHeader.this.setActive(key);
                    if (navigateListener != null) navigateListener.accept(key);
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }
            });
        }

        void setActive(boolean active) {
            this.active = active;
            Color fg = active ? AppColor.ACCENT_HOVER : AppColor.TEXT_MUTED;
            icon.setIconColor(fg);
            label.setForeground(fg);
            label.setFont(label.getFont().deriveFont(active ? Font.BOLD : Font.PLAIN));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (hover && !active) {
                g2.setColor(AppColor.ACCENT_BG_SOFT);
                g2.fillRoundRect(0, 0, getWidth(), getHeight() - 3, 8, 8);
            }
            if (active) {
                g2.setColor(AppColor.ACCENT_HOVER);
                g2.fillRect(0, getHeight() - 3, getWidth(), 3);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ---------- Account dropdown (dong bo giao dien voi Header cua admin) ----------

    private static final int AVATAR_SIZE = 36;
    private static final int EMAIL_MAX_WIDTH = 150;

    private JPanel buildAccountBadge() {
        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setOpaque(false);

        accountBadge = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getMousePosition() != null) {
                    g2.setColor(AppColor.BG_LIGHTER);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        accountBadge.setOpaque(false);
        accountBadge.setBorder(new EmptyBorder(4, 8, 4, 8));
        accountBadge.setCursor(new Cursor(Cursor.HAND_CURSOR));

        String displayName = currentDisplayName();

        JLabel avatarLabel = new JLabel(currentAvatarIcon(displayName));
        avatarLabel.setName("accountAvatarLabel");

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

        JLabel nameLabel = new JLabel(displayName);
        nameLabel.setForeground(AppColor.TEXT_PRIMARY);
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        nameLabel.setName("accountNameLabel");
        textPanel.add(nameLabel);

        String email = AuthService.getInstance().isLoggedIn() ? AuthService.getInstance().getCurrentUser().getEmail() : "";
        if (email != null && !email.isBlank()) {
            JLabel emailLabel = new JLabel(truncate(email, new Font("Segoe UI", Font.PLAIN, 11), EMAIL_MAX_WIDTH));
            emailLabel.setForeground(AppColor.TEXT_MUTED);
            emailLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            emailLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            textPanel.add(Box.createVerticalStrut(1));
            textPanel.add(emailLabel);
        }

        FontIcon chevronIcon = FontIcon.of(FontAwesomeSolid.CHEVRON_DOWN, 10);
        chevronIcon.setIconColor(AppColor.TEXT_MUTED);
        JLabel chevronLabel = new JLabel(chevronIcon);

        accountBadge.add(avatarLabel);
        accountBadge.add(textPanel);
        accountBadge.add(chevronLabel);

        // Popup duoc xay lai MOI LAN mo (thay vi 1 lan luc khoi tao) de avatar/ten/email/sdt
        // luon phan anh du lieu moi nhat, vd ngay sau khi doi anh dai dien o Trang ca nhan.
        accountBadge.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                User freshUser = AuthService.getInstance().isLoggedIn() ? AuthService.getInstance().getCurrentUser() : null;
                String freshName = currentDisplayName();
                String freshEmail = freshUser != null ? freshUser.getEmail() : "";
                ImageIcon freshAvatarIcon = ImageUtil.circularIcon(freshUser != null ? freshUser.getAvatarUrl() : null, AVATAR_SIZE, freshName);

                JPopupMenu menu = buildAccountPopup(freshUser, freshName, freshEmail, freshAvatarIcon);
                menu.addPopupMenuListener(new PopupMenuListener() {
                    @Override
                    public void popupMenuWillBecomeVisible(PopupMenuEvent ev) {
                        chevronIcon.setIkon(FontAwesomeSolid.CHEVRON_UP);
                        chevronLabel.repaint();
                    }

                    @Override
                    public void popupMenuWillBecomeInvisible(PopupMenuEvent ev) {
                        chevronIcon.setIkon(FontAwesomeSolid.CHEVRON_DOWN);
                        chevronLabel.repaint();
                    }

                    @Override
                    public void popupMenuCanceled(PopupMenuEvent ev) {
                    }
                });
                menu.show(accountBadge, accountBadge.getWidth() - 220, accountBadge.getHeight() + 8);
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                accountBadge.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                accountBadge.repaint();
            }
        });

        wrapper.add(accountBadge);
        return wrapper;
    }

    /** Icon avatar hien tai cua nguoi dung dang dang nhap (hoac placeholder neu chua co/chua dang nhap). */
    private ImageIcon currentAvatarIcon(String displayName) {
        User user = AuthService.getInstance().isLoggedIn() ? AuthService.getInstance().getCurrentUser() : null;
        return ImageUtil.circularIcon(user != null ? user.getAvatarUrl() : null, AVATAR_SIZE, displayName);
    }

    private JPopupMenu buildAccountPopup(User user, String displayName, String email, ImageIcon avatarIcon) {
        JPopupMenu popup = new JPopupMenu();
        popup.setBorder(new EmptyBorder(0, 0, 0, 0));
        popup.setOpaque(false);

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(AppColor.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(AppColor.BORDER, 1, true),
            new EmptyBorder(8, 8, 8, 8)
        ));

        card.add(buildInfoSection(user, displayName, email, avatarIcon));
        card.add(Box.createVerticalStrut(6));
        card.add(buildDivider());
        card.add(Box.createVerticalStrut(6));

        card.add(buildMenuRow(Lang.get("client.header.profile"), FontAwesomeSolid.ID_CARD, AppColor.TEXT_PRIMARY, popup, () -> {
            if (profileListener != null) profileListener.run();
        }));
        card.add(Box.createVerticalStrut(6));
        card.add(buildDivider());
        card.add(Box.createVerticalStrut(6));
        card.add(buildMenuRow(Lang.get("client.header.logout"), FontAwesomeSolid.SIGN_OUT_ALT, AppColor.ERROR, popup, () -> {
            if (logoutListener != null) logoutListener.run();
        }));

        popup.add(card);
        popup.pack();
        return popup;
    }

    /** Khoi thong tin ca nhan hien o dau dropdown: avatar + ho ten + email + SDT (neu co). */
    private JPanel buildInfoSection(User user, String displayName, String email, ImageIcon avatarIcon) {
        JPanel section = new JPanel(new BorderLayout(10, 0));
        section.setOpaque(false);
        section.setBorder(new EmptyBorder(4, 6, 4, 6));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.setMaximumSize(new Dimension(240, 60));

        JLabel avatarLabel = new JLabel(avatarIcon);
        section.add(avatarLabel, BorderLayout.WEST);

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

        JLabel nameLabel = new JLabel(displayName);
        nameLabel.setForeground(AppColor.TEXT_PRIMARY);
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        textPanel.add(nameLabel);

        if (email != null && !email.isBlank()) {
            JLabel emailLabel = new JLabel(truncate(email, new Font("Segoe UI", Font.PLAIN, 11), 170));
            emailLabel.setForeground(AppColor.TEXT_MUTED);
            emailLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            emailLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            textPanel.add(Box.createVerticalStrut(2));
            textPanel.add(emailLabel);
        }

        String phone = user != null ? user.getPhone() : null;
        if (phone != null && !phone.isBlank()) {
            JLabel phoneLabel = new JLabel(phone);
            phoneLabel.setForeground(AppColor.TEXT_MUTED);
            phoneLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            phoneLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            textPanel.add(Box.createVerticalStrut(2));
            textPanel.add(phoneLabel);
        }

        section.add(textPanel, BorderLayout.CENTER);
        return section;
    }

    private JPanel buildMenuRow(String label, FontAwesomeSolid iconType, Color fg, JPopupMenu popup, Runnable action) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(true);
        row.setBackground(AppColor.WHITE);
        row.setBorder(new EmptyBorder(9, 10, 9, 14));
        row.setCursor(new Cursor(Cursor.HAND_CURSOR));
        row.setMaximumSize(new Dimension(220, 40));
        row.setPreferredSize(new Dimension(190, 38));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        FontIcon icon = FontIcon.of(iconType, 14);
        icon.setIconColor(fg);
        row.add(new JLabel(icon), BorderLayout.WEST);

        JLabel text = new JLabel(label);
        text.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        text.setForeground(fg);
        text.setBorder(new EmptyBorder(0, 8, 0, 0));
        row.add(text, BorderLayout.CENTER);

        row.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                popup.setVisible(false);
                action.run();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                row.setBackground(AppColor.BG_LIGHTER);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                row.setBackground(AppColor.WHITE);
            }
        });
        return row;
    }

    private JComponent buildDivider() {
        JSeparator sep = new JSeparator();
        sep.setForeground(AppColor.BORDER);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        return sep;
    }

    /** Cat chuoi + them "..." neu vuot qua maxWidth (px) theo font truyen vao - dung cho email dai. */
    private String truncate(String text, Font font, int maxWidth) {
        if (text == null || text.isBlank()) return "";
        FontMetrics fm = getFontMetrics(font);
        if (fm.stringWidth(text) <= maxWidth) return text;

        String ellipsis = "...";
        int ellipsisWidth = fm.stringWidth(ellipsis);
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (fm.stringWidth(sb.toString() + c) + ellipsisWidth > maxWidth) break;
            sb.append(c);
        }
        return sb + ellipsis;
    }

    private String currentDisplayName() {
        return AuthService.getInstance().isLoggedIn()
                ? AuthService.getInstance().getCurrentUser().getFullName()
                : Lang.get("client.header.guest");
    }

    /** Goi lai sau khi doi ho ten/anh dai dien o Trang ca nhan de cap nhat lai badge tren header. */
    public void refreshAccountLabel() {
        String displayName = currentDisplayName();
        for (Component c : accountBadge.getComponents()) {
            if (c instanceof JLabel && "accountAvatarLabel".equals(c.getName())) {
                ((JLabel) c).setIcon(currentAvatarIcon(displayName));
            }
            if (c instanceof JPanel) {
                for (Component inner : ((JPanel) c).getComponents()) {
                    if (inner instanceof JLabel && "accountNameLabel".equals(inner.getName())) {
                        ((JLabel) inner).setText(displayName);
                    }
                }
            }
        }
        accountBadge.revalidate();
        accountBadge.repaint();
    }

    public void onProfile(Runnable listener) {
        this.profileListener = listener;
    }

    public void onLogout(Runnable listener) {
        this.logoutListener = listener;
    }
}
