package com.winmart.wms.service;

import com.winmart.wms.dto.report.DashboardStatsResponse;
import com.winmart.wms.entity.Inventory;
import com.winmart.wms.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;
    private final InventoryRepository inventoryRepository;
    private final StockReceiptRepository receiptRepository;
    private final StockIssueRepository issueRepository;

    public DashboardStatsResponse getDashboardStats() {
        long totalWarehouses = warehouseRepository.count();
        long totalProducts = productRepository.count();
        long totalSuppliers = supplierRepository.count();
        long totalReceipts = receiptRepository.count();
        long totalIssues = issueRepository.count();

        List<Inventory> allInventory = inventoryRepository.findAll();
        long totalStockQuantity = 0;
        BigDecimal totalStockValue = BigDecimal.ZERO;
        long expiringCount = 0;
        LocalDate alertDate = LocalDate.now().plusDays(30);

        for (Inventory inv : allInventory) {
            totalStockQuantity += inv.getQuantity();

            BigDecimal price = inv.getBatch().getProduct().getPurchasePrice() != null
                    ? inv.getBatch().getProduct().getPurchasePrice() : BigDecimal.ZERO;
            totalStockValue = totalStockValue.add(price.multiply(BigDecimal.valueOf(inv.getQuantity())));

            if (inv.getQuantity() > 0 && inv.getBatch().getExpiryDate().isBefore(alertDate.plusDays(1))) {
                expiringCount++;
            }
        }

        return DashboardStatsResponse.builder()
                .totalWarehouses(totalWarehouses)
                .totalProducts(totalProducts)
                .totalSuppliers(totalSuppliers)
                .totalStockQuantity(totalStockQuantity)
                .totalStockValue(totalStockValue)
                .totalReceipts(totalReceipts)
                .totalIssues(totalIssues)
                .expiringBatchesCount(expiringCount)
                .lowStockProductsCount(0) // Đã có API riêng chi tiết theo từng kho
                .build();
    }
}
