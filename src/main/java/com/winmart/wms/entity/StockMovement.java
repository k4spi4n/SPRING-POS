package com.winmart.wms.entity;

import com.winmart.wms.common.BaseEntity;
import com.winmart.wms.entity.enums.MovementType;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "stock_movements")
public class StockMovement extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "batch_id", nullable = false)
    private ProductBatch batch;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", length = 30, nullable = false)
    private MovementType movementType;

    @Column(name = "quantity", nullable = false)
    private Integer quantity; // Số lượng thay đổi (dương hoặc âm)

    @Column(name = "balance_after", nullable = false)
    private Integer balanceAfter; // Số tồn sau khi phát sinh biến động

    @Column(name = "reference_code", length = 50)
    private String referenceCode; // Mã phiếu nhập hoặc xuất liên quan

    @Column(name = "note", length = 255)
    private String note;
}
