package com.winmart.wms.exception;

/**
 * Thao tác không hợp lệ với trạng thái hiện tại của chứng từ (vd: hủy phiếu đã hủy).
 */
public class InvalidStateException extends AppException {
    public InvalidStateException(String message) {
        super(ErrorCode.INVALID_STATE, message);
    }
}
