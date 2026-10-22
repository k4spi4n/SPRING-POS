package com.winmart.wms.logging;

import com.winmart.wms.common.MdcKeys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Filter đầu chuỗi: gán mã truy vết (requestId) + IP vào MDC cho mọi dòng log của request,
 * trả lại cho client qua header {@code X-Request-Id} và log 1 dòng access-log khi kết thúc
 * (method, URI, status, thời gian xử lý). Chạy TRƯỚC Spring Security nên ghi nhận được cả
 * các request bị từ chối 401/403.
 * <p>
 * Không log query string, header hay body để tránh lộ token / mật khẩu.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    private final long slowRequestThresholdMs;

    public RequestLoggingFilter(
            @Value("${app.performance.slow-request-threshold-ms:1000}") long slowRequestThresholdMs) {
        this.slowRequestThresholdMs = slowRequestThresholdMs;
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return true;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        MDC.put(MdcKeys.REQUEST_ID, requestId);
        MDC.put(MdcKeys.CLIENT_IP, request.getRemoteAddr());
        response.setHeader(REQUEST_ID_HEADER, requestId);

        long start = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long tookMs = (System.nanoTime() - start) / 1_000_000;
            logAccess(request, response, tookMs);
            MDC.remove(MdcKeys.REQUEST_ID);
            MDC.remove(MdcKeys.CLIENT_IP);
            MDC.remove(MdcKeys.USERNAME);
        }
    }

    private void logAccess(HttpServletRequest request, HttpServletResponse response, long tookMs) {
        int status = response.getStatus();
        String uri = request.getRequestURI();
        if (isNoise(uri) && status < 400) {
            log.trace("{} {} -> {} ({} ms)", request.getMethod(), uri, status, tookMs);
            return;
        }
        if (status >= 500) {
            log.error("{} {} -> {} ({} ms)", request.getMethod(), uri, status, tookMs);
        } else if (tookMs >= slowRequestThresholdMs) {
            log.warn("SLOW REQUEST {} {} -> {} ({} ms, ngưỡng {} ms)",
                    request.getMethod(), uri, status, tookMs, slowRequestThresholdMs);
        } else {
            log.info("{} {} -> {} ({} ms)", request.getMethod(), uri, status, tookMs);
        }
    }

    private static boolean isNoise(String uri) {
        return uri.startsWith("/actuator/health")
                || uri.startsWith("/swagger-ui")
                || uri.startsWith("/v3/api-docs")
                || uri.equals("/favicon.ico");
    }

    private static String resolveRequestId(HttpServletRequest request) {
        String incoming = request.getHeader(REQUEST_ID_HEADER);
        // Chỉ chấp nhận id "sạch" từ client để tránh log injection (xuống dòng, ký tự lạ)
        if (incoming != null && SAFE_REQUEST_ID.matcher(incoming).matches()) {
            return incoming;
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
