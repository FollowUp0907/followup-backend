package com.followup.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.when;

import com.followup.global.exception.BusinessException;
import com.followup.global.exception.ErrorCode;
import com.followup.global.security.CurrentUserProvider;
import com.followup.invitation.dto.InvitationResDto;
import com.followup.invitation.entity.InvitationStatus;
import com.followup.invitation.entity.ProjectInvitation;
import com.followup.invitation.repository.ProjectInvitationRepository;
import com.followup.invitation.service.InvitationService;
import com.followup.invitation.util.InvitationTokenGenerator;
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
 * InvitationService.acceptInvitation()의 토큰 조회에 락이 없으면(수정 전) 동시 요청이 둘 다
 * status==PENDING과 "아직 멤버 아님"을 읽고 둘 다 ProjectMember를 insert하려다 유니크 제약 위반으로
 * 처리되지 않은 예외(500)를 낼 수 있었다. findByTokenHashForUpdate 락으로 직렬화한 뒤에는 정확히
 * 1번만 성공하고 나머지는 INVITATION_ALREADY_ACCEPTED로 끝나야 한다.
 * ProjectMemberConcurrencyTest와 같은 이유로 클래스에 @Transactional을 붙이지 않는다 — setUp()에서
 * 커밋된 데이터를 워커 스레드들이 각자 별도 커넥션으로 봐야 한다.
 */
@SpringBootTest
class InvitationAcceptConcurrencyTest {

    private static final int THREAD_COUNT = 5;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private InvitationService invitationService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private ProjectInvitationRepository projectInvitationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InvitationTokenGenerator invitationTokenGenerator;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    private Long ownerId;
    private Long inviteeUserId;
    private Long projectId;
    private Long invitationId;
    private String rawToken;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        ownerId = userRepository.save(User.builder()
                .email("owner-" + suffix + "@test.com")
                .password("pw")
                .name("Owner")
                .build()).getId();
        String inviteeEmail = "invitee-" + suffix + "@test.com";
        inviteeUserId = userRepository.save(User.builder()
                .email(inviteeEmail)
                .password("pw")
                .name("Invitee")
                .build()).getId();

        when(currentUserProvider.getCurrentUserId()).thenReturn(ownerId);
        ProjectResDto project = projectService.createProject(new ProjectCreateReqDto("Invitation Concurrency", null));
        projectId = project.id();

        rawToken = "raw-token-" + UUID.randomUUID();
        ProjectInvitation invitation = projectInvitationRepository.save(ProjectInvitation.builder()
                .project(projectRepository.getReferenceById(projectId))
                .email(inviteeEmail)
                .tokenHash(invitationTokenGenerator.hash(rawToken))
                .status(InvitationStatus.PENDING)
                .invitedBy(userRepository.getReferenceById(ownerId))
                .invitedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build());
        invitationId = invitation.getId();
    }

    @AfterEach
    void cleanUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            projectInvitationRepository.deleteById(invitationId);
            projectMemberRepository.deleteAllByProjectId(projectId);
            projectRepository.deleteById(projectId);
            userRepository.deleteById(inviteeUserId);
            userRepository.deleteById(ownerId);
        });
    }

    @Test
    void fiveConcurrentAcceptRequests_resolveToOneSuccessAndRestAlreadyAccepted() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch readyLatch = new CountDownLatch(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        List<InvitationResDto> successes = Collections.synchronizedList(new ArrayList<>());
        List<BusinessException> conflicts = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> unexpected = Collections.synchronizedList(new ArrayList<>());

        try {
            for (int i = 0; i < THREAD_COUNT; i++) {
                executor.submit(() -> {
                    try {
                        readyLatch.countDown();
                        startLatch.await();
                        InvitationResDto result = invitationService.acceptInvitation(rawToken, inviteeUserId);
                        successes.add(result);
                    } catch (BusinessException e) {
                        if (e.getErrorCode() == ErrorCode.INVITATION_ALREADY_ACCEPTED) {
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
                    .filter(m -> m.getUser().getId().equals(inviteeUserId))
                    .count();

            System.out.println("[InvitationAcceptConcurrencyTest] finishedInTime=" + finishedInTime
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
