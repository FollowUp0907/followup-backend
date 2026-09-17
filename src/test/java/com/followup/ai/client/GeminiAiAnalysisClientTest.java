package com.followup.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.followup.actionitem.entity.Priority;
import com.followup.ai.dto.AiDraftResultDto;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Exercises GeminiAiAnalysisClient's HTTP + JSON handling with the HTTP layer mocked via
 * MockRestServiceServer -- no real network call is made.
 */
@SpringBootTest
class GeminiAiAnalysisClientTest {

    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/test-model:generateContent";
    private static final LocalDateTime MEETING_DATE = LocalDateTime.of(2026, 9, 8, 14, 0);

    @Autowired
    private ObjectMapper objectMapper;

    private MockRestServiceServer mockServer;
    private GeminiAiAnalysisClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new GeminiAiAnalysisClient(builder, objectMapper, "test-api-key", "test-model");
    }

    private String geminiEnvelope(String innerText) {
        GeminiAiAnalysisClient.GeminiResponse envelope = new GeminiAiAnalysisClient.GeminiResponse(
                List.of(new GeminiAiAnalysisClient.GeminiResponse.Candidate(
                        new GeminiAiAnalysisClient.GeminiResponse.Content(
                                List.of(new GeminiAiAnalysisClient.GeminiResponse.Part(innerText))))));
        return objectMapper.writeValueAsString(envelope);
    }

    private String emptyGeminiEnvelope() {
        return objectMapper.writeValueAsString(new GeminiAiAnalysisClient.GeminiResponse(List.of()));
    }

    @Test
    void analyze_parsesValidJsonResponse() {
        String draftJson = """
                {"decisions":[{"content":"Proceed with plan A"}],\
                "actionItems":[{"title":"Write docs","description":null,"assigneeName":null,\
                "dueDate":null,"priority":"HIGH","priorityReason":null}]}""";

        mockServer.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-api-key"))
                .andRespond(withSuccess(geminiEnvelope(draftJson), MediaType.APPLICATION_JSON));

        AiDraftResultDto result = client.analyze("Some meeting notes", MEETING_DATE);

        assertThat(result.decisions()).hasSize(1);
        assertThat(result.decisions().get(0).content()).isEqualTo("Proceed with plan A");
        assertThat(result.actionItems()).hasSize(1);
        assertThat(result.actionItems().get(0).title()).isEqualTo("Write docs");
        assertThat(result.actionItems().get(0).priority()).isEqualTo(Priority.HIGH);
        mockServer.verify();
    }

    @Test
    void analyze_emptyDraftArraysAllowed() {
        String draftJson = "{\"decisions\":[],\"actionItems\":[]}";
        mockServer.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess(geminiEnvelope(draftJson), MediaType.APPLICATION_JSON));

        AiDraftResultDto result = client.analyze("No decisions here", MEETING_DATE);

        assertThat(result.decisions()).isEmpty();
        assertThat(result.actionItems()).isEmpty();
    }

    @Test
    void analyze_malformedJsonInTextThrows() {
        mockServer.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess(geminiEnvelope("this is not valid json"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.analyze("Some content", MEETING_DATE))
                .isInstanceOf(AiAnalysisClientException.class);
    }

    @Test
    void analyze_httpErrorThrows() {
        mockServer.expect(requestTo(ENDPOINT)).andRespond(withServerError());

        assertThatThrownBy(() -> client.analyze("Some content", MEETING_DATE))
                .isInstanceOf(AiAnalysisClientException.class);
    }

    @Test
    void analyze_httpErrorMessageIncludesStatusAndGeminiErrorBody() {
        String errorBody = "{\"error\":{\"code\":400,\"message\":\"Invalid JSON payload\",\"status\":\"INVALID_ARGUMENT\"}}";
        mockServer.expect(requestTo(ENDPOINT))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body(errorBody));

        assertThatThrownBy(() -> client.analyze("Some content", MEETING_DATE))
                .isInstanceOf(AiAnalysisClientException.class)
                .hasMessageContaining("status=400")
                .hasMessageContaining("INVALID_ARGUMENT");
    }

    @Test
    void analyze_errorMessageNeverContainsApiKey() {
        String errorBody = "{\"error\":{\"code\":400,\"message\":\"Invalid JSON payload\"}}";
        mockServer.expect(requestTo(ENDPOINT))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body(errorBody));

        assertThatThrownBy(() -> client.analyze("Some content", MEETING_DATE))
                .isInstanceOf(AiAnalysisClientException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("test-api-key"));
    }

    @Test
    void analyze_noCandidatesThrows() {
        mockServer.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess(emptyGeminiEnvelope(), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.analyze("Some content", MEETING_DATE))
                .isInstanceOf(AiAnalysisClientException.class);
    }

    @Test
    void analyze_sendsUppercaseSchemaTypesMatchingGeminiTypeEnum() {
        mockServer.expect(requestTo(ENDPOINT))
                .andExpect(request -> {
                    String body = ((MockClientHttpRequest) request).getBodyAsString();
                    JsonNode schema = objectMapper.readTree(body).at("/generationConfig/responseSchema");
                    assertThat(schema.at("/type").asString()).isEqualTo("OBJECT");
                    assertThat(schema.at("/properties/decisions/type").asString()).isEqualTo("ARRAY");
                    assertThat(schema.at("/properties/decisions/items/type").asString()).isEqualTo("OBJECT");
                    assertThat(schema.at("/properties/decisions/items/properties/content/type").asString())
                            .isEqualTo("STRING");
                    assertThat(schema.at("/properties/actionItems/items/properties/priority/type").asString())
                            .isEqualTo("STRING");
                    assertThat(schema.at("/properties/actionItems/items/properties/priority/format").asString())
                            .isEqualTo("enum");
                    assertThat(schema.at("/properties/actionItems/items/properties/description/nullable").asBoolean())
                            .isTrue();
                })
                .andRespond(withSuccess(geminiEnvelope("{\"decisions\":[],\"actionItems\":[]}"), MediaType.APPLICATION_JSON));

        client.analyze("Some content", MEETING_DATE);

        mockServer.verify();
    }

    @Test
    void analyze_promptIncludesMeetingDateAsYearReferenceWhenScheduledAtProvided() {
        // scheduledAt = 2026-09-08, meeting notes mention a year-less date ("9/10까지"). We can't
        // unit test Gemini's own reasoning, but we can verify the prompt gives it the correct
        // reference year (2026) instead of leaving it to guess -- that's what was missing before.
        mockServer.expect(requestTo(ENDPOINT))
                .andExpect(request -> {
                    String prompt = requestPromptText(request);
                    assertThat(prompt).contains("회의 일시: 2026-09-08");
                    assertThat(prompt).contains("9/10까지");
                })
                .andRespond(withSuccess(geminiEnvelope("{\"decisions\":[],\"actionItems\":[]}"), MediaType.APPLICATION_JSON));

        client.analyze("다음 회의까지 문서 작업을 9/10까지 마무리하기로 함", MEETING_DATE);

        mockServer.verify();
    }

    @Test
    void analyze_retriesOn503AndSucceedsOnThirdAttempt() {
        String draftJson = "{\"decisions\":[],\"actionItems\":[]}";
        mockServer.expect(requestTo(ENDPOINT))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("overloaded"));
        mockServer.expect(requestTo(ENDPOINT))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("overloaded"));
        mockServer.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess(geminiEnvelope(draftJson), MediaType.APPLICATION_JSON));

        AiDraftResultDto result = client.analyze("Some content", MEETING_DATE);

        assertThat(result.decisions()).isEmpty();
        mockServer.verify();
    }

    @Test
    void analyze_doesNotRetryOn401() {
        mockServer.expect(requestTo(ENDPOINT))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("invalid api key"));

        assertThatThrownBy(() -> client.analyze("Some content", MEETING_DATE))
                .isInstanceOf(AiAnalysisClientException.class)
                .hasMessageContaining("status=401");

        mockServer.verify();
    }

    @Test
    void analyze_throwsAfterExhaustingRetriesOn503() {
        mockServer.expect(requestTo(ENDPOINT))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("overloaded"));
        mockServer.expect(requestTo(ENDPOINT))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("overloaded"));
        mockServer.expect(requestTo(ENDPOINT))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("overloaded"));
        mockServer.expect(requestTo(ENDPOINT))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("overloaded"));

        assertThatThrownBy(() -> client.analyze("Some content", MEETING_DATE))
                .isInstanceOf(AiAnalysisClientException.class)
                .hasMessageContaining("status=503");

        mockServer.verify();
    }

    @Test
    void analyze_promptMarksMeetingDateUnknownWhenScheduledAtNull() {
        mockServer.expect(requestTo(ENDPOINT))
                .andExpect(request -> {
                    String prompt = requestPromptText(request);
                    assertThat(prompt).contains("회의 일시: 알 수 없음");
                })
                .andRespond(withSuccess(geminiEnvelope("{\"decisions\":[],\"actionItems\":[]}"), MediaType.APPLICATION_JSON));

        client.analyze("9/10까지 마무리하기로 함", null);

        mockServer.verify();
    }

    @Test
    void analyze_resolvesDueDateUsingMeetingScheduledAtYear() {
        // Simulates Gemini correctly following the prompt's reference-year rule: scheduledAt is
        // 2026-09-08, so a year-less "9/10까지" mention should resolve to 2026-09-10, not a past
        // year like 2024.
        String draftJson = """
                {"decisions":[],"actionItems":[{"title":"문서 작업","description":null,\
                "assigneeName":null,"dueDate":"2026-09-10","priority":"MEDIUM","priorityReason":null}]}""";
        mockServer.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess(geminiEnvelope(draftJson), MediaType.APPLICATION_JSON));

        AiDraftResultDto result = client.analyze("문서 작업을 9/10까지 마무리하기로 함", MEETING_DATE);

        assertThat(result.actionItems().get(0).dueDate()).isEqualTo(LocalDate.of(2026, 9, 10));
    }

    @Test
    void analyze_allowsNullDueDateWhenReferenceYearCannotBeDetermined() {
        String draftJson = """
                {"decisions":[],"actionItems":[{"title":"문서 작업","description":null,\
                "assigneeName":null,"dueDate":null,"priority":"MEDIUM","priorityReason":null}]}""";
        mockServer.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess(geminiEnvelope(draftJson), MediaType.APPLICATION_JSON));

        AiDraftResultDto result = client.analyze("문서 작업을 9/10까지 마무리하기로 함", null);

        assertThat(result.actionItems().get(0).dueDate()).isNull();
    }

    private String requestPromptText(ClientHttpRequest request) {
        String body = ((MockClientHttpRequest) request).getBodyAsString();
        JsonNode root = objectMapper.readTree(body);
        return root.at("/contents/0/parts/0/text").asString();
    }
}
