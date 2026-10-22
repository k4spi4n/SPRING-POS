package com.winmart.wms.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    // 4xx - lỗi do phía client
    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND", "Không tìm thấy tài nguyên yêu cầu", HttpStatus.NOT_FOUND),
    BAD_REQUEST("BAD_REQUEST", "Yêu cầu không hợp lệ", HttpStatus.BAD_REQUEST),
    VALIDATION_ERROR("VALIDATION_ERROR", "Dữ liệu không hợp lệ", HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST("MALFORMED_REQUEST", "Nội dung yêu cầu sai định dạng", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED("UNAUTHORIZED", "Chưa xác thực hoặc token không hợp lệ", HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS("INVALID_CREDENTIALS", "Tên đăng nhập hoặc mật khẩu không chính xác", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("FORBIDDEN", "Bạn không có quyền thực hiện thao tác này", HttpStatus.FORBIDDEN),
    ACCOUNT_DISABLED("ACCOUNT_DISABLED", "Tài khoản đã bị vô hiệu hóa", HttpStatus.FORBIDDEN),
    ACCOUNT_LOCKED("ACCOUNT_LOCKED", "Tài khoản tạm thời bị khóa do đăng nhập sai nhiều lần", HttpStatus.LOCKED),
    TOO_MANY_REQUESTS("TOO_MANY_REQUESTS", "Quá nhiều yêu cầu, vui lòng thử lại sau", HttpStatus.TOO_MANY_REQUESTS),
    METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED", "Phương thức HTTP không được hỗ trợ", HttpStatus.METHOD_NOT_ALLOWED),
    UNSUPPORTED_MEDIA_TYPE("UNSUPPORTED_MEDIA_TYPE", "Định dạng nội dung không được hỗ trợ", HttpStatus.UNSUPPORTED_MEDIA_TYPE),

    // Lỗi nghiệp vụ kho
    INSUFFICIENT_STOCK("INSUFFICIENT_STOCK", "Số lượng tồn kho không đủ để xuất", HttpStatus.BAD_REQUEST),
    PRODUCT_EXPIRED("PRODUCT_EXPIRED", "Lô hàng đã hết hạn sử dụng", HttpStatus.BAD_REQUEST),
    INVALID_STATE("INVALID_STATE", "Trạng thái chứng từ không cho phép thao tác này", HttpStatus.CONFLICT),

    // Xung đột dữ liệu / đồng thời
    DUPLICATE_RESOURCE("DUPLICATE_RESOURCE", "Dữ liệu đã tồn tại trong hệ thống", HttpStatus.CONFLICT),
    DATA_INTEGRITY_VIOLATION("DATA_INTEGRITY_VIOLATION", "Dữ liệu vi phạm ràng buộc toàn vẹn (đang được tham chiếu hoặc trùng lặp)", HttpStatus.CONFLICT),
    CONCURRENT_UPDATE("CONCURRENT_UPDATE", "Dữ liệu vừa được người khác thay đổi, vui lòng thử lại", HttpStatus.CONFLICT),

    // 5xx
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", "Lỗi máy chủ nội bộ", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }
}
