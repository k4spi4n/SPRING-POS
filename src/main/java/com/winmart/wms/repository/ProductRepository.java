package com.winmart.wms.repository;

import com.winmart.wms.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findBySku(String sku);
    Optional<Product> findByBarcode(String barcode);
    boolean existsBySku(String sku);
    boolean existsByBarcode(String barcode);

    Page<Product> findByNameContainingIgnoreCaseOrSkuContainingIgnoreCase(String name, String sku, Pageable pageable);
    Page<Product> findByCategoryIdAndNameContainingIgnoreCaseOrCategoryIdAndSkuContainingIgnoreCase(
            Long nameCategoryId, String name, Long skuCategoryId, String sku, Pageable pageable);
    List<Product> findByCategoryId(Long categoryId);
}
