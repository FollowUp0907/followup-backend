package com.followup.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.when;

import com.followup.global.exception.BusinessException;
import com.followup.global.exception.ErrorCode;
import com.followup.global.security.CurrentUserProvider;
import com.followup.project.dto.ProjectCreateReqDto;
import com.followup.project.dto.ProjectMemberCreateReqDto;
import com.followup.project.dto.ProjectMemberResDto;
import com.followup.project.dto.ProjectResDto;
import com.followup.project.repository.ProjectMemberRepository;
import com.followup.project.repository.ProjectRepository;
import com.followup.project.service.ProjectMemberService;
import com.followup.project.service.ProjectService;
import com.followup.user.entity.User;
import com.followup.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ProjectMemberService.addMember()의 check-then-act 레이스가 실제 동시 부하에서 500이 아니라
 * 정상 응답(성공 1건 + 409 나머지)으로만 끝나는지 검증한다. 클래스에 @Transactional을 붙이지 않는다 —
 * setUp()에서 만든 데이터가 커밋되어야 워커 스레드들이(각자 별도 커넥션으로) 그 데이터를 볼 수 있다.
 */
@SpringBootTest
class ProjectMemberConcurrencyTest {

    private static final int THREAD_COUNT = 10;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectMemberService projectMemberService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    private Long ownerId;
    private Long targetUserId;
    private String targetEmail;
    private Long projectId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        ownerId = userRepository.save(User.builder()
                .email("owner-" + suffix + "@test.com")
                .password("pw")
                .name("Owner")
                .build()).getId();
        targetEmail = "target-" + suffix + "@test.com";
        targetUserId = userRepository.save(User.builder()
                .email(targetEmail)
                .password("pw")
                .name("Target")
                .build()).getId();

        when(currentUserProvider.getCurrentUserId()).thenReturn(ownerId);
        ProjectResDto project = projectService.createProject(new ProjectCreateReqDto("Member Concurrency", null));
        projectId = project.id();
    }

    @AfterEach
    void cleanUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            projectMemberRepository.deleteAllByProjectId(projectId);
            projectRepository.deleteById(projectId);
            userRepository.deleteById(targetUserId);
            userRepository.deleteById(ownerId);
        });
    }

    @Test
    void tenConcurrentAddMemberRequests_resolveToOneSuccessAndRestConflict() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch readyLatch = new CountDownLatch(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        List<ProjectMemberResDto> successes = Collections.synchronizedList(new ArrayList<>());
        List<BusinessException> conflicts = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> unexpected = Collections.synchronizedList(new ArrayList<>());

        try {
            for (int i = 0; i < THREAD_COUNT; i++) {
                executor.submit(() -> {
                    try {
                        readyLatch.countDown();
                        startLatch.await();
                        ProjectMemberResDto result = projectMemberService.addMember(
                                projectId, new ProjectMemberCreateReqDto(targetEmail));
                        successes.add(result);
                    } catch (BusinessException e) {
                        if (e.getErrorCode() == ErrorCode.PROJECT_MEMBER_ALREADY_EXISTS) {
                            conflicts.add(e);
                        } else {
                            unexpected.add(e);
                        }
                    } catch (Throwable t) {
                        unexpected.add(t);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            readyLatch.await(10, TimeUnit.SECONDS);
            startLatch.countDown();
            boolean finishedInTime = doneLatch.await(30, TimeUnit.SECONDS);
            if (!finishedInTime) {
                fail("워커 스레드가 제한 시간 내에 끝나지 않음");
            }

            long remainingRows = projectMemberRepository.findAllByProjectId(projectId).stream()
                    .filter(m -> m.getUser().getId().equals(targetUserId))
                    .count();

            System.out.println("[ProjectMemberConcurrencyTest] finishedInTime=" + finishedInTime
                    + ", successCount=" + successes.size() + ", conflictCount=" + conflicts.size()
                    + ", unexpectedCount=" + unexpected.size() + ", remainingRows=" + remainingRows);
            unexpected.forEach(t -> System.out.println("  unexpected: " + t));

            assertThat(unexpected).isEmpty();
            assertThat(successes).hasSize(1);
            assertThat(conflicts).hasSize(THREAD_COUNT - 1);
            assertThat(remainingRows).isEqualTo(1);
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
