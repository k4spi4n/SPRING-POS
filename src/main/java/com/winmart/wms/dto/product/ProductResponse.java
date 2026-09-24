package com.winmart.wms.dto.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {
    private Long id;
    private String sku;
    private String name;
    private String barcode;
    private String unit;
    private BigDecimal purchasePrice;
    private BigDecimal sellingPrice;
    private Integer minStockLevel;
    private Long categoryId;
    private String categoryName;
    private Long supplierId;
    private String supplierName;
    private Boolean active;
    private LocalDateTime createdAt;
}
