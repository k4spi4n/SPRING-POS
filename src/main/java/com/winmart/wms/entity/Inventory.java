package com.winmart.wms.entity;

import com.winmart.wms.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "inventories", uniqueConstraints = {
        @UniqueConstraint(name = "uk_warehouse_batch", columnNames = {"warehouse_id", "batch_id"})
})
public class Inventory extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "batch_id", nullable = false)
    private ProductBatch batch;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;
}
