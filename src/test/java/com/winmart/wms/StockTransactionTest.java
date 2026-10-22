package com.winmart.wms;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import com.winmart.wms.entity.Inventory;
import com.winmart.wms.entity.Product;
import com.winmart.wms.entity.ProductBatch;
import com.winmart.wms.entity.Warehouse;
import com.winmart.wms.repository.InventoryRepository;
import com.winmart.wms.repository.ProductBatchRepository;
import com.winmart.wms.repository.ProductRepository;
import com.winmart.wms.repository.WarehouseRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Kiểm chứng tính đúng đắn của giao dịch kho: FEFO, rollback toàn phần, chống xuất lố khi đồng thời,
 * hủy phiếu hoàn tồn đúng lô, quy tắc hạn sử dụng, audit trail.
 */
class StockTransactionTest extends AbstractApiTest {

    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ProductBatchRepository batchRepository;
    @Autowired
    private InventoryRepository inventoryRepository;
    @Autowired
    private WarehouseRepository warehouseRepository;

    @Test
    @DisplayName("FEFO: xuất 150 khi có lô cận date 100 + lô xa 300 -> lấy hết lô cận date rồi lấy thêm 50 từ lô xa")
    void fefoAllocatesNearestExpiryFirst() throws Exception {
        String manager = managerToken();
        long wh = hubWarehouseId(manager);
        long product = createProduct(manager, 10);

        // Cố ý nhập lô xa TRƯỚC để chứng minh thứ tự xuất không phụ thuộc thứ tự nhập
        receive(manager, wh, List.of(
                receiptItem(product, "FAR", LocalDate.now().plusDays(200), 300),
                receiptItem(product, "NEAR", LocalDate.now().plusDays(10), 100)));
        assertThat(stockOf(manager, wh, product)).isEqualTo(400);

        MvcResult result = issue(staffToken(), wh, "STORE_TRANSFER", product, 150);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);

        JsonNode items = body(result).path("data").path("items");
        assertThat(items).hasSize(2);
        assertThat(items.get(0).path("batchNumber").asText()).isEqualTo("NEAR");
        assertThat(items.get(0).path("quantity").asInt()).isEqualTo(100);
        assertThat(items.get(1).path("batchNumber").asText()).isEqualTo("FAR");
        assertThat(items.get(1).path("quantity").asInt()).isEqualTo(50);
        assertThat(stockOf(manager, wh, product)).isEqualTo(250);
    }

    @Test
    @DisplayName("Không đủ tồn: 400 INSUFFICIENT_STOCK và toàn bộ phiếu bị rollback (không trừ dở dang, không sinh biến động)")
    void insufficientStockRollsBackEverything() throws Exception {
        String manager = managerToken();
        long wh = hubWarehouseId(manager);
        long enough = createProduct(manager, 10);
        long scarce = createProduct(manager, 10);
        receive(manager, wh, List.of(
                receiptItem(enough, "A1", LocalDate.now().plusDays(90), 100),
                receiptItem(scarce, "B1", LocalDate.now().plusDays(90), 5)));

        // Phiếu 2 dòng: dòng 1 đủ hàng (đã trừ trong bộ nhớ), dòng 2 thiếu -> phải rollback cả dòng 1
        MvcResult result = mockMvc.perform(jsonPost("/api/v1/issues", manager, Map.of(
                "warehouseId", wh, "destination", "WinMart+ Test", "issueType", "STORE_TRANSFER",
                "items", List.of(
                        Map.of("productId", enough, "quantity", 40),
                        Map.of("productId", scarce, "quantity", 50))))).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result).path("errorCode").asText()).isEqualTo("INSUFFICIENT_STOCK");
        assertThat(stockOf(manager, wh, enough)).isEqualTo(100);
        assertThat(stockOf(manager, wh, scarce)).isEqualTo(5);

        JsonNode movements = body(mockMvc.perform(authedGet("/api/v1/inventory/movements?warehouseId=" + wh, manager)).andReturn()).path("data");
        for (JsonNode m : movements) {
            assertThat(m.path("movementType").asText().equals("OUTBOUND")
                    && m.path("productSku").asText().startsWith("TEST-")
                    && m.path("quantity").asInt() == -40).isFalse();
        }
    }

    @Test
    @DisplayName("Đồng thời: 2 phiếu xuất 80 cùng lúc từ tồn 100 -> đúng 1 phiếu thành công, tồn không bao giờ âm")
    void concurrentIssuesCannotOversell() throws Exception {
        String manager = managerToken();
        String staff = staffToken();
        long wh = hubWarehouseId(manager);
        long product = createProduct(manager, 1);
        receive(manager, wh, List.of(receiptItem(product, "C1", LocalDate.now().plusDays(60), 100)));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);
        Callable<Integer> task = () -> {
            startGate.await();
            return issue(staff, wh, "STORE_TRANSFER", product, 80).getResponse().getStatus();
        };
        Future<Integer> first = pool.submit(task);
        Future<Integer> second = pool.submit(task);
        startGate.countDown();
        List<Integer> statuses = List.of(first.get(), second.get());
        pool.shutdown();

        assertThat(statuses).containsExactlyInAnyOrder(201, 400);
        assertThat(stockOf(manager, wh, product)).isEqualTo(20);
    }

    @Test
    @DisplayName("Hủy phiếu xuất: hoàn đúng tồn về đúng lô, ghi bút toán ADJUSTMENT; hủy lần 2 -> 409 INVALID_STATE")
    void cancelIssueRestoresStockOnce() throws Exception {
        String manager = managerToken();
        long wh = hubWarehouseId(manager);
        long product = createProduct(manager, 1);
        receive(manager, wh, List.of(
                receiptItem(product, "N", LocalDate.now().plusDays(10), 30),
                receiptItem(product, "F", LocalDate.now().plusDays(100), 100)));

        MvcResult issued = issue(manager, wh, "STORE_TRANSFER", product, 50);
        long issueId = body(issued).path("data").path("id").asLong();
        assertThat(stockOf(manager, wh, product)).isEqualTo(80);

        mockMvc.perform(jsonPost("/api/v1/issues/" + issueId + "/cancel", staffToken(), Map.of("reason", "nhầm")))
                .andExpect(status().isForbidden());
        mockMvc.perform(jsonPost("/api/v1/issues/" + issueId + "/cancel", manager, Map.of("reason", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        mockMvc.perform(jsonPost("/api/v1/issues/" + issueId + "/cancel", manager, Map.of("reason", "Cửa hàng hủy đơn")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancelReason").value("Cửa hàng hủy đơn"))
                .andExpect(jsonPath("$.data.cancelledBy").value("manager"));
        assertThat(stockOf(manager, wh, product)).isEqualTo(130);

        mockMvc.perform(jsonPost("/api/v1/issues/" + issueId + "/cancel", manager, Map.of("reason", "lần 2")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATE"));
        assertThat(stockOf(manager, wh, product)).isEqualTo(130);
    }

    @Test
    @DisplayName("Hủy phiếu nhập: trừ tồn nếu còn đủ; nếu hàng đã xuất đi thì từ chối và rollback")
    void cancelReceiptRules() throws Exception {
        String manager = managerToken();
        long wh = hubWarehouseId(manager);
        long product = createProduct(manager, 1);

        long receiptId = body(receive(manager, wh, List.of(receiptItem(product, "R1", LocalDate.now().plusDays(50), 100))))
                .path("data").path("id").asLong();
        issue(manager, wh, "STORE_TRANSFER", product, 30); // còn 70, thiếu 30 so với 100 cần hoàn

        mockMvc.perform(jsonPost("/api/v1/receipts/" + receiptId + "/cancel", manager, Map.of("reason", "Sai chứng từ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_STOCK"));
        assertThat(stockOf(manager, wh, product)).isEqualTo(70);

        // Phiếu nhập khác chưa động tới -> hủy được
        long untouched = createProduct(manager, 1);
        long okReceiptId = body(receive(manager, wh, List.of(receiptItem(untouched, "R2", LocalDate.now().plusDays(50), 40))))
                .path("data").path("id").asLong();
        mockMvc.perform(jsonPost("/api/v1/receipts/" + okReceiptId + "/cancel", manager, Map.of("reason", "Sai chứng từ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        assertThat(stockOf(manager, wh, untouched)).isEqualTo(0);
    }

    @Test
    @DisplayName("Nhập hàng đã hết hạn -> 400 PRODUCT_EXPIRED; HSD khác với lô đã tồn tại -> 400")
    void receiptValidationRules() throws Exception {
        String manager = managerToken();
        long wh = hubWarehouseId(manager);
        long product = createProduct(manager, 1);

        MvcResult expired = receive(manager, wh, List.of(receiptItem(product, "OLD", LocalDate.now().minusDays(1), 10)));
        assertThat(expired.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(expired).path("errorCode").asText()).isEqualTo("PRODUCT_EXPIRED");

        receive(manager, wh, List.of(receiptItem(product, "L1", LocalDate.now().plusDays(30), 10)));
        MvcResult conflict = receive(manager, wh, List.of(receiptItem(product, "L1", LocalDate.now().plusDays(99), 10)));
        assertThat(conflict.getResponse().getStatus()).isEqualTo(400);
        assertThat(stockOf(manager, wh, product)).isEqualTo(10);
    }

    @Test
    @DisplayName("Hàng hết hạn không được điều chuyển cửa hàng nhưng được xuất hủy; không xuất hủy hàng còn hạn")
    void expiredStockRules() throws Exception {
        String manager = managerToken();
        long wh = hubWarehouseId(manager);
        long productId = createProduct(manager, 1);

        // Hàng hết hạn không thể nhập qua API (bị chặn) -> tạo trực tiếp như hàng "trôi" quá hạn trong kho
        Product product = productRepository.findById(productId).orElseThrow();
        Warehouse warehouse = warehouseRepository.findById(wh).orElseThrow();
        ProductBatch expiredBatch = batchRepository.save(ProductBatch.builder()
                .batchNumber("EXPIRED-1").product(product)
                .manufacturingDate(LocalDate.now().minusMonths(6)).expiryDate(LocalDate.now().minusDays(3)).build());
        inventoryRepository.save(Inventory.builder().warehouse(warehouse).batch(expiredBatch).quantity(20).build());

        MvcResult transfer = issue(manager, wh, "STORE_TRANSFER", productId, 5);
        assertThat(transfer.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(transfer).path("errorCode").asText()).isEqualTo("PRODUCT_EXPIRED");

        receive(manager, wh, List.of(receiptItem(productId, "FRESH", LocalDate.now().plusDays(60), 10)));
        MvcResult tooMuchDisposal = issue(manager, wh, "DISPOSAL_EXPIRED", productId, 25);
        assertThat(tooMuchDisposal.getResponse().getStatus()).isEqualTo(400); // chỉ có 20 hàng hết hạn

        MvcResult disposal = issue(manager, wh, "DISPOSAL_EXPIRED", productId, 20);
        assertThat(disposal.getResponse().getStatus()).isEqualTo(201);
        assertThat(body(disposal).path("data").path("items").get(0).path("batchNumber").asText()).isEqualTo("EXPIRED-1");
        assertThat(stockOf(manager, wh, productId)).isEqualTo(10); // lô FRESH còn nguyên
    }

    @Test
    @DisplayName("Nghiệp vụ kho được ghi audit (thành công + thất bại) và JPA Auditing điền createdBy")
    void stockOperationsAreAudited() throws Exception {
        String manager = managerToken();
        long wh = hubWarehouseId(manager);
        long product = createProduct(manager, 1);
        receive(manager, wh, List.of(receiptItem(product, "AU", LocalDate.now().plusDays(60), 10)));

        MvcResult ok = issue(manager, wh, "STORE_TRANSFER", product, 4);
        long issueId = body(ok).path("data").path("id").asLong();
        assertThat(body(ok).path("data").path("createdBy").asText()).isEqualTo("manager");
        issue(manager, wh, "STORE_TRANSFER", product, 9999); // thất bại

        String admin = adminToken();
        JsonNode success = body(mockMvc.perform(authedGet(
                "/api/v1/audit-logs?entityType=StockIssue&action=CREATE&success=true&username=manager", admin)).andReturn())
                .path("data").path("content");
        assertThat(success.findValuesAsText("entityId")).contains(String.valueOf(issueId));

        JsonNode failure = body(mockMvc.perform(authedGet(
                "/api/v1/audit-logs?entityType=StockIssue&action=CREATE&success=false&username=manager", admin)).andReturn())
                .path("data").path("content");
        assertThat(failure).isNotEmpty();
        assertThat(failure.get(0).path("details").asText()).contains("không đủ tồn kho");
    }

    @Test
    @DisplayName("Liệt kê phiếu nhập/xuất trả đủ chi tiết dòng hàng (bản gốc lỗi 500 LazyInitializationException)")
    void listingReceiptsAndIssuesIncludesItems() throws Exception {
        String manager = managerToken();
        long wh = hubWarehouseId(manager);
        long product = createProduct(manager, 1);
        receive(manager, wh, List.of(receiptItem(product, "LST", LocalDate.now().plusDays(60), 10)));
        issue(manager, wh, "STORE_TRANSFER", product, 3);

        for (String path : List.of("/api/v1/receipts", "/api/v1/receipts/warehouse/" + wh,
                "/api/v1/issues", "/api/v1/issues/warehouse/" + wh)) {
            JsonNode data = body(mockMvc.perform(authedGet(path, manager)).andExpect(status().isOk()).andReturn()).path("data");
            assertThat(data).isNotEmpty();
            assertThat(data.get(0).path("items")).isNotEmpty();
        }
    }

    @Test
    @DisplayName("Dashboard tính bằng truy vấn tổng hợp và đếm đúng cảnh báo tồn thấp")
    void dashboardCountsLowStock() throws Exception {
        String manager = managerToken();
        long wh = hubWarehouseId(manager);
        long product = createProduct(manager, 500); // định mức 500 nhưng chỉ nhập 10 -> tồn thấp
        receive(manager, wh, List.of(receiptItem(product, "LOW", LocalDate.now().plusDays(60), 10)));

        mockMvc.perform(authedGet("/api/v1/reports/dashboard", manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lowStockProductsCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.totalStockQuantity").value(org.hamcrest.Matchers.greaterThan(0)));
    }
}
