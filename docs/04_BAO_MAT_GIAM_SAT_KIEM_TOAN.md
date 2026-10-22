# TÀI LIỆU 04: BẢO MẬT, GIAO DỊCH, LOGGING, GIÁM SÁT HIỆU NĂNG, KIỂM TOÁN & XỬ LÝ LỖI
## Đề tài: Hệ Thống Quản Lý Kho Chuỗi Siêu Thị WinMart (WinMart WMS)

Tài liệu mô tả các thành phần **cắt ngang (cross-cutting concerns)** của hệ thống: cách hoạt động, cấu hình và cách kiểm chứng.

```text
Request ─► RequestLoggingFilter ─► Spring Security (JwtAuthenticationFilter, RBAC) ─► Controller
           (requestId, access log)    │ 401/403 JSON + audit                           │ @Valid
                                      ▼                                                ▼
                              GlobalExceptionHandler ◄──────── Service  ◄── PerformanceMonitoringAspect (Timer, slow log)
                              (JSON thống nhất, không lộ lỗi)      │      ◄── AuditAspect (@Audited → audit_logs + audit.log)
                                                                   ▼
                                                  @Transactional + khóa bi quan (FEFO, chống xuất lố)
```

---

## 1. LOGGING

| Thành phần | Mô tả |
| :--- | :--- |
| `logback-spring.xml` | Console có màu; file `logs/app.log` (30 ngày), `logs/error.log` (chỉ WARN+, 60 ngày), `logs/audit.log` (365 ngày). Rolling theo ngày + dung lượng, nén `.gz`, ghi bất đồng bộ. |
| `RequestLoggingFilter` | Sinh/nhận `X-Request-Id`, đưa vào **MDC** cùng IP và username → **mọi dòng log** của 1 request mang cùng mã. Trả mã về client qua header và trong body lỗi. Ghi 1 dòng access-log: `METHOD URI -> status (ms)`. |
| Chống log injection | Chỉ chấp nhận `X-Request-Id` khớp `[A-Za-z0-9._-]{1,64}`, giá trị khác bị thay bằng id sinh mới. |
| Không log dữ liệu nhạy cảm | Không log body, header, query string, mật khẩu, token. |

Mẫu dòng log: `07:50:30.737  INFO [c8c46fe8a1154b04] [admin] c.w.w.…GlobalExceptionHandler : …` → định dạng `[requestId] [username] logger : message`.

Đổi thư mục log: biến môi trường `LOG_PATH` (mặc định `logs/`). Đổi mức log lúc đang chạy (ADMIN): `POST /actuator/loggers/com.winmart.wms` với body `{"configuredLevel":"DEBUG"}`.

---

## 2. SECURITY

### 2.1. Các lỗ hổng đã vá so với bản đầu
| Vấn đề | Cách xử lý |
| :--- | :--- |
| `POST /auth/register` **công khai và cho tự chọn `ROLE_ADMIN`** (ai cũng tự cấp quyền quản trị) | Chỉ **ADMIN** được tạo tài khoản (`@PreAuthorize`), trả `201 Created`. |
| Dò mật khẩu không giới hạn | `LoginAttemptService`: sai **5 lần** liên tiếp (theo cặp IP + username, kể cả username không tồn tại) → khóa 15 phút, HTTP `423` + header `Retry-After`. Cấu hình `app.security.login.*`. |
| Token không thu hồi được | `jti` + `TokenBlacklistService`: `POST /auth/logout` và đổi mật khẩu làm token hiện tại vô hiệu ngay. |
| Tài khoản bị khóa vẫn dùng được token cũ | `JwtAuthenticationFilter` kiểm tra `isEnabled()` ở mọi request. |
| JWT secret nằm cứng trong repo | Đọc từ biến môi trường `JWT_SECRET`; từ chối khởi động nếu < 32 ký tự; cảnh báo khi còn dùng secret demo. Thêm kiểm tra `issuer`. |
| CORS `*` kèm credentials | Chỉ các origin trong `app.security.cors.allowed-origins` (env `CORS_ALLOWED_ORIGINS`), không credentials. |
| H2 Console mở cho mọi IP | `web-allow-others=false`; chỉ mở khi `spring.h2.console.enabled=true`; profile `mysql` tắt hẳn. Header `X-Frame-Options: DENY` khi không dùng H2. |
| 401/403 trả body rỗng | `RestAuthenticationEntryPoint` / `RestAccessDeniedHandler` trả JSON chuẩn; 403 được ghi audit. |
| Mật khẩu yếu (≥ 6 ký tự) | ≥ 8 ký tự, có chữ và số, tối đa 72 (giới hạn BCrypt). |
| `sort` tùy ý, `size` không giới hạn | Whitelist trường sắp xếp; `size` tối đa 100. |
| Actuator | `/actuator/health`, `/actuator/info` công khai; còn lại (`metrics`, `loggers`) chỉ ADMIN. |

### 2.2. Phân quyền (RBAC)
| Chức năng | ADMIN | MANAGER | STAFF |
| :--- | :-: | :-: | :-: |
| Xem dữ liệu, tạo phiếu nhập/xuất | ✔ | ✔ | ✔ |
| Tạo/sửa sản phẩm, kho, NCC, ngành hàng | ✔ | ✔ | ✖ |
| **Hủy** phiếu nhập/xuất | ✔ | ✔ | ✖ |
| Xóa danh mục | ✔ | ✖ | ✖ |
| Tạo tài khoản, quản lý người dùng, xem audit log, metrics | ✔ | ✖ | ✖ |

### 2.3. Giới hạn đã biết
Lockout và danh sách thu hồi token lưu **trong bộ nhớ** (đủ cho 1 instance). Khi chạy nhiều instance cần chuyển sang Redis/DB dùng chung.

---

## 3. TRANSACTION & TOÀN VẸN TỒN KHO

1. **Phân tách đọc/ghi**: mọi service mặc định `@Transactional(readOnly = true)`; phương thức ghi khai báo `@Transactional` riêng. (Việc này cũng sửa lỗi `LazyInitializationException` khi liệt kê phiếu nhập/xuất vì `open-in-view=false`.)
2. **All-or-nothing**: phiếu + lô + tồn kho + `StockMovement` commit hoặc rollback cùng nhau. Phiếu 2 dòng mà dòng 2 thiếu hàng → dòng 1 không bị trừ (có test).
3. **Chống xuất lố khi đồng thời**: `findAvailableStockForUpdate` dùng **khóa bi quan** (`SELECT … FOR UPDATE`) trên các lô của sản phẩm; hai phiếu xuất song song được tuần tự hóa. Các sản phẩm trong 1 phiếu luôn được khóa theo thứ tự `productId` tăng dần để tránh **deadlock**. Test: 2 luồng cùng xuất 80 từ tồn 100 → đúng 1 thành công, tồn còn 20.
4. **Chốt chặn CSDL**: `CHECK (quantity >= 0)` trên bảng `inventories`.
5. **Quy tắc hạn sử dụng** (dùng mã lỗi `PRODUCT_EXPIRED` có sẵn):
   - `STORE_TRANSFER`: chỉ xuất lô **còn hạn**. `DISPOSAL_EXPIRED`: chỉ xuất (hủy) lô **đã hết hạn**. `RETURN_SUPPLIER`: mọi lô.
   - Nhập kho: từ chối lô đã hết hạn, ngày sản xuất vô lý, và HSD mâu thuẫn với lô đã tồn tại.
6. **Hủy phiếu (bù trừ giao dịch)**: `POST /receipts/{id}/cancel`, `POST /issues/{id}/cancel` — hoàn/trừ tồn đúng lô, ghi bút toán `ADJUSTMENT`, khóa dòng phiếu nên **chỉ hủy được một lần**; hủy phiếu nhập bị từ chối nếu hàng đã xuất đi.
7. **Sự kiện sau commit**: `StockChangedEvent` + `@TransactionalEventListener(AFTER_COMMIT)` → `LowStockAlertListener` cảnh báo tồn thấp **chỉ khi giao dịch thật sự commit**.
8. **Mã chứng từ**: `DocumentCodeGenerator` (`PXK-yyyyMMdd-XXXXXX`) kiểm tra trùng và thử lại, thay cho số ngẫu nhiên 4 chữ số dễ va chạm.

---

## 4. GIÁM SÁT HIỆU NĂNG (PERFORMANCE MONITORING)

| Cơ chế | Cách xem |
| :--- | :--- |
| `PerformanceMonitoringAspect`: Timer `wms.service.execution` (tag `class`, `method`, `outcome` = `success`/`business_error`/`error`), có histogram phân vị | `GET /actuator/metrics/wms.service.execution` (ADMIN) |
| Log `SLOW SERVICE` khi service chậm hơn `app.performance.slow-service-threshold-ms` (500 ms); `SLOW REQUEST` khi HTTP chậm hơn 1000 ms | `logs/app.log` |
| Hibernate: log SQL chậm hơn 200 ms (logger `org.hibernate.SQL_SLOW`) + Hibernate Statistics → metric `hibernate.*` | `logs/app.log`, `/actuator/metrics` |
| Metric HTTP có sẵn của Spring (`http.server.requests`), JVM, HikariCP | `/actuator/metrics` |
| Metric nghiệp vụ `wms.business.events` (tag `event`: `receipt_created`, `issue_created`, `issue_cancelled`, `login`, `stock_rejected`, `low_stock_alert`…) | `/actuator/metrics/wms.business.events?tag=event:issue_created` |
| Health check cho load balancer / Kubernetes | `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness` |

**Tối ưu truy vấn đã thực hiện**: Dashboard dùng `SUM/COUNT` tại CSDL thay vì nạp toàn bộ tồn kho lên bộ nhớ; `@EntityGraph` cho danh sách tồn kho/phiếu nhập/phiếu xuất (tránh N+1); `default_batch_fetch_size=50`; `lowStockProductsCount` trước đây luôn bằng 0 nay được tính thật.

---

## 5. AUDITING (KIỂM TOÁN)

Hai tầng bổ trợ nhau:

1. **JPA Auditing (dữ liệu)** — mọi entity kế thừa `BaseEntity` tự có `createdAt`, `updatedAt`, **`createdBy`, `updatedBy`** (username, hoặc `system` cho tác vụ nền) qua `AuditorAware`.
2. **Audit trail (hành động)** — bảng bất biến `audit_logs` (`@Immutable`, không FK để còn vết khi dữ liệu gốc bị xóa) **và** file `logs/audit.log`:
   - Gắn `@Audited(action = …, entity = "…")` lên phương thức service → `AuditAspect` tự ghi **SUCCESS sau khi transaction commit** hoặc **FAILURE (kèm lý do nghiệp vụ) sau khi rollback**.
   - Ghi tường minh: đăng nhập thành công/thất bại, khóa tài khoản, đăng xuất, đổi mật khẩu, kích hoạt/vô hiệu hóa user, **truy cập bị từ chối (403)**.
   - Mỗi bản ghi có: ai, hành động, đối tượng + id, kết quả, IP, `requestId` (nối được với dòng log kỹ thuật), thời điểm.
   - Ghi bằng transaction **độc lập** (`REQUIRES_NEW`): bản ghi lỗi không bị rollback theo nghiệp vụ; lỗi ghi audit không làm hỏng nghiệp vụ chính.
   - Tra cứu (chỉ ADMIN): `GET /api/v1/audit-logs?username=&action=&entityType=&success=&from=&to=&page=&size=`.

Quan hệ với `StockMovement`: `StockMovement` là sổ cái số lượng theo lô (nghiệp vụ, có số dư); `audit_logs` trả lời "ai đã thao tác gì" (kiểm soát).

---

## 6. EXCEPTION HANDLING

Mọi lỗi trả về cùng cấu trúc:
```json
{
  "success": false,
  "errorCode": "INSUFFICIENT_STOCK",
  "message": "Sản phẩm [VNM-100-1L - …] không đủ tồn kho. Tồn hiện tại: 250, Yêu cầu xuất: 99999",
  "path": "/api/v1/issues",
  "requestId": "c8c46fe8a1154b04",
  "timestamp": "2026-10-01T07:50:30.737"
}
```
| Tình huống | HTTP | `errorCode` |
| :--- | :-: | :--- |
| Validation `@Valid` (kèm `data` = map trường → lỗi) | 400 | `VALIDATION_ERROR` |
| JSON sai cú pháp / enum sai (nêu rõ tên trường) | 400 | `MALFORMED_REQUEST` |
| Tham số sai kiểu, thiếu tham số, `sort` sai trường | 400 | `BAD_REQUEST` |
| Không đủ tồn / hàng hết hạn | 400 | `INSUFFICIENT_STOCK` / `PRODUCT_EXPIRED` |
| Chưa đăng nhập / token sai, hết hạn, bị thu hồi | 401 | `UNAUTHORIZED` |
| Sai tên đăng nhập hoặc mật khẩu (không phân biệt, chống dò user) | 401 | `INVALID_CREDENTIALS` |
| Không đủ quyền / tài khoản bị vô hiệu | 403 | `FORBIDDEN` / `ACCOUNT_DISABLED` |
| Không tìm thấy dữ liệu / đường dẫn | 404 | `RESOURCE_NOT_FOUND` |
| Sai method / media type | 405 / 415 | `METHOD_NOT_ALLOWED` / `UNSUPPORTED_MEDIA_TYPE` |
| Trùng dữ liệu, xóa dữ liệu đang được tham chiếu, thao tác sai trạng thái chứng từ | 409 | `DUPLICATE_RESOURCE` / `DATA_INTEGRITY_VIOLATION` / `INVALID_STATE` |
| Va chạm đồng thời (deadlock, lock timeout) — client nên thử lại | 409 | `CONCURRENT_UPDATE` |
| Bị khóa do đăng nhập sai nhiều lần (`Retry-After`) | 423 | `ACCOUNT_LOCKED` |
| Lỗi không lường trước | 500 | `INTERNAL_SERVER_ERROR` |

Nguyên tắc: lỗi client → log `WARN` một dòng; lỗi hệ thống → log `ERROR` đầy đủ stack trace **nhưng client chỉ nhận thông điệp chung + `requestId`** (bản cũ trả thẳng `ex.getMessage()` ra ngoài, có thể lộ tên bảng/cột/SQL). Trước đây `404` đường dẫn lạ và `409` khi xóa dữ liệu đang tham chiếu đều rơi vào 500.

---

## 7. KIỂM THỬ

`mvn test` chạy 31 test tích hợp (toàn bộ filter chain + H2) trong `src/test/java`:
- `StockTransactionTest` — FEFO, rollback toàn phần, **đồng thời không xuất lố**, hủy phiếu 1 lần, quy tắc hạn sử dụng, liệt kê phiếu, audit nghiệp vụ, dashboard.
- `SecurityAndErrorHandlingTest` — 401/403 JSON, lockout, logout, vô hiệu hóa user, đổi mật khẩu, RBAC, audit đăng nhập/403, actuator, Timer hiệu năng, toàn bộ bảng mã lỗi.

Đã kiểm chứng test đồng thời có giá trị: bỏ `@Lock` thì test **fail** (hai phiếu cùng thành công, xuất lố tồn).

## 8. BIẾN MÔI TRƯỜNG

| Biến | Ý nghĩa | Mặc định |
| :--- | :--- | :--- |
| `JWT_SECRET` | Khóa ký JWT (≥ 32 ký tự) — **bắt buộc đặt khi triển khai thật** | secret demo (có cảnh báo) |
| `JWT_EXPIRATION_MS` | Thời hạn token | 86400000 (24h) |
| `CORS_ALLOWED_ORIGINS` | Danh sách origin, phân cách bằng dấu phẩy | localhost:3000/4200/5173/8080 |
| `LOG_PATH` | Thư mục log | `logs` |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Kết nối MySQL (profile `mysql`) | localhost / root / rootpassword |
