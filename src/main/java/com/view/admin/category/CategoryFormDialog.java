package com.view.admin.category;

import com.components.category.CategoryCard;
import com.components.crud.BaseFormDialog;
import com.components.crud.CrudMode;
import com.dao.CategoryDAO;
import com.model.Category;
import com.theme.AppColor;
import com.theme.AppFont;
import com.theme.AppRadius;
import com.validation.FormValidator;
import com.validation.Rules;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.Box;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Dialog Thêm mới / Cập nhật 1 danh mục, dùng trong {@link CategoryPanel}.
 * Chỉ 2 trường: Tên danh mục (duy nhất, không trùng) và Trạng thái
 * (ACTIVE/DISABLED) - giống cấu trúc bảng Categories trong SIMS.sql.
 * <p>
 * Không hỗ trợ xóa cứng (xem {@link CategoryPanel#supportsDelete()}): danh
 * mục đang được sản phẩm tham chiếu (FOREIGN KEY) nên "Vô hiệu hóa" ở đây
 * (đổi Trạng thái = DISABLED) là cách an toàn để ẩn danh mục khỏi phía
 * khách hàng mà không vi phạm ràng buộc khóa ngoại.
 * <p>
 * UX được đồng bộ với {@link com.view.admin.permission.RoleFormDialog} (banner
 * ngữ cảnh, field bọc icon, kiểm tra trùng tên "chạy nền" có debounce, đếm ký
 * tự) và với {@link com.components.category.CategoryCard} phía khách hàng
 * (thẻ "Xem trước" dùng chung {@link CategoryCard#iconFor}/tintFor/iconColorFor
 * nên Admin thấy đúng icon/màu mà khách hàng sẽ nhìn thấy).
 */
public class CategoryFormDialog extends BaseFormDialog<Category> {

    private static final String[] STATUS_LABELS = {"Đang hoạt động", "Vô hiệu hóa"};
    private static final int MAX_NAME_LENGTH = 100;
    private static final int VALIDATION_DELAY_MS = 300;

    private final CategoryDAO categoryDAO;

    /** So san pham (moi trang thai) dang thuoc danh muc nay - null khi ADD hoac chua nap xong. */
    private Integer productCount;

    private JTextField categoryNameField;
    private JComboBox<String> statusCombo;
    private JLabel nameStatusIcon;
    private JLabel nameHintLabel;
    private JLabel nameCounterLabel;
    private PreviewBadge previewIcon;
    private JLabel previewNameLabel;
    private JLabel previewCountLabel;
    private JPanel disableWarningBanner;
    private JLabel disableWarningLabel;

    private Timer nameValidationTimer;

    public CategoryFormDialog(Frame owner, CrudMode mode, Category editingEntity, CategoryDAO categoryDAO) {
        super(owner, "danh mục", mode, editingEntity);
        this.categoryDAO = categoryDAO;
        init();
    }

    @Override
    protected int getDialogWidth() { return 480; }

    @Override
    protected int getDialogHeight() { return mode == CrudMode.EDIT ? 620 : 540; }

    @Override
    protected void buildFields(JPanel panel) {
        panel.add(buildInfoBanner());
        panel.add(Box.createVerticalStrut(16));

        if (mode == CrudMode.EDIT && editingEntity != null) {
            panel.add(buildMetaRow());
            panel.add(Box.createVerticalStrut(14));
        }

        // ---- Tên danh mục -------------------------------------------------
        panel.add(fieldLabel("Tên danh mục", true));
        JPanel nameWrapper = createIconTextFieldWrapper(FontAwesomeSolid.TAG);
        categoryNameField = (JTextField) nameWrapper.getClientProperty("field");
        installMaxLengthFilter(categoryNameField, MAX_NAME_LENGTH);
        panel.add(nameWrapper);
        panel.add(Box.createVerticalStrut(4));

        JPanel nameHintRow = new JPanel(new BorderLayout(4, 0));
        nameHintRow.setOpaque(false);
        nameHintRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        nameStatusIcon = new JLabel();
        nameStatusIcon.setPreferredSize(new Dimension(14, 14));
        nameHintLabel = hintLabel("Tên hiển thị cho khách hàng, không được trùng danh mục khác.");
        JPanel nameHintLeft = new JPanel(new BorderLayout(4, 0));
        nameHintLeft.setOpaque(false);
        nameHintLeft.add(nameStatusIcon, BorderLayout.WEST);
        nameHintLeft.add(nameHintLabel, BorderLayout.CENTER);
        nameCounterLabel = new JLabel("0/" + MAX_NAME_LENGTH);
        nameCounterLabel.setFont(AppFont.SMALL);
        nameCounterLabel.setForeground(AppColor.TEXT_MUTED);
        nameHintRow.add(nameHintLeft, BorderLayout.CENTER);
        nameHintRow.add(nameCounterLabel, BorderLayout.EAST);
        panel.add(nameHintRow);
        panel.add(Box.createVerticalStrut(16));

        categoryNameField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { onNameChanged(); }
            @Override public void removeUpdate(DocumentEvent e) { onNameChanged(); }
            @Override public void changedUpdate(DocumentEvent e) { onNameChanged(); }
        });

        // ---- Xem trước ------------------------------------------------------
        panel.add(fieldLabel("Xem trước"));
        panel.add(buildPreviewCard());
        panel.add(hintLabel("Đây là cách danh mục hiển thị cho khách hàng ở trang \"Danh mục\"."));
        panel.add(Box.createVerticalStrut(16));

        // ---- Trạng thái -----------------------------------------------------
        panel.add(fieldLabel("Trạng thái", true));
        JPanel statusWrapper = new JPanel(new BorderLayout());
        statusWrapper.setOpaque(false);
        statusWrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusWrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        FontIcon statusIcon = FontIcon.of(FontAwesomeSolid.CHECK_CIRCLE, 14);
        statusIcon.setIconColor(AppColor.TEXT_MUTED);
        JLabel statusIconLabel = new JLabel(statusIcon);
        statusIconLabel.setBorder(new EmptyBorder(0, 2, 0, 8));

        statusCombo = new JComboBox<>(STATUS_LABELS);
        statusCombo.setFont(AppFont.FIELD);
        statusCombo.setBackground(AppColor.WHITE);
        statusWrapper.add(statusIconLabel, BorderLayout.WEST);
        statusWrapper.add(statusCombo, BorderLayout.CENTER);
        panel.add(statusWrapper);

        // Danh muc moi luon o trang thai "Dang hoat dong" - chi cho phep
        // chon "Vo hieu hoa" khi Sua (giong Customer/Employee: khong ai vo
        // hieu hoa 1 thu vua tao ra).
        if (mode == CrudMode.ADD) {
            statusCombo.setEnabled(false);
            panel.add(Box.createVerticalStrut(4));
            panel.add(hintLabel("Danh mục mới luôn bắt đầu ở trạng thái \"Đang hoạt động\"."));
        } else {
            statusCombo.addActionListener(e -> updateDisableWarning());
        }

        if (mode == CrudMode.EDIT) {
            panel.add(Box.createVerticalStrut(10));
            disableWarningBanner = buildDisableWarningBanner();
            disableWarningBanner.setVisible(false);
            panel.add(disableWarningBanner);
        }
    }

    // ---------------------------------------------------------------
    // Banner ngữ cảnh trên cùng - đồng bộ phong cách RoleFormDialog
    // ---------------------------------------------------------------

    private JPanel buildInfoBanner() {
        JPanel banner = new JPanel(new BorderLayout(12, 0));
        banner.setOpaque(true);
        banner.setBackground(AppColor.ACCENT_BG_SOFT);
        banner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColor.ACCENT, 1, true),
                new EmptyBorder(12, 14, 12, 14)));
        banner.setAlignmentX(Component.LEFT_ALIGNMENT);
        banner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 76));

        JPanel iconWrap = new JPanel();
        iconWrap.setOpaque(false);
        iconWrap.setPreferredSize(new Dimension(32, 32));
        FontIcon icon = FontIcon.of(mode == CrudMode.EDIT ? FontAwesomeSolid.TAGS : FontAwesomeSolid.PLUS, 16);
        icon.setIconColor(AppColor.ACCENT);
        iconWrap.add(new JLabel(icon));

        String html = mode == CrudMode.ADD
                ? "<html><b style='color:" + hex(AppColor.TEXT_PRIMARY) + "'>Thêm danh mục mới</b><br/>"
                + "<span style='color:" + hex(AppColor.TEXT_SECONDARY) + "'>"
                + "Danh mục sẽ hiển thị ngay cho khách hàng sau khi lưu.</span></html>"
                : "<html><b style='color:" + hex(AppColor.TEXT_PRIMARY) + "'>Cập nhật danh mục</b><br/>"
                + "<span style='color:" + hex(AppColor.TEXT_SECONDARY) + "'>"
                + "Đổi tên hoặc trạng thái hiển thị cho khách hàng.</span></html>";
        JLabel text = new JLabel(html);
        text.setFont(AppFont.BODY);

        banner.add(iconWrap, BorderLayout.WEST);
        banner.add(text, BorderLayout.CENTER);
        return banner;
    }

    /** Hàng nhỏ hiển thị Mã danh mục + số sản phẩm đang thuộc danh mục (chỉ khi Sửa). */
    private JPanel buildMetaRow() {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(true);
        row.setBackground(AppColor.BG_LIGHTER);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        row.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(8, 12, 8, 12)));

        FontIcon hashIcon = FontIcon.of(FontAwesomeSolid.HASHTAG, 11);
        hashIcon.setIconColor(AppColor.TEXT_MUTED);
        JLabel idLabel = new JLabel("Mã danh mục " + editingEntity.getCategoryId(), hashIcon, SwingConstants.LEFT);
        idLabel.setIconTextGap(6);
        idLabel.setFont(AppFont.SMALL_BOLD);
        idLabel.setForeground(AppColor.TEXT_MUTED);
        row.add(idLabel, BorderLayout.WEST);

        FontIcon boxIcon = FontIcon.of(FontAwesomeSolid.BOXES, 11);
        boxIcon.setIconColor(AppColor.TEXT_MUTED);
        previewCountLabel = new JLabel("Đang tải...", boxIcon, SwingConstants.RIGHT);
        previewCountLabel.setIconTextGap(6);
        previewCountLabel.setFont(AppFont.SMALL_BOLD);
        previewCountLabel.setForeground(AppColor.TEXT_MUTED);
        row.add(previewCountLabel, BorderLayout.EAST);

        loadProductCountAsync();
        return row;
    }

    private void loadProductCountAsync() {
        new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() {
                return categoryDAO.countProducts(editingEntity.getCategoryId());
            }

            @Override
            protected void done() {
                try {
                    productCount = get();
                } catch (Exception ex) {
                    productCount = 0;
                }
                if (previewCountLabel != null) {
                    previewCountLabel.setText(productCount == 0
                            ? "Chưa có sản phẩm"
                            : productCount + " sản phẩm thuộc danh mục");
                }
                updateDisableWarning();
            }
        }.execute();
    }

    // ---------------------------------------------------------------
    // Thẻ "Xem trước" - dùng chung icon/màu với CategoryCard (khách hàng)
    // ---------------------------------------------------------------

    private JPanel buildPreviewCard() {
        JPanel card = new JPanel(new BorderLayout(12, 0));
        card.setOpaque(true);
        card.setBackground(AppColor.WHITE);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(10, 12, 10, 12)));

        previewIcon = new PreviewBadge();
        previewIcon.setPreferredSize(new Dimension(40, 40));

        JPanel textCol = new JPanel();
        textCol.setOpaque(false);
        textCol.setLayout(new javax.swing.BoxLayout(textCol, javax.swing.BoxLayout.Y_AXIS));
        previewNameLabel = new JLabel("Tên danh mục");
        previewNameLabel.setFont(AppFont.HEADING_MD);
        previewNameLabel.setForeground(AppColor.TEXT_TITLE);
        previewNameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel captionLabel = new JLabel("Hiển thị trên trang \"Danh mục\" của khách hàng");
        captionLabel.setFont(AppFont.SMALL);
        captionLabel.setForeground(AppColor.TEXT_MUTED);
        captionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        textCol.add(previewNameLabel);
        textCol.add(captionLabel);

        card.add(previewIcon, BorderLayout.WEST);
        card.add(textCol, BorderLayout.CENTER);
        return card;
    }

    /** Icon badge bo goc dung mau/icon giong het {@link CategoryCard} phia khach hang. */
    private static final class PreviewBadge extends JPanel {
        private String categoryName = "";

        PreviewBadge() {
            setOpaque(false);
            setLayout(new java.awt.GridBagLayout());
        }

        void setCategoryName(String name) {
            this.categoryName = name;
            removeAll();
            FontIcon icon = FontIcon.of(CategoryCard.iconFor(name), 18);
            icon.setIconColor(CategoryCard.iconColorFor(name));
            add(new JLabel(icon));
            revalidate();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(CategoryCard.tintFor(categoryName));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), AppRadius.MEDIUM, AppRadius.MEDIUM);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private void updatePreview() {
        String name = categoryNameField.getText();
        String trimmed = name == null ? "" : name.trim();
        previewIcon.setCategoryName(trimmed);
        previewNameLabel.setText(trimmed.isEmpty() ? "Tên danh mục" : trimmed);
        previewNameLabel.setForeground(trimmed.isEmpty() ? AppColor.TEXT_MUTED : AppColor.TEXT_TITLE);
    }

    // ---------------------------------------------------------------
    // Cảnh báo khi chọn "Vô hiệu hóa" 1 danh mục đang có sản phẩm
    // ---------------------------------------------------------------

    private JPanel buildDisableWarningBanner() {
        JPanel banner = new JPanel(new BorderLayout(10, 0));
        banner.setOpaque(true);
        banner.setBackground(AppColor.WARNING_BG);
        banner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColor.WARNING, 1, true),
                new EmptyBorder(10, 12, 10, 12)));
        banner.setAlignmentX(Component.LEFT_ALIGNMENT);
        banner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));

        FontIcon icon = FontIcon.of(FontAwesomeSolid.EXCLAMATION_TRIANGLE, 15);
        icon.setIconColor(AppColor.WARNING);
        banner.add(new JLabel(icon), BorderLayout.WEST);

        disableWarningLabel = new JLabel();
        disableWarningLabel.setFont(AppFont.SMALL);
        banner.add(disableWarningLabel, BorderLayout.CENTER);
        return banner;
    }

    /** Chi hien banner canh bao khi dang Sua, chon "Vo hieu hoa", VA danh muc dang co san pham. */
    private void updateDisableWarning() {
        if (disableWarningBanner == null || statusCombo == null) return;
        boolean disabling = statusCombo.getSelectedIndex() == 1;
        boolean hasProducts = productCount != null && productCount > 0;
        boolean show = disabling && hasProducts;
        if (show) {
            disableWarningLabel.setText("<html><b style='color:" + hex(AppColor.WARNING) + "'>Sẽ ảnh hưởng " + productCount + " sản phẩm.</b> "
                    + "<span style='color:" + hex(AppColor.TEXT_SECONDARY) + "'>Toàn bộ sản phẩm thuộc danh mục này sẽ ngừng bán "
                    + "(kể cả tại quầy POS) cho đến khi kích hoạt lại.</span></html>");
        }
        disableWarningBanner.setVisible(show);
        disableWarningBanner.revalidate();
    }

    // ---------------------------------------------------------------
    // Kiểm tra trùng tên "chạy nền" có debounce - đồng bộ RoleFormDialog
    // ---------------------------------------------------------------

    private void onNameChanged() {
        updatePreview();
        int len = categoryNameField.getText() != null ? categoryNameField.getText().length() : 0;
        nameCounterLabel.setText(len + "/" + MAX_NAME_LENGTH);
        nameCounterLabel.setForeground(len >= MAX_NAME_LENGTH ? AppColor.ERROR : AppColor.TEXT_MUTED);
        scheduleNameValidation();
    }

    private void scheduleNameValidation() {
        nameStatusIcon.setIcon(null);
        if (nameValidationTimer != null && nameValidationTimer.isRunning()) {
            nameValidationTimer.restart();
            return;
        }
        nameValidationTimer = new Timer(VALIDATION_DELAY_MS, e -> validateNameInBackground());
        nameValidationTimer.setRepeats(false);
        nameValidationTimer.start();
    }

    private void validateNameInBackground() {
        final String name = categoryNameField.getText() != null ? categoryNameField.getText().trim() : "";
        final int excludeId = editingEntity != null ? editingEntity.getCategoryId() : -1;

        if (name.isEmpty()) {
            SwingUtilities.invokeLater(() -> {
                nameStatusIcon.setIcon(null);
                nameHintLabel.setText("Tên hiển thị cho khách hàng, không được trùng danh mục khác.");
                nameHintLabel.setForeground(AppColor.TEXT_MUTED);
            });
            return;
        }

        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                return categoryDAO.nameExistsExcluding(name, excludeId);
            }

            @Override
            protected void done() {
                boolean duplicate;
                try {
                    duplicate = get();
                } catch (Exception ex) {
                    return; // giu nguyen hint hien tai neu loi, validateForm() luc Luu se bat lai
                }
                FontIcon icon = duplicate
                        ? FontIcon.of(FontAwesomeSolid.TIMES_CIRCLE, 13)
                        : FontIcon.of(FontAwesomeSolid.CHECK_CIRCLE, 13);
                icon.setIconColor(duplicate ? AppColor.ERROR : AppColor.SUCCESS);
                nameStatusIcon.setIcon(icon);
                nameHintLabel.setText(duplicate
                        ? "Tên \"" + name + "\" đã tồn tại — hãy chọn tên khác."
                        : "Tên hợp lệ, có thể sử dụng.");
                nameHintLabel.setForeground(duplicate ? AppColor.ERROR : AppColor.SUCCESS);
            }
        }.execute();
    }

    // ---------------------------------------------------------------
    // Field bọc icon - đồng bộ RoleFormDialog
    // ---------------------------------------------------------------

    private JPanel createIconTextFieldWrapper(FontAwesomeSolid iconKey) {
        JPanel wrapper = new JPanel(new BorderLayout(0, 0));
        wrapper.setOpaque(true);
        wrapper.setBackground(AppColor.WHITE);
        wrapper.setBorder(new LineBorder(AppColor.FIELD_BORDER, 1, true));
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        FontIcon icon = FontIcon.of(iconKey, 14);
        icon.setIconColor(AppColor.TEXT_MUTED);
        JLabel iconLabel = new JLabel(icon);
        iconLabel.setBorder(new EmptyBorder(0, 8, 0, 6));
        wrapper.add(iconLabel, BorderLayout.WEST);

        JTextField field = new JTextField();
        field.setFont(AppFont.FIELD);
        field.setForeground(AppColor.TEXT_PRIMARY);
        field.setBackground(AppColor.WHITE);
        field.setCaretColor(AppColor.ACCENT);
        field.setBorder(new EmptyBorder(6, 2, 6, 8));
        wrapper.add(field, BorderLayout.CENTER);

        wrapper.putClientProperty("field", field);
        return wrapper;
    }

    private static String hex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    /** Chan khong cho go/dan qua {@code maxLength} ky tu - phan hoi UX ngay luc go, khong doi den Luu moi bao loi. */
    private static void installMaxLengthFilter(JTextField field, int maxLength) {
        ((javax.swing.text.AbstractDocument) field.getDocument()).setDocumentFilter(new javax.swing.text.DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offset, String string, javax.swing.text.AttributeSet attr)
                    throws javax.swing.text.BadLocationException {
                if (string == null) return;
                int over = fb.getDocument().getLength() + string.length() - maxLength;
                super.insertString(fb, offset, over > 0 ? string.substring(0, Math.max(0, string.length() - over)) : string, attr);
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, javax.swing.text.AttributeSet attrs)
                    throws javax.swing.text.BadLocationException {
                if (text == null) {
                    super.replace(fb, offset, length, null, attrs);
                    return;
                }
                int over = fb.getDocument().getLength() - length + text.length() - maxLength;
                super.replace(fb, offset, length, over > 0 ? text.substring(0, Math.max(0, text.length() - over)) : text, attrs);
            }
        });
    }

    // ---------------------------------------------------------------
    // Hook BaseFormDialog
    // ---------------------------------------------------------------

    @Override
    protected void fillForm(Category entity) {
        categoryNameField.setText(entity.getCategoryName());
        statusCombo.setSelectedIndex(entity.isActive() ? 0 : 1);
        updatePreview();
    }

    @Override
    protected String validateForm() {
        int excludeId = editingEntity != null ? editingEntity.getCategoryId() : -1;

        FormValidator validator = new FormValidator();

        validator.field(categoryNameField.getText())
                .required("Vui lòng nhập tên danh mục.")
                .maxLength(MAX_NAME_LENGTH, "Tên danh mục không được vượt quá " + MAX_NAME_LENGTH + " ký tự.")
                .rule(Rules.custom(v -> !categoryDAO.nameExistsExcluding(v.trim(), excludeId),
                        "Tên danh mục này đã tồn tại."));

        return validator.validate();
    }

    @Override
    protected Category collectFormData() {
        Category category = editingEntity != null ? editingEntity : new Category();
        category.setCategoryName(categoryNameField.getText().trim());
        category.setStatus(statusCombo.getSelectedIndex() == 1 ? "DISABLED" : "ACTIVE");
        return category;
    }

    @Override
    protected boolean persist(Category entity, CrudMode mode) {
        return mode == CrudMode.ADD ? categoryDAO.insertCategory(entity) : categoryDAO.updateCategory(entity);
    }
}