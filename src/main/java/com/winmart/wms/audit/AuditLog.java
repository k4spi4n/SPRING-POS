package com.winmart.wms.audit;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

/**
 * Bản ghi nhật ký kiểm toán: AI làm GÌ, lên ĐỐI TƯỢNG nào, KHI nào, từ ĐÂU, kết quả ra sao.
 * Bất biến (@Immutable): chỉ INSERT, không bao giờ UPDATE/DELETE qua ứng dụng.
 * Không có khóa ngoại tới bảng nghiệp vụ để vẫn giữ được vết khi dữ liệu gốc bị xóa.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Immutable
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_created_at", columnList = "created_at"),
        @Index(name = "idx_audit_username", columnList = "username"),
        @Index(name = "idx_audit_entity", columnList = "entity_type, entity_id")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "username", length = 50, nullable = false, updatable = false)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 30, nullable = false, updatable = false)
    private AuditAction action;

    @Column(name = "entity_type", length = 50, updatable = false)
    private String entityType;

    @Column(name = "entity_id", updatable = false)
    private Long entityId;

    @Column(name = "success", nullable = false, updatable = false)
    private boolean success;

    @Column(name = "details", length = 500, updatable = false)
    private String details;

    @Column(name = "client_ip", length = 64, updatable = false)
    private String clientIp;

    @Column(name = "request_id", length = 64, updatable = false)
    private String requestId;
}
