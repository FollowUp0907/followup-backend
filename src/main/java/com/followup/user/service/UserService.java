package com.followup.user.service;

import com.followup.actionitem.repository.ActionItemRepository;
import com.followup.ai.repository.AiAnalysisRunRepository;
import com.followup.global.exception.BusinessException;
import com.followup.global.exception.ErrorCode;
import com.followup.global.security.CurrentUserProvider;
import com.followup.meeting.repository.MeetingParticipantRepository;
import com.followup.meeting.repository.MeetingRepository;
import com.followup.notification.repository.NotificationReadRepository;
import com.followup.notification.repository.NotificationRepository;
import com.followup.project.entity.ProjectMember;
import com.followup.project.entity.ProjectRole;
import com.followup.project.repository.ProjectMemberRepository;
import com.followup.project.service.ProjectService;
import com.followup.user.dto.MeResDto;
import com.followup.user.dto.UpdateMeReqDto;
import com.followup.user.entity.User;
import com.followup.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ActionItemRepository actionItemRepository;
    private final MeetingRepository meetingRepository;
    private final AiAnalysisRunRepository aiAnalysisRunRepository;
    private final MeetingParticipantRepository meetingParticipantRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationReadRepository notificationReadRepository;
    private final ProjectService projectService;
    private final CurrentUserProvider currentUserProvider;

    @Transactional
    public MeResDto updateMe(UpdateMeReqDto request) {
        User user = getCurrentUserOrThrow();
        user.updateName(request.name());
        return MeResDto.from(user);
    }

    /**
     * 되돌릴 수 없는 작업이라 자식/참조부터 정리하고 마지막에 User를 지운다 — 중간에 하나라도 실패하면
     * 전체가 롤백되도록 단일 트랜잭션으로 묶는다.
     * OWNER인 프로젝트는 기존 ProjectService.deleteProject()로 통째로 cascade 삭제하고(이미 검증된
     * 로직 재사용), MEMBER로만 속한 프로젝트는 프로젝트 자체는 남긴 채 담당자 목록/멤버십만 정리한다.
     * 그 외 U를 참조하는 테이블은, FK가 nullable인 이력성 데이터(회의/AI분석/업무 작성자)는 참조만 끊고
     * 데이터는 남기며, U 소유의 데이터(알림/읽음기록/회의 참가자 기록)는 통째로 지운다.
     */
    @Transactional
    public void deleteMe() {
        Long userId = currentUserProvider.getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        List<ProjectMember> memberships = projectMemberRepository.findAllByUserId(userId);
        List<Long> ownedProjectIds = memberships.stream()
                .filter(member -> member.getRole() == ProjectRole.OWNER)
                .map(member -> member.getProject().getId())
                .toList();
        List<Long> memberOnlyProjectIds = memberships.stream()
                .filter(member -> member.getRole() != ProjectRole.OWNER)
                .map(member -> member.getProject().getId())
                .toList();

        ownedProjectIds.forEach(projectService::deleteProject);

        memberOnlyProjectIds.forEach(projectId ->
                actionItemRepository.findAllByProjectIdAndAssigneesId(projectId, userId)
                        .forEach(actionItem -> actionItem.removeAssignee(userId)));
        // action_items.created_by는 FK가 RESTRICT라서, OWNER가 아니었던 프로젝트에 U가 만든 업무가
        // 남아 있으면 삭제가 막힌다 — 담당자 목록 정리와 별개로 작성자 참조도 끊어야 한다.
        actionItemRepository.detachCreator(userId);

        meetingRepository.detachCreator(userId);
        aiAnalysisRunRepository.detachRequestedBy(userId);
        meetingParticipantRepository.deleteAllByUserId(userId);
        notificationRepository.deleteAllByUserId(userId);
        notificationReadRepository.deleteAllByIdUserId(userId);
        projectMemberRepository.deleteAllByUserId(userId);

        userRepository.delete(user);
    }

    private User getCurrentUserOrThrow() {
        return userRepository.findById(currentUserProvider.getCurrentUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
