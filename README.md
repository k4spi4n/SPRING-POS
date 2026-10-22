# WinMart Warehouse Management System (WinMart WMS)
> **Đề tài cuối kỳ / Khóa luận tốt nghiệp**: Hệ thống Quản lý Kho Chuỗi Siêu Thị WinMart trên nền tảng **Spring Boot (Java 21)**.

![Java 21](https://img.shields.io/badge/Java-21_LTS-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3.4-brightgreen.svg)
![Spring Security](https://img.shields.io/badge/Spring_Security-JWT-blue.svg)
![Spring Data JPA](https://img.shields.io/badge/Spring_Data-JPA-yellow.svg)
![OpenAPI 3](https://img.shields.io/badge/API_Docs-Swagger_UI-green.svg)
![Database](https://img.shields.io/badge/Database-H2_|_MySQL-lightgrey.svg)

---

## 📌 Giới Thiệu Đề Tài
WinMart (và WinMart+) là chuỗi bán lẻ hàng đầu tại Việt Nam với lưu lượng hàng hóa luân chuyển khổng lồ hàng ngày. Kho hàng của hệ thống WinMart phải giải quyết các bài toán đặc thù của ngành bán lẻ thực phẩm:
- **Quản lý theo Lô sản xuất và Hạn sử dụng (Batch/Lot & Expiry Date)**.
- **Thuật toán xuất kho FEFO (First Expired, First Out)**: Ưu tiên xuất các lô hàng có hạn sử dụng gần nhất trước nhằm ngăn ngừa hư hỏng và cận date lưu kho.
- **Truy vết biến động kho (Audit Trail)** và kiểm soát mức tồn kho an toàn (Safety Stock).

Hệ thống được thiết kế theo tiêu chuẩn đồ án chất lượng cao, **làm nổi bật các thế mạnh kỹ thuật cốt lõi của Spring Boot** (IoC/DI, Spring Data JPA, Spring Security + JWT, Declarative Transactions `@Transactional`, Centralized Exception Handling, OpenAPI Swagger) nhưng giữ phạm vi tinh gọn, dễ bảo vệ và demo trực quan.

---

## 🏛️ Kiến Trúc Tổng Thể & Công Nghệ

### 1. Tech Stack
- **Ngôn ngữ**: Java 21 LTS
- **Framework**: Spring Boot 3.3.4
- **Security**: Spring Security 6 + Stateless JWT (JJWT 0.12.6)
- **ORM & Data**: Spring Data JPA, Hibernate ORM
- **Database**:
  - H2 Database (In-Memory, profile `dev`, không cần cài đặt DB)
  - Hỗ trợ sẵn cấu hình MySQL 8 qua profile `mysql`
- **Documentation**: Springdoc OpenAPI 3 (Swagger UI tích hợp xác thực Bearer Token)
- **Tiện ích**: Lombok, Jakarta Bean Validation

### 2. Mô hình phân tầng (3-Tier Layered Architecture)
```text
Controller (REST API + Validation)
   └── Service Layer (Business Logic + @Transactional + FEFO Engine)
         └── Repository Layer (Spring Data JPA + Custom Queries)
               └── Database (H2 / MySQL)
```

---

## ⚡ Các Tính Năng Nổi Bật

1. **Bảo mật & Phân quyền Role-based (RBAC)**:
   - 3 cấp độ: `ROLE_ADMIN`, `ROLE_MANAGER` (Thủ kho), `ROLE_STAFF` (Nhân viên).
   - Bảo mật tầng method bằng `@PreAuthorize`.
   - Chỉ ADMIN được gọi `POST /api/v1/auth/register` để tạo tài khoản và chọn vai trò. Đăng nhập qua `/api/v1/auth/login` để lấy JWT; thiếu token trả 401, STAFF/MANAGER đăng ký trả 403.
2. **Quản lý Hàng hóa & Ngành hàng**:
   - Quản lý mã SKU, mã vạch (Barcode), giá vốn, giá bán, đơn vị tính và định mức tồn kho tối thiểu.
3. **Nhập kho Inbound (Goods Receipt)**:
   - Tiếp nhận đơn hàng từ Nhà cung cấp (Vinamilk, Masan, Acecook...).
   - Tự động sinh lô hàng (`ProductBatch`), ghi nhận NSX, HSD và tăng tồn kho trong `@Transactional`.
4. **Xuất kho Outbound theo FEFO (Goods Issue)**:
   - Tự động phân bổ số lượng xuất dựa trên hạn sử dụng của từng lô hàng (date gần nhất xuất trước).
   - Tự động chặn xuất âm kho và ném ngoại lệ chuẩn hóa nếu không đủ hàng tồn.
5. **Cảnh báo Hàng Cận Date & Tồn An Toàn**:
   - Lọc nhanh các lô hàng có hạn dùng dưới 15 ngày / 30 ngày.
   - Báo cáo các mặt hàng có tồn kho dưới định mức tối thiểu.
6. **Nhật ký Biến động kho (Audit Trail)**:
   - Ghi lại vết lịch sử mọi giao dịch (Inbound, Outbound) kèm số dư tồn kho sau mỗi bước.
7. **Báo cáo Thống kê & Dashboard**:
   - Thống kê tổng quan KPIs: Tổng mặt hàng, tổng giá trị kho hàng, số phiếu nhập/xuất, cảnh báo cận date, cảnh báo tồn thấp (tính bằng truy vấn tổng hợp tại CSDL).
8. **Hủy phiếu nhập/xuất (bù trừ giao dịch)**: hoàn/trừ tồn đúng lô, ghi bút toán điều chỉnh, mỗi phiếu chỉ hủy được một lần.
9. **Quy tắc hạn sử dụng**: không điều chuyển hàng hết hạn ra cửa hàng, chỉ xuất hủy hàng hết hạn, không nhập lô đã quá hạn.

### 🛡️ Các Thành Phần Cắt Ngang (Cross-Cutting Concerns)
Chi tiết, bảng mã lỗi và cách kiểm chứng xem [Tài liệu 04](docs/04_BAO_MAT_GIAM_SAT_KIEM_TOAN.md).

| Mảng | Nội dung chính |
| :--- | :--- |
| **Logging** | Logback rolling (`logs/app.log`, `error.log`, `audit.log`); `X-Request-Id` + username + IP trong MDC cho mọi dòng log; access-log từng request. |
| **Security** | JWT có `jti`/issuer, đăng xuất thu hồi token, khóa tạm thời khi sai mật khẩu nhiều lần, tạo tài khoản chỉ ADMIN, vô hiệu hóa user có hiệu lực tức thì, 401/403 dạng JSON, CORS theo cấu hình, secret qua biến môi trường. |
| **Transaction** | `readOnly` mặc định, khóa bi quan `FOR UPDATE` chống xuất lố khi đồng thời, khóa theo thứ tự chống deadlock, `CHECK (quantity >= 0)`, sự kiện `AFTER_COMMIT` cảnh báo tồn thấp. |
| **Performance monitoring** | Actuator + Micrometer: Timer mọi phương thức service, metric nghiệp vụ, log service/request/SQL chậm, health probes. |
| **Auditing** | `createdBy/updatedBy` tự động (JPA Auditing) + bảng bất biến `audit_logs` & `audit.log` ghi ai làm gì (kể cả thất bại, đăng nhập, bị từ chối); tra cứu `GET /api/v1/audit-logs` (ADMIN). |
| **Exception handling** | Một cấu trúc lỗi duy nhất (`errorCode`, `path`, `requestId`), ánh xạ đầy đủ 4xx, lỗi 500 không lộ chi tiết nội bộ. |

> **Triển khai thật:** dùng profile `mysql` (mặc định nếu không chọn profile), cung cấp `JWT_SECRET`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` qua biến môi trường. H2 Console và Swagger bị tắt, không nạp dữ liệu hay tài khoản demo. Chỉ bật `dev` trên máy phát triển.

---

## 🚀 Hướng Dẫn Khởi Chạy

### Cách 1: Chạy ngay lập tức với H2 Database (Khuyến nghị khi chấm điểm)
Chỉ cần máy có cài **Java 21** và **Maven**:
```bash
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```
> Hệ thống sẽ tự động tạo bảng và nạp sẵn dữ liệu mẫu thực tế về sản phẩm WinMart, tài khoản test và các lô hàng cận date.
> Profile `dev` tự sinh JWT secret ngẫu nhiên nếu chưa đặt `JWT_SECRET`; token cũ hết hiệu lực sau khi khởi động lại. Dữ liệu H2 chỉ tồn tại trong lần chạy đó.

- **Truy cập Swagger UI (Thao tác chính)**: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
- **Truy cập H2 Console**: [http://localhost:8080/h2-console](http://localhost:8080/h2-console) (JDBC URL: `jdbc:h2:mem:winmart_wms_db`, User: `sa`, Password: để trống)

### Cách 2: Triển khai với MySQL 8 (profile `mysql`)
Tạo database `winmart_wms` và tài khoản MySQL phù hợp, rồi đặt biến môi trường (ví dụ Bash):
```bash
export DB_URL='jdbc:mysql://localhost:3306/winmart_wms?serverTimezone=UTC'
export DB_USERNAME='winmart_app'
export DB_PASSWORD='<mat-khau-MySQL-cua-ban>'
export JWT_SECRET="$(openssl rand -base64 48)"
export CORS_ALLOWED_ORIGINS='https://pos.example.com'
mvn spring-boot:run "-Dspring-boot.run.profiles=mysql"
```

Trong PowerShell, dùng `$env:TEN_BIEN = 'gia-tri'` thay cho `export`. Cấp một `JWT_SECRET` ngẫu nhiên, giữ ổn định giữa các lần chạy và các instance, tối thiểu 32 byte UTF-8. Không lưu secret/mật khẩu thật vào repo.

Không chọn profile cũng dùng `mysql` với cùng yêu cầu biến môi trường. Thiếu biến bắt buộc hoặc JWT secret quá ngắn sẽ làm ứng dụng không khởi động. `CORS_ALLOWED_ORIGINS` là danh sách origin đầy đủ (scheme, host, port), phân cách bằng dấu phẩy; để trống thì không cho trình duyệt gọi khác origin. `dev` mặc định cho phép `http://localhost:3000`, `4200`, `5173`, `8080`.

Profile `mysql` không tạo tài khoản ban đầu, vai trò hay dữ liệu nghiệp vụ mẫu. Cần cấp vai trò và tài khoản ADMIN ban đầu qua quy trình quản trị CSDL có kiểm soát (mật khẩu lưu dạng BCrypt); sau đó ADMIN tạo người dùng qua API. Không bật kèm `dev` trên môi trường triển khai.

---

## 🔑 Tài Khoản Thử Nghiệm Nạp Sẵn (chỉ profile `dev`)

| Username | Password | Họ và tên | Quyền hạn |
| :--- | :--- | :--- | :--- |
| `admin` | `admin123` | Nguyễn Văn Quản Trị | `ROLE_ADMIN` (Toàn quyền) |
| `manager` | `manager123` | Trần Thị Thu Trang | `ROLE_MANAGER` (Thủ kho) |
| `staff` | `staff123` | Lê Hoàng Nam | `ROLE_STAFF` (Nhân viên kho) |

---

## 📚 Thư Mục Tài Liệu Chi Tiết (Nộp Kèm Đồ Án)

Toàn bộ tài liệu phân tích và thiết kế được lưu tại thư mục [`docs/`](file:///d:/CODE/CMC/SPRING/docs):
- [📄 Tài liệu 01: Khảo sát Bài toán & Xác định Phạm vi (SRS)](file:///d:/CODE/CMC/SPRING/docs/01_BAI_TOAN_VA_PHAM_VI.md)
- [📄 Tài liệu 02: Thiết kế Kiến trúc Phân tầng & Cơ sở Dữ liệu (ERD)](file:///d:/CODE/CMC/SPRING/docs/02_THIET_KE_KIEN_TRUC_VA_CSDL.md)
- [📄 Tài liệu 03: Hướng dẫn Cài đặt & Kịch bản Demo Bảo vệ Đồ án](file:///d:/CODE/CMC/SPRING/docs/03_HUONG_DAN_CAI_DAT_VA_DEMO.md)
- [📄 Tài liệu 04: Bảo mật, Giao dịch, Logging, Giám sát hiệu năng, Kiểm toán & Xử lý lỗi](file:///d:/CODE/CMC/SPRING/docs/04_BAO_MAT_GIAM_SAT_KIEM_TOAN.md)
