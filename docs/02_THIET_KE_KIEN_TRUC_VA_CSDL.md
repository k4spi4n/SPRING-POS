# TÀI LIỆU 02: THIẾT KẾ KIẾN TRÚC & CƠ SỞ DỮ LIỆU
## Đề tài: Hệ Thống Quản Lý Kho Chuỗi Siêu Thị WinMart (WinMart WMS)

---

## 1. TỔNG QUAN KIẾN TRÚC HỆ THỐNG

### 1.1. Kiến trúc phân tầng (3-Tier Layered Architecture)
Hệ thống WinMart WMS được xây dựng theo mô hình kiến trúc phân tầng chuẩn công nghiệp của hệ sinh thái Spring Framework. Kiến trúc này đảm bảo nguyên lý **Đơn nhiệm (Single Responsibility Principle)** và **Phân tách mối quan tâm (Separation of Concerns)**:

```mermaid
graph TD
    Client["Client Layer (Web Admin, Scanner Barcode, Mobile, Postman, Swagger UI)"]
    
    subgraph SpringBootApp ["Hệ thống Backend Spring Boot 3 (Java 21)"]
        subgraph SecurityFilter ["Security & Interceptor Layer"]
            JWTFilter["JwtAuthenticationFilter (Xác thực Bearer Token)"]
            CorsFilter["CORS & CSRF Configuration"]
        end
        
        subgraph Presentation ["Presentation Layer (Controller)"]
            AuthController["AuthController"]
            ProductController["ProductController"]
            ReceiptController["ReceiptController (Nhập kho)"]
            IssueController["IssueController (Xuất kho FEFO)"]
            InventoryController["InventoryController"]
            ReportController["ReportController"]
        end
        
        subgraph ServiceLayer ["Business Logic Layer (Service)"]
            AuthService["AuthService"]
            ProductService["ProductService"]
            ReceiptService["ReceiptService (@Transactional)"]
            IssueService["IssueService (FEFO Algorithm + @Transactional)"]
            InventoryService["InventoryService"]
            ReportService["ReportService"]
        end
        
        subgraph PersistenceLayer ["Data Access Layer (Repository)"]
            UserRepository["UserRepository"]
            ProductRepository["ProductRepository"]
            BatchRepository["ProductBatchRepository"]
            InventoryRepository["InventoryRepository (Custom JPQL Queries)"]
            ReceiptRepository["StockReceiptRepository"]
            IssueRepository["StockIssueRepository"]
            MovementRepository["StockMovementRepository"]
        end
        
        subgraph CrossCutting ["Cross-Cutting Concerns"]
            ExHandler["GlobalExceptionHandler (@RestControllerAdvice)"]
            Auditor["AuditingConfig (@EnableJpaAuditing)"]
            OpenAPI["OpenApiConfig (Swagger UI Specs)"]
        end
    end
    
    subgraph Storage ["Database Layer"]
        H2DB[("H2 Database (In-Memory Development)")]
        MySQLDB[("MySQL 8 Server (Production Ready)")]
    end

    Client -->|HTTPS REST Request + JWT| SecurityFilter
    SecurityFilter --> Presentation
    Presentation --> ServiceLayer
    ServiceLayer --> PersistenceLayer
    PersistenceLayer --> H2DB
    PersistenceLayer -.->|Profile: mysql| MySQLDB
    Presentation -.-> ExHandler
```

### 1.2. Làm nổi bật các thế mạnh cốt lõi của Spring Boot trong đồ án
Dự án được thiết kế có chủ đích nhằm phô diễn tối đa các tính năng quan trọng mà giảng viên/hội đồng đánh giá cao ở một ứng dụng Spring Boot chuyên nghiệp:

1. **Inversion of Control (IoC) & Dependency Injection (DI)**:
   - Sử dụng cơ chế Constructor Injection thông qua `@RequiredArgsConstructor` của Lombok.
   - Các Service, Repository, Filter được container quản lý vòng đời chặt chẽ, giảm sự phụ thuộc cứng (loose coupling) và dễ dàng viết Unit Test với Mockito.
2. **Quản lý Giao dịch Khai báo (Declarative Transaction Management - `@Transactional`)**:
   - Thể hiện rõ nét nhất ở `ReceiptService` và `IssueService`.
   - Khi tạo một phiếu nhập hoặc xuất: Hệ thống phải thực hiện liên hoàn nhiều bước: Lưu thông tin phiếu -> Tìm/tạo Lô -> Cập nhật tăng/giảm tồn kho -> Ghi nhật ký biến động (`StockMovement`).
   - Nếu xảy ra lỗi ở bất kỳ bước nào (ví dụ thiếu tồn kho, vi phạm khóa ngoại), `@Transactional` đảm bảo **Rollback 100%**, giữ cho CSDL luôn ở trạng thái nhất quán tuyệt đối.
3. **Spring Data JPA & Hibernate ORM**:
   - Sử dụng các JPA Derived Query Methods (`findByWarehouseIdAndBatch_ProductId...`).
   - Tối ưu hóa truy vấn bằng JPQL tùy biến (`@Query`) cho bài toán tìm lô cận date và tính tổng số lượng tồn kho.
   - Cơ chế tự động ghi nhận thời gian `@CreatedDate` và `@LastModifiedDate` thông qua `AuditingEntityListener`.
4. **Spring Security 6 & Stateless JWT**:
   - Triển khai `JwtAuthenticationFilter` tự động chặn và giải mã Token trong header `Authorization: Bearer <token>`.
   - Kết hợp `RoleBased Access Control` (RBAC) với `@PreAuthorize("hasRole('ADMIN')")` hoặc `@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")` trực tiếp trên từng hàm nghiệp vụ.
5. **Xử lý Ngoại lệ Tập trung (Centralized Exception Handling)**:
   - Thay vì `try-catch` rải rác trong Controller, hệ thống dùng `@RestControllerAdvice` và `@ExceptionHandler`.
   - Chuẩn hóa mã lỗi nghiệp vụ (`RESOURCE_NOT_FOUND`, `INSUFFICIENT_STOCK`, `UNAUTHORIZED`,...) và tự động map HTTP status tương ứng.
6. **Spring Validation (`jakarta.validation`)**:
   - Kiểm tra tính hợp lệ dữ liệu ngay từ tầng tiếp nhận (`@Valid`, `@NotBlank`, `@Min`, `@DecimalMin`).
   - Trả về danh sách chi tiết các trường bị lỗi định dạng cho Client.
7. **Tài liệu hóa Tự động với Springdoc OpenAPI 3**:
   - Tự động quét toàn bộ Controller và sinh Swagger UI tương tác trực tiếp mà không cần viết tay file tài liệu API riêng biệt.

---

## 2. THIẾT KẾ CƠ SỞ DỮ LIỆU (DATABASE SCHEMA)

### 2.1. Sơ đồ Thực thể Liên kết (Entity Relationship Diagram - ERD)

```mermaid
erDiagram
    ROLES ||--o{ USERS : "has"
    USERS ||--o{ STOCK_RECEIPTS : "created_by"
    USERS ||--o{ STOCK_ISSUES : "created_by"

    CATEGORIES ||--o{ PRODUCTS : "contains"
    SUPPLIERS ||--o{ PRODUCTS : "supplies"
    SUPPLIERS ||--o{ STOCK_RECEIPTS : "delivers_to"

    WAREHOUSES ||--o{ INVENTORIES : "stores"
    WAREHOUSES ||--o{ STOCK_RECEIPTS : "receives_at"
    WAREHOUSES ||--o{ STOCK_ISSUES : "dispatches_from"
    WAREHOUSES ||--o{ STOCK_MOVEMENTS : "logs_at"

    PRODUCTS ||--o{ PRODUCT_BATCHES : "has_lots"
    PRODUCTS ||--o{ RECEIPT_ITEMS : "details"
    PRODUCTS ||--o{ ISSUE_ITEMS : "details"
    PRODUCTS ||--o{ STOCK_MOVEMENTS : "applies_to"

    PRODUCT_BATCHES ||--o{ INVENTORIES : "counted_in"
    PRODUCT_BATCHES ||--o{ ISSUE_ITEMS : "fulfilled_by"
    PRODUCT_BATCHES ||--o{ STOCK_MOVEMENTS : "references"

    STOCK_RECEIPTS ||--o{ RECEIPT_ITEMS : "contains"
    STOCK_ISSUES ||--o{ ISSUE_ITEMS : "contains"

    ROLES {
        bigint id PK
        varchar name "ROLE_ADMIN, ROLE_MANAGER, ROLE_STAFF"
        varchar description
        datetime created_at
    }

    USERS {
        bigint id PK
        varchar username UK
        varchar password
        varchar full_name
        varchar email UK
        varchar phone
        bigint role_id FK
        boolean active
        datetime created_at
    }

    CATEGORIES {
        bigint id PK
        varchar code UK
        varchar name
        varchar description
        datetime created_at
    }

    SUPPLIERS {
        bigint id PK
        varchar code UK
        varchar name
        varchar contact_name
        varchar phone
        varchar email
        varchar address
        datetime created_at
    }

    WAREHOUSES {
        bigint id PK
        varchar code UK
        varchar name
        varchar location
        double capacity
        boolean active
        datetime created_at
    }

    PRODUCTS {
        bigint id PK
        varchar sku UK
        varchar name
        varchar barcode UK
        varchar unit
        decimal purchase_price
        decimal selling_price
        int min_stock_level
        bigint category_id FK
        bigint supplier_id FK
        boolean active
        datetime created_at
    }

    PRODUCT_BATCHES {
        bigint id PK
        varchar batch_number "UK cùng product_id"
        bigint product_id FK
        date manufacturing_date
        date expiry_date
        datetime created_at
    }

    INVENTORIES {
        bigint id PK
        bigint warehouse_id FK
        bigint batch_id FK
        int quantity "Tồn thực tế của lô tại kho"
        datetime updated_at
    }

    STOCK_RECEIPTS {
        bigint id PK
        varchar receipt_code UK
        bigint warehouse_id FK
        bigint supplier_id FK
        bigint received_by_id FK
        varchar status "COMPLETED, CANCELLED"
        decimal total_amount
        varchar note
        datetime created_at
    }

    RECEIPT_ITEMS {
        bigint id PK
        bigint receipt_id FK
        bigint product_id FK
        varchar batch_number
        date manufacturing_date
        date expiry_date
        int quantity
        decimal unit_price
        decimal sub_total
    }

    STOCK_ISSUES {
        bigint id PK
        varchar issue_code UK
        bigint warehouse_id FK
        bigint issued_by_id FK
        varchar destination "WinMart+ store hoặc điểm tiêu hủy"
        varchar issue_type "STORE_TRANSFER, DISPOSAL_EXPIRED"
        varchar status "COMPLETED, CANCELLED"
        decimal total_amount
        varchar note
        datetime created_at
    }

    ISSUE_ITEMS {
        bigint id PK
        bigint issue_id FK
        bigint product_id FK
        bigint batch_id FK
        int quantity
        decimal unit_price
        decimal sub_total
    }

    STOCK_MOVEMENTS {
        bigint id PK
        bigint warehouse_id FK
        bigint product_id FK
        bigint batch_id FK
        varchar movement_type "INBOUND, OUTBOUND, ADJUSTMENT"
        int quantity
        int balance_after
        varchar reference_code
        varchar note
        datetime created_at
    }
```

### 2.2. Từ điển Dữ liệu (Data Dictionary - Các bảng trọng tâm)

#### Bảng `product_batches` (Lô sản xuất & Hạn sử dụng)
| Tên cột | Kiểu dữ liệu | Ràng buộc | Ý nghĩa |
| :--- | :--- | :--- | :--- |
| `id` | BIGINT | PK, AUTO_INCREMENT | Khóa chính |
| `batch_number` | VARCHAR(50) | NOT NULL | Số lô sản xuất (Ví dụ: `LOT-VNM-2601`) |
| `product_id` | BIGINT | FK -> products(id), NOT NULL | Sản phẩm thuộc lô |
| `manufacturing_date` | DATE | NULL | Ngày sản xuất |
| `expiry_date` | DATE | NOT NULL | Hạn sử dụng (Dùng cho FEFO & Cảnh báo) |
| *Ràng buộc duy nhất* | UK(product_id, batch_number) | | Một sản phẩm không có 2 lô trùng số hiệu |

#### Bảng `inventories` (Số lượng tồn kho theo lô)
| Tên cột | Kiểu dữ liệu | Ràng buộc | Ý nghĩa |
| :--- | :--- | :--- | :--- |
| `id` | BIGINT | PK, AUTO_INCREMENT | Khóa chính |
| `warehouse_id` | BIGINT | FK -> warehouses(id), NOT NULL | Kho lưu trữ |
| `batch_id` | BIGINT | FK -> product_batches(id), NOT NULL | Lô hàng lưu trữ |
| `quantity` | INT | NOT NULL, DEFAULT 0 | Số lượng còn tồn thực tế |
| *Ràng buộc duy nhất* | UK(warehouse_id, batch_id) | | Một kho chỉ có 1 dòng tồn cho mỗi lô |

#### Bảng `stock_movements` (Lịch sử biến động kho)
| Tên cột | Kiểu dữ liệu | Ràng buộc | Ý nghĩa |
| :--- | :--- | :--- | :--- |
| `id` | BIGINT | PK, AUTO_INCREMENT | Khóa chính |
| `warehouse_id` | BIGINT | FK, NOT NULL | Kho phát sinh biến động |
| `product_id` | BIGINT | FK, NOT NULL | Sản phẩm biến động |
| `batch_id` | BIGINT | FK, NOT NULL | Lô hàng biến động |
| `movement_type` | VARCHAR(30) | NOT NULL | `INBOUND`, `OUTBOUND`, `ADJUSTMENT` |
| `quantity` | INT | NOT NULL | Số lượng (+ với Inbound, - với Outbound) |
| `balance_after` | INT | NOT NULL | Số tồn sau giao dịch |
| `reference_code` | VARCHAR(50) | NULL | Mã phiếu nhập/xuất liên quan |

---

## 3. THIẾT KẾ QUY TRÌNH NGHIỆP VỤ & SƠ ĐỒ TUẦN TỰ (SEQUENCE DIAGRAM)

### 3.1. Sơ đồ tuần tự: Xuất kho theo thuật toán FEFO (Outbound Flow)
Đây là quy trình nghiệp vụ quan trọng nhất của đồ án, thể hiện sự kết hợp giữa **thuật toán FEFO** và **Spring Transaction**:

```mermaid
sequenceDiagram
    autonumber
    actor Staff as Nhân viên kho (Staff)
    participant Controller as IssueController
    participant Service as IssueService (@Transactional)
    participant InvRepo as InventoryRepository
    participant DB as Database (H2 / MySQL)

    Staff->>Controller: POST /api/v1/issues (warehouseId, destination, items:[productId, quantity])
    Controller->>Service: createIssue(request, username)
    
    rect rgb(240, 248, 255)
        note over Service, DB: Bắt đầu Transaction (@Transactional)
        
        loop Với từng sản phẩm yêu cầu
            Service->>InvRepo: getTotalStockByWarehouseAndProduct(warehouseId, productId)
            InvRepo->>DB: SELECT COALESCE(SUM(quantity), 0) ...
            DB-->>InvRepo: Trả về totalStock
            
            alt totalStock < requestedQuantity
                Service-->>Controller: Ném InsufficientStockException (Rollback)
                Controller-->>Staff: HTTP 400 Bad Request ("Tồn không đủ")
            end
            
            Service->>InvRepo: findByWarehouseIdAndBatch_ProductId...OrderByExpiryDateAsc
            InvRepo->>DB: SELECT * FROM inventories WHERE qty > 0 ORDER BY expiry_date ASC
            DB-->>InvRepo: Danh sách các lô (Lô cận date xếp trước)
            
            loop Phân bổ số lượng cần trừ
                Service->>Service: deduct = min(inv.quantity, remaining)
                Service->>InvRepo: save(inv.quantity - deduct)
                Service->>DB: INSERT INTO issue_items (gắn batch_id)
                Service->>DB: INSERT INTO stock_movements (OUTBOUND, -deduct)
            end
        end
        
        Service->>DB: INSERT INTO stock_issues
        note over Service, DB: Commit Transaction thành công
    end
    
    Service-->>Controller: Trả về IssueResponse DTO
    Controller-->>Staff: HTTP 201 Created (Kèm chi tiết các lô đã xuất)
```

---

## 4. TỔ CHỨC CẤU TRÚC GÓI MÃ NGUỒN (PACKAGE ARCHITECTURE)

```text
com.winmart.wms
│
├── WinmartWmsApplication.java          # Lớp Boot khởi chạy ứng dụng
│
├── common/                             # Các thành phần tái sử dụng toàn hệ thống
│   ├── ApiResponse.java                # Chuẩn hóa vỏ bọc kết quả {success, message, data, timestamp}
│   ├── PageResponse.java               # Chuẩn hóa phân trang {content, pageNo, pageSize, totalElements}
│   └── BaseEntity.java                 # Entity cha cung cấp id, createdAt, updatedAt
│
├── config/                             # Cấu hình Spring Context
│   ├── AuditingConfig.java             # Kích hoạt JPA Auditing
│   ├── OpenApiConfig.java              # Cấu hình Swagger UI 3 tích hợp Bearer JWT
│   └── DataInitializer.java           # CommandLineRunner nạp dữ liệu mẫu ban đầu
│
├── entity/                             # Các thực thể JPA ánh xạ CSDL
│   ├── enums/                          # RoleName, ReceiptStatus, IssueType, MovementType...
│   ├── User.java, Role.java
│   ├── Category.java, Supplier.java, Warehouse.java, Product.java
│   ├── ProductBatch.java, Inventory.java
│   ├── StockReceipt.java, ReceiptItem.java
│   ├── StockIssue.java, IssueItem.java
│   └── StockMovement.java
│
├── repository/                         # Tầng thao tác dữ liệu Spring Data JPA
│   ├── UserRepository.java, RoleRepository.java
│   ├── CategoryRepository.java, SupplierRepository.java, WarehouseRepository.java
│   ├── ProductRepository.java, ProductBatchRepository.java
│   ├── InventoryRepository.java       # Các câu truy vấn FEFO và cảnh báo cận date
│   ├── StockReceiptRepository.java, StockIssueRepository.java
│   └── StockMovementRepository.java
│
├── dto/                                # Data Transfer Objects
│   ├── auth/ (LoginRequest, RegisterRequest, AuthResponse, UserProfileResponse)
│   ├── category/ (CategoryRequest, CategoryResponse)
│   ├── supplier/ (SupplierRequest, SupplierResponse)
│   ├── warehouse/ (WarehouseRequest, WarehouseResponse)
│   ├── product/ (ProductRequest, ProductResponse)
│   ├── receipt/ (ReceiptCreateRequest, ReceiptResponse...)
│   ├── issue/ (IssueCreateRequest, IssueResponse...)
│   ├── inventory/ (InventoryResponse, ExpiringBatchResponse, LowStockResponse...)
│   └── report/ (DashboardStatsResponse)
│
├── service/                            # Tầng xử lý nghiệp vụ chính
│   ├── AuthService.java
│   ├── CategoryService.java, SupplierService.java, WarehouseService.java
│   ├── ProductService.java
│   ├── ReceiptService.java            # Nghiệp vụ nhập kho & tăng tồn
│   ├── IssueService.java              # Nghiệp vụ xuất kho FEFO & trừ tồn
│   ├── InventoryService.java          # Cảnh báo date, tồn an toàn, biến động kho
│   └── ReportService.java             # Thống kê Dashboard
│
├── controller/                         # Tầng RESTful Controller
│   ├── AuthController.java
│   ├── CategoryController.java, SupplierController.java, WarehouseController.java
│   ├── ProductController.java
│   ├── ReceiptController.java
│   ├── IssueController.java
│   ├── InventoryController.java
│   └── ReportController.java
│
├── security/                           # Tầng bảo mật Spring Security & JWT
│   ├── CustomUserDetails.java
│   ├── UserDetailsServiceImpl.java
│   ├── JwtTokenProvider.java
│   ├── JwtAuthenticationFilter.java
│   └── SecurityConfig.java
│
└── exception/                          # Tầng xử lý lỗi tập trung
    ├── ErrorCode.java
    ├── AppException.java
    ├── ResourceNotFoundException.java
    ├── InsufficientStockException.java
    ├── BadRequestException.java
    └── GlobalExceptionHandler.java
```
