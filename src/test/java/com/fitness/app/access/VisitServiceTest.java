package com.fitness.app.access;

import com.fitness.app.access.dto.CheckInRequest;
import com.fitness.app.access.model.AccessChannel;
import com.fitness.app.access.model.FacilityVisit;
import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.directory.MemberService;
import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.iam.model.UserRole;
import com.fitness.app.membership.MembershipService;
import com.fitness.app.membership.dto.MembershipResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Check-in and check-out rules. The Postman collection covers the happy path and
 * most errors; these tests cover the state machine transitions.
 */
@ExtendWith(MockitoExtension.class)
class VisitServiceTest
{
    private static final Long MEMBER_ID      = 5L;
    private static final Long MEMBERSHIP_ID  = 10L;
    private static final Long USER_ID        = 1L;
    private static final Long VISIT_ID       = 20L;

    @Mock private FacilityVisitRepository facilityVisitRepository;
    @Mock private MembershipService       membershipService;
    @Mock private MemberService          memberService;

    @InjectMocks private VisitService visitService;

    @Test
    void checkInRejectsAFrozenMembership()
    {
        when(memberService.findById(MEMBER_ID, principal())).thenReturn(null);
        when(membershipService.findActiveMembership(MEMBER_ID))
                .thenThrow(new BusinessException(ErrorCode.MEMBERSHIP_FROZEN));

        assertEquals(ErrorCode.MEMBERSHIP_FROZEN,
                     assertThrows(BusinessException.class,
                                  () -> visitService.checkIn(new CheckInRequest(MEMBER_ID, null), principal()))
                             .getErrorCode());
    }

    @Test
    void checkInSavesTheMembershipIdOfTheValidContract()
    {
        var membership = membershipResponse();

        when(memberService.findById(MEMBER_ID, principal())).thenReturn(null);
        when(membershipService.findActiveMembership(MEMBER_ID)).thenReturn(membership);
        when(facilityVisitRepository.existsByMemberIdAndCheckedOutAtIsNull(MEMBER_ID)).thenReturn(false);
        when(facilityVisitRepository.save(any(FacilityVisit.class))).thenAnswer(call -> {
            var visit = (FacilityVisit) call.getArgument(0);
            visit.setFacilityVisitId(VISIT_ID);
            return visit;
        });

        var response = visitService.checkIn(new CheckInRequest(MEMBER_ID, null), principal());

        assertEquals(MEMBERSHIP_ID, response.membershipId());
        assertEquals(AccessChannel.FRONT_DESK, response.channel());
        assertNotNull(response.checkedInAt());
        assertNull(response.checkedOutAt());
    }

    @Test
    void refusesASecondCheckInWithoutCheckOut()
    {
        var membership = membershipResponse();

        when(memberService.findById(MEMBER_ID, principal())).thenReturn(null);
        when(membershipService.findActiveMembership(MEMBER_ID)).thenReturn(membership);
        when(facilityVisitRepository.existsByMemberIdAndCheckedOutAtIsNull(MEMBER_ID)).thenReturn(true);

        assertEquals(ErrorCode.VISIT_ALREADY_OPEN,
                     assertThrows(BusinessException.class,
                                  () -> visitService.checkIn(new CheckInRequest(MEMBER_ID, null), principal()))
                             .getErrorCode());
    }

    @Test
    void refusesASecondCheckOut()
    {
        var visit = visit();

        visit.setCheckedOutAt(Instant.now());

        when(facilityVisitRepository.findById(VISIT_ID)).thenReturn(Optional.of(visit));
        when(memberService.findById(MEMBER_ID, principal())).thenReturn(null);

        assertEquals(ErrorCode.INVALID_STATE_TRANSITION,
                     assertThrows(BusinessException.class,
                                  () -> visitService.checkOut(VISIT_ID, principal())).getErrorCode());
    }

    private static MembershipResponse membershipResponse()
    {
        return new MembershipResponse(MEMBERSHIP_ID, MEMBER_ID, null, null, null, null, null, 30, null, null, null);
    }

    private static FacilityVisit visit()
    {
        var visit = new FacilityVisit();

        visit.setFacilityVisitId(VISIT_ID);
        visit.setMemberId(MEMBER_ID);
        visit.setMembershipId(MEMBERSHIP_ID);
        visit.setCheckedInAt(Instant.now().minusSeconds(3600));
        visit.setChannel(AccessChannel.FRONT_DESK);
        visit.setCheckedInByUserId(USER_ID);

        return visit;
    }

    private static AuthenticatedUser principal()
    {
        return new AuthenticatedUser(USER_ID, "recepcion", UserRole.RECEPTIONIST);
    }
}
