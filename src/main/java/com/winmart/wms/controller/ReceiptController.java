package com.winmart.wms.controller;

import com.winmart.wms.common.ApiResponse;
import com.winmart.wms.dto.receipt.ReceiptCreateRequest;
import com.winmart.wms.dto.receipt.ReceiptResponse;
import com.winmart.wms.service.ReceiptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/receipts")
@RequiredArgsConstructor
@Tag(name = "Inbound (Nhập kho)", description = "API Tiếp nhận đơn hàng NCC, lập phiếu nhập kho, tăng tồn kho theo lô")
public class ReceiptController {

    private final ReceiptService receiptService;

    @GetMapping
    @Operation(summary = "Lấy danh sách tất cả phiếu nhập kho")
    public ResponseEntity<ApiResponse<List<ReceiptResponse>>> getAllReceipts() {
        return ResponseEntity.ok(ApiResponse.success(receiptService.getAllReceipts()));
    }

    @GetMapping("/warehouse/{warehouseId}")
    @Operation(summary = "Lấy danh sách phiếu nhập kho theo từng kho hàng")
    public ResponseEntity<ApiResponse<List<ReceiptResponse>>> getReceiptsByWarehouse(@PathVariable Long warehouseId) {
        return ResponseEntity.ok(ApiResponse.success(receiptService.getReceiptsByWarehouse(warehouseId)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết phiếu nhập kho và danh sách lô hàng đã nhập")
    public ResponseEntity<ApiResponse<ReceiptResponse>> getReceiptById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(receiptService.getReceiptById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Operation(summary = "Tạo phiếu nhập kho (Tự động cập nhật tăng tồn kho & ghi lịch sử biến động)")
    public ResponseEntity<ApiResponse<ReceiptResponse>> createReceipt(
            @Valid @RequestBody ReceiptCreateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        ReceiptResponse response = receiptService.createReceipt(request, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo phiếu nhập kho thành công", response));
    }
}
