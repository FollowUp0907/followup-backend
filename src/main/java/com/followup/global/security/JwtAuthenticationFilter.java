package com.followup.global.security;

import com.followup.global.exception.BusinessException;
import com.followup.global.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 유효한 토큰이면 userId를 principal로 하는 Authentication을 SecurityContext에 저장해
 * CurrentUserProvider가 로그인 사용자를 조회할 수 있게 한다. 토큰이 없으면 그대로 통과시키고,
 * 유효하지 않거나 만료된 토큰은 원인만 요청 속성에 남겨 JwtAuthenticationEntryPoint가 구분해 응답하게 한다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String TOKEN_ERROR_ATTRIBUTE = "jwtTokenError";

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null) {
            try {
                Long userId = jwtProvider.validateAndGetUserId(token);
                Authentication authentication = new UsernamePasswordAuthenticationToken(userId, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (BusinessException e) {
                request.setAttribute(TOKEN_ERROR_ATTRIBUTE, e.getErrorCode());
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
