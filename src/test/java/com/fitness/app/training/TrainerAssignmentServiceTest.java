package com.fitness.app.training;

import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.membership.MembershipService;
import com.fitness.app.membership.model.PlanBenefit;
import com.fitness.app.notification.NotificationService;
import com.fitness.app.training.model.AssignmentEndReason;
import com.fitness.app.training.model.TrainerAssignment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * "Transferir la cartera sin perder el historial": that the old stretch is closed and
 * not overwritten, and that the destination's cap is respected before anything moves.
 */
@ExtendWith(MockitoExtension.class)
class TrainerAssignmentServiceTest
{
    private static final Long  FROM_TRAINER = 7L;
    private static final Long  TO_TRAINER   = 8L;
    private static final Long  ADMIN_USER   = 1L;
    private static final short MAX_LOAD     = (short) 10;

    @Mock private TrainerAssignmentRepository trainerAssignmentRepository;
    @Mock private MembershipService           membershipService;
    @Mock private NotificationService         notificationService;

    @InjectMocks private TrainerAssignmentService trainerAssignmentService;

    @Test
    void transferClosesTheOldStretchAndOpensANewOnePerMember()
    {
        var first  = openAssignment(101L, 3L);
        var second = openAssignment(102L, 4L);

        when(trainerAssignmentRepository.findByTrainerIdAndEndDateIsNull(FROM_TRAINER)).thenReturn(List.of(first, second));
        when(trainerAssignmentRepository.countByTrainerIdAndEndDateIsNull(TO_TRAINER)).thenReturn(0L);

        var transferred = trainerAssignmentService.transferCaseload(FROM_TRAINER, TO_TRAINER, MAX_LOAD,
                                                                    "Luis Gómez", ADMIN_USER);

        // The history: the previous rows are dated and given a reason, never rewritten.
        assertEquals(AssignmentEndReason.TRAINER_LEFT, first.getEndReason());
        assertEquals(AssignmentEndReason.TRAINER_LEFT, second.getEndReason());
        assertNotNull(first.getEndDate());
        assertEquals(FROM_TRAINER, first.getTrainerId());

        assertEquals(2, transferred.size());
        assertTrue(transferred.stream().allMatch(assignment -> TO_TRAINER.equals(assignment.trainerId())));
        assertTrue(transferred.stream().allMatch(assignment -> assignment.endDate() == null));

        // uq_assign_current: the closing update must reach the database before the insert.
        verify(trainerAssignmentRepository).flush();
        verify(notificationService).trainerAssigned(3L, "Luis Gómez");
        verify(notificationService).trainerAssigned(4L, "Luis Gómez");
    }

    @Test
    void refusesTheTransferWhenTheDestinationWouldPassItsCap()
    {
        // Checked over the whole caseload and before any write: half a transfer would
        // leave the members split between two trainers.
        when(trainerAssignmentRepository.findByTrainerIdAndEndDateIsNull(FROM_TRAINER))
                .thenReturn(List.of(openAssignment(101L, 3L), openAssignment(102L, 4L)));
        when(trainerAssignmentRepository.countByTrainerIdAndEndDateIsNull(TO_TRAINER)).thenReturn(9L);

        assertEquals(ErrorCode.TRAINER_CAPACITY_EXCEEDED,
                     assertThrows(BusinessException.class,
                                  () -> trainerAssignmentService.transferCaseload(FROM_TRAINER, TO_TRAINER, MAX_LOAD,
                                                                                  "Luis Gómez", ADMIN_USER))
                             .getErrorCode());

        verify(trainerAssignmentRepository, never()).saveAll(any());
        verifyNoInteractions(notificationService);
    }

    @Test
    void anEmptyCaseloadIsNotAnError()
    {
        when(trainerAssignmentRepository.findByTrainerIdAndEndDateIsNull(FROM_TRAINER)).thenReturn(List.of());

        assertTrue(trainerAssignmentService.transferCaseload(FROM_TRAINER, TO_TRAINER, MAX_LOAD,
                                                             "Luis Gómez", ADMIN_USER).isEmpty());

        verify(trainerAssignmentRepository, never()).saveAll(any());
        verifyNoInteractions(notificationService);
    }

    @Test
    void refusesATrainerToAPlanWithoutTheBenefit()
    {
        // "El sistema debe validar, en cada intento de [...] solicitud de entrenador,
        // si el plan actual del socio incluye ese beneficio" (Enunciado): only Élite
        // carries PERSONAL_TRAINER, and the answer suggests the upgrade.
        when(membershipService.hasBenefit(3L, PlanBenefit.PERSONAL_TRAINER)).thenReturn(false);

        assertEquals(ErrorCode.PLAN_BENEFIT_NOT_INCLUDED,
                     assertThrows(BusinessException.class,
                                  () -> trainerAssignmentService.assign(3L, TO_TRAINER, MAX_LOAD,
                                                                        "Luis Gómez", ADMIN_USER))
                             .getErrorCode());

        verify(trainerAssignmentRepository, never()).save(any());
        verifyNoInteractions(notificationService);
    }

    @Test
    void refusesASecondTrainerWhileOneIsInForce()
    {
        // uq_assign_current in Java: the open row is the relationship, and a member
        // has exactly one.
        when(membershipService.hasBenefit(3L, PlanBenefit.PERSONAL_TRAINER)).thenReturn(true);
        when(trainerAssignmentRepository.findByMemberIdAndEndDateIsNull(3L))
                .thenReturn(Optional.of(openAssignment(101L, 3L)));

        assertEquals(ErrorCode.TRAINER_ALREADY_ASSIGNED,
                     assertThrows(BusinessException.class,
                                  () -> trainerAssignmentService.assign(3L, TO_TRAINER, MAX_LOAD,
                                                                        "Luis Gómez", ADMIN_USER))
                             .getErrorCode());

        verifyNoInteractions(notificationService);
    }

    @Test
    void refusesAnAssignmentThatWouldFillTheTrainerPastItsCap()
    {
        when(membershipService.hasBenefit(3L, PlanBenefit.PERSONAL_TRAINER)).thenReturn(true);
        when(trainerAssignmentRepository.findByMemberIdAndEndDateIsNull(3L)).thenReturn(Optional.empty());
        when(trainerAssignmentRepository.countByTrainerIdAndEndDateIsNull(TO_TRAINER)).thenReturn((long) MAX_LOAD);

        assertEquals(ErrorCode.TRAINER_CAPACITY_EXCEEDED,
                     assertThrows(BusinessException.class,
                                  () -> trainerAssignmentService.assign(3L, TO_TRAINER, MAX_LOAD,
                                                                        "Luis Gómez", ADMIN_USER))
                             .getErrorCode());

        verify(trainerAssignmentRepository, never()).save(any());
    }

    @Test
    void aMemberWithoutAnOpenStretchHasNoTrainer()
    {
        when(trainerAssignmentRepository.findByMemberIdAndEndDateIsNull(3L)).thenReturn(Optional.empty());

        assertEquals(ErrorCode.TRAINER_ASSIGNMENT_NOT_FOUND,
                     assertThrows(BusinessException.class,
                                  () -> trainerAssignmentService.currentTrainerOf(3L)).getErrorCode());
    }

    private static TrainerAssignment openAssignment(Long assignmentId, Long memberId)
    {
        var assignment = new TrainerAssignment();

        assignment.setTrainerAssignmentId(assignmentId);
        assignment.setMemberId(memberId);
        assignment.setTrainerId(FROM_TRAINER);
        assignment.setStartDate(LocalDate.now().minusMonths(2));
        assignment.setAssignedByUserId(ADMIN_USER);

        return assignment;
    }
}
