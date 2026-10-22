package com.winmart.wms.dto.issue;

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
public class IssueResponse {
    private Long id;
    private String issueCode;
    private Long warehouseId;
    private String warehouseName;
    private String issuedBy;
    private String destination;
    private String issueType;
    private String status;
    private BigDecimal totalAmount;
    private String note;
    private LocalDateTime createdAt;
    private String createdBy;
    private String cancelReason;
    private String cancelledBy;
    private LocalDateTime cancelledAt;
    private List<IssueItemResponse> items;
}
