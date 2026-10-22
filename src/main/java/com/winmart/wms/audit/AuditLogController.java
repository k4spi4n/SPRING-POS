package com.winmart.wms.audit;

import com.winmart.wms.common.ApiResponse;
import com.winmart.wms.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Audit Log", description = "Tra cứu nhật ký kiểm toán: ai đã làm gì, lúc nào, kết quả ra sao (chỉ ADMIN)")
public class AuditLogController {

    private static final int MAX_PAGE_SIZE = 200;

    private final AuditService auditService;

    @GetMapping
    @Operation(summary = "Tìm kiếm nhật ký kiểm toán có phân trang và lọc theo người dùng/hành động/đối tượng/thời gian")
    public ResponseEntity<ApiResponse<PageResponse<AuditLog>>> search(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        return ResponseEntity.ok(ApiResponse.success(
                auditService.search(username, action, entityType, success, from, to, pageable)));
    }
}
