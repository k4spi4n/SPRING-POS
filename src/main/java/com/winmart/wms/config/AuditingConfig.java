package com.winmart.wms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Bật JPA Auditing: mọi entity kế thừa BaseEntity tự ghi createdBy / updatedBy
 * là username của người dùng đang đăng nhập ("system" cho tác vụ chạy nền / khởi tạo dữ liệu).
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class AuditingConfig {

    public static final String SYSTEM_AUDITOR = "system";

    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
                return Optional.of(SYSTEM_AUDITOR);
            }
            return Optional.of(auth.getName());
        };
    }
}
