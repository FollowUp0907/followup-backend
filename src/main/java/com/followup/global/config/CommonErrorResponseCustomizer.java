package com.followup.global.config;

import com.followup.auth.controller.AuthController;
import com.followup.global.common.HealthController;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.List;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

/**
 * 이 프로젝트의 모든 서비스 메서드가 getXxxOrThrow + requireMember/requireOwner 패턴을 공통으로 쓰기 때문에,
 * 인증이 필요한 API는 401/403/404를, 그 외에는 400을 매 endpoint마다 반복 선언하지 않고 여기서 채운다.
 * signup/login/health처럼 개별적으로 다른 응답이 필요한 경우는 @ApiResponse로 직접 덮어쓰면 된다.
 */
@Component
public class CommonErrorResponseCustomizer implements GlobalOperationCustomizer {

    private static final List<String> ERROR_CODES = List.of("400", "401", "403", "404", "409");

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        ApiResponses responses = operation.getResponses();
        addIfAbsent(responses, "400", "잘못된 요청");

        Class<?> beanType = handlerMethod.getBeanType();
        if (beanType != AuthController.class && beanType != HealthController.class) {
            addIfAbsent(responses, "401", "인증 필요");
            addIfAbsent(responses, "403", "권한 없음");
            addIfAbsent(responses, "404", "리소스를 찾을 수 없음");
        }

        // @ApiResponse로 개별 선언된 에러 코드(예: 409)는 swagger-core가 기본적으로
        // 메서드 반환 타입 스키마를 그대로 붙이므로, 에러 코드는 전부 ErrorResponse로 강제한다.
        for (String code : ERROR_CODES) {
            ApiResponse response = responses.get(code);
            if (response != null) {
                response.setContent(errorContent());
            }
        }
        return operation;
    }

    private void addIfAbsent(ApiResponses responses, String code, String description) {
        if (responses.containsKey(code)) {
            return;
        }
        responses.addApiResponse(code, new ApiResponse()
                .description(description)
                .content(errorContent()));
    }

    private Content errorContent() {
        return new Content().addMediaType("application/json",
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse")));
    }
}
