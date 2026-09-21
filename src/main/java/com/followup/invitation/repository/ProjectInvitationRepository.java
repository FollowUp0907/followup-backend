package com.followup.invitation.repository;

import com.followup.invitation.entity.InvitationStatus;
import com.followup.invitation.entity.ProjectInvitation;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectInvitationRepository extends JpaRepository<ProjectInvitation, Long> {

    Optional<ProjectInvitation> findByTokenHash(String tokenHash);

    List<ProjectInvitation> findAllByProjectId(Long projectId);

    /** PENDING 살아있는 초대 중복 체크용 — 만료 여부(isExpired())는 호출 측에서 판단한다. */
    Optional<ProjectInvitation> findByProjectIdAndEmailAndStatus(Long projectId, String email, InvitationStatus status);

    /** 정원 체크용 — 만료된 채 방치된 PENDING 초대는 정원을 차지하지 않도록 expiresAt까지 함께 따진다. */
    long countByProjectIdAndStatusAndExpiresAtAfter(Long projectId, InvitationStatus status, LocalDateTime now);
}
