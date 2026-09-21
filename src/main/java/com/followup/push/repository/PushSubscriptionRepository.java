package com.followup.push.repository;

import com.followup.push.entity.PushSubscription;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByToken(String token);

    List<PushSubscription> findAllByUserId(Long userId);

    void deleteByToken(String token);

    /** 회원 탈퇴 시 그 사람의 구독을 전부 지우는 용도로 쓸 수 있다. */
    void deleteAllByUserId(Long userId);
}
