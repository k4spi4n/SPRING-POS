package com.winmart.wms;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SecurityAndErrorHandlingTest extends AbstractApiTest {

    // ------------------------------------------------------------------ Xác thực

    @Test
    @DisplayName("Không có token -> 401 JSON chuẩn, kèm requestId để truy vết")
    void unauthenticatedRequestGets401Json() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/api/v1/products"))
                .andReturn();
        assertThat(body(result).path("requestId").asText())
                .isEqualTo(result.getResponse().getHeader("X-Request-Id"));
    }

    @Test
    @DisplayName("Token rác / giả mạo -> 401")
    void garbageTokenIsRejected() throws Exception {
        mockMvc.perform(authedGet("/api/v1/auth/me", "not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Client gửi X-Request-Id hợp lệ thì được giữ nguyên; giá trị độc hại bị thay thế")
    void requestIdPropagationAndSanitizing() throws Exception {
        mockMvc.perform(get("/api/v1/products").header("X-Request-Id", "trace-abc-123"))
                .andExpect(header().string("X-Request-Id", "trace-abc-123"));

        MvcResult result = mockMvc.perform(get("/api/v1/products").header("X-Request-Id", "bad id\nforged-log-line"))
                .andReturn();
        assertThat(result.getResponse().getHeader("X-Request-Id")).doesNotContain("\n").doesNotContain(" ");
    }

    @Test
    @DisplayName("Sai mật khẩu -> 401 INVALID_CREDENTIALS, không tiết lộ user có tồn tại hay không")
    void wrongPasswordDoesNotRevealUserExistence() throws Exception {
        String known = mockMvc.perform(jsonPost("/api/v1/auth/login", null, Map.of("username", "admin", "password", "sai-mat-khau-1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();
        String unknown = mockMvc.perform(jsonPost("/api/v1/auth/login", null, Map.of("username", "khong-ton-tai-" + UUID.randomUUID(), "password", "sai-mat-khau-1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(known).path("message")).isEqualTo(objectMapper.readTree(unknown).path("message"));
    }

    @Test
    @DisplayName("Sai mật khẩu quá 5 lần -> bị khóa tạm thời 423 + Retry-After, ngay cả khi lần sau nhập đúng")
    void bruteForceLockout() throws Exception {
        String username = "lockout-" + UUID.randomUUID().toString().substring(0, 8);
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(jsonPost("/api/v1/auth/login", null, Map.of("username", username, "password", "x" + i)))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(jsonPost("/api/v1/auth/login", null, Map.of("username", username, "password", "whatever")))
                .andExpect(status().isLocked())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_LOCKED"));
    }

    @Test
    @DisplayName("Đăng nhập thành công của tài khoản khác không bị ảnh hưởng bởi khóa của username khác")
    void lockoutIsPerUsername() throws Exception {
        String victim = "victim-" + UUID.randomUUID().toString().substring(0, 8);
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(jsonPost("/api/v1/auth/login", null, Map.of("username", victim, "password", "bad" + i)));
        }
        assertThat(login("staff", "staff123")).isNotBlank();
    }

    @Test
    @DisplayName("Logout thu hồi token: dùng lại token cũ -> 401")
    void logoutRevokesToken() throws Exception {
        String token = managerToken();
        mockMvc.perform(authedGet("/api/v1/auth/me", token)).andExpect(status().isOk());

        mockMvc.perform(jsonPost("/api/v1/auth/logout", token, Map.of())).andExpect(status().isOk());

        mockMvc.perform(authedGet("/api/v1/auth/me", token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    // ------------------------------------------------------------------ Phân quyền & quản lý tài khoản

    @Test
    @DisplayName("Đăng ký tài khoản không còn công khai: ẩn danh 401, STAFF 403, chỉ ADMIN được tạo")
    void registerRequiresAdmin() throws Exception {
        String username = "newuser" + UUID.randomUUID().toString().substring(0, 6);
        Map<String, Object> payload = Map.of(
                "username", username, "password", "Passw0rdOK", "fullName", "Người Dùng Mới", "role", "ROLE_ADMIN");

        mockMvc.perform(jsonPost("/api/v1/auth/register", null, payload)).andExpect(status().isUnauthorized());
        mockMvc.perform(jsonPost("/api/v1/auth/register", staffToken(), payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        String admin = adminToken();
        mockMvc.perform(jsonPost("/api/v1/auth/register", admin, payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.username").value(username));
        mockMvc.perform(jsonPost("/api/v1/auth/register", admin, payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_RESOURCE"));
    }

    @Test
    @DisplayName("Mật khẩu yếu bị từ chối với danh sách lỗi theo trường")
    void weakPasswordRejected() throws Exception {
        mockMvc.perform(jsonPost("/api/v1/auth/register", adminToken(), Map.of(
                        "username", "weak" + UUID.randomUUID().toString().substring(0, 6),
                        "password", "12345678", "fullName", "Yếu", "role", "ROLE_STAFF")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.password").exists());
    }

    @Test
    @DisplayName("Vô hiệu hóa tài khoản làm token đang dùng bị từ chối ngay; không tự vô hiệu hóa chính mình")
    void deactivatedUserLosesAccessImmediately() throws Exception {
        String admin = adminToken();
        String username = "temp" + UUID.randomUUID().toString().substring(0, 6);
        MvcResult created = mockMvc.perform(jsonPost("/api/v1/auth/register", admin, Map.of(
                "username", username, "password", "Passw0rdOK", "fullName", "Tạm thời", "role", "ROLE_STAFF"))).andReturn();
        long userId = body(created).path("data").path("id").asLong();

        String userToken = login(username, "Passw0rdOK");
        mockMvc.perform(authedGet("/api/v1/auth/me", userToken)).andExpect(status().isOk());

        mockMvc.perform(authed(patch("/api/v1/users/" + userId + "/active").param("value", "false"), admin))
                .andExpect(status().isOk());

        mockMvc.perform(authedGet("/api/v1/auth/me", userToken)).andExpect(status().isUnauthorized());
        mockMvc.perform(jsonPost("/api/v1/auth/login", null, Map.of("username", username, "password", "Passw0rdOK")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_DISABLED"));

        long adminId = body(mockMvc.perform(authedGet("/api/v1/auth/me", admin)).andReturn()).path("data").path("id").asLong();
        mockMvc.perform(authed(patch("/api/v1/users/" + adminId + "/active").param("value", "false"), admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Đổi mật khẩu: sai mật khẩu cũ bị từ chối; thành công thì token cũ bị thu hồi và mật khẩu mới dùng được")
    void changePasswordFlow() throws Exception {
        String admin = adminToken();
        String username = "pwd" + UUID.randomUUID().toString().substring(0, 6);
        mockMvc.perform(jsonPost("/api/v1/auth/register", admin, Map.of(
                "username", username, "password", "OldPass123", "fullName", "Đổi mật khẩu", "role", "ROLE_STAFF")));
        String token = login(username, "OldPass123");

        mockMvc.perform(authed(put("/api/v1/auth/change-password").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", "SaiRoi999", "newPassword", "NewPass456"))), token))
                .andExpect(status().isBadRequest());

        mockMvc.perform(authed(put("/api/v1/auth/change-password").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", "OldPass123", "newPassword", "NewPass456"))), token))
                .andExpect(status().isOk());

        mockMvc.perform(authedGet("/api/v1/auth/me", token)).andExpect(status().isUnauthorized());
        assertThat(login(username, "NewPass456")).isNotBlank();
    }

    @Test
    @DisplayName("STAFF tạo sản phẩm -> 403 và hành vi bị từ chối được ghi vào audit log")
    void accessDeniedIsAudited() throws Exception {
        long categoryId = idOfByCode("/api/v1/categories", "CAT-MILK", adminToken());
        mockMvc.perform(jsonPost("/api/v1/products", staffToken(), Map.of(
                        "sku", "DENIED-1", "name", "Không được tạo", "unit", "Hộp",
                        "purchasePrice", 1000, "sellingPrice", 2000,
                        "minStockLevel", 1, "categoryId", categoryId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        JsonNode logs = body(mockMvc.perform(authedGet("/api/v1/audit-logs?action=ACCESS_DENIED&username=staff", adminToken()))
                .andExpect(status().isOk()).andReturn()).path("data").path("content");
        assertThat(logs).isNotEmpty();
        assertThat(logs.get(0).path("success").asBoolean()).isFalse();
        assertThat(logs.get(0).path("details").asText()).contains("/api/v1/products");
    }

    @Test
    @DisplayName("Audit log chỉ dành cho ADMIN")
    void auditLogRestrictedToAdmin() throws Exception {
        mockMvc.perform(authedGet("/api/v1/audit-logs", managerToken())).andExpect(status().isForbidden());
        mockMvc.perform(authedGet("/api/v1/users", staffToken())).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Đăng nhập thành công / thất bại đều được ghi audit")
    void loginIsAudited() throws Exception {
        String username = "audit-" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(jsonPost("/api/v1/auth/login", null, Map.of("username", username, "password", "sai")));
        login("manager", "manager123");

        String admin = adminToken();
        JsonNode failed = body(mockMvc.perform(authedGet("/api/v1/audit-logs?action=LOGIN_FAILURE&username=" + username, admin))
                .andReturn()).path("data").path("content");
        assertThat(failed).hasSize(1);
        assertThat(failed.get(0).path("success").asBoolean()).isFalse();

        JsonNode ok = body(mockMvc.perform(authedGet("/api/v1/audit-logs?action=LOGIN_SUCCESS&username=manager", admin))
                .andReturn()).path("data").path("content");
        assertThat(ok).isNotEmpty();
    }

    // ------------------------------------------------------------------ Actuator

    @Test
    @DisplayName("Actuator: health công khai; metrics/loggers chỉ ADMIN")
    void actuatorAccessControl() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
        mockMvc.perform(authedGet("/actuator/metrics", staffToken())).andExpect(status().isForbidden());
        mockMvc.perform(authedGet("/actuator/metrics", adminToken())).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Performance monitoring: Timer của tầng service xuất hiện trong metrics sau khi gọi API")
    void serviceTimerIsExposed() throws Exception {
        mockMvc.perform(authedGet("/api/v1/warehouses", staffToken())).andExpect(status().isOk());
        mockMvc.perform(authedGet("/actuator/metrics/wms.service.execution", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurements[?(@.statistic=='COUNT')].value").isNotEmpty());
    }

    // ------------------------------------------------------------------ Exception handling

    @Test
    @DisplayName("JSON sai cú pháp / sai kiểu enum -> 400 MALFORMED_REQUEST, không lộ chi tiết nội bộ")
    void malformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{ not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"));

        mockMvc.perform(jsonPost("/api/v1/issues", managerToken(), Map.of(
                        "warehouseId", 1, "destination", "x", "issueType", "KHONG_CO", "items", java.util.List.of())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("issueType")));
    }

    @Test
    @DisplayName("Đường dẫn không tồn tại -> 404 JSON; sai method -> 405 JSON (không phải 500)")
    void notFoundAndMethodNotAllowed() throws Exception {
        String token = managerToken();
        mockMvc.perform(authedGet("/api/v1/khong-ton-tai", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(authed(delete("/api/v1/auth/me"), token))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.errorCode").value("METHOD_NOT_ALLOWED"));
        mockMvc.perform(authedGet("/api/v1/products/999999", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("Tham số sai kiểu / sort sai trường -> 400 thay vì 500")
    void badParameters() throws Exception {
        String token = managerToken();
        mockMvc.perform(authedGet("/api/v1/products/abc", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
        mockMvc.perform(authedGet("/api/v1/products?sort=password,asc", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
        mockMvc.perform(authedGet("/api/v1/products?size=100000&page=-5", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageSize").value(100));
    }

    @Test
    @DisplayName("Xóa dữ liệu đang được tham chiếu -> 409 DATA_INTEGRITY_VIOLATION, không phải 500")
    void deleteReferencedEntityGives409() throws Exception {
        String admin = adminToken();
        long warehouseId = hubWarehouseId(admin); // kho có tồn kho & phiếu tham chiếu
        mockMvc.perform(authed(delete("/api/v1/warehouses/" + warehouseId), admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DATA_INTEGRITY_VIOLATION"));
    }
}
