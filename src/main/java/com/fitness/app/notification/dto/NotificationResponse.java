package com.fitness.app.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fitness.app.notification.model.Notification;
import com.fitness.app.notification.model.NotificationChannel;
import com.fitness.app.notification.model.NotificationStatus;
import com.fitness.app.notification.model.NotificationType;

import java.time.Instant;

/**
 * One inbox row as the interface sees it. Serves the listing and the read
 * acknowledgement: two endpoints, one shape.
 *
 * failureReason travels so the front desk can tell why a formal notice never left,
 * which is the whole point of keeping FAILED apart from PENDING.
 */
public record NotificationResponse(
    @JsonProperty("notification_id")
    Long                notificationId,

    @JsonProperty("notification_type")
    NotificationType    notificationType,

    NotificationChannel channel,

    String              title,

    String              message,

    NotificationStatus  status,

    @JsonProperty("created_at")
    Instant             createdAt,

    @JsonProperty("sent_at")
    Instant             sentAt,

    @JsonProperty("read_at")
    Instant             readAt,

    @JsonProperty("failure_reason")
    String              failureReason
)
{
    public static NotificationResponse from(Notification notification)
    {
        return new NotificationResponse(
            notification.getNotificationId(),
            notification.getNotificationType(),
            notification.getChannel(),
            notification.getTitle(),
            notification.getMessage(),
            notification.getStatus(),
            notification.getCreatedAt(),
            notification.getSentAt(),
            notification.getReadAt(),
            notification.getFailureReason()
        );
    }
}
