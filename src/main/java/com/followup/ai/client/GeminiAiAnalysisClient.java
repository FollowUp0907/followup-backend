package com.followup.ai.client;

import com.followup.ai.dto.AiDraftResultDto;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 실제 Gemini API를 호출하는 {@link AiAnalysisClient} 구현체다.
 * structured output(responseSchema)으로 AiDraftResultDto 형태만 반환하도록 제약한다.
 */
public class GeminiAiAnalysisClient implements AiAnalysisClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiAiAnalysisClient.class);

    private static final String PROMPT_VERSION = "gemini-v1";
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models";
    private static final int MAX_LOGGED_BODY_LENGTH = 1000;

    // 503(과부하)/429(요청 제한)만 일시적 오류로 보고 재시도한다. 401/400은 재시도해도 결과가
    // 똑같은 구조적 오류라 즉시 실패시킨다. 1s -> 2s -> 4s 지수 백오프, 총 대기시간 상한은 7초다.
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 1000L;
    private static final Set<Integer> RETRYABLE_STATUS_CODES = Set.of(429, 503);

    // Gemini의 Schema.type은 대문자 전용 enum이다(STRING/OBJECT/ARRAY). 소문자를 쓰면 400 오류가 난다.
    private static final String RESPONSE_SCHEMA_JSON = """
            {
              "type": "OBJECT",
              "properties": {
                "decisions": {
                  "type": "ARRAY",
                  "items": {
                    "type": "OBJECT",
                    "properties": {
                      "content": { "type": "STRING" }
                    },
                    "required": ["content"]
                  }
                },
                "actionItems": {
                  "type": "ARRAY",
                  "items": {
                    "type": "OBJECT",
                    "properties": {
                      "title": { "type": "STRING" },
                      "description": { "type": "STRING", "nullable": true },
                      "assigneeName": { "type": "STRING", "nullable": true },
                      "dueDate": { "type": "STRING", "nullable": true },
                      "priority": { "type": "STRING", "format": "enum", "enum": ["HIGH", "MEDIUM", "LOW"] },
                      "priorityReason": { "type": "STRING", "nullable": true }
                    },
                    "required": ["title", "priority"]
                  }
                }
              },
              "required": ["decisions", "actionItems"]
            }
            """;

    private static final DateTimeFormatter MEETING_DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String UNKNOWN_MEETING_DATE = "알 수 없음";

    private static final String PROMPT_TEMPLATE = """
            다음은 회의록입니다. 이 내용을 분석해서 결정된 사항(decisions)과 후속 업무(actionItems)를 JSON으로 추출하세요.

            회의 일시: %s

            규칙:
            - 회의록에 명시적인 근거가 없는 내용은 만들어내지 마세요.
            - decisions 또는 actionItems가 없으면 빈 배열로 반환하세요.
            - actionItems의 assigneeName은 회의록에 담당자가 명확히 언급된 경우에만 채우고, 절대 추측하지 마세요. 언급이 없으면 null로 두세요.
            - dueDate는 회의록에서 명확한 날짜를 알 수 있을 때만 "YYYY-MM-DD" 형식으로 채우고, 불명확하면 null로 두세요.
            - dueDate에 연도가 명시되지 않은 날짜(예: "9/10까지", "다음 주 금요일")가 있다면, 임의의 연도(특히 과거 연도)를 추정해서 붙이지 마세요. 반드시 위 "회의 일시"의 연도를 기준으로 해석하세요.
            - 회의 일시가 "%s"로 표시되어 있거나(즉 알 수 없거나) 기준 연도를 안전하게 판단할 수 없으면, 해당 dueDate는 null로 반환하세요.
            - priority는 반드시 HIGH, MEDIUM, LOW 중 하나여야 합니다.
            - 결정 사항과 후속 업무를 과도하게 만들어내지 말고, 실제로 회의에서 논의된 내용만 반영하세요.

            회의록:
            \"\"\"
            %s
            \"\"\"
            """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final JsonNode responseSchema;

    public GeminiAiAnalysisClient(RestClient.Builder restClientBuilder, ObjectMapper objectMapper,
                                   String apiKey, String model) {
        this.restClient = restClientBuilder.baseUrl(BASE_URL).build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.responseSchema = objectMapper.readTree(RESPONSE_SCHEMA_JSON);
    }

    @Override
    public AiDraftResultDto analyze(String meetingContent, LocalDateTime meetingScheduledAt) {
        String meetingDate = meetingScheduledAt != null
                ? meetingScheduledAt.toLocalDate().format(MEETING_DATE_FORMAT)
                : UNKNOWN_MEETING_DATE;
        String prompt = PROMPT_TEMPLATE.formatted(meetingDate, UNKNOWN_MEETING_DATE, meetingContent);

        GeminiRequest request = new GeminiRequest(
                List.of(new GeminiRequest.Content("user", List.of(new GeminiRequest.Part(prompt)))),
                new GeminiRequest.GenerationConfig("application/json", responseSchema));

        GeminiResponse response = callGeminiWithRetry(request);

        String text = extractText(response);
        try {
            return objectMapper.readValue(text, AiDraftResultDto.class);
        } catch (Exception e) {
            log.warn("Gemini response JSON parsing failed: model={}, rawText={}", model, truncate(text));
            throw new AiAnalysisClientException(
                    "Failed to parse Gemini response as JSON: model=%s, reason=%s"
                            .formatted(model, e.getMessage()), e);
        }
    }

    /**
     * 503/429는 지수 백오프로 최대 {@value #MAX_RETRIES}회 재시도하고, 그 외 4xx/5xx와 네트워크 오류는
     * 즉시 실패시킨다.
     */
    private GeminiResponse callGeminiWithRetry(GeminiRequest request) {
        long backoffMs = INITIAL_BACKOFF_MS;
        for (int attempt = 1; ; attempt++) {
            try {
                return restClient.post()
                        .uri("/{model}:generateContent", model)
                        .header("x-goog-api-key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(GeminiResponse.class);
            } catch (RestClientResponseException e) {
                // 4xx/5xx: 에러 응답 바디는 API 키를 포함하지 않으므로 그대로 노출해도 안전하다.
                int status = e.getStatusCode().value();
                String body = truncate(e.getResponseBodyAsString());
                if (!RETRYABLE_STATUS_CODES.contains(status) || attempt > MAX_RETRIES) {
                    log.warn("Gemini API HTTP failure: model={}, status={}, body={}", model, status, body);
                    throw new AiAnalysisClientException(
                            "Gemini API request failed: status=%d, message=%s".formatted(status, body), e);
                }
                log.warn("Gemini API returned status={}, retrying ({}/{}) after {}ms: model={}",
                        status, attempt, MAX_RETRIES, backoffMs, model);
                sleep(backoffMs);
                backoffMs *= 2;
            } catch (ResourceAccessException e) {
                // 네트워크 실패라 응답 자체가 없어 status/body를 남길 수 없다.
                log.warn("Gemini API network failure: model={}, reason={}", model, e.getMessage());
                throw new AiAnalysisClientException(
                        "Gemini API request failed: network error, reason=%s".formatted(e.getMessage()), e);
            } catch (RestClientException e) {
                log.warn("Gemini API call failed: model={}, reason={}", model, e.getMessage());
                throw new AiAnalysisClientException(
                        "Gemini API request failed: reason=%s".formatted(e.getMessage()), e);
            }
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiAnalysisClientException("Gemini API retry wait was interrupted", e);
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > MAX_LOGGED_BODY_LENGTH ? value.substring(0, MAX_LOGGED_BODY_LENGTH) + "...(truncated)" : value;
    }

    @Override
    public String getModelName() {
        return model;
    }

    @Override
    public String getPromptVersion() {
        return PROMPT_VERSION;
    }

    private String extractText(GeminiResponse response) {
        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            log.warn("Gemini response had no candidates: model={}", model);
            throw new AiAnalysisClientException("Gemini API returned no candidates: model=" + model);
        }
        GeminiResponse.Candidate candidate = response.candidates().get(0);
        if (candidate.content() == null
                || candidate.content().parts() == null
                || candidate.content().parts().isEmpty()) {
            log.warn("Gemini response candidate had no text: model={}", model);
            throw new AiAnalysisClientException("Gemini API returned a candidate with no text: model=" + model);
        }
        return candidate.content().parts().get(0).text();
    }

    record GeminiRequest(List<Content> contents, GenerationConfig generationConfig) {

        record Content(String role, List<Part> parts) {
        }

        record Part(String text) {
        }

        record GenerationConfig(String responseMimeType, JsonNode responseSchema) {
        }
    }

    record GeminiResponse(List<Candidate> candidates) {

        record Candidate(Content content) {
        }

        record Content(List<Part> parts) {
        }

        record Part(String text) {
        }
    }
}
