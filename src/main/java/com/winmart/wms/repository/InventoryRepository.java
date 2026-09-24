package com.winmart.wms.repository;

import com.winmart.wms.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByWarehouseIdAndBatchId(Long warehouseId, Long batchId);

    List<Inventory> findByWarehouseId(Long warehouseId);

    /**
     * Tìm các lô tồn kho của 1 sản phẩm tại 1 kho còn hàng, sắp xếp theo Hạn sử dụng tăng dần (FEFO)
     */
    List<Inventory> findByWarehouseIdAndBatch_ProductIdAndQuantityGreaterThanOrderByBatch_ExpiryDateAsc(
            Long warehouseId, Long productId, Integer minQuantity);

    /**
     * Tổng số lượng tồn của 1 sản phẩm tại 1 kho
     */
    @Query("SELECT COALESCE(SUM(i.quantity), 0) FROM Inventory i " +
           "WHERE i.warehouse.id = :warehouseId AND i.batch.product.id = :productId")
    Integer getTotalStockByWarehouseAndProduct(@Param("warehouseId") Long warehouseId, @Param("productId") Long productId);

    /**
     * Tìm danh sách lô hàng sắp hết hạn (cận date) trước một mốc thời gian cụ thể
     */
    @Query("SELECT i FROM Inventory i " +
           "WHERE i.warehouse.id = :warehouseId AND i.quantity > 0 AND i.batch.expiryDate <= :targetDate " +
           "ORDER BY i.batch.expiryDate ASC")
    List<Inventory> findExpiringInventory(@Param("warehouseId") Long warehouseId, @Param("targetDate") LocalDate targetDate);

    /**
     * Thống kê các sản phẩm có tổng tồn kho dưới ngưỡng an toàn (min_stock_level)
     */
    @Query("SELECT i.batch.product.id, i.batch.product.sku, i.batch.product.name, " +
           "SUM(i.quantity), i.batch.product.minStockLevel " +
           "FROM Inventory i " +
           "WHERE i.warehouse.id = :warehouseId " +
           "GROUP BY i.batch.product.id, i.batch.product.sku, i.batch.product.name, i.batch.product.minStockLevel " +
           "HAVING SUM(i.quantity) <= i.batch.product.minStockLevel")
    List<Object[]> findLowStockProducts(@Param("warehouseId") Long warehouseId);
}
