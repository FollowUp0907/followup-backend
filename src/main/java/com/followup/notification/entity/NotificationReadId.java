package com.followup.notification.entity;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class NotificationReadId implements Serializable {

    private Long userId;
    private String dedupKey;
    private LocalDate readDate;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NotificationReadId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId)
                && Objects.equals(dedupKey, that.dedupKey)
                && Objects.equals(readDate, that.readDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, dedupKey, readDate);
    }
}
