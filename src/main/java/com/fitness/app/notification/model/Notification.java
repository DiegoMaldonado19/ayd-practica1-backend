package com.fitness.app.notification.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One notice addressed to one account: the outgoing record and the in-app inbox row
 * are the same thing, so channel only says how delivery was attempted.
 *
 * appUserId is a plain Long: AppUser belongs to iam and the isolation rule forbids
 * navigating there through JPA (02-Modulos §1).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Notification
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long                notificationId;

    private Long                appUserId;

    @Enumerated(EnumType.STRING)
    private NotificationType    notificationType;

    @Enumerated(EnumType.STRING)
    private NotificationChannel channel;

    private String              title;
    private String              message;

    @Enumerated(EnumType.STRING)
    private NotificationStatus  status;

    private Instant             createdAt;
    private Instant             sentAt;
    private Instant             readAt;
    private String              failureReason;
}
