package com.winmart.wms.entity;

import com.winmart.wms.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "products")
public class Product extends BaseEntity {

    @Column(name = "sku", length = 50, unique = true, nullable = false)
    private String sku;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Column(name = "barcode", length = 50, unique = true)
    private String barcode;

    @Column(name = "unit", length = 30, nullable = false)
    private String unit; // Ví dụ: Hộp, Thùng, Chai, Gói, Kg

    @Column(name = "purchase_price", precision = 15, scale = 2)
    private BigDecimal purchasePrice;

    @Column(name = "selling_price", precision = 15, scale = 2)
    private BigDecimal sellingPrice;

    @Builder.Default
    @Column(name = "min_stock_level", nullable = false)
    private Integer minStockLevel = 10; // Ngưỡng cảnh báo tồn kho tối thiểu

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = true;
}
