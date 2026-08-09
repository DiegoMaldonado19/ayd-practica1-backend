package com.fitness.app.access;

import com.fitness.app.access.dto.CheckInRequest;
import com.fitness.app.access.dto.FacilityVisitResponse;
import com.fitness.app.access.model.AccessChannel;
import com.fitness.app.access.model.FacilityVisit;
import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.directory.MemberService;
import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.membership.MembershipService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Check-in and check-out. The validation chain for check-in:
 * 1. Member exists (MemberService.findById)
 * 2. Membership is active (MembershipService.findActiveMembership, exact reason)
 * 3. No open visit yet (existsByMemberIdAndCheckedOutAtIsNull)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class VisitService
{
    private final FacilityVisitRepository facilityVisitRepository;
    private final MembershipService       membershipService;
    private final MemberService          memberService;

    public FacilityVisitResponse checkIn(CheckInRequest request, AuthenticatedUser principal)
    {
        // 1. Validate member exists
        memberService.findById(request.memberId(), principal);

        // 2. Validate membership is active (returns error code for exact reason)
        var membership = membershipService.findActiveMembership(request.memberId());

        // 3. Validate no open visit yet
        if (facilityVisitRepository.existsByMemberIdAndCheckedOutAtIsNull(request.memberId()))
        {
            throw new BusinessException(ErrorCode.VISIT_ALREADY_OPEN);
        }

        // Create visit under the validated membership
        var visit = new FacilityVisit();

        visit.setMemberId(request.memberId());
        visit.setMembershipId(membership.membershipId());
        visit.setCheckedInAt(Instant.now());
        visit.setChannel(request.channel() != null ? request.channel() : AccessChannel.FRONT_DESK);
        visit.setCheckedInByUserId(principal.appUserId());

        var saved = facilityVisitRepository.save(visit);

        return FacilityVisitResponse.from(saved);
    }

    public FacilityVisitResponse checkOut(Long visitId, AuthenticatedUser principal)
    {
        var visit = facilityVisitRepository.findById(visitId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VISIT_NOT_FOUND));

        // Verify access to this visit through member row-level check
        memberService.findById(visit.getMemberId(), principal);

        // Cannot check out an already-checked-out visit
        if (visit.getCheckedOutAt() != null)
        {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }

        visit.setCheckedOutAt(Instant.now());
        visit.setCheckedOutByUserId(principal.appUserId());

        var updated = facilityVisitRepository.save(visit);

        return FacilityVisitResponse.from(updated);
    }

    @Transactional(readOnly = true)
    public Page<FacilityVisitResponse> search(Long memberId, LocalDate from, LocalDate to, Boolean open, Pageable pageable)
    {
        // If open filter was not specified, both flags are false (don't filter)
        var range      = DayRange.parse(from, to);
        var openOnly   = open != null && open;
        var closedOnly = open != null && !open;

        return facilityVisitRepository.search(memberId, range.from(), range.to(), openOnly, closedOnly, pageable)
                .map(FacilityVisitResponse::from);
    }
}
