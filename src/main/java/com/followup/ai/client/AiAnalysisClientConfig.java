package com.followup.ai.client;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * 설정값에 따라 Gemini 클라이언트 또는 Fake 클라이언트를 하나만 등록한다.
 * 두 구현체의 Bean 충돌을 방지하기 위해 이 설정 클래스에서 선택적으로 생성한다.
 */
@Configuration
public class AiAnalysisClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    @Bean
    @ConditionalOnProperty(name = "GEMINI_API_KEY")
    public AiAnalysisClient geminiAiAnalysisClient(ObjectMapper objectMapper,
                                                    @Value("${gemini.api-key}") String apiKey,
                                                    @Value("${gemini.model}") String model) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        return new GeminiAiAnalysisClient(
                RestClient.builder().requestFactory(requestFactory), objectMapper, apiKey, model);
    }

    @Bean
    @ConditionalOnMissingBean(AiAnalysisClient.class)
    public AiAnalysisClient fakeAiAnalysisClient() {
        return new FakeAiAnalysisClient();
    }
}
