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
}
