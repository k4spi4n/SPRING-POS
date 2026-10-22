package com.winmart.wms.security;

import com.winmart.wms.exception.AccountLockedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chống dò mật khẩu (brute-force): sau {@code max-attempts} lần sai liên tiếp của cùng
 * (địa chỉ IP, username) sẽ chặn đăng nhập trong {@code lock-duration-minutes} phút.
 * Áp dụng cả với username không tồn tại để không lộ thông tin tài khoản nào có thật.
 * <p>
 * Lưu trong bộ nhớ (1 instance). Khi scale ngang cần chuyển sang Redis/DB dùng chung.
 */
@Service
public class LoginAttemptService {

    private static final int CLEANUP_THRESHOLD = 1000;

    private final int maxAttempts;
    private final Duration lockDuration;
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    public LoginAttemptService(
            @Value("${app.security.login.max-attempts:5}") int maxAttempts,
            @Value("${app.security.login.lock-duration-minutes:15}") long lockMinutes) {
        this.maxAttempts = maxAttempts;
        this.lockDuration = Duration.ofMinutes(lockMinutes);
    }

    /** Ném {@link AccountLockedException} nếu khóa đang còn hiệu lực. */
    public void assertNotLocked(String clientIp, String username) {
        Attempt attempt = attempts.get(key(clientIp, username));
        if (attempt != null && attempt.lockedUntil != null) {
            Instant now = Instant.now();
            if (attempt.lockedUntil.isAfter(now)) {
                throw new AccountLockedException(Duration.between(now, attempt.lockedUntil).getSeconds());
            }
        }
    }

    /** Ghi nhận 1 lần đăng nhập sai. @return true nếu lần sai này làm kích hoạt khóa. */
    public boolean recordFailure(String clientIp, String username) {
        if (attempts.size() > CLEANUP_THRESHOLD) {
            Instant now = Instant.now();
            attempts.values().removeIf(a -> a.isStale(now, lockDuration));
        }
        boolean[] justLocked = {false};
        attempts.compute(key(clientIp, username), (k, current) -> {
            Instant now = Instant.now();
            Attempt next = (current == null || current.isStale(now, lockDuration)) ? new Attempt() : current;
            next.failures++;
            next.lastFailure = now;
            if (next.failures >= maxAttempts) {
                next.lockedUntil = now.plus(lockDuration);
                next.failures = 0;
                justLocked[0] = true;
            }
            return next;
        });
        return justLocked[0];
    }

    public void recordSuccess(String clientIp, String username) {
        attempts.remove(key(clientIp, username));
    }

    private static String key(String clientIp, String username) {
        String user = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        if (user.length() > 100) {
            user = user.substring(0, 100);
        }
        return (clientIp == null ? "unknown" : clientIp) + "|" + user;
    }

    private static final class Attempt {
        private int failures;
        private Instant lastFailure = Instant.now();
        private Instant lockedUntil;

        boolean isStale(Instant now, Duration window) {
            if (lockedUntil != null) {
                return lockedUntil.isBefore(now);
            }
            return lastFailure.plus(window).isBefore(now);
        }
    }
}
