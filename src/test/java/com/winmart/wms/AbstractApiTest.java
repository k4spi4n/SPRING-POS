package com.winmart.wms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Nền tảng chung cho các integration test: boot toàn bộ ứng dụng với H2 + dữ liệu mẫu của DataInitializer,
 * và các hàm tiện ích gọi API thật (qua toàn bộ filter chain bảo mật).
 * Mỗi test tự tạo sản phẩm riêng (SKU duy nhất) nên độc lập với nhau và với dữ liệu mẫu.
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "logging.file.path=target/test-logs",
        "app.security.login.max-attempts=5"
})
public abstract class AbstractApiTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", password))))
                .andReturn();
        if (result.getResponse().getStatus() != 200) {
            throw new IllegalStateException("Đăng nhập thất bại: " + result.getResponse().getContentAsString());
        }
        return body(result).path("data").path("token").asText();
    }

    protected String adminToken() throws Exception {
        return login("admin", "admin123");
    }

    protected String managerToken() throws Exception {
        return login("manager", "manager123");
    }

    protected String staffToken() throws Exception {
        return login("staff", "staff123");
    }

    protected MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder, String token) {
        return builder.header("Authorization", "Bearer " + token);
    }

    protected MockHttpServletRequestBuilder jsonPost(String path, String token, Object body) throws Exception {
        MockHttpServletRequestBuilder builder = post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body));
        return token == null ? builder : authed(builder, token);
    }

    protected MockHttpServletRequestBuilder authedGet(String path, String token) {
        return authed(get(path), token);
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    protected JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    // ----------------------------------------------------------------- Dữ liệu nghiệp vụ

    protected long idOfByCode(String listPath, String code, String token) throws Exception {
        JsonNode list = body(mockMvc.perform(authedGet(listPath, token)).andReturn()).path("data");
        for (JsonNode node : list) {
            if (code.equals(node.path("code").asText())) {
                return node.path("id").asLong();
            }
        }
        throw new IllegalStateException("Không tìm thấy " + code + " tại " + listPath);
    }

    protected long hubWarehouseId(String token) throws Exception {
        return idOfByCode("/api/v1/warehouses", "WH-MB-01", token);
    }

    /** Tạo sản phẩm mới với SKU duy nhất; trả về id sản phẩm. */
    protected long createProduct(String token, int minStock) throws Exception {
        long categoryId = idOfByCode("/api/v1/categories", "CAT-MILK", token);
        String sku = "TEST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        MvcResult result = mockMvc.perform(jsonPost("/api/v1/products", token, Map.of(
                        "sku", sku,
                        "name", "Sản phẩm kiểm thử " + sku,
                        "unit", "Hộp",
                        "purchasePrice", 10000,
                        "sellingPrice", 15000,
                        "minStockLevel", minStock,
                        "categoryId", categoryId)))
                .andReturn();
        if (result.getResponse().getStatus() != 201) {
            throw new IllegalStateException("Không tạo được sản phẩm: " + result.getResponse().getContentAsString());
        }
        return body(result).path("data").path("id").asLong();
    }

    protected Map<String, Object> receiptItem(long productId, String batch, LocalDate expiry, int qty) {
        return Map.of(
                "productId", productId,
                "batchNumber", batch,
                "manufacturingDate", LocalDate.now().minusDays(5).toString(),
                "expiryDate", expiry.toString(),
                "quantity", qty,
                "unitPrice", 10000);
    }

    protected MvcResult receive(String token, long warehouseId, List<Map<String, Object>> items) throws Exception {
        long supplierId = idOfByCode("/api/v1/suppliers", "SUP-VNM", token);
        return mockMvc.perform(jsonPost("/api/v1/receipts", token, Map.of(
                "warehouseId", warehouseId,
                "supplierId", supplierId,
                "items", items))).andReturn();
    }

    protected MvcResult issue(String token, long warehouseId, String type, long productId, int qty) throws Exception {
        return mockMvc.perform(jsonPost("/api/v1/issues", token, Map.of(
                "warehouseId", warehouseId,
                "destination", "WinMart+ Cầu Giấy",
                "issueType", type,
                "items", List.of(Map.of("productId", productId, "quantity", qty))))).andReturn();
    }

    /** Tổng tồn của 1 sản phẩm tại 1 kho, lấy qua API tồn kho. */
    protected int stockOf(String token, long warehouseId, long productId) throws Exception {
        JsonNode rows = body(mockMvc.perform(authedGet("/api/v1/inventory/warehouse/" + warehouseId, token)).andReturn()).path("data");
        int total = 0;
        for (JsonNode row : rows) {
            if (row.path("productId").asLong() == productId) {
                total += row.path("quantity").asInt();
            }
        }
        return total;
    }
}
