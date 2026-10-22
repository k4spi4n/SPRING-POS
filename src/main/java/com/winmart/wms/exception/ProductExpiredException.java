package com.winmart.wms.exception;

public class ProductExpiredException extends AppException {
    public ProductExpiredException(String message) {
        super(ErrorCode.PRODUCT_EXPIRED, message);
    }
}
