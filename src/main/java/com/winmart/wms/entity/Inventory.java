package com.winmart.wms.entity;

import com.winmart.wms.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Check;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Check(constraints = "quantity >= 0") // Chốt chặn cuối cùng ở tầng CSDL: tồn kho không bao giờ âm
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
