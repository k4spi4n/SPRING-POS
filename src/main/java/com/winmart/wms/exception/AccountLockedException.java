package com.winmart.wms.exception;

import lombok.Getter;

/**
 * Đăng nhập bị chặn tạm thời do sai mật khẩu quá số lần cho phép.
 */
@Getter
public class AccountLockedException extends AppException {

    private final long retryAfterSeconds;

    public AccountLockedException(long retryAfterSeconds) {
        super(ErrorCode.ACCOUNT_LOCKED, String.format(
                "Đăng nhập sai quá nhiều lần. Vui lòng thử lại sau %d phút.",
                Math.max(1, (retryAfterSeconds + 59) / 60)));
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
