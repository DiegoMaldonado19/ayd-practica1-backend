package com.fitness.app.classes;

import com.fitness.app.classes.dto.ClassRatingRequest;
import com.fitness.app.classes.dto.ClassRatingResponse;
import com.fitness.app.classes.model.ClassRating;
import com.fitness.app.classes.model.EnrollmentStatus;
import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.directory.MemberService;
import com.fitness.app.iam.dto.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * "El socio puede [...] calificar una clase [...] recibido" (Enunciado): rating and
 * listing what was rated.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ClassRatingService
{
    private final ClassRatingRepository     classRatingRepository;
    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final ClassSessionService       classSessionService;
    private final MemberService             memberService;

    /**
     * "Requiere haber asistido" (§3.6): checked against the enrolment's own ATTENDED
     * status, never a separate flag. Rating twice collides with uq_rating_member; the
     * error catalog (§4) defines no code for that case, so it is pre-checked here
     * rather than inventing one outside contract (design decision).
     */
    public ClassRatingResponse rate(Long sessionId, ClassRatingRequest request, AuthenticatedUser principal)
    {
        classSessionService.findOrFail(sessionId);

        var memberId = memberService.findOwnMemberId(principal);

        if (!classEnrollmentRepository.existsByClassSessionIdAndMemberIdAndStatus(sessionId, memberId,
                                                                                   EnrollmentStatus.ATTENDED))
        {
            throw new BusinessException(ErrorCode.RATING_REQUIRES_ATTENDANCE);
        }

        if (classRatingRepository.existsByClassSessionIdAndMemberId(sessionId, memberId))
        {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Ya calificaste esta sesión.");
        }

        var rating = new ClassRating();

        rating.setClassSessionId(sessionId);
        rating.setMemberId(memberId);
        rating.setClassScore(request.classScore());
        rating.setTrainerScore(request.trainerScore());
        rating.setComment(request.comment());
        rating.setRatedAt(Instant.now());

        return ClassRatingResponse.from(classRatingRepository.save(rating));
    }

    /** "Calificaciones recibidas por la sesión" (§3.6), scoped to the trainer's own classes. */
    @Transactional(readOnly = true)
    public Page<ClassRatingResponse> findBySession(Long sessionId, AuthenticatedUser principal, Pageable pageable)
    {
        var session = classSessionService.findOrFail(sessionId);

        classSessionService.assertTrainerScope(session, principal);

        return classRatingRepository.findByClassSessionId(sessionId, pageable).map(ClassRatingResponse::from);
    }
}
