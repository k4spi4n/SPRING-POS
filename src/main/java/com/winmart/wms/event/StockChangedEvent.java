package com.winmart.wms.event;

/**
 * Phát ra bên trong transaction khi tồn kho của 1 sản phẩm tại 1 kho bị giảm.
 * Chỉ được xử lý SAU KHI transaction commit thành công (xem {@link LowStockAlertListener}),
 * nên giao dịch bị rollback sẽ không bao giờ sinh cảnh báo sai.
 */
public record StockChangedEvent(Long warehouseId, Long productId) {
}
