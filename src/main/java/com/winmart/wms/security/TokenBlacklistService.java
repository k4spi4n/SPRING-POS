package com.winmart.wms.security;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Danh sách thu hồi token (logout / đổi mật khẩu) lưu trong bộ nhớ, tự dọn các token đã hết hạn.
 * <p>
 * Lưu ý: chỉ phù hợp chạy 1 instance. Khi scale ngang cần chuyển sang Redis/DB dùng chung.
 */
@Service
public class TokenBlacklistService {

    private static final int PURGE_THRESHOLD = 500;

    private final Map<String, Instant> revoked = new ConcurrentHashMap<>();

    public void revoke(String jti, Date expiresAt) {
        if (jti == null || expiresAt == null) {
            return;
        }
        if (revoked.size() > PURGE_THRESHOLD) {
            Instant now = Instant.now();
            revoked.values().removeIf(exp -> exp.isBefore(now));
        }
        revoked.put(jti, expiresAt.toInstant());
    }

    public boolean isRevoked(String jti) {
        return jti != null && revoked.containsKey(jti);
    }
}
