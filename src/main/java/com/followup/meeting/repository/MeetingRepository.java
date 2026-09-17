package com.followup.meeting.repository;

import com.followup.meeting.entity.Meeting;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    /**
     * 같은 회의에 대한 동시 AI 분석 요청(재사용 판단 + PROCESSING row 생성)을 직렬화하기 위한
     * 비관적 쓰기 락 조회다. 호출한 트랜잭션이 끝날 때까지 같은 meeting에 대한 다른 잠금 요청은 대기한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Meeting m where m.id = :meetingId")
    Optional<Meeting> findByIdForUpdate(@Param("meetingId") Long meetingId);
}
