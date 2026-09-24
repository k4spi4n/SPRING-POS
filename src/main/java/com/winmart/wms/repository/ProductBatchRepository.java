package com.winmart.wms.repository;

import com.winmart.wms.entity.ProductBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductBatchRepository extends JpaRepository<ProductBatch, Long> {
    Optional<ProductBatch> findByProductIdAndBatchNumber(Long productId, String batchNumber);
    List<ProductBatch> findByProductId(Long productId);
    List<ProductBatch> findByExpiryDateBefore(LocalDate date);
}
