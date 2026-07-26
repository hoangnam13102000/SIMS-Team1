package com.components;

import com.theme.AppColor;
import com.theme.ThemeMode;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * O tim kiem co debounce (tranh goi search lien tuc khi dang go) + autocomplete
 * (goi y tu 1 danh sach cho san qua setSuggestions()).
 * <p>
 * CAC DIEM QUAN TRONG:
 * <ul>
 *   <li><b>Khong bi goi notifyListeners() 2 lan khi chon 1 goi y.</b> Moi thay doi
 *   text CO CHU DICH TU CODE (chon goi y, hoac goi setText() tu ben ngoai) deu di
 *   qua setTextSilently(), dat co programmaticChange=true de onTextChanged() bo
 *   qua hoan toan debounce + autocomplete trong luc do.</li>
 *   <li>An popup khi mat focus: dung SwingUtilities.isDescendingFrom(...) de bao
 *   quat moi component con nam trong popup, tranh an nham khi dang click chon.</li>
 *   <li><b>Popup trong suot THAT:</b> KHONG dung JPopupMenu nua - vi JPopupMenu di
 *   qua PopupFactory, va cac Look&amp;Feel (vd FlatLaf) thuong tu dong boc them 1
 *   lop panel/shadow rieng, DUC san (de ve do bong dep cho moi popup trong app),
 *   nam NGOAI tam kiem soat cua setOpaque(false) tren cac component con - du sua
 *   dung huong o JScrollPane/JViewport van khong an thua vi lop boc do khong to.
 *   Thay vao do, card goi y duoc them TRUC TIEP vao JLayeredPane cua cua so chua
 *   no (xem showOverlay()/hideSuggestions()) - khong con lop boc LAF nao chen
 *   vao nua, nen alpha compositing (Java2D) hoat dong dung y muon.</li>
 *   <li>Popup tu co gian ca chieu rong (theo toan bo thanh search, ke ca icon)
 *   lan chieu cao (theo so luong goi y thuc te, toi da MAX_VISIBLE_ROWS dong).</li>
 *   <li>Giao dien dong goi y: bo goc, icon kinh lup nhat mau, padding thoai mai,
 *   hover + trang thai chon theo mau accent cua app - thay cho JList mac dinh.</li>
 *   <li>Nut "X" xoa noi dung, chi hien khi o tim kiem dang co text.</li>
 *   <li>onSuggestionSelected(...) va clearSuggestions() de API linh hoat hon.</li>
 * </ul>
 */
public class BaseSearch extends JPanel {

    private static final int DEFAULT_DEBOUNCE_MS = 350;
    private static final int MAX_SUGGESTIONS = 10;
    private static final int ROW_HEIGHT = 38;
    private static final int MAX_VISIBLE_ROWS = 6;

    private final JTextField searchField;
    private final JLabel clearButton;
    private final List<Consumer<String>> listeners = new ArrayList<>();
    private final Timer debounceTimer;

    // Autocomplete
    private List<String> suggestions = new ArrayList<>();
    private final JPanel popupCard;
    private final JList<String> suggestionList;
    private final JScrollPane scrollPane;
    private int hoveredIndex = -1;

    // Popup gio la 1 JPanel thuong duoc them/go truc tiep vao JLayeredPane cua
    // cua so chua BaseSearch nay - KHONG dung JPopupMenu (xem giai thich o
    // javadoc dau class). 2 bien duoi day theo doi trang thai hien/an va noi
    // popup dang duoc gan vao, de go dung cho khi an.
    private boolean suggestionsVisible = false;
    private JLayeredPane attachedLayeredPane;

    // Callback khi 1 goi y duoc chon
    private Consumer<String> onSuggestionSelected;

    // True trong luc code TU DAT lai text (chon goi y / setText() tu ben ngoai) -
    // giup onTextChanged() phan biet duoc voi nguoi dung tu go, de KHONG kich hoat
    // debounce/autocomplete mot cach ngoai y muon trong nhung truong hop do.
    private boolean programmaticChange = false;

    public BaseSearch() {
        this("Tìm kiếm...");
    }

    public BaseSearch(String placeholder) {
        this(placeholder, DEFAULT_DEBOUNCE_MS);
    }

    public BaseSearch(String placeholder, int debounceMs) {
        setLayout(new BorderLayout());
        setBackground(AppColor.BG_LIGHT);
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(AppColor.BORDER, 1, true),
            new EmptyBorder(6, 12, 6, 12)
        ));
        setPreferredSize(new Dimension(320, 38));
        setOpaque(true);

        // Icon Search
        FontIcon searchIcon = FontIcon.of(FontAwesomeSolid.SEARCH, 14);
        searchIcon.setIconColor(AppColor.TEXT_MUTED);
        JLabel iconLabel = new JLabel(searchIcon);
        iconLabel.setBorder(new EmptyBorder(0, 0, 0, 8));

        searchField = new JTextField();
        searchField.setBorder(BorderFactory.createEmptyBorder());
        searchField.setOpaque(false);
        searchField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        searchField.putClientProperty("JTextField.placeholderText", placeholder);

        // Nut "X" xoa noi dung - chi hien khi o tim kiem dang co text
        FontIcon clearIcon = FontIcon.of(FontAwesomeSolid.TIMES_CIRCLE, 13);
        clearIcon.setIconColor(AppColor.TEXT_MUTED);
        clearButton = new JLabel(clearIcon);
        clearButton.setBorder(new EmptyBorder(0, 8, 0, 0));
        clearButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        clearButton.setVisible(false);
        clearButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                clear();
                searchField.requestFocusInWindow();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                ((FontIcon) clearButton.getIcon()).setIconColor(AppColor.TEXT_PRIMARY);
                clearButton.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                ((FontIcon) clearButton.getIcon()).setIconColor(AppColor.TEXT_MUTED);
                clearButton.repaint();
            }
        });

        add(iconLabel, BorderLayout.WEST);
        add(searchField, BorderLayout.CENTER);
        add(clearButton, BorderLayout.EAST);

        // Debounce Timer
        debounceTimer = new Timer(debounceMs, e -> notifyListeners());
        debounceTimer.setRepeats(false);

        // Document Listener (Debounce + Autocomplete + hien/an nut X)
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { updateClearButtonVisibility(); onTextChanged(); }
            @Override public void removeUpdate(DocumentEvent e) { updateClearButtonVisibility(); onTextChanged(); }
            @Override public void changedUpdate(DocumentEvent e) { updateClearButtonVisibility(); onTextChanged(); }
        });

        searchField.addActionListener(e -> {
            debounceTimer.stop();
            hideSuggestions();
            notifyListeners();
        });

        // Focus listener de an popup khi mat focus
        searchField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                Component opposite = e.getOppositeComponent();
                if (opposite == null || !SwingUtilities.isDescendingFrom(opposite, popupCard)) {
                    hideSuggestions();
                }
            }
        });

        // ===== Autocomplete card (KHONG dung JPopupMenu - xem javadoc dau class) =====
        suggestionList = new JList<>();
        suggestionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        suggestionList.setOpaque(false); // de nen ban trong suot cua card xuyen qua duoc
        suggestionList.setBorder(new EmptyBorder(6, 0, 6, 0));
        suggestionList.setFixedCellHeight(ROW_HEIGHT);
        suggestionList.setFocusable(false); // khong can focus that - dieu huong ban phim xu ly qua KeyListener tren searchField
        suggestionList.setCellRenderer(new SuggestionCellRenderer());

        suggestionList.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int idx = suggestionList.locationToIndex(e.getPoint());
                if (idx != hoveredIndex) {
                    hoveredIndex = idx;
                    suggestionList.repaint();
                }
            }
        });
        suggestionList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                hoveredIndex = -1;
                suggestionList.repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                int idx = suggestionList.locationToIndex(e.getPoint());
                if (idx >= 0) suggestionList.setSelectedIndex(idx);
                if (e.getClickCount() == 1) {
                    selectSuggestion();
                }
            }
        });

        scrollPane = new JScrollPane(suggestionList);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        // JViewport mac dinh dung BACKINGSTORE_SCROLL_MODE (tu ve 1 anh dem
        // rieng de cuon muot hon) - che do nay VAN TU FILL nen duc ngam, BO
        // QUA setOpaque(false) o tren. Chuyen sang SIMPLE_SCROLL_MODE de
        // viewport khong tu ve nen nua.
        scrollPane.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        // Card bo goc, nen mau ban trong suot LAY THEO AppColor.WHITE (tu doi
        // sang/toi theo theme hien tai) thay vi trang cung - de popup luon
        // khop mau voi theme dang chon (sang thi nen trang mo, toi thi nen
        // toi mo). Day la trong suot THAT (Java2D alpha compositing): noi
        // dung ben duoi (bang du lieu, header...) se "phang phat" hien qua
        // lop nen nay. Vi khong con di qua JPopupMenu/PopupFactory nua (xem
        // javadoc dau class), khong co lop boc LAF nao ep ve dac nua.
        popupCard = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                Color base = AppColor.WHITE; // trang (light) hoac xam dam (dark) tuy theme
                g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 232));
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);

                g2.setColor(new Color(AppColor.BORDER.getRed(), AppColor.BORDER.getGreen(), AppColor.BORDER.getBlue(), 190));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
                g2.dispose();
            }
        };
        popupCard.setOpaque(false);
        popupCard.setBorder(new EmptyBorder(1, 1, 1, 1));
        popupCard.add(scrollPane, BorderLayout.CENTER);

        // Keyboard navigation
        setupKeyboardNavigation();
    }

    private void onTextChanged() {
        if (programmaticChange) return; // code tu doi text (khong phai nguoi dung go) -> bo qua
        debounceTimer.restart();
        updateSuggestions();
    }

    /** Hien/an nut "X" xoa noi dung tuy theo o tim kiem dang trong hay co text. */
    private void updateClearButtonVisibility() {
        boolean shouldShow = !searchField.getText().isEmpty();
        if (clearButton.isVisible() != shouldShow) {
            clearButton.setVisible(shouldShow);
            revalidate();
            repaint();
        }
    }

    private void setupKeyboardNavigation() {
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (!suggestionsVisible) return;

                switch (e.getKeyCode()) {
                    case KeyEvent.VK_DOWN:
                        int idx = suggestionList.getSelectedIndex();
                        if (idx < suggestionList.getModel().getSize() - 1) {
                            suggestionList.setSelectedIndex(idx + 1);
                            suggestionList.ensureIndexIsVisible(idx + 1);
                        }
                        e.consume();
                        break;
                    case KeyEvent.VK_UP:
                        idx = suggestionList.getSelectedIndex();
                        if (idx > 0) {
                            suggestionList.setSelectedIndex(idx - 1);
                            suggestionList.ensureIndexIsVisible(idx - 1);
                        } else if (idx == 0) {
                            searchField.requestFocus();
                            searchField.setCaretPosition(searchField.getText().length());
                        }
                        e.consume();
                        break;
                    case KeyEvent.VK_ENTER:
                        selectSuggestion();
                        e.consume();
                        break;
                    case KeyEvent.VK_ESCAPE:
                        hideSuggestions();
                        e.consume();
                        break;
                }
            }
        });
    }

    private void updateSuggestions() {
        String text = searchField.getText().trim().toLowerCase();
        if (text.isEmpty() || suggestions.isEmpty()) {
            hideSuggestions();
            return;
        }

        List<String> filtered = suggestions.stream()
                .filter(s -> s.toLowerCase().contains(text))
                .limit(MAX_SUGGESTIONS)
                .collect(Collectors.toList());

        if (filtered.isEmpty()) {
            hideSuggestions();
            return;
        }

        hoveredIndex = -1;
        suggestionList.setListData(filtered.toArray(new String[0]));
        suggestionList.setSelectedIndex(0);

        showOverlay();
    }

    /**
     * Tinh vi tri/kich thuoc roi THEM popupCard truc tiep vao JLayeredPane cua
     * cua so dang chua BaseSearch nay (thay vi goi JPopupMenu.show()). Day la
     * ly do popup trong suot duoc THAT SU (khong co lop boc LAF nao chen vao).
     */
    private void showOverlay() {
        JRootPane rootPane = SwingUtilities.getRootPane(this);
        if (rootPane == null) return; // chua thuc su nam trong 1 cua so hien thi
        JLayeredPane layeredPane = rootPane.getLayeredPane();

        // Om tron dung chieu rong CA THANH SEARCH (this, bao gom ca icon).
        int popupWidth = Math.max(getWidth(), 220);

        // Chieu cao tu co gian theo SO LUONG goi y thuc te (toi da
        // MAX_VISIBLE_ROWS dong, con lai thi cuon).
        int itemCount = suggestionList.getModel().getSize();
        int visibleRows = Math.max(1, Math.min(itemCount, MAX_VISIBLE_ROWS));
        int popupHeight = visibleRows * ROW_HEIGHT
                + suggestionList.getInsets().top + suggestionList.getInsets().bottom + 2;

        Point origin = SwingUtilities.convertPoint(this, 0, getHeight() + 4, layeredPane);
        popupCard.setBounds(origin.x, origin.y, popupWidth, popupHeight);

        if (popupCard.getParent() != layeredPane) {
            layeredPane.add(popupCard, JLayeredPane.POPUP_LAYER);
            attachedLayeredPane = layeredPane;
        }
        popupCard.validate(); // layout dong bo cho scrollPane khop bounds moi, tranh giat hinh
        popupCard.repaint();
        suggestionsVisible = true;
    }

    private void selectSuggestion() {
        String selected = suggestionList.getSelectedValue();
        if (selected == null) return;

        // Dat text bang duong "im lang" (khong kich hoat debounce/autocomplete
        // lai lan nua) - day chinh la phan sua loi goi notifyListeners() 2 lan.
        setTextSilently(selected);
        hideSuggestions();

        if (onSuggestionSelected != null) {
            onSuggestionSelected.accept(selected);
        }

        // Chi notify DUY NHAT 1 lan tai day
        notifyListeners();
    }

    /** Dat text ma KHONG kich hoat debounce/autocomplete (dung cho thay doi text tu code). */
    private void setTextSilently(String text) {
        programmaticChange = true;
        try {
            searchField.setText(text);
        } finally {
            programmaticChange = false;
        }
    }

    private void hideSuggestions() {
        if (!suggestionsVisible) return;
        suggestionsVisible = false;
        if (attachedLayeredPane != null) {
            attachedLayeredPane.remove(popupCard);
            attachedLayeredPane.repaint();
            attachedLayeredPane = null;
        }
    }

    private void notifyListeners() {
        String keyword = searchField.getText().trim();
        for (Consumer<String> listener : listeners) {
            listener.accept(keyword);
        }
    }

    /**
     * Cell renderer rieng cho danh sach goi y: icon kinh lup nhat mau truoc
     * moi dong, padding thoai mai. Ban than moi dong KHONG to nen dac (opaque
     * = false) - de lop nen ban trong suot cua popupCard xuyen qua deu, chi
     * ve THEM 1 lop phu (tint) rieng khi dong dang hover/duoc chon.
     */
    private final class SuggestionCellRenderer extends JPanel implements ListCellRenderer<String> {
        private final JLabel iconLabel = new JLabel();
        private final JLabel textLabel = new JLabel();
        private boolean selectedState = false;
        private boolean hoveredState = false;

        SuggestionCellRenderer() {
            setLayout(new BorderLayout(10, 0));
            setBorder(new EmptyBorder(0, 14, 0, 14));
            textLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            add(iconLabel, BorderLayout.WEST);
            add(textLabel, BorderLayout.CENTER);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (selectedState) {
                Color accent = AppColor.ACCENT;
                g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 215));
                g2.fillRoundRect(6, 2, getWidth() - 12, getHeight() - 4, 8, 8);
            } else if (hoveredState) {
                // Light: phu 1 lop den nhat (lam noi bat tren nen sang).
                // Dark: phu 1 lop trang nhat (lam noi bat tren nen toi) -
                // dung 1 mau den mo tren nen toi se gan nhu khong thay gi.
                boolean dark = AppColor.getCurrentMode() == ThemeMode.DARK;
                g2.setColor(dark ? new Color(255, 255, 255, 30) : new Color(0, 0, 0, 22));
                g2.fillRoundRect(6, 2, getWidth() - 12, getHeight() - 4, 8, 8);
            }
            g2.dispose();
            super.paintComponent(g);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends String> list, String value, int index,
                                                        boolean isSelected, boolean cellHasFocus) {
            textLabel.setText(value);
            this.selectedState = isSelected;
            this.hoveredState = !isSelected && index == hoveredIndex;

            FontIcon icon = FontIcon.of(FontAwesomeSolid.SEARCH, 11);
            if (isSelected) {
                textLabel.setForeground(Color.WHITE);
                icon.setIconColor(new Color(255, 255, 255, 210));
            } else {
                textLabel.setForeground(AppColor.TEXT_PRIMARY);
                icon.setIconColor(AppColor.TEXT_MUTED);
            }
            iconLabel.setIcon(icon);
            return this;
        }
    }

    // ====================== PUBLIC API ======================

    public void onSearch(Consumer<String> listener) {
        listeners.add(listener);
    }

    /** Dang ky callback rieng, duoc goi CHI KHI nguoi dung chon 1 goi y tu danh sach autocomplete. */
    public void onSuggestionSelected(Consumer<String> callback) {
        this.onSuggestionSelected = callback;
    }

    public String getText() {
        return searchField.getText();
    }

    /** Dat text tu code (vd: khoi phuc filter da luu). Khong kich hoat debounce/search, khong hien popup. */
    public void setText(String text) {
        setTextSilently(text);
        hideSuggestions();
    }

    public void clear() {
        setTextSilently("");
        hideSuggestions();
        notifyListeners(); // Bao cho listener biet search da duoc xoa trong (tim tat ca)
    }

    /** Xoa toan bo danh sach goi y hien co va an popup neu dang mo. */
    public void clearSuggestions() {
        this.suggestions.clear();
        hideSuggestions();
    }

    public JTextField getTextField() {
        return searchField;
    }

    /**
     * Cung cấp danh sách gợi ý autocomplete
     */
    public void setSuggestions(List<String> suggestions) {
        this.suggestions = suggestions != null ? new ArrayList<>(suggestions) : new ArrayList<>();
        if (!searchField.getText().trim().isEmpty()) {
            updateSuggestions();
        }
    }

    public void setPreferredWidth(int width) {
        setPreferredSize(new Dimension(width, getPreferredSize().height));
    }
}