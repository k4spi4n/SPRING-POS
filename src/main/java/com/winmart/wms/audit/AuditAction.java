package com.winmart.wms.audit;

/**
 * Các hành động được ghi vào nhật ký kiểm toán (audit trail).
 */
public enum AuditAction {
    // Nghiệp vụ chung
    CREATE,
    UPDATE,
    DELETE,
    CANCEL,

    // Bảo mật / tài khoản
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    LOGOUT,
    ACCOUNT_LOCKED,
    PASSWORD_CHANGED,
    ACTIVATE_USER,
    DEACTIVATE_USER,
    ACCESS_DENIED
}
