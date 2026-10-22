package com.winmart.wms.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.winmart.wms.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.MDC;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private String message;
    /** Mã lỗi máy đọc được (chỉ có khi success = false), xem {@link ErrorCode}. */
    private String errorCode;
    private T data;
    /** Đường dẫn request gây lỗi (chỉ có khi success = false). */
    private String path;
    /** Mã truy vết request (khớp header X-Request-Id và dòng log) - chỉ có khi lỗi. */
    private String requestId;
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message("Thành công")
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .requestId(MDC.get(MdcKeys.REQUEST_ID))
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message, String path) {
        return error(errorCode, message, path, null);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message, String path, T data) {
        return ApiResponse.<T>builder()
                .success(false)
                .errorCode(errorCode.getCode())
                .message(message)
                .path(path)
                .data(data)
                .requestId(MDC.get(MdcKeys.REQUEST_ID))
                .timestamp(LocalDateTime.now())
                .build();
    }
}
