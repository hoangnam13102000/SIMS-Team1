package com.components.crud;


import com.theme.AppColor;
import com.google.gson.Gson;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.KeyEvent;

/**
 * Form dialog dùng chung cho add/edit/view của một entity.
 * <p>
 * Cách dùng: subclass triển khai các hook {@link #buildFields}, {@link #fillForm},
 * {@link #validateForm}, {@link #collectFormData}, {@link #persist}, rồi gọi
 * {@link #init()} ở CUỐI constructor của subclass (sau khi các field phụ trợ
 * như DAO/combo data đã sẵn sàng) — không gọi từ constructor của lớp cha vì
 * lúc đó field của subclass chưa được khởi tạo.
 */
public abstract class BaseFormDialog<T> extends JDialog {

    protected final CrudMode mode;
    protected final T editingEntity;
    private final String entityLabel;

    private static final Gson SNAPSHOT_GSON = new Gson();

    /**
     * Snapshot JSON cua editingEntity chup NGAY LUC MO DIALOG, truoc khi
     * collectFormData() ghi de len cung 1 tham chieu editingEntity. Dung lam
     * "oldValue" cho audit log khi EDIT - neu chup sau save() se bi mat du
     * lieu goc vi collectFormData() sua truc tiep tren editingEntity.
     * Null neu ADD (chua co entity goc).
     */
    protected final String originalSnapshotJson;

    private JPanel formPanel;
    private JLabel messageLabel;
    private JButton saveButton;
    private boolean saved = false;
    private T result;
    private CrudCallback<T> callback;

    /**
     * @param owner         cửa sổ cha (có thể null).
     * @param entityLabel   tên hiển thị của entity, dùng cho tiêu đề/thông báo
     *                      (vd "danh mục", "điện thoại").
     * @param mode          ADD / EDIT / VIEW.
     * @param editingEntity entity đang sửa, hoặc null nếu đang ADD.
     */
    protected BaseFormDialog(Frame owner, String entityLabel, CrudMode mode, T editingEntity) {
        super(owner, true);
        this.entityLabel = entityLabel;
        this.mode = mode;
        this.editingEntity = editingEntity;
        this.originalSnapshotJson = editingEntity != null ? SNAPSHOT_GSON.toJson(editingEntity) : null;
    }

    // ---------------------------------------------------------------
    // Hook bắt buộc - subclass phải triển khai
    // ---------------------------------------------------------------

    /** Thêm các field nhập liệu vào panel (dùng {@link #addTextField}, {@link #addComboBox}, {@link #addTextArea}...). */
    protected abstract void buildFields(JPanel panel);

    /** Đổ dữ liệu của entity đang sửa lên các field đã tạo ở {@link #buildFields}. */
    protected abstract void fillForm(T entity);

    /** Kiểm tra dữ liệu nhập. Trả về null nếu hợp lệ, ngược lại trả về thông báo lỗi. */
    protected abstract String validateForm();

    /** Gom dữ liệu từ các field thành entity để lưu. */
    protected abstract T collectFormData();

    /** Lưu entity xuống DB (insert nếu ADD, update nếu EDIT). Trả về true nếu thành công. */
    protected abstract boolean persist(T entity, CrudMode mode);

    // ---------------------------------------------------------------
    // Hook tùy chọn - subclass có thể override
    // ---------------------------------------------------------------

    protected int getDialogWidth() { return 420; }

    protected int getDialogHeight() { return 420; }

    // ---------------------------------------------------------------
    // Khởi tạo giao diện - gọi ở CUỐI constructor của subclass
    // ---------------------------------------------------------------

    protected void init() {
        setTitle((mode.isReadOnly() ? "Chi tiết " : mode == CrudMode.EDIT ? "Cập nhật " : "Thêm ") + entityLabel);
        setSize(getDialogWidth(), getDialogHeight());
        setResizable(false);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(AppColor.WHITE);

        add(buildHeader(), BorderLayout.NORTH);

        formPanel = new JPanel();
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
        formPanel.setBackground(AppColor.WHITE);
        formPanel.setBorder(new EmptyBorder(18, 24, 18, 24));
        buildFields(formPanel);

        JScrollPane scrollPane = new JScrollPane(formPanel);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(AppColor.WHITE);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);

        add(buildFooter(), BorderLayout.SOUTH);

        if (editingEntity != null) {
            fillForm(editingEntity);
        }

        if (mode.isReadOnly()) {
            setFieldsEnabled(formPanel, false);
            saveButton.setVisible(false);
        }

        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        setLocationRelativeTo(getOwner());
    }

    // ---------------------------------------------------------------
    // Header / Footer
    // ---------------------------------------------------------------

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(AppColor.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AppColor.BORDER),
                new EmptyBorder(18, 24, 18, 24)));

        JLabel titleLabel = new JLabel((mode.isReadOnly() ? "Chi tiết " : mode == CrudMode.EDIT ? "Cập nhật " : "Thêm ") + entityLabel);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 17));
        titleLabel.setForeground(AppColor.TEXT_PRIMARY);
        header.add(titleLabel, BorderLayout.CENTER);
        return header;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(AppColor.BG_LIGHT);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, AppColor.BORDER),
                new EmptyBorder(12, 24, 12, 24)));

        messageLabel = new JLabel(" ");
        messageLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        messageLabel.setForeground(AppColor.ERROR);
        footer.add(messageLabel, BorderLayout.WEST);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setBackground(footer.getBackground());

        JButton cancelButton = new JButton("Hủy");
        cancelButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        cancelButton.setFocusPainted(false);
        cancelButton.setBackground(AppColor.BORDER);
        cancelButton.setForeground(AppColor.TEXT_PRIMARY);
        cancelButton.setBorder(new EmptyBorder(8, 18, 8, 18));
        cancelButton.addActionListener(e -> dispose());
        buttons.add(cancelButton);

        saveButton = new JButton(mode == CrudMode.EDIT ? "Lưu thay đổi" : "Thêm mới");
        saveButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        saveButton.setFocusPainted(false);
        saveButton.setBackground(AppColor.ACCENT);
        saveButton.setForeground(Color.WHITE);
        saveButton.setBorder(new EmptyBorder(8, 18, 8, 18));
        saveButton.addActionListener(e -> onSaveClicked());
        saveButton.getModel().addChangeListener(e ->
                saveButton.setBackground(saveButton.getModel().isRollover() ? AppColor.ACCENT_HOVER : AppColor.ACCENT));
        buttons.add(saveButton);

        footer.add(buttons, BorderLayout.EAST);
        getRootPane().setDefaultButton(saveButton);
        return footer;
    }

    private void onSaveClicked() {
        String error = validateForm();
        if (error != null) {
            showMessage(error);
            return;
        }

        T data = collectFormData();
        saveButton.setEnabled(false);
        boolean ok;
        try {
            ok = persist(data, mode);
        } finally {
            saveButton.setEnabled(true);
        }

        if (ok) {
            this.result = data;
            this.saved = true;
            if (callback != null) {
                callback.onSaved(data, mode);
            }
            dispose();
        } else {
            showMessage("Không thể lưu " + entityLabel + ". Vui lòng thử lại.");
        }
    }

    // ---------------------------------------------------------------
    // Helper dựng field - dùng trong buildFields() của subclass
    // ---------------------------------------------------------------

    protected final JLabel fieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 12));
        label.setForeground(AppColor.TEXT_MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(0, 2, 4, 0));
        return label;
    }

    protected JTextField addTextField(JPanel panel, String label) {
        panel.add(fieldLabel(label));
        JTextField field = new JTextField();
        styleField(field);
        panel.add(field);
        panel.add(Box.createVerticalStrut(14));
        return field;
    }

    protected JTextArea addTextArea(JPanel panel, String label) {
        panel.add(fieldLabel(label));
        JTextArea area = new JTextArea(4, 20);
        area.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(new EmptyBorder(8, 10, 8, 10));

        JScrollPane scroll = new JScrollPane(area);
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
        scroll.setBorder(new LineBorder(AppColor.BORDER, 1, true));
        panel.add(scroll);
        panel.add(Box.createVerticalStrut(14));
        return area;
    }

    protected <E> JComboBox<E> addComboBox(JPanel panel, String label, E[] items) {
        panel.add(fieldLabel(label));
        JComboBox<E> combo = new JComboBox<>(items);
        combo.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        combo.setAlignmentX(Component.LEFT_ALIGNMENT);
        combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        combo.setBackground(AppColor.WHITE);
        panel.add(combo);
        panel.add(Box.createVerticalStrut(14));
        return combo;
    }

    private void styleField(JTextField field) {
        field.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        field.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(6, 10, 6, 10)));
    }

    private void setFieldsEnabled(Container container, boolean enabled) {
        for (Component c : container.getComponents()) {
            c.setEnabled(enabled);
            if (c instanceof Container) {
                setFieldsEnabled((Container) c, enabled);
            }
        }
    }

    /** Hiện thông báo lỗi/ghi chú ở góc dưới trái dialog. Truyền null/rỗng để xóa. */
    protected void showMessage(String message) {
        messageLabel.setText(message == null || message.isBlank() ? " " : message);
    }

    // ---------------------------------------------------------------
    // Callback / kết quả
    // ---------------------------------------------------------------

    /** Đăng ký callback được gọi sau khi lưu thành công. Vd: {@code dialog.onSaved(this::handleFormSaved);} */
    public void onSaved(CrudCallback<T> callback) {
        this.callback = callback;
    }

    public boolean isSaved() {
        return saved;
    }

    public T getResult() {
        return result;
    }
}