package com.fitness.app.notification;

import com.fitness.app.notification.model.Notification;
import com.fitness.app.notification.model.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface NotificationRepository extends JpaRepository<Notification, Long>
{
    /**
     * The inbox of ix_notification_inbox: always scoped to one account, with the
     * status filter of §3.9 left nullable.
     */
    @Query("""
           SELECT n
             FROM Notification n
            WHERE n.appUserId = :appUserId
              AND (:status IS NULL OR n.status = :status)
           """)
    Page<Notification> findInbox(Long appUserId, NotificationStatus status, Pageable pageable);
}
