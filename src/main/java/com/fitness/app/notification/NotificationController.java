package com.fitness.app.notification;

import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.notification.dto.NotificationResponse;
import com.fitness.app.notification.dto.NotificationStatusRequest;
import com.fitness.app.notification.model.NotificationStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The two /notifications routes of §3.9, both open to the four roles.
 *
 * There is no user id in either path: the inbox is always the caller's, so the
 * principal is the only scope and no matcher could express it anyway.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController
{
    private final NotificationService notificationService;

    @GetMapping
    public PagedModel<NotificationResponse> inbox(@RequestParam(required = false) NotificationStatus status,
                                                  @AuthenticationPrincipal AuthenticatedUser principal,
                                                  Pageable pageable)
    {
        return new PagedModel<>(notificationService.inbox(status, principal, pageable));
    }

    @PatchMapping("/{notificationId}/status")
    public NotificationResponse changeStatus(@PathVariable Long notificationId,
                                             @Valid @RequestBody NotificationStatusRequest request,
                                             @AuthenticationPrincipal AuthenticatedUser principal)
    {
        return notificationService.changeStatus(notificationId, request, principal);
    }
}
