package com.followup.notification.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** DUE_SOON류 가상 알림의 "하루 한 번 읽음"을 표시만 하는 마커 행이다 — id 자체가 곧 데이터다. */
@Entity
@Table(name = "notification_reads")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationRead {

    @EmbeddedId
    private NotificationReadId id;

    public NotificationRead(Long userId, String dedupKey, LocalDate readDate) {
        this.id = new NotificationReadId(userId, dedupKey, readDate);
    }
}
