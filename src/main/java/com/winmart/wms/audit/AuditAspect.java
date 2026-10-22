package com.winmart.wms.audit;

import com.winmart.wms.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * Tự động ghi audit cho mọi phương thức gắn {@link Audited}.
 * <p>
 * Order 100 (nhỏ hơn advisor @Transactional) => aspect nằm BÊN NGOÀI transaction nghiệp vụ:
 * bản ghi SUCCESS chỉ được ghi khi transaction đã commit thật sự; bản ghi FAILURE được ghi
 * sau khi transaction nghiệp vụ đã rollback.
 */
@Aspect
@Component
@Order(100)
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditService auditService;

    @Around("@annotation(audited)")
    public Object audit(ProceedingJoinPoint pjp, Audited audited) throws Throwable {
        Long argId = firstLongArgument(pjp.getArgs());
        Object result;
        try {
            result = pjp.proceed();
        } catch (Throwable ex) {
            auditService.failure(audited.action(), audited.entity(), argId, describe(ex));
            throw ex;
        }
        Long entityId = extractId(result);
        auditService.success(audited.action(), audited.entity(), entityId != null ? entityId : argId,
                ((MethodSignature) pjp.getSignature()).getMethod().getName());
        return result;
    }

    private static Long firstLongArgument(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof Long id) {
                return id;
            }
        }
        return null;
    }

    private static Long extractId(Object result) {
        if (result == null) {
            return null;
        }
        try {
            Method getter = result.getClass().getMethod("getId");
            Object id = getter.invoke(result);
            return id instanceof Long l ? l : null;
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    private static String describe(Throwable ex) {
        // Chỉ đưa thông điệp nghiệp vụ (AppException) vào audit; lỗi hệ thống chỉ ghi tên lớp.
        if (ex instanceof AppException) {
            return ex.getMessage();
        }
        return ex.getClass().getSimpleName();
    }
}
