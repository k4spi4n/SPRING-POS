package com.winmart.wms.repository;

import com.winmart.wms.entity.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    /** Nạp sẵn kho + lô + sản phẩm bằng 1 câu SELECT, tránh N+1 khi liệt kê tồn kho. */
    @Override
    @EntityGraph(attributePaths = {"warehouse", "batch", "batch.product"})
    List<Inventory> findAll();

    @EntityGraph(attributePaths = {"warehouse", "batch", "batch.product"})
    List<Inventory> findByWarehouseId(Long warehouseId);

    Optional<Inventory> findByWarehouseIdAndBatchId(Long warehouseId, Long batchId);

    /**
     * Khóa bi quan (SELECT ... FOR UPDATE) dòng tồn kho của 1 lô tại 1 kho.
     * Dùng khi nhập kho / hoàn tác để 2 giao dịch đồng thời không ghi đè số lượng của nhau.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.warehouse.id = :warehouseId AND i.batch.id = :batchId")
    Optional<Inventory> findByWarehouseAndBatchForUpdate(@Param("warehouseId") Long warehouseId,
                                                         @Param("batchId") Long batchId);

    /**
     * Khóa bi quan toàn bộ các lô còn hàng của 1 sản phẩm tại 1 kho, sắp xếp FEFO
     * (hạn sử dụng gần nhất trước). Đây là điểm chặn chính ngăn xuất lố tồn khi nhiều
     * phiếu xuất chạy đồng thời: giao dịch đến sau phải chờ giao dịch trước commit rồi
     * mới đọc số tồn đã được trừ.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i " +
           "WHERE i.warehouse.id = :warehouseId AND i.batch.product.id = :productId AND i.quantity > 0 " +
           "ORDER BY i.batch.expiryDate ASC, i.id ASC")
    List<Inventory> findAvailableStockForUpdate(@Param("warehouseId") Long warehouseId,
                                                @Param("productId") Long productId);

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
           "JOIN FETCH i.warehouse JOIN FETCH i.batch b JOIN FETCH b.product " +
           "WHERE i.warehouse.id = :warehouseId AND i.quantity > 0 AND b.expiryDate <= :targetDate " +
           "ORDER BY b.expiryDate ASC")
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

    // ---- Các truy vấn tổng hợp cho Dashboard (tính ở CSDL thay vì nạp toàn bộ tồn kho lên bộ nhớ) ----

    @Query("SELECT COALESCE(SUM(i.quantity), 0) FROM Inventory i")
    long sumTotalQuantity();

    @Query("SELECT COALESCE(SUM(i.quantity * COALESCE(i.batch.product.purchasePrice, 0)), 0) FROM Inventory i")
    BigDecimal sumTotalStockValue();

    @Query("SELECT COUNT(i) FROM Inventory i WHERE i.quantity > 0 AND i.batch.expiryDate <= :targetDate")
    long countExpiringBatches(@Param("targetDate") LocalDate targetDate);

    /** Mỗi phần tử là 1 cặp (kho, sản phẩm) có tồn dưới ngưỡng an toàn; kích thước danh sách = số cảnh báo. */
    @Query("SELECT i.batch.product.id FROM Inventory i " +
           "GROUP BY i.warehouse.id, i.batch.product.id, i.batch.product.minStockLevel " +
           "HAVING SUM(i.quantity) <= i.batch.product.minStockLevel")
    List<Long> findLowStockProductIdsAcrossWarehouses();
}
