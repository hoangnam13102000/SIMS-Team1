# SIMS - UI quản lý hóa đơn + chuông thông báo

Patch này chỉ sửa Java, không có migration SQL.

## 1. Quản lý hóa đơn responsive

- Cột `Thao tác` được thu gọn còn đủ chỗ cho 2 icon Xem/PDF và giữ nhìn thấy khi cửa sổ thu nhỏ.
- Các cột dữ liệu có min-width responsive để ưu tiên cột `Thao tác` thay vì đẩy nó ra ngoài vùng nhìn.
- Thanh lọc hóa đơn có chế độ compact khi panel hẹp:
  - ô tìm kiếm thu từ 320 -> 250 px;
  - 2 ô ngày thu từ 130 -> 112 px;
  - nút `Quét QR HĐ` chuyển thành `QR` nhưng vẫn giữ icon + tooltip.
- Khi panel rộng trở lại, UI tự trở về kích thước đầy đủ.
- Mục tiêu: `Quét QR` không đè `Tổng cộng`, và `Thao tác` không mất khi thu nhỏ.

## 2. Chuông cho thông báo

Dùng lại `NotificationSound.playDing()` + `ChimePlayer` đã có sẵn trong project.

- Tất cả toast đi qua `AppAlert.success/error/warning/info` đều phát chuông.
- `NotificationSettings.isSoundEnabled()` vẫn được tôn trọng: tắt "Âm thanh thông báo" thì toast vẫn hiện nhưng không phát tiếng.
- Các `JOptionPane.showMessageDialog(...)` legacy loại INFO/WARNING/ERROR cũng được hook toàn app để phát cùng tiếng chuông.
- Dialog xác nhận / nhập liệu (QUESTION/PLAIN) không phát chuông để tránh gây ồn khi thao tác bình thường.
- Order/return/stock poller vốn đã dùng `NotificationSound`, không bị thay đổi nghiệp vụ.

## 3. Test nhanh

### Invoice UI
1. Mở `Quản lý hóa đơn` ở cửa sổ rộng -> `Quét QR HĐ`, `Tổng cộng`, `Thao tác` đều thấy.
2. Thu nhỏ cửa sổ -> nút scan đổi thành `QR`, không đè `Tổng cộng`.
3. Cột `Thao tác` vẫn thấy icon con mắt + PDF và bấm được.
4. Phóng rộng lại -> nút scan trở về `Quét QR HĐ`.

### Notification sound
1. Bật `Âm thanh thông báo` trong cài đặt.
2. Tạo một thao tác sinh `AppAlert.success` (vd copy mã hóa đơn) -> toast + 1 tiếng ding.
3. Thử cảnh báo/lỗi -> toast + ding.
4. Thử màn legacy dùng `JOptionPane.showMessageDialog` -> ding.
5. Tắt `Âm thanh thông báo` -> các thông báo vẫn hiện nhưng không có ding.

## 4. Eclipse

- `Project -> Clean...`
- `Maven -> Update Project... -> Force Update`

Không commit/push trước khi test UI và âm thanh xong.
