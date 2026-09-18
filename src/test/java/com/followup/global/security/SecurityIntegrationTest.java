package com.followup.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.followup.auth.dto.LoginReqDto;
import com.followup.auth.dto.SignupReqDto;
import com.followup.auth.dto.TokenResDto;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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

    @Autowired
    private JwtProvider jwtProvider;

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
