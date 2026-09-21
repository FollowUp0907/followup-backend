package com.followup.project.repository;

import com.followup.project.entity.ProjectMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {

    boolean existsByProjectIdAndUserId(Long projectId, Long userId);

    Optional<ProjectMember> findByProjectIdAndUserId(Long projectId, Long userId);

    List<ProjectMember> findAllByProjectId(Long projectId);

    long countByProjectId(Long projectId);

    List<ProjectMember> findAllByUserId(Long userId);

    void deleteAllByProjectId(Long projectId);

    /** 회원 탈퇴 시 남은(=OWNER가 아니었던) 멤버십 정리용 — OWNER였던 프로젝트는 이미 cascade 삭제로 함께 지워진다. */
    void deleteAllByUserId(Long userId);
}
