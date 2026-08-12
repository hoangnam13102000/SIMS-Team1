package com.view.admin.invoice;

import com.components.BaseDialog;
import com.dao.ProductDAO;
import com.dao.ReturnExchangeDAO;
import com.model.Invoice;
import com.model.InvoiceDetail;
import com.model.Product;
import com.model.ReturnExchange;
import com.model.ReturnExchangeDetail;
import com.service.AuthService;
import com.theme.AppColor;
import com.theme.AppFont;
import com.utils.NumberUtil;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.event.KeyEvent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dialog tao 1 yeu cau doi/tra hang cho 1 hoa don ACTIVE - mo tu nut
 * "Đổi / trả hàng" tren {@link InvoiceDetailDialog}.
 * <p>
 * Phan tren: chon Loai (Tra hang / Doi hang) + danh sach cac dong san pham
 * cua hoa don goc, moi dong co 1 JSpinner de nhap so luong khach TRA lai
 * (Direction=IN, gioi han boi so luong con co the tra - da tru cac yeu
 * cau PENDING/APPROVED truoc do, xem {@link ReturnExchangeDAO#getReturnableQuantities}).
 * <p>
 * Phan duoi (chi hien khi Loai = Doi hang): chon san pham moi + so luong
 * de them vao danh sach "hang doi" (Direction=OUT).
 * <p>
 * Ly do la bat buoc (R4). Don gia tri lon (xem
 * {@link ReturnExchangeDAO#APPROVAL_THRESHOLD}) se o trang thai "Chờ
 * duyệt" sau khi tao, con lai duoc tu dong duyet ngay (kho/hoa don goc
 * duoc trigger DB dieu chinh ngay lap tuc).
 */
public class ReturnExchangeDialog extends JDialog {

    private final Invoice invoice;
    private final ReturnExchangeDAO returnExchangeDAO;
    private final ProductDAO productDAO = new ProductDAO();

    private final Map<Integer, InvoiceDetail> invoiceLinesByProduct = new LinkedHashMap<>();
    private final Map<Integer, JSpinner> returnSpinners = new LinkedHashMap<>();
    private final Map<Integer, Integer> returnableQty;

    private JRadioButton typeReturn;
    private JRadioButton typeExchange;
    private JPanel exchangeSection;
    private JPanel exchangeListPanel;
    private JComboBox<Product> productCombo;
    private JSpinner exchangeQtySpinner;
    private JTextArea reasonArea;

    private final List<ReturnExchangeDetail> exchangeOutLines = new ArrayList<>();

    private boolean created = false;

    public ReturnExchangeDialog(Frame owner, Invoice invoice, List<InvoiceDetail> invoiceDetails,
                                 ReturnExchangeDAO returnExchangeDAO) {
        super(owner, "Đổi / trả hàng", Dialog.ModalityType.APPLICATION_MODAL);
        this.invoice = invoice;
        this.returnExchangeDAO = returnExchangeDAO;
        this.returnableQty = returnExchangeDAO.getReturnableQuantities(invoice.getInvoiceId());
        for (InvoiceDetail d : invoiceDetails) {
            invoiceLinesByProduct.put(d.getProductId(), d);
        }

        setSize(720, 700);
        setMinimumSize(new Dimension(620, 560));
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(AppColor.WHITE);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(invoiceDetails), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        setLocationRelativeTo(owner);
    }

    /** true neu dialog da tao yeu cau thanh cong (dung de InvoiceDetailDialog biet ma reload). */
    public boolean isCreated() { return created; }

    // ---------------------------------------------------------------
    // Header
    // ---------------------------------------------------------------

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(14, 0));
        header.setBackground(AppColor.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AppColor.BORDER),
                new EmptyBorder(18, 24, 18, 24)));

        FontIcon icon = FontIcon.of(FontAwesomeSolid.EXCHANGE_ALT, 18);
        icon.setIconColor(AppColor.ACCENT);
        JLabel iconBadge = new JLabel(icon, SwingConstants.CENTER);
        iconBadge.setPreferredSize(new Dimension(44, 44));
        iconBadge.setOpaque(true);
        iconBadge.setBackground(AppColor.ACCENT_BG_SOFT);

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel("Đổi / trả hàng - " + invoice.getInvoiceCode());
        titleLabel.setFont(AppFont.DIALOG_TITLE);
        titleLabel.setForeground(AppColor.TEXT_PRIMARY);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitleLabel = new JLabel("Ghi nhận hàng khách trả và/hoặc hàng đổi mới giao");
        subtitleLabel.setFont(AppFont.BODY);
        subtitleLabel.setForeground(AppColor.TEXT_MUTED);
        subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        titleBox.add(titleLabel);
        titleBox.add(Box.createVerticalStrut(4));
        titleBox.add(subtitleLabel);

        header.add(iconBadge, BorderLayout.WEST);
        header.add(titleBox, BorderLayout.CENTER);
        return header;
    }

    // ---------------------------------------------------------------
    // Body
    // ---------------------------------------------------------------

    private JScrollPane buildBody(List<InvoiceDetail> invoiceDetails) {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(AppColor.WHITE);
        content.setBorder(new EmptyBorder(18, 24, 18, 24));

        // ---- Loai ----
        JLabel typeLabel = sectionLabel("Loại yêu cầu");
        content.add(typeLabel);
        content.add(Box.createVerticalStrut(8));

        typeReturn = new JRadioButton("Trả hàng (khách trả lại, không lấy sản phẩm khác)", true);
        typeExchange = new JRadioButton("Đổi hàng (khách trả lại + lấy sản phẩm khác thay thế)");
        typeReturn.setOpaque(false);
        typeExchange.setOpaque(false);
        typeReturn.setFont(AppFont.BODY);
        typeExchange.setFont(AppFont.BODY);
        ButtonGroup group = new ButtonGroup();
        group.add(typeReturn);
        group.add(typeExchange);
        typeReturn.addActionListener(e -> exchangeSection.setVisible(false));
        typeExchange.addActionListener(e -> exchangeSection.setVisible(true));

        JPanel typeRow = new JPanel();
        typeRow.setLayout(new BoxLayout(typeRow, BoxLayout.Y_AXIS));
        typeRow.setOpaque(false);
        typeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        typeRow.add(typeReturn);
        typeRow.add(typeExchange);
        content.add(typeRow);
        content.add(Box.createVerticalStrut(16));

        // ---- Danh sach san pham trong hoa don (Direction=IN) ----
        content.add(sectionLabel("Sản phẩm khách trả lại"));
        content.add(Box.createVerticalStrut(8));
        content.add(buildInvoiceLinesPanel(invoiceDetails));
        content.add(Box.createVerticalStrut(18));

        // ---- Hang doi moi (chi hien khi Doi hang) ----
        exchangeSection = buildExchangeSection();
        exchangeSection.setVisible(false);
        exchangeSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(exchangeSection);
        content.add(Box.createVerticalStrut(18));

        // ---- Ly do ----
        content.add(sectionLabel("Lý do đổi/trả (bắt buộc)"));
        content.add(Box.createVerticalStrut(8));
        reasonArea = new JTextArea(3, 20);
        reasonArea.setFont(AppFont.BODY);
        reasonArea.setLineWrap(true);
        reasonArea.setWrapStyleWord(true);
        reasonArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(8, 10, 8, 10)));
        JScrollPane reasonScroll = new JScrollPane(reasonArea);
        reasonScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        reasonScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        content.add(reasonScroll);

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getViewport().setBackground(AppColor.WHITE);
        return scroll;
    }

    private JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(AppFont.BODY_BOLD);
        label.setForeground(AppColor.TEXT_PRIMARY);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JPanel buildInvoiceLinesPanel(List<InvoiceDetail> invoiceDetails) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(10, 12, 10, 12)));

        for (InvoiceDetail d : invoiceDetails) {
            int maxReturnable = Math.max(0, returnableQty.getOrDefault(d.getProductId(), 0));

            JPanel row = new JPanel(new BorderLayout(10, 0));
            row.setOpaque(false);
            row.setBorder(new EmptyBorder(6, 0, 6, 0));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

            JLabel nameLabel = new JLabel(d.getProductName()
                    + "  ·  đã bán " + d.getQuantity()
                    + "  ·  " + NumberUtil.formatThousands(d.getUnitPrice().longValue()) + " đ/sp");
            nameLabel.setFont(AppFont.BODY);
            nameLabel.setForeground(maxReturnable > 0 ? AppColor.TEXT_PRIMARY : AppColor.TEXT_MUTED);

            JSpinner spinner = new JSpinner(new SpinnerNumberModel(0, 0, maxReturnable, 1));
            spinner.setPreferredSize(new Dimension(70, 28));
            spinner.setEnabled(maxReturnable > 0);
            returnSpinners.put(d.getProductId(), spinner);

            JPanel spinnerBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            spinnerBox.setOpaque(false);
            JLabel maxLabel = new JLabel("SL trả  ");
            maxLabel.setFont(AppFont.SMALL_BOLD);
            maxLabel.setForeground(AppColor.TEXT_MUTED);
            spinnerBox.add(maxLabel);
            spinnerBox.add(spinner);
            if (maxReturnable == 0) {
                JLabel doneLabel = new JLabel("  (đã trả hết)");
                doneLabel.setFont(AppFont.SMALL_BOLD);
                doneLabel.setForeground(AppColor.TEXT_MUTED);
                spinnerBox.add(doneLabel);
            }

            row.add(nameLabel, BorderLayout.CENTER);
            row.add(spinnerBox, BorderLayout.EAST);
            panel.add(row);
        }
        return panel;
    }

    private JPanel buildExchangeSection() {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setOpaque(false);

        section.add(sectionLabel("Hàng đổi mới giao cho khách"));
        section.add(Box.createVerticalStrut(8));

        List<Product> activeProducts = productDAO.findAllActive();

        JPanel pickerRow = new JPanel(new BorderLayout(8, 0));
        pickerRow.setOpaque(false);
        pickerRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        pickerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        productCombo = new JComboBox<>(activeProducts.toArray(new Product[0]));
        productCombo.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(value == null ? "" :
                    value.getProductName() + "  (" + NumberUtil.formatThousands(value.getSellPrice().longValue())
                            + " đ, còn " + value.getStock() + ")");
            label.setOpaque(true);
            label.setBackground(isSelected ? AppColor.ACCENT_BG_SOFT : AppColor.WHITE);
            label.setFont(AppFont.BODY);
            label.setBorder(new EmptyBorder(4, 6, 4, 6));
            return label;
        });

        exchangeQtySpinner = new JSpinner(new SpinnerNumberModel(1, 1, 999, 1));
        exchangeQtySpinner.setPreferredSize(new Dimension(70, 28));

        JButton addButton = new JButton("Thêm");
        addButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        addButton.setFocusPainted(false);
        addButton.setBackground(AppColor.ACCENT_BG_SOFT);
        addButton.setForeground(AppColor.ACCENT);
        addButton.addActionListener(e -> addExchangeLine());

        JPanel rightBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        rightBox.setOpaque(false);
        rightBox.add(exchangeQtySpinner);
        rightBox.add(addButton);

        pickerRow.add(productCombo, BorderLayout.CENTER);
        pickerRow.add(rightBox, BorderLayout.EAST);
        section.add(pickerRow);
        section.add(Box.createVerticalStrut(8));

        exchangeListPanel = new JPanel();
        exchangeListPanel.setLayout(new BoxLayout(exchangeListPanel, BoxLayout.Y_AXIS));
        exchangeListPanel.setOpaque(false);
        exchangeListPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        exchangeListPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(8, 10, 8, 10)));
        section.add(exchangeListPanel);
        refreshExchangeList();

        return section;
    }

    private void addExchangeLine() {
        Product selected = (Product) productCombo.getSelectedItem();
        if (selected == null) return;
        int qty = (int) exchangeQtySpinner.getValue();
        if (qty > selected.getStock()) {
            BaseDialog.error(this, "Không đủ tồn kho",
                    "Sản phẩm \"" + selected.getProductName() + "\" chỉ còn " + selected.getStock() + " trong kho.");
            return;
        }
        exchangeOutLines.add(new ReturnExchangeDetail(
                selected.getProductId(), qty, ReturnExchangeDetail.DIRECTION_OUT, selected.getSellPrice()));
        // giu lai ten SP de hien thi (khong query lai) - gan tam qua setter public field khong co,
        // nen dung setProductName truc tiep
        exchangeOutLines.get(exchangeOutLines.size() - 1).setProductName(selected.getProductName());
        refreshExchangeList();
        // reset SL ve 1 sau khi them, tranh giu lai gia tri cu cho lan them tiep theo
        exchangeQtySpinner.setValue(1);
    }

    private void refreshExchangeList() {
        exchangeListPanel.removeAll();
        if (exchangeOutLines.isEmpty()) {
            JLabel empty = new JLabel("Chưa thêm sản phẩm đổi nào.");
            empty.setFont(AppFont.BODY);
            empty.setForeground(AppColor.TEXT_MUTED);
            exchangeListPanel.add(empty);
        } else {
            for (int i = 0; i < exchangeOutLines.size(); i++) {
                ReturnExchangeDetail line = exchangeOutLines.get(i);
                int index = i;

                JPanel row = new JPanel(new BorderLayout(10, 0));
                row.setOpaque(false);
                row.setBorder(new EmptyBorder(4, 0, 4, 0));
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

                JLabel label = new JLabel(line.getProductName() + "  x " + line.getQuantity()
                        + "  ·  " + NumberUtil.formatThousands(line.getLineTotal().longValue()) + " đ");
                label.setFont(AppFont.BODY);
                label.setForeground(AppColor.TEXT_PRIMARY);

                JButton removeButton = new JButton("Xóa");
                removeButton.setFont(AppFont.SMALL_BOLD);
                removeButton.setFocusPainted(false);
                removeButton.setForeground(AppColor.ERROR);
                removeButton.setBackground(AppColor.WHITE);
                removeButton.setBorderPainted(false);
                removeButton.addActionListener(e -> {
                    exchangeOutLines.remove(index);
                    refreshExchangeList();
                });

                row.add(label, BorderLayout.CENTER);
                row.add(removeButton, BorderLayout.EAST);
                exchangeListPanel.add(row);
            }
        }
        exchangeListPanel.revalidate();
        exchangeListPanel.repaint();
    }

    // ---------------------------------------------------------------
    // Footer
    // ---------------------------------------------------------------

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        footer.setBackground(AppColor.BG_LIGHT);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, AppColor.BORDER),
                new EmptyBorder(12, 24, 12, 24)));

        JButton cancelButton = new JButton("Hủy");
        cancelButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        cancelButton.setFocusPainted(false);
        cancelButton.setBackground(AppColor.BORDER);
        cancelButton.setForeground(AppColor.TEXT_PRIMARY);
        cancelButton.setBorder(new EmptyBorder(8, 18, 8, 18));
        cancelButton.addActionListener(e -> dispose());
        footer.add(cancelButton);

        JButton submitButton = new JButton("Tạo yêu cầu");
        submitButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        submitButton.setFocusPainted(false);
        submitButton.setBackground(AppColor.ACCENT);
        submitButton.setForeground(AppColor.WHITE);
        submitButton.setBorder(new EmptyBorder(8, 18, 8, 18));
        submitButton.addActionListener(e -> handleSubmit());
        footer.add(submitButton);

        getRootPane().setDefaultButton(submitButton);
        return footer;
    }

    private void handleSubmit() {
        boolean exchange = typeExchange.isSelected();
        String reason = reasonArea.getText();
        if (reason == null || reason.isBlank()) {
            BaseDialog.error(this, "Thiếu lý do", "Vui lòng nhập lý do đổi/trả hàng.");
            return;
        }

        List<ReturnExchangeDetail> details = new ArrayList<>();
        for (Map.Entry<Integer, JSpinner> entry : returnSpinners.entrySet()) {
            int qty = (int) entry.getValue().getValue();
            if (qty <= 0) continue;
            InvoiceDetail line = invoiceLinesByProduct.get(entry.getKey());
            ReturnExchangeDetail d = new ReturnExchangeDetail(
                    entry.getKey(), qty, ReturnExchangeDetail.DIRECTION_IN, line.getUnitPrice());
            d.setProductName(line.getProductName());
            details.add(d);
        }
        if (exchange) {
            details.addAll(exchangeOutLines);
        }

        if (details.isEmpty()) {
            BaseDialog.error(this, "Chưa chọn sản phẩm",
                    "Vui lòng nhập số lượng sản phẩm khách trả lại (và/hoặc hàng đổi nếu chọn \"Đổi hàng\").");
            return;
        }

        ReturnExchange header = new ReturnExchange();
        header.setInvoiceId(invoice.getInvoiceId());
        header.setType(exchange ? ReturnExchange.TYPE_EXCHANGE : ReturnExchange.TYPE_RETURN);
        header.setReason(reason.trim());
        header.setCreatedBy(AuthService.getInstance().getCurrentUser().getUserId());

        String error = returnExchangeDAO.createReturnExchange(header, details);
        if (error != null) {
            BaseDialog.error(this, "Không thể tạo yêu cầu", error);
            return;
        }

        created = true;
        String message = header.isRequiresApproval()
                ? "Đã tạo yêu cầu đổi/trả cho hóa đơn " + invoice.getInvoiceCode()
                        + ". Giá trị lớn nên cần Quản lý bán hàng duyệt trước khi cập nhật kho."
                : "Đã tạo và xử lý xong yêu cầu đổi/trả cho hóa đơn " + invoice.getInvoiceCode() + ".";
        // dung getOwner() (Frame chinh) lam anchor, KHONG dung "this": dialog nay se
        // dispose() ngay ben duoi, ma dispose() mot Window se keo theo dispose() cac
        // window no so huu (owned windows) - neu anchor vao "this" thi toast toast bi
        // huy theo ngay lap tuc, chua kip hien len nguoi dung da khong thay thong bao.
        BaseDialog.success(getOwner(), "Thành công", message);
        dispose();
    }
}