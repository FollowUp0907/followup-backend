package com.followup.global.security;

import com.followup.global.exception.ErrorCode;
import com.followup.global.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.ObjectMapper;

/**
 * 인증되지 않은 요청이 보호된 API에 접근하면 GlobalExceptionHandler와 동일한 {@link ErrorResponse} 형식으로 응답한다.
 * JwtAuthenticationFilter가 남긴 토큰 오류(INVALID_TOKEN/EXPIRED_TOKEN)가 있으면 그대로 쓰고, 없으면 UNAUTHORIZED로 처리한다.
 * Spring Security의 인증 체크가 MVC 핸들러 매핑보다 먼저 실행되므로, 매핑 자체가 없는 경로도 그대로 두면
 * 401로 응답돼 "존재하지 않는 API"와 "인증이 안 된 API"를 프론트가 구분할 수 없다. 그래서 401을 쓰기 전에
 * 실제 매핑 여부를 직접 확인해, 매핑이 없으면 GlobalExceptionHandler의 NoHandlerFoundException 처리와
 * 동일한 ROUTE_NOT_FOUND(404)로 응답한다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;
    private final RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws IOException {
        ErrorCode errorCode = resolveErrorCode(request);

        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ErrorResponse.of(errorCode)));
    }

    private ErrorCode resolveErrorCode(HttpServletRequest request) {
        if (!isMappedToHandler(request)) {
            return ErrorCode.ROUTE_NOT_FOUND;
        }

        ErrorCode tokenError = (ErrorCode) request.getAttribute(JwtAuthenticationFilter.TOKEN_ERROR_ATTRIBUTE);
        return tokenError != null ? tokenError : ErrorCode.UNAUTHORIZED;
    }

    /**
     * 매핑이 실제로 있는지 직접 조회한다 — 조회 중 예외가 나는 경우(예: 경로는 있지만 메서드가 안 맞는
     * 경우)는 있는지 없는지 애매하므로, 존재하지 않는 경로라고 단정하지 않고 보수적으로 인증 실패(401)
     * 쪽으로 둔다.
     */
    private boolean isMappedToHandler(HttpServletRequest request) {
        try {
            return requestMappingHandlerMapping.getHandler(request) != null;
        } catch (Exception e) {
            return true;
        }
    }
}
