package com.winmart.wms.security;

import com.winmart.wms.common.MdcKeys;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Request attribute báo cho EntryPoint biết token có gửi lên nhưng không hợp lệ. */
    public static final String INVALID_TOKEN_ATTRIBUTE = "wms.invalidToken";

    private final JwtTokenProvider tokenProvider;
    private final UserDetailsServiceImpl userDetailsService;
    private final TokenBlacklistService tokenBlacklist;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String jwt = getJwtFromRequest(request);
        if (StringUtils.hasText(jwt)) {
            authenticate(jwt, request);
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(String jwt, HttpServletRequest request) {
        try {
            Claims claims = tokenProvider.parseToken(jwt).orElse(null);
            if (claims == null || tokenBlacklist.isRevoked(claims.getId())) {
                request.setAttribute(INVALID_TOKEN_ATTRIBUTE, Boolean.TRUE);
                return;
            }

            UserDetails userDetails = userDetailsService.loadUserByUsername(claims.getSubject());
            // Tài khoản bị vô hiệu hóa sau khi cấp token thì token cũ cũng mất hiệu lực ngay
            if (!userDetails.isEnabled()) {
                log.warn("Token của tài khoản đã bị vô hiệu hóa bị từ chối: {}", userDetails.getUsername());
                request.setAttribute(INVALID_TOKEN_ATTRIBUTE, Boolean.TRUE);
                return;
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            MDC.put(MdcKeys.USERNAME, userDetails.getUsername());
        } catch (AuthenticationException ex) {
            // Ví dụ: user trong token không còn tồn tại
            request.setAttribute(INVALID_TOKEN_ATTRIBUTE, Boolean.TRUE);
            log.debug("Không thể xác thực từ JWT: {}", ex.getClass().getSimpleName());
        } catch (RuntimeException ex) {
            // Lỗi hạ tầng (vd: DB): ghi log lỗi, request tiếp tục như chưa xác thực -> 401
            log.error("Lỗi khi xử lý JWT", ex);
        }
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
