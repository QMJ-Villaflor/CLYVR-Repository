package clyvr.backend.database;

import java.time.OffsetDateTime;

public record BackendNotification(
        long notificationId,
        String message,
        OffsetDateTime createdAt,
        boolean isRead) {
}
