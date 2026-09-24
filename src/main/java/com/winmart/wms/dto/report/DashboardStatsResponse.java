package com.winmart.wms.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsResponse {
    private long totalWarehouses;
    private long totalProducts;
    private long totalSuppliers;
    private long totalStockQuantity;
    private BigDecimal totalStockValue;
    private long totalReceipts;
    private long totalIssues;
    private long expiringBatchesCount;
    private long lowStockProductsCount;
}
