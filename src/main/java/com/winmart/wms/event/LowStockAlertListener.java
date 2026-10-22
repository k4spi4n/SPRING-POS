package com.winmart.wms.event;

import com.winmart.wms.entity.Product;
import com.winmart.wms.monitoring.WmsMetrics;
import com.winmart.wms.repository.InventoryRepository;
import com.winmart.wms.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sau khi giao dịch xuất kho/hủy nhập kho commit, kiểm tra tồn còn lại so với định mức an toàn
 * và phát cảnh báo (log WARN + metric). Chạy trong transaction mới (chỉ đọc) vì phase AFTER_COMMIT
 * không còn transaction của nghiệp vụ. Lỗi ở đây không ảnh hưởng nghiệp vụ đã commit.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LowStockAlertListener {

    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final WmsMetrics metrics;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onStockChanged(StockChangedEvent event) {
        Product product = productRepository.findById(event.productId()).orElse(null);
        if (product == null) {
            return;
        }
        int remaining = inventoryRepository.getTotalStockByWarehouseAndProduct(event.warehouseId(), event.productId());
        if (remaining <= product.getMinStockLevel()) {
            log.warn("CẢNH BÁO TỒN KHO THẤP: sản phẩm [{} - {}] tại kho #{} còn {} (định mức tối thiểu {})",
                    product.getSku(), product.getName(), event.warehouseId(), remaining, product.getMinStockLevel());
            metrics.count("low_stock_alert");
        }
    }
}
