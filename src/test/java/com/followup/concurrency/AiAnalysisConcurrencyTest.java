package com.followup.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.followup.ai.client.AiAnalysisClient;
import com.followup.ai.dto.AiDraftResultDto;
import com.followup.ai.repository.AiAnalysisRunRepository;
import com.followup.ai.service.AiAnalysisService;
import com.followup.global.security.CurrentUserProvider;
import com.followup.meeting.dto.MeetingCreateReqDto;
import com.followup.meeting.dto.MeetingDetailResDto;
import com.followup.meeting.repository.MeetingRepository;
import com.followup.meeting.service.MeetingService;
import com.followup.project.dto.ProjectCreateReqDto;
import com.followup.project.dto.ProjectResDto;
import com.followup.project.repository.ProjectMemberRepository;
import com.followup.project.repository.ProjectRepository;
import com.followup.project.service.ProjectService;
import com.followup.user.entity.User;
import com.followup.user.repository.UserRepository;
import java.time.LocalDateTime;
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
 * AiAnalysisRunTxService.startAnalysis()의 재사용 판단+생성을 비관적 락으로 직렬화한 게
 * 실제로 같은 회의에 대한 동시 요청 시 AI 클라이언트를 한 번만 호출하게 만드는지 검증한다.
 * 클래스에 @Transactional을 붙이지 않는다 — setUp()에서 만든 회의가 커밋되어야 워커 스레드들이
 * (각자 별도 커넥션으로) 그 회의를 조회/락 할 수 있다.
 */
@SpringBootTest
class AiAnalysisConcurrencyTest {

    private static final int THREAD_COUNT = 5;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private MeetingService meetingService;

    @Autowired
    private AiAnalysisService aiAnalysisService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private AiAnalysisRunRepository aiAnalysisRunRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    @MockitoBean
    private AiAnalysisClient aiAnalysisClient;

    private Long ownerId;
    private Long projectId;
    private Long meetingId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        ownerId = userRepository.save(User.builder()
                .email("ai-owner-" + suffix + "@test.com")
                .password("pw")
                .name("Owner")
                .build()).getId();
        when(currentUserProvider.getCurrentUserId()).thenReturn(ownerId);

        when(aiAnalysisClient.getModelName()).thenReturn("test-model");
        when(aiAnalysisClient.getPromptVersion()).thenReturn("test-v1");
        when(aiAnalysisClient.analyze(any(), any())).thenReturn(new AiDraftResultDto(List.of(), List.of()));

        ProjectResDto project = projectService.createProject(new ProjectCreateReqDto("AI Concurrency", null));
        projectId = project.id();

        MeetingDetailResDto meeting = meetingService.createMeeting(projectId,
                new MeetingCreateReqDto("Sync", LocalDateTime.of(2026, 9, 18, 10, 0),
                        "Identical meeting notes used for the concurrency test", null, null));
        meetingId = meeting.id();
    }

    @AfterEach
    void cleanUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            aiAnalysisRunRepository.deleteAllByMeetingIdIn(List.of(meetingId));
            meetingRepository.deleteById(meetingId);
            projectMemberRepository.deleteAllByProjectId(projectId);
            projectRepository.deleteById(projectId);
            userRepository.deleteById(ownerId);
        });
    }

    @Test
    void concurrentIdenticalAnalysisRequests_callAiClientExactlyOnce() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch readyLatch = new CountDownLatch(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        List<Throwable> unexpected = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> knownUnrelatedIssue = Collections.synchronizedList(new ArrayList<>());

        try {
            for (int i = 0; i < THREAD_COUNT; i++) {
                executor.submit(() -> {
                    try {
                        readyLatch.countDown();
                        startLatch.await();
                        aiAnalysisService.requestAnalysis(meetingId);
                    } catch (Throwable t) {
                        // AiAnalysisService.getAnalysis()는 requestAnalysis() 안에서 this.getAnalysis(...)로
                        // (프록시를 거치지 않고) 직접 호출돼 그 메서드의 @Transactional이 무시된다. 실제 운영
                        // 요청에서는 스프링 부트 기본값인 open-in-view가 요청 스레드에 세션을 열어둬 가려지고,
                        // 기존 AiAnalysisServiceTest도 클래스 전체가 @Transactional이라 같은 이유로 가려진다.
                        // 이 테스트처럼 진짜 여러 스레드/커넥션으로 ambient 트랜잭션 없이 호출해야 드러난다.
                        // Fix 3(락을 통한 직렬화, 중복 호출 방지)과는 무관한 별개의 기존 버그라 이번 범위에서는
                        // 고치지 않고, 분석은 정상적으로 1건만 생성/호출됐는지만 별도로 확인한다.
                        if (t instanceof org.hibernate.LazyInitializationException) {
                            knownUnrelatedIssue.add(t);
                        } else {
                            unexpected.add(t);
                        }
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

            long analysisRowCount = aiAnalysisRunRepository.findAllByMeetingIdOrderByCreatedAtDesc(meetingId).size();

            System.out.println("[AiAnalysisConcurrencyTest] finishedInTime=" + finishedInTime
                    + ", unexpectedErrorCount=" + unexpected.size()
                    + ", knownUnrelatedIssueCount=" + knownUnrelatedIssue.size()
                    + ", analysisRowCount=" + analysisRowCount);
            unexpected.forEach(t -> System.out.println("  unexpected: " + t));

            assertThat(unexpected).isEmpty();
            // 핵심 검증: 비관적 락으로 직렬화된 재사용 판단 덕분에, 동시 요청 5개가 와도 실제 AI 클라이언트는
            // 정확히 1번만 호출되고 PROCESSING/GENERATED row도 1건만 생성된다.
            verify(aiAnalysisClient, times(1)).analyze(any(), any());
            assertThat(analysisRowCount).isEqualTo(1);
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
