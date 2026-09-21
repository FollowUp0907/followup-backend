package com.followup.auth.google;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 구글 ID 토큰을 검증한다. 서명 불일치/만료/audience 불일치 등 검증 실패 사유를 개별적으로 구분하지 않고
 * {@link Optional#empty()}로 통일해 반환한다 — 호출 측(AuthService) 입장에서는 "유효한 토큰인지 아닌지"만
 * 중요하고, 실패 사유는 INVALID_GOOGLE_TOKEN 하나로 처리하면 되기 때문이다.
 */
@Component
public class GoogleIdTokenVerifierComponent {

    private final GoogleIdTokenVerifier verifier;

    public GoogleIdTokenVerifierComponent(@Value("${google.client-id}") String clientId) {
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(clientId))
                .build();
    }

    public Optional<GoogleUserInfo> verify(String idToken) {
        try {
            GoogleIdToken googleIdToken = verifier.verify(idToken);
            if (googleIdToken == null) {
                return Optional.empty();
            }
            GoogleIdToken.Payload payload = googleIdToken.getPayload();
            boolean emailVerified = Boolean.TRUE.equals(payload.getEmailVerified());
            String name = (String) payload.get("name");
            return Optional.of(new GoogleUserInfo(payload.getSubject(), payload.getEmail(), emailVerified, name));
        } catch (GeneralSecurityException | IOException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
