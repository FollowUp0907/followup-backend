package com.followup.global.config;

import com.followup.global.exception.ErrorResponse;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI에서 로그인으로 받은 JWT를 Authorize에 등록하면
 * 이후 모든 요청에 Authorization: Bearer {token}이 자동으로 붙도록 bearerAuth 보안 스키마를 전역 등록한다.
 */
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FollowUp API")
                        .description("회의 후속 업무 관리 서비스 FollowUp Backend API")
                        .version("v1"))
                .components(new Components().addSchemas("ErrorResponse", errorResponseSchema()))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    private io.swagger.v3.oas.models.media.Schema<?> errorResponseSchema() {
        ResolvedSchema resolved = ModelConverters.getInstance()
                .resolveAsResolvedSchema(new AnnotatedType(ErrorResponse.class));
        return resolved.schema;
    }
}
