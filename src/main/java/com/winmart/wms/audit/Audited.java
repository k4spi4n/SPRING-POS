package com.winmart.wms.audit;

import java.lang.annotation.*;

/**
 * Đánh dấu một phương thức service cần được ghi nhật ký kiểm toán.
 * <p>
 * {@link AuditAspect} sẽ ghi 1 bản ghi SUCCESS khi phương thức hoàn tất (sau khi transaction commit)
 * hoặc FAILURE khi ném ngoại lệ. entityId lấy từ {@code getId()} của kết quả trả về,
 * hoặc từ tham số {@code Long} đầu tiên (id) của phương thức.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Audited {

    AuditAction action();

    /** Tên loại đối tượng bị tác động, ví dụ "Product", "StockIssue". */
    String entity();
}
