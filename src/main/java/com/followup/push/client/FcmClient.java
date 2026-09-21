package com.followup.push.client;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MulticastMessage;

/** FCM 전송을 감싸는 얇은 경계 — 테스트에서 실제 Firebase 호출 없이 mock으로 대체하기 위한 인터페이스다. */
public interface FcmClient {

    BatchResponse sendEachForMulticast(MulticastMessage message) throws FirebaseMessagingException;
}
