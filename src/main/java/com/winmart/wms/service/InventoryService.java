package com.winmart.wms.service;

import com.winmart.wms.dto.inventory.ExpiringBatchResponse;
import com.winmart.wms.dto.inventory.InventoryResponse;
import com.winmart.wms.dto.inventory.LowStockResponse;
import com.winmart.wms.dto.inventory.StockMovementResponse;
import com.winmart.wms.entity.Inventory;
import com.winmart.wms.entity.StockMovement;
import com.winmart.wms.repository.InventoryRepository;
import com.winmart.wms.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository movementRepository;

    public List<InventoryResponse> getAllInventory() {
        return inventoryRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    public List<InventoryResponse> getInventoryByWarehouse(Long warehouseId) {
        return inventoryRepository.findByWarehouseId(warehouseId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Lấy danh sách hàng cận date hoặc đã hết hạn tại kho (đặc thù WinMart)
     */
    public List<ExpiringBatchResponse> getExpiringInventory(Long warehouseId, int days) {
        final int thresholdDays = (days <= 0) ? 30 : days;
        LocalDate targetDate = LocalDate.now().plusDays(thresholdDays);
        List<Inventory> expiringList = inventoryRepository.findExpiringInventory(warehouseId, targetDate);

        LocalDate today = LocalDate.now();

        return expiringList.stream().map(inv -> {
            long daysRemaining = ChronoUnit.DAYS.between(today, inv.getBatch().getExpiryDate());
            String status;
            if (daysRemaining < 0) {
                status = "ĐÃ HẾT HẠN";
            } else if (daysRemaining <= 15) {
                status = "CẬN DATE NGUY CƠ CAO (≤ 15 ngày)";
            } else {
                status = "CẬN DATE (≤ " + thresholdDays + " ngày)";
            }

            return ExpiringBatchResponse.builder()
                    .productId(inv.getBatch().getProduct().getId())
                    .productSku(inv.getBatch().getProduct().getSku())
                    .productName(inv.getBatch().getProduct().getName())
                    .unit(inv.getBatch().getProduct().getUnit())
                    .batchNumber(inv.getBatch().getBatchNumber())
                    .expiryDate(inv.getBatch().getExpiryDate())
                    .daysRemaining(daysRemaining)
                    .quantity(inv.getQuantity())
                    .warehouseName(inv.getWarehouse().getName())
                    .warningStatus(status)
                    .build();
        }).toList();
    }

    /**
     * Cảnh báo sản phẩm tồn kho dưới ngưỡng an toàn (min_stock_level)
     */
    public List<LowStockResponse> getLowStockProducts(Long warehouseId) {
        List<Object[]> rawList = inventoryRepository.findLowStockProducts(warehouseId);
        return rawList.stream().map(row -> LowStockResponse.builder()
                .productId((Long) row[0])
                .productSku((String) row[1])
                .productName((String) row[2])
                .currentStock((Long) row[3])
                .minStockLevel((Integer) row[4])
                .warehouseId(warehouseId)
                .build()
        ).toList();
    }

    /**
     * Tra cứu lịch sử biến động kho
     */
    public List<StockMovementResponse> getStockMovements(Long warehouseId) {
        List<StockMovement> movements = warehouseId != null
                ? movementRepository.findByWarehouseIdOrderByCreatedAtDesc(warehouseId)
                : movementRepository.findTop50ByOrderByCreatedAtDesc();

        return movements.stream().map(m -> StockMovementResponse.builder()
                .id(m.getId())
                .warehouseName(m.getWarehouse().getName())
                .productSku(m.getProduct().getSku())
                .productName(m.getProduct().getName())
                .batchNumber(m.getBatch().getBatchNumber())
                .movementType(m.getMovementType().name())
                .quantity(m.getQuantity())
                .balanceAfter(m.getBalanceAfter())
                .referenceCode(m.getReferenceCode())
                .note(m.getNote())
                .createdAt(m.getCreatedAt())
                .build()
        ).toList();
    }

    public InventoryResponse mapToResponse(Inventory inv) {
        BigDecimal purchasePrice = inv.getBatch().getProduct().getPurchasePrice() != null
                ? inv.getBatch().getProduct().getPurchasePrice() : BigDecimal.ZERO;
        BigDecimal totalStockValue = purchasePrice.multiply(BigDecimal.valueOf(inv.getQuantity()));

        return InventoryResponse.builder()
                .id(inv.getId())
                .warehouseId(inv.getWarehouse().getId())
                .warehouseName(inv.getWarehouse().getName())
                .productId(inv.getBatch().getProduct().getId())
                .productSku(inv.getBatch().getProduct().getSku())
                .productName(inv.getBatch().getProduct().getName())
                .unit(inv.getBatch().getProduct().getUnit())
                .batchId(inv.getBatch().getId())
                .batchNumber(inv.getBatch().getBatchNumber())
                .manufacturingDate(inv.getBatch().getManufacturingDate())
                .expiryDate(inv.getBatch().getExpiryDate())
                .quantity(inv.getQuantity())
                .purchasePrice(inv.getBatch().getProduct().getPurchasePrice())
                .sellingPrice(inv.getBatch().getProduct().getSellingPrice())
                .totalStockValue(totalStockValue)
                .build();
    }
}
