package com.winmart.wms.controller;

import com.winmart.wms.common.ApiResponse;
import com.winmart.wms.dto.issue.IssueCreateRequest;
import com.winmart.wms.dto.issue.IssueResponse;
import com.winmart.wms.service.IssueService;
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
@RequestMapping("/api/v1/issues")
@RequiredArgsConstructor
@Tag(name = "Outbound (Xuất kho FEFO)", description = "API Xuất kho phân phối/điều chuyển tới WinMart+ áp dụng thuật toán FEFO (Hạn gần xuất trước)")
public class IssueController {

    private final IssueService issueService;

    @GetMapping
    @Operation(summary = "Lấy danh sách tất cả phiếu xuất kho")
    public ResponseEntity<ApiResponse<List<IssueResponse>>> getAllIssues() {
        return ResponseEntity.ok(ApiResponse.success(issueService.getAllIssues()));
    }

    @GetMapping("/warehouse/{warehouseId}")
    @Operation(summary = "Lấy danh sách phiếu xuất kho theo từng kho hàng")
    public ResponseEntity<ApiResponse<List<IssueResponse>>> getIssuesByWarehouse(@PathVariable Long warehouseId) {
        return ResponseEntity.ok(ApiResponse.success(issueService.getIssuesByWarehouse(warehouseId)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết phiếu xuất kho và các lô hàng đã phân bổ xuất")
    public ResponseEntity<ApiResponse<IssueResponse>> getIssueById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(issueService.getIssueById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Operation(summary = "Tạo phiếu xuất kho FEFO (Tự động trừ tồn kho theo lô date gần nhất & ghi log)")
    public ResponseEntity<ApiResponse<IssueResponse>> createIssue(
            @Valid @RequestBody IssueCreateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        IssueResponse response = issueService.createIssue(request, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo phiếu xuất kho thành công", response));
    }
}
