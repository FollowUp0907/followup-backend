package com.followup.push.client;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MulticastMessage;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FirebaseFcmClient implements FcmClient {

    private final FirebaseApp firebaseApp;

    @Override
    public BatchResponse sendEachForMulticast(MulticastMessage message) throws FirebaseMessagingException {
        return FirebaseMessaging.getInstance(firebaseApp).sendEachForMulticast(message);
    }
}
