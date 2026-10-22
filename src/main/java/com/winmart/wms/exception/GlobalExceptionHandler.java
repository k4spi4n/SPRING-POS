package com.winmart.wms.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.winmart.wms.audit.AuditAction;
import com.winmart.wms.audit.AuditService;
import com.winmart.wms.common.ApiResponse;
import com.winmart.wms.common.MdcKeys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Xử lý ngoại lệ tập trung. Nguyên tắc:
 * <ul>
 *   <li>Mọi lỗi trả về cùng cấu trúc {@link ApiResponse} kèm {@code errorCode}, {@code path}, {@code requestId}</li>
 *   <li>Lỗi do client (4xx): log WARN 1 dòng, không stack trace</li>
 *   <li>Lỗi hệ thống (5xx): log ERROR đầy đủ stack trace, nhưng KHÔNG trả chi tiết nội bộ cho client -
 *       chỉ trả requestId để tra log</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final AuditService auditService;

    // ---------------------------------------------------------------- Nghiệp vụ

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccountLocked(AccountLockedException ex, HttpServletRequest request) {
        log.warn("[{}] {}", ex.getErrorCode().getCode(), ex.getMessage());
        return ResponseEntity.status(ex.getErrorCode().getHttpStatus())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Object>> handleAppException(AppException ex, HttpServletRequest request) {
        ErrorCode code = ex.getErrorCode();
        if (code == ErrorCode.INTERNAL_SERVER_ERROR) {
            log.error("[{}] {}", code.getCode(), ex.getMessage(), ex);
        } else {
            log.warn("[{}] {}", code.getCode(), ex.getMessage());
        }
        return build(code, ex.getMessage(), request);
    }

    // ---------------------------------------------------------------- Validation / request sai

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        // Lỗi mức đối tượng (class-level constraint) không có tên field
        ex.getBindingResult().getGlobalErrors()
                .forEach(error -> errors.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));

        log.warn("[VALIDATION_ERROR] {} {}: {}", request.getMethod(), request.getRequestURI(), errors);
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.VALIDATION_ERROR, "Dữ liệu không hợp lệ", request.getRequestURI(), errors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, String> errors = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        v -> v.getPropertyPath().toString(),
                        v -> v.getMessage(),
                        (a, b) -> a,
                        LinkedHashMap::new));
        log.warn("[VALIDATION_ERROR] {} {}: {}", request.getMethod(), request.getRequestURI(), errors);
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.VALIDATION_ERROR, "Dữ liệu không hợp lệ", request.getRequestURI(), errors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        String message = "Nội dung yêu cầu không phải JSON hợp lệ";
        if (ex.getCause() instanceof JsonMappingException jme && !jme.getPath().isEmpty()) {
            String field = jme.getPath().stream()
                    .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
                    .collect(Collectors.joining("."));
            message = "Giá trị của trường '" + field + "' sai định dạng hoặc không hợp lệ";
        }
        log.warn("[MALFORMED_REQUEST] {} {}: {}", request.getMethod(), request.getRequestURI(), message);
        return build(ErrorCode.MALFORMED_REQUEST, message, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String message = "Tham số '" + ex.getName() + "' có giá trị không hợp lệ";
        log.warn("[BAD_REQUEST] {}", message);
        return build(ErrorCode.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest request) {
        String message = "Thiếu tham số bắt buộc '" + ex.getParameterName() + "'";
        log.warn("[BAD_REQUEST] {}", message);
        return build(ErrorCode.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ApiResponse<Object>> handleBadProperty(PropertyReferenceException ex, HttpServletRequest request) {
        String message = "Trường sắp xếp/lọc '" + ex.getPropertyName() + "' không tồn tại";
        log.warn("[BAD_REQUEST] {}", message);
        return build(ErrorCode.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        log.warn("[METHOD_NOT_ALLOWED] {} {}", request.getMethod(), request.getRequestURI());
        return build(ErrorCode.METHOD_NOT_ALLOWED, "Phương thức " + ex.getMethod() + " không được hỗ trợ cho đường dẫn này", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        log.warn("[UNSUPPORTED_MEDIA_TYPE] {}", ex.getContentType());
        return build(ErrorCode.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE.getDefaultMessage(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        log.debug("[RESOURCE_NOT_FOUND] {} {}", request.getMethod(), request.getRequestURI());
        return build(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đường dẫn " + request.getRequestURI(), request);
    }

    // ---------------------------------------------------------------- Dữ liệu / đồng thời

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        // Chi tiết ràng buộc (tên bảng/cột) chỉ ghi log, không trả cho client
        log.warn("[DATA_INTEGRITY_VIOLATION] {} {}: {}", request.getMethod(), request.getRequestURI(),
                ex.getMostSpecificCause().getMessage());
        return build(ErrorCode.DATA_INTEGRITY_VIOLATION, ErrorCode.DATA_INTEGRITY_VIOLATION.getDefaultMessage(), request);
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<ApiResponse<Object>> handleConcurrency(RuntimeException ex, HttpServletRequest request) {
        log.warn("[CONCURRENT_UPDATE] {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getClass().getSimpleName());
        return build(ErrorCode.CONCURRENT_UPDATE, ErrorCode.CONCURRENT_UPDATE.getDefaultMessage(), request);
    }

    // ---------------------------------------------------------------- Bảo mật

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Object>> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        return build(ErrorCode.INVALID_CREDENTIALS, ErrorCode.INVALID_CREDENTIALS.getDefaultMessage(), request);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiResponse<Object>> handleDisabled(DisabledException ex, HttpServletRequest request) {
        return build(ErrorCode.ACCOUNT_DISABLED, ErrorCode.ACCOUNT_DISABLED.getDefaultMessage(), request);
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ApiResponse<Object>> handleLocked(LockedException ex, HttpServletRequest request) {
        return build(ErrorCode.ACCOUNT_LOCKED, ErrorCode.ACCOUNT_LOCKED.getDefaultMessage(), request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Object>> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        log.warn("[UNAUTHORIZED] {}", ex.getClass().getSimpleName());
        return build(ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.getDefaultMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        String user = AuditService.currentUsername();
        log.warn("[FORBIDDEN] user={} bị từ chối {} {}", user, request.getMethod(), request.getRequestURI());
        auditService.failure(AuditAction.ACCESS_DENIED, "Endpoint", null, request.getMethod() + " " + request.getRequestURI());
        return build(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.getDefaultMessage(), request);
    }

    // ---------------------------------------------------------------- Lỗi không lường trước

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("[INTERNAL_SERVER_ERROR] {} {} - lỗi không xử lý được", request.getMethod(), request.getRequestURI(), ex);
        String message = ErrorCode.INTERNAL_SERVER_ERROR.getDefaultMessage()
                + ". Vui lòng liên hệ quản trị viên và cung cấp mã: " + MDC.get(MdcKeys.REQUEST_ID);
        return build(ErrorCode.INTERNAL_SERVER_ERROR, message, request);
    }

    private ResponseEntity<ApiResponse<Object>> build(ErrorCode code, String message, HttpServletRequest request) {
        return ResponseEntity.status(code.getHttpStatus())
                .body(ApiResponse.error(code, message, request.getRequestURI()));
    }
}
