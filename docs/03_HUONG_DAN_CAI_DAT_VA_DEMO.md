# TÀI LIỆU 03: HƯỚNG DẪN CÀI ĐẶT & KỊCH BẢN DEMO BẢO VỆ ĐỒ ÁN
## Đề tài: Hệ Thống Quản Lý Kho Chuỗi Siêu Thị WinMart (WinMart WMS)

---

## 1. YÊU CẦU MÔI TRƯỜNG & KHỞI CHẠY HỆ THỐNG

### 1.1. Yêu cầu môi trường
- **Java Development Kit (JDK)**: Phiên bản **Java 21 LTS** trở lên.
- **Apache Maven**: Phiên bản 3.8.x hoặc 3.9.x trở lên.
- **Trình duyệt Web**: Chrome, Edge, Firefox (để mở giao diện Swagger UI).

### 1.2. Khởi chạy ứng dụng (Mặc định H2 In-Memory - Không cần cài Database)
Dự án được cấu hình sẵn chế độ "Zero-Config" sử dụng H2 Database. Mọi bảng biểu và dữ liệu mẫu thực tế sẽ tự động được khởi tạo ngay khi ứng dụng khởi động.

Mở terminal tại thư mục gốc dự án (`d:/CODE/CMC/SPRING`):
```bash
mvn spring-boot:run
```

Khi nhìn thấy dòng log:
```text
>> Đang khởi tạo dữ liệu mẫu cho Hệ thống Quản lý Kho WinMart (WinMart WMS)...
>> Khởi tạo dữ liệu mẫu WinMart WMS thành công!
Started WinmartWmsApplication in ... seconds
```
-> Ứng dụng đã khởi chạy thành công tại cổng: **`http://localhost:8080`**.

### 1.3. Chuyển sang MySQL 8 (Nếu muốn demo trên Database thật)
1. Tạo database trống trên MySQL:
```sql
CREATE DATABASE winmart_wms CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```
2. Khởi chạy với profile `mysql`:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```
*(Có thể tùy chỉnh user/password trong file `src/main/resources/application-mysql.yml`)*

---

## 2. TRANG QUẢN TRỊ & TÀI KHOẢN TRUY CẬP

### 2.1. Địa chỉ các trang giao diện
- **Tài liệu API Swagger UI (Giao diện chính để thao tác và demo)**:
  👉 **`http://localhost:8080/swagger-ui/index.html`**
- **Trang quản trị CSDL H2 Console**:
  👉 **`http://localhost:8080/h2-console`**
  - JDBC URL: `jdbc:h2:mem:winmart_wms_db`
  - User: `sa` | Password: (để trống)

### 2.2. Danh sách tài khoản đã được nạp sẵn (Data Seed)
| Tên đăng nhập (Username) | Mật khẩu (Password) | Họ và tên | Vai trò (Role) | Chức năng chính |
| :--- | :--- | :--- | :--- | :--- |
| `admin` | `admin123` | Nguyễn Văn Quản Trị | `ROLE_ADMIN` | Toàn quyền cấu hình hệ thống, quản lý tài khoản, ngành hàng, nhà cung cấp, kho. |
| `manager` | `manager123` | Trần Thị Thu Trang | `ROLE_MANAGER` | Thủ kho trưởng, phê duyệt nhập/xuất kho, theo dõi báo cáo và kiểm soát cận date. |
| `staff` | `staff123` | Lê Hoàng Nam | `ROLE_STAFF` | Nhân viên kho thực hiện scan barcode, tạo phiếu nhập/xuất kho thực tế. |

---

## 3. KỊCH BẢN DEMO STEP-BY-STEP DÀNH CHO BẢO VỆ ĐỒ ÁN
*(Thực hiện trực tiếp trên giao diện Swagger UI để gây ấn tượng mạnh với Hội đồng chấm điểm)*

### Bước 1: Đăng nhập & Lấy Bearer Token
1. Mở Swagger UI tại `http://localhost:8080/swagger-ui/index.html`.
2. Mở rộng nhóm API **`Authentication`** -> Chọn `POST /api/v1/auth/login`.
3. Bấm **"Try it out"**, nhập body:
```json
{
  "username": "manager",
  "password": "manager123"
}
```
4. Bấm **"Execute"**. Kiểm tra kết quả trả về `200 OK`.
5. Copy chuỗi token trong trường `data.token`.
6. Cuộn lên đầu trang Swagger, bấm vào nút xanh **`Authorize`** (nằm ở góc trên bên phải).
7. Dán chuỗi token vừa copy vào ô `Value` (hoặc gõ `Bearer <token>`) -> Bấm **Authorize** -> Bấm **Close**.
> *Lúc này toàn bộ các API bảo mật đã được gắn kèm JWT token.*

---

### Bước 2: Xem Báo cáo Tổng quan Kho (Dashboard KPIs)
1. Mở nhóm API **`Report & Dashboard`** -> Chọn `GET /api/v1/reports/dashboard`.
2. Bấm **"Try it out"** -> Bấm **"Execute"**.
3. **Phân tích cho Hội đồng xem**:
   - Hệ thống thống kê ngay lập tức: Tổng số mặt hàng, tổng giá trị tồn kho (VND), tổng số kho đang quản lý, và số lượng lô hàng đang trong tình trạng cảnh báo cận date (`expiringBatchesCount`).

---

### Bước 3: Chứng minh Tính năng Cảnh báo Hàng Cận Date (Đặc thù WinMart)
1. Mở nhóm API **`Inventory & Stock Tracking`** -> Chọn `GET /api/v1/inventory/warehouse/{warehouseId}/expiring`.
2. Bấm **"Try it out"**, nhập `warehouseId = 1`, `days = 30` -> Bấm **"Execute"**.
3. **Kết quả nổi bật**:
   - Xuất hiện lô hàng sữa tươi Vinamilk `LOT-VNM-2601` có trạng thái:
     `warningStatus: "CẬN DATE NGUY CƠ CAO (≤ 15 ngày)"` kèm số ngày còn hạn chính xác (ví dụ còn 12 ngày).
   - Trong khi đó, lô sữa `LOT-VNM-2602` (còn 180 ngày) không nằm trong diện cảnh báo này.

---

### Bước 4: Demo Thuật Toán Xuất Kho Tự Động FEFO (First Expired, First Out)
> *Tình huống nghiệp vụ: Cửa hàng WinMart+ 45 Ba Đình đặt mua 150 hộp sữa tươi Vinamilk. Kho đang có:*
> - *Lô `LOT-VNM-2601` (hạn còn 12 ngày): tồn 100 hộp.*
> - *Lô `LOT-VNM-2602` (hạn còn 180 ngày): tồn 300 hộp.*
> *Hệ thống sẽ chứng minh sự thông minh bằng cách tự động xuất hết 100 hộp của lô cận date trước, rồi mới lấy tiếp 50 hộp của lô date xa.*

1. Mở nhóm API **`Outbound (Xuất kho FEFO)`** -> Chọn `POST /api/v1/issues`.
2. Bấm **"Try it out"**, nhập body:
```json
{
  "warehouseId": 1,
  "destination": "Siêu thị WinMart+ 45 Ba Đình",
  "issueType": "STORE_TRANSFER",
  "note": "Xuất điều chuyển định kỳ theo đơn đặt hàng",
  "items": [
    {
      "productId": 1,
      "quantity": 150
    }
  ]
}
```
3. Bấm **"Execute"**.
4. **Kết quả chứng minh FEFO thành công**:
   - Quan sát danh sách `items` trong kết quả trả về `201 Created`:
     - Dòng 1: Xuất đúng **100 hộp** từ Lô `LOT-VNM-2601` (Lô cận date nhất).
     - Dòng 2: Xuất tiếp **50 hộp** từ Lô `LOT-VNM-2602` (Lô date xa).
   - Toàn bộ được thực thi tự động trong 1 Transaction duy nhất!

---

### Bước 5: Demo Cơ chế Chống Âm Kho & Bắt Lỗi Tập Trung
1. Tại cùng API `POST /api/v1/issues`, thử đặt số lượng xuất `quantity = 99999` (vượt xa lượng tồn).
2. Bấm **"Execute"**.
3. **Kết quả**:
   - Hệ thống chặn lại ngay lập tức với HTTP **`400 Bad Request`**.
   - Phản hồi JSON chuẩn hóa:
     ```json
     {
       "success": false,
       "message": "Sản phẩm [VNM-100-1L - Sữa tươi tiệt trùng Vinamilk...] không đủ tồn kho. Tồn hiện tại: 250, Yêu cầu xuất: 99999",
       "timestamp": "..."
     }
     ```
   - Điểm nhấn kỹ thuật: Minh họa cho hội đồng thấy `@Transactional` đã tự động Rollback, không có dữ liệu rác nào bị ghi vào CSDL.

---

### Bước 6: Demo Nhập Kho Inbound từ Nhà Cung Cấp
1. Mở nhóm API **`Inbound (Nhập kho)`** -> Chọn `POST /api/v1/receipts`.
2. Bấm **"Try it out"**, nhập body:
```json
{
  "warehouseId": 1,
  "supplierId": 3,
  "note": "Nhập lô Mì Hảo Hảo mới từ Acecook Việt Nam",
  "items": [
    {
      "productId": 2,
      "batchNumber": "LOT-ACK-2609",
      "manufacturingDate": "2026-09-01",
      "expiryDate": "2027-03-01",
      "quantity": 200,
      "unitPrice": 3500
    }
  ]
}
```
3. Bấm **"Execute"** -> Nhận mã phiếu `PNK-20260924-XXXX`, tồn kho lô mới được sinh tự động.

---

### Bước 7: Kiểm tra Nhật ký Biến động kho (Audit Trail)
1. Mở nhóm API **`Inventory & Stock Tracking`** -> Chọn `GET /api/v1/inventory/movements`.
2. Bấm **"Execute"**.
3. **Kết quả**:
   - Mọi lịch sử biến động từ lúc nạp tồn ban đầu, xuất kho 150 hộp (ghi âm `-100` và `-50`), nhập thêm 200 gói mì (`+200`) đều hiển thị kèm số dư tồn kho sau mỗi bước (`balanceAfter`).
   - Thể hiện sự chặt chẽ, chuyên nghiệp của hệ thống quản lý kho cấp doanh nghiệp.
