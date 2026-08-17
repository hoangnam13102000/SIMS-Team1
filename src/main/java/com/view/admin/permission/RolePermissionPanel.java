package com.view.admin.permission;

import com.components.AppAlert;
import com.components.LoadingOverlay;
import com.components.SectionHeader;
import com.components.ToggleSwitch;
import com.core.log.AppLogger;
import com.dao.RolePermissionDAO;
import com.model.ActivityLog;
import com.model.Role;
import com.model.User;
import com.model.permission.AppPermission;
import com.model.permission.AppPermissionCatalog;
import com.model.permission.RolePermissions;
import com.service.AuthService;
import com.theme.AppColor;
import com.theme.AppFont;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Trang "Phân quyền vai trò" dành cho Admin - bật/tắt các {@link AppPermission}
 * mà từng {@link Role} được phép sử dụng trong hệ thống, lưu trực tiếp xuống
 * CSDL qua {@link RolePermissionDAO} (bảng RolePermissions), thay vì phải sửa
 * code + build lại như trước (xem {@link RolePermissions}).
 * <p>
 * Được đăng ký trong sidebar (AdminMainFrame) với quyền {@link AppPermission#RBAC_MANAGE}
 * - mặc định CHỈ Admin có quyền này (Role.ADMIN luôn nhận toàn bộ AppPermission.values()).
 * <p>
 * Role.ADMIN hiển thị ở dạng CHỈ ĐỌC (luôn toàn quyền) - tránh trường hợp Admin
 * lỡ tay bỏ/lưu thiếu quyền cho chính vai trò của mình rồi tự khoá mình khỏi
 * hệ thống, không còn ai đủ quyền vào lại để sửa (xem RolePermissions).
 */
public class RolePermissionPanel extends JPanel {

    /**
     * Cac Role duoc quan ly tren trang nay - CO Y LOAI {@link Role#CUSTOMER}.
     * Ly do: LoginFrame dieu huong "if (role != CUSTOMER) new AdminMainFrame();
     * else new ClientMainFrame();" - Customer KHONG BAO GIO mo AdminMainFrame,
     * va toan bo khu vuc com.view.client KHONG goi PermissionManager/
     * RolePermissions/AppPermission o dau ca. Vi vay bat/tat AppPermission cho
     * Role.CUSTOMER khong co tac dung gi trong thuc te - hien no ra chi gay
     * nham lan cho Admin (tuong minh dang cau hinh duoc quyen cho khach hang).
     */
    private static final Role[] MANAGED_ROLES = {
            Role.ADMIN, Role.SALES_MANAGER, Role.INVENTORY_MANAGER, Role.SALES_STAFF
    };

    private final RolePermissionDAO dao = new RolePermissionDAO();
    private final LoadingOverlay loadingOverlay = new LoadingOverlay("Đang tải phân quyền...");

    private final JPanel roleListPanel = new JPanel();
    private final JPanel permissionListPanel = new ScrollableFormPanel();
    private final JLabel currentRoleLabel = new JLabel();

    private JButton saveButton;
    private JButton resetButton;

    /** Quyen cua tung Role doc tu DB (hoac mac dinh neu DB loi) - nguon du lieu cho danh sach ben trai. */
    private Map<Role, Set<AppPermission>> roleDataCache = new EnumMap<>(Role.class);
    /** Quyen dang CHINH SUA tren UI cho Role dang chon (chua luu xuong DB cho toi khi bam "Lưu thay đổi"). */
    private final Set<AppPermission> workingSet = EnumSet.noneOf(AppPermission.class);

    private Role selectedRole = Role.SALES_MANAGER;

    public RolePermissionPanel() {
        setLayout(new BorderLayout());
        setBackground(AppColor.PAGE_BG);
        setBorder(new EmptyBorder(20, 24, 20, 24));

        SectionHeader header = new SectionHeader(FontAwesomeSolid.USER_SHIELD, AppColor.ACCENT,
                "Phân quyền vai trò",
                "Bật/tắt chức năng mà từng vai trò (Role) được phép sử dụng trong hệ thống");
        resetButton = header.addButton("Khôi phục mặc định", FontAwesomeSolid.UNDO,
                SectionHeader.ButtonStyle.OUTLINE, this::onResetClicked);
        saveButton = header.addButton("Lưu thay đổi", FontAwesomeSolid.SAVE,
                SectionHeader.ButtonStyle.PRIMARY, this::onSaveClicked);

        JPanel body = new JPanel(new BorderLayout(16, 0));
        body.setOpaque(false);
        body.add(buildRoleListCard(), BorderLayout.WEST);
        body.add(buildPermissionCard(), BorderLayout.CENTER);

        add(header, BorderLayout.NORTH);
        add(LoadingOverlay.attach(body, loadingOverlay), BorderLayout.CENTER);

        selectRole(selectedRole);
        loadAllAsync();
    }

    // ==================== Nạp dữ liệu ====================

    private void loadAllAsync() {
        loadingOverlay.start("Đang tải phân quyền...");
        setControlsEnabled(false);

        SwingWorker<Map<Role, Set<AppPermission>>, Void> worker = new SwingWorker<>() {
            @Override
            protected Map<Role, Set<AppPermission>> doInBackground() {
                Map<Role, Set<AppPermission>> map = new EnumMap<>(Role.class);
                for (Role role : MANAGED_ROLES) {
                    if (role == Role.ADMIN) {
                        map.put(role, EnumSet.allOf(AppPermission.class));
                        continue;
                    }
                    Set<AppPermission> fromDb = dao.getPermissionsByRole(role);
                    map.put(role, fromDb != null ? fromDb : RolePermissions.getDefaultAppPermissions(role));
                }
                return map;
            }

            @Override
            protected void done() {
                try {
                    roleDataCache = get();
                } catch (Exception ex) {
                    AppAlert.error(RolePermissionPanel.this,
                            "Không tải được dữ liệu phân quyền: " + ex.getMessage());
                }
                loadingOverlay.stop();
                setControlsEnabled(true);
                selectRole(selectedRole);
            }
        };
        worker.execute();
    }

    private void setControlsEnabled(boolean enabled) {
        saveButton.setEnabled(enabled);
        resetButton.setEnabled(enabled);
    }

    // ==================== Danh sách Role (trái) ====================

    private JPanel buildRoleListCard() {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setOpaque(true);
        card.setBackground(AppColor.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(14, 12, 14, 12)));
        card.setPreferredSize(new Dimension(240, 10));

        JLabel heading = new JLabel("VAI TRÒ");
        heading.setFont(AppFont.SMALL_BOLD);
        heading.setForeground(AppColor.TEXT_MUTED);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        heading.setBorder(new EmptyBorder(0, 6, 8, 0));
        card.add(heading);

        roleListPanel.setLayout(new BoxLayout(roleListPanel, BoxLayout.Y_AXIS));
        roleListPanel.setOpaque(false);
        roleListPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(roleListPanel);
        card.add(Box.createVerticalGlue());

        return card;
    }

    private void rebuildRoleList() {
        roleListPanel.removeAll();
        for (Role role : MANAGED_ROLES) {
            roleListPanel.add(buildRoleRow(role));
            roleListPanel.add(Box.createVerticalStrut(6));
        }
        roleListPanel.revalidate();
        roleListPanel.repaint();
    }

    private JPanel buildRoleRow(Role role) {
        boolean selected = role == selectedRole;
        int count = role == Role.ADMIN
                ? AppPermission.values().length
                : roleDataCache.getOrDefault(role, EnumSet.noneOf(AppPermission.class)).size();

        JPanel row = new JPanel(new BorderLayout(8, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                if (selected) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(AppColor.ACCENT_SOFT);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(9, 10, 9, 10));
        row.setCursor(new Cursor(Cursor.HAND_CURSOR));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));

        JLabel name = new JLabel(roleLabel(role));
        name.setFont(selected ? AppFont.BODY_BOLD : AppFont.BODY);
        name.setForeground(selected ? AppColor.ACCENT : AppColor.TEXT_PRIMARY);

        JLabel badge = new JLabel(count + " quyền");
        badge.setFont(AppFont.SMALL);
        badge.setForeground(AppColor.TEXT_MUTED);

        row.add(name, BorderLayout.WEST);
        row.add(badge, BorderLayout.EAST);

        row.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                selectRole(role);
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                if (!selected) {
                    row.setOpaque(true);
                    row.setBackground(AppColor.BG_LIGHTER);
                    row.repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (!selected) {
                    row.setOpaque(false);
                    row.repaint();
                }
            }
        });

        return row;
    }

    private static String roleLabel(Role role) {
        switch (role) {
            case ADMIN: return "Quản trị viên";
            case SALES_MANAGER: return "Quản lý bán hàng";
            case INVENTORY_MANAGER: return "Quản lý kho";
            case SALES_STAFF: return "Nhân viên bán hàng";
            case CUSTOMER: return "Khách hàng";
            default: return role.name();
        }
    }

    // ==================== Danh sách quyền (phải) ====================

    private JPanel buildPermissionCard() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);

        currentRoleLabel.setFont(AppFont.HEADING_MD);
        currentRoleLabel.setForeground(AppColor.TEXT_TITLE);
        currentRoleLabel.setBorder(new EmptyBorder(0, 4, 10, 0));

        permissionListPanel.setLayout(new BoxLayout(permissionListPanel, BoxLayout.Y_AXIS));
        permissionListPanel.setOpaque(false);
        permissionListPanel.setBorder(new EmptyBorder(2, 0, 12, 0));

        JScrollPane scroll = new JScrollPane(permissionListPanel);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);

        wrapper.add(currentRoleLabel, BorderLayout.NORTH);
        wrapper.add(scroll, BorderLayout.CENTER);
        return wrapper;
    }

    private void selectRole(Role role) {
        this.selectedRole = role;
        currentRoleLabel.setText("Quyền của vai trò: " + roleLabel(role));

        Set<AppPermission> current = roleDataCache.getOrDefault(role, EnumSet.noneOf(AppPermission.class));
        workingSet.clear();
        workingSet.addAll(current);

        rebuildRoleList();
        rebuildPermissionList();
    }

    private void rebuildPermissionList() {
        permissionListPanel.removeAll();
        boolean isAdmin = selectedRole == Role.ADMIN;

        if (isAdmin) {
            JLabel note = new JLabel("<html>Quản trị viên luôn có <b>toàn quyền hệ thống</b> để tránh trường hợp"
                    + " tự khoá quyền quản trị của chính mình. Muốn giới hạn chức năng, hãy tạo tài khoản"
                    + " với vai trò khác.</html>");
            note.setFont(AppFont.BODY);
            note.setForeground(AppColor.TEXT_MUTED);
            note.setAlignmentX(Component.LEFT_ALIGNMENT);
            note.setBorder(new EmptyBorder(4, 4, 16, 4));
            permissionListPanel.add(note);
        }

        // Quyen con cua cac "resource" Hàng hoá - hien trong dropdown, khong liet ke phang
        Set<AppPermission> nestedPermissions = EnumSet.of(
                AppPermission.USER_MANAGE, AppPermission.USER_EDIT, AppPermission.USER_VIEW,
                AppPermission.CUSTOMER_MANAGE, AppPermission.CUSTOMER_EDIT, AppPermission.CUSTOMER_VIEW,
                AppPermission.CATEGORY_MANAGE, AppPermission.CATEGORY_EDIT, AppPermission.CATEGORY_VIEW,
                AppPermission.PRODUCT_MANAGE, AppPermission.PRODUCT_EDIT, AppPermission.PRODUCT_VIEW,
                AppPermission.SUPPLIER_MANAGE, AppPermission.SUPPLIER_EDIT, AppPermission.SUPPLIER_VIEW,
                AppPermission.EXCEPTION_REPORT_VIEW, AppPermission.EXCEPTION_REPORT_CREATE,
                AppPermission.EXCEPTION_REPORT_HANDLE
        );

        Map<String, List<AppPermission>> byGroup = new LinkedHashMap<>();
        for (AppPermission permission : AppPermission.values()) {
            if (nestedPermissions.contains(permission)) {
                continue; // ve trong buildHangHoaResourceDropdowns
            }
            String group = AppPermissionCatalog.get(permission).group;
            byGroup.computeIfAbsent(group, g -> new ArrayList<>()).add(permission);
        }

        // Thu tu: Tổng quan -> Người dùng -> Hàng hoá -> Kho/Bán hàng -> Báo cáo -> Hệ thống
        boolean nguoiDungInserted = false;
        boolean hangHoaInserted = false;
        boolean baoCaoInserted = false;
        for (Map.Entry<String, List<AppPermission>> group : byGroup.entrySet()) {
            String key = group.getKey();
            if ("Người dùng".equals(key) || "Hàng hoá".equals(key) || "Báo cáo".equals(key)) {
                continue;
            }
            if (!nguoiDungInserted && !"Tổng quan".equals(key)) {
                permissionListPanel.add(buildNguoiDungGroupCard(isAdmin));
                permissionListPanel.add(Box.createVerticalStrut(12));
                nguoiDungInserted = true;
                permissionListPanel.add(buildHangHoaGroupCard(isAdmin));
                permissionListPanel.add(Box.createVerticalStrut(12));
                hangHoaInserted = true;
            }
            // Chen Báo cáo truoc "Hệ thống"
            if (!baoCaoInserted && "Hệ thống".equals(key)) {
                permissionListPanel.add(buildBaoCaoGroupCard(isAdmin));
                permissionListPanel.add(Box.createVerticalStrut(12));
                baoCaoInserted = true;
            }
            permissionListPanel.add(buildGroupCard(key, group.getValue(), isAdmin));
            permissionListPanel.add(Box.createVerticalStrut(12));
        }
        if (!nguoiDungInserted) {
            permissionListPanel.add(buildNguoiDungGroupCard(isAdmin));
            permissionListPanel.add(Box.createVerticalStrut(12));
        }
        if (!hangHoaInserted) {
            permissionListPanel.add(buildHangHoaGroupCard(isAdmin));
            permissionListPanel.add(Box.createVerticalStrut(12));
        }
        if (!baoCaoInserted) {
            permissionListPanel.add(buildBaoCaoGroupCard(isAdmin));
            permissionListPanel.add(Box.createVerticalStrut(12));
        }

        permissionListPanel.revalidate();
        permissionListPanel.repaint();
    }

    private JPanel buildGroupCard(String groupName, List<AppPermission> permissions, boolean isAdmin) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setOpaque(true);
        card.setBackground(AppColor.WHITE);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(14, 16, 6, 16)));

        JLabel title = new JLabel(groupName);
        title.setFont(AppFont.HEADING_MD);
        title.setForeground(AppColor.TEXT_TITLE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(title);
        card.add(Box.createVerticalStrut(6));

        for (int i = 0; i < permissions.size(); i++) {
            card.add(buildPermissionRow(permissions.get(i), isAdmin));
            if (i < permissions.size() - 1) {
                JSeparator sep = new JSeparator();
                sep.setForeground(AppColor.BORDER);
                sep.setAlignmentX(Component.LEFT_ALIGNMENT);
                card.add(sep);
            }
        }
        return card;
    }

    /**
     * Nhom "Báo cáo": Báo cáo ngoại lệ dạng dropdown (Xem / Gửi / Xử lý)
     * + các báo cáo thống kê (doanh thu, lợi nhuận, tồn kho) dạng toggle phẳng.
     */
    private JPanel buildBaoCaoGroupCard(boolean isAdmin) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setOpaque(true);
        card.setBackground(AppColor.WHITE);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(14, 16, 10, 16)));

        JLabel title = new JLabel("Báo cáo");
        title.setFont(AppFont.HEADING_MD);
        title.setForeground(AppColor.TEXT_TITLE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(title);

        JLabel hint = new JLabel("Báo cáo ngoại lệ: mở dropdown để bật Xem · Gửi · Xử lý");
        hint.setFont(AppFont.SMALL);
        hint.setForeground(AppColor.TEXT_MUTED);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(hint);
        card.add(Box.createVerticalStrut(8));

        card.add(buildExceptionReportDropdown(isAdmin));
        card.add(Box.createVerticalStrut(8));

        AppPermission[] reportViews = {
                AppPermission.REVENUE_REPORT_VIEW,
                AppPermission.PROFIT_REPORT_VIEW,
                AppPermission.STOCK_REPORT_VIEW
        };
        for (int i = 0; i < reportViews.length; i++) {
            card.add(buildPermissionRow(reportViews[i], isAdmin));
            if (i < reportViews.length - 1) {
                JSeparator sep = new JSeparator();
                sep.setForeground(AppColor.BORDER);
                sep.setAlignmentX(Component.LEFT_ALIGNMENT);
                card.add(sep);
            }
        }
        return card;
    }

    /**
     * Dropdown Báo cáo ngoại lệ: Chỉ xem / Gửi báo cáo / Xử lý báo cáo.
     */
    private JPanel buildExceptionReportDropdown(boolean isAdmin) {
        AppPermission viewPerm = AppPermission.EXCEPTION_REPORT_VIEW;
        AppPermission createPerm = AppPermission.EXCEPTION_REPORT_CREATE;
        AppPermission handlePerm = AppPermission.EXCEPTION_REPORT_HANDLE;

        JPanel wrap = new JPanel();
        wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
        wrap.setOpaque(false);
        wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrap.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(0, 0, 0, 0)));

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(true);
        body.setBackground(AppColor.BG_LIGHTER);
        body.setBorder(new EmptyBorder(4, 12, 8, 12));
        body.setVisible(false);
        body.setAlignmentX(Component.LEFT_ALIGNMENT);

        FontIcon chevron = FontIcon.of(FontAwesomeSolid.CHEVRON_DOWN, 12);
        chevron.setIconColor(AppColor.TEXT_MUTED);
        JLabel chevronLabel = new JLabel(chevron);

        JLabel summary = new JLabel(summarizeExceptionPerms(viewPerm, createPerm, handlePerm));
        summary.setFont(AppFont.SMALL);
        summary.setForeground(AppColor.TEXT_MUTED);

        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(10, 12, 10, 12));
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));

        JPanel textCol = new JPanel();
        textCol.setOpaque(false);
        textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));
        JLabel nameLabel = new JLabel("Báo cáo ngoại lệ");
        nameLabel.setFont(AppFont.BODY_BOLD);
        nameLabel.setForeground(AppColor.TEXT_PRIMARY);
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel descLabel = new JLabel("NV gửi báo cáo bất thường · QL xem và xử lý");
        descLabel.setFont(AppFont.SMALL);
        descLabel.setForeground(AppColor.TEXT_MUTED);
        descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        textCol.add(nameLabel);
        textCol.add(descLabel);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);
        right.add(summary);
        right.add(chevronLabel);

        header.add(textCol, BorderLayout.CENTER);
        header.add(right, BorderLayout.EAST);

        Runnable refreshSummary = () ->
                summary.setText(summarizeExceptionPerms(viewPerm, createPerm, handlePerm));

        body.add(buildNestedToggleRow("Chỉ xem", "Xem danh sách báo cáo, không gửi / xử lý",
                viewPerm, isAdmin, refreshSummary));
        JSeparator s1 = new JSeparator();
        s1.setForeground(AppColor.BORDER);
        body.add(s1);
        body.add(buildNestedToggleRow("Gửi báo cáo", "Tạo báo cáo ngoại lệ mới (NV bán hàng)",
                createPerm, isAdmin, refreshSummary));
        JSeparator s2 = new JSeparator();
        s2.setForeground(AppColor.BORDER);
        body.add(s2);
        body.add(buildNestedToggleRow("Xử lý báo cáo", "Đánh dấu đã xử lý báo cáo chờ xử lý",
                handlePerm, isAdmin, refreshSummary));

        header.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                boolean open = !body.isVisible();
                body.setVisible(open);
                FontIcon icon = FontIcon.of(
                        open ? FontAwesomeSolid.CHEVRON_UP : FontAwesomeSolid.CHEVRON_DOWN, 12);
                icon.setIconColor(AppColor.TEXT_MUTED);
                chevronLabel.setIcon(icon);
                wrap.revalidate();
                wrap.repaint();
                permissionListPanel.revalidate();
            }
        });

        wrap.add(header);
        wrap.add(body);
        return wrap;
    }

    private String summarizeExceptionPerms(AppPermission view, AppPermission create, AppPermission handle) {
        List<String> parts = new ArrayList<>();
        if (workingSet.contains(view)) parts.add("Xem");
        if (workingSet.contains(create)) parts.add("Gửi");
        if (workingSet.contains(handle)) parts.add("Xử lý");
        if (parts.isEmpty()) return "Chưa gán";
        return String.join(" · ", parts);
    }

    /**
     * Nhom "Người dùng": 2 resource (Tài khoản & NV / Khách hàng), mỗi resource
     * dropdown 3 toggle: Chỉ xem / Chỉ sửa / Quản lý đầy đủ.
     */
    private JPanel buildNguoiDungGroupCard(boolean isAdmin) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setOpaque(true);
        card.setBackground(AppColor.WHITE);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(14, 16, 10, 16)));

        JLabel title = new JLabel("Người dùng");
        title.setFont(AppFont.HEADING_MD);
        title.setForeground(AppColor.TEXT_TITLE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(title);

        JLabel hint = new JLabel("Bấm mũi tên để mở và bật/tắt từng quyền Xem · Sửa · Quản lý đầy đủ");
        hint.setFont(AppFont.SMALL);
        hint.setForeground(AppColor.TEXT_MUTED);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(hint);
        card.add(Box.createVerticalStrut(8));

        card.add(buildResourceDropdown(
                "Tài khoản & nhân viên",
                "Xem / sửa / thêm NV · khoá tài khoản đăng nhập",
                AppPermission.USER_VIEW,
                AppPermission.USER_EDIT,
                AppPermission.USER_MANAGE,
                isAdmin));
        card.add(Box.createVerticalStrut(6));
        card.add(buildResourceDropdown(
                "Khách hàng",
                "Xem / sửa / xoá mềm khách hàng",
                AppPermission.CUSTOMER_VIEW,
                AppPermission.CUSTOMER_EDIT,
                AppPermission.CUSTOMER_MANAGE,
                isAdmin));

        return card;
    }

    /**
     * Nhom "Hàng hoá": 3 resource (Danh mục / Sản phẩm / Nhà cung cấp), mỗi resource
     * là 1 hàng có nút mở dropdown với 3 toggle: Chỉ xem / Chỉ sửa / Quản lý đầy đủ.
     */
    private JPanel buildHangHoaGroupCard(boolean isAdmin) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setOpaque(true);
        card.setBackground(AppColor.WHITE);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(14, 16, 10, 16)));

        JLabel title = new JLabel("Hàng hoá");
        title.setFont(AppFont.HEADING_MD);
        title.setForeground(AppColor.TEXT_TITLE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(title);

        JLabel hint = new JLabel("Bấm mũi tên để mở và bật/tắt từng quyền Xem · Sửa · Quản lý đầy đủ");
        hint.setFont(AppFont.SMALL);
        hint.setForeground(AppColor.TEXT_MUTED);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(hint);
        card.add(Box.createVerticalStrut(8));

        card.add(buildResourceDropdown(
                "Danh mục",
                "Xem / sửa / thêm-xoá danh mục sản phẩm",
                AppPermission.CATEGORY_VIEW,
                AppPermission.CATEGORY_EDIT,
                AppPermission.CATEGORY_MANAGE,
                isAdmin));
        card.add(Box.createVerticalStrut(6));
        card.add(buildResourceDropdown(
                "Sản phẩm",
                "Xem / sửa / thêm-xoá thông tin sản phẩm, giá bán",
                AppPermission.PRODUCT_VIEW,
                AppPermission.PRODUCT_EDIT,
                AppPermission.PRODUCT_MANAGE,
                isAdmin));
        card.add(Box.createVerticalStrut(6));
        card.add(buildResourceDropdown(
                "Nhà cung cấp",
                "Xem / sửa / thêm-xoá nhà cung cấp",
                AppPermission.SUPPLIER_VIEW,
                AppPermission.SUPPLIER_EDIT,
                AppPermission.SUPPLIER_MANAGE,
                isAdmin));

        return card;
    }

    /**
     * 1 resource: hàng header (tên + summary + chevron), click để mở/đóng panel con
     * chứa 3 toggle: Chỉ xem / Chỉ sửa / Quản lý đầy đủ (thêm + xoá).
     */
    private JPanel buildResourceDropdown(String resourceName, String resourceDesc,
                                        AppPermission viewPerm, AppPermission editPerm,
                                        AppPermission managePerm, boolean isAdmin) {
        JPanel wrap = new JPanel();
        wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
        wrap.setOpaque(false);
        wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrap.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(0, 0, 0, 0)));

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(true);
        body.setBackground(AppColor.BG_LIGHTER);
        body.setBorder(new EmptyBorder(4, 12, 8, 12));
        body.setVisible(false);
        body.setAlignmentX(Component.LEFT_ALIGNMENT);

        body.add(buildNestedToggleRow("Chỉ xem", "Chỉ xem/tìm kiếm, không thêm/sửa/xoá",
                viewPerm, isAdmin));
        body.add(new JSeparator());
        body.add(buildNestedToggleRow("Chỉ sửa", "Sửa bản ghi đã có, không thêm mới / xoá",
                editPerm, isAdmin));
        body.add(new JSeparator());
        body.add(buildNestedToggleRow("Quản lý đầy đủ", "Thêm mới + sửa + xoá / đổi trạng thái",
                managePerm, isAdmin));

        FontIcon chevron = FontIcon.of(FontAwesomeSolid.CHEVRON_DOWN, 12);
        chevron.setIconColor(AppColor.TEXT_MUTED);
        JLabel chevronLabel = new JLabel(chevron);

        JLabel summary = new JLabel(summarizeResourcePerms(viewPerm, editPerm, managePerm));
        summary.setFont(AppFont.SMALL);
        summary.setForeground(AppColor.TEXT_MUTED);

        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(10, 12, 10, 12));
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));

        JPanel textCol = new JPanel();
        textCol.setOpaque(false);
        textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));
        JLabel nameLabel = new JLabel(resourceName);
        nameLabel.setFont(AppFont.BODY_BOLD);
        nameLabel.setForeground(AppColor.TEXT_PRIMARY);
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel descLabel = new JLabel(resourceDesc);
        descLabel.setFont(AppFont.SMALL);
        descLabel.setForeground(AppColor.TEXT_MUTED);
        descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        textCol.add(nameLabel);
        textCol.add(descLabel);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);
        right.add(summary);
        right.add(chevronLabel);

        header.add(textCol, BorderLayout.CENTER);
        header.add(right, BorderLayout.EAST);

        Runnable refreshSummary = () ->
                summary.setText(summarizeResourcePerms(viewPerm, editPerm, managePerm));

        // Re-wire toggles in body to also refresh summary - rebuild nested with callback
        body.removeAll();
        body.add(buildNestedToggleRow("Chỉ xem", "Chỉ xem/tìm kiếm, không thêm/sửa/xoá",
                viewPerm, isAdmin, refreshSummary));
        JSeparator s1 = new JSeparator();
        s1.setForeground(AppColor.BORDER);
        body.add(s1);
        body.add(buildNestedToggleRow("Chỉ sửa", "Sửa bản ghi đã có, không thêm mới / xoá",
                editPerm, isAdmin, refreshSummary));
        JSeparator s2 = new JSeparator();
        s2.setForeground(AppColor.BORDER);
        body.add(s2);
        body.add(buildNestedToggleRow("Quản lý đầy đủ", "Thêm mới + sửa + xoá / đổi trạng thái",
                managePerm, isAdmin, refreshSummary));

        header.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                boolean open = !body.isVisible();
                body.setVisible(open);
                FontIcon icon = FontIcon.of(
                        open ? FontAwesomeSolid.CHEVRON_UP : FontAwesomeSolid.CHEVRON_DOWN, 12);
                icon.setIconColor(AppColor.TEXT_MUTED);
                chevronLabel.setIcon(icon);
                wrap.revalidate();
                wrap.repaint();
                permissionListPanel.revalidate();
            }
        });

        wrap.add(header);
        wrap.add(body);
        return wrap;
    }

    private String summarizeResourcePerms(AppPermission view, AppPermission edit, AppPermission manage) {
        List<String> parts = new ArrayList<>();
        if (workingSet.contains(view)) parts.add("Xem");
        if (workingSet.contains(edit)) parts.add("Sửa");
        if (workingSet.contains(manage)) parts.add("Đầy đủ");
        if (parts.isEmpty()) return "Chưa gán";
        return String.join(" · ", parts);
    }

    private JPanel buildNestedToggleRow(String label, String description,
                                       AppPermission permission, boolean isAdmin) {
        return buildNestedToggleRow(label, description, permission, isAdmin, null);
    }

    private JPanel buildNestedToggleRow(String label, String description,
                                       AppPermission permission, boolean isAdmin,
                                       Runnable onChanged) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(new EmptyBorder(8, 4, 8, 4));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));

        JPanel textCol = new JPanel();
        textCol.setOpaque(false);
        textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));
        JLabel name = new JLabel(label);
        name.setFont(AppFont.BODY_BOLD);
        name.setForeground(AppColor.TEXT_PRIMARY);
        name.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel desc = new JLabel(description);
        desc.setFont(AppFont.SMALL);
        desc.setForeground(AppColor.TEXT_MUTED);
        desc.setAlignmentX(Component.LEFT_ALIGNMENT);
        textCol.add(name);
        textCol.add(desc);

        JComponent control;
        if (isAdmin) {
            FontIcon checkIcon = FontIcon.of(FontAwesomeSolid.CHECK_CIRCLE, 13);
            checkIcon.setIconColor(AppColor.SUCCESS);
            JLabel badge = new JLabel("Luôn bật", checkIcon, SwingConstants.LEFT);
            badge.setIconTextGap(6);
            badge.setFont(AppFont.SMALL_BOLD);
            badge.setForeground(AppColor.SUCCESS);
            control = badge;
        } else {
            ToggleSwitch toggle = new ToggleSwitch(workingSet.contains(permission));
            toggle.onChange(selected -> {
                if (selected) workingSet.add(permission);
                else workingSet.remove(permission);
                if (onChanged != null) onChanged.run();
            });
            control = toggle;
        }

        JPanel controlWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        controlWrap.setOpaque(false);
        controlWrap.add(control);

        row.add(textCol, BorderLayout.CENTER);
        row.add(controlWrap, BorderLayout.EAST);
        return row;
    }

    private JPanel buildPermissionRow(AppPermission permission, boolean isAdmin) {
        AppPermissionCatalog.Entry entry = AppPermissionCatalog.get(permission);

        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(new EmptyBorder(9, 0, 9, 0));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));

        JPanel textCol = new JPanel();
        textCol.setOpaque(false);
        textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));

        JLabel label = new JLabel(entry.label);
        label.setFont(AppFont.BODY_BOLD);
        label.setForeground(AppColor.TEXT_PRIMARY);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel desc = new JLabel(entry.description);
        desc.setFont(AppFont.SMALL);
        desc.setForeground(AppColor.TEXT_MUTED);
        desc.setAlignmentX(Component.LEFT_ALIGNMENT);

        textCol.add(label);
        textCol.add(desc);

        JComponent control;
        if (isAdmin) {
            FontIcon checkIcon = FontIcon.of(FontAwesomeSolid.CHECK_CIRCLE, 13);
            checkIcon.setIconColor(AppColor.SUCCESS);
            JLabel badge = new JLabel("Luôn bật", checkIcon, SwingConstants.LEFT);
            badge.setIconTextGap(6);
            badge.setFont(AppFont.SMALL_BOLD);
            badge.setForeground(AppColor.SUCCESS);
            control = badge;
        } else {
            ToggleSwitch toggle = new ToggleSwitch(workingSet.contains(permission));
            toggle.onChange(selected -> {
                if (selected) workingSet.add(permission);
                else workingSet.remove(permission);
            });
            control = toggle;
        }

        JPanel controlWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        controlWrap.setOpaque(false);
        controlWrap.add(control);

        row.add(textCol, BorderLayout.CENTER);
        row.add(controlWrap, BorderLayout.EAST);
        return row;
    }

    // ==================== Hành động ====================

    private void onResetClicked() {
        if (selectedRole == Role.ADMIN) {
            AppAlert.info(this, "Quản trị viên luôn có toàn quyền hệ thống, không cần khôi phục.");
            return;
        }
        workingSet.clear();
        workingSet.addAll(RolePermissions.getDefaultAppPermissions(selectedRole));
        rebuildPermissionList();
        AppAlert.info(this, "Đã khôi phục về danh sách quyền mặc định của vai trò \""
                + roleLabel(selectedRole) + "\". Bấm \"Lưu thay đổi\" để áp dụng.");
    }

    private void onSaveClicked() {
        if (selectedRole == Role.ADMIN) {
            AppAlert.info(this, "Quản trị viên luôn có toàn quyền hệ thống, không cần lưu.");
            return;
        }

        Role roleToSave = selectedRole;
        Set<AppPermission> toSave = EnumSet.copyOf(workingSet);

        setControlsEnabled(false);
        loadingOverlay.start("Đang lưu phân quyền...");

        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            @Override
            protected Boolean doInBackground() {
                return dao.savePermissionsForRole(roleToSave, toSave);
            }

            @Override
            protected void done() {
                loadingOverlay.stop();
                setControlsEnabled(true);

                boolean ok;
                try {
                    ok = Boolean.TRUE.equals(get());
                } catch (Exception ex) {
                    ok = false;
                }

                if (ok) {
                    roleDataCache.put(roleToSave, EnumSet.copyOf(toSave.isEmpty()
                            ? EnumSet.noneOf(AppPermission.class) : toSave));
                    RolePermissions.reload();
                    logChange(roleToSave);
                    rebuildRoleList();
                    AppAlert.success(RolePermissionPanel.this,
                            "Đã lưu phân quyền cho vai trò \"" + roleLabel(roleToSave)
                                    + "\". Áp dụng ngay cho các lần đăng nhập tiếp theo.");
                } else {
                    AppAlert.error(RolePermissionPanel.this, "Lưu phân quyền thất bại, vui lòng thử lại.");
                }
            }
        };
        worker.execute();
    }

    private void logChange(Role role) {
        User currentUser = AuthService.getInstance().getCurrentUser();
        String username = currentUser != null ? currentUser.getUsername() : "SYSTEM";
        AppLogger.getInstance().log(username, ActivityLog.ACTION_UPDATE, ActivityLog.ENTITY_ROLE_PERMISSION,
                "Cập nhật quyền cho vai trò \"" + roleLabel(role) + "\" (" + role.name() + ")");
    }

    /**
     * Panel co the cuon doc, LUON bang dung chieu rong viewport (khong bao
     * gio rong hon) - noi dung tu co gian theo cua so, khong bao gio phat
     * sinh thanh cuon ngang. Cung 1 ky thuat da dung trong SettingsPanel.
     */
    private static class ScrollableFormPanel extends JPanel implements Scrollable {
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 100;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}