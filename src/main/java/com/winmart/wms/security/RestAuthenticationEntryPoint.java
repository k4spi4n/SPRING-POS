package com.winmart.wms.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.winmart.wms.common.ApiResponse;
import com.winmart.wms.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 401 dạng JSON thống nhất khi truy cập tài nguyên cần đăng nhập mà thiếu/sai/hết hạn token
 * (mặc định Spring trả body rỗng).
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        boolean tokenSent = request.getAttribute(JwtAuthenticationFilter.INVALID_TOKEN_ATTRIBUTE) != null;
        String message = tokenSent
                ? "Token không hợp lệ, đã hết hạn hoặc đã bị thu hồi. Vui lòng đăng nhập lại"
                : "Bạn cần đăng nhập để truy cập tài nguyên này";

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("WWW-Authenticate", "Bearer");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(),
                ApiResponse.error(ErrorCode.UNAUTHORIZED, message, request.getRequestURI()));
    }
}
