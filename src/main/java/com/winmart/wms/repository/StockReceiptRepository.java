package com.winmart.wms.repository;

import com.winmart.wms.entity.StockReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockReceiptRepository extends JpaRepository<StockReceipt, Long> {
    Optional<StockReceipt> findByReceiptCode(String receiptCode);
    List<StockReceipt> findByWarehouseIdOrderByCreatedAtDesc(Long warehouseId);
}
