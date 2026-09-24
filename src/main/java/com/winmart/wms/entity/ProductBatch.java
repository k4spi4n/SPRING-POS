package com.winmart.wms.entity;

import com.winmart.wms.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "product_batches", uniqueConstraints = {
        @UniqueConstraint(name = "uk_product_batch", columnNames = {"product_id", "batch_number"})
})
public class ProductBatch extends BaseEntity {

    @Column(name = "batch_number", length = 50, nullable = false)
    private String batchNumber;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "manufacturing_date")
    private LocalDate manufacturingDate;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;
}
