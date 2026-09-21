package com.followup.invitation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.followup.global.exception.BusinessException;
import com.followup.global.exception.ErrorCode;
import com.followup.global.security.CurrentUserProvider;
import com.followup.invitation.dto.CreateInvitationReqDto;
import com.followup.invitation.dto.InvitationResDto;
import com.followup.invitation.dto.InvitationViewResDto;
import com.followup.invitation.entity.InvitationStatus;
import com.followup.invitation.entity.ProjectInvitation;
import com.followup.invitation.repository.ProjectInvitationRepository;
import com.followup.invitation.util.InvitationTokenGenerator;
import com.followup.project.dto.ProjectCreateReqDto;
import com.followup.project.dto.ProjectMemberCreateReqDto;
import com.followup.project.dto.ProjectResDto;
import com.followup.project.repository.ProjectMemberRepository;
import com.followup.project.repository.ProjectRepository;
import com.followup.project.service.ProjectMemberService;
import com.followup.project.service.ProjectService;
import com.followup.user.entity.User;
import com.followup.user.repository.UserRepository;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class InvitationServiceTest {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectMemberService projectMemberService;

    @Autowired
    private InvitationService invitationService;

    @Autowired
    private ProjectInvitationRepository projectInvitationRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InvitationTokenGenerator invitationTokenGenerator;

    @Value("${app.origin}")
    private String appOrigin;

    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    @MockitoBean
    private JavaMailSender javaMailSender;

    private Long ownerId;
    private String ownerName;
    private Long memberId;
    private String memberEmail;
    private Long projectId;

    @BeforeEach
    void setUp() throws Exception {
        String suffix = UUID.randomUUID().toString();
        ownerName = "Owner";
        ownerId = userRepository.save(User.builder()
                .email("owner-" + suffix + "@test.com").password("pw").name(ownerName).build()).getId();
        memberEmail = "member-" + suffix + "@test.com";
        memberId = userRepository.save(User.builder()
                .email(memberEmail).password("pw").name("Member").build()).getId();

        actingAs(ownerId);
        projectId = projectService.createProject(new ProjectCreateReqDto("Project", null)).id();

        reset(javaMailSender);
        Session session = Session.getInstance(new Properties());
        when(javaMailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage(session));
    }

    private void actingAs(Long userId) {
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);
    }

    private String uniqueEmail() {
        return "invitee-" + UUID.randomUUID() + "@test.com";
    }

    /**
     * multipart/mixed(alternative(text/plain, text/html)) 구조를 가정하지 않고, 첫 번째 text 파트를
     * 재귀적으로 찾는다. isMimeType()(Content-Type 헤더 기반)는 안 쓴다 — 실제 전송 없이 mock으로
     * send()만 가로채는 테스트라 MimeMessage.saveChanges()가 호출되지 않아 헤더가 아직 최종 반영되기
     * 전 상태일 수 있고, getContent()가 돌려주는 실제 객체 타입만이 신뢰할 수 있는 기준이다.
     */
    private String extractPlainTextBody(Object content) throws Exception {
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                String found = extractPlainTextBody(multipart.getBodyPart(i).getContent());
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private String extractLatestSentToken() throws Exception {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender).send(captor.capture());
        String body = extractPlainTextBody(captor.getValue().getContent());
        String prefix = appOrigin + "/invite/";
        assertThat(body).contains(prefix);
        int idx = body.indexOf(prefix) + prefix.length();
        return body.substring(idx).split("\\s")[0].trim();
    }

    private ProjectInvitation saveRawInvitation(String email, String rawToken, InvitationStatus status,
                                                 LocalDateTime expiresAt) {
        return projectInvitationRepository.save(ProjectInvitation.builder()
                .project(projectRepository.getReferenceById(projectId))
                .email(email)
                .tokenHash(invitationTokenGenerator.hash(rawToken))
                .status(status)
                .invitedBy(userRepository.getReferenceById(ownerId))
                .invitedAt(LocalDateTime.now().minusDays(1))
                .expiresAt(expiresAt)
                .build());
    }

    // ---- createInvitation ----

    @Test
    void createInvitation_success() {
        actingAs(ownerId);
        String email = uniqueEmail();

        InvitationResDto response = invitationService.createInvitation(projectId, new CreateInvitationReqDto(email));

        assertThat(response.email()).isEqualTo(email);
        assertThat(response.status()).isEqualTo(InvitationStatus.PENDING);
        assertThat(response.invitedByName()).isEqualTo(ownerName);
        assertThat(response.acceptedAt()).isNull();
    }

    @Test
    void createInvitation_notOwner_forbidden() {
        projectMemberService.addMember(projectId, new ProjectMemberCreateReqDto(memberEmail));
        actingAs(memberId);

        assertThatThrownBy(() -> invitationService.createInvitation(projectId, new CreateInvitationReqDto(uniqueEmail())))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROJECT_OWNER_REQUIRED);
    }

    @Test
    void createInvitation_alreadyMember_conflict() {
        projectMemberService.addMember(projectId, new ProjectMemberCreateReqDto(memberEmail));
        actingAs(ownerId);

        assertThatThrownBy(() -> invitationService.createInvitation(projectId, new CreateInvitationReqDto(memberEmail)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROJECT_MEMBER_ALREADY_EXISTS);
    }

    @Test
    void createInvitation_livePendingInviteAlreadyExists_conflict() {
        actingAs(ownerId);
        String email = uniqueEmail();
        invitationService.createInvitation(projectId, new CreateInvitationReqDto(email));

        assertThatThrownBy(() -> invitationService.createInvitation(projectId, new CreateInvitationReqDto(email)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVITATION_ALREADY_EXISTS);
    }

    @Test
    void createInvitation_capacityExceeded_rejected() {
        actingAs(ownerId);
        // owner(1명) + PENDING 9건 = 10명 도달 후, 그다음 초대는 거부돼야 한다.
        for (int i = 0; i < 9; i++) {
            invitationService.createInvitation(projectId, new CreateInvitationReqDto(uniqueEmail()));
        }

        assertThatThrownBy(() -> invitationService.createInvitation(projectId, new CreateInvitationReqDto(uniqueEmail())))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROJECT_MEMBER_LIMIT_EXCEEDED);
    }

    @Test
    void createInvitation_expiredPendingInvitesDoNotCountTowardCapacity() {
        actingAs(ownerId);
        // owner(1명) + 만료된 PENDING 9건을 만들어도, 만료된 건 정원에서 빠지므로 새 초대는 여전히 성공해야 한다.
        for (int i = 0; i < 9; i++) {
            saveRawInvitation(uniqueEmail(), "raw-token-" + UUID.randomUUID(), InvitationStatus.PENDING,
                    LocalDateTime.now().minusDays(1));
        }

        InvitationResDto response = invitationService.createInvitation(projectId, new CreateInvitationReqDto(uniqueEmail()));

        assertThat(response.status()).isEqualTo(InvitationStatus.PENDING);
    }

    @Test
    void createInvitation_sendsEmailWithAcceptUrlMatchingStoredTokenHash() throws Exception {
        actingAs(ownerId);
        String email = uniqueEmail();

        InvitationResDto response = invitationService.createInvitation(projectId, new CreateInvitationReqDto(email));

        String tokenInEmail = extractLatestSentToken();
        ProjectInvitation saved = projectInvitationRepository.findById(response.id()).orElseThrow();
        assertThat(invitationTokenGenerator.hash(tokenInEmail)).isEqualTo(saved.getTokenHash());
    }

    // ---- listInvitations ----

    @Test
    void listInvitations_memberCanView() {
        actingAs(ownerId);
        invitationService.createInvitation(projectId, new CreateInvitationReqDto(uniqueEmail()));
        projectMemberService.addMember(projectId, new ProjectMemberCreateReqDto(memberEmail));

        actingAs(memberId);
        assertThat(invitationService.listInvitations(projectId)).hasSize(1);
    }

    // ---- resend / cancel ----

    @Test
    void resendInvitation_notOwner_forbidden() {
        actingAs(ownerId);
        InvitationResDto invitation = invitationService.createInvitation(projectId, new CreateInvitationReqDto(uniqueEmail()));
        projectMemberService.addMember(projectId, new ProjectMemberCreateReqDto(memberEmail));

        actingAs(memberId);
        assertThatThrownBy(() -> invitationService.resendInvitation(projectId, invitation.id()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROJECT_OWNER_REQUIRED);
    }

    @Test
    void resendInvitation_replacesTokenAndResendsEmail() throws Exception {
        actingAs(ownerId);
        InvitationResDto invitation = invitationService.createInvitation(projectId, new CreateInvitationReqDto(uniqueEmail()));
        String firstTokenHash = projectInvitationRepository.findById(invitation.id()).orElseThrow().getTokenHash();
        reset(javaMailSender);
        Session session = Session.getInstance(new Properties());
        when(javaMailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(session));

        invitationService.resendInvitation(projectId, invitation.id());

        String tokenInEmail = extractLatestSentToken();
        ProjectInvitation reloaded = projectInvitationRepository.findById(invitation.id()).orElseThrow();
        assertThat(reloaded.getTokenHash()).isNotEqualTo(firstTokenHash);
        assertThat(invitationTokenGenerator.hash(tokenInEmail)).isEqualTo(reloaded.getTokenHash());
    }

    @Test
    void cancelInvitation_notOwner_forbidden() {
        actingAs(ownerId);
        InvitationResDto invitation = invitationService.createInvitation(projectId, new CreateInvitationReqDto(uniqueEmail()));
        projectMemberService.addMember(projectId, new ProjectMemberCreateReqDto(memberEmail));

        actingAs(memberId);
        assertThatThrownBy(() -> invitationService.cancelInvitation(projectId, invitation.id()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROJECT_OWNER_REQUIRED);
    }

    @Test
    void cancelInvitation_thenViewShowsCancelledAndAcceptIsBlocked() throws Exception {
        actingAs(ownerId);
        String email = uniqueEmail();
        InvitationResDto invitation = invitationService.createInvitation(projectId, new CreateInvitationReqDto(email));
        String rawToken = extractLatestSentToken();

        invitationService.cancelInvitation(projectId, invitation.id());

        InvitationViewResDto view = invitationService.viewInvitation(rawToken);
        assertThat(view.status()).isEqualTo("CANCELLED");

        Long inviteeUserId = userRepository.save(User.builder()
                .email(email).password("pw").name("Invitee").build()).getId();
        assertThatThrownBy(() -> invitationService.acceptInvitation(rawToken, inviteeUserId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVITATION_CANCELLED);
    }

    // ---- viewInvitation ----

    @Test
    void viewInvitation_unknownToken_notFound() {
        assertThatThrownBy(() -> invitationService.viewInvitation("no-such-token"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVITATION_NOT_FOUND);
    }

    @Test
    void viewInvitation_expired_showsExpiredStatusWithoutError() {
        String rawToken = "raw-token-" + UUID.randomUUID();
        saveRawInvitation(uniqueEmail(), rawToken, InvitationStatus.PENDING, LocalDateTime.now().minusDays(1));

        InvitationViewResDto view = invitationService.viewInvitation(rawToken);

        assertThat(view.status()).isEqualTo("EXPIRED");
    }

    // ---- acceptInvitation ----

    @Test
    void acceptInvitation_emailMismatch_forbidden() {
        String rawToken = "raw-token-" + UUID.randomUUID();
        saveRawInvitation(uniqueEmail(), rawToken, InvitationStatus.PENDING, LocalDateTime.now().plusDays(7));

        assertThatThrownBy(() -> invitationService.acceptInvitation(rawToken, memberId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVITATION_EMAIL_MISMATCH);
    }

    @Test
    void acceptInvitation_expired_gone() {
        String rawToken = "raw-token-" + UUID.randomUUID();
        ProjectInvitation invitation = saveRawInvitation(memberEmail, rawToken, InvitationStatus.PENDING,
                LocalDateTime.now().minusDays(1));

        assertThatThrownBy(() -> invitationService.acceptInvitation(rawToken, memberId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVITATION_EXPIRED);
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.PENDING);
    }

    @Test
    void acceptInvitation_cancelled_rejected() {
        String rawToken = "raw-token-" + UUID.randomUUID();
        saveRawInvitation(memberEmail, rawToken, InvitationStatus.CANCELLED, LocalDateTime.now().plusDays(7));

        assertThatThrownBy(() -> invitationService.acceptInvitation(rawToken, memberId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVITATION_CANCELLED);
    }

    @Test
    void acceptInvitation_alreadyAccepted_rejected() {
        String rawToken = "raw-token-" + UUID.randomUUID();
        saveRawInvitation(memberEmail, rawToken, InvitationStatus.ACCEPTED, LocalDateTime.now().plusDays(7));

        assertThatThrownBy(() -> invitationService.acceptInvitation(rawToken, memberId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVITATION_ALREADY_ACCEPTED);
    }

    @Test
    void acceptInvitation_success_addsProjectMember() {
        actingAs(ownerId);
        InvitationResDto created = invitationService.createInvitation(projectId, new CreateInvitationReqDto(memberEmail));

        String rawToken = "raw-token-" + UUID.randomUUID();
        // 원본 토큰은 서비스가 보관하지 않으므로, 검증용으로 같은 해시가 나오게 직접 교체해 둔다.
        ProjectInvitation stored = projectInvitationRepository.findById(created.id()).orElseThrow();
        stored.replaceToken(invitationTokenGenerator.hash(rawToken), LocalDateTime.now().plusDays(7));

        InvitationResDto accepted = invitationService.acceptInvitation(rawToken, memberId);

        assertThat(accepted.status()).isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(projectMemberRepository.existsByProjectIdAndUserId(projectId, memberId)).isTrue();
    }
}
