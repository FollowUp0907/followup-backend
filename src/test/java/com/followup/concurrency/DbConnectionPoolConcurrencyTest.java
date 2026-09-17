package com.followup.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.followup.global.security.CurrentUserProvider;
import com.followup.project.dto.ProjectCreateReqDto;
import com.followup.project.dto.ProjectResDto;
import com.followup.project.repository.ProjectMemberRepository;
import com.followup.project.repository.ProjectRepository;
import com.followup.project.service.ProjectService;
import com.followup.user.entity.User;
import com.followup.user.repository.UserRepository;
import com.zaxxer.hikari.HikariDataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * HikariCP maximum-pool-size보다 많은 요청이 동시에 몰렸을 때 실제로 어떻게 되는지 재현한다.
 * 이 클래스도 @Transactional을 붙이지 않는다 -- setUp()에서 만든 프로젝트가 실제 커밋되어야
 * 워커 스레드들이(각자 별도 커넥션으로) 그 프로젝트를 조회할 수 있다.
 */
@SpringBootTest
class DbConnectionPoolConcurrencyTest {

    private static final long HOLD_MILLIS = 500L;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private SlowConnectionHolder slowConnectionHolder;

    @Autowired
    private ProjectService projectService;

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

    private Long userId;
    private Long projectId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        userId = userRepository.save(User.builder()
                .email("pool-" + suffix + "@test.com")
                .password("pw")
                .name("Pool Tester")
                .build()).getId();
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);

        ProjectResDto project = projectService.createProject(new ProjectCreateReqDto("Pool Project", null));
        projectId = project.id();
    }

    @AfterEach
    void cleanUp() {
        // 커스텀 derived delete 메서드(deleteAllByProjectId)는 트랜잭션이 없으면
        // TransactionRequiredException이 나서, 정리 작업만 별도 트랜잭션으로 감싼다.
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            projectMemberRepository.deleteAllByProjectId(projectId);
            projectRepository.deleteById(projectId);
            userRepository.deleteById(userId);
        });
    }

    @Test
    void requestsBeyondPoolSize_waitInsteadOfErroring() throws InterruptedException {
        int maxPoolSize = ((HikariDataSource) dataSource).getMaximumPoolSize();
        int requestCount = maxPoolSize + 10;

        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        List<Callable<Long>> tasks = new ArrayList<>();
        for (int i = 0; i < requestCount; i++) {
            tasks.add(() -> {
                long taskStart = System.currentTimeMillis();
                slowConnectionHolder.holdConnection(projectId, HOLD_MILLIS);
                return System.currentTimeMillis() - taskStart;
            });
        }

        long wallClockStart = System.currentTimeMillis();
        List<Future<Long>> futures = executor.invokeAll(tasks, 60, TimeUnit.SECONDS);
        long wallClockMs = System.currentTimeMillis() - wallClockStart;
        executor.shutdown();

        int succeeded = 0;
        int failed = 0;
        long maxIndividualLatencyMs = 0;
        List<String> failureReasons = new ArrayList<>();
        for (Future<Long> future : futures) {
            try {
                long latency = future.get();
                succeeded++;
                maxIndividualLatencyMs = Math.max(maxIndividualLatencyMs, latency);
            } catch (ExecutionException e) {
                failed++;
                failureReasons.add(e.getCause().getClass().getSimpleName() + ": " + e.getCause().getMessage());
            }
        }

        System.out.printf(
                "[DbConnectionPoolConcurrencyTest] maxPoolSize=%d, requestCount=%d, succeeded=%d, failed=%d, "
                        + "wallClockMs=%d, maxIndividualLatencyMs=%d%n",
                maxPoolSize, requestCount, succeeded, failed, wallClockMs, maxIndividualLatencyMs);
        if (!failureReasons.isEmpty()) {
            System.out.println("[DbConnectionPoolConcurrencyTest] failures: " + failureReasons);
        }

        // 초과 요청은 커넥션이 반환될 때까지 기다렸다가 처리된다 -- 개별 지연시간이 hold 시간보다
        // 뚜렷하게 길게 나오는 것이 그 증거다. 에러 없이 전부 성공하는지가 이 테스트의 핵심이다.
        assertThat(failed).isZero();
        assertThat(succeeded).isEqualTo(requestCount);
        assertThat(maxIndividualLatencyMs).isGreaterThan(HOLD_MILLIS);
    }
}
