package com.followup.concurrency;

import com.followup.project.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 테스트 전용 보조 빈이다(src/test에만 존재, 프로덕션 코드 아님).
 * DB 커넥션 풀 소진 시나리오를 재현하기 위해, 트랜잭션 안에서 실제로 커넥션을 획득한 뒤
 * 일정 시간 동안 반환하지 않고 붙들고 있는다.
 */
@Service
public class SlowConnectionHolder {

    private final ProjectRepository projectRepository;

    public SlowConnectionHolder(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Transactional
    public void holdConnection(Long projectId, long millis) {
        projectRepository.findById(projectId);
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
