package com.followup.meeting.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.followup.meeting.entity.Meeting;
import com.followup.meeting.entity.MeetingStatus;
import com.followup.project.entity.Project;
import com.followup.user.entity.User;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class MeetingListResDtoTest {

    /** 만든 사람이 탈퇴해 createdBy가 null인 회의도 예외 없이 응답으로 변환돼야 한다. */
    @Test
    void from_createdByNull_doesNotThrowAndCreatedByIsNull() {
        User owner = User.builder().email("owner@test.com").password("pw").name("Owner").build();
        Project project = Project.builder().name("P").createdBy(owner).build();
        Meeting meeting = Meeting.builder()
                .project(project)
                .title("Sync")
                .scheduledAt(LocalDateTime.of(2026, 1, 1, 10, 0))
                .content("notes")
                .status(MeetingStatus.CONFIRMED)
                .createdBy(null)
                .build();

        assertThatCode(() -> MeetingListResDto.from(meeting)).doesNotThrowAnyException();

        MeetingListResDto dto = MeetingListResDto.from(meeting);
        assertThat(dto.createdBy()).isNull();
    }

    @Test
    void from_createdByPresent_mapsUserId() {
        User owner = User.builder().email("owner2@test.com").password("pw").name("Owner").build();
        setId(owner, 42L);
        Project project = Project.builder().name("P").createdBy(owner).build();
        Meeting meeting = Meeting.builder()
                .project(project)
                .title("Sync")
                .scheduledAt(LocalDateTime.of(2026, 1, 1, 10, 0))
                .content("notes")
                .status(MeetingStatus.CONFIRMED)
                .createdBy(owner)
                .build();

        MeetingListResDto dto = MeetingListResDto.from(meeting);

        assertThat(dto.createdBy()).isEqualTo(42L);
    }

    private void setId(Object entity, Long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
