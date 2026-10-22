package com.winmart.wms.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.winmart.wms.common.BaseEntity;
import com.winmart.wms.entity.enums.IssueStatus;
import com.winmart.wms.entity.enums.IssueType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "stock_issues")
public class StockIssue extends BaseEntity {

    @Column(name = "issue_code", length = 50, unique = true, nullable = false)
    private String issueCode;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "issued_by_id", nullable = false)
    private User issuedBy;

    @Column(name = "destination", length = 200, nullable = false)
    private String destination; // Nơi nhận (Ví dụ: Cửa hàng WinMart+ Cầu Giấy)

    @Enumerated(EnumType.STRING)
    @Column(name = "issue_type", length = 30, nullable = false)
    private IssueType issueType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private IssueStatus status;

    @Column(name = "total_amount", precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "cancelled_by", length = 50)
    private String cancelledBy;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Builder.Default
    @OneToMany(mappedBy = "stockIssue", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<IssueItem> items = new ArrayList<>();

    public void addItem(IssueItem item) {
        items.add(item);
        item.setStockIssue(this);
    }
}
