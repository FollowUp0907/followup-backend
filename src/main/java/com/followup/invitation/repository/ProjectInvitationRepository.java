package com.followup.invitation.repository;

import com.followup.invitation.entity.InvitationStatus;
import com.followup.invitation.entity.ProjectInvitation;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectInvitationRepository extends JpaRepository<ProjectInvitation, Long> {

    Optional<ProjectInvitation> findByTokenHash(String tokenHash);

    /**
     * accept 전용 — 같은 토큰에 대한 동시 수락 요청(상태 확인 + ProjectMember insert)을 직렬화하는
     * 비관적 쓰기 락 조회다(MeetingRepository.findByIdForUpdate와 같은 패턴). view/resend 등 락이
     * 필요 없는 다른 조회에는 쓰지 않는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ProjectInvitation i where i.tokenHash = :tokenHash")
    Optional<ProjectInvitation> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    List<ProjectInvitation> findAllByProjectId(Long projectId);

    /** PENDING 살아있는 초대 중복 체크용 — 만료 여부(isExpired())는 호출 측에서 판단한다. */
    Optional<ProjectInvitation> findByProjectIdAndEmailAndStatus(Long projectId, String email, InvitationStatus status);

    /** 정원 체크용 — 만료된 채 방치된 PENDING 초대는 정원을 차지하지 않도록 expiresAt까지 함께 따진다. */
    long countByProjectIdAndStatusAndExpiresAtAfter(Long projectId, InvitationStatus status, LocalDateTime now);

    /** 프로젝트 cascade 삭제용 — 다른 테이블이 project_invitations를 참조하지 않아 이른 시점에 지워도 된다. */
    void deleteAllByProjectId(Long projectId);
}
