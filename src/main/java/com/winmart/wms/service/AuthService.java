package com.winmart.wms.service;

import com.winmart.wms.audit.AuditAction;
import com.winmart.wms.audit.AuditService;
import com.winmart.wms.audit.Audited;
import com.winmart.wms.common.MdcKeys;
import com.winmart.wms.dto.auth.AuthResponse;
import com.winmart.wms.dto.auth.ChangePasswordRequest;
import com.winmart.wms.dto.auth.LoginRequest;
import com.winmart.wms.dto.auth.RegisterRequest;
import com.winmart.wms.dto.auth.UserProfileResponse;
import com.winmart.wms.entity.Role;
import com.winmart.wms.entity.User;
import com.winmart.wms.exception.AppException;
import com.winmart.wms.exception.BadRequestException;
import com.winmart.wms.exception.ErrorCode;
import com.winmart.wms.exception.ResourceNotFoundException;
import com.winmart.wms.monitoring.WmsMetrics;
import com.winmart.wms.repository.RoleRepository;
import com.winmart.wms.repository.UserRepository;
import com.winmart.wms.security.CustomUserDetails;
import com.winmart.wms.security.JwtTokenProvider;
import com.winmart.wms.security.LoginAttemptService;
import com.winmart.wms.security.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttempts;
    private final TokenBlacklistService tokenBlacklist;
    private final AuditService auditService;
    private final WmsMetrics metrics;

    /**
     * Đăng nhập: chặn brute-force, ghi audit (thành công & thất bại), đếm metric.
     * Không đặt @Transactional ở đây để các bản ghi audit/lockout không phụ thuộc giao dịch đọc.
     */
    public AuthResponse login(LoginRequest request) {
        String username = request.getUsername();
        String clientIp = MDC.get(MdcKeys.CLIENT_IP);

        try {
            loginAttempts.assertNotLocked(clientIp, username);
        } catch (RuntimeException locked) {
            auditService.record(username, AuditAction.LOGIN_FAILURE, "User", null, false,
                    "Bị chặn do đã bị khóa tạm thời");
            metrics.count("login", "result", "blocked");
            throw locked;
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, request.getPassword()));
        } catch (BadCredentialsException | DisabledException ex) {
            boolean nowLocked = loginAttempts.recordFailure(clientIp, username);
            String reason = ex instanceof DisabledException ? "Tài khoản bị vô hiệu hóa" : "Sai tên đăng nhập hoặc mật khẩu";
            auditService.record(username, AuditAction.LOGIN_FAILURE, "User", null, false, reason);
            metrics.count("login", "result", "failure");
            if (nowLocked) {
                log.warn("Khóa tạm thời đăng nhập: user={} ip={} do sai quá số lần cho phép", username, clientIp);
                auditService.record(username, AuditAction.ACCOUNT_LOCKED, "User", null, false,
                        "Sai mật khẩu quá số lần cho phép");
            }
            throw ex;
        }

        loginAttempts.recordSuccess(clientIp, username);
        String token = tokenProvider.generateToken(authentication);

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));

        auditService.record(user.getUsername(), AuditAction.LOGIN_SUCCESS, "User", user.getId(), true, null);
        metrics.count("login", "result", "success");

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .role(user.getRole().getName().name())
                .build();
    }

    /** Thu hồi token hiện tại để không thể dùng lại dù chưa hết hạn. */
    public void logout(String token) {
        revokeToken(token);
        auditService.success(AuditAction.LOGOUT, "User", null, null);
    }

    @Audited(action = AuditAction.CREATE, entity = "User")
    @Transactional
    public UserProfileResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new AppException(ErrorCode.DUPLICATE_RESOURCE, "Tên đăng nhập đã được sử dụng");
        }
        if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail())) {
            throw new AppException(ErrorCode.DUPLICATE_RESOURCE, "Email đã được sử dụng");
        }

        Role role = roleRepository.findByName(request.getRole())
                .orElseThrow(() -> new ResourceNotFoundException("Vai trò không hợp lệ: " + request.getRole()));

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .role(role)
                .active(true)
                .build();

        User saved = userRepository.save(user);

        return mapToProfile(saved);
    }

    @Transactional
    public void changePassword(String username, ChangePasswordRequest request, String currentToken) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại: " + username));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            auditService.record(username, AuditAction.PASSWORD_CHANGED, "User", user.getId(), false,
                    "Mật khẩu hiện tại không đúng");
            throw new BadRequestException("Mật khẩu hiện tại không đúng");
        }
        if (request.getCurrentPassword().equals(request.getNewPassword())) {
            throw new BadRequestException("Mật khẩu mới phải khác mật khẩu hiện tại");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        revokeToken(currentToken); // buộc đăng nhập lại bằng mật khẩu mới
        auditService.record(username, AuditAction.PASSWORD_CHANGED, "User", user.getId(), true, null);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUserProfile(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại: " + username));
        return mapToProfile(user);
    }

    private void revokeToken(String token) {
        if (token == null) {
            return;
        }
        tokenProvider.parseToken(token)
                .ifPresent((Claims claims) -> tokenBlacklist.revoke(claims.getId(), claims.getExpiration()));
    }

    private UserProfileResponse mapToProfile(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole().getName().name())
                .active(user.getActive())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
