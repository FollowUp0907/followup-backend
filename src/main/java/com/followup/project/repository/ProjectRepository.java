package com.followup.project.repository;

import com.followup.project.entity.Project;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    /**
     * 같은 프로젝트에 대한 동시 멤버 추가 요청(존재 확인 + insert)을 직렬화하기 위한 비관적 쓰기 락
     * 조회다. 호출한 트랜잭션이 끝날 때까지 같은 project에 대한 다른 잠금 요청은 대기한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Project p where p.id = :projectId")
    Optional<Project> findByIdForUpdate(@Param("projectId") Long projectId);
}
