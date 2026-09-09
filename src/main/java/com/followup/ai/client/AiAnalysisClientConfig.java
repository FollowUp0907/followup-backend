package com.followup.ai.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * 설정값에 따라 Gemini 클라이언트 또는 Fake 클라이언트를 하나만 등록한다.
 * 두 구현체의 Bean 충돌을 방지하기 위해 이 설정 클래스에서 선택적으로 생성한다.
 */
@Configuration
public class AiAnalysisClientConfig {

    @Bean
    @ConditionalOnProperty(name = "GEMINI_API_KEY")
    public AiAnalysisClient geminiAiAnalysisClient(ObjectMapper objectMapper,
                                                    @Value("${gemini.api-key}") String apiKey,
                                                    @Value("${gemini.model}") String model) {
        return new GeminiAiAnalysisClient(RestClient.builder(), objectMapper, apiKey, model);
    }

    @Bean
    @ConditionalOnMissingBean(AiAnalysisClient.class)
    public AiAnalysisClient fakeAiAnalysisClient() {
        return new FakeAiAnalysisClient();
    }
}
