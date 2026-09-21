package com.followup.push.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.followup.push.client.FcmClient;
import com.google.firebase.FirebaseApp;
import org.junit.jupiter.api.Test;

/** 환경변수가 비어 있을 때 FirebaseApp/FcmClient 초기화를 건너뛰고 null을 반환하는지(예외 없이) 확인한다. */
class FirebaseConfigTest {

    private final FirebaseConfig firebaseConfig = new FirebaseConfig();

    @Test
    void firebaseApp_blankServiceAccount_returnsNullWithoutThrowing() throws Exception {
        FirebaseApp app = firebaseConfig.firebaseApp("");

        assertThat(app).isNull();
    }

    @Test
    void firebaseApp_nullServiceAccount_returnsNullWithoutThrowing() throws Exception {
        FirebaseApp app = firebaseConfig.firebaseApp(null);

        assertThat(app).isNull();
    }

    @Test
    void fcmClient_noFirebaseApp_returnsNull() {
        FcmClient client = firebaseConfig.fcmClient(java.util.Optional.empty());

        assertThat(client).isNull();
    }
}
