package com.winmart.wms.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.winmart.wms.audit.AuditAction;
import com.winmart.wms.audit.AuditService;
import com.winmart.wms.common.ApiResponse;
import com.winmart.wms.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 403 dạng JSON + ghi audit khi người đã đăng nhập bị chặn ở tầng URL (vd: /actuator/** chỉ ADMIN).
 * (Các trường hợp @PreAuthorize ở controller đi qua GlobalExceptionHandler.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.warn("[FORBIDDEN] user={} bị từ chối {} {}", AuditService.currentUsername(),
                request.getMethod(), request.getRequestURI());
        auditService.failure(AuditAction.ACCESS_DENIED, "Endpoint", null,
                request.getMethod() + " " + request.getRequestURI());

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(),
                ApiResponse.error(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.getDefaultMessage(), request.getRequestURI()));
    }
}
