# WinMart Warehouse Management System (WinMart WMS)
> **Đề tài cuối kỳ**: Hệ thống Quản lý Kho Chuỗi Siêu Thị WinMart trên nền tảng **Spring Boot (Java 21)**.

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
Chỉ cần máy có cài **JDK 21** và **Maven 3.6.3+** trên PATH (kiểm tra bằng `java -version`, `mvn -version`). Không cần Node, npm hay database bên ngoài để chạy giao diện:
```bash
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```
> Hệ thống sẽ tự động tạo bảng và nạp sẵn dữ liệu mẫu thực tế về sản phẩm WinMart, tài khoản test và các lô hàng cận date.
> Profile `dev` tự sinh JWT secret ngẫu nhiên nếu chưa đặt `JWT_SECRET`; token cũ hết hiệu lực sau khi khởi động lại. Dữ liệu H2 chỉ tồn tại trong lần chạy đó.
> Đặt `JAVA_HOME` đúng JDK 21 và kiểm tra cả Java mà `mvn -version` đang dùng. JDK 24/27 trên máy khảo sát không tương thích với Lombok hiện tại của project khi build sạch.

- **Giao diện POS / Trang chính**: [http://localhost:8080/](http://localhost:8080/)
- **Swagger UI (tra cứu và kiểm tra API)**: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
- **Truy cập H2 Console**: [http://localhost:8080/h2-console](http://localhost:8080/h2-console) (JDBC URL: `jdbc:h2:mem:winmart_wms_db`, User: `sa`, Password: để trống)

### Sử dụng giao diện POS

Trước đây repo chỉ có REST API, không có frontend/template/static assets; `/` còn yêu cầu JWT nên chạy backend không hiện màn hình sử dụng. Giao diện hiện nằm trong `src/main/resources/static/`, được Spring Boot phục vụ cùng origin với API. Chỉ các tệp giao diện được truy cập công khai; dữ liệu vẫn yêu cầu JWT và phân quyền hiện có. Không cần chạy frontend riêng hay mở thêm CORS. Truy cập qua HTTP ở URL trên, không mở trực tiếp `index.html` bằng `file://`.

1. Đăng nhập bằng tài khoản được cấp. Với `dev`, tài khoản thử nghiệm nằm trong bảng bên dưới.
2. Tìm sản phẩm theo tên/SKU, lọc nhóm hàng và sắp xếp theo tên/giá. Thêm vào giỏ, nhập số lượng hoặc dùng nút ±; xóa dòng có thể hoàn tác trước thao tác thay đổi giỏ tiếp theo.
3. Chọn kho xuất và nhập nơi nhận. Dữ liệu dev có tồn ở `WH-MB-01`; kho khác có thể chưa có hàng.
4. Chọn **Tạo đơn xuất kho**. Khi thành công, giao diện hiển thị mã phiếu, tổng tiền máy chủ xác nhận và các lô hàng đã xuất. Khi thất bại, giỏ hàng vẫn giữ nguyên.

Backend hiện là WMS, chưa có đơn bán lẻ/thanh toán. POS cơ bản dùng nghiệp vụ **phiếu xuất kho `STORE_TRANSFER`**, bắt buộc kho và điểm nhận, trừ tồn theo FEFO của các lô còn hạn. Giao diện không gửi `unitPrice` hay tổng tiền: backend dùng giá bán hiện hành và tính tổng bằng `BigDecimal`. Giá trên giỏ là tạm tính, có thể khác giá lúc máy chủ xử lý. Sản phẩm ngừng kinh doanh không thể thêm từ giao diện.

API dùng trong luồng chính: `POST /api/v1/auth/login`, `POST /api/v1/auth/logout`, `GET /api/v1/products?search=...&page=...&size=12&sort=name,asc&categoryId=...` (`categoryId` tùy chọn), `GET /api/v1/categories`, `GET /api/v1/warehouses/active`, `POST /api/v1/issues`. Không dùng dữ liệu mock trong ứng dụng; profile dev cung cấp dữ liệu seed trong H2 và mọi thao tác đều đi qua backend/database thật.

Giao diện mới giữ hai màn đăng nhập/quầy xuất kho, dùng cùng hệ màu và khoảng cách, có lỗi tại trường, phản hồi đã chọn, trạng thái tải/trống/lỗi và xác nhận trước khi xóa cả giỏ hoặc đăng xuất mất bản nháp. Nguồn nghiên cứu ứng dụng POS, lý do thiết kế và giới hạn accessibility/responsive nằm trong [Tài liệu giao diện POS](docs/POS_UI_UX.md).

Token chỉ giữ trong bộ nhớ tab. Tải lại/đóng tab mất phiên đăng nhập và giỏ; đăng xuất thành công xóa bản nháp. Khi token hết hiệu lực trong lúc dùng, có thể đăng nhập lại để tiếp tục giỏ hiện tại. Lỗi kết nối/timeout khi tạo đơn có thể xảy ra sau khi máy chủ đã commit: giao diện không tự gửi lại. Kiểm tra `GET /api/v1/issues/warehouse/{warehouseId}` bằng tài khoản hiện tại (Swagger trong dev) trước khi gửi lại để tránh tạo hai phiếu. API hiện chưa có idempotency key.

### Build và kiểm thử

```bash
mvn test
mvn package
java -jar target/winmart-wms-1.0.0.jar --spring.profiles.active=dev
```

Giao diện được đóng gói trong JAR, URL mặc định vẫn là `http://localhost:8080/`. Có thể đổi cổng bằng `--server.port=8081`, khi đó truy cập `http://localhost:8081/`. Nếu cổng đang được dùng, chọn cổng khác; không cần đổi URL API trong JavaScript.

Kiểm thử hành vi JavaScript không cần dependency ngoài, dùng **Node 18+** (chỉ khi kiểm thử):

```bash
node --test src/test/js/pos.test.cjs
```

`PosFrontendTest` kiểm tra truy cập trang chủ/static assets, API vẫn bảo vệ bằng JWT, tìm kiếm sản phẩm, tạo phiếu bằng giá backend, trừ tồn và phản hồi thiếu tồn/validation. Bộ JavaScript kiểm tra giỏ hàng, số lượng không hợp lệ, chống gửi trùng, tổng máy chủ trả về, giữ giỏ khi lỗi, mất kết nối, hết phiên, dữ liệu trống và phản hồi tìm kiếm đến sai thứ tự.

Kiểm chứng bản UI ngày 29/10/2026: build/package bằng JDK 21 thành công; 51 test Java và 12 test JavaScript đều qua. Do quyền ghi trong `target` cũ bị lỗi, build được thực hiện trên bản sao tạm và đối chiếu SHA-256 với `src`; JAR được chép về `target/winmart-wms-1.0.0.jar`. JAR chạy với dev/H2, HTTP trang chủ và static assets trả 200; luồng đăng nhập, tìm kiếm, tạo phiếu, trừ tồn, lỗi thiếu tồn/validation và thu hồi token sau đăng xuất được kiểm tra thực tế. Java kiểm tra thêm bộ lọc nhóm kết hợp SKU và phân trang. Lỗi kết nối, chống gửi trùng, hoàn tác/xác nhận và lỗi tại trường được kiểm tra trong JavaScript. Kiểm tra tương phản chỉ bao phủ các màu chữ/viền chính trên nền trắng; không chứng nhận WCAG 2.2 AA.

Phiên kiểm thử giao diện mới dùng **http://localhost:8081/** vì instance cũ trên cổng 8080 được giữ nguyên. Để chạy JAR trên cổng này, dùng JDK 21:

```bash
java -jar target/winmart-wms-1.0.0.jar --spring.profiles.active=dev --server.port=8081
```

Chưa có ảnh giao diện/kiểm tra console và chưa kiểm chứng trực quan tại 360, 768, 1024, 1440px: IAB không khả dụng, công cụ Brave báo lỗi geometry/input và trả ảnh cửa sổ khác. Các quy tắc responsive đã triển khai và phần còn cần kiểm tra được ghi riêng trong tài liệu giao diện. Chưa chạy với MySQL thật. JDK dùng để kiểm thử ở thư mục tạm; cần cài JDK 21 ổn định để tiếp tục phát triển sau khi hệ thống dọn thư mục tạm.

Bản sửa ghi chú và spacing: đã đóng thẻ `textarea`, đồng bộ khoảng cách/căn lề toàn trang; 14 test JavaScript qua và đóng gói JAR thành công với `-DskipTests` (không đổi/chạy lại Java). Bản xem trước tại **http://localhost:8082/** phục vụ mã frontend hiện tại và chuyển tiếp API tới backend 8081 để giữ dữ liệu H2/phiên đang chạy. Đây là helper tạm trong `target/pos-preview.cjs`, không phải bước chạy production; khi khởi động JAR đã cập nhật, giao diện sửa sẵn được phục vụ trực tiếp bởi Spring Boot.

Nhận diện hiện dùng đỏ–trắng WinMart; thẻ hàng và giỏ có minh họa riêng cho sữa/mì/nước mắm, cùng hình kiện hàng dự phòng cho SKU khác. Hình SVG được nhúng sẵn, có nhãn minh họa, không phụ thuộc mạng và không thay đổi dữ liệu sản phẩm/API. API hiện chưa cung cấp ảnh chụp sản phẩm. Bộ JavaScript hiện có 15 test qua; chi tiết màu và phạm vi hình trong tài liệu giao diện.

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
