package com.winmart.wms.common;

import com.winmart.wms.exception.AppException;
import com.winmart.wms.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.function.Predicate;

/**
 * Sinh mã chứng từ dạng {@code PREFIX-yyyyMMdd-XXXXXX} (6 ký tự chữ/số ngẫu nhiên) và kiểm tra
 * trùng với CSDL, thử lại nếu trùng. Ràng buộc UNIQUE ở CSDL vẫn là chốt chặn cuối cùng.
 */
@Component
public class DocumentCodeGenerator {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // bỏ ký tự dễ nhầm: I, O, 0, 1
    private static final int SUFFIX_LENGTH = 6;
    private static final int MAX_ATTEMPTS = 10;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final SecureRandom random = new SecureRandom();

    public String next(String prefix, Predicate<String> alreadyExists) {
        String date = LocalDate.now().format(DATE_FORMAT);
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            StringBuilder suffix = new StringBuilder(SUFFIX_LENGTH);
            for (int i = 0; i < SUFFIX_LENGTH; i++) {
                suffix.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            String code = prefix + "-" + date + "-" + suffix;
            if (!alreadyExists.test(code)) {
                return code;
            }
        }
        throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Không thể sinh mã chứng từ duy nhất, vui lòng thử lại");
    }
}
