# SIMS - Hệ Thống Quản Lý Bán Hàng & Kho

Sales and Inventory Management System — Ứng dụng desktop quản lý bán hàng và tồn kho cho chuỗi cửa hàng **Connect Mart**, xây dựng bằng Java Swing với cơ sở dữ liệu tập trung MySQL.

[![Java](https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.java.com/)
[![Swing](https://img.shields.io/badge/Java_Swing-Desktop_UI-437291?style=for-the-badge&logo=java&logoColor=white)](https://docs.oracle.com/javase/tutorial/uiswing/)
[![FlatLaf](https://img.shields.io/badge/FlatLaf-^3.7.1-2C2C2C?style=for-the-badge)](https://www.formdev.com/flatlaf/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0%2B-4479A1?style=for-the-badge&logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Maven](https://img.shields.io/badge/Maven-Build_Tool-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white)](https://maven.apache.org/)

[![Ikonli](https://img.shields.io/badge/Ikonli-^12.4.0-4A4A4A?style=for-the-badge)](https://kordamp.org/ikonli/)
[![Gson](https://img.shields.io/badge/Gson-^2.10.1-4285F4?style=for-the-badge&logo=google&logoColor=white)](https://github.com/google/gson)
[![Jakarta Mail](https://img.shields.io/badge/Jakarta_Mail-^2.0.1-EA4335?style=for-the-badge&logo=gmail&logoColor=white)](https://eclipse-ee4j.github.io/mail/)
[![JBCrypt](https://img.shields.io/badge/JBCrypt-^0.4-2C2C2C?style=for-the-badge)](https://www.mindrot.org/projects/jBCrypt/)

## Giới thiệu

Sau nhiều năm mở rộng chi nhánh, Connect Mart gặp khó khăn khi vẫn quản lý bán hàng và kho thủ công: dữ liệu sai lệch, thất thoát hàng hóa, thiếu báo cáo thống kê, không phân quyền rõ ràng. **SIMS** ra đời để giải quyết các vấn đề đó bằng cách:

- Tự động hóa quản lý bán hàng và tồn kho
- Giao diện trực quan, dễ thao tác
- Phân quyền rõ ràng theo 4 vai trò: **Admin**, **Quản lý bán hàng**, **Quản lý kho**, **Nhân viên bán hàng**
- Hỗ trợ đổi/trả hàng, hủy hóa đơn, cảnh báo tồn kho tối thiểu
- Báo cáo, biểu đồ xu hướng hỗ trợ ra quyết định kinh doanh

## Công nghệ sử dụng

| Thành phần        | Công nghệ                                       |
| ----------------- | ----------------------------------------------- |
| Ngôn ngữ          | Java 17                                         |
| Giao diện         | Java Swing + FlatLaf (theme Light/Dark)         |
| Icon              | Ikonli (FontAwesome 5)                          |
| Cơ sở dữ liệu     | MySQL 8.0+ (JDBC driver `mysql-connector-j`)    |
| Build tool        | Apache Maven                                    |
| Bảo mật mật khẩu  | JBCrypt (hash + salt)                           |
| Gửi email OTP     | Jakarta Mail (Gmail SMTP)                       |
| Serialize dữ liệu | Gson (snapshot JSON cho audit trail)            |

**Công cụ phát triển:**

[![Eclipse](https://img.shields.io/badge/Eclipse_IDE-Maven_Project-2C2255?style=for-the-badge&logo=eclipseide&logoColor=white)](https://www.eclipse.org/)
[![phpMyAdmin](https://img.shields.io/badge/phpMyAdmin-Database_UI-6C78AF?style=for-the-badge&logo=phpmyadmin&logoColor=white)](https://www.phpmyadmin.net/)

## Yêu cầu hệ thống

- JDK 17 trở lên
- Eclipse IDE (có hỗ trợ Maven/m2e)
- MySQL 8.0+ và phpMyAdmin/MySQL Workbench
- Maven (đi kèm Eclipse qua m2e)

## Cài đặt và chạy dự án

### Bước 1 — Clone / giải nén dự án

```bash
git clone <repo-url>
```

### Bước 2 — Thiết lập cơ sở dữ liệu MySQL

Mở phpMyAdmin tại `http://42.112.26.37:8080/phpmyadmin`, vào tab **Import** và chạy theo đúng thứ tự trong thư mục `sql/mysql/SIMS_MySQL_Import/`:

```
01_SIMS_Schema_MySQL.sql      -- Tạo database SIMS_DB và toàn bộ schema
03_SIMS_SampleData_MySQL.sql  -- Dữ liệu mẫu (tùy chọn, xóa dữ liệu cũ)
02_SIMS_Triggers_MySQL.sql    -- Hàm/trigger nghiệp vụ; luôn chạy sau dữ liệu mẫu
```

Nếu không muốn dữ liệu mẫu, chạy `01`, sau đó `00_RBAC_Admin_MySQL.sql`, rồi `02`.
Các file `.sql` ở ngay thư mục `sql/` là bản SQL Server cũ, chỉ giữ để đối chiếu.

### Bước 3 — Import dự án vào Eclipse

1. Mở Eclipse → `File > Import > Existing Maven Projects` → chọn thư mục `SIMS`
2. Đợi Eclipse tải dependencies qua Maven (m2e)
3. Build project để có `target/classes` — chạy `mvn compile`, hoặc dùng `Project > Build` trong Eclipse

### Bước 4 — Khai báo cấu hình gốc trong `merged.properties`

Mở file `merged.properties` ở thư mục gốc dự án, điền `DB_USER` và `DB_PASSWORD` MySQL được cấp. URL phpMyAdmin là giao diện web, không phải JDBC URL; ứng dụng mặc định thử kết nối MySQL tại `42.112.26.37:3306`. Nếu nhà cung cấp dùng host/port/database khác, sửa `DB_URL` tương ứng.

### Bước 5 — Sinh master key (genkey)

Ứng dụng đọc cấu hình từ file **đã mã hoá** `secure-config.enc`, không đọc trực tiếp `merged.properties`. Vì vậy cần sinh một master key AES-256 trước bằng class `ConfigTool` (`src/main/java/com/security/tool/ConfigTool.java`). Có 2 cách chạy, chọn 1:

**Cách A — Chạy ngay trong Eclipse (không cần mở terminal)**

1. Mở file `ConfigTool.java` trong Package Explorer
2. Chuột phải → `Run As > Java Application` (Eclipse sẽ tự thêm `target/classes` vào classpath giúp bạn)
3. Lần chạy đầu tiên chưa có tham số, Eclipse chỉ in ra hướng dẫn sử dụng — vào `Run > Run Configurations... > (tab) Arguments > Program arguments`, gõ `genkey`, rồi bấm `Run` lại

**Cách B — Chạy bằng Command Prompt / Terminal**
Mở Command Prompt (cmd/PowerShell/terminal), `cd` vào thư mục gốc dự án (nơi có `pom.xml`), rồi chạy:

```bash
java -cp target/classes com.security.tool.ConfigTool genkey
```

> Đây là lệnh gõ ở cửa sổ dòng lệnh của hệ điều hành (không phải gõ trong khung soạn code của Eclipse). Lệnh này chỉ chạy được sau khi project đã build (đã có thư mục `target/classes` — Eclipse tự tạo khi bạn save/build project).

Cả 2 cách đều in ra một **master key dạng Base64**. Set key này vào biến môi trường `MYSHOP_CONFIG_KEY`:

- Nếu dùng Cách A (chạy trong Eclipse): vào `Run Configurations... > (tab) Environment > New...` → Name: `MYSHOP_CONFIG_KEY`, Value: key vừa sinh ra
- Nếu dùng Cách B (terminal):

```bash
# Windows (cmd)
set MYSHOP_CONFIG_KEY=<key vừa sinh ra>

# Windows (PowerShell)
$env:MYSHOP_CONFIG_KEY = "<key vừa sinh ra>"

# Linux / macOS
export MYSHOP_CONFIG_KEY=<key vừa sinh ra>
```

> Lưu key này ở nơi riêng tư, an toàn (password manager). Không commit key lên Git — nếu mất key sẽ không giải mã lại được config cũ.

### Bước 6 — Mã hoá `merged.properties` thành `secure-config.enc`

Với biến môi trường `MYSHOP_CONFIG_KEY` đã được set (theo Cách A hoặc B ở Bước 5), đổi tham số chạy của `ConfigTool` sang lệnh `encrypt`:

- **Trong Eclipse**: `Run Configurations... > Arguments`, sửa Program arguments thành `encrypt merged.properties secure-config.enc`, rồi `Run`
- **Terminal**:

```bash
java -cp target/classes com.security.tool.ConfigTool encrypt merged.properties secure-config.enc
```

Sau khi mã hoá thành công:

1. Copy file `secure-config.enc` vừa tạo vào `src/main/resources/` (để được đóng gói cùng jar khi build), hoặc đặt cạnh file `.jar` khi triển khai thực tế.
2. Xoá file `merged.properties` gốc (dạng plaintext), không commit file này lên Git.

Có thể kiểm tra lại nội dung đã mã hoá đúng chưa bằng lệnh:

```bash
java -cp target/classes com.security.tool.ConfigTool decrypt secure-config.enc
```

### Bước 7 — Chạy ứng dụng

Trong Eclipse, đảm bảo biến môi trường `MYSHOP_CONFIG_KEY` đã được set cho Run Configuration (hoặc set ở cấp hệ điều hành), sau đó chạy file `src/main/java/com/Main.java` (Run As → Java Application).

## Sơ đồ cơ sở dữ liệu – ERD

Sơ đồ ERD của hệ thống SIMS thể hiện các bảng, khóa chính, khóa ngoại
và mối quan hệ giữa các bảng trong cơ sở dữ liệu.

🔗 [Xem sơ đồ ERD trực tuyến trên dbdiagram.io](https://dbdiagram.io/d/SIMS-6a68100ac3a90dd98dca30f9)


## Kiến trúc hệ thống (mô hình 3 lớp)

```
┌───────────────────────────────────────────────────────────┐
│                        VIEW LAYER                         │
│   Java Swing Forms (Admin / Sales / Inventory / Staff)    │
│   - Nhận input người dùng, hiển thị dữ liệu               │
└───────────────────────────┬───────────────────────────────┘
                            │ gọi
┌───────────────────────────▼───────────────────────────────┐
│                       SERVICE LAYER                       │
│   Xử lý nghiệp vụ (Business Logic)                        │
│   - Kiểm tra Business Rules, điều phối transaction        │
│   - Xác thực & phân quyền (RBAC)                          │
└───────────────────────────┬───────────────────────────────┘
                            │ gọi
┌───────────────────────────▼───────────────────────────────┐
│                         DAO LAYER                         │
│   Data Access Object – CRUD qua JDBC (mysql-connector-j)  │
└───────────────────────────┬───────────────────────────────┘
                            │
                    ┌───────▼────────┐
                    │    MySQL DB    │
                    │    SIMS_DB     │
                    └────────────────┘
```

**Nguyên tắc phân lớp:**

- **View**: chỉ chứa Swing components, sự kiện UI, không truy vấn DB trực tiếp
- **Service**: chứa toàn bộ logic nghiệp vụ, validate ràng buộc, xử lý transaction, sinh snapshot JSON (Gson)
- **DAO**: chỉ thao tác CSDL, trả về Model/Entity
- **Model/Entity**: POJO ánh xạ bảng dữ liệu (User, Product, Invoice, Warehouse, Supplier...)
- **Util/Common/Security**: JBCrypt, ConfigTool, gửi mail OTP

**Cấu trúc thư mục dự kiến:**

```
src/main/java/com/
├── Main.java
├── model/          # Entity/POJO
├── dao/            # Data Access Object
├── service/        # Business logic theo vai trò
├── view/           # Swing UI (theo vai trò)
├── security/       # JBCrypt, ConfigTool, mã hóa AES
├── util/           # Helper, Gson serialize, gửi mail
└── config/         # Đọc cấu hình từ secure-config.enc
```

---

## Phân công module cho từng thành viên (dự tính)

| Thành viên                        | Module phụ trách chính       | Chi tiết công việc                                                                                                                                                               |
| --------------------------------- | ---------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Hoàng Trung Nam** (Trưởng nhóm) | Core hệ thống + Module Admin | Thiết kế CSDL & ERD, Model dùng chung, Auth/Login/RBAC, ConfigTool, quản lý người dùng – danh mục – sản phẩm – nhà cung cấp – cấu hình hệ thống; điều phối tiến độ & review code |
| **Lê Hoa Trường Vũ**              | Module Nhân viên Bán hàng    | Tìm sản phẩm, xem trạng thái kho, tạo/hủy hóa đơn, đổi/trả hàng, gửi thông báo hết hàng, gửi báo cáo ngoại lệ                                                                    |
| **Trần Tài Phương**               | Module Quản lý Kho           | Nhập hàng vào kho, đối chiếu kho cuối ngày, xử lý báo cáo thiếu hàng, báo cáo & biểu đồ xu hướng tồn kho                                                                         |
| **Hà Minh Tuấn**                  | Module Quản lý Bán hàng      | Thống kê bán hàng hằng ngày, xử lý báo cáo ngoại lệ, biểu đồ xu hướng mua hàng, báo cáo lợi nhuận                                                                                |

**Công việc dùng chung (cả nhóm phối hợp):** thiết kế CSDL (Nam chủ trì, cả nhóm review), chuẩn hóa giao diện chung (FlatLaf, layout, icon), viết tài liệu/báo cáo, kiểm thử tích hợp giữa các module.
