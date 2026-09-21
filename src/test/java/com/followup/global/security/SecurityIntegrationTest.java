package com.followup.global.security;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.followup.auth.dto.GoogleLoginReqDto;
import com.followup.auth.dto.LoginReqDto;
import com.followup.auth.dto.SignupReqDto;
import com.followup.auth.dto.TokenResDto;
import com.followup.auth.google.GoogleIdTokenVerifierComponent;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoSpyBean
    private GoogleIdTokenVerifierComponent googleIdTokenVerifierComponent;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private String uniqueEmail() {
        return "sec-" + UUID.randomUUID() + "@test.com";
    }

    @Test
    void signupAndLogin_arePermitAll() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupReqDto(email, "password123", "Tester"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginReqDto(email, "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void health_isPermitAll() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }

    @Test
    void protectedApi_withoutToken_returns401WithErrorResponseFormat() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void existingProtectedPath_withoutToken_returns401NotFallenBackTo404() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    /** 인증 체크가 MVC 핸들러 매핑보다 먼저 실행되므로, 존재하지 않는 경로는 401이 아니라 404여야 한다. */
    @Test
    void unmappedPath_withoutToken_returns404WithRouteNotFound() throws Exception {
        mockMvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROUTE_NOT_FOUND"));
    }

    /** 유효한 토큰이 있어도 매핑 자체가 없으면 여전히 404다 — 존재하지 않는 API라는 사실은 토큰과 무관하다. */
    @Test
    void unmappedPath_withValidToken_stillReturns404() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupReqDto(email, "password123", "Tester"))))
                .andExpect(status().isCreated());
        String loginResponseBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginReqDto(email, "password123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        TokenResDto token = objectMapper.readValue(loginResponseBody, TokenResDto.class);

        mockMvc.perform(get("/api/does-not-exist").header("Authorization", "Bearer " + token.accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROUTE_NOT_FOUND"));
    }

    /**
     * 매핑이 실제로 존재하는 경로에 대한 토큰 오류(무효/만료)는, 매핑이 없는 경로와 절대 혼동돼선 안 된다 —
     * 여전히 401 + 기존 토큰 에러 코드 그대로여야 한다.
     */
    @Test
    void existingPath_withInvalidToken_returns401NotConfusedWith404() throws Exception {
        mockMvc.perform(get("/api/projects").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void protectedApi_withMalformedToken_returns401WithInvalidToken() throws Exception {
        mockMvc.perform(get("/api/projects").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void protectedApi_withExpiredToken_returns401WithExpiredToken() throws Exception {
        // Thread.sleep 없이 검증하기 위해 이미 지난 만료시간(음수)으로 발급한다.
        JwtProvider expiredProvider = new JwtProvider(jwtSecret, -1_000L);
        String expiredToken = expiredProvider.generateAccessToken(1L);

        mockMvc.perform(get("/api/projects").header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("EXPIRED_TOKEN"));
    }

    /** EventSource는 커스텀 헤더를 못 실으므로 이 경로만 쿼리 파라미터 토큰도 허용한다 — 둘 다 없으면 여전히 401이다. */
    @Test
    void sseStream_withoutTokenAtAll_returns401() throws Exception {
        mockMvc.perform(get("/api/notifications/stream"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void sseStream_withValidQueryToken_returnsEventStream() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupReqDto(email, "password123", "Tester"))))
                .andExpect(status().isCreated());
        String loginResponseBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginReqDto(email, "password123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        TokenResDto token = objectMapper.readValue(loginResponseBody, TokenResDto.class);

        mockMvc.perform(get("/api/notifications/stream").param("token", token.accessToken()))
                .andExpect(request().asyncStarted())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.TEXT_EVENT_STREAM_VALUE));
    }

    /**
     * permitAll이 안 걸려있으면 요청이 Spring Security 인증 단계에서 막혀 컨트롤러/GoogleIdTokenVerifierComponent까지
     * 아예 도달하지 못한다. permitAll이 걸려 있으면 요청이 통과해 검증기까지 호출되고, 거기서 실패한 토큰이
     * 애플리케이션 레벨 에러(INVALID_GOOGLE_TOKEN)로 응답됨을 확인한다 — 둘 다 401이지만 원인이 다르다.
     */
    @Test
    void googleLogin_isPermitAll_reachesControllerAndFailsAtApplicationLevel() throws Exception {
        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoogleLoginReqDto("not-a-real-google-id-token"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_GOOGLE_TOKEN"));

        verify(googleIdTokenVerifierComponent).verify("not-a-real-google-id-token");
    }

    @Test
    void loginThenAccessProtectedApi_succeeds() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupReqDto(email, "password123", "Tester"))))
                .andExpect(status().isCreated());

        String loginResponseBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginReqDto(email, "password123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        TokenResDto token = objectMapper.readValue(loginResponseBody, TokenResDto.class);

        mockMvc.perform(get("/api/projects").header("Authorization", "Bearer " + token.accessToken()))
                .andExpect(status().isOk());
    }
}
