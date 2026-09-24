package com.winmart.wms.dto.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockMovementResponse {
    private Long id;
    private String warehouseName;
    private String productSku;
    private String productName;
    private String batchNumber;
    private String movementType;
    private Integer quantity;
    private Integer balanceAfter;
    private String referenceCode;
    private String note;
    private LocalDateTime createdAt;
}
