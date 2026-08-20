# Cấu hình SIMS với MySQL

## 1. Import database bằng phpMyAdmin

Mở `http://42.112.26.37:8080/phpmyadmin`, đăng nhập và import theo thứ tự:

1. `sql/mysql/SIMS_MySQL_Import/01_SIMS_Schema_MySQL.sql`
2. Chọn một trong hai:
   - Có dữ liệu demo: `03_SIMS_SampleData_MySQL.sql`
   - Chỉ có tài khoản admin: `00_RBAC_Admin_MySQL.sql`
3. `02_SIMS_Triggers_MySQL.sql` (luôn chạy cuối)

Tài khoản demo sau khi import file `03`: `admin / 123456`.

> Cảnh báo: file `03` dùng `TRUNCATE` và sẽ xóa dữ liệu hiện có trong các bảng SIMS.

## 2. Điền thông tin JDBC

Mở `merged.properties` và thay:

```properties
DB_URL=jdbc:mysql://42.112.26.37:3306/SIMS_DB?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Ho_Chi_Minh&useSSL=false&allowPublicKeyRetrieval=true
DB_USER=YOUR_MYSQL_USERNAME
DB_PASSWORD=YOUR_MYSQL_PASSWORD
```

`http://42.112.26.37:8080/phpmyadmin` chỉ là trang quản trị web. Nếu MySQL không mở cổng `3306` công khai, cần lấy **MySQL host**, **port**, **database name**, **username** và **password** từ quản trị máy chủ rồi sửa `DB_URL`.

## 3. Tạo lại cấu hình mã hóa

Sau khi Eclipse tải xong Maven dependencies và build project:

```bash
java -cp target/classes com.security.tool.ConfigTool genkey
java -cp target/classes com.security.tool.ConfigTool encrypt merged.properties secure-config.enc
```

Đặt key do lệnh `genkey` sinh ra vào biến môi trường `MYSHOP_CONFIG_KEY`, rồi đặt `secure-config.enc` cạnh file JAR hoặc trong `src/main/resources/`.

## 4. Kiểm tra kết nối

Chạy `com.Main` trong Eclipse. Nếu báo `Communications link failure` hoặc `Connection refused`, kiểm tra host/port MySQL và firewall. Nếu báo `Access denied`, kiểm tra `DB_USER`, `DB_PASSWORD` và quyền của user trên database `SIMS_DB`.
