package com.winmart.wms.dto.receipt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptItemResponse {
    private Long id;
    private Long productId;
    private String productSku;
    private String productName;
    private String unit;
    private String batchNumber;
    private LocalDate manufacturingDate;
    private LocalDate expiryDate;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal subTotal;
}
