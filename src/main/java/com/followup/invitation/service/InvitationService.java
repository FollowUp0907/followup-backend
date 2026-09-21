package com.followup.invitation.service;

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
import com.followup.project.entity.Project;
import com.followup.project.entity.ProjectMember;
import com.followup.project.entity.ProjectRole;
import com.followup.project.repository.ProjectMemberRepository;
import com.followup.project.repository.ProjectRepository;
import com.followup.user.entity.User;
import com.followup.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InvitationService {

    private static final int MAX_PROJECT_SIZE = 10;
    private static final long INVITATION_EXPIRES_DAYS = 7;

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectInvitationRepository projectInvitationRepository;
    private final UserRepository userRepository;
    private final InvitationTokenGenerator invitationTokenGenerator;
    private final InvitationEmailService invitationEmailService;
    private final CurrentUserProvider currentUserProvider;

    /**
     * Project를 비관적 쓰기 락으로 먼저 잠가 같은 프로젝트에 대한 동시 초대 요청을 직렬화한다
     * (AiAnalysisRunTxService.startAnalysis와 같은 패턴) — 그래서 정원 체크 + insert를 하나의
     * 트랜잭션 안에서 그대로 처리해도 레이스로 정원을 넘기지 않는다.
     */
    @Transactional
    public InvitationResDto createInvitation(Long projectId, CreateInvitationReqDto request) {
        Project project = projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        Long actorId = currentUserProvider.getCurrentUserId();
        requireOwner(projectId, actorId);

        String email = request.email();

        boolean alreadyMember = userRepository.findByEmail(email)
                .map(user -> projectMemberRepository.existsByProjectIdAndUserId(projectId, user.getId()))
                .orElse(false);
        if (alreadyMember) {
            throw new BusinessException(ErrorCode.PROJECT_MEMBER_ALREADY_EXISTS);
        }

        boolean hasLivePendingInvite = projectInvitationRepository
                .findByProjectIdAndEmailAndStatus(projectId, email, InvitationStatus.PENDING)
                .filter(invitation -> !invitation.isExpired())
                .isPresent();
        if (hasLivePendingInvite) {
            throw new BusinessException(ErrorCode.INVITATION_ALREADY_EXISTS);
        }

        requireCapacity(projectId);

        User inviter = userRepository.getReferenceById(actorId);
        String rawToken = invitationTokenGenerator.generateToken();
        LocalDateTime now = LocalDateTime.now();

        ProjectInvitation invitation = ProjectInvitation.builder()
                .project(project)
                .email(email)
                .tokenHash(invitationTokenGenerator.hash(rawToken))
                .status(InvitationStatus.PENDING)
                .invitedBy(inviter)
                .invitedAt(now)
                .expiresAt(now.plusDays(INVITATION_EXPIRES_DAYS))
                .build();
        projectInvitationRepository.save(invitation);

        invitationEmailService.sendInvitationEmail(invitation, inviter.getName(), rawToken);

        return InvitationResDto.from(invitation);
    }

    /** 프로젝트 멤버라면(OWNER가 아니어도) 조회할 수 있다 — 프론트가 PENDING만 걸러서 쓴다. */
    @Transactional(readOnly = true)
    public List<InvitationResDto> listInvitations(Long projectId) {
        getProjectOrThrow(projectId);
        requireMember(projectId, currentUserProvider.getCurrentUserId());

        return projectInvitationRepository.findAllByProjectId(projectId).stream()
                .map(InvitationResDto::from)
                .toList();
    }

    /** 대상이 PENDING이 아니면(CANCELLED/ACCEPTED) 재발송할 수 없다. */
    @Transactional
    public void resendInvitation(Long projectId, Long invitationId) {
        getProjectOrThrow(projectId);
        requireOwner(projectId, currentUserProvider.getCurrentUserId());

        ProjectInvitation invitation = getInvitationOrThrow(projectId, invitationId);
        requirePending(invitation);

        String rawToken = invitationTokenGenerator.generateToken();
        invitation.replaceToken(invitationTokenGenerator.hash(rawToken),
                LocalDateTime.now().plusDays(INVITATION_EXPIRES_DAYS));

        invitationEmailService.sendInvitationEmail(invitation, invitation.getInvitedBy().getName(), rawToken);
    }

    /** 하드 삭제가 아니라 상태만 CANCELLED로 바꾼다 — 초대 이력은 남긴다. */
    @Transactional
    public void cancelInvitation(Long projectId, Long invitationId) {
        getProjectOrThrow(projectId);
        requireOwner(projectId, currentUserProvider.getCurrentUserId());

        ProjectInvitation invitation = getInvitationOrThrow(projectId, invitationId);
        invitation.cancel();
    }

    /** 인증 불필요 — 토큰만 있으면 누구나 초대 내용을 확인할 수 있다. */
    @Transactional(readOnly = true)
    public InvitationViewResDto viewInvitation(String token) {
        return InvitationViewResDto.from(getInvitationByTokenOrThrow(token));
    }

    /**
     * 토큰 조회부터 비관적 쓰기 락을 걸어 같은 토큰에 대한 동시 수락 요청을 직렬화한다 — 그래야
     * 두 요청이 동시에 status==PENDING을 읽고 둘 다 ProjectMember를 insert하려다 유니크 제약 위반으로
     * 한쪽이 처리되지 않은 예외(500)를 내는 대신, 뒤에 도착한 쪽이 잠금 해제 후 바뀐 상태를 다시 읽어
     * INVITATION_ALREADY_ACCEPTED로 깔끔하게 끝난다.
     */
    @Transactional
    public InvitationResDto acceptInvitation(String token, Long currentUserId) {
        ProjectInvitation invitation = getInvitationByTokenForUpdateOrThrow(token);

        if (invitation.getStatus() == InvitationStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.INVITATION_CANCELLED);
        }
        if (invitation.isExpired()) {
            throw new BusinessException(ErrorCode.INVITATION_EXPIRED);
        }
        if (invitation.getStatus() == InvitationStatus.ACCEPTED) {
            throw new BusinessException(ErrorCode.INVITATION_ALREADY_ACCEPTED);
        }

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!currentUser.getEmail().equalsIgnoreCase(invitation.getEmail())) {
            throw new BusinessException(ErrorCode.INVITATION_EMAIL_MISMATCH);
        }

        Long projectId = invitation.getProject().getId();
        if (!projectMemberRepository.existsByProjectIdAndUserId(projectId, currentUserId)) {
            projectMemberRepository.save(ProjectMember.builder()
                    .project(invitation.getProject())
                    .user(currentUser)
                    .role(ProjectRole.MEMBER)
                    .build());
        }
        invitation.accept();

        return InvitationResDto.from(invitation);
    }

    /** (현재 멤버 수 + 아직 만료되지 않은 PENDING 초대 수) 기준 — 만료된 채 방치된 초대는 정원을 차지하지 않는다. */
    private void requireCapacity(Long projectId) {
        long memberCount = projectMemberRepository.countByProjectId(projectId);
        long pendingCount = projectInvitationRepository.countByProjectIdAndStatusAndExpiresAtAfter(
                projectId, InvitationStatus.PENDING, LocalDateTime.now());
        if (memberCount + pendingCount >= MAX_PROJECT_SIZE) {
            throw new BusinessException(ErrorCode.PROJECT_MEMBER_LIMIT_EXCEEDED);
        }
    }

    private void requirePending(ProjectInvitation invitation) {
        if (invitation.getStatus() == InvitationStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.INVITATION_CANCELLED);
        }
        if (invitation.getStatus() == InvitationStatus.ACCEPTED) {
            throw new BusinessException(ErrorCode.INVITATION_ALREADY_ACCEPTED);
        }
    }

    private ProjectInvitation getInvitationOrThrow(Long projectId, Long invitationId) {
        ProjectInvitation invitation = projectInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITATION_NOT_FOUND));
        if (!invitation.getProject().getId().equals(projectId)) {
            throw new BusinessException(ErrorCode.INVITATION_NOT_FOUND);
        }
        return invitation;
    }

    private ProjectInvitation getInvitationByTokenOrThrow(String token) {
        return projectInvitationRepository.findByTokenHash(invitationTokenGenerator.hash(token))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITATION_NOT_FOUND));
    }

    private ProjectInvitation getInvitationByTokenForUpdateOrThrow(String token) {
        return projectInvitationRepository.findByTokenHashForUpdate(invitationTokenGenerator.hash(token))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITATION_NOT_FOUND));
    }

    private Project getProjectOrThrow(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }

    private ProjectMember requireMember(Long projectId, Long userId) {
        return projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_ACCESS_DENIED));
    }

    private void requireOwner(Long projectId, Long userId) {
        ProjectMember member = requireMember(projectId, userId);
        if (member.getRole() != ProjectRole.OWNER) {
            throw new BusinessException(ErrorCode.PROJECT_OWNER_REQUIRED);
        }
    }
}
