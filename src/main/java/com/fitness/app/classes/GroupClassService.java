package com.fitness.app.classes;

import com.fitness.app.classes.dto.ClassSessionResponse;
import com.fitness.app.classes.dto.GroupClassRequest;
import com.fitness.app.classes.dto.GroupClassResponse;
import com.fitness.app.classes.model.ClassSession;
import com.fitness.app.classes.model.DifficultyLevel;
import com.fitness.app.classes.model.Discipline;
import com.fitness.app.classes.model.GroupClass;
import com.fitness.app.classes.model.SessionStatus;
import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.directory.TrainerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The recurring class catalog: "el administrador crea las clases grupales definiendo
 * nombre, entrenador a cargo, horario, duración y cupo máximo" (Enunciado), plus
 * generating the dated sessions of a range.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class GroupClassService
{
    private final GroupClassRepository   groupClassRepository;
    private final ClassSessionRepository classSessionRepository;
    private final TrainerService         trainerService;

    @Transactional(readOnly = true)
    public Page<GroupClassResponse> search(Discipline discipline, Long trainerId, DifficultyLevel difficultyLevel,
                                           Boolean active, Pageable pageable)
    {
        return groupClassRepository.search(discipline, trainerId, difficultyLevel, active, pageable)
                .map(GroupClassResponse::from);
    }

    @Transactional(readOnly = true)
    public GroupClassResponse findById(Long groupClassId)
    {
        return GroupClassResponse.from(findOrFail(groupClassId));
    }

    public GroupClassResponse create(GroupClassRequest request)
    {
        if (groupClassRepository.existsByCode(request.code()))
        {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Ya existe una clase con ese código.");
        }

        // Validates the trainer exists before writing anything.
        trainerService.findById(request.trainerId());

        var groupClass = new GroupClass();

        groupClass.setCode(request.code());
        applyRequest(groupClass, request);
        // The DDL defaults active and created_at, but the entity maps them, so
        // Hibernate sends them in the INSERT and a null would break NOT NULL.
        groupClass.setActive(true);
        groupClass.setCreatedAt(Instant.now());

        return GroupClassResponse.from(groupClassRepository.save(groupClass));
    }

    public GroupClassResponse update(Long groupClassId, GroupClassRequest request)
    {
        var groupClass = findOrFail(groupClassId);

        trainerService.findById(request.trainerId());
        applyRequest(groupClass, request);

        return GroupClassResponse.from(groupClass);
    }

    /** "Desactiva la clase (baja lógica; no borra el historial)" (§3.6). */
    public void deactivate(Long groupClassId)
    {
        findOrFail(groupClassId).setActive(false);
    }

    /**
     * "Genera las sesiones fechadas de un rango" (§3.6): one class_session per date in
     * [from, to] whose day of week matches the class, copying trainer, start time and
     * capacity as this session's own values. Dates that already have a session
     * (uq_session_date) are skipped instead of failing the whole batch.
     */
    public List<ClassSessionResponse> generateSessions(Long groupClassId, LocalDate from, LocalDate to)
    {
        if (to.isBefore(from))
        {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "La fecha final no puede ser anterior a la inicial.");
        }

        var groupClass = findOrFail(groupClassId);
        var created    = new ArrayList<ClassSession>();

        for (var date = from; !date.isAfter(to); date = date.plusDays(1))
        {
            if (date.getDayOfWeek() != groupClass.getWeekday())
            {
                continue;
            }

            if (classSessionRepository.existsByGroupClass_GroupClassIdAndSessionDate(groupClassId, date))
            {
                continue;
            }

            var session = new ClassSession();

            session.setGroupClass(groupClass);
            session.setTrainerId(groupClass.getTrainerId());
            session.setSessionDate(date);
            session.setStartTime(groupClass.getStartTime());
            session.setMaxCapacity(groupClass.getMaxCapacity());
            session.setStatus(SessionStatus.SCHEDULED);

            created.add(classSessionRepository.save(session));
        }

        // A session just generated has no enrolments yet: seatsTaken is 0 by construction.
        return created.stream().map(session -> ClassSessionResponse.from(session, 0)).toList();
    }

    /** The entity, for the sibling services that need to read or write it directly. */
    GroupClass findOrFail(Long groupClassId)
    {
        return groupClassRepository.findById(groupClassId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_CLASS_NOT_FOUND));
    }

    private static void applyRequest(GroupClass groupClass, GroupClassRequest request)
    {
        groupClass.setName(request.name());
        groupClass.setDiscipline(request.discipline());
        groupClass.setDifficultyLevel(request.difficultyLevel() == null ? DifficultyLevel.BEGINNER
                                                                        : request.difficultyLevel());
        groupClass.setTrainerId(request.trainerId());
        groupClass.setWeekday(request.weekday());
        groupClass.setStartTime(request.startTime());
        groupClass.setDurationMinutes(request.durationMinutes());
        groupClass.setMaxCapacity(request.maxCapacity());
    }
}
