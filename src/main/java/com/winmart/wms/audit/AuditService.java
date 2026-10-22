package com.winmart.wms.audit;

import com.winmart.wms.common.MdcKeys;
import com.winmart.wms.common.PageResponse;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Ghi và truy vấn nhật ký kiểm toán. Mỗi bản ghi được ghi đồng thời vào:
 * <ol>
 *   <li>bảng {@code audit_logs} (truy vấn qua API cho ADMIN)</li>
 *   <li>file {@code logs/audit.log} qua logger "AUDIT" (chống mất vết nếu DB gặp sự cố)</li>
 * </ol>
 * Dùng {@code REQUIRES_NEW} để bản ghi (đặc biệt là bản ghi lỗi) KHÔNG bị rollback theo
 * giao dịch nghiệp vụ đang thất bại. Việc ghi audit không bao giờ được làm hỏng nghiệp vụ chính.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("AUDIT");
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final int MAX_DETAILS = 500;

    private final AuditLogRepository repository;
    private final PlatformTransactionManager transactionManager;

    public void success(AuditAction action, String entityType, Long entityId, String details) {
        record(currentUsername(), action, entityType, entityId, true, details);
    }

    public void failure(AuditAction action, String entityType, Long entityId, String details) {
        record(currentUsername(), action, entityType, entityId, false, details);
    }

    /**
     * Ghi 1 bản ghi audit trong transaction ĐỘC LẬP (REQUIRES_NEW) để không bị rollback theo
     * nghiệp vụ. Dùng TransactionTemplate thay vì @Transactional để tránh self-invocation và
     * để bắt được lỗi commit ngay tại đây.
     */
    public void record(String username, AuditAction action, String entityType, Long entityId,
                       boolean success, String details) {
        String clientIp = MDC.get(MdcKeys.CLIENT_IP);
        String requestId = MDC.get(MdcKeys.REQUEST_ID);
        String safeDetails = truncate(details);

        AUDIT_LOG.info("user={} action={} entity={} entityId={} success={} ip={} details={}",
                username, action, entityType, entityId, success, clientIp, safeDetails);

        AuditLog entry = AuditLog.builder()
                .createdAt(LocalDateTime.now())
                .username(truncate(username, 50))
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .success(success)
                .details(safeDetails)
                .clientIp(clientIp)
                .requestId(requestId)
                .build();

        try {
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            template.executeWithoutResult(status -> repository.save(entry));
        } catch (RuntimeException ex) {
            // Không để lỗi ghi audit làm hỏng luồng nghiệp vụ; bản ghi vẫn còn trong audit.log
            log.error("Không thể lưu audit log vào CSDL (action={}, user={}): {}", action, username, ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLog> search(String username, AuditAction action, String entityType,
                                         Boolean success, LocalDateTime from, LocalDateTime to,
                                         Pageable pageable) {
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (username != null && !username.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("username")), username.toLowerCase()));
            }
            if (action != null) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (entityType != null && !entityType.isBlank()) {
                predicates.add(cb.equal(root.get("entityType"), entityType));
            }
            if (success != null) {
                predicates.add(cb.equal(root.get("success"), success));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<AuditLog> page = repository.findAll(spec, pageable);
        return PageResponse.of(page);
    }

    public static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated()) {
            String fromMdc = MDC.get(MdcKeys.USERNAME);
            return fromMdc != null ? fromMdc : "anonymous";
        }
        return auth.getName();
    }

    private static String truncate(String value) {
        return truncate(value, MAX_DETAILS);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }
}
