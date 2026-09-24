# TÀI LIỆU 01: KHẢO SÁT BÀI TOÁN & XÁC ĐỊNH PHẠM VI (SRS)
## Đề tài: Hệ Thống Quản Lý Kho Chuỗi Siêu Thị WinMart (WinMart WMS)

---

## 1. GIỚI THIỆU BÀI TOÁN

### 1.1. Bối cảnh chuỗi bán lẻ WinMart
WinMart (và WinMart+) là chuỗi bán lẻ tiêu dùng hàng đầu tại Việt Nam với mạng lưới hàng nghìn siêu thị và cửa hàng tiện ích trải rộng khắp toàn quốc. Để duy trì sự vận hành thông suốt và hàng hóa luôn tươi mới trên kệ, hệ thống chuỗi cung ứng của WinMart vận hành theo mô hình trung tâm phân phối (Distribution Center - Hub Warehouse) chuyển tiếp hàng hóa tới các điểm bán lẻ (Retail Outlets).

Đặc thù của ngành hàng bán lẻ tiêu dùng nhanh (FMCG) và thực phẩm tươi sống tại WinMart đặt ra những thách thức rất lớn:
1. **Quản lý hạn sử dụng (Expiry Date) và Lô hàng (Batch/Lot)**: Các sản phẩm như sữa, thực phẩm đóng gói, hóa phẩm có vòng đời sử dụng ngắn. Nếu không kiểm soát date chặt chẽ, hàng cận date hoặc hết hạn lưu kho sẽ gây tổn thất tài chính nghiêm trọng và vi phạm an toàn vệ sinh thực phẩm.
2. **Quy tắc xuất kho FEFO (First Expired, First Out)**: Khác với kho công nghiệp chỉ dùng FIFO (First In, First Out), kho bán lẻ thực phẩm bắt buộc phải ưu tiên xuất các lô hàng có **hạn sử dụng gần nhất** trước.
3. **Truy xuất nguồn gốc (Traceability)**: Khi có sự cố sản phẩm từ nhà sản xuất, kho phải có khả năng truy xuất ngay lập tức lô hàng đó đã được nhập từ nhà cung cấp nào, vào ngày nào, và đã xuất phân phối tới những siêu thị/cửa hàng WinMart+ nào.
4. **Tối ưu hóa mức tồn kho an toàn (Safety Stock)**: Tránh tình trạng "đứt gãy chuỗi cung ứng" (hết hàng để bán) hoặc "ứ đọng vốn" (nhập quá nhiều gây vượt sức chứa của kho).

### 1.2. Hạn chế của quy trình truyền thống
- Sử dụng bảng tính Excel rời rạc hoặc ghi chép sổ sách thủ công.
- Dễ nhầm lẫn số lô, không cảnh báo được hàng sắp hết hạn trước 15 - 30 ngày để bộ phận kinh doanh có chính sách giảm giá/khuyến mãi xả hàng.
- Thiếu cơ chế kiểm soát đồng thời (Concurrency Control) và bảo đảm toàn vẹn dữ liệu (Data Integrity) khi nhiều nhân viên cùng thực hiện nhập - xuất kho cùng một lúc.
- Khó phân quyền bảo mật: Nhân viên kho có thể vô tình xóa hoặc sửa dữ liệu lịch sử.

### 1.3. Mục tiêu của hệ thống WinMart WMS
Xây dựng một giải pháp phần mềm Quản lý Kho hiện đại trên nền tảng **Spring Boot (Java 21)** nhằm:
- Số hóa 100% quy trình Nhập kho (Inbound PO), Xuất kho điều phối (Outbound Store Transfer) và Theo dõi tồn kho (Inventory Tracking).
- Tự động hóa thuật toán xuất kho **FEFO**, tự động chọn lô cận date nhất để xuất trước.
- Cảnh báo trực quan hàng cận date và hàng dưới ngưỡng tồn an toàn theo thời gian thực.
- Cung cấp giao diện lập trình ứng dụng RESTful API chuẩn hóa, bảo mật với JWT, sẵn sàng kết nối với Web Client, Mobile App hoặc máy quét mã vạch (Barcode Scanner).

---

## 2. ĐỊNH NGHĨA PHẠM VI HỆ THỐNG (SCOPE DEFINITION)

Để đồ án đạt chất lượng cao về mặt kỹ thuật, làm nổi bật vai trò của **Spring Boot**, đồng thời khả thi trong khuôn khổ thời gian của đồ án sinh viên/khóa luận tốt nghiệp, phạm vi được phân định rõ ràng như sau:

### 2.1. Phạm vi thực hiện (In-Scope)
Hệ thống tập trung vào các phân hệ nghiệp vụ cốt lõi tại Kho Tổng (Distribution Center) và Kho Điểm Bán:

| Phân hệ (Module) | Chức năng chi tiết | Mô tả nghiệp vụ WinMart |
| :--- | :--- | :--- |
| **1. Xác thực & Phân quyền (RBAC)** | - Đăng ký, đăng nhập cấp phát JWT Token.<br>- Phân quyền 3 cấp vai trò: `ROLE_ADMIN` (Quản trị hệ thống), `ROLE_MANAGER` (Thủ kho/Quản lý kho), `ROLE_STAFF` (Nhân viên kho). | Đảm bảo tính bảo mật và trách nhiệm giải trình của từng nhân sự tham gia vận hành kho. |
| **2. Quản lý Danh mục & Hàng hóa** | - Quản lý Ngành hàng (Sữa, Mì, Gia vị, Đồ uống...).<br>- Quản lý Sản phẩm: SKU, Tên, Mã vạch (Barcode), Đơn vị tính, Giá nhập, Giá bán, Định mức tồn an toàn (`minStockLevel`). | Chuẩn hóa thông tin hàng hóa, hỗ trợ tra cứu nhanh bằng mã SKU hoặc quét mã vạch Barcode. |
| **3. Quản lý Đối tác & Kho bãi** | - Quản lý Nhà cung cấp (Vinamilk, Masan, Acecook...).<br>- Quản lý danh sách Kho hàng (Kho tổng KCN Quang Minh, Kho trung chuyển Hoàng Mai...). | Quản lý nguồn gốc nhập hàng và mạng lưới các kho trực thuộc chuỗi WinMart. |
| **4. Nghiệp vụ Nhập kho (Inbound)** | - Lập Phiếu nhập kho (Goods Receipt) từ Nhà cung cấp.<br>- Tiếp nhận và ghi nhận chi tiết: Số lô (`batchNumber`), Ngày sản xuất (MFG), Hạn sử dụng (EXP), Đơn giá, Số lượng.<br>- Tự động tăng tồn kho theo từng lô tương ứng. | Bảo đảm nguyên tắc mọi hàng hóa nhập vào đều phải gắn với Lô sản xuất và Hạn dùng rõ ràng. |
| **5. Nghiệp vụ Xuất kho (Outbound FEFO)** | - Lập Phiếu xuất kho (Goods Issue) điều chuyển tới các siêu thị WinMart+ con.<br>- **Thuật toán FEFO**: Tự động tìm và trừ số lượng từ các lô hàng có hạn sử dụng gần nhất.<br>- Kiểm tra số lượng tồn, chặn xuất âm kho. | Giải quyết triệt để bài toán chống hư hỏng, hết hạn hàng hóa của chuỗi siêu thị. |
| **6. Quản lý Tồn kho & Theo dõi Lô** | - Tra cứu tồn kho chi tiết theo từng kho và từng lô.<br>- **Bộ lọc cảnh báo cận date**: Liệt kê các lô hàng còn hạn dưới 15 ngày hoặc dưới 30 ngày.<br>- Cảnh báo sản phẩm sắp hết hàng (tồn <= ngưỡng an toàn). | Cung cấp thông tin cảnh báo sớm cho thủ kho và bộ phận kinh doanh xử lý. |
| **7. Nhật ký Biến động kho (Audit Trail)** | - Tự động ghi lại lịch sử mọi giao dịch (Nhập, Xuất) kèm số dư tồn kho sau giao dịch (`balanceAfter`). | Minh bạch hóa dữ liệu, phục vụ việc đối soát và thanh tra kho. |
| **8. Báo cáo & Dashboard** | - Thống kê KPIs: Tổng số mặt hàng, tổng giá trị tồn kho (VND), tổng số phiếu nhập/xuất trong kỳ, số lượng cảnh báo cận date. | Hỗ trợ cấp quản lý có cái nhìn tổng quan về tình hình lưu chuyển hàng hóa. |

### 2.2. Phạm vi loại trừ (Out-of-Scope)
Để giữ cho đồ án tập trung vào chiều sâu backend, các phần sau không đưa vào phạm vi:
- *Không* tích hợp phần cứng máy quét RFID chuyên dụng hoặc điều khiển robot tự hành (AGV) trong kho thông minh (chỉ cung cấp API nhận chuỗi barcode).
- *Không* làm cổng thanh toán trực tuyến của khách lẻ (Point of Sale - POS bán hàng tại quầy thu ngân).
- *Không* giải bài toán tối ưu lộ trình xe tải giao hàng (Vehicle Routing Problem).

---

## 3. YÊU CẦU CHỨC NĂNG & PHI CHỨC NĂNG

### 3.1. Ma trận Phân quyền Người dùng (Matrix Access Control)
| Quyền hạn / Nghiệp vụ | ROLE_ADMIN | ROLE_MANAGER | ROLE_STAFF |
| :--- | :---: | :---: | :---: |
| Quản lý tài khoản người dùng | Toàn quyền | Không | Không |
| Tạo / Sửa / Xóa Sản phẩm, Ngành hàng | Có | Có | Chỉ xem |
| Quản lý Nhà cung cấp & Kho hàng | Có | Có | Chỉ xem |
| Lập Phiếu Nhập Kho (Inbound) | Có | Có | Có |
| Lập Phiếu Xuất Kho (Outbound FEFO) | Có | Có | Có |
| Xem Tồn kho & Cảnh báo Cận date | Có | Có | Có |
| Xem Lịch sử Biến động kho (Audit Trail) | Có | Có | Có |
| Xem Báo cáo Dashboard & Định giá kho | Có | Có | Không |

### 3.2. Yêu cầu Phi chức năng (Non-Functional Requirements)
1. **Tính Toàn vẹn Dữ liệu (ACID Compliance)**:
   - Mọi thao tác Nhập/Xuất kho đều phải chạy trong giao dịch (`@Transactional`). Nếu có sự cố (ví dụ lỗi cập nhật tồn kho hay thiếu số lượng), toàn bộ giao dịch phải được Rollback tự động, tuyệt đối không để xảy ra tình trạng "phiếu đã tạo nhưng kho không trừ" hoặc "trừ kho nhưng không lưu được phiếu".
2. **Tính Bảo mật (Security)**:
   - Mật khẩu mã hóa 1 chiều bằng thuật toán `BCrypt`.
   - Phiên làm việc không lưu trạng thái (Stateless) xác thực qua JWT Bearer Token.
   - Chống tấn công khai thác quyền với Spring Method Security (`@PreAuthorize`).
3. **Chuẩn hóa API (RESTful Principles)**:
   - Các mã trạng thái HTTP chuẩn mực: `200 OK`, `201 Created`, `400 Bad Request`, `401 Unauthorized`, `403 Forbidden`, `404 Not Found`.
   - Cấu trúc phản hồi JSON thống nhất qua `ApiResponse<T>` và `PageResponse<T>`.
4. **Tài liệu hóa Tự động (Self-Documenting API)**:
   - Tích hợp OpenAPI 3 / Swagger UI cho phép hội đồng chấm điểm trải nghiệm và gửi request trực tiếp trên trình duyệt.
