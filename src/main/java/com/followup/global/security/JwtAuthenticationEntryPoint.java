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
import tools.jackson.databind.ObjectMapper;

/**
 * 인증되지 않은 요청이 보호된 API에 접근하면 GlobalExceptionHandler와 동일한 {@link ErrorResponse} 형식으로 응답한다.
 * JwtAuthenticationFilter가 남긴 토큰 오류(INVALID_TOKEN/EXPIRED_TOKEN)가 있으면 그대로 쓰고, 없으면 UNAUTHORIZED로 처리한다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws IOException {
        ErrorCode errorCode = (ErrorCode) request.getAttribute(JwtAuthenticationFilter.TOKEN_ERROR_ATTRIBUTE);
        if (errorCode == null) {
            errorCode = ErrorCode.UNAUTHORIZED;
        }

        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ErrorResponse.of(errorCode)));
    }
}
