package com.fitness.app.classes;

import com.fitness.app.classes.dto.ClassSessionResponse;
import com.fitness.app.classes.dto.SessionCancellationRequest;
import com.fitness.app.classes.dto.SessionRescheduleRequest;
import com.fitness.app.classes.dto.SessionStatusRequest;
import com.fitness.app.classes.model.ClassSession;
import com.fitness.app.classes.model.Discipline;
import com.fitness.app.classes.model.EnrollmentStatus;
import com.fitness.app.classes.model.SessionStatus;
import com.fitness.app.classes.model.WaitlistStatus;
import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.directory.TrainerService;
import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.iam.model.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The billboard and the session state machine: cartelera, detalle, reprogramación,
 * inicio/cierre y cancelación. Also the single place that turns active enrolments and
 * reserved waitlist seats into seatsTaken/seatsAvailable, so no other service repeats
 * the two-part sum of design decision #2.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ClassSessionService
{
    private final ClassSessionRepository    classSessionRepository;
    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final WaitlistEntryRepository   waitlistEntryRepository;
    private final GroupClassService         groupClassService;
    private final TrainerService            trainerService;

    @Transactional(readOnly = true)
    public Page<ClassSessionResponse> search(LocalDate from, LocalDate to, Discipline discipline, Long trainerId,
                                             Long groupClassId, Boolean hasSeats, Pageable pageable)
    {
        var range    = DateRange.parse(from, to);
        var openOnly = hasSeats != null && hasSeats;
        var fullOnly = hasSeats != null && !hasSeats;

        var page = classSessionRepository.search(range.from(), range.to(), discipline, trainerId, groupClassId,
                                                  openOnly, fullOnly, EnrollmentStatus.CANCELLED,
                                                  WaitlistStatus.NOTIFIED, Instant.now(), pageable);

        return toResponsePage(page);
    }

    @Transactional(readOnly = true)
    public ClassSessionResponse findById(Long sessionId)
    {
        var session = findOrFail(sessionId);

        return ClassSessionResponse.from(session, seatsTaken(sessionId));
    }

    /** "Sesiones generadas" of one class (§3.6), filtered by range. */
    @Transactional(readOnly = true)
    public Page<ClassSessionResponse> findByGroupClass(Long groupClassId, LocalDate from, LocalDate to, Pageable pageable)
    {
        groupClassService.findOrFail(groupClassId);

        var range = DateRange.parse(from, to);
        var page  = classSessionRepository.findByGroupClass(groupClassId, range.from(), range.to(), pageable);

        return toResponsePage(page);
    }

    /**
     * "Reprograma: fecha, hora, cupo o entrenador de esa sesión" (§3.6). There is no
     * DB constraint at the session level (only uq_group_class_slot at the class
     * level), so the trainer-schedule conflict is checked here whenever the slot
     * actually changes.
     */
    public ClassSessionResponse reschedule(Long sessionId, SessionRescheduleRequest request)
    {
        var session = findOrFail(sessionId);

        assertNotClosed(session);

        var slotChanged = !session.getTrainerId().equals(request.trainerId())
                || !session.getSessionDate().equals(request.sessionDate())
                || !session.getStartTime().equals(request.startTime());

        if (slotChanged)
        {
            trainerService.findById(request.trainerId());

            var conflict = classSessionRepository.existsByTrainerIdAndSessionDateAndStartTimeAndClassSessionIdNot(
                    request.trainerId(), request.sessionDate(), request.startTime(), sessionId);

            if (conflict)
            {
                throw new BusinessException(ErrorCode.TRAINER_SCHEDULE_CONFLICT);
            }
        }

        session.setSessionDate(request.sessionDate());
        session.setStartTime(request.startTime());
        session.setMaxCapacity(request.maxCapacity());
        session.setTrainerId(request.trainerId());

        // ponytail: SESSION_RESCHEDULED debiera notificar a los inscritos; el módulo
        // notification no existe todavía (02-Modulos §2.9).

        return ClassSessionResponse.from(session, seatsTaken(sessionId));
    }

    /**
     * "El entrenador inicia (IN_PROGRESS) y cierra (COMPLETED) su clase" (§3.6), each
     * from exactly one prior state.
     */
    public ClassSessionResponse changeStatus(Long sessionId, SessionStatusRequest request, AuthenticatedUser principal)
    {
        var session = findOrFail(sessionId);

        assertTrainerScope(session, principal);

        switch (request.status())
        {
            case IN_PROGRESS ->
            {
                if (session.getStatus() != SessionStatus.SCHEDULED)
                {
                    throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
                }

                session.setStatus(SessionStatus.IN_PROGRESS);
                session.setOpenedAt(Instant.now());
            }
            case COMPLETED ->
            {
                if (session.getStatus() != SessionStatus.IN_PROGRESS)
                {
                    throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
                }

                session.setStatus(SessionStatus.COMPLETED);
                session.setClosedAt(Instant.now());
            }
            default -> throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }

        return ClassSessionResponse.from(session, seatsTaken(sessionId));
    }

    /** "Cancela la sesión con motivo [...] en bloque las inscripciones activas" (§3.6). */
    public ClassSessionResponse cancel(Long sessionId, SessionCancellationRequest request)
    {
        var session = findOrFail(sessionId);

        assertNotClosed(session);

        session.setStatus(SessionStatus.CANCELLED);
        session.setCancellationReason(request.cancellationReason());

        var now = Instant.now();

        classEnrollmentRepository.findByClassSessionIdAndStatus(sessionId, EnrollmentStatus.ENROLLED)
                .forEach(enrollment ->
                {
                    enrollment.setStatus(EnrollmentStatus.CANCELLED);
                    enrollment.setCancelledAt(now);
                });

        // ponytail: SESSION_CANCELLED debiera notificar a los inscritos; el módulo
        // notification no existe todavía (02-Modulos §2.9).

        return ClassSessionResponse.from(session, seatsTaken(sessionId));
    }

    /**
     * "Puede ver únicamente las clases que le han sido asignadas" (Enunciado): security,
     * not an interface filter. Package-private so EnrollmentService (attendance) and
     * ClassRatingService (GET .../ratings) reuse the same check instead of repeating it.
     */
    void assertTrainerScope(ClassSession session, AuthenticatedUser principal)
    {
        if (principal.role() != UserRole.TRAINER)
        {
            return;
        }

        if (!trainerService.findTrainerIdByUser(principal).equals(session.getTrainerId()))
        {
            throw new BusinessException(ErrorCode.TRAINER_SCOPE_VIOLATION);
        }
    }

    /** Seats taken on one session: active enrolments + reserved-but-unexpired waitlist notifications. */
    int seatsTaken(Long sessionId)
    {
        return seatsTakenBulk(List.of(sessionId)).get(sessionId);
    }

    /** The session, fetched with its class already loaded: every write and read path needs both. */
    ClassSession findOrFail(Long sessionId)
    {
        return classSessionRepository.findDetailById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CLASS_SESSION_NOT_FOUND));
    }

    private Page<ClassSessionResponse> toResponsePage(Page<ClassSession> page)
    {
        var sessionIds = page.getContent().stream().map(ClassSession::getClassSessionId).toList();
        var seatsTaken = seatsTakenBulk(sessionIds);

        return page.map(session -> ClassSessionResponse.from(session, seatsTaken.getOrDefault(session.getClassSessionId(), 0)));
    }

    /**
     * The only place the two-part sum of design decision #2 is written: one grouped
     * query per part, so a page of sessions costs two queries and not two per row.
     */
    private Map<Long, Integer> seatsTakenBulk(List<Long> sessionIds)
    {
        if (sessionIds.isEmpty())
        {
            return Map.of();
        }

        var enrolled = classEnrollmentRepository.countActiveByClassSessionIds(sessionIds, EnrollmentStatus.CANCELLED).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
        var reserved = waitlistEntryRepository.countReservedByClassSessionIds(sessionIds, WaitlistStatus.NOTIFIED, Instant.now())
                .stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));

        var result = new HashMap<Long, Integer>();

        sessionIds.forEach(id -> result.put(id, (int) (enrolled.getOrDefault(id, 0L) + reserved.getOrDefault(id, 0L))));

        return result;
    }

    private static void assertNotClosed(ClassSession session)
    {
        if (session.getStatus() == SessionStatus.CANCELLED || session.getStatus() == SessionStatus.COMPLETED)
        {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }
    }
}
