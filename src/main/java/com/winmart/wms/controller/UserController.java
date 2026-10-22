package com.winmart.wms.controller;

import com.winmart.wms.common.ApiResponse;
import com.winmart.wms.common.PageResponse;
import com.winmart.wms.dto.auth.UserProfileResponse;
import com.winmart.wms.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "User Management", description = "Quản trị người dùng hệ thống (chỉ ADMIN)")
public class UserController {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserService userService;

    @GetMapping
    @Operation(summary = "Danh sách người dùng có phân trang")
    public ResponseEntity<ApiResponse<PageResponse<UserProfileResponse>>> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by("id"));
        return ResponseEntity.ok(ApiResponse.success(userService.getUsers(pageable)));
    }

    @PatchMapping("/{id}/active")
    @Operation(summary = "Kích hoạt / vô hiệu hóa tài khoản",
            description = "Tài khoản bị vô hiệu hóa không thể đăng nhập và token đang dùng bị từ chối ngay lập tức")
    public ResponseEntity<ApiResponse<UserProfileResponse>> setActive(
            @PathVariable Long id,
            @RequestParam boolean value,
            @AuthenticationPrincipal UserDetails currentUser) {
        UserProfileResponse response = userService.setActive(id, value, currentUser.getUsername());
        return ResponseEntity.ok(ApiResponse.success(
                value ? "Đã kích hoạt tài khoản" : "Đã vô hiệu hóa tài khoản", response));
    }
}
