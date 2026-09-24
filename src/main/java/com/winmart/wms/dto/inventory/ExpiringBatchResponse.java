package com.winmart.wms.dto.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpiringBatchResponse {
    private Long productId;
    private String productSku;
    private String productName;
    private String unit;
    private String batchNumber;
    private LocalDate expiryDate;
    private long daysRemaining;
    private Integer quantity;
    private String warehouseName;
    private String warningStatus; // "HẾT HẠN", "CẬN DATE NGUY CƠ CAO (<15 ngày)", "CẬN DATE (<30 ngày)"
}
