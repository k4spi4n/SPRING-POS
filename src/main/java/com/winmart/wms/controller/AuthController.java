package com.winmart.wms.controller;

import com.winmart.wms.common.ApiResponse;
import com.winmart.wms.dto.auth.AuthResponse;
import com.winmart.wms.dto.auth.ChangePasswordRequest;
import com.winmart.wms.dto.auth.LoginRequest;
import com.winmart.wms.dto.auth.RegisterRequest;
import com.winmart.wms.dto.auth.UserProfileResponse;
import com.winmart.wms.service.AuthService;
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

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "API Xác thực người dùng và Quản lý phiên đăng nhập")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Đăng nhập hệ thống",
            description = "Xác thực tài khoản và trả về JWT Bearer Token. Sai mật khẩu nhiều lần liên tiếp sẽ bị khóa tạm thời (HTTP 423).")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Đăng nhập thành công", response));
    }

    @PostMapping("/logout")
    @Operation(summary = "Đăng xuất", description = "Thu hồi JWT hiện tại, token không thể dùng lại dù chưa hết hạn")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.logout(extractToken(authorization));
        return ResponseEntity.ok(ApiResponse.success("Đăng xuất thành công", null));
    }

    @PostMapping("/register")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tạo tài khoản mới (Yêu cầu ADMIN)",
            description = "Chỉ quản trị viên được tạo tài khoản nhân viên/thủ kho/quản trị. Không cho phép tự đăng ký công khai.")
    public ResponseEntity<ApiResponse<UserProfileResponse>> register(@Valid @RequestBody RegisterRequest request) {
        UserProfileResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo tài khoản thành công", response));
    }

    @PutMapping("/change-password")
    @Operation(summary = "Đổi mật khẩu của chính mình", description = "Sau khi đổi, token hiện tại bị thu hồi và cần đăng nhập lại")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.changePassword(userDetails.getUsername(), request, extractToken(authorization));
        return ResponseEntity.ok(ApiResponse.success("Đổi mật khẩu thành công, vui lòng đăng nhập lại", null));
    }

    @GetMapping("/me")
    @Operation(summary = "Lấy thông tin tài khoản hiện tại", description = "Xem profile người dùng đang đăng nhập dựa trên JWT")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        UserProfileResponse response = authService.getCurrentUserProfile(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private static String extractToken(String authorizationHeader) {
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring(7);
        }
        return null;
    }
}
