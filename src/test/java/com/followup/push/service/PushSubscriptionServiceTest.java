package com.followup.push.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.followup.push.entity.PushSubscription;
import com.followup.push.repository.PushSubscriptionRepository;
import com.followup.user.entity.User;
import com.followup.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class PushSubscriptionServiceTest {

    @Autowired
    private PushSubscriptionService pushSubscriptionService;

    @Autowired
    private PushSubscriptionRepository pushSubscriptionRepository;

    @Autowired
    private UserRepository userRepository;

    private Long ownerId;
    private Long otherId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        ownerId = userRepository.save(User.builder()
                .email("owner-" + suffix + "@test.com").password("pw").name("Owner").build()).getId();
        otherId = userRepository.save(User.builder()
                .email("other-" + suffix + "@test.com").password("pw").name("Other").build()).getId();
    }

    private String uniqueToken() {
        return "token-" + UUID.randomUUID();
    }

    @Test
    void subscribe_newToken_createsSubscription() {
        String token = uniqueToken();

        pushSubscriptionService.subscribe(ownerId, token);

        PushSubscription saved = pushSubscriptionRepository.findByToken(token).orElseThrow();
        assertThat(saved.getUser().getId()).isEqualTo(ownerId);
    }

    @Test
    void subscribe_existingTokenSameUser_doesNotDuplicate() {
        String token = uniqueToken();
        pushSubscriptionService.subscribe(ownerId, token);

        pushSubscriptionService.subscribe(ownerId, token);

        assertThat(pushSubscriptionRepository.findAllByUserId(ownerId)).hasSize(1);
    }

    /** 같은 브라우저에서 다른 계정으로 로그인하면 같은 토큰이 재등록될 수 있다 — 소유자를 교체해야 한다. */
    @Test
    void subscribe_tokenOwnedByAnotherUser_reassignsOwner() {
        String token = uniqueToken();
        pushSubscriptionService.subscribe(ownerId, token);

        pushSubscriptionService.subscribe(otherId, token);

        PushSubscription reloaded = pushSubscriptionRepository.findByToken(token).orElseThrow();
        assertThat(reloaded.getUser().getId()).isEqualTo(otherId);
        assertThat(pushSubscriptionRepository.findAllByUserId(ownerId)).isEmpty();
    }

    @Test
    void unsubscribe_ownToken_deletesSubscription() {
        String token = uniqueToken();
        pushSubscriptionService.subscribe(ownerId, token);

        pushSubscriptionService.unsubscribe(ownerId, token);

        assertThat(pushSubscriptionRepository.findByToken(token)).isEmpty();
    }

    /** 다른 사람 소유 토큰에 대한 해제 시도는 아무 정보도 노출하지 않고 조용히 무시된다(예외도, 삭제도 없음). */
    @Test
    void unsubscribe_tokenOwnedByAnotherUser_silentlyIgnored() {
        String token = uniqueToken();
        pushSubscriptionService.subscribe(ownerId, token);

        pushSubscriptionService.unsubscribe(otherId, token);

        assertThat(pushSubscriptionRepository.findByToken(token)).isPresent();
    }

    @Test
    void unsubscribe_unknownToken_doesNotThrow() {
        pushSubscriptionService.unsubscribe(ownerId, "no-such-token");
    }
}
