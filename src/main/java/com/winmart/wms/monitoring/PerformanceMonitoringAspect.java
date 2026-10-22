package com.winmart.wms.monitoring;

import com.winmart.wms.exception.AppException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Giám sát hiệu năng + logging cho toàn bộ tầng service:
 * <ul>
 *   <li>Đo thời gian từng phương thức và đẩy vào Micrometer Timer {@code wms.service.execution}
 *       (tag class / method / outcome) - xem qua {@code /actuator/metrics/wms.service.execution}</li>
 *   <li>Log DEBUG khi vào/ra, log WARN khi chậm hơn ngưỡng {@code app.performance.slow-service-threshold-ms}</li>
 *   <li>Log lỗi hệ thống (không phải lỗi nghiệp vụ) kèm thời gian xử lý</li>
 * </ul>
 * Không log tham số / kết quả để tránh lộ mật khẩu hoặc dữ liệu nhạy cảm.
 * Order 50: ngoài cùng, đo trọn cả thời gian mở/commit transaction.
 */
@Slf4j
@Aspect
@Component
@Order(50)
public class PerformanceMonitoringAspect {

    public static final String TIMER_NAME = "wms.service.execution";

    private final MeterRegistry registry;
    private final long slowThresholdMs;

    public PerformanceMonitoringAspect(
            MeterRegistry registry,
            @Value("${app.performance.slow-service-threshold-ms:500}") long slowThresholdMs) {
        this.registry = registry;
        this.slowThresholdMs = slowThresholdMs;
    }

    @Around("within(com.winmart.wms.service..*)")
    public Object measure(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();

        log.debug("→ {}.{}", className, methodName);
        long start = System.nanoTime();
        String outcome = "success";
        try {
            return pjp.proceed();
        } catch (AppException ex) {
            outcome = "business_error";
            throw ex;
        } catch (Throwable ex) {
            outcome = "error";
            log.error("✗ {}.{} thất bại: {}", className, methodName, ex.toString());
            throw ex;
        } finally {
            Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
            Timer.builder(TIMER_NAME)
                    .description("Thời gian thực thi phương thức tầng service")
                    .tag("class", className)
                    .tag("method", methodName)
                    .tag("outcome", outcome)
                    .publishPercentileHistogram()
                    .register(registry)
                    .record(elapsed);

            long ms = elapsed.toMillis();
            if (ms >= slowThresholdMs) {
                log.warn("SLOW SERVICE {}.{} mất {} ms (ngưỡng {} ms, kết quả: {})",
                        className, methodName, ms, slowThresholdMs, outcome);
            } else {
                log.debug("← {}.{} [{}] {} ms", className, methodName, outcome, ms);
            }
        }
    }
}
