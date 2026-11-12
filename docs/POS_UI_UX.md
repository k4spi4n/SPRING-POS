# Giao diện quầy xuất kho

## Phạm vi và người dùng

Nhân viên kho, thủ kho và quản trị viên dùng màn đăng nhập và quầy xuất kho. Project là Spring Boot WMS, có sản phẩm, kho, lô hàng và phiếu `STORE_TRANSFER`; chưa có nghiệp vụ thu tiền bán lẻ. Giao diện tiếp tục dùng HTML/CSS/JavaScript trong `src/main/resources/static`, JWT Bearer trong bộ nhớ tab và API cùng origin. Không thêm framework, dependency, màn quản trị hoặc chức năng thanh toán.

Không tìm thấy `AGENTS.md` trong repository lúc khảo sát. Các thay đổi trước đó trong workspace được giữ và phát triển tiếp.

## Nguồn tham khảo

Đọc ngày 29/10/2026. Đây là tài liệu thao tác ứng dụng chính thức, không dùng bố cục landing page quảng cáo làm giao diện POS.

| Nguồn | Bằng chứng liên quan | Quyết định áp dụng |
| --- | --- | --- |
| [Shopify POS — Searching for products](https://help.shopify.com/en/manual/sell-in-person/shopify-pos/inventory-management/searching-for-products) | Tìm kiếm hiển thị kết quả cạnh giỏ; có bộ lọc và sắp xếp | Danh mục và đơn cùng màn hình trên desktop; giữ từ khóa khi thêm hàng |
| [Square POS — Set up item grid](https://squareup.com/help/us/en/article/8334-set-up-item-grid) và [Build your customer's cart](https://squareup.com/help/us/en/article/8238-build-your-customer-s-cart-in-the-square-retail-pos-app) | Chọn ô sản phẩm để thêm, tìm bằng từ khóa/SKU, chỉnh số lượng trong giỏ | Thẻ gọn, nút thêm rõ ràng, báo số lượng đã chọn và sửa số lượng tại dòng |
| [KiotViet — Bán hàng](https://www.kiotviet.vn/huong-dan-su-dung-kiotviet/retail-ban-hang/ban-hang/?industry=retailer) | Hướng dẫn tìm tên/mã, lọc nhóm, chỉnh dòng hàng; kèm ảnh màn hình ứng dụng | Ngôn ngữ tiếng Việt, lọc nhóm, giá/tổng căn phải, thao tác bàn phím và chạm |

Trang Odoo được thử nhưng timeout, không dùng làm căn cứ thiết kế. Văn bản và liên kết ảnh KiotViet truy cập được qua công cụ web; trình duyệt tương tác không cung cấp kết quả đáng tin cậy để quan sát ảnh. Không sao chép thương hiệu, nội dung hay tài sản của các sản phẩm tham khảo.

## Hệ thống giao diện

- Nhận diện đỏ–trắng, tham khảo [website WinMart chính thức](https://winmart.vn/info/about): đỏ `#ED1C24` cho logo/đường nhấn, đỏ đậm `#C8101E` cho nút và chữ nhỏ để đủ tương phản. Nền `#f7f7f8`, bề mặt trắng, chữ phụ `#665d62`, viền trường `#97868e`. Xanh chỉ dùng cho phản hồi thành công; trạng thái luôn có chữ/biểu tượng đi kèm.
- Font hệ thống Segoe UI/system-ui, không tải font hoặc tài nguyên ngoài. Tên hàng, giá và hành động chính có phân cấp; tiền dùng `Intl.NumberFormat('vi-VN')`, tối đa hai chữ số thập phân và chữ số có độ rộng đồng nhất.
- Khoảng cách theo nhịp 4/8px, vùng thao tác chính từ 44px, bo góc trường 8px và bề mặt 12px. Container chung dùng grid/flex; giỏ nằm cạnh danh mục khi đủ chiều rộng và chuyển xuống dưới trên màn hẹp.
- Lề header/nội dung dùng chung `--page-gutter`; hai panel dùng `--panel-padding`, khoảng cách khối dùng `--section-gap`. Nhãn–ô nhập–lỗi cách nhau 8px; nhóm trường cách nhau 16px bằng grid, không phụ thuộc phần thông báo đang ẩn hay hiện. Khung tìm kiếm căn cùng mép với bộ lọc; tiêu đề hai cột có cùng cấu trúc.
- Hover, focus-visible, active, disabled và loading có biểu hiện riêng. Chuyển trạng thái 120ms; `prefers-reduced-motion` tắt animation/transition. Spinner luôn đi cùng mô tả đang tải.
- Sản phẩm có minh họa SVG vẽ riêng: hộp sữa `VNM-100-1L`, gói mì `ACK-HH-TC`, chai nước mắm `MSN-NN-500`; cùng một hình xuất hiện trong thẻ và thumbnail giỏ hàng. Tất cả nhúng trong JavaScript, không hotlink/tải ảnh ngoài, không cần mở thêm đường dẫn public. Hình có alt, kích thước ổn định và nhãn “Hình minh họa”. SKU khác dùng hình kiện hàng dự phòng. Đây là minh họa phân biệt mặt hàng, không phải ảnh chụp bao bì thực; API chưa có trường ảnh nên chưa thêm thư viện ảnh/upload ngoài phạm vi.

## Tương tác và dữ liệu thật

Tìm tên/SKU, lọc nhóm hàng và sắp xếp đều gọi `GET /api/v1/products`. `categoryId` là tham số đọc tùy chọn bổ sung; tìm kiếm, lọc và phân trang kết hợp tại database để tổng kết quả đúng. Nhóm hàng lấy từ `GET /api/v1/categories`. Tham số cũ và quyền truy cập không đổi.

Giỏ có nhập số lượng và nút tăng/giảm, nhãn theo từng sản phẩm, tổng tạm tính và phản hồi đã chọn trên thẻ. Xóa một dòng có thể hoàn tác cho đến thao tác thay đổi giỏ tiếp theo. Xóa toàn giỏ/đăng xuất khi còn hàng yêu cầu xác nhận bằng `<dialog>`; nút giữ bản nháp là lựa chọn focus mặc định. Dialog dùng cơ chế focus/modal/Escape của trình duyệt. Tải lại/đóng tab khi còn giỏ có cảnh báo chuẩn `beforeunload` nếu trình duyệt cho phép.

Lỗi required/số lượng và validation API hiển thị tại trường, liên kết bằng `aria-describedby`/`aria-invalid`; lỗi chung có thông báo và nút thử lại phù hợp. Không xóa dữ liệu khi lỗi. Nội dung từ API được đưa vào bằng `textContent`.

Đang gửi phiếu thì khóa chỉnh giỏ, đăng xuất và tải lại. Chỉ báo thành công khi backend trả xác nhận; dùng tổng và chi tiết lô do backend trả. Không gửi giá hoặc tổng tiền từ client. Chi tiết lô có bảng ngữ nghĩa, cột tiền căn phải và cuộn ngang trong vùng riêng. API chưa có idempotency key: nếu mất phản hồi sau commit, cần kiểm tra phiếu trước khi gửi lại.

## Kiểm chứng và giới hạn

Chạy `mvn package` với JDK 21 và `node --test src/test/js/pos.test.cjs`. Kiểm thử bổ sung gồm lọc nhóm kết hợp SKU trước phân trang, không rò kết quả từ nhóm khác, nút số lượng, hoàn tác, xác nhận xóa giỏ, validation tại trường và tương phản của các màu chữ/viền chính trên nền trắng. Đây chỉ là các kiểm tra thành phần, không phải chứng nhận WCAG.

Lần đổi màu/hình: 15 test JavaScript qua, gồm hình khác nhau theo SKU, dùng chung hình giữa danh mục/giỏ và fallback an toàn; kiểm tra lại tương phản palette đỏ. Đóng gói bằng `mvn -DskipTests package`, không thay đổi hoặc chạy lại Java.

Sửa lỗi ghi chú: thẻ `<textarea>` thiếu thẻ đóng làm trình duyệt đưa phần còn lại của HTML vào giá trị ghi chú, nuốt các điều khiển phía dưới. Đã thêm `</textarea>` và hai kiểm thử hồi quy: ghi chú mặc định rỗng/các điều khiển nằm ngoài textarea; ghi chú nhiều dòng được gửi và giữ khi lỗi, chỉ xóa sau thành công. Bộ JavaScript hiện có 14 test qua. Lần sửa HTML/CSS này đóng gói bằng `mvn -DskipTests package`; không thay đổi Java và không chạy lại bộ Java. Đã xác nhận lỗi HTML trên accessibility tree của trang cũ; thao tác trình duyệt sau đó bị lỗi kết nối nên chưa kiểm chứng trực quan bản sửa.

| Chiều rộng yêu cầu | Quy tắc đã triển khai | Kiểm tra trực quan |
| --- | --- | --- |
| 360px | Một cột nội dung, thẻ hàng hai cột, bộ lọc xếp dọc, điều hướng xuống hàng | Chưa xác minh |
| 768px | Danh mục ba cột, đơn nằm dưới; không dùng giỏ cuộn lồng | Chưa xác minh |
| 1024px | Danh mục hai cột và đơn bên phải rộng 360px | Chưa xác minh |
| 1440px | Danh mục ba cột và đơn bên phải rộng 420px | Chưa xác minh |

Công cụ IAB không khả dụng; công cụ cửa sổ Brave báo thiếu geometry, phát hiện input khác và trả ảnh VS Code dù đã chọn Brave. Vì vậy chưa có ảnh giao diện đáng tin cậy, chưa quan sát console, chưa xác minh focus/Escape thực tế hoặc tràn ngang bằng trình duyệt. Không tuyên bố đạt WCAG 2.2 AA, không dùng ảnh mô phỏng thay cho bằng chứng. Cần kiểm tra lại các độ rộng trên và bàn phím/screen reader khi công cụ trình duyệt hoạt động.
