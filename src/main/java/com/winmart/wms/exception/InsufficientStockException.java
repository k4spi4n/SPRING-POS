package com.winmart.wms.exception;

public class InsufficientStockException extends AppException {
    public InsufficientStockException(String message) {
        super(ErrorCode.INSUFFICIENT_STOCK, message);
    }
}
