package com.winmart.wms.dto.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LowStockResponse {
    private Long productId;
    private String productSku;
    private String productName;
    private Long currentStock;
    private Integer minStockLevel;
    private Long warehouseId;
}
