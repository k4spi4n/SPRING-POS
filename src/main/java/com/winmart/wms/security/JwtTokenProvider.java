package com.winmart.wms.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
public class JwtTokenProvider {

    /** Secret mặc định chỉ dành cho demo; xuất hiện trong repo nên KHÔNG an toàn cho môi trường thật. */
    private static final String DEV_DEFAULT_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final long jwtExpirationMs;
    private final String issuer;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long jwtExpirationMs,
            @Value("${app.jwt.issuer:winmart-wms}") String issuer) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("app.jwt.secret (biến môi trường JWT_SECRET) phải dài tối thiểu "
                    + MIN_SECRET_BYTES + " ký tự để đảm bảo an toàn cho HS256");
        }
        if (DEV_DEFAULT_SECRET.equals(secret)) {
            log.warn("!!! Đang dùng JWT secret mặc định dành cho DEV. Hãy đặt biến môi trường JWT_SECRET trước khi triển khai thật.");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.jwtExpirationMs = jwtExpirationMs;
        this.issuer = issuer;
    }

    public String generateToken(Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        String roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        return Jwts.builder()
                .id(UUID.randomUUID().toString()) // jti: định danh token để có thể thu hồi khi logout
                .issuer(issuer)
                .subject(userDetails.getUsername())
                .claim("userId", userDetails.getId())
                .claim("fullName", userDetails.getFullName())
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    /**
     * Xác thực chữ ký, hạn dùng và issuer. Trả về claims nếu token hợp lệ, rỗng nếu không.
     */
    public Optional<Claims> parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException e) {
            // Không log token; chỉ log loại lỗi ở mức DEBUG để tránh spam khi bị dò token
            log.debug("JWT bị từ chối: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
