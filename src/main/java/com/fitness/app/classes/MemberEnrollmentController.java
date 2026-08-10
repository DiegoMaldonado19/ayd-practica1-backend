package com.fitness.app.classes;

import com.fitness.app.classes.dto.ClassEnrollmentResponse;
import com.fitness.app.classes.model.EnrollmentStatus;
import com.fitness.app.iam.dto.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * "Clases del socio. Filtros: from, to, status" (§3.2). The route hangs from /members,
 * but the rows are class_enrollment's, and 02-Modulos §3 says directory does not use
 * classes - the same reason MemberMembershipController lives in membership.
 */
@RestController
@RequestMapping("/api/v1/members/{memberId}/enrollments")
@RequiredArgsConstructor
public class MemberEnrollmentController
{
    private final EnrollmentService enrollmentService;

    @GetMapping
    public PagedModel<ClassEnrollmentResponse> history(@PathVariable Long memberId,
                                                       @RequestParam(required = false) LocalDate from,
                                                       @RequestParam(required = false) LocalDate to,
                                                       @RequestParam(required = false) EnrollmentStatus status,
                                                       @AuthenticationPrincipal AuthenticatedUser principal,
                                                       Pageable pageable)
    {
        return new PagedModel<>(enrollmentService.history(memberId, from, to, status, principal, pageable));
    }
}
