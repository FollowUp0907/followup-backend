package com.followup.push.service;

import com.followup.push.entity.PushSubscription;
import com.followup.push.repository.PushSubscriptionRepository;
import com.followup.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PushSubscriptionService {

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final UserRepository userRepository;

    /**
     * 토큰이 이미 등록돼 있으면(다른 사람 소유였더라도, 같은 브라우저에서의 계정 전환 등) 소유자만
     * 교체하고, 없으면 새로 만든다.
     */
    @Transactional
    public void subscribe(Long userId, String token) {
        pushSubscriptionRepository.findByToken(token)
                .ifPresentOrElse(
                        subscription -> subscription.reassignTo(userRepository.getReferenceById(userId)),
                        () -> pushSubscriptionRepository.save(PushSubscription.builder()
                                .user(userRepository.getReferenceById(userId))
                                .token(token)
                                .build()));
    }

    /** 다른 사람 소유 토큰에 대한 해제 시도는 아무 정보도 노출하지 않고 조용히 무시한다. */
    @Transactional
    public void unsubscribe(Long userId, String token) {
        pushSubscriptionRepository.findByToken(token)
                .filter(subscription -> subscription.getUser().getId().equals(userId))
                .ifPresent(pushSubscriptionRepository::delete);
    }
}
