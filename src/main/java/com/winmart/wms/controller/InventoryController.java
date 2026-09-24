package com.winmart.wms.controller;

import com.winmart.wms.common.ApiResponse;
import com.winmart.wms.dto.inventory.ExpiringBatchResponse;
import com.winmart.wms.dto.inventory.InventoryResponse;
import com.winmart.wms.dto.inventory.LowStockResponse;
import com.winmart.wms.dto.inventory.StockMovementResponse;
import com.winmart.wms.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory & Stock Tracking", description = "API Quản lý Tồn kho, Theo dõi Lô/Hạn dùng & Lịch sử biến động")
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping
    @Operation(summary = "Xem toàn bộ tồn kho hệ thống theo từng lô hàng")
    public ResponseEntity<ApiResponse<List<InventoryResponse>>> getAllInventory() {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.getAllInventory()));
    }

    @GetMapping("/warehouse/{warehouseId}")
    @Operation(summary = "Xem tồn kho chi tiết theo từng kho hàng")
    public ResponseEntity<ApiResponse<List<InventoryResponse>>> getInventoryByWarehouse(@PathVariable Long warehouseId) {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.getInventoryByWarehouse(warehouseId)));
    }

    @GetMapping("/warehouse/{warehouseId}/expiring")
    @Operation(summary = "Cảnh báo các lô hàng sắp hết hạn (Cận date) hoặc đã hết hạn",
               description = "Tìm các lô hàng có hạn sử dụng còn lại dưới N ngày (mặc định 30 ngày)")
    public ResponseEntity<ApiResponse<List<ExpiringBatchResponse>>> getExpiringInventory(
            @PathVariable Long warehouseId,
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.getExpiringInventory(warehouseId, days)));
    }

    @GetMapping("/warehouse/{warehouseId}/low-stock")
    @Operation(summary = "Cảnh báo các sản phẩm có số lượng tồn dưới ngưỡng tối thiểu (Safety Stock)")
    public ResponseEntity<ApiResponse<List<LowStockResponse>>> getLowStockProducts(@PathVariable Long warehouseId) {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.getLowStockProducts(warehouseId)));
    }

    @GetMapping("/movements")
    @Operation(summary = "Truy vấn lịch sử biến động kho (Audit trail log)")
    public ResponseEntity<ApiResponse<List<StockMovementResponse>>> getMovements(
            @RequestParam(required = false) Long warehouseId) {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.getStockMovements(warehouseId)));
    }
}
