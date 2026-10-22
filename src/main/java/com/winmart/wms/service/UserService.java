package com.winmart.wms.service;

import com.winmart.wms.audit.AuditAction;
import com.winmart.wms.audit.AuditService;
import com.winmart.wms.common.PageResponse;
import com.winmart.wms.dto.auth.UserProfileResponse;
import com.winmart.wms.entity.User;
import com.winmart.wms.exception.BadRequestException;
import com.winmart.wms.exception.ResourceNotFoundException;
import com.winmart.wms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final AuditService auditService;

    public PageResponse<UserProfileResponse> getUsers(Pageable pageable) {
        return PageResponse.of(userRepository.findAll(pageable).map(this::mapToProfile));
    }

    /**
     * Kích hoạt / vô hiệu hóa tài khoản. Token đang dùng của tài khoản bị vô hiệu hóa
     * bị từ chối ngay ở JwtAuthenticationFilter.
     */
    @Transactional
    public UserProfileResponse setActive(Long id, boolean active, String currentUsername) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với ID: " + id));

        if (!active && user.getUsername().equals(currentUsername)) {
            throw new BadRequestException("Bạn không thể tự vô hiệu hóa tài khoản của chính mình");
        }

        user.setActive(active);
        User saved = userRepository.save(user);
        auditService.success(active ? AuditAction.ACTIVATE_USER : AuditAction.DEACTIVATE_USER,
                "User", saved.getId(), "username=" + saved.getUsername());
        return mapToProfile(saved);
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
