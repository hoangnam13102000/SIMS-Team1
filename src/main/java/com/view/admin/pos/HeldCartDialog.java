package com.view.admin.pos;

import com.components.AppAlert;
import com.model.HeldCart;
import com.service.HeldCartService;
import com.service.PosCartService;
import com.theme.AppColor;
import com.theme.AppFont;
import com.utils.NumberUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Danh sach gio POS dang tam giu trong ca hien tai cua nhan vien. */
public class HeldCartDialog extends JDialog {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final HeldCartService service;
    private final PosCartService cart;
    private final JTextField searchField = new JTextField();
    private final DefaultTableModel model;
    private final JTable table;
    private List<HeldCart> rows = List.of();
    private boolean restored;
    private HeldCart restoredCart;

    public HeldCartDialog(Frame owner, HeldCartService service, PosCartService cart) {
        super(owner, "Giỏ hàng tạm giữ", true);
        this.service = service;
        this.cart = cart;

        setSize(880, 500);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(0, 12));
        getContentPane().setBackground(AppColor.WHITE);

        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.setOpaque(false);
        top.setBorder(new EmptyBorder(16, 16, 0, 16));
        JLabel title = new JLabel("Giỏ đang tạm giữ trong ca hiện tại");
        title.setFont(AppFont.HEADING_MD);
        title.setForeground(AppColor.TEXT_TITLE);
        top.add(title, BorderLayout.NORTH);

        JPanel search = new JPanel(new BorderLayout(8, 0));
        search.setOpaque(false);
        search.setBorder(new EmptyBorder(10, 0, 0, 0));
        searchField.setToolTipText("Tìm theo mã phiếu, khách hàng hoặc ghi chú");
        searchField.addActionListener(e -> loadData());
        JButton searchBtn = new JButton("Tìm");
        searchBtn.addActionListener(e -> loadData());
        search.add(searchField, BorderLayout.CENTER);
        search.add(searchBtn, BorderLayout.EAST);
        top.add(search, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        String[] cols = {"Mã phiếu", "Khách hàng", "Mặt hàng", "Tạm tính", "Thời gian giữ", "Ghi chú"};
        model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JTable(model);
        table.setRowHeight(34);
        table.setFont(AppFont.BODY);
        table.getTableHeader().setFont(AppFont.SMALL_BOLD);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && table.getSelectedRow() >= 0) restoreSelected();
            }
        });
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(AppColor.BORDER));
        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        center.setBorder(new EmptyBorder(0, 16, 0, 16));
        center.add(scroll, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(0, 16, 16, 16));
        JButton refresh = new JButton("Làm mới");
        refresh.addActionListener(e -> loadData());
        JButton cancelHold = new JButton("Hủy phiếu");
        cancelHold.addActionListener(e -> cancelSelected());
        JButton restore = new JButton("Khôi phục giỏ");
        restore.setBackground(AppColor.ACCENT);
        restore.setForeground(Color.WHITE);
        restore.addActionListener(e -> restoreSelected());
        JButton close = new JButton("Đóng");
        close.addActionListener(e -> dispose());
        footer.add(refresh);
        footer.add(cancelHold);
        footer.add(restore);
        footer.add(close);
        add(footer, BorderLayout.SOUTH);

        loadData();
    }

    public boolean isRestored() { return restored; }
    public HeldCart getRestoredCart() { return restoredCart; }

    private void loadData() {
        rows = service.getMyHeldCarts(searchField.getText());
        model.setRowCount(0);
        for (HeldCart h : rows) {
            String customer = h.getCustomerLabelSnapshot() != null && !h.getCustomerLabelSnapshot().isBlank()
                    ? h.getCustomerLabelSnapshot() : "Khách lẻ";
            model.addRow(new Object[]{
                    h.getHoldCode(),
                    customer,
                    h.getItemCount(),
                    NumberUtil.formatThousands(h.getSubTotalSnapshot().longValue()) + " đ",
                    h.getHeldAt() != null ? h.getHeldAt().format(DATE_TIME) : "—",
                    h.getNote() != null ? h.getNote() : "—"
            });
        }
    }

    private HeldCart selected() {
        int row = table.getSelectedRow();
        return row >= 0 && row < rows.size() ? rows.get(row) : null;
    }

    private void restoreSelected() {
        HeldCart h = selected();
        if (h == null) {
            AppAlert.warning(this, "Hãy chọn một phiếu tạm giữ để khôi phục.");
            return;
        }
        HeldCartService.Result<HeldCart> result = service.restoreToCurrentCart(h.getHoldId(), cart);
        if (!result.isSuccess()) {
            AppAlert.error(this, "Không thể khôi phục", result.getMessage());
            return;
        }
        restored = true;
        restoredCart = result.getData();
        AppAlert.success(this, "Đã khôi phục giỏ", result.getMessage());
        dispose();
    }

    private void cancelSelected() {
        HeldCart h = selected();
        if (h == null) {
            AppAlert.warning(this, "Hãy chọn một phiếu tạm giữ để hủy.");
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this,
                "Hủy phiếu " + h.getHoldCode() + "? Giỏ này sẽ không thể khôi phục lại.",
                "Xác nhận hủy phiếu", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        HeldCartService.Result<HeldCart> result = service.cancel(h.getHoldId());
        if (!result.isSuccess()) {
            AppAlert.error(this, "Không thể hủy phiếu", result.getMessage());
            return;
        }
        AppAlert.success(this, "Đã hủy phiếu", result.getMessage());
        loadData();
    }
}
