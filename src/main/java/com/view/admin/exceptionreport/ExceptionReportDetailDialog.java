package com.view.admin.exceptionreport;

import com.components.BaseDialog;
import com.dao.ExceptionReportDAO;
import com.model.ExceptionReport;
import com.model.permission.AppPermission;
import com.permission.PermissionManager;
import com.service.AuthService;
import com.theme.AppColor;
import com.theme.AppFont;
import com.theme.AppRadius;
import com.theme.AppSpacing;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.time.format.DateTimeFormatter;

/**
 * Dialog CHI XEM chi tiết 1 báo cáo ngoại lệ - meta info (người gửi/ngày gửi,
 * người xử lý/ngày xử lý), nội dung đầy đủ (không bị cắt như ở cột bảng), và
 * nút "Đánh dấu đã xử lý" ngay tại đây nếu báo cáo còn PENDING và người xem
 * có quyền {@link AppPermission#EXCEPTION_REPORT_HANDLE} - tránh phải đóng
 * dialog rồi thao tác lại từ bảng.
 */
final class ExceptionReportDetailDialog extends JDialog {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ExceptionReportDAO exceptionReportDAO = new ExceptionReportDAO();
    private boolean handled = false;

    private ExceptionReportDetailDialog(Window owner, ExceptionReport report) {
        super(owner, "Chi tiết báo cáo ngoại lệ", ModalityType.APPLICATION_MODAL);
        setSize(560, 480);
        setMinimumSize(new Dimension(460, 400));
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());
        getContentPane().setBackground(AppColor.PAGE_BG);

        add(buildHeader(report), BorderLayout.NORTH);
        add(buildBody(report), BorderLayout.CENTER);
        add(buildFooter(report), BorderLayout.SOUTH);
    }

    // ------------------------------------------------------------------
    // Header: icon badge + title + trạng thái pill
    // ------------------------------------------------------------------

    private JPanel buildHeader(ExceptionReport report) {
        boolean pending = report.isPending();
        Color accent = pending ? AppColor.WARNING : AppColor.SUCCESS;
        FontAwesomeSolid icon = pending ? FontAwesomeSolid.EXCLAMATION_TRIANGLE : FontAwesomeSolid.CHECK_CIRCLE;

        JPanel header = new JPanel(new BorderLayout(AppSpacing.MD, 0));
        header.setOpaque(true);
        header.setBackground(AppColor.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AppColor.BORDER),
                new EmptyBorder(AppSpacing.LG, AppSpacing.XL, AppSpacing.LG, AppSpacing.XL)));

        JLabel iconLabel = new JLabel(FontIcon.of(icon, 18, accent));
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconLabel.setPreferredSize(new Dimension(44, 44));
        iconLabel.setOpaque(true);
        iconLabel.setBackground(soft(accent, 28));
        iconLabel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel textCol = new JPanel();
        textCol.setOpaque(false);
        textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Chi tiết báo cáo ngoại lệ");
        title.setFont(AppFont.DIALOG_TITLE);
        title.setForeground(AppColor.TEXT_TITLE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel("#" + report.getReportId());
        subtitle.setFont(AppFont.BODY);
        subtitle.setForeground(AppColor.TEXT_MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        textCol.add(title);
        textCol.add(Box.createVerticalStrut(3));
        textCol.add(subtitle);

        JLabel badge = new JLabel(pending ? "CHỜ XỬ LÝ" : "ĐÃ XỬ LÝ");
        badge.setFont(AppFont.SMALL_BOLD);
        badge.setForeground(accent);
        badge.setOpaque(true);
        badge.setBackground(soft(accent, 24));
        badge.setBorder(new EmptyBorder(6, 12, 6, 12));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, AppSpacing.MD, 0));
        left.setOpaque(false);
        left.add(iconLabel);
        left.add(textCol);

        header.add(left, BorderLayout.WEST);
        header.add(badge, BorderLayout.EAST);
        return header;
    }

    // ------------------------------------------------------------------
    // Body: meta grid (người gửi/ngày gửi/người xử lý/ngày xử lý) + nội dung
    // ------------------------------------------------------------------

    private JPanel buildBody(ExceptionReport report) {
        JPanel body = new JPanel(new BorderLayout(0, AppSpacing.MD));
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(AppSpacing.LG, AppSpacing.XL, AppSpacing.SM, AppSpacing.XL));

        JPanel grid = new JPanel(new GridLayout(2, 2, AppSpacing.MD, AppSpacing.MD));
        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));

        String sentAt = report.getCreatedAt() != null ? report.getCreatedAt().format(DATE_TIME_FORMAT) : "—";
        String handledAt = report.getHandledAt() != null ? report.getHandledAt().format(DATE_TIME_FORMAT) : "—";
        boolean isHandled = report.getHandledByName() != null;

        grid.add(metaCard(FontAwesomeSolid.USER, "Người gửi",
                report.getCreatedByName() != null ? report.getCreatedByName() : "—", AppColor.ACCENT));
        grid.add(metaCard(FontAwesomeSolid.CLOCK, "Ngày gửi", sentAt, AppColor.ACCENT));
        grid.add(metaCard(FontAwesomeSolid.USER_TIE, "Người xử lý",
                isHandled ? report.getHandledByName() : "—", isHandled ? AppColor.SUCCESS : AppColor.TEXT_MUTED));
        grid.add(metaCard(FontAwesomeSolid.CALENDAR_CHECK, "Ngày xử lý", handledAt,
                report.getHandledAt() != null ? AppColor.SUCCESS : AppColor.TEXT_MUTED));

        body.add(grid, BorderLayout.NORTH);
        body.add(contentCard(report.getContent()), BorderLayout.CENTER);
        return body;
    }

    private JPanel metaCard(FontAwesomeSolid icon, String label, String value, Color accent) {
        JPanel card = new RoundedPanel(AppRadius.MEDIUM);
        card.setLayout(new BorderLayout(AppSpacing.SM, 0));
        card.setBackground(AppColor.WHITE);
        card.setBorder(new EmptyBorder(AppSpacing.MD, AppSpacing.MD, AppSpacing.MD, AppSpacing.MD));

        JLabel iconLbl = new JLabel(FontIcon.of(icon, 14, accent));
        iconLbl.setVerticalAlignment(SwingConstants.TOP);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));

        JLabel lbl = new JLabel(label.toUpperCase());
        lbl.setFont(AppFont.SMALL_BOLD);
        lbl.setForeground(AppColor.TEXT_MUTED);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel val = new JLabel(value);
        val.setFont(AppFont.BODY_BOLD);
        val.setForeground(AppColor.TEXT_TITLE);
        val.setAlignmentX(Component.LEFT_ALIGNMENT);
        val.setToolTipText(value);

        text.add(lbl);
        text.add(Box.createVerticalStrut(4));
        text.add(val);

        card.add(iconLbl, BorderLayout.WEST);
        card.add(text, BorderLayout.CENTER);
        return card;
    }

    private JPanel contentCard(String content) {
        JPanel section = new JPanel(new BorderLayout());
        section.setOpaque(false);

        JLabel sectionTitle = new JLabel("Nội dung báo cáo");
        sectionTitle.setFont(AppFont.BODY_BOLD);
        sectionTitle.setForeground(AppColor.TEXT_TITLE);
        sectionTitle.setBorder(new EmptyBorder(AppSpacing.SM, 0, AppSpacing.SM, 0));

        JTextArea area = new JTextArea(content != null ? content : "");
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(AppFont.BODY);
        area.setForeground(AppColor.TEXT_PRIMARY);
        area.setBackground(AppColor.WHITE);
        area.setBorder(new EmptyBorder(AppSpacing.MD, AppSpacing.MD, AppSpacing.MD, AppSpacing.MD));
        area.setCaretPosition(0);

        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setBackground(AppColor.WHITE);
        scroll.getViewport().setBackground(AppColor.WHITE);

        JPanel card = new RoundedPanel(AppRadius.MEDIUM);
        card.setLayout(new BorderLayout());
        card.setBackground(AppColor.WHITE);
        card.setBorder(new EmptyBorder(2, 2, 2, 2));
        card.add(scroll, BorderLayout.CENTER);

        section.add(sectionTitle, BorderLayout.NORTH);
        section.add(card, BorderLayout.CENTER);
        return section;
    }

    // ------------------------------------------------------------------
    // Footer: Đóng (+ Đánh dấu đã xử lý nếu còn PENDING và có quyền)
    // ------------------------------------------------------------------

    private JPanel buildFooter(ExceptionReport report) {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        footer.setOpaque(true);
        footer.setBackground(AppColor.WHITE);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, AppColor.BORDER),
                new EmptyBorder(AppSpacing.MD, AppSpacing.XL, AppSpacing.MD, AppSpacing.XL)));

        JButton close = flatButton("Đóng", AppColor.CANCEL_BG, AppColor.CANCEL_HOVER, AppColor.TEXT_PRIMARY);
        close.addActionListener(e -> dispose());
        footer.add(close);
        getRootPane().setDefaultButton(close);

        boolean canHandle = report.isPending()
                && PermissionManager.getInstance().can(AppPermission.EXCEPTION_REPORT_HANDLE);
        if (canHandle) {
            JButton handleButton = flatButton("Đánh dấu đã xử lý", AppColor.SUCCESS, AppColor.SUCCESS, Color.WHITE);
            handleButton.addActionListener(e -> onHandleClicked(report));
            footer.add(handleButton);
            getRootPane().setDefaultButton(handleButton);
        }

        return footer;
    }

    private void onHandleClicked(ExceptionReport report) {
        boolean confirmed = BaseDialog.confirm(this, "Đánh dấu đã xử lý",
                "Xác nhận đã xử lý xong báo cáo ngoại lệ #" + report.getReportId() + "?",
                "Xác nhận", AppColor.SUCCESS, AppColor.SUCCESS, FontAwesomeSolid.CHECK_CIRCLE);
        if (!confirmed) return;

        int currentUserId = AuthService.getInstance().getCurrentUser().getUserId();
        if (exceptionReportDAO.handle(report.getReportId(), currentUserId)) {
            handled = true;
            BaseDialog.success(this, "Thành công", "Đã đánh dấu xử lý xong báo cáo #" + report.getReportId() + ".");
            dispose();
        } else {
            BaseDialog.error(this, "Không thể cập nhật", "Cập nhật trạng thái thất bại. Vui lòng thử lại.");
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private JButton flatButton(String text, Color bg, Color hover, Color fg) {
        JButton button = new JButton(text);
        button.setFont(AppFont.BUTTON);
        button.setForeground(fg);
        button.setBackground(bg);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setOpaque(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(new EmptyBorder(9, 20, 9, 20));
        button.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { button.setBackground(hover); }
            @Override public void mouseExited(MouseEvent e) { button.setBackground(bg); }
        });
        return button;
    }

    private static Color soft(Color base, int alpha) {
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);
    }

    /** Panel bo góc nhẹ, vẽ nền + viền (giống AuditLogDetailDialog). */
    private static class RoundedPanel extends JPanel {
        private final int radius;

        RoundedPanel(int radius) {
            this.radius = radius;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            RoundRectangle2D.Float shape = new RoundRectangle2D.Float(0, 0, w - 1, h - 1, radius, radius);
            g2.setColor(getBackground());
            g2.fill(shape);
            g2.setColor(AppColor.BORDER);
            g2.draw(shape);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Hien thi dialog chi tiet. Tra ve true neu bao cao vua duoc danh dau xu ly xong tu day
     *  (de ExceptionReportPanel biet ma reload lai bang). */
    static boolean show(Window owner, ExceptionReport report) {
        ExceptionReportDetailDialog dialog = new ExceptionReportDetailDialog(owner, report);
        dialog.setVisible(true);
        return dialog.handled;
    }
}