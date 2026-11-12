package com.winmart.wms;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PosFrontendTest extends AbstractApiTest {

    @Test
    void categoryFilterCombinesWithSearchBeforePaginationAndDoesNotLeakSkuMatches() throws Exception {
        String manager = managerToken();
        long product = createProduct(manager, 1);
        var data = body(mockMvc.perform(authedGet("/api/v1/products/" + product, manager)).andReturn()).path("data");
        String query = "/api/v1/products?search=" + data.path("sku").asText() + "&size=1&categoryId=";
        String staff = staffToken();
        mockMvc.perform(authedGet(query + data.path("categoryId").asLong(), staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(product));
        long otherCategory = idOfByCode("/api/v1/categories", "CAT-NOODLE", staff);
        mockMvc.perform(authedGet(query + otherCategory, staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(authedGet("/api/v1/products?categoryId=" + otherCategory + "&size=1&sort=sellingPrice,asc", staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].categoryId").value(otherCategory));
    }

    @Test
    void frontendIsPublicButDataAndOrderCreationStillRequireJwt() throws Exception {
        // MockMvc checks the welcome-page forward; the running server resolves index.html.
        mockMvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("index.html"));
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
        for (String path : List.of("/pos.js", "/pos.css", "/favicon.svg")) {
            mockMvc.perform(get(path)).andExpect(status().isOk());
        }
        for (String path : List.of("/api/v1/products", "/api/v1/warehouses/active", "/api/v1/issues", "/private.js")) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        mockMvc.perform(jsonPost("/api/v1/issues", null, Map.of())).andExpect(status().isUnauthorized());
    }

    @Test
    void posContractUsesRealProductsAndServerSellingPriceWithoutClientAmount() throws Exception {
        String manager = managerToken();
        long warehouse = hubWarehouseId(manager);
        long product = createProduct(manager, 1);
        var productData = body(mockMvc.perform(authedGet("/api/v1/products/" + product, manager))
                .andExpect(status().isOk()).andReturn()).path("data");
        receive(manager, warehouse, List.of(receiptItem(product, "POS", LocalDate.now().plusDays(90), 10)));
        String staff = staffToken();

        mockMvc.perform(authedGet("/api/v1/products?search=" + productData.path("sku").asText() + "&size=12&sort=name,asc", staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(product))
                .andExpect(jsonPath("$.data.content[0].sellingPrice").value(15000));
        mockMvc.perform(authedGet("/api/v1/warehouses/active", staff)).andExpect(status().isOk());

        var result = issue(staff, warehouse, "STORE_TRANSFER", product, 3);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        var order = body(result).path("data");
        assertThat(order.path("totalAmount").decimalValue()).isEqualByComparingTo("45000");
        assertThat(order.path("items").get(0).path("unitPrice").decimalValue()).isEqualByComparingTo("15000");
        assertThat(order.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(stockOf(staff, warehouse, product)).isEqualTo(7);

        var failed = issue(staff, warehouse, "STORE_TRANSFER", product, 8);
        assertThat(failed.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(failed).path("errorCode").asText()).isEqualTo("INSUFFICIENT_STOCK");
        assertThat(stockOf(staff, warehouse, product)).isEqualTo(7);

        mockMvc.perform(jsonPost("/api/v1/issues", staff, Map.of("warehouseId", warehouse,
                        "destination", " ", "issueType", "STORE_TRANSFER",
                        "items", List.of(Map.of("productId", product, "quantity", 0)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.destination").exists())
                .andExpect(jsonPath("$.data['items[0].quantity']").exists());
    }
}
