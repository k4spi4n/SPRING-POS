package com.winmart.wms.dto.receipt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptResponse {
    private Long id;
    private String receiptCode;
    private Long warehouseId;
    private String warehouseName;
    private Long supplierId;
    private String supplierName;
    private String receivedBy;
    private String status;
    private BigDecimal totalAmount;
    private String note;
    private LocalDateTime createdAt;
    private String createdBy;
    private String cancelReason;
    private String cancelledBy;
    private LocalDateTime cancelledAt;
    private List<ReceiptItemResponse> items;
}
