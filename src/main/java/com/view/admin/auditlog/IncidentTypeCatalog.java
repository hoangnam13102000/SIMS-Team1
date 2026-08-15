package com.view.admin.auditlog;

import com.incident.IncidentSeverity;
import com.incident.IncidentType;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bảng tra cứu (catalog) mô tả các loại sự cố (IncidentType) mà hệ thống SIMS
 * có thể tự động phát hiện và ghi lại vào nhật ký sự cố (incidents/*.jsonl).
 *
 * Chỉ phục vụ hiển thị cho người dùng (nút "Loại sự cố hệ thống" trên trang
 * Nhật ký sự cố), KHÔNG ảnh hưởng tới logic ghi log thực tế trong package
 * com.incident. Khi thêm IncidentType mới, nên bổ sung thêm 1 dòng ở đây để
 * người dùng luôn thấy đủ danh sách.
 */
public final class IncidentTypeCatalog {

    private IncidentTypeCatalog() {
    }

    /** Một mục mô tả cho 1 IncidentType, dùng để hiển thị trên giao diện. */
    public static final class Entry {
        public final IncidentType type;
        public final String label;
        public final String description;
        public final IncidentSeverity typicalSeverity;
        public final FontAwesomeSolid icon;
        public final String raisedBy;

        private Entry(IncidentType type, String label, String description,
                      IncidentSeverity typicalSeverity, FontAwesomeSolid icon, String raisedBy) {
            this.type = type;
            this.label = label;
            this.description = description;
            this.typicalSeverity = typicalSeverity;
            this.icon = icon;
            this.raisedBy = raisedBy;
        }
    }

    private static final Map<IncidentType, Entry> ENTRIES = new LinkedHashMap<>();

    static {
        add(IncidentType.DB_CONNECTION_LOST, "Mất kết nối cơ sở dữ liệu",
                "Hệ thống kiểm tra kết nối MySQL định kỳ và không thể kết nối được sau nhiều lần thử "
                        + "liên tiếp - nghi ngờ server CSDL bị sập, mất mạng hoặc đang bị tấn công.",
                IncidentSeverity.CRITICAL, FontAwesomeSolid.PLUG, "DbHealthMonitor");

        add(IncidentType.DB_QUERY_ERROR, "Lỗi truy vấn dữ liệu",
                "Một câu lệnh SQL khi thực thi bị lỗi (sai cú pháp, vi phạm ràng buộc dữ liệu, "
                        + "hết thời gian chờ...).",
                IncidentSeverity.HIGH, FontAwesomeSolid.TIMES_CIRCLE, "Các lớp DAO");

        add(IncidentType.DB_CORRUPTION_SUSPECTED, "Nghi ngờ dữ liệu bị hỏng",
                "Dữ liệu đọc ra không nhất quán hoặc sai định dạng - nghi ngờ bảng dữ liệu hoặc "
                        + "file dữ liệu đã bị hỏng.",
                IncidentSeverity.CRITICAL, FontAwesomeSolid.EXCLAMATION_TRIANGLE, "Tầng dữ liệu");

        add(IncidentType.UNAUTHORIZED_ACCESS_SUSPECTED, "Nghi ngờ truy cập trái phép",
                "Phát hiện dấu hiệu đăng nhập hoặc thao tác bất thường, nghi ngờ có người cố truy cập "
                        + "hệ thống không đúng quyền hạn.",
                IncidentSeverity.CRITICAL, FontAwesomeSolid.SHIELD_ALT, "Module xác thực / phân quyền");

        add(IncidentType.SYSTEM_CRASH, "Ứng dụng gặp sự cố nghiêm trọng",
                "Chương trình gặp lỗi không xử lý được (crash) và phải đóng đột ngột hoặc khởi động lại.",
                IncidentSeverity.CRITICAL, FontAwesomeSolid.BOLT, "Main / vòng lặp ứng dụng");

        add(IncidentType.BACKUP_FAILED, "Sao lưu thất bại",
                "Tiến trình tự động sao lưu dữ liệu định kỳ không hoàn tất được.",
                IncidentSeverity.HIGH, FontAwesomeSolid.TIMES_CIRCLE, "BackupScheduler / BackupManager");

        add(IncidentType.BACKUP_SUCCEEDED, "Sao lưu thành công",
                "Bản sao lưu dữ liệu định kỳ đã được tạo thành công - ghi lại để đối chiếu khi cần khôi phục.",
                IncidentSeverity.LOW, FontAwesomeSolid.CHECK_CIRCLE, "BackupManager");

        add(IncidentType.RESTORE_PERFORMED, "Đã khôi phục dữ liệu",
                "Quản trị viên đã thực hiện khôi phục dữ liệu từ một bản sao lưu - luôn được ghi ở mức "
                        + "nghiêm trọng để dễ theo dõi vì đây là thao tác ảnh hưởng toàn bộ dữ liệu.",
                IncidentSeverity.CRITICAL, FontAwesomeSolid.UNDO, "BackupManager");

        add(IncidentType.RESTORE_FAILED, "Khôi phục dữ liệu thất bại",
                "Thao tác khôi phục dữ liệu từ bản sao lưu không thành công.",
                IncidentSeverity.CRITICAL, FontAwesomeSolid.EXCLAMATION_TRIANGLE, "BackupManager");

        add(IncidentType.CONFIG_ERROR, "Lỗi cấu hình hệ thống",
                "Tệp cấu hình (ví dụ secure-config.enc) bị thiếu, sai định dạng hoặc không đọc/giải mã "
                        + "được lúc khởi động.",
                IncidentSeverity.CRITICAL, FontAwesomeSolid.COGS, "Main / AppConfig");

        add(IncidentType.SECURITY_CHECK_FAILED, "Kiểm tra bảo mật thất bại",
                "Một bước kiểm tra bảo mật (xác thực, phân quyền, tính toàn vẹn cấu hình...) không vượt qua.",
                IncidentSeverity.HIGH, FontAwesomeSolid.SHIELD_ALT, "Module bảo mật");

        add(IncidentType.OTHER, "Sự cố khác",
                "Các sự kiện/cảnh báo khác của hệ thống không thuộc những nhóm trên, ví dụ ghi nhận khi "
                        + "kết nối cơ sở dữ liệu đã hồi phục sau khi bị mất.",
                IncidentSeverity.MEDIUM, FontAwesomeSolid.INFO_CIRCLE, "DbHealthMonitor và các module khác");
    }

    private static void add(IncidentType type, String label, String description,
                             IncidentSeverity typicalSeverity, FontAwesomeSolid icon, String raisedBy) {
        ENTRIES.put(type, new Entry(type, label, description, typicalSeverity, icon, raisedBy));
    }

    /** Danh sách đầy đủ các loại sự cố, theo đúng thứ tự khai báo ở trên. */
    public static Collection<Entry> all() {
        return ENTRIES.values();
    }

    public static Entry get(IncidentType type) {
        return ENTRIES.get(type);
    }
}