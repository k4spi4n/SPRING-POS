package com.winmart.wms.service;

import com.winmart.wms.dto.report.DashboardStatsResponse;
import com.winmart.wms.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;
    private final InventoryRepository inventoryRepository;
    private final StockReceiptRepository receiptRepository;
    private final StockIssueRepository issueRepository;

    @Value("${app.inventory.expiry-warning-days:30}")
    private int expiryWarningDays;

    /**
     * Tất cả chỉ số được tính bằng truy vấn tổng hợp (SUM/COUNT) tại CSDL,
     * không nạp toàn bộ bảng tồn kho lên bộ nhớ ứng dụng.
     */
    public DashboardStatsResponse getDashboardStats() {
        LocalDate alertDate = LocalDate.now().plusDays(expiryWarningDays);
        BigDecimal stockValue = inventoryRepository.sumTotalStockValue();

        return DashboardStatsResponse.builder()
                .totalWarehouses(warehouseRepository.count())
                .totalProducts(productRepository.count())
                .totalSuppliers(supplierRepository.count())
                .totalStockQuantity(inventoryRepository.sumTotalQuantity())
                .totalStockValue(stockValue != null ? stockValue : BigDecimal.ZERO)
                .totalReceipts(receiptRepository.count())
                .totalIssues(issueRepository.count())
                .expiringBatchesCount(inventoryRepository.countExpiringBatches(alertDate))
                .lowStockProductsCount(inventoryRepository.findLowStockProductIdsAcrossWarehouses().size())
                .build();
    }
}
