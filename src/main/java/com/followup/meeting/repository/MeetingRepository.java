package com.followup.meeting.repository;

import com.followup.meeting.entity.Meeting;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {

    List<Meeting> findAllByProjectIdAndDeletedAtIsNullOrderByScheduledAtDesc(Long projectId);

    /** Dashboard 최근 회의 영역을 위해 전체 조회 후 자르지 않고 최신 3건만 DB에서 조회한다. */
    List<Meeting> findTop3ByProjectIdAndDeletedAtIsNullOrderByScheduledAtDesc(Long projectId);

    boolean existsByProjectIdAndDeletedAtIsNull(Long projectId);

    /** 프로젝트 cascade 삭제용으로 soft-delete 여부와 무관하게 회의 id 전체를 조회한다. */
    @Query("select m.id from Meeting m where m.project.id = :projectId")
    List<Long> findIdsByProjectId(@Param("projectId") Long projectId);
}
