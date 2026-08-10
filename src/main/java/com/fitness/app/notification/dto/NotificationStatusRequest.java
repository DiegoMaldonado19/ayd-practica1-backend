package com.fitness.app.notification.dto;

import com.fitness.app.notification.model.NotificationStatus;
import jakarta.validation.constraints.NotNull;

/**
 * "Marca una notificación como leída" (§3.9). READ is the only destination the API
 * accepts; PENDING, SENT and FAILED are delivery states that only the server writes.
 */
public record NotificationStatusRequest(
    @NotNull
    NotificationStatus status
)
{
}
