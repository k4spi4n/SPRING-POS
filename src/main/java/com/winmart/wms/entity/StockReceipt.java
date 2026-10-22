package com.winmart.wms.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.winmart.wms.common.BaseEntity;
import com.winmart.wms.entity.enums.ReceiptStatus;
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
@Table(name = "stock_receipts")
public class StockReceipt extends BaseEntity {

    @Column(name = "receipt_code", length = 50, unique = true, nullable = false)
    private String receiptCode;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "received_by_id", nullable = false)
    private User receivedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private ReceiptStatus status;

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
    @OneToMany(mappedBy = "stockReceipt", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<ReceiptItem> items = new ArrayList<>();

    public void addItem(ReceiptItem item) {
        items.add(item);
        item.setStockReceipt(this);
    }
}
