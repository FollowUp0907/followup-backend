package com.followup.push.config;

import com.followup.push.client.FcmClient;
import com.followup.push.client.FirebaseFcmClient;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * FIREBASE_SERVICE_ACCOUNT_BASE64가 비어 있으면(로컬/테스트 환경 등) FirebaseApp을 초기화하지 않고
 * 경고 로그만 남긴다 — 이 경우 fcmClient 빈도 만들어지지 않으므로, PushNotificationSender는 FcmClient를
 * Optional로 주입받아 비어 있으면 푸시 발송 자체를 건너뛴다. SSE/폴링에는 영향이 없다.
 */
@Slf4j
@Configuration
public class FirebaseConfig {

    @Bean
    public FirebaseApp firebaseApp(@Value("${firebase.service-account-base64:}") String serviceAccountBase64)
            throws IOException {
        if (serviceAccountBase64 == null || serviceAccountBase64.isBlank()) {
            log.warn("FIREBASE_SERVICE_ACCOUNT_BASE64가 설정되지 않아 Firebase Cloud Messaging 초기화를 건너뜁니다.");
            return null;
        }

        byte[] decoded = Base64.getDecoder().decode(serviceAccountBase64);
        try (ByteArrayInputStream credentialStream = new ByteArrayInputStream(decoded)) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentialStream))
                    .build();
            return FirebaseApp.initializeApp(options);
        }
    }

    @Bean
    public FcmClient fcmClient(Optional<FirebaseApp> firebaseApp) {
        return firebaseApp.<FcmClient>map(FirebaseFcmClient::new).orElse(null);
    }
}
